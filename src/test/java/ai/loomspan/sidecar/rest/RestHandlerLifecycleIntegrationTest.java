package ai.loomspan.sidecar.rest;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import ai.loomspan.sidecar.execution.ExecutionStatus;
import ai.loomspan.sidecar.security.ExecutionOwner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sidecarWorkersClientsAndObserversSurviveNormalClose(boolean managementAndAsync) throws Exception {
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
        var management = new java.util.concurrent.atomic.AtomicReference<org.springframework.context.ConfigurableApplicationContext>();
        var context = application(managementAndAsync).listeners(event -> {
            if (event instanceof org.springframework.boot.web.server.context.WebServerInitializedEvent initialized
                    && initialized.getApplicationContext().getParent() != null) {
                management.set((org.springframework.context.ConfigurableApplicationContext) initialized.getApplicationContext());
            }
        }).run(
                        "--server.port=0", "--management.server.port=0",
                        "--loomspan.skills.locations=" + skill.toUri(),
                        "--loomspan.observability.enabled=false",
                        "--loomspan.shutdown.timeout=3s",
                        "--loomspan-sidecar.executions.max-concurrent=1",
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
            java.util.UUID queued = null;
            if (managementAndAsync) {
                assertThat(management.get()).isNotNull();
                management.get().close();
                assertThat(clients.isClosed()).isFalse();
                // Boot can propagate the child's readiness change; Sidecar admission
                // and its outbound clients must remain owned by the parent context.
                queued = coordinator.admit("echoRest", Map.of("message", "queued"), 10, owner, authentication);
                assertThat(coordinator.find(queued, owner)).isPresent();
            }
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
            if (queued != null) assertThat(coordinator.find(queued, owner)).isEmpty();
        } finally {
            release.countDown();
            if (context.isActive()) context.close();
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void frameworkCutoffBoundsBlockedWorkWithoutEarlyOrSecondSidecarTeardown(boolean managementAndAsync) throws Exception {
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
        var context = application(managementAndAsync).run(
                        "--server.port=0", "--management.server.port=0",
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

    private SpringApplicationBuilder application(boolean managementAndAsync) {
        var builder = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(managementAndAsync ? WebApplicationType.SERVLET : WebApplicationType.NONE);
        if (managementAndAsync) builder.sources(AsyncEvents.class);
        return builder;
    }

    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class AsyncEvents {
        @org.springframework.context.annotation.Bean(destroyMethod = "shutdownNow")
        java.util.concurrent.ExecutorService applicationEventsExecutor() {
            return Executors.newVirtualThreadPerTaskExecutor();
        }

        @org.springframework.context.annotation.Bean(name = "applicationEventMulticaster")
        org.springframework.context.event.SimpleApplicationEventMulticaster applicationEventMulticaster(
                org.springframework.beans.factory.BeanFactory beanFactory,
                @org.springframework.beans.factory.annotation.Qualifier("applicationEventsExecutor")
                java.util.concurrent.ExecutorService applicationEventsExecutor) {
            var multicaster = new org.springframework.context.event.SimpleApplicationEventMulticaster(beanFactory);
            multicaster.setTaskExecutor(applicationEventsExecutor);
            return multicaster;
        }
    }

}
