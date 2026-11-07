package ai.loomspan.sdk;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bouncycastle.asn1.ASN1Null;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.Attribute;

/** Loads one startup signer and atomically publishes only its public trust. */
public final class SigningKeys {
    private static final Pattern PRIVATE_KEY_PEM = Pattern.compile(
            "\\A\\s*-----BEGIN PRIVATE KEY-----[ \\t]*[\\r\\n]([A-Za-z0-9+/= \\t\\r\\n]+)[\\r\\n]"
                    + "-----END PRIVATE KEY-----\\s*\\z");

    private SigningKeys() {}

    public static SigningKeySource initialize(SigningSettings settings) {
        return initialize(settings, new FileOperations());
    }

    static SigningKeySource initialize(SigningSettings settings, FileOperations files) {
        Path export = "colocated".equals(settings.mode()) ? settings.keyDirectory().resolve("jwks.json") : settings.provided().jwksExport();
        Path material = "colocated".equals(settings.mode()) ? settings.keyDirectory() : settings.provided().keyLocation();
        if (settings.provided() != null) validateExportDestination(settings.provided());
        try {
            PrivateKey privateKey;
            RSAPublicKey publicKey;
            if ("colocated".equals(settings.mode())) {
                if (!Files.isDirectory(settings.keyDirectory())) throw new GeneralSecurityException();
                var generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                var pair = generator.generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
            } else {
                RSAPrivateCrtKey rsa = loadPem(settings.provided().keyLocation());
                privateKey = rsa;
                publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(
                        new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
            }
            if (!(privateKey instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < 2048
                    || publicKey.getModulus().bitLength() < 2048) throw new GeneralSecurityException();
            byte[] challenge = "Loomspan signing key validation".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] proof = sign(privateKey, challenge);
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey); verifier.update(challenge);
            if (!verifier.verify(proof)) throw new GeneralSecurityException();
            String kid = new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
            var builder = new RSAKey.Builder(publicKey).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256);
            publish(export, new JWKSet(builder.build()).toString(), files);
            return new LocalSource(privateKey, publicKey, kid);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Unable to initialize RSA RS256 signing material (minimum 2048 bits) at " + material + "; public export " + export);
        }
    }

    private static byte[] sign(PrivateKey key, byte[] input) throws GeneralSecurityException {
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key); signature.update(input); return signature.sign();
    }
    private static void validateExportDestination(SigningSettings.Provided provided) {
        Path destination = provided.jwksExport().toAbsolutePath();
        Path input = provided.keyLocation().toAbsolutePath();
        try {
            // Use the original paths for filesystem identity; normalization can skip a link traversal.
            if (destination.normalize().equals(input.normalize())
                    || (Files.exists(destination) && Files.exists(input) && Files.isSameFile(destination, input)))
                throw new IllegalArgumentException("JWKS export must not replace signing input: " + destination);
        } catch (IOException ignored) {
            throw new IllegalArgumentException("Unable to validate JWKS export destination: " + destination);
        }
    }

    private static RSAPrivateCrtKey loadPem(Path location) throws IOException, GeneralSecurityException {
        var match = PRIVATE_KEY_PEM.matcher(Files.readString(location));
        if (!match.matches()) throw new GeneralSecurityException();
        String base64 = match.group(1).replaceAll("[ \\t\\r\\n]", "");
        if (base64.isEmpty() || base64.length() % 4 != 0) throw new GeneralSecurityException();
        byte[] encoded = Base64.getDecoder().decode(base64);
        try {
            // KeyFactory can ignore PKCS#8 parameters, optional fields, and trailing data.
            var info = PrivateKeyInfo.getInstance(ASN1Primitive.fromByteArray(encoded));
            var algorithm = info.getPrivateKeyAlgorithm();
            var parameters = algorithm.getParameters();
            if (PKCSObjectIdentifiers.rsaEncryption.equals(algorithm.getAlgorithm())
                    && parameters != null && !(parameters.toASN1Primitive() instanceof ASN1Null))
                throw new GeneralSecurityException();
            var attributes = info.getAttributes();
            if (attributes != null) {
                for (var attribute : attributes) {
                    if (Attribute.getInstance(attribute).getAttrValues().size() == 0)
                        throw new GeneralSecurityException();
                }
            }
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
            if (!(key instanceof RSAPrivateCrtKey rsa)) throw new GeneralSecurityException();
            if (info.hasPublicKey()) {
                var publicKey = org.bouncycastle.asn1.pkcs.RSAPublicKey.getInstance(info.parsePublicKey());
                if (!rsa.getModulus().equals(publicKey.getModulus())
                        || !rsa.getPublicExponent().equals(publicKey.getPublicExponent()))
                    throw new GeneralSecurityException();
            }
            return rsa;
        } finally { Arrays.fill(encoded, (byte) 0); }
    }

    private static final class LocalSource implements SigningKeySource {
        private final PrivateKey privateKey;
        private final RSAPublicKey publicKey;
        private final String kid;
        LocalSource(PrivateKey privateKey, RSAPublicKey publicKey, String kid) {
            this.privateKey = privateKey; this.publicKey = publicKey; this.kid = kid;
        }
        public byte[] signRs256(byte[] input) {
            try { return sign(privateKey, input); }
            catch (GeneralSecurityException ignored) { throw new IllegalStateException("Execution credential signing failed"); }
        }
        public RSAPublicKey publicKey() { return publicKey; }
        public String kid() { return kid; }
        @Override public String toString() { return "SigningKeySource[private material redacted]"; }
    }

    private static void publish(Path destination, String publicJson, FileOperations files) throws IOException {
        Path temporary = null;
        try {
            temporary = files.temporary(destination.toAbsolutePath().getParent());
            files.write(temporary, publicJson);
            files.move(temporary, destination.toAbsolutePath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { if (temporary != null) Files.deleteIfExists(temporary); }
    }
    /** Small NIO seam for deterministic platform failure tests; no permission probing. */
    static class FileOperations {
        Path temporary(Path parent) throws IOException { return Files.createTempFile(parent, ".jwks-", ".tmp"); }
        void write(Path path, String content) throws IOException { Files.writeString(path, content); }
        void move(Path source, Path target, CopyOption... options) throws IOException { Files.move(source, target, options); }
    }
}
