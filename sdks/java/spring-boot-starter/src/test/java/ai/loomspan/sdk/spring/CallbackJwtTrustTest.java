package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.*;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static ai.loomspan.sdk.spring.CallbackJwtDecoderTest.*;
import static org.assertj.core.api.Assertions.*;

class CallbackJwtTrustTest {
    @TempDir Path directory;
    static final KeyPair OWN = pair(), OLD = pair(), NEXT = pair();

    @Test void ownTrustUsesStartupSignerWithoutReadingOrSchedulingInBothSigningModes() throws Exception {
        for (String mode : List.of("colocated", "provided")) {
            var properties = new SigningProperties(); properties.setMode(mode);
            if (mode.equals("colocated")) properties.getColocated().setKeyDirectory(directory);
            else {
                Path pem = directory.resolve("signer.pem");
                Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n"
                        + Base64.getEncoder().encodeToString(OWN.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
                properties.getProvided().setFormat("PEM"); properties.getProvided().setKeyLocation(pem);
                properties.getProvided().setJwksExport(directory.resolve("provided.json"));
            }
            SigningKeySource signer = SigningKeys.initialize(properties.settings());
            try (var trust = new CallbackJwtTrust(signer, new CallbackProperties(), path -> {
                throw new AssertionError("immutable own trust must not read");
            }, System::nanoTime, true)) {
                assertThat(trust.snapshot().keys()).containsOnlyKeys(signer.kid());
                assertThat(trust.snapshot().keys().get(signer.kid())).isEqualTo(signer.publicKey());
                var header = headers(); header.put("kid", signer.kid());
                var base64 = Base64.getUrlEncoder().withoutPadding();
                String input = base64.encodeToString(JSONObjectUtils.toJSONString(header).getBytes(StandardCharsets.UTF_8))
                        + "." + base64.encodeToString(JSONObjectUtils.toJSONString(claims()).getBytes(StandardCharsets.UTF_8));
                String credential = input + "." + base64.encodeToString(signer.signRs256(input.getBytes(StandardCharsets.US_ASCII)));
                assertThat(decoder(trust).decode(credential).getSubject()).isEqualTo("alice");
                trust.forKid("unknown"); trust.reload();
            }
        }
    }

    @Test void mergesReplacesRemovesAndNeverRestoresOldKeysFromAnUnknownKidCache() throws Exception {
        Path file = write(jwks(key("old", OLD)));
        try (var trust = trust(file)) {
            accepts(trust, "own", OWN); accepts(trust, "old", OLD);
            Files.writeString(file, jwks(key("next", NEXT))); trust.reload();
            accepts(trust, "own", OWN); accepts(trust, "next", NEXT); rejects(trust, "old", OLD);
            assertThat(trust.forKid("old").keys()).containsOnlyKeys("own", "next");
            Files.writeString(file, jwks(key("own", OWN))); trust.reload();
            accepts(trust, "own", OWN);
            Files.writeString(file, jwks(key("own", OLD))); trust.reload();
            assertThat(trust.snapshot().keys()).isEmpty(); rejects(trust, "own", OWN);
        }
    }

    @Test void missingMalformedEmptyAndUnreadableOverlapWithdrawAllTrustAndCorrectedFileRecovers() throws Exception {
        Path file = directory.resolve("overlap.json");
        try (var trust = trust(file)) {
            assertThat(trust.snapshot().keys()).isEmpty(); rejects(trust, "own", OWN);
            Files.writeString(file, jwks(key("old", OLD))); trust.reload();
            accepts(trust, "own", OWN); accepts(trust, "old", OLD);
            for (String broken : List.of("invalid", "", "{\"keys\":[]}", "{\"keys\":{}}", "{\"keys\":[17]}")) {
                Files.writeString(file, broken); trust.reload();
                assertThat(trust.snapshot().keys()).isEmpty(); rejects(trust, "own", OWN); rejects(trust, "old", OLD);
                Files.writeString(file, jwks(key("old", OLD))); trust.reload(); accepts(trust, "old", OLD);
            }
            Files.delete(file); trust.reload(); assertThat(trust.snapshot().keys()).isEmpty();
            Files.writeString(file, jwks(key("old", OLD))); trust.reload(); accepts(trust, "own", OWN);
        }
        var unreadable = new AtomicBoolean(false);
        try (var trust = new CallbackJwtTrust(source(OWN, "own"), properties(file), path -> {
            if (unreadable.get()) throw new IOException("synthetic confidential parser text");
            return Files.readAllBytes(path);
        }, System::nanoTime, false)) {
            accepts(trust, "own", OWN); unreadable.set(true); trust.reload();
            assertThat(trust.snapshot().keys()).isEmpty(); unreadable.set(false); trust.reload(); accepts(trust, "own", OWN);
        }
    }

    @Test void rejectsWholeCandidateForEveryMalformedKeyAndInvalidUtf8() throws Exception {
        List<Map<String, Object>> invalid = new ArrayList<>();
        for (var entry : Map.<String, Object>of("kty", "EC", "kid", " ", "use", "enc", "alg", "RS512", "n", "!", "e", "AA").entrySet()) {
            var candidate = key("bad", OLD); candidate.put(entry.getKey(), entry.getValue()); invalid.add(candidate);
        }
        for (String field : List.of("d", "p", "q", "dp", "dq", "qi", "oth")) {
            var candidate = key("bad", OLD); candidate.put(field, null); invalid.add(candidate);
        }
        for (String field : List.of("kid", "n", "e", "kty")) {
            var candidate = key("bad", OLD); candidate.remove(field); invalid.add(candidate);
        }
        for (var number : List.of(java.math.BigInteger.valueOf(2), java.math.BigInteger.valueOf(3),
                ((RSAPublicKey) OLD.getPublic()).getModulus().subtract(java.math.BigInteger.ONE))) {
            var candidate = key("bad", OLD); candidate.put("n", unsigned(number)); invalid.add(candidate);
        }
        for (var number : List.of(java.math.BigInteger.ONE, java.math.BigInteger.valueOf(4),
                ((RSAPublicKey) OLD.getPublic()).getModulus())) {
            var candidate = key("bad", OLD); candidate.put("e", unsigned(number)); invalid.add(candidate);
        }
        Path file = write(jwks(key("old", OLD)));
        try (var trust = trust(file)) {
            for (Map<String, Object> bad : invalid) {
                Files.writeString(file, jwks(key("old", OLD), bad)); trust.reload();
                assertThat(trust.snapshot().keys()).as("whole-file rejection for %s", bad.keySet()).isEmpty();
            }
            Files.writeString(file, jwks(key("old", OLD), key("old", NEXT))); trust.reload();
            assertThat(trust.snapshot().keys()).isEmpty();
            Files.write(file, new byte[]{(byte) 0xc3, (byte) 0x28}); trust.reload();
            assertThat(trust.snapshot().keys()).isEmpty();
        }
    }

    @Test void comparesBytesRatherThanFileMetadataAndAvoidsUnchangedPublication() throws Exception {
        Path file = write(jwks(key("old", OLD))); FileTime modified = Files.getLastModifiedTime(file);
        try (var trust = trust(file)) {
            var before = trust.snapshot(); trust.reload(); assertThat(trust.snapshot()).isSameAs(before);
            Files.writeString(file, jwks(key("next", NEXT))); Files.setLastModifiedTime(file, modified);
            trust.reload(); assertThat(trust.snapshot()).isNotSameAs(before);
            assertThat(trust.snapshot().keys()).containsOnlyKeys("own", "next");
            assertThatThrownBy(() -> trust.snapshot().keys().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Test void unknownKidsReloadImmediatelyButConcurrentFloodIsGloballyLimited() throws Exception {
        Path file = write(jwks(key("old", OLD))); var reads = new AtomicInteger(); var ticker = new AtomicLong();
        try (var trust = new CallbackJwtTrust(source(OWN, "own"), properties(file), path -> {
            reads.incrementAndGet(); return Files.readAllBytes(path);
        }, ticker::get, false)) {
            trust.forKid("own"); assertThat(reads).hasValue(1);
            Files.writeString(file, jwks(key("next", NEXT))); trust.forKid("next");
            assertThat(reads).hasValue(2); accepts(trust, "next", NEXT);
            try (var executor = Executors.newFixedThreadPool(8)) {
                var tasks = new ArrayList<Future<?>>();
                for (int i = 0; i < 50; i++) { final String kid = "unknown-" + i; tasks.add(executor.submit(() -> trust.forKid(kid))); }
                for (var task : tasks) task.get(5, TimeUnit.SECONDS);
            }
            assertThat(reads).hasValue(2); ticker.set(TimeUnit.SECONDS.toNanos(1)); trust.forKid("unknown");
            assertThat(reads).hasValue(3);
        }
    }

    @Test void concurrentReadersObserveOnlyCompleteSnapshots() throws Exception {
        var content = new AtomicReference<>(jwks(key("old", OLD)).getBytes(StandardCharsets.UTF_8));
        Path file = directory.resolve("seam.json");
        try (var trust = new CallbackJwtTrust(source(OWN, "own"), properties(file), path -> content.get(), System::nanoTime, false);
             var executor = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1); var readers = new ArrayList<Future<?>>();
            for (int i = 0; i < 3; i++) readers.add(executor.submit(() -> {
                try { start.await(); } catch (InterruptedException error) { throw new AssertionError(error); }
                for (int j = 0; j < 10000; j++) assertThat(trust.snapshot().keys().keySet())
                        .isIn(Set.of("own", "old"), Set.of("own", "next"), Set.of());
            }));
            start.countDown();
            for (int i = 0; i < 100; i++) {
                content.set((i % 3 == 0 ? "invalid" : jwks(key(i % 2 == 0 ? "old" : "next", i % 2 == 0 ? OLD : NEXT)))
                        .getBytes(StandardCharsets.UTF_8)); trust.reload();
            }
            for (var reader : readers) reader.get(10, TimeUnit.SECONDS);
        }
    }

    @Test void pollingDetectsWithdrawalAndRecoveryAndCloseStopsFutureReads() throws Exception {
        var reads = new AtomicInteger(); var content = new AtomicReference<>(jwks(key("old", OLD)).getBytes(StandardCharsets.UTF_8));
        var trust = new CallbackJwtTrust(source(OWN, "own"), properties(directory.resolve("poll.json")), path -> {
            reads.incrementAndGet(); return content.get();
        }, System::nanoTime, true);
        try {
            content.set("invalid".getBytes(StandardCharsets.UTF_8)); eventually(() -> trust.snapshot().keys().isEmpty());
            content.set(jwks(key("next", NEXT)).getBytes(StandardCharsets.UTF_8));
            eventually(() -> trust.snapshot().keys().containsKey("next"));
        } finally { trust.close(); }
        int stopped = reads.get(); trust.reload(); trust.forKid("unknown");
        // Advance past one poll interval to prove the daemon was cancelled, not merely idle between ticks.
        var timeout = new CountDownLatch(1); assertThat(timeout.await(1200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(reads).hasValue(stopped);
    }

    private static void eventually(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) new CountDownLatch(1).await(20, TimeUnit.MILLISECONDS);
        assertThat(condition.getAsBoolean()).isTrue();
    }
    private static String unsigned(java.math.BigInteger value) {
        byte[] bytes = value.toByteArray(); if (bytes[0] == 0) bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private CallbackJwtTrust trust(Path file) { return new CallbackJwtTrust(source(OWN, "own"), properties(file), Files::readAllBytes, System::nanoTime, false); }
    private static CallbackProperties properties(Path file) { var p = new CallbackProperties(); p.setOverlapJwks(file.toString()); return p; }
    private Path write(String value) throws Exception { Path file = directory.resolve("overlap.json"); Files.writeString(file, value); return file; }
    private static Map<String, Object> key(String kid, KeyPair pair) { return new LinkedHashMap<>(new RSAKey.Builder((RSAPublicKey) pair.getPublic()).keyID(kid).build().toJSONObject()); }
    @SafeVarargs private static String jwks(Map<String, Object>... keys) { return JSONObjectUtils.toJSONString(Map.of("keys", List.of(keys))); }
    private static CallbackJwtDecoder decoder(CallbackJwtTrust trust) { return new CallbackJwtDecoder(trust, "issuer", "receiver", Clock.fixed(NOW, ZoneOffset.UTC)); }
    private static String signed(String kid, KeyPair pair) { var h = headers(); h.put("kid", kid); return token(h, claims(), pair); }
    private static void accepts(CallbackJwtTrust trust, String kid, KeyPair pair) { assertThat(decoder(trust).decode(signed(kid, pair)).getSubject()).isEqualTo("alice"); }
    private static void rejects(CallbackJwtTrust trust, String kid, KeyPair pair) { assertThatThrownBy(() -> decoder(trust).decode(signed(kid, pair))).isInstanceOf(org.springframework.security.oauth2.jwt.BadJwtException.class); }
}
