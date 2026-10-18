package ai.loomspan.sdk;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64;
import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;

/** Loads one startup signer and atomically publishes only its public trust. */
public final class SigningKeys {
    private static final Logger LOG = Logger.getLogger(SigningKeys.class.getName());
    private SigningKeys() {}

    public static SigningKeySource initialize(SigningSettings settings) {
        return initialize(settings, Clock.systemUTC(), new FileOperations());
    }

    static SigningKeySource initialize(SigningSettings settings, Clock clock, FileOperations files) {
        Path export = "colocated".equals(settings.mode()) ? settings.keyDirectory().resolve("jwks.json") : settings.provided().jwksExport();
        Path material = "colocated".equals(settings.mode()) ? settings.keyDirectory() : settings.provided().keyLocation();
        if (settings.provided() != null) validateExportDestination(settings.provided());
        try {
            PrivateKey privateKey;
            RSAPublicKey publicKey;
            List<X509Certificate> certificates = new ArrayList<>();
            if ("colocated".equals(settings.mode())) {
                if (!Files.isDirectory(settings.keyDirectory())) throw new GeneralSecurityException();
                var generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                var pair = generator.generateKeyPair();
                privateKey = pair.getPrivate();
                publicKey = (RSAPublicKey) pair.getPublic();
            } else {
                var p = settings.provided();
                if ("PEM".equals(p.format())) {
                    String pem = Files.readString(p.keyLocation());
                    if (!pem.strip().startsWith("-----BEGIN PRIVATE KEY-----")
                            || !pem.strip().endsWith("-----END PRIVATE KEY-----"))
                        throw new IllegalArgumentException("PEM requires unencrypted PKCS#8 PRIVATE KEY; convert PKCS#1/encrypted material or use PKCS12/JKS");
                    byte[] encoded = java.util.Base64.getMimeDecoder().decode(pem.strip()
                            .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
                    try { privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded)); }
                    finally { Arrays.fill(encoded, (byte) 0); }
                    if (!(privateKey instanceof RSAPrivateCrtKey rsa)) throw new GeneralSecurityException();
                    publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
                    if (p.certificateLocation() != null) {
                        try (var input = Files.newInputStream(p.certificateLocation())) {
                            certificates.add((X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input));
                        }
                    }
                } else {
                    char[] storePassword = p.keystorePasswordReference().resolve();
                    char[] keyPassword = null;
                    try {
                        keyPassword = p.keyPasswordReference() == null ? storePassword.clone() : p.keyPasswordReference().resolve();
                        var store = KeyStore.getInstance(p.keyLocation().toFile(), storePassword);
                        if (!store.getType().equalsIgnoreCase(p.format())) throw new GeneralSecurityException();
                        var key = store.getKey(p.keyAlias(), keyPassword);
                        if (!(key instanceof PrivateKey loaded)) throw new GeneralSecurityException();
                        privateKey = loaded;
                        var chain = store.getCertificateChain(p.keyAlias());
                        if (chain == null || chain.length == 0) throw new GeneralSecurityException();
                        for (var certificate : chain) certificates.add((X509Certificate) certificate);
                        if (!(certificates.getFirst().getPublicKey() instanceof RSAPublicKey rsa)) throw new GeneralSecurityException();
                        publicKey = rsa;
                    } finally {
                        Arrays.fill(storePassword, '\0');
                        if (keyPassword != null) Arrays.fill(keyPassword, '\0');
                    }
                }
            }
            if (!(privateKey instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < 2048
                    || publicKey.getModulus().bitLength() < 2048) throw new GeneralSecurityException();
            byte[] challenge = "Loomspan signing key validation".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] proof = sign(privateKey, challenge);
            var verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(publicKey); verifier.update(challenge);
            if (!verifier.verify(proof)) throw new GeneralSecurityException();
            if (!certificates.isEmpty()) {
                if (!certificates.getFirst().getPublicKey().equals(publicKey)) throw new GeneralSecurityException();
                if (!certificates.getFirst().getNotAfter().toInstant().isAfter(clock.instant().plus(Duration.ofDays(30))))
                    LOG.warning("SDK signing certificate expired or expires within 30 days; replace operator material and restart");
            }
            String kid = new RSAKey.Builder(publicKey).build().computeThumbprint().toString();
            var builder = new RSAKey.Builder(publicKey).keyID(kid).keyUse(KeyUse.SIGNATURE).algorithm(JWSAlgorithm.RS256);
            if (!certificates.isEmpty()) {
                var chain = new ArrayList<Base64>();
                for (var cert : certificates) chain.add(Base64.encode(cert.getEncoded()));
                builder.x509CertChain(chain);
            }
            publish(export, new JWKSet(builder.build()).toString(), files);
            return new LocalSource(privateKey, publicKey, kid);
        } catch (IllegalArgumentException error) {
            // Never attach parsing exceptions: providers may include passwords or key input.
            if (error.getMessage() != null && error.getMessage().startsWith("PEM requires"))
                throw new IllegalArgumentException(error.getMessage() + "; key-location=" + material);
            throw new IllegalArgumentException("Unable to initialize RSA RS256 signing material at " + material + "; public export " + export);
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Unable to initialize RSA RS256 signing material (minimum 2048 bits) at " + material + "; public export " + export);
        }
    }

    private static byte[] sign(PrivateKey key, byte[] input) throws GeneralSecurityException {
        var signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key); signature.update(input); return signature.sign();
    }
    private static void validateExportDestination(SigningSettings.Provided provided) {
        var inputs = new ArrayList<Path>();
        inputs.add(provided.keyLocation());
        if (provided.certificateLocation() != null) inputs.add(provided.certificateLocation());
        for (var reference : new SecretReference[]{provided.keystorePasswordReference(), provided.keyPasswordReference()}) {
            if (reference != null && reference.filePath() != null) inputs.add(reference.filePath());
        }
        Path destination = provided.jwksExport().toAbsolutePath().normalize();
        for (Path input : inputs) {
            try {
                if (destination.equals(input.toAbsolutePath().normalize())
                        || (Files.exists(destination) && Files.exists(input) && Files.isSameFile(destination, input)))
                    throw new IllegalArgumentException("JWKS export must not replace signing input: " + destination);
            } catch (IOException ignored) {
                throw new IllegalArgumentException("Unable to validate JWKS export destination: " + destination);
            }
        }
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
