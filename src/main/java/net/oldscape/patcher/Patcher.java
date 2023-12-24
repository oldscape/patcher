package net.oldscape.patcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import net.oldscape.patcher.transformer.BitShiftTransformer;
import net.oldscape.patcher.transformer.Jdk9MouseFixer;
import net.oldscape.patcher.transformer.PacketVariantMapper;
import net.oldscape.patcher.transformer.PacketVariantMapper.MethodVariants;
import net.oldscape.patcher.transformer.RSAPubKeyReplacer;
import net.oldscape.patcher.transformer.RSAPubKeyReplacer.RSAKeyFields;
import net.oldscape.patcher.transformer.RemoveImpossibleJumps;
import net.oldscape.patcher.transformer.RemoveUnusedMath;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

public class Patcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Patcher.class);
    private static final ObjectMapper TOML_MAPPER = new TomlMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final List<Transformer> transformers;
    private final Path srcJar;
    private final Path outJar;

    private Patcher(List<Transformer> transformers, Path srcJar, Path outJar) {
        this.transformers = transformers;
        this.srcJar = srcJar;
        this.outJar = outJar;
    }

    public static Patcher create(PatcherOptions options) throws IOException, InterruptedException {
        RSAPublicKeySpec publicKeySpec;

        if (options.loginKeyUrl() == null) {
            publicKeySpec = loadOrGenRsaPubKey();
        } else {
            publicKeySpec = loadRsaPubKey(options.loginKeyUrl());
        }
        var transformers = List.of(
                new BitShiftTransformer(),
                new Jdk9MouseFixer(),
                new RemoveUnusedMath(),
                new RemoveImpossibleJumps(),
                RSAPubKeyReplacer.create(publicKeySpec, loadRsaKeyFields()),
                PacketVariantMapper.create(loadPacketVariants())
        );
        return new Patcher(transformers, options.srcJar(), options.outJar());
    }

    public void process() throws IOException {
        if (Files.notExists(srcJar)) {
            LOGGER.error("Could not find src jar.");
            return;
        }
        var classNodes = loadJar(srcJar);
        for (var transformer : transformers) {
            transformer.transform(classNodes);
        }
        saveJar(outJar, classNodes.values(), loadMappings());
    }

    private void saveJar(Path target, Collection<ClassNode> classNodes, Map<String, String> mappings) throws IOException {

        try (var output = new JarOutputStream(Files.newOutputStream(target, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            for (var node : classNodes) {
                var analyzer = new Analyzer<>(new BasicVerifier());
                for (var methodNode : node.methods) {
                    try {
                        analyzer.analyze(node.name, methodNode);
                    } catch (AnalyzerException e) {
                        var textifier = new Textifier();
                        var methodVisitor = new TraceMethodVisitor(textifier);
                        methodNode.accept(methodVisitor);

                        try (var printWriter = new PrintWriter(System.out)) {
                            textifier.print(printWriter);
                        }
                        throw new IllegalStateException("Bytecode correctness failed: " + e.getMessage() + " at " + node.name + "." + methodNode.name + methodNode.desc);
                    }
                }

                var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                var remapper = new SimpleRemapper(mappings);
                node.accept(new ClassRemapper(writer, remapper));

                var entry = new JarEntry(mappings.getOrDefault(node.name, node.name) + ".class");
                output.putNextEntry(entry);
                output.write(writer.toByteArray());

                output.closeEntry();
            }
        }
    }

    private Map<String, ClassNode> loadJar(Path pathToJar) throws IOException {
        return loadJar(pathToJar, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private Map<String, ClassNode> loadJar(Path pathToJar, int parsingOptions) throws IOException {
        Map<String, ClassNode> classNodes = new HashMap<>();

        try (var jarFile = new JarFile(pathToJar.toString())) {
            var enums = jarFile.entries();

            while (enums.hasMoreElements()) {
                var entry = (JarEntry) enums.nextElement();

                if (!entry.getName().endsWith(".class")) {
                    continue;
                }
                var classReader = new ClassReader(jarFile.getInputStream(entry));
                var classNode = new ClassNode();

                classReader.accept(classNode, parsingOptions);
                classNodes.put(classNode.name, classNode);
            }
        }
        return classNodes;
    }

    private static RSAPublicKeySpec loadRsaPubKey(String url) throws IOException, InterruptedException {
        var httpClient = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create(url))
                                 .GET()
                                 .build();
        var response = httpClient.send(request, BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new IOException("Http response code: " + response.statusCode());
        }

        try (var body = response.body()) {
            return RSAKeyFactory.create().publicKeySpecFrom(body);
        }
    }

    private static RSAPublicKeySpec loadOrGenRsaPubKey() throws IOException {
        var key = Path.of("login-private-key.der");
        var keyFactory = RSAKeyFactory.create();

        if (Files.exists(key)) {
            LOGGER.info("Loading key pair from disk...");
            try (var stream = Files.newInputStream(key, StandardOpenOption.READ)) {
                var privateKey = keyFactory.privateKeySpecFrom(stream);
                var publicKey = keyFactory.publicKeySpecFrom(privateKey);
                printKeyPairInfo(privateKey, publicKey);
                return publicKey;
            }
        } else {
            return generateRsaKey();
        }
    }

    private static RSAPublicKeySpec generateRsaKey() throws IOException {
        LOGGER.info("Generating new RSA key pair...");
        var keyFactory = RSAKeyFactory.create();
        var keyPair = keyFactory.generateKeyPair();
        var publicKey = keyFactory.publicKeySpecFrom(keyPair.getPublic());
        var privateKey = keyFactory.privateKeySpecFrom(keyPair.getPrivate());
        printKeyPairInfo(privateKey, publicKey);

        try (var stream = Files.newOutputStream(Path.of("login-private-key.der"), StandardOpenOption.CREATE)) {
            stream.write(keyPair.getPrivate().getEncoded());
        }
        return publicKey;
    }

    private static void printKeyPairInfo(RSAPrivateKeySpec privateKey, RSAPublicKeySpec publicKey) {
        var info = """
                ===== RSA Key Pair Info =====
                ----- Public key -----
                exponent=%s
                modulus=%s
                ----- Private key -----
                exponent=%s
                modulus=%s
                """.formatted(publicKey.getPublicExponent(), publicKey.getModulus(), privateKey.getPrivateExponent(), privateKey.getModulus());
        LOGGER.info(info);
    }

    private static RSAKeyFields loadRsaKeyFields() throws IOException {
        try (var stream = Patcher.class.getResourceAsStream("/rsa-key.toml")) {
            return TOML_MAPPER.readValue(stream, RSAKeyFields.class);
        }
    }

    private static Map<String, List<MethodVariants>> loadPacketVariants() throws IOException {
        try (var stream = Patcher.class.getResourceAsStream("/packet-variants.toml")) {
            return TOML_MAPPER.readValue(stream, new TypeReference<>() {
            });
        }
    }

    private static Map<String, String> loadMappings() throws IOException {
        try (var stream = Patcher.class.getResourceAsStream("/mappings.toml")) {
            return TOML_MAPPER.readValue(stream, new TypeReference<>() {
            });
        }
    }
}
