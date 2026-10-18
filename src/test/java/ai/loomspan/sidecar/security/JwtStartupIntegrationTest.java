package ai.loomspan.sidecar.security;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.support.JwtTestTokens;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class JwtStartupIntegrationTest {
    @TempDir Path directory;
    @ParameterizedTest @ValueSource(strings = {"colocated", "file"})
    void automaticSchedulerWithdrawsAndRecoversReadiness(String mode) throws Exception {
        Path file = directory.resolve("jwks.json");
        String valid = Files.readString(Path.of("src/test/resources/fixtures/jwks.json"));
        var settings = new LinkedHashMap<String, String>(); settings.put("loomspan-sidecar.trust.mode", mode);
        settings.put(mode.equals("file") ? "loomspan-sidecar.trust.file.jwks" : "loomspan-sidecar.trust.colocated.key-directory",
                mode.equals("file") ? file.toString() : directory.toString());
        try (var context = application(WebApplicationType.SERVLET, settings); var client = HttpClient.newHttpClient()) {
            int port = ((WebServerApplicationContext)context).getWebServer().getPort();
            int management = port;
            String token = JwtTestTokens.token("loomspan-app", "loomspan-sidecar", "owner", List.of());
            assertThat(get(client, port, "/v1/skills", token).statusCode()).isEqualTo(401);
            assertThat(get(client, management, "/actuator/health/readiness", null).statusCode()).isEqualTo(503);
            assertThat(get(client, management, "/actuator/health/liveness", null).body()).isEqualTo("{\"status\":\"UP\"}");
            Files.writeString(file, valid);
            // Health is snapshot-only: polling it cannot force an unknown-kid refresh.
            awaitStatus(client, port, "/actuator/health/readiness", null, 200);
            assertThat(get(client, port, "/v1/skills", token).statusCode()).isEqualTo(200);
            assertThat(get(client, management, "/actuator/health/readiness", null).statusCode()).isEqualTo(200);
            assertThat(get(client, management, "/actuator/health", null).body()).contains("\"status\":\"UP\"").doesNotContain("components", "details", "path", "kids");
            assertThat(get(client, management, "/actuator/health", token).body()).doesNotContain("jwks", "kids", "executionTrust");
            Files.writeString(file, "{\"keys\":[]}");
            awaitStatus(client, port, "/v1/skills", token, 401);
            assertThat(get(client, management, "/actuator/health/readiness", null).statusCode()).isEqualTo(503);
            Files.writeString(file, valid);
            // Health is snapshot-only: polling it cannot force an unknown-kid refresh.
            awaitStatus(client, port, "/actuator/health/readiness", null, 200);
            assertThat(get(client, port, "/v1/skills", token).statusCode()).isEqualTo(200);
        }
    }
    @Test void configurationErrorsFailStartupWithActionableMessages() {
        for (var entry : Map.of("", "loomspan-sidecar.trust.mode", "unknown", "colocated or file").entrySet()) {
            assertThatThrownBy(() -> { try (var ignored = application(WebApplicationType.NONE,
                    Map.of("loomspan-sidecar.trust.mode", entry.getKey()))) {} })
                    .hasStackTraceContaining(entry.getValue()).hasStackTraceContaining("examples/quickstart");
        }
        assertThatThrownBy(() -> { try (var ignored = application(WebApplicationType.NONE,
                Map.of("loomspan-sidecar.trust.mode", "colocated"))) {} }).hasStackTraceContaining("colocated.key-directory");
        assertThatThrownBy(() -> { try (var ignored = application(WebApplicationType.NONE,
                Map.of("loomspan-sidecar.trust.mode", "colocated", "loomspan-sidecar.trust.colocated.key-directory", directory.toString(),
                        "server.address", "0.0.0.0"))) {} }).hasStackTraceContaining("explicit loopback server.address");
    }
    @Test void validatesAllModeAndListenerCombinations() {
        var environment = new MockEnvironment().withProperty("server.address", "127.0.0.1").withProperty("server.port", "8080");
        var p = new SidecarTrustProperties(); p.setMode("colocated"); p.getColocated().setKeyDirectory(directory.toString());
        assertThat(p.resolve(environment)).isEqualTo(directory.resolve("jwks.json"));
        for (String address : List.of("", "0.0.0.0", "::", "192.0.2.1")) {
            environment.setProperty("server.address", address);
            assertThatThrownBy(() -> p.resolve(environment)).hasMessageContaining("server.address");
        }
        environment.setProperty("server.address", "::1"); environment.setProperty("management.server.port", "0");
        assertThatThrownBy(() -> p.resolve(environment)).hasMessageContaining("management.server.address");
        environment.setProperty("management.server.address", "0.0.0.0");
        assertThatThrownBy(() -> p.resolve(environment)).hasMessageContaining("management.server.address");
        environment.setProperty("management.server.address", "127.0.0.1"); assertThat(p.resolve(environment)).isNotNull();
        environment.setProperty("management.server.port", "-1"); environment.setProperty("management.server.address", ""); assertThat(p.resolve(environment)).isNotNull();
        environment.setProperty("management.server.port", "8080"); assertThat(p.resolve(environment)).isNotNull();
        p.getFile().setJwks("wrong.json"); assertThatThrownBy(() -> p.resolve(environment)).hasMessageContaining("other trust mode");
        p.setMode("file"); assertThatThrownBy(() -> p.resolve(environment)).hasMessageContaining("other trust mode");
        p.getColocated().setKeyDirectory(null); environment.setProperty("server.address", "0.0.0.0"); assertThat(p.resolve(environment)).isNotNull();
        p.getFile().setJwks("https://example.test/jwks"); assertThatThrownBy(() -> p.resolve(environment)).hasMessageContaining("filesystem path");
        p.getFile().setJwks(null); assertThatThrownBy(() -> p.resolve(environment)).hasMessageContaining("file.jwks");
    }
    private ConfigurableApplicationContext application(WebApplicationType type, Map<String, String> overrides) {
        var settings = new LinkedHashMap<>(Map.of("server.port", "0", "server.address", "127.0.0.1",
                "loomspan.observability.enabled", "false",
                "loomspan-sidecar.storage.database-path", directory.resolve("sidecar.db").toString())); settings.putAll(overrides);
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class).web(type)
                .run(settings.entrySet().stream().map(e -> "--"+e.getKey()+"="+e.getValue()).toArray(String[]::new));
    }
    private HttpResponse<String> get(HttpClient client, int port, String path, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(java.time.Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private void awaitStatus(HttpClient client, int port, String path, String token, int status) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(4);
        while (System.nanoTime() < deadline) { if (get(client, port, path, token).statusCode() == status) return; Thread.sleep(20); }
        assertThat(get(client, port, path, token).statusCode()).isEqualTo(status);
    }
}
