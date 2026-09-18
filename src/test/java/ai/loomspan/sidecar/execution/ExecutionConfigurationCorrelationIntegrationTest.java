package ai.loomspan.sidecar.execution;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.security.ExecutionOwner;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.support.SidecarApplicationFixture;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionConfigurationCorrelationIntegrationTest {
    @TempDir Path directory;

    @Test
    void modelOnlyExecutionKeepsDurableIdAfterPublicationAndRetirement() throws Exception {
        try (var fixture = new SidecarApplicationFixture()) {
            fixture.modelResponses().add(SidecarApplicationFixture.completion("model-only result"));
            Path database = directory.resolve("model-only.db");
            SidecarApplicationFixture.seedDatabase(database,
                    List.of(SidecarApplicationFixture.resourceFile("fixtures/skills/mounted-yaml-skill.yaml")),
                    ConfigurationSnapshotStore.EMPTY_REST_ROUTES);
            try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                    .web(WebApplicationType.NONE).run(
                            "--loomspan-sidecar.storage.database-path=" + database,
                            "--loomspan.observability.enabled=false",
                            "--loomspan.connections.fixture.driver=openai",
                            "--loomspan.connections.fixture.base-url=http://127.0.0.1:" + fixture.modelPort() + "/v1",
                            "--loomspan.connections.fixture.api-key=local-test-key",
                            "--loomspan.models.fixture-model.connection=fixture",
                            "--loomspan.models.fixture-model.provider-model=fixture-provider-model",
                            "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                            "--loomspan-sidecar.auth.jwt.audience=sidecar",
                            "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem")) {
                var store = context.getBean(ConfigurationSnapshotStore.class);
                var service = context.getBean(RuntimeConfigurationService.class);
                var coordinator = context.getBean(ExecutionCoordinator.class);
                var a = store.current();
                var jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                        .issuer("https://issuer.test").subject("owner")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
                var authentication = new JwtAuthenticationToken(jwt);
                var owner = ExecutionOwner.from(authentication);
                var id = coordinator.admit("mountedYamlSkill", Map.of(), 2, owner, authentication);
                var terminal = awaitTerminal(coordinator, id, owner);
                assertThat(terminal.configurationSnapshotId()).isEqualTo(a.localId());
                assertThat(terminal.status()).isEqualTo(ExecutionStatus.COMPLETED);
                var draft = new ConfigurationDraft(a);
                draft.replaceContent(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
                assertThat(service.validate(draft).successful()).isTrue();
                service.publish(draft::validatedCandidate);
                assertThat(coordinator.find(id, owner).orElseThrow().configurationSnapshotId()).isEqualTo(a.localId());
            }
        }
    }

    @Test
    void publicationBetweenFrameworkHandoffAndMappingKeepsCapturedDurableId() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = "old-route".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            Path skill = directory.resolve("echoRest.yaml");
            Files.writeString(skill, """
                    name: echoRest
                    description: Captured generation fixture.
                    rest: true
                    input_schema: {type: object, properties: {}}
                    """);
            String routes = """
                    targets:
                      callback: {base-url: 'http://127.0.0.1:%d', auth: {mode: none}, connect-timeout: 1s, read-timeout: 2s, max-response-size: 1KB}
                    routes:
                      echoRest: {target: callback, method: GET, path: /echo}
                    """.formatted(server.getAddress().getPort());
            Path database = directory.resolve("sidecar.db");
            SidecarApplicationFixture.seedDatabase(database, List.of(skill), routes);
            try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                    .web(WebApplicationType.NONE).run(
                            "--loomspan-sidecar.storage.database-path=" + database,
                            "--loomspan.observability.enabled=false",
                            "--loomspan-sidecar.executions.diagnostics=NEVER",
                            "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                            "--loomspan-sidecar.auth.jwt.audience=sidecar",
                            "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem")) {
                var store = context.getBean(ConfigurationSnapshotStore.class);
                var service = context.getBean(RuntimeConfigurationService.class);
                var coordinator = context.getBean(ExecutionCoordinator.class);
                var a = store.current();
                var draft = new ConfigurationDraft(a);
                draft.replaceContent(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
                assertThat(service.validate(draft).successful()).isTrue();
                coordinator.afterHandoff(admitted -> service.publish(draft::validatedCandidate));
                var jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                        .issuer("https://issuer.test").subject("owner")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
                var authentication = new JwtAuthenticationToken(jwt);
                var owner = ExecutionOwner.from(authentication);
                var id = coordinator.admit("echoRest", Map.of(), 2, owner, authentication);
                ExecutionSnapshot terminal = awaitTerminal(coordinator, id, owner);
                assertThat(terminal.status()).isEqualTo(ExecutionStatus.COMPLETED);
                assertThat(terminal.result()).isEqualTo("old-route");
                assertThat(terminal.configurationSnapshotId()).isEqualTo(a.localId());
                assertThat(terminal.events()).isNull();
                assertThat(service.inspect().publishedId()).isNotEqualTo(a.localId());
            }
        } finally { server.stop(0); }
    }

    private ExecutionSnapshot awaitTerminal(ExecutionCoordinator coordinator, java.util.UUID id,
            ExecutionOwner owner) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var snapshot = coordinator.find(id, owner).orElseThrow();
            if (snapshot.completedAt() != null) return snapshot;
            Thread.sleep(10);
        }
        throw new AssertionError("Execution did not finish");
    }
}
