package ai.loomspan.sidecar.rest;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import ai.loomspan.sidecar.execution.ExecutionStatus;
import ai.loomspan.sidecar.security.ExecutionOwner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class RestHandlerLifecycleIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void sidecarWorkersClientsAndObserversSurviveNormalClose() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            byte[] body = "completed-during-close".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        Path skill = temporaryDirectory.resolve("echo-rest.yml");
        Files.writeString(skill, """
                name: echoRest
                description: Lifecycle REST skill.
                rest: true
                input_schema:
                  type: object
                  properties:
                    message: {type: string}
                  required: [message]
                """);
        Path routes = temporaryDirectory.resolve("routes.yaml");
        Files.writeString(routes, """
                targets:
                  callback: {base-url: 'http://127.0.0.1:%d', auth: {mode: none}, connect-timeout: 1s, read-timeout: 5s, max-response-size: 1KB}
                routes:
                  echoRest: {target: callback, method: POST, path: /echo}
                """.formatted(server.getAddress().getPort()));
        var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE).run(
                        "--loomspan.skills.locations=" + skill.toUri(),
                        "--loomspan.observability.enabled=false",
                        "--loomspan.shutdown.timeout=3s",
                        "--loomspan-sidecar.executions.diagnostics=ALWAYS",
                        "--loomspan-sidecar.rest-routes-location=" + routes.toUri(),
                        "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            RestTargetClients clients = context.getBean(RestTargetClients.class);
            ExecutionCoordinator coordinator = context.getBean(ExecutionCoordinator.class);
            JwtAuthenticationToken authentication = authentication();
            ExecutionOwner owner = ExecutionOwner.from(authentication);
            var id = coordinator.admit("echoRest", Map.of("message", "x"), 10, owner, authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var closing = executor.submit(context::close);
            Thread.sleep(100);
            assertThat(closing.isDone()).isFalse();
            assertThat(clients.isClosed()).isFalse();
            release.countDown();
            closing.get(3, TimeUnit.SECONDS);
            assertThat(clients.isClosed()).isTrue();
            var completed = coordinator.find(id, owner).orElseThrow();
            assertThat(completed.status()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(completed.result()).isEqualTo("completed-during-close");
            assertThat(completed.events()).isNotEmpty();
        } finally {
            if (context.isActive()) context.close();
            server.stop(0);
        }
    }

    @Test
    void frameworkCutoffBoundsBlockedWorkWithoutEarlyOrSecondSidecarTeardown() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch neverRelease = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            entered.countDown();
            try { neverRelease.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        server.start();
        Path skill = temporaryDirectory.resolve("cutoff-rest.yml");
        Files.writeString(skill, """
                name: cutoffRest
                description: Cutoff REST skill.
                rest: true
                input_schema:
                  type: object
                  properties: {}
                """);
        Path routes = temporaryDirectory.resolve("cutoff-routes.yaml");
        Files.writeString(routes, """
                targets:
                  callback: {base-url: 'http://127.0.0.1:%d', auth: {mode: none}, connect-timeout: 1s, read-timeout: 10s, max-response-size: 1KB}
                routes:
                  cutoffRest: {target: callback, method: GET, path: /echo}
                """.formatted(server.getAddress().getPort()));
        var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE).run(
                        "--loomspan.skills.locations=" + skill.toUri(),
                        "--loomspan.observability.enabled=false",
                        "--loomspan.shutdown.timeout=200ms",
                        "--spring.lifecycle.timeout-per-shutdown-phase=50ms",
                        "--loomspan-sidecar.rest-routes-location=" + routes.toUri(),
                        "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
        RestTargetClients clients = context.getBean(RestTargetClients.class);
        try {
            ExecutionCoordinator coordinator = context.getBean(ExecutionCoordinator.class);
            JwtAuthenticationToken authentication = authentication();
            coordinator.admit("cutoffRest", Map.of(), 2, ExecutionOwner.from(authentication), authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            long started = System.nanoTime();
            context.close();
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertThat(elapsed).isBetween(Duration.ofMillis(100), Duration.ofSeconds(2));
            assertThat(clients.isClosed()).isTrue();
        } finally {
            if (context.isActive()) context.close();
            neverRelease.countDown();
            server.stop(0);
        }
    }

    private JwtAuthenticationToken authentication() {
        var jwt = Jwt.withTokenValue("lifecycle-token").header("alg", "none")
                .issuer("https://issuer.test").subject("lifecycle-owner")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

}
