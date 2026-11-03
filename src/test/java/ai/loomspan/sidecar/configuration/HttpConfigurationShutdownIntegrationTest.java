package ai.loomspan.sidecar.configuration;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.execution.*;
import ai.loomspan.sidecar.http.HttpExecutorClient;
import ai.loomspan.sidecar.security.ExecutionOwner;
import ai.loomspan.sidecar.support.JwtTestTokens;
import ai.loomspan.sidecar.support.SidecarApplicationFixture;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import static org.assertj.core.api.Assertions.*;

class HttpConfigurationShutdownIntegrationTest {
    @TempDir Path directory;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void fullSidecarDiscardsQueueAndClosesClientAfterCompletionOrFrameworkCutoff(boolean cutoff) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            byte[] bytes = "completed".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        var workers = Executors.newVirtualThreadPerTaskExecutor();
        org.springframework.context.ConfigurableApplicationContext context = null;
        try {
            context = new SpringApplicationBuilder(LoomspanSidecarApplication.class, SidecarApplicationFixture.Credentials.class)
                .web(WebApplicationType.NONE).run(
                        "--loomspan-sidecar.storage.database-path=" + directory.resolve("shutdown.db"),
                        "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                        "--loomspan.observability.enabled=false", "--loomspan-sidecar.trust.mode=file",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json",
                        "--loomspan-sidecar.executions.max-concurrent=1", "--loomspan-sidecar.executions.max-queued=1",
                        "--loomspan.shutdown.timeout=" + (cutoff ? "300ms" : "3s"));
            HttpConfigurationIntegrationTest.publish(context,
                    HttpConfigurationIntegrationTest.skill("{target: app, method: GET, path: /}"),
                    HttpConfigurationIntegrationTest.execution(server.getAddress().getPort(), "auth: {mode: none}, read-timeout: 30s"), List.of());
            var client = context.getBean(HttpExecutorClient.class);
            var coordinator = context.getBean(ExecutionCoordinator.class);
            var jwt = context.getBean(JwtDecoder.class).decode(JwtTestTokens.token("shutdown-owner", List.of()));
            var authentication = new JwtAuthenticationToken(jwt);
            var owner = ExecutionOwner.from(authentication);
            var active = coordinator.admit("call", Map.of(), 2, owner, authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = coordinator.admit("call", Map.of(), 2, owner, authentication);
            assertThat(coordinator.find(queued, owner).orElseThrow().status()).isEqualTo(ExecutionStatus.QUEUED);
            var closingContext = context;
            var closing = workers.submit(closingContext::close);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (coordinator.find(queued, owner).isPresent() && System.nanoTime() < deadline) Thread.sleep(5);
            assertThat(coordinator.find(queued, owner)).isEmpty();
            assertThatThrownBy(() -> coordinator.admit("call", Map.of(), 2, owner, authentication))
                    .isInstanceOf(ExecutionUnavailableException.class);
            if (!cutoff) {
                assertThat(closing.isDone()).isFalse();
                assertThat(client.isClosed()).isFalse();
                release.countDown();
            }
            closing.get(cutoff ? 2 : 5, TimeUnit.SECONDS); // A 30s read timeout cannot become another drain budget.
            assertThat(client.isClosed()).isTrue();
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (coordinator.find(active, owner).orElseThrow().completedAt() == null && System.nanoTime() < deadline) Thread.sleep(5);
            var result = coordinator.find(active, owner).orElseThrow();
            assertThat(result.status()).isEqualTo(cutoff ? ExecutionStatus.FAILED : ExecutionStatus.COMPLETED);
            if (!cutoff) assertThat(result.result()).isEqualTo("completed");
        } finally {
            release.countDown();
            if (context != null) context.close();
            server.stop(0);
            workers.shutdownNow();
            workers.close();
        }
    }
}
