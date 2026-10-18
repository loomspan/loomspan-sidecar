package ai.loomspan.sidecar.security;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class CurrentFileJwtTrustTest {
    @TempDir Path directory;

    @Test void currentFileWithdrawalRejectsPreviouslyAcceptedTokenAndRecovers() throws Exception {
        Path path = directory.resolve("jwks.json");
        java.security.interfaces.RSAPublicKey key;
        try (var input = Files.newInputStream(Path.of("src/test/resources/fixtures/jwt-public.pem"))) {
            key = (java.security.interfaces.RSAPublicKey) org.springframework.security.converter.RsaKeyConverters.x509().convert(input);
        }
        String valid = "{\"keys\":[" + new com.nimbusds.jose.jwk.RSAKey.Builder(key)
                .keyID("fixture").build().toJSONString() + "]}";
        Files.writeString(path, valid);
        try (var trust = new CurrentFileJwtTrust("file", path)) {
            assertThat(trust.snapshot().keys()).containsOnlyKeys("fixture");
            var decoder = new ExecutionJwtDecoder(trust, "sidecar", java.time.Clock.systemUTC());
            String token = ai.loomspan.sidecar.support.JwtTestTokens.token("owner", java.util.List.of());
            assertThat(decoder.decode(token)).isNotNull();
            Files.writeString(path, "{\"keys\":[]}");
            trust.reload();
            assertThat(trust.snapshot().keys()).isEmpty();
            assertThat(trust.snapshot().ready()).isFalse();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoder.decode(token))
                    .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
            Files.writeString(path, valid);
            trust.reload();
            assertThat(trust.snapshot().keys()).containsOnlyKeys("fixture");
            assertThat(trust.snapshot().ready()).isTrue();
            assertThat(decoder.decode(token)).isNotNull();
        }
    }

    @Test void everyMalformedCandidateWithdrawsWholeFileAndIdenticalRestorationRecovers() throws Exception {
        String valid = Files.readString(Path.of("src/test/resources/fixtures/jwks.json"));
        var parsed = com.nimbusds.jose.util.JSONObjectUtils.parse(valid);
        var original = (java.util.Map<String, Object>) ((java.util.List<?>) parsed.get("keys")).getFirst();
        var invalid = new java.util.ArrayList<String>(java.util.List.of("[]", "{}", "{", "{\"keys\":null}", "{\"keys\":{}}", "{\"keys\":[3]}"));
        for (String name : java.util.List.of("kty", "kid", "n", "e", "use", "alg")) {
            for (Object value : java.util.Arrays.asList(null, 1, "", "!invalid")) {
                if (name.equals("kid") && "!invalid".equals(value)) continue;
                var candidate = new java.util.LinkedHashMap<>(original); candidate.put("kid", "second"); candidate.put(name, value);
                invalid.add(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(java.util.Map.of("keys", java.util.List.of(original, candidate))));
            }
        }
        for (String member : java.util.List.of("d", "p", "q", "dp", "dq", "qi", "oth")) {
            var candidate = new java.util.LinkedHashMap<>(original); candidate.put("kid", "second"); candidate.put(member, null);
            invalid.add(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(java.util.Map.of("keys", java.util.List.of(original, candidate))));
        }
        for (String name : java.util.List.of("kid", "n", "e")) {
            var candidate = new java.util.LinkedHashMap<>(original); candidate.put("kid", "second"); candidate.remove(name);
            invalid.add(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(java.util.Map.of("keys", java.util.List.of(original, candidate))));
        }
        invalid.add(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(java.util.Map.of("keys", java.util.List.of(original, original))));
        var small = new java.util.LinkedHashMap<>(original); small.put("kid", "small"); small.put("n", "AQAB");
        invalid.add(com.nimbusds.jose.util.JSONObjectUtils.toJSONString(java.util.Map.of("keys", java.util.List.of(original, small))));
        Path file = directory.resolve("jwks.json"); Files.writeString(file, valid);
        try (var trust = new CurrentFileJwtTrust("file", file, Files::readAllBytes, System::nanoTime, false)) {
            var second = new java.util.LinkedHashMap<>(original); second.put("kid", "second");
            Files.writeString(file, com.nimbusds.jose.util.JSONObjectUtils.toJSONString(
                    java.util.Map.of("keys", java.util.List.of(original, second))));
            trust.reload();
            assertThat(trust.snapshot().keys()).containsOnlyKeys("fixture", "second");
            var decoder = new ExecutionJwtDecoder(trust, "sidecar", java.time.Clock.systemUTC());
            String token = ai.loomspan.sidecar.support.JwtTestTokens.token("owner", java.util.List.of());
            for (String malformed : invalid) {
                Files.writeString(file, malformed); trust.reload();
                assertThat(trust.snapshot().keys()).as("whole candidate rejected").isEmpty();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> decoder.decode(token))
                        .isInstanceOf(org.springframework.security.oauth2.jwt.JwtException.class);
                Files.writeString(file, valid); trust.reload(); assertThat(decoder.decode(token)).isNotNull();
            }
            byte[] malformedEncoding = valid.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            malformedEncoding[valid.indexOf("fixture")] = (byte) 0xff;
            Files.write(file, malformedEncoding); trust.reload(); assertThat(trust.snapshot().keys()).isEmpty();
            Files.delete(file); trust.reload(); assertThat(trust.snapshot().ready()).isFalse();
            Files.writeString(file, valid); trust.reload(); assertThat(trust.snapshot().ready()).isTrue();
            var ignored = new java.util.LinkedHashMap<>(original); ignored.put("x5c", java.util.List.of("expired and malformed")); ignored.put("unknown", 123);
            Files.writeString(file, com.nimbusds.jose.util.JSONObjectUtils.toJSONString(java.util.Map.of("keys", java.util.List.of(ignored)))); trust.reload();
            assertThat(decoder.decode(token)).isNotNull();
        }
    }

    @Test void unreadableIoFailsClosedAndRepeatedStateDoesNotReparseOrLog() throws Exception {
        var unreadable = new java.util.concurrent.atomic.AtomicBoolean();
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CurrentFileJwtTrust.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        byte[] bytes = Files.readAllBytes(Path.of("src/test/resources/fixtures/jwks.json"));
        try (var trust = new CurrentFileJwtTrust("file", directory.resolve("not-used"), path -> {
            if (unreadable.get()) throw new java.io.IOException("private member and secret must never be logged"); return bytes;
        }, System::nanoTime, false)) {
            trust.reload(); assertThat(appender.list).hasSize(1);
            unreadable.set(true); trust.reload(); assertThat(trust.snapshot().keys()).isEmpty();
            trust.reload(); assertThat(appender.list).hasSize(2);
            unreadable.set(false); trust.reload(); assertThat(trust.snapshot().ready()).isTrue();
            assertThat(appender.list).hasSize(3);
            assertThat(appender.list).allSatisfy(event -> assertThat(event.getFormattedMessage())
                    .contains("mode=file", "path=", "kids=", "lastLoadTime=", "state=").doesNotContain("secret", "private member"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @Test void globalUnknownKidLimitDoesNotDelayOrdinaryContentDetection() throws Exception {
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var tick = new java.util.concurrent.atomic.AtomicLong();
        var bytes = new java.util.concurrent.atomic.AtomicReference<>(Files.readAllBytes(Path.of("src/test/resources/fixtures/jwks.json")));
        try (var trust = new CurrentFileJwtTrust("file", directory.resolve("jwks.json"), path -> {
            reads.incrementAndGet(); return bytes.get();
        }, tick::get, false)) {
            trust.forKid("fixture"); trust.forKid(null); trust.forKid(" "); assertThat(reads.get()).isEqualTo(1);
            try (var pool = java.util.concurrent.Executors.newFixedThreadPool(8)) {
                var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for (int i=0;i<80;i++) { final String kid="unknown"+i; tasks.add(pool.submit(() -> trust.forKid(kid))); }
                for (var task : tasks) task.get();
            }
            assertThat(reads.get()).isEqualTo(2);
            tick.set(999999999); trust.forKid("unknown"); assertThat(reads.get()).isEqualTo(2);
            tick.incrementAndGet(); trust.forKid("unknown"); assertThat(reads.get()).isEqualTo(3);
            bytes.set("{\"keys\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            trust.reload(); assertThat(trust.snapshot().keys()).isEmpty(); assertThat(reads.get()).isEqualTo(4);
            bytes.set(Files.readAllBytes(Path.of("src/test/resources/fixtures/jwks.json")));
            trust.reload(); assertThat(trust.snapshot().ready()).isTrue();
            bytes.set("malformed".getBytes(java.nio.charset.StandardCharsets.UTF_8)); tick.addAndGet(1000000000);
            trust.forKid("absent"); assertThat(trust.snapshot().keys()).isEmpty();
            String added = Files.readString(Path.of("src/test/resources/fixtures/jwks.json")).replace("fixture", "new-key");
            bytes.set(added.getBytes(java.nio.charset.StandardCharsets.UTF_8)); tick.addAndGet(1000000000);
            var header = java.util.Map.<String, Object>of("alg", "RS256", "kid", "new-key", "typ", "loomspan-execution+jwt");
            String rotated = ai.loomspan.sidecar.support.JwtTestTokens.rawToken(header, JwtSecurityConfigurationTest.claims());
            assertThat(new ExecutionJwtDecoder(trust, "loomspan-sidecar", java.time.Clock.systemUTC()).decode(rotated)).isNotNull();
            assertThat(trust.snapshot().keys()).containsOnlyKeys("new-key");
        }
    }

    @Test void atomicReadersSeeCompleteSnapshotsAndEqualMetadataReplacementIsDetected() throws Exception {
        String valid = Files.readString(Path.of("src/test/resources/fixtures/jwks.json"));
        var root = com.nimbusds.jose.util.JSONObjectUtils.parse(valid);
        var original = (java.util.Map<String, Object>) ((java.util.List<?>) root.get("keys")).getFirst();
        var first = new java.util.LinkedHashMap<>(original); first.put("kid", "key-a1");
        var second = new java.util.LinkedHashMap<>(original); second.put("kid", "key-a2");
        valid = com.nimbusds.jose.util.JSONObjectUtils.toJSONString(java.util.Map.of("keys", java.util.List.of(first, second)));
        final String initial = valid;
        String other = valid.replace("key-a", "key-b"); // same byte length
        Path file = directory.resolve("jwks.json"); Files.writeString(file, valid);
        var mtime = Files.getLastModifiedTime(file);
        try (var trust = new CurrentFileJwtTrust("file", file, Files::readAllBytes, System::nanoTime, false);
                var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> {
                try { for (int i=0;i<100;i++) {
                    Files.writeString(file, i%2 == 0 ? other : initial); Files.setLastModifiedTime(file, mtime); trust.reload();
                }} catch (Exception e) { throw new RuntimeException(e); }
            });
            var reader = pool.submit(() -> {
                for(int i=0;i<10000;i++) assertThat(java.util.Set.of(java.util.Set.of("key-a1", "key-a2"), java.util.Set.of("key-b1", "key-b2"))).contains(trust.snapshot().keys().keySet());
            });
            writer.get(); reader.get(); Files.writeString(file, other); Files.setLastModifiedTime(file, mtime); trust.reload();
            assertThat(trust.snapshot().keys()).containsOnlyKeys("key-b1", "key-b2");
        }
    }
}
