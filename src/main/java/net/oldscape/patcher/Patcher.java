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
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

    public static Patcher create(Path srcJar, Path outJar) throws IOException {
        var publicKeySpec = generateRsaKey();

        var transformers = List.of(
                new BitShiftTransformer(),
                new Jdk9MouseFixer(),
                RSAPubKeyReplacer.create(publicKeySpec, loadRsaKeyFields()),
                PacketVariantMapper.create(loadPacketVariants())
        );
        return new Patcher(transformers, srcJar, outJar);
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

    private static RSAPublicKeySpec loadOrGenRsaPubKey() throws IOException {
        var key = Path.of("login-key.key");
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

        try (var stream = Files.newOutputStream(Path.of("login-key.key"), StandardOpenOption.CREATE)) {
            stream.write(keyPair.getPrivate().getEncoded());
        }
        return publicKey;
    }

    private static void printKeyPairInfo(RSAPrivateKeySpec privateKey, RSAPublicKeySpec publicKey) {
        LOGGER.info("===== RSA Key Pair Info =====");
        LOGGER.info("----- Public key -----");
        LOGGER.info("exponent={}", publicKey.getPublicExponent());
        LOGGER.info("modulus={}", publicKey.getModulus());
        LOGGER.info("----- Private key -----");
        LOGGER.info("exponent={}", privateKey.getPrivateExponent());
        LOGGER.info("modulus={}", privateKey.getModulus());
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
