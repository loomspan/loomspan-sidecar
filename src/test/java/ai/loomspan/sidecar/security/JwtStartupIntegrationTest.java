package ai.loomspan.sidecar.security;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.support.JwtTestTokens;
import com.nimbusds.jose.jwk.RSAKey;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtStartupIntegrationTest {
    @TempDir Path temporaryDirectory;

    @ParameterizedTest(name = "explicit JWKS = {0}")
    @ValueSource(booleans = {false, true})
    void propertyBoundRemoteKeyModesAuthenticateHttpRequests(boolean explicitJwks) throws Exception {
        RSAPublicKey key;
        try (var input = new ClassPathResource("fixtures/jwt-public.pem").getInputStream()) {
            key = (RSAPublicKey) RsaKeyConverters.x509().convert(input);
        }
        String jwks = "{\"keys\":[" + new RSAKey.Builder(key).build().toJSONString() + "]}";
        var discoveryRequests = new AtomicInteger();
        var keyRequests = new AtomicInteger();
        var provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String issuer = "http://127.0.0.1:" + provider.getAddress().getPort();
        provider.createContext("/.well-known/openid-configuration", exchange -> {
            discoveryRequests.incrementAndGet();
            byte[] body = ("{\"issuer\":\"" + issuer + "\",\"jwks_uri\":\"" + issuer + "/jwks\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        provider.createContext("/jwks", exchange -> {
            keyRequests.incrementAndGet();
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        provider.start();
        var properties = new LinkedHashMap<String, String>();
        properties.put("issuer-uri", issuer);
        properties.put("public-key-location", "");
        properties.put("clock-skew", "0s");
        if (explicitJwks) properties.put("jwk-set-uri", issuer + "/jwks");
        try (var context = application(WebApplicationType.SERVLET, properties);
                var client = HttpClient.newHttpClient()) {
            assertThat(discoveryRequests.get()).isEqualTo(explicitJwks ? 0 : 1);
            assertThat(context.getBean(SidecarJwtProperties.class).getClockSkew()).isEqualTo(Duration.ZERO);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String valid = JwtTestTokens.token(issuer, "sidecar", "operator", List.of());
            assertThat(status(client, port, valid)).isEqualTo(200);
            assertThat(keyRequests.get()).isPositive();
            assertThat(status(client, port, null)).isEqualTo(401);
            var invalidTokens = List.of(
                    JwtTestTokens.token(issuer, "wrong", "operator", List.of()),
                    JwtTestTokens.token(issuer + "/wrong", "sidecar", "operator", List.of()),
                    JwtTestTokens.token(issuer, "sidecar", " ", List.of()),
                    JwtTestTokens.tokenWithoutExpiration(issuer, "sidecar", "operator", List.of()),
                    JwtTestTokens.token(issuer, "sidecar", "operator", List.of(), -30));
            for (String token : invalidTokens) {
                assertThat(status(client, port, token)).isEqualTo(401);
            }
            String[] segments = valid.split("\\.");
            segments[2] = (segments[2].startsWith("A") ? "B" : "A") + segments[2].substring(1);
            assertThat(status(client, port, String.join(".", segments))).isEqualTo(401);
            assertThat(discoveryRequests.get()).isEqualTo(explicitJwks ? 0 : 1);
        } finally {
            provider.stop(0);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "issuer-uri, '', JWT issuer-uri",
            "audience, '', JWT issuer-uri",
            "roles-claim, '', JWT issuer-uri",
            "clock-skew, -1s, clock-skew must not be negative",
            "jwk-set-uri, http://127.0.0.1:9/jwks, Configure at most one"
    })
    void invalidBoundPolicyPreventsStartup(String name, String value, String message) {
        assertThatThrownBy(() -> {
            try (var ignored = application(WebApplicationType.NONE, Map.of(name, value))) {
                // Close even if a regression unexpectedly permits startup.
            }
        }).hasStackTraceContaining("sidecarJwtDecoder").hasStackTraceContaining(message);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unusablePublicKeyPreventsStartup(boolean malformed) throws Exception {
        Path key = temporaryDirectory.resolve("public.pem");
        if (malformed) Files.writeString(key, "not a public key");
        assertThatThrownBy(() -> {
            try (var ignored = application(WebApplicationType.NONE,
                    Map.of("public-key-location", key.toUri().toString()))) {
                // Close even if a regression unexpectedly permits startup.
            }
        }).hasStackTraceContaining("sidecarJwtDecoder")
                .hasRootCauseInstanceOf(malformed ? IllegalArgumentException.class : java.io.FileNotFoundException.class);
    }

    private ConfigurableApplicationContext application(WebApplicationType type, Map<String, String> overrides) {
        var jwt = new LinkedHashMap<>(Map.of(
                "issuer-uri", "https://issuer.test",
                "audience", "sidecar",
                "public-key-location", "classpath:fixtures/jwt-public.pem"));
        jwt.putAll(overrides);
        var arguments = new ArrayList<>(List.of(
                "--server.port=0", "--management.server.port=0",
                "--loomspan.observability.enabled=false",
                "--loomspan-sidecar.storage.database-path=" + temporaryDirectory.resolve("sidecar.db")));
        jwt.forEach((name, value) -> arguments.add("--loomspan-sidecar.auth.jwt." + name + "=" + value));
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(type).run(arguments.toArray(String[]::new));
    }

    private int status(HttpClient client, int port, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/skills"))
                .timeout(Duration.ofSeconds(10)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
