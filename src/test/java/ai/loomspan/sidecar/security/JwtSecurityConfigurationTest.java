package ai.loomspan.sidecar.security;

import java.net.InetSocketAddress;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import java.time.Duration;

import ai.loomspan.sidecar.support.JwtTestTokens;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityConfigurationTest {
    @Test
    void publicKeyModeValidatesSignatureIssuerAudienceLifetimeAndClaims() throws Exception {
        var properties = validProperties();
        var decoder = new JwtSecurityConfiguration().sidecarJwtDecoder(properties);
        var jwt = decoder.decode(JwtTestTokens.token("subject", List.of("EXECUTOR")));
        assertThat(jwt.getIssuer().toString()).isEqualTo("https://issuer.test");
        assertThat(jwt.getAudience()).containsExactly("sidecar");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("EXECUTOR");

        properties.setAudience("different");
        var wrongAudience = new JwtSecurityConfiguration().sidecarJwtDecoder(properties);
        assertThatThrownBy(() -> wrongAudience.decode(JwtTestTokens.token("subject", List.of())))
                .isInstanceOf(JwtValidationException.class);

        assertThatThrownBy(() -> decoder.decode(JwtTestTokens.token(
                "https://other-issuer.test", "sidecar", "subject", List.of())))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(JwtTestTokens.token(
                "https://issuer.test", "sidecar", null, List.of())))
                .isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(JwtTestTokens.tokenWithoutExpiration(
                "https://issuer.test", "sidecar", "subject", List.of())))
                .isInstanceOf(JwtValidationException.class);
        String signed = JwtTestTokens.token("subject", List.of());
        String[] segments = signed.split("\\.");
        segments[2] = (segments[2].startsWith("A") ? "B" : "A") + segments[2].substring(1);
        String tampered = String.join(".", segments);
        assertThatThrownBy(() -> decoder.decode(tampered)).isInstanceOf(RuntimeException.class);

        properties.setAudience("sidecar");
        assertThat(new JwtSecurityConfiguration().sidecarJwtDecoder(properties).decode(
                JwtTestTokens.token("https://issuer.test", "sidecar", "subject", List.of(), -30)))
                .isNotNull();
        properties.setClockSkew(Duration.ZERO);
        var noSkew = new JwtSecurityConfiguration().sidecarJwtDecoder(properties);
        assertThatThrownBy(() -> noSkew.decode(JwtTestTokens.token(
                "https://issuer.test", "sidecar", "subject", List.of(), -30)))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsMissingAndAmbiguousKeyPolicy() {
        var missing = new SidecarJwtProperties();
        assertThatThrownBy(missing::validate).isInstanceOf(IllegalArgumentException.class);
        var ambiguous = validProperties();
        ambiguous.setJwkSetUri("https://issuer.test/jwks");
        assertThatThrownBy(ambiguous::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customRoleClaimAndPrefixStayAligned() {
        var properties = validProperties();
        properties.setRolesClaim("groups");
        properties.setRolePrefix("APP_");
        var jwt = Jwt.withTokenValue("token").header("alg", "none")
                .claim("groups", List.of("EXECUTOR")).build();
        assertThat(new JwtSecurityConfiguration().sidecarJwtAuthenticationConverter(properties)
                .convert(jwt).getAuthorities()).extracting(authority -> authority.getAuthority())
                .contains("APP_EXECUTOR");
        assertThat(new JwtSecurityConfiguration().grantedAuthorityDefaults(properties).getRolePrefix())
                .isEqualTo("APP_");
    }

    @Test
    void discoveryAndExplicitJwksUseTheSameIssuerAndAudienceValidation() throws Exception {
        RSAPublicKey publicKey;
        try (var input = new ClassPathResource("fixtures/jwt-public.pem").getInputStream()) {
            publicKey = (RSAPublicKey) RsaKeyConverters.x509().convert(input);
        }
        String jwks = "{\"keys\":[" + new RSAKey.Builder(publicKey).keyID("fixture").build().toJSONString() + "]}";
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String issuer = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/.well-known/openid-configuration", exchange -> respond(exchange,
                "{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}"));
        server.createContext("/jwks", exchange -> respond(exchange, jwks));
        server.start();
        try {
            var discovery = new SidecarJwtProperties();
            discovery.setIssuerUri(issuer);
            discovery.setAudience("sidecar");
            assertThat(new JwtSecurityConfiguration().sidecarJwtDecoder(discovery)
                    .decode(JwtTestTokens.token(issuer, "sidecar", "subject", List.of())).getSubject())
                    .isEqualTo("subject");

            var explicit = new SidecarJwtProperties();
            explicit.setIssuerUri(issuer);
            explicit.setAudience("sidecar");
            explicit.setJwkSetUri(issuer + "/jwks");
            assertThat(new JwtSecurityConfiguration().sidecarJwtDecoder(explicit)
                    .decode(JwtTestTokens.token(issuer, "sidecar", "subject", List.of())).getSubject())
                    .isEqualTo("subject");
        } finally {
            server.stop(0);
        }
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    private SidecarJwtProperties validProperties() {
        var properties = new SidecarJwtProperties();
        properties.setIssuerUri("https://issuer.test");
        properties.setAudience("sidecar");
        properties.setPublicKeyLocation(new ClassPathResource("fixtures/jwt-public.pem"));
        return properties;
    }
}
