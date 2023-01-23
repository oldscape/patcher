package net.oldscape.patcher;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;

public record RSAKeyFactory(KeyFactory factory, KeyPairGenerator generator) {

    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 512;

    public static RSAKeyFactory create() {
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
            keyGen.initialize(KEY_SIZE);

            return new RSAKeyFactory(factory, keyGen);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("RSA algorithm is not available on this platform", e);
        }
    }

    public KeyPair generateKeyPair() {
        return generator.generateKeyPair();
    }

    public RSAPublicKeySpec publicKeySpecFrom(KeyPair keyPair) {
        return publicKeySpecFrom(keyPair.getPublic());
    }

    public RSAPublicKeySpec publicKeySpecFrom(PublicKey publicKey) {
        try {
            return factory.getKeySpec(publicKey, RSAPublicKeySpec.class);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Invalid key spec", e);
        }
    }

    public RSAPublicKeySpec publicKeySpecFrom(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            var encodedKeySpec = new X509EncodedKeySpec(bytes);

            var publicKey = factory.generatePublic(encodedKeySpec);

            return publicKeySpecFrom(publicKey);
        } catch (IOException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to load pubkey", e);
        }
    }

    public RSAPublicKeySpec publicKeySpecFrom(PrivateKey privateKey) {
        try {
            var crtPrivateKeySpec = factory.getKeySpec(privateKey, RSAPrivateCrtKeySpec.class);
            return new RSAPublicKeySpec(crtPrivateKeySpec.getModulus(), crtPrivateKeySpec.getPublicExponent());
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Invalid key spec", e);
        }
    }

    public RSAPublicKeySpec publicKeySpecFrom(RSAPrivateKeySpec privateKeySpec) {
        try {
            var privateKey = factory.generatePrivate(privateKeySpec);
            var crtPrivateKeySpec = factory.getKeySpec(privateKey, RSAPrivateCrtKeySpec.class);
            return new RSAPublicKeySpec(crtPrivateKeySpec.getModulus(), crtPrivateKeySpec.getPublicExponent());
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Invalid key spec", e);
        }
    }

    public RSAPrivateKeySpec privateKeySpecFrom(KeyPair keyPair) {
        return privateKeySpecFrom(keyPair.getPrivate());
    }

    public RSAPrivateKeySpec privateKeySpecFrom(PrivateKey publicKey) {
        try {
            return factory.getKeySpec(publicKey, RSAPrivateKeySpec.class);
        } catch (InvalidKeySpecException e) {
            throw new RuntimeException("Invalid key spec", e);
        }
    }

    public RSAPrivateKeySpec privateKeySpecFrom(InputStream inputStream) {
        try {
            byte[] bytes = inputStream.readAllBytes();
            var encodedKeySpec = new PKCS8EncodedKeySpec(bytes);

            var privateKey = factory.generatePrivate(encodedKeySpec);

            return privateKeySpecFrom(privateKey);
        } catch (IOException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to load privkey", e);
        }
    }
}