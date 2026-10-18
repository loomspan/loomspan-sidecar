package ai.loomspan.sdk;

import com.nimbusds.jose.jwk.*;
import java.nio.file.*;
import java.security.Signature;
import java.time.*;
import java.util.*;
import java.util.logging.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SigningKeysTest {
    @TempDir Path directory;
    @Test void colocatedExportsOnlyPublicKeyAndChangesKidOnRestart() throws Exception {
        var settings = SigningSettings.colocated(directory);
        var first = SigningKeys.initialize(settings);
        assertEquals(2048, first.publicKey().getModulus().bitLength());
        assertEquals(new RSAKey.Builder(first.publicKey()).build().computeThumbprint().toString(), first.kid());
        verify(first);
        var jwk = JWKSet.parse(Files.readString(directory.resolve("jwks.json"))).getKeys().getFirst();
        assertFalse(jwk.isPrivate());
        assertEquals(Set.of("kty", "n", "e", "alg", "use", "kid"), jwk.toJSONObject().keySet());
        var second = SigningKeys.initialize(settings);
        assertNotEquals(first.kid(), second.kid());
        try (var files = Files.list(directory)) { assertEquals(List.of("jwks.json"), files.map(p -> p.getFileName().toString()).toList()); }
    }
    @Test void directoryErrorsAndMixedModesAreActionable() throws Exception {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SigningSettings.colocated(null)).getMessage().contains("<unset>"));
        Path missing = directory.resolve("missing");
        assertTrue(assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(SigningSettings.colocated(missing))).getMessage().contains(missing.toString()));
        assertFalse(Files.exists(missing));
        Path file = Files.writeString(directory.resolve("file"), "file");
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(SigningSettings.colocated(file)));
        var provided = new SigningSettings.Provided("PEM", file, null, null, null, null, file);
        assertThrows(IllegalArgumentException.class, () -> new SigningSettings("colocated", directory, provided));
        assertThrows(IllegalArgumentException.class, () -> new SigningSettings("provided", directory, provided));
    }
    @Test void atomicExportFailsClosedAndPreservesPriorFile() throws Exception {
        Path export = Files.writeString(directory.resolve("jwks.json"), "prior complete public export");
        for (int mode = 0; mode < 3; mode++) {
            int failure = mode;
            var files = new SigningKeys.FileOperations() {
                @Override void write(Path path, String content) throws java.io.IOException {
                    assertEquals(directory, path.getParent());
                    if (failure == 0) throw new AccessDeniedException(path.toString());
                    super.write(path, content);
                }
                @Override void move(Path source, Path target, CopyOption... options) throws java.io.IOException {
                    assertEquals(Set.of(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING), Set.of(options));
                    assertTrue(Files.readString(source).contains("\"keys\""));
                    if (failure == 1) throw new AccessDeniedException(target.toString());
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "synthetic");
                }
            };
            assertTrue(assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(SigningSettings.colocated(directory), Clock.systemUTC(), files)).getMessage().contains(export.toString()));
            assertEquals("prior complete public export", Files.readString(export));
            try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
        }
    }
    @Test void providedFormatsLoadTheSuppliedKeyAndExportCertificates() throws Exception {
        for (String format : List.of("PKCS12", "JKS")) {
            var fixture = new SigningTestMaterial(directory.resolve(format), format, "RSA", 2048);
            Path export = directory.resolve(format + ".json");
            var first = SigningKeys.initialize(fixture.settings(format, export, false));
            verify(first);
            assertEquals(first.publicKey(), ((RSAKey) JWKSet.parse(Files.readString(export)).getKeys().getFirst()).toRSAPublicKey());
            assertEquals(first.kid(), SigningKeys.initialize(fixture.settings(format, export, false)).kid());
            var pem = SigningKeys.initialize(fixture.settings("PEM", export, true));
            assertEquals(first.kid(), pem.kid()); verify(pem);
            assertNotNull(JWKSet.parse(Files.readString(export)).getKeys().getFirst().getX509CertChain());
            var bare = SigningKeys.initialize(fixture.settings("PEM", export, false));
            assertEquals(first.kid(), bare.kid());
            assertNull(JWKSet.parse(Files.readString(export)).getKeys().getFirst().getX509CertChain());
        }
    }
    @Test void keystoreAliasesMissingFilesDistinctPasswordsAndFormatOptionsFailClosed() throws Exception {
        var fixture = new SigningTestMaterial(directory.resolve("keys"), "JKS", "RSA", 2048);
        Path export = Files.writeString(directory.resolve("jwks.json"), "prior");
        var reference = new SecretReference("file:" + fixture.secret);
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(new SigningSettings("provided", null,
                new SigningSettings.Provided("PKCS12", fixture.storePath, "signer", reference, null, null, export))));
        for (String alias : List.of("missing", "public")) {
            fixture.store.setCertificateEntry("public", fixture.store.getCertificate("signer"));
            try (var output = Files.newOutputStream(fixture.storePath)) { fixture.store.store(output, SigningTestMaterial.PASSWORD.toCharArray()); }
            var settings = new SigningSettings("provided", null, new SigningSettings.Provided("JKS", fixture.storePath, alias, reference, null, null, export));
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(settings));
            assertEquals("prior", Files.readString(export));
        }
        Path distinct = Files.writeString(directory.resolve("key-password"), "different-synthetic-key-password");
        var privateKey = fixture.store.getKey("signer", SigningTestMaterial.PASSWORD.toCharArray());
        fixture.store.setKeyEntry("signer", privateKey, "different-synthetic-key-password".toCharArray(), fixture.store.getCertificateChain("signer"));
        try (var output = Files.newOutputStream(fixture.storePath)) { fixture.store.store(output, SigningTestMaterial.PASSWORD.toCharArray()); }
        var settings = new SigningSettings("provided", null, new SigningSettings.Provided("JKS", fixture.storePath, "signer", reference,
                new SecretReference("file:" + distinct), null, export));
        verify(SigningKeys.initialize(settings));
        var absent = new SigningSettings("provided", null, new SigningSettings.Provided("PEM", directory.resolve("absent.pem"), null, null, null, null, export));
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(absent));
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(new SigningSettings("provided", null,
                new SigningSettings.Provided("PKCS12", fixture.storePath, "signer", reference, null, null, export))));
        for (var invalid : List.of(
                new SigningSettings.Provided(null, fixture.pem, null, null, null, null, export),
                new SigningSettings.Provided("PEM", null, null, null, null, null, export),
                new SigningSettings.Provided("PEM", fixture.pem, "alias", null, null, null, export),
                new SigningSettings.Provided("PEM", fixture.pem, null, reference, null, null, export),
                new SigningSettings.Provided("JKS", fixture.storePath, null, reference, null, null, export),
                new SigningSettings.Provided("JKS", fixture.storePath, "signer", reference, null, fixture.certificate, export)))
            assertThrows(IllegalArgumentException.class, () -> new SigningSettings("provided", null, invalid));
        Path targetDirectory = Files.createDirectory(directory.resolve("target-directory"));
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(fixture.settings("PEM", targetDirectory, false)));
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(fixture.settings("PEM", directory.resolve("missing/keys.json"), false)));
    }
    @Test void invalidWeakNonRsaAndMismatchedMaterialNeverReplacesExport() throws Exception {
        Path export = Files.writeString(directory.resolve("jwks.json"), "prior");
        for (var specification : List.of(List.of("RSA", "1024"), List.of("EC", "256"))) {
            var fixture = new SigningTestMaterial(directory.resolve(specification.getFirst()), "PKCS12", specification.getFirst(), Integer.parseInt(specification.get(1)));
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(fixture.settings("PKCS12", export, false)));
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(fixture.settings("PEM", export, false)));
            assertEquals("prior", Files.readString(export));
        }
        var good = new SigningTestMaterial(directory.resolve("good"), "JKS", "RSA", 2048);
        var other = new SigningTestMaterial(directory.resolve("other"), "PKCS12", "RSA", 2048);
        Files.copy(other.certificate, good.certificate, StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(good.settings("PEM", export, true)));
        Files.writeString(good.secret, "wrong-secret");
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(good.settings("JKS", export, false)));
        for (String pem : List.of("malformed", "-----BEGIN RSA PRIVATE KEY-----\nprivate-marker\n-----END RSA PRIVATE KEY-----", "-----BEGIN ENCRYPTED PRIVATE KEY-----\na\n-----END ENCRYPTED PRIVATE KEY-----", "-----BEGIN PUBLIC KEY-----\na\n-----END PUBLIC KEY-----")) {
            Files.writeString(good.pem, pem);
            var error = assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(good.settings("PEM", export, false)));
            assertFalse(error.toString().contains("private-marker")); assertNull(error.getCause());
        }
        assertEquals("prior", Files.readString(export));
    }
    @Test void publicExportNeverOverwritesProvidedInputs() throws Exception {
        var fixture = new SigningTestMaterial(directory.resolve("inputs"), "JKS", "RSA", 2048);
        for (var input : List.of(fixture.storePath, fixture.pem, fixture.certificate, fixture.secret)) {
            byte[] original = Files.readAllBytes(input);
            String format = input.equals(fixture.storePath) || input.equals(fixture.secret) ? "JKS" : "PEM";
            var settings = fixture.settings(format, input.getParent().resolve(".").resolve(input.getFileName()), true);
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(settings), input.toString());
            assertArrayEquals(original, Files.readAllBytes(input));
        }
        // Different paths to one input must also be protected where links are available.
        Path linkedParent = directory.resolve("linked-inputs");
        try { Files.createSymbolicLink(linkedParent, fixture.storePath.getParent()); }
        catch (UnsupportedOperationException | java.io.IOException unavailable) { return; }
        byte[] original = Files.readAllBytes(fixture.pem);
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(fixture.settings("PEM", linkedParent.resolve(fixture.pem.getFileName()), false)));
        assertArrayEquals(original, Files.readAllBytes(fixture.pem));
    }
    @Test void secretReferencesAndSigningWarningsAreSafe() throws Exception {
        assertArrayEquals(" secret ".toCharArray(), new SecretReference("env:SELECTED").resolve(name -> name.equals("SELECTED") ? " secret " : null));
        Path secret = Files.writeString(directory.resolve("secret"), " secret \r\n");
        assertArrayEquals(" secret ".toCharArray(), new SecretReference("file:" + secret).resolve());
        for (String ref : List.of("literal-password-marker", "env:MISSING", "file:relative", "vault:unknown", "file:" + directory.resolve("absent"))) {
            var error = assertThrows(IllegalArgumentException.class, () -> new SecretReference(ref).resolve(name -> null));
            assertFalse(error.toString().contains("literal-password-marker"));
        }
        assertThrows(IllegalArgumentException.class, () -> new SecretReference("env:EMPTY").resolve(name -> ""));
        Files.writeString(secret, "\n");
        assertThrows(IllegalArgumentException.class, () -> new SecretReference("file:" + secret).resolve());
        assertFalse(new SecretReference("literal-password-marker").toString().contains("literal-password-marker"));
        var fixture = new SigningTestMaterial(directory.resolve("warnings"), "PKCS12", "RSA", 2048);
        var expiry = fixture.certificate().getNotAfter().toInstant();
        var messages = new ArrayList<String>();
        var logger = Logger.getLogger(SigningKeys.class.getName());
        var handler = new Handler() { public void publish(LogRecord r) { messages.add(r.getMessage()); } public void flush() {} public void close() {} };
        logger.addHandler(handler);
        try {
            for (var now : List.of(expiry.plusSeconds(1), expiry.minus(Duration.ofDays(30)), expiry.minus(Duration.ofDays(30)).minusSeconds(1))) {
                SigningKeys.initialize(fixture.settings("PKCS12", directory.resolve("warn.json"), false), Clock.fixed(now, ZoneOffset.UTC), new SigningKeys.FileOperations());
            }
            assertEquals(2, messages.size());
            assertFalse(messages.toString().contains(SigningTestMaterial.PASSWORD));
            assertFalse(messages.toString().contains("PRIVATE KEY"));
        } finally { logger.removeHandler(handler); }
    }
    private void verify(SigningKeySource source) throws Exception {
        byte[] bytes = "synthetic challenge".getBytes();
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(source.publicKey()); verifier.update(bytes);
        assertTrue(verifier.verify(source.signRs256(bytes)));
    }
}
