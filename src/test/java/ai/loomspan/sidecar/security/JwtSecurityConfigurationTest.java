package ai.loomspan.sidecar.security;

import ai.loomspan.sidecar.support.JwtTestTokens;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import static org.assertj.core.api.Assertions.*;

class JwtSecurityConfigurationTest {
    private static final Map<String, Object> HEADERS = Map.of("alg", "RS256", "kid", "fixture", "typ", "loomspan-execution+jwt");
    static Map<String, Object> claims() {
        return new LinkedHashMap<>(Map.of("iss", "loomspan-app", "sub", "owner", "roles", List.of("ROLE_ORDER_EDITOR"),
                "exp", Instant.now().plusSeconds(300).getEpochSecond(), "aud", "loomspan-sidecar", "token_use", "loomspan_execution"));
    }
    @Test void validatesSignatureProfileRawShapesAudienceAndFixedSkew() throws Exception {
        try (var trust = new CurrentFileJwtTrust("file", Path.of("src/test/resources/fixtures/jwks.json"))) {
            var decoder = new ExecutionJwtDecoder(trust, "loomspan-sidecar", Clock.systemUTC());
            var jwt = decoder.decode(JwtTestTokens.rawToken(HEADERS, claims()));
            assertThat(jwt.<String>getClaim("iss")).isEqualTo("loomspan-app");
            assertThat(ExecutionOwner.from(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt)).issuer()).isEqualTo("loomspan-app");
            for (String name : List.of("iss", "sub", "roles", "aud", "exp", "token_use")) {
                var missing = claims(); missing.remove(name);
                rejected(decoder, HEADERS, missing);
                for (Object invalid : java.util.Arrays.asList(null, 7, Map.of("x", "y"), List.of(7), true)) {
                    var c = claims(); c.put(name, invalid); rejected(decoder, HEADERS, c);
                }
            }
            for (String name : List.of("iss", "sub")) { var c = claims(); c.put(name, " "); rejected(decoder, HEADERS, c); }
            for (Object invalid : List.of("123", "", List.of("1"))) { var c = claims(); c.put("exp", invalid); rejected(decoder, HEADERS, c); }
            for (String name : List.of("iat", "nbf")) for (Object invalid : java.util.Arrays.asList(null, "123", false, Map.of())) {
                var c = claims(); c.put(name, invalid); rejected(decoder, HEADERS, c);
            }
            for (Object invalid : java.util.Arrays.asList(null, 1, List.of("x"))) { var c = claims(); c.put("jti", invalid); rejected(decoder, HEADERS, c); }
            for (Object invalid : java.util.Arrays.asList(null, "EXECUTOR", List.of("x", 1))) { var c = claims(); c.put("roles", invalid); rejected(decoder, HEADERS, c); }
            for (Object invalid : java.util.Arrays.asList(null, "wrong", List.of("loomspan-sidecar", 1))) { var c = claims(); c.put("aud", invalid); rejected(decoder, HEADERS, c); }
            for (String name : List.of("kid", "typ", "alg")) {
                var h = new LinkedHashMap<>(HEADERS); h.remove(name); rejected(decoder, h, claims());
                for (Object invalid : java.util.Arrays.asList(null, "wrong", "", " ", 1, List.of("x"))) {
                    h.put(name, invalid); rejected(decoder, h, claims());
                }
            }
            for (Object invalid : List.of(Long.MAX_VALUE, 1e308)) { var c = claims(); c.put("exp", invalid); rejected(decoder, HEADERS, c); }
            var expired = claims(); expired.put("exp", Instant.now().minusSeconds(61).getEpochSecond()); rejected(decoder, HEADERS, expired);
            var skew = claims(); skew.put("exp", Instant.now().minusSeconds(30).getEpochSecond());
            assertThat(decoder.decode(JwtTestTokens.rawToken(HEADERS, skew))).isNotNull();
            var fractional = claims(); fractional.put("exp", Instant.now().plusSeconds(60).getEpochSecond() + 0.25);
            fractional.put("nbf", Instant.now().plusSeconds(30).getEpochSecond() + 0.5);
            fractional.put("iat", Instant.now().minusSeconds(10).getEpochSecond() + 0.125);
            fractional.put("aud", List.of("extra", "loomspan-sidecar")); fractional.put("roles", List.of());
            assertThat(decoder.decode(JwtTestTokens.rawToken(HEADERS, fractional)).getExpiresAt().getNano()).isEqualTo(250000000);
            var future = claims(); future.put("nbf", Instant.now().plusSeconds(120).getEpochSecond()); rejected(decoder, HEADERS, future);
            String signed = JwtTestTokens.rawToken(HEADERS, claims());
            String[] segments = signed.split("\\."); segments[2] = (segments[2].startsWith("A") ? "B" : "A") + segments[2].substring(1);
            assertThatThrownBy(() -> decoder.decode(String.join(".", segments))).isInstanceOf(JwtException.class);
        }
    }
    @Test void untrustedKeyWithTheSameKidAndWrongAlgorithmAreRejected() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var path = java.nio.file.Files.createTempFile("untrusted-", ".json");
        try {
            java.nio.file.Files.writeString(path, "{\"keys\":[" + new com.nimbusds.jose.jwk.RSAKey.Builder(
                    (java.security.interfaces.RSAPublicKey) pair.getPublic()).keyID("fixture").build().toJSONString() + "]}");
            try (var trust = new CurrentFileJwtTrust("file", path)) {
                rejected(new ExecutionJwtDecoder(trust, "loomspan-sidecar", Clock.systemUTC()), HEADERS, claims());
            }
            try (var trust = new CurrentFileJwtTrust("file", Path.of("src/test/resources/fixtures/jwks.json"))) {
                var header = new LinkedHashMap<>(HEADERS); header.put("alg", "RS384");
                rejected(new ExecutionJwtDecoder(trust, "loomspan-sidecar", Clock.systemUTC()), header, claims());
            }
        } finally { java.nio.file.Files.deleteIfExists(path); }
    }

    @Test void tokenUrlsNeverFetchAndIssuerIsOnlySignedOwnership() throws Exception {
        var count = new java.util.concurrent.atomic.AtomicInteger();
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { count.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close(); }); server.start();
        try (var trust = new CurrentFileJwtTrust("file", Path.of("src/test/resources/fixtures/jwks.json"))) {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            var h = new LinkedHashMap<>(HEADERS); h.put("jku", url); h.put("x5u", url);
            var c = claims(); c.put("iss", url);
            assertThat(new ExecutionJwtDecoder(trust, "loomspan-sidecar", Clock.systemUTC()).decode(JwtTestTokens.rawToken(h, c)).getSubject()).isEqualTo("owner");
            assertThat(count.get()).isZero();
        } finally { server.stop(0); }
    }
    @Test void rolesAreLiteralAndOnlyAudienceIsConfigurable() {
        var properties = new SidecarJwtProperties(); assertThat(properties.getAudience()).isEqualTo("loomspan-sidecar");
        properties.setAudience(" "); assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("x").header("alg", "none").claim("sub", "owner")
                .claim("roles", List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR")).build();
        var configuration = new JwtSecurityConfiguration();
        assertThat(configuration.sidecarJwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting(org.springframework.security.core.GrantedAuthority::getAuthority).containsExactlyInAnyOrder("ROLE_ORDER_EDITOR", "ORDER_EDITOR");
        assertThat(configuration.grantedAuthorityDefaults().getRolePrefix()).isEmpty();
    }
    private void rejected(org.springframework.security.oauth2.jwt.JwtDecoder decoder, Map<String, Object> headers, Map<String, Object> claims) {
        assertThatThrownBy(() -> decoder.decode(JwtTestTokens.rawToken(headers, claims))).isInstanceOf(JwtException.class);
    }
}
