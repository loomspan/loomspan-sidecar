package ai.loomspan.sidecar.execution;

import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.config.SidecarExecutionProperties.Diagnostics;
import ai.loomspan.sidecar.support.JwtTestTokens;
import ai.loomspan.sidecar.support.SidecarApplicationFixture;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionOutputSchemaIntegrationTest {
    private static final String CUSTOMER_MESSAGE = "I already contacted support about my delivery.";
    private static final String TRIAGE = "tsTriageStructured";
    private static final String DEPENDENT = "dependent";
    private static final String FIRST = "first";
    private static final String SECOND = "second";
    private static final String CUSTOMER_SCHEMA = """
            input_schema:
              type: object
              properties:
                customerMessage: {type: string}
              required: [customerMessage]
              additionalProperties: false
            """;
    private static final String EMPTY_SCHEMA = "input_schema: {type: object, additionalProperties: false}\n";
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(Diagnostics.class)
    void exposesSelectedTriageOutputFailure(Diagnostics diagnostics) throws Exception {
        writeSkill("parent", parent(DEPENDENT) + CUSTOMER_SCHEMA + """
                allowed_skills:
                  - name: tsTriageStructured
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /customerMessage: {from: input, path: /customerMessage}
                  - name: dependent
                    required: true
                    max_tasks: 1
                    input_bindings:
                      /probability: {from: child_result, skill: tsTriageStructured, path: /repeatContactProbability}
                """);
        writeSkill(TRIAGE, """
                name: tsTriageStructured
                description: Assess whether this is a repeat contact.
                executor: typesafe
                input_transform:
                  language: jslt
                  expression: |
                    {
                      "model": "jev-1.13.0",
                      "state": {"contact": .customerMessage},
                      "questions": {"repeat": {"type": "noul", "instructions": "Is this a repeat contact?"}}
                    }
                output_transform:
                  language: jslt
                  expression: |
                    {"repeatContactProbability": .answers.repeat.noul}
                """ + CUSTOMER_SCHEMA + outputSchema("repeatContactProbability"));
        writeSkill(DEPENDENT, executorLeaf(DEPENDENT) + """
                input_schema:
                  type: object
                  properties:
                    probability: {type: number}
                  required: [probability]
                  additionalProperties: false
                """);

        try (var model = new SidecarApplicationFixture(); var typesafe = new TypeSafeFixture()) {
            queuePlan(model, task(TRIAGE, null), task(DEPENDENT, null, TRIAGE));
            try (var context = start(model, diagnostics, typesafe.port())) {
                var api = new ExecutionApi(context);
                URI location = api.submit(JSON.writeValueAsString(Map.of("customerMessage", CUSTOMER_MESSAGE)));
                JsonNode snapshot = api.awaitFailed(location);
                if (diagnostics == Diagnostics.NEVER) {
                    assertThat(snapshot.has("events")).isFalse();
                } else {
                    assertThat(outputSchemaSkills(snapshot)).containsExactly(canonical(TRIAGE));
                }
                assertThat(model.modelRequests()).hasSize(1);
                assertThat(typesafe.requests).singleElement().satisfies(body -> {
                    JsonNode request = JSON.readTree(body);
                    assertThat(request.path("model").asText()).isEqualTo("jev-1.13.0");
                    assertThat(request.at("/state/contact").asText()).isEqualTo(CUSTOMER_MESSAGE);
                    assertThat(request.at("/questions/repeat/type").asText()).isEqualTo("noul");
                });
                assertThat(context.getBean(FixtureExecutor.class).calls).isEmpty();
                var foreign = api.get(location, JwtTestTokens.token("other-owner", List.of()));
                assertThat(foreign.statusCode()).isEqualTo(404);
                assertThat(JSON.readTree(foreign.body()).has("failure")).isFalse();

                JsonNode issue = assertOutputSchemaFailure(snapshot, TRIAGE, "/repeatContactProbability");
                assertThat(issue.path("message").asText()).containsIgnoringCase("null").containsIgnoringCase("number");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void competingChildFailuresUseOnlyTheSelectedCause(boolean unrelatedFirst) throws Exception {
        writeSkill("parent", parent(FIRST) + EMPTY_SCHEMA + """
                allowed_skills:
                  - {name: first, required: true, max_tasks: 1}
                  - {name: second, required: true, max_tasks: 1}
                """);
        writeSkill(FIRST, executorLeaf(FIRST) + EMPTY_SCHEMA + outputSchema("firstProbability"));
        writeSkill(SECOND, executorLeaf(SECOND) + EMPTY_SCHEMA + outputSchema("secondProbability"));
        try (var model = new SidecarApplicationFixture()) {
            queuePlan(model, task(FIRST, "batch"), task(SECOND, "batch"));
            try (var context = start(model, Diagnostics.ONERROR, null)) {
                var fixture = context.getBean(FixtureExecutor.class);
                fixture.failFirstBeforeSchema = unrelatedFirst;
                var api = new ExecutionApi(context);
                JsonNode snapshot = api.awaitFailed(api.submit("{}"));
                assertThat(model.modelRequests()).hasSize(1);
                assertThat(fixture.calls).containsExactlyInAnyOrder(canonical(FIRST), canonical(SECOND));

                if (unrelatedFirst) {
                    assertThat(outputSchemaSkills(snapshot)).containsExactly(canonical(SECOND));
                    JsonNode failure = snapshot.path("failure");
                    assertThat(failure.path("kind").asText()).isEqualTo("SKILL_FAILURE");
                    assertThat(failure.has("producingSkillName")).isFalse();
                    assertThat(failure.has("issues")).isFalse();
                } else {
                    assertThat(outputSchemaSkills(snapshot)).containsExactlyInAnyOrder(canonical(FIRST), canonical(SECOND));
                    assertOutputSchemaFailure(snapshot, FIRST, "/firstProbability");
                }
            }
        }
    }

    private static JsonNode assertOutputSchemaFailure(JsonNode snapshot, String child, String pointer) {
        JsonNode failure = snapshot.path("failure");
        assertThat(failure.path("kind").asText()).as("terminal failure in %s", snapshot).isEqualTo("OUTPUT_SCHEMA");
        assertThat(failure.path("message").asText()).isEqualTo("Skill 'parent' execution failed.");
        assertThat(failure.path("producingSkillName").asText()).isEqualTo(canonical(child));
        assertThat(failure.propertyNames()).containsExactlyInAnyOrder("kind", "message", "producingSkillName", "issues");
        JsonNode issues = failure.path("issues");
        assertThat(issues.isArray()).isTrue();
        assertThat(issues.size()).isEqualTo(1);
        JsonNode issue = issues.get(0);
        assertThat(issue.path("path").asText()).isEqualTo(pointer);
        assertThat(issue.path("code").asText()).isEqualTo("type");
        return issue;
    }

    private static List<String> outputSchemaSkills(JsonNode snapshot) {
        JsonNode events = snapshot.path("events");
        assertThat(events.isArray()).isTrue();
        List<String> skills = new ArrayList<>();
        events.forEach(event -> {
            if (event.path("type").asText().equals("OUTPUT_SCHEMA")) {
                skills.add(event.path("details").path("skillName").asText());
            }
        });
        return skills;
    }

    private void writeSkill(String name, String text) throws IOException {
        Files.writeString(directory.resolve(name + ".yaml"), text);
    }

    private static String parent(String outputChild) {
        return """
                name: parent
                description: Plan the two required checks.
                model: fixture-model
                planning_mode: true
                max_steps: 2
                output_from: {skill: %s}
                """.formatted(outputChild);
    }

    private static String executorLeaf(String name) {
        return """
                name: %s
                description: Test child executor.
                executor: fixture
                """.formatted(name);
    }

    private static String outputSchema(String property) {
        return """
                output_schema:
                  type: object
                  properties:
                    %s: {type: number}
                  required: [%s]
                  additionalProperties: false
                """.formatted(property, property);
    }

    private static String canonical(String name) {
        return "default-" + name;
    }

    private static Map<String, Object> task(String child, String group, String... dependencies) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("taskId", child);
        task.put("title", "Run " + child);
        task.put("status", "PENDING");
        task.put("capabilityName", canonical(child));
        task.put("intent", "Run " + child);
        task.put("dependsOn", List.of(dependencies));
        task.put("expectedOutputs", List.of("result"));
        task.put("parallelGroup", group);
        task.put("note", "");
        return task;
    }

    private static void queuePlan(SidecarApplicationFixture model, Object... tasks) {
        model.modelResponses().add(SidecarApplicationFixture.completion(JSON.writeValueAsString(Map.of(
                "capabilityName", canonical("parent"), "createdAt", "2026-11-18T00:00:00Z",
                "status", "VALID", "tasks", List.of(tasks)))));
    }

    private ServletWebServerApplicationContext start(SidecarApplicationFixture model, Diagnostics diagnostics,
            Integer typeSafePort) throws IOException {
        Path configuration = Files.writeString(directory.resolve("execution.yml"), """
                loomspan:
                  executor-config:
                    default:
                      http: {}
                      fixture: {}
                """ + (typeSafePort == null ? "" : """
                      typesafe:
                        base_url: http://127.0.0.1:%d
                        api_key: fixture-key
                """.formatted(typeSafePort)));
        List<String> args = new ArrayList<>(List.of(
                "--server.address=127.0.0.1", "--server.port=0",
                "--spring.config.additional-location=" + configuration.toUri(),
                "--loomspan-sidecar.configuration.mode=file",
                "--loomspan.skills.locations=" + directory.toUri() + "*.yaml",
                "--loomspan-sidecar.storage.database-path=" + directory.resolve("sidecar.db"),
                "--loomspan-sidecar.trust.mode=file",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json",
                "--loomspan.execution-trace.persistence=never",
                "--loomspan.session.mission-timeout=15s",
                "--loomspan.connections.fixture.driver=openai",
                "--loomspan.connections.fixture.base-url=http://127.0.0.1:" + model.modelPort() + "/v1",
                "--loomspan.connections.fixture.api-key=fixture-key",
                "--loomspan.connections.fixture.provider-retry.enabled=false",
                "--loomspan.models.fixture-model.connection=fixture",
                "--loomspan.models.fixture-model.provider-model=fixture-provider-model"));
        if (diagnostics != Diagnostics.NEVER) {
            args.add("--loomspan-sidecar.executions.diagnostics=" + diagnostics);
        }
        var app = new SpringApplicationBuilder(LoomspanSidecarApplication.class, FixtureConfiguration.class);
        if (typeSafePort != null) app.profiles("typesafe");
        return (ServletWebServerApplicationContext) app.run(args.toArray(String[]::new));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureConfiguration {
        @Bean
        FixtureExecutor fixtureExecutor() {
            return new FixtureExecutor();
        }
    }

    static class FixtureExecutor {
        final Queue<String> calls = new ConcurrentLinkedQueue<>();
        volatile boolean failFirstBeforeSchema;

        @SkillExecutor(name = "fixture")
        public String invoke(SkillExecutorInvocation invocation) {
            String skill = invocation.skillName();
            calls.add(skill);
            if (skill.equals(canonical(FIRST))) {
                if (failFirstBeforeSchema) throw new IllegalStateException("ordinary fixture failure");
                return """
                        {"firstProbability":null}
                        """;
            }
            return """
                    {"secondProbability":null}
                    """;
        }
    }

    private static final class TypeSafeFixture implements AutoCloseable {
        final Queue<String> requests = new ConcurrentLinkedQueue<>();
        private final HttpServer server;

        TypeSafeFixture() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/systemone", exchange -> {
                try (exchange) {
                    if (!exchange.getRequestMethod().equals("POST")
                            || !exchange.getRequestURI().getPath().equals("/v1/systemone")) {
                        exchange.sendResponseHeaders(404, -1);
                        return;
                    }
                    requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    byte[] body = """
                            {"answers":{"repeat":{"type":"noul"}}}
                            """.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                }
            });
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class ExecutionApi {
        private final HttpClient client = HttpClient.newHttpClient();
        private final String owner = JwtTestTokens.token("owner", List.of());
        private final URI base;

        ExecutionApi(ServletWebServerApplicationContext context) {
            base = URI.create("http://127.0.0.1:" + context.getWebServer().getPort());
        }

        URI submit(String input) throws Exception {
            var request = HttpRequest.newBuilder(base.resolve("/v1/skills/parent/executions"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Authorization", "Bearer " + owner)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(input)).build();
            var accepted = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(accepted.statusCode()).as("submit response: %s", accepted.body()).isEqualTo(202);
            String id = JSON.readTree(accepted.body()).path("id").asText();
            assertThat(id).isNotBlank();
            URI location = base.resolve(accepted.headers().firstValue("Location").orElseThrow());
            assertThat(location.getPath()).isEqualTo("/v1/executions/" + id);
            return location;
        }

        JsonNode awaitFailed(URI location) throws Exception {
            Instant deadline = Instant.now().plusSeconds(15);
            JsonNode snapshot;
            do {
                var polled = get(location, owner);
                assertThat(polled.statusCode()).isEqualTo(200);
                snapshot = JSON.readTree(polled.body());
                String status = snapshot.path("status").asText();
                if (status.equals("FAILED") || status.equals("COMPLETED")) break;
                Thread.sleep(20);
            } while (Instant.now().isBefore(deadline));
            assertThat(snapshot.path("skillName").asText()).isEqualTo("parent");
            assertThat(snapshot.path("status").asText()).as("terminal snapshot: %s", snapshot).isEqualTo("FAILED");
            assertThat(snapshot.path("result").isNull()).isTrue();
            return snapshot;
        }

        HttpResponse<String> get(URI location, String token) throws Exception {
            return client.send(HttpRequest.newBuilder(location).timeout(Duration.ofSeconds(5))
                            .header("Authorization", "Bearer " + token).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
