package ai.loomspan.sidecar.rest;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.support.JwtTestTokens;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RestFailureEnvelopeIntegrationTest {
    @TempDir Path directory;

    @Test
    void transportFailuresRemainSafeSkillFailuresThroughExecutionPolling() throws Exception {
        var release = new CountDownLatch(1);
        var entered = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(workers);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            byte[] body = "PRIVATE-UPSTREAM-BODY".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type",
                    path.equals("/media") ? "application/octet-stream" : "text/plain");
            if (path.equals("/timeout")) {
                exchange.sendResponseHeaders(200, 0);
                try (var output = exchange.getResponseBody()) {
                    output.write(body); output.flush(); entered.countDown();
                    try { release.await(10, TimeUnit.SECONDS); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
                }
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            }
        });
        server.start();
        try {
            var routes = new StringBuilder("targets:\n");
            for (String mode : List.of("timeout", "oversize", "media")) {
                Files.writeString(directory.resolve(mode + ".yml"), """
                        name: %s
                        description: Transport failure fixture.
                        rest: true
                        input_schema: {type: object, properties: {}}
                        """.formatted(mode));
                routes.append("  %s: {base-url: 'http://127.0.0.1:%d', auth: {mode: none}, connect-timeout: 1s, read-timeout: 200ms, max-response-size: %s}\n"
                        .formatted(mode, server.getAddress().getPort(), mode.equals("oversize") ? "5B" : "1KB"));
            }
            routes.append("routes:\n");
            for (String mode : List.of("timeout", "oversize", "media")) {
                routes.append("  %s: {target: %s, method: GET, path: /%s}\n".formatted(mode, mode, mode));
            }
            Path file = directory.resolve("routes.yaml");
            Files.writeString(file, routes);
            try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class).run(
                    "--server.port=0", "--management.server.port=0", "--loomspan.observability.enabled=false",
                    "--loomspan.skills.locations=" + directory.toUri() + "*.yml",
                    "--loomspan-sidecar.rest-routes-location=" + file.toUri(),
                    "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                    "--loomspan-sidecar.auth.jwt.audience=sidecar",
                    "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
                    var client = HttpClient.newHttpClient()) {
                int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                String base = "http://127.0.0.1:" + port;
                String token = JwtTestTokens.token("transport-owner", List.of());
                var mapper = new ObjectMapper();
                for (var scenario : Map.of("timeout", "transport error", "oversize", "byte limit", "media", "unsupported").entrySet()) {
                    var accepted = client.send(request(base + "/v1/skills/" + scenario.getKey() + "/executions", token)
                            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                            HttpResponse.BodyHandlers.ofString());
                    assertThat(accepted.statusCode()).isEqualTo(202);
                    if (scenario.getKey().equals("timeout")) assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                    String id = mapper.readTree(accepted.body()).path("id").asText();
                    long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
                    tools.jackson.databind.JsonNode terminal;
                    do {
                        var response = client.send(request(base + "/v1/executions/" + id, token).GET().build(),
                                HttpResponse.BodyHandlers.ofString());
                        assertThat(response.statusCode()).isEqualTo(200);
                        terminal = mapper.readTree(response.body());
                        if (terminal.path("status").asText().equals("FAILED")) break;
                        Thread.sleep(10);
                    } while (System.nanoTime() < deadline);
                    assertThat(terminal.path("status").asText()).isEqualTo("FAILED");
                    assertThat(terminal.path("failure").path("kind").asText()).isEqualTo("SKILL_FAILURE");
                    assertThat(terminal.path("failure").path("message").asText()).contains(scenario.getValue());
                    assertThat(terminal.toString()).doesNotContain("PRIVATE-UPSTREAM-BODY", "stackTrace", "java.lang");
                }
            }
        } finally {
            release.countDown();
            server.stop(0);
            workers.shutdownNow();
            workers.close();
        }
    }

    private HttpRequest.Builder request(String url, String token) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token);
    }
}
