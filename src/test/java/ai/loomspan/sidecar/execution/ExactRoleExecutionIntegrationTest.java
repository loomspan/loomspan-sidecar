package ai.loomspan.sidecar.execution;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.nio.file.Path;

import ai.loomspan.sidecar.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "loomspan.observability.enabled=false",
        "loomspan-sidecar.trust.mode=file",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json"
})
class ExactRoleExecutionIntegrationTest {
    @TempDir static Path storageDirectory;
    private static final com.sun.net.httpserver.HttpServer CALLBACK = callbackServer();
    private static final java.nio.file.Path ROUTES = routeFile();
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void restProperties(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> ai.loomspan.sidecar.support.SidecarApplicationFixture.seedDatabase(
                storageDirectory.resolve("sidecar.db"), java.util.List.of(
                        roleSkill()),
                readRoutes()).toString());
    }

    private static java.nio.file.Path roleSkill() {
        try {
            var path = java.nio.file.Files.createTempFile("exact-role-", ".yaml");
            java.nio.file.Files.writeString(path, java.nio.file.Files.readString(
                    ai.loomspan.sidecar.support.SidecarApplicationFixture.resourceFile("fixtures/execution-skills/echo-rest.yml"))
                    .replace("REST_USER", "ROLE_ORDER_EDITOR"));
            path.toFile().deleteOnExit(); return path;
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static String readRoutes() {
        try { return java.nio.file.Files.readString(ROUTES); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }

    @org.junit.jupiter.api.AfterAll
    static void stopCallback() { CALLBACK.stop(0); }

    @Test
    void matchesLiteralRolesThroughPublicFrameworkAuthorization() throws Exception {
        String allowed = JwtTestTokens.token("loomspan-app", "sidecar", "custom-owner", List.of("ROLE_ORDER_EDITOR"));
        var accepted = post(allowed);
        assertThat(accepted.statusCode()).isEqualTo(202);
        String id = mapper.readTree(accepted.body()).path("id").asText();
        assertThat(poll(id, allowed).path("result").asText()).isEqualTo("REST: custom-role");

        for (String denied : List.of(
                JwtTestTokens.token("wrong", List.of("ORDER_EDITOR")),
                JwtTestTokens.token("wrong-prefix", List.of("ROLE_ROLE_ORDER_EDITOR")),
                JwtTestTokens.token("empty", List.of()))) {
            assertThat(post(denied).statusCode()).isEqualTo(403);
        }
    }

    private HttpResponse<String> post(String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/v1/skills/echoRest/executions"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"custom-role\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode poll(String id, String token) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/executions/" + id))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token).GET().build();
            JsonNode body = mapper.readTree(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (body.path("status").asText().matches("COMPLETED|FAILED")) return body;
            Thread.sleep(10);
        }
        throw new AssertionError("Execution did not finish");
    }

    private static com.sun.net.httpserver.HttpServer callbackServer() {
        try {
            var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/echo", exchange -> {
                String request = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                String message = new ObjectMapper().readTree(request).path("message").asText();
                byte[] response = ("REST: " + message).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, response.length);
                try (var output = exchange.getResponseBody()) { output.write(response); }
            });
            server.start();
            return server;
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }

    private static java.nio.file.Path routeFile() {
        try {
            var file = java.nio.file.Files.createTempFile("sidecar-custom-role-routes-", ".yaml");
            java.nio.file.Files.writeString(file, "loomspan: {executor-config: {default: {http: {targets: {callback: {base-url: 'http://127.0.0.1:%d', auth: {mode: none}}}}}}}\n".formatted(CALLBACK.getAddress().getPort()));
            file.toFile().deleteOnExit();
            return file;
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
}
