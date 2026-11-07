package ai.loomspan.sdk;

import com.nimbusds.jose.jwk.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        var provided = new SigningSettings.Provided(file, file);
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
            assertTrue(assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(SigningSettings.colocated(directory), files)).getMessage().contains(export.toString()));
            assertEquals("prior complete public export", Files.readString(export));
            try (var paths = Files.list(directory)) { assertEquals(1, paths.count()); }
        }
    }
    @Test void explicitModesAndPathsAreRequired() {
        for (String mode : new String[]{null, "", "unknown"})
            assertThrows(IllegalArgumentException.class, () -> new SigningSettings(mode, null, null));
        assertThrows(IllegalArgumentException.class, () -> new SigningSettings("provided", null, null));
        assertThrows(IllegalArgumentException.class, () -> provided(null, directory.resolve("out")));
        assertThrows(IllegalArgumentException.class, () -> provided(directory.resolve("in"), null));
    }

    @Test void providedPemAcceptsWhitespaceAndRetainsFixedIdentity() throws Exception {
        String original = fixture();
        var expected = JWKSet.parse(resource("public-jwks.json")).getKeys().getFirst();
        Path pem = directory.resolve("signer.pem"), export = directory.resolve("public.json");
        for (String ending : List.of("\n", "\r\n", "\r")) {
            String content = " \t\f\013" + ending + original.replace("\n", ending) + " \t\f\013" + ending;
            Files.writeString(pem, content);
            var first = SigningKeys.initialize(provided(pem, export));
            var second = SigningKeys.initialize(provided(pem, export));
            assertEquals(expected.getKeyID(), first.kid()); assertEquals(first.kid(), second.kid());
            verify(first);
            assertEquals(expected.toJSONObject(), JWKSet.parse(Files.readString(export)).getKeys().getFirst().toJSONObject());
            assertEquals(content, Files.readString(pem));
            assertFalse(first.toString().contains("PRIVATE KEY"));
        }
        var running = SigningKeys.initialize(provided(pem, export));
        Files.writeString(pem, pem(pair("RSA", 2048).getPrivate().getEncoded()));
        verify(running);
        assertEquals(expected.getKeyID(), running.kid());
        assertNotEquals(running.kid(), SigningKeys.initialize(provided(pem, export)).kid());
    }

    @Test void providedPemRequiresAnEndBoundaryLineAndCompleteBase64Quanta() throws Exception {
        String good = fixture();
        Path input = directory.resolve("signer.pem"), export = directory.resolve("public.json");
        assertTrue(good.contains("="), "fixture exercises required padding");
        for (String malformed : List.of(good.replace("\n-----END", "-----END"), good.replace("=", ""))) {
            Files.writeString(input, malformed);
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export)));
            assertFalse(Files.exists(export));
        }
    }

    @Test void pkcs8OptionalAttributesMustBeStructurallyValid() throws Exception {
        var encoded = Base64.getMimeDecoder().decode(fixture()
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
        Path input = directory.resolve("signer.pem"), export = directory.resolve("public.json");
        String expectedKid = JWKSet.parse(resource("public-jwks.json")).getKeys().getFirst().getKeyID();

        // Attributes SET containing OID 1.2.3 and a UTF8String value.
        Files.writeString(input, pem(withAttributes(encoded, "a00b300906022a0331030c0141")));
        assertEquals(expectedKid, SigningKeys.initialize(provided(input, export)).kid());
        String prior = Files.readString(export);

        // Truncated child TLV, then a complete TLV with the wrong attribute type.
        for (String malformed : List.of("a0020101", "a0020500")) {
            Files.writeString(input, pem(withAttributes(encoded, malformed)));
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export)));
            assertEquals(prior, Files.readString(export));
        }
    }

    @Test void pkcs8RsaEncryptionRejectsNonNullAlgorithmParameters() throws Exception {
        var encoded = Base64.getMimeDecoder().decode(fixture()
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
        var info = PrivateKeyInfo.getInstance(encoded);
        var oid = info.getPrivateKeyAlgorithm().getAlgorithm();
        var innerKey = info.getPrivateKey().getOctets();
        Path input = directory.resolve("signer.pem"), export = directory.resolve("public.json");
        String expectedKid = JWKSet.parse(resource("public-jwks.json")).getKeys().getFirst().getKeyID();
        Files.writeString(input, pem(new PrivateKeyInfo(new AlgorithmIdentifier(oid), innerKey).getEncoded()));
        assertEquals(expectedKid, SigningKeys.initialize(provided(input, export)).kid());
        String prior = Files.readString(export);

        var malformed = new PrivateKeyInfo(new AlgorithmIdentifier(oid, new ASN1Integer(1)), innerKey);
        Files.writeString(input, pem(malformed.getEncoded()));
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export)));
        assertEquals(prior, Files.readString(export));
    }

    @Test void pkcs8OptionalPublicKeyMustMatchTheSigner() throws Exception {
        var encoded = Base64.getMimeDecoder().decode(fixture()
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
        var info = PrivateKeyInfo.getInstance(encoded);
        var expected = (RSAKey) JWKSet.parse(resource("public-jwks.json")).getKeys().getFirst();
        var modulus = expected.getModulus().decodeToBigInteger();
        var exponent = expected.getPublicExponent().decodeToBigInteger();
        var matching = new org.bouncycastle.asn1.pkcs.RSAPublicKey(modulus, exponent).getEncoded();
        var different = new org.bouncycastle.asn1.pkcs.RSAPublicKey(modulus.add(java.math.BigInteger.TWO), exponent).getEncoded();
        Path input = directory.resolve("signer.pem"), export = directory.resolve("public.json");
        Files.writeString(input, pem(new PrivateKeyInfo(info.getPrivateKeyAlgorithm(),
                info.getPrivateKey().getOctets(), null, matching).getEncoded()));
        assertEquals(expected.getKeyID(), SigningKeys.initialize(provided(input, export)).kid());
        String prior = Files.readString(export);

        for (byte[] malformed : List.of(DERNull.INSTANCE.getEncoded(), different)) {
            Files.writeString(input, pem(new PrivateKeyInfo(info.getPrivateKeyAlgorithm(),
                    info.getPrivateKey().getOctets(), null, malformed).getEncoded()));
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export)));
            assertEquals(prior, Files.readString(export));
        }
    }

    private static byte[] withAttributes(byte[] encoded, String tail) {
        // The fixed fixture's outer sequence uses exactly two length octets.
        assertEquals(0x82, encoded[1] & 0xff);
        byte[] attributes = HexFormat.of().parseHex(tail);
        byte[] variant = Arrays.copyOf(encoded, encoded.length + attributes.length);
        int length = variant.length - 4;
        variant[2] = (byte) (length >> 8);
        variant[3] = (byte) length;
        System.arraycopy(attributes, 0, variant, encoded.length, attributes.length);
        return variant;
    }

    @Test void invalidMaterialNeverFallsBackReplacesExportOrDisclosesInput() throws Exception {
        String good = fixture();
        byte[] der = Base64.getMimeDecoder().decode(good.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
        byte[] trailing = Arrays.copyOf(der, der.length + 1);
        String certificate = "-----BEGIN CERTIFICATE-----\nprivate-marker\n-----END CERTIFICATE-----\n";
        String publicKey = "-----BEGIN PUBLIC KEY-----\nprivate-marker\n-----END PUBLIC KEY-----\n";
        var invalid = new ArrayList<>(List.of("", "private-marker", good + good,
                certificate, publicKey, certificate + good, good + certificate, publicKey + good, good + publicKey,
                "private-marker" + good, good + "private-marker",
                good.replace("PRIVATE KEY", "RSA PRIVATE KEY"), good.replace("PRIVATE KEY", "ENCRYPTED PRIVATE KEY"),
                "-----BEGIN PRIVATE KEY-----\n-----END PRIVATE KEY-----",
                good.replace("MII", "M!II"), pem(new byte[]{0x30, 0}), pem(trailing), pem(Arrays.copyOf(der, der.length - 1)),
                pem(pair("RSA", 1024).getPrivate().getEncoded()), pem(pair("EC", 256).getPrivate().getEncoded())));
        Path input = directory.resolve("signer.pem"), export = Files.writeString(directory.resolve("public.json"), "prior");
        for (String content : invalid) {
            Files.writeString(input, content);
            var error = assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export)));
            assertNull(error.getCause()); assertFalse(error.toString().contains("private-marker"));
            assertFalse(error.toString().contains("BEGIN PRIVATE"));
            assertEquals("prior", Files.readString(export)); assertEquals(content, Files.readString(input));
            try (var files = Files.list(directory)) { assertEquals(2, files.count()); }
        }
        Files.delete(input);
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export)));
        assertEquals("prior", Files.readString(export));
    }

    @Test void publicExportNeverOverwritesPrivateInputOrEquivalentFiles() throws Exception {
        Path input = Files.writeString(directory.resolve("signer.pem"), fixture());
        byte[] original = Files.readAllBytes(input);
        Path hardlink = Files.createLink(directory.resolve("hardlink.pem"), input);
        for (Path export : List.of(input, directory.resolve(".").resolve("signer.pem"), hardlink)) {
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export)));
            assertArrayEquals(original, Files.readAllBytes(input));
        }
    }

    @Test void symbolicInputAliasIsProtectedWhenPlatformAllowsLinks() throws Exception {
        Path input = Files.writeString(directory.resolve("signer.pem"), fixture());
        Path link = directory.resolve("symlink.pem");
        try { Files.createSymbolicLink(link, input); }
        catch (UnsupportedOperationException | java.io.IOException unavailable) {
            assumeTrue(false, "Symbolic links unavailable: " + unavailable.getClass().getSimpleName());
        }
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, link)));
        assertEquals(fixture(), Files.readString(input));
    }

    @Test void providedPublicationErrorsPreservePrivateAndPriorPublicMaterial() throws Exception {
        Path input = Files.writeString(directory.resolve("signer.pem"), fixture());
        Path export = Files.writeString(directory.resolve("public.json"), "prior");
        for (int failure = 0; failure < 4; failure++) {
            int stage = failure; int[] moves = {0};
            var files = new SigningKeys.FileOperations() {
                @Override Path temporary(Path parent) throws java.io.IOException {
                    if (stage == 0) throw new AccessDeniedException("synthetic");
                    return super.temporary(parent);
                }
                @Override void write(Path path, String content) throws java.io.IOException {
                    if (stage == 1) throw new AccessDeniedException("synthetic");
                    super.write(path, content);
                }
                @Override void move(Path source, Path target, CopyOption... options) throws java.io.IOException {
                    moves[0]++;
                    assertEquals(Set.of(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING), Set.of(options));
                    if (stage == 2) throw new AccessDeniedException("synthetic");
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "synthetic");
                }
            };
            assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, export), files));
            assertEquals(stage >= 2 ? 1 : 0, moves[0]);
            assertEquals(fixture(), Files.readString(input)); assertEquals("prior", Files.readString(export));
            try (var paths = Files.list(directory)) { assertEquals(2, paths.count()); }
        }
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, directory.resolve("absent/public.json"))));
        assertFalse(Files.exists(directory.resolve("absent")));
        Path target = Files.createDirectory(directory.resolve("target"));
        assertThrows(IllegalArgumentException.class, () -> SigningKeys.initialize(provided(input, target)));
    }

    private static SigningSettings provided(Path input, Path export) {
        return new SigningSettings("provided", null, new SigningSettings.Provided(input, export));
    }
    private static String resource(String name) throws Exception {
        try (var input = SigningKeysTest.class.getResourceAsStream("/signing/" + name)) {
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    private static String fixture() throws Exception { return resource("synthetic-rsa.pem"); }
    private static KeyPair pair(String algorithm, int bits) throws Exception {
        var generator = KeyPairGenerator.getInstance(algorithm); generator.initialize(bits); return generator.generateKeyPair();
    }
    private static String pem(byte[] bytes) {
        return "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(bytes) + "\n-----END PRIVATE KEY-----\n";
    }
    private void verify(SigningKeySource source) throws Exception {
        byte[] bytes = "synthetic challenge".getBytes();
        var verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(source.publicKey()); verifier.update(bytes);
        assertTrue(verifier.verify(source.signRs256(bytes)));
    }
}
