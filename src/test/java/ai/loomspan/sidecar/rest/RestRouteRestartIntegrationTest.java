package ai.loomspan.sidecar.rest;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestRouteRestartIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void keepsRouteAndCatalogSnapshotsUntilRestart() throws Exception {
        HttpServer first = server("first");
        HttpServer second = server("second");
        Path skill = temporaryDirectory.resolve("echo-rest.yml");
        writeSkill(skill, "Restart REST skill.");
        Path routes = temporaryDirectory.resolve("routes.yaml");
        writeRoutes(routes, first.getAddress().getPort(), "echoRest");
        String[] properties = properties(skill, routes);
        try {
            try (var context = application(properties)) {
                var handler = context.getBean(RestSkillHandler.class);
                assertThat(handler.handle(new RestSkillInvocation("echoRest", Map.of("message", "x"))))
                        .isEqualTo("first");
                assertThat(context.getBean(SkillCatalog.class).skill("echoRest").orElseThrow().description())
                        .isEqualTo("Restart REST skill.");
                writeSkill(skill, "Updated restart REST skill.");
                writeRoutes(routes, second.getAddress().getPort(), "echoRest");
                assertThat(handler.handle(new RestSkillInvocation("echoRest", Map.of("message", "x"))))
                        .isEqualTo("first");
                assertThat(context.getBean(SkillCatalog.class).skill("echoRest").orElseThrow().description())
                        .isEqualTo("Restart REST skill.");
            }
            try (var context = application(properties)) {
                assertThat(context.getBean(RestSkillHandler.class)
                        .handle(new RestSkillInvocation("echoRest", Map.of("message", "x"))))
                        .isEqualTo("second");
                assertThat(context.getBean(SkillCatalog.class).skill("echoRest").orElseThrow().description())
                        .isEqualTo("Updated restart REST skill.");
            }
            writeRoutes(routes, second.getAddress().getPort(), "unknownSkill");
            assertThatThrownBy(() -> application(properties)).hasMessage(
                    "Invalid REST routes at " + routes.toUri() + ": REST skill 'echoRest' has no route");
        } finally {
            first.stop(0);
            second.stop(0);
        }
    }

    private static void writeSkill(Path skill, String description) throws Exception {
        Files.writeString(skill, """
                name: echoRest
                description: %s
                rest: true
                input_schema:
                  type: object
                  properties:
                    message: {type: string}
                  required: [message]
                """.formatted(description));
    }

    private org.springframework.context.ConfigurableApplicationContext application(String[] properties) {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE).run(properties);
    }

    private String[] properties(Path skill, Path routes) {
        return new String[] {"--loomspan.skills.locations=" + skill.toUri(),
                "--loomspan.observability.enabled=false",
                "--loomspan-sidecar.rest-routes-location=" + routes.toUri(),
                "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"};
    }

    private static void writeRoutes(Path path, int port, String skill) throws Exception {
        Files.writeString(path, """
                targets:
                  callback: {base-url: 'http://127.0.0.1:%d', auth: {mode: none}, connect-timeout: 1s, read-timeout: 2s, max-response-size: 1KB}
                routes:
                  %s: {target: callback, method: POST, path: /echo}
                """.formatted(port, skill));
    }

    private static HttpServer server(String result) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = result.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        return server;
    }
}
