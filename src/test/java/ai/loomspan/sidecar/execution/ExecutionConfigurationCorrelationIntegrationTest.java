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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionConfigurationCorrelationIntegrationTest {
    @TempDir Path directory;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"root", "child", "retry"})
    void runningModelCallKeepsCapturedConnectionWhileNewHandoffUsesPublishedConnection(String scenario) throws Exception {
        try (var oldProvider = new SidecarApplicationFixture();
                var newProvider = new SidecarApplicationFixture()) {
            oldProvider.blockNextModelResponse();
            if (scenario.equals("retry")) oldProvider.modelResponses().add("RETRY");
            if (scenario.equals("child")) {
                oldProvider.modelResponses().add(SidecarApplicationFixture.completion("""
                        {"capabilityName":"parent","createdAt":"2026-09-27T00:00:00Z","status":"VALID",
                        "tasks":[{"taskId":"child-task","title":"Call child","status":"PENDING",
                        "capabilityName":"mountedYamlSkill","intent":"Call child","dependsOn":[],
                        "expectedOutputs":["result"],"parallelGroup":null,"note":""}]}
                        """));
                oldProvider.modelResponses().add(SidecarApplicationFixture.completion("""
                        {"stepAction":"CALL_TOOL","taskId":"child-task","toolName":"mountedYamlSkill","toolArguments":{}}
                        """));
                oldProvider.modelResponses().add(SidecarApplicationFixture.completion("child-provider"));
                oldProvider.modelResponses().add(SidecarApplicationFixture.completion("""
                        {"stepAction":"FINAL_RESPONSE","finalResponse":"old-provider"}
                        """));
            } else oldProvider.modelResponses().add(SidecarApplicationFixture.completion("old-provider"));
            newProvider.modelResponses().add(SidecarApplicationFixture.completion("new-provider"));
            Path database = directory.resolve("model-transition.db");
            var skill = SidecarApplicationFixture.resourceFile("fixtures/skills/mounted-yaml-skill.yaml");
            Path parent = directory.resolve("parent.yaml");
            Files.writeString(parent, """
                    name: parent
                    description: Delayed model child fixture.
                    model: fixture-model
                    planning_mode: true
                    concurrency: false
                    max_steps: 2
                    allowed_skills:
                      - name: mountedYamlSkill
                    """);
            SidecarApplicationFixture.seedDatabase(database, List.of(skill, parent),
                    ConfigurationSnapshotStore.EMPTY_REST_ROUTES, modelConfiguration(oldProvider.modelPort()), Map.of("fixture.model.key", "local-test-key"));
            try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class, SidecarApplicationFixture.Credentials.class)
                    .web(WebApplicationType.NONE).run(
                            "--loomspan-sidecar.storage.database-path=" + database,
                            "--loomspan.observability.enabled=false",
                            "--fixture.model.key=local-test-key",
                            "--loomspan.connections.fixture.driver=openai",
                            "--loomspan.connections.fixture.base-url=http://127.0.0.1:" + oldProvider.modelPort() + "/v1",
                            "--loomspan.connections.fixture.api-key=${fixture.model.key}",
                            "--loomspan.models.fixture-model.connection=fixture",
                            "--loomspan.models.fixture-model.provider-model=fixture-provider-model",
                            "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                            "--loomspan-sidecar.auth.jwt.audience=sidecar",
                            "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem")) {
                var service = context.getBean(RuntimeConfigurationService.class);
                var coordinator = context.getBean(ExecutionCoordinator.class);
                var oldSnapshot = service.publishedSnapshot();
                var jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                        .issuer("https://issuer.test").subject("owner")
                        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
                var authentication = new JwtAuthenticationToken(jwt);
                var owner = ExecutionOwner.from(authentication);
                try {
                    var oldId = coordinator.admit(scenario.equals("child") ? "parent" : "mountedYamlSkill", Map.of(), 2, owner, authentication);
                    assertThat(oldProvider.awaitModelBlock(3, TimeUnit.SECONDS)).isTrue();
                    var draft = new ConfigurationDraft(oldSnapshot);
                    draft.replaceContent(new ManagedConfiguration(oldSnapshot.configuration().skillDocuments(),
                            ConfigurationSnapshotStore.EMPTY_REST_ROUTES, modelConfiguration(newProvider.modelPort())
                                    .replace("fixture-provider-model", "new-provider-model").replace("max-provider-attempts: 8", "max-provider-attempts: 1")));
                    var newCredentials = SidecarApplicationFixture.encrypt(Map.of("fixture.model.key", "local-test-key-new"));
                    assertThat(ai.loomspan.sidecar.support.TestDrafts.validate(service, draft, newCredentials).successful()).isTrue();
                    var newSnapshot = context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class)
                            .publish(draft, newCredentials);
                    var newId = coordinator.admit("mountedYamlSkill", Map.of(), 2, owner, authentication);
                    var newResult = awaitTerminal(coordinator, newId, owner);
                    assertThat(newResult.status()).isEqualTo(ExecutionStatus.COMPLETED);
                    assertThat(newResult.result()).isEqualTo("new-provider");
                    assertThat(newResult.configurationSnapshotId()).isEqualTo(newSnapshot.localId());
                    assertThat(newProvider.modelAuthorizations()).containsExactly("Bearer local-test-key-new");
                    assertThat(newProvider.modelRequests()).allMatch(body -> body.contains("new-provider-model"));
                    assertThat(coordinator.find(oldId, owner).orElseThrow().configurationSnapshotId())
                            .isEqualTo(oldSnapshot.localId());
                    oldProvider.releaseModelBlock();
                    var oldResult = awaitTerminal(coordinator, oldId, owner);
                    assertThat(oldResult.status()).isEqualTo(ExecutionStatus.COMPLETED);
                    assertThat(oldResult.result()).isEqualTo("old-provider");
                    assertThat(oldResult.configurationSnapshotId()).isEqualTo(oldSnapshot.localId());
                    assertThat(oldProvider.modelAuthorizations()).hasSize(scenario.equals("child") ? 4 : scenario.equals("retry") ? 2 : 1)
                            .allMatch(value -> value.equals("Bearer local-test-key"));
                    assertThat(oldProvider.modelRequests()).allMatch(body -> body.contains("fixture-provider-model"));
                } finally { oldProvider.releaseModelBlock(); }
            }
        }
    }

    private static String modelConfiguration(int port) {
        return """
                loomspan:
                  connections:
                    fixture:
                      driver: openai
                      base-url: http://127.0.0.1:%d/v1
                      api-key-ref: fixture.model.key
                  models:
                    fixture-model:
                      connection: fixture
                      provider-model: fixture-provider-model
                  session:
                    quotas:
                      max-provider-attempts: 8
                """.formatted(port);
    }

    @Test
    void modelOnlyExecutionKeepsDurableIdAfterPublicationAndRetirement() throws Exception {
        try (var fixture = new SidecarApplicationFixture()) {
            fixture.modelResponses().add(SidecarApplicationFixture.completion("model-only result"));
            Path database = directory.resolve("model-only.db");
            SidecarApplicationFixture.seedDatabase(database,
                    List.of(SidecarApplicationFixture.resourceFile("fixtures/skills/mounted-yaml-skill.yaml")),
                    ConfigurationSnapshotStore.EMPTY_REST_ROUTES, """
                            loomspan:
                              connections:
                                fixture:
                                  driver: openai
                                  base-url: http://127.0.0.1:%d/v1
                                  api-key-ref: fixture.model.key
                              models:
                                fixture-model:
                                  connection: fixture
                                  provider-model: fixture-provider-model
                            """.formatted(fixture.modelPort()), Map.of("fixture.model.key", "local-test-key"));
            try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class, SidecarApplicationFixture.Credentials.class)
                    .web(WebApplicationType.NONE).run(
                            "--loomspan-sidecar.storage.database-path=" + database,
                            "--loomspan.observability.enabled=false",
                            "--fixture.model.key=local-test-key",
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
                draft.replaceContent(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES, ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION));
                assertThat(ai.loomspan.sidecar.support.TestDrafts.validate(service, draft, List.of()).successful()).isTrue();
                context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(draft, List.of());
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
            try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class, SidecarApplicationFixture.Credentials.class)
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
                draft.replaceContent(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES, ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION));
                assertThat(ai.loomspan.sidecar.support.TestDrafts.validate(service, draft, List.of()).successful()).isTrue();
                coordinator.afterHandoff(admitted -> context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(draft));
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
