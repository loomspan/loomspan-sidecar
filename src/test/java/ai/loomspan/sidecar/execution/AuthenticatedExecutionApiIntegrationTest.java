package ai.loomspan.sidecar.execution;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import ai.loomspan.sidecar.support.JwtTestTokens;
import ai.loomspan.sidecar.support.SidecarApplicationFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "loomspan.observability.enabled=false",
        "loomspan.skills.locations=classpath*:fixtures/execution-skills/*.yml",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem",
        "loomspan-sidecar.auth.jwt.clock-skew=0s",
        "loomspan-sidecar.executions.max-concurrent=1",
        "loomspan-sidecar.executions.max-queued=2",
        "loomspan-sidecar.executions.max-input-size=64B"
})
class AuthenticatedExecutionApiIntegrationTest {
    private static final SidecarApplicationFixture FIXTURE = new SidecarApplicationFixture();
    private static final ConcurrentLinkedQueue<String> MODEL_RESPONSES = FIXTURE.modelResponses();
    private static final SidecarApplicationFixture.CallbackFixture CALLBACK = FIXTURE.callback();
    private static final Path ROUTES = FIXTURE.routes();
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired ExecutionCoordinator coordinator;
    @Autowired ConfigurableApplicationContext applicationContext;

    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry properties) {
        properties.add("loomspan.connections.fixture.driver", () -> "openai");
        properties.add("loomspan.connections.fixture.base-url",
                () -> "http://127.0.0.1:" + FIXTURE.modelPort() + "/v1");
        properties.add("loomspan.connections.fixture.api-key", () -> "local-test-key");
        properties.add("loomspan.models.fixture-model.connection", () -> "fixture");
        properties.add("loomspan.models.fixture-model.provider-model", () -> "fixture-provider-model");
        properties.add("loomspan-sidecar.rest-routes-location", () -> ROUTES.toUri().toString());
    }

    @AfterAll
    static void stopModelServer() {
        FIXTURE.close();
    }

    @Test
    void requiresJwtAndExposesCatalogWithoutRoleFiltering() throws Exception {
        var unauthenticated = send("GET", "/v1/skills", null, null);
        assertThat(unauthenticated.statusCode()).isEqualTo(401);
        assertThat(unauthenticated.headers().firstValue("cache-control")).contains("no-store");

        var authenticated = send("GET", "/v1/skills", JwtTestTokens.token("reader", List.of()), null);
        assertThat(authenticated.statusCode()).isEqualTo(200);
        assertThat(authenticated.body()).contains("echoRest", "REST", "inputSchema");

        var missing = send("GET", "/v1/skills/missing", JwtTestTokens.token("reader", List.of()), null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
    }

    @Test
    void rejectsInvalidInputAndMissingRoleBeforeAdmission() throws Exception {
        var roleless = send("POST", "/v1/skills/echoRest/executions",
                JwtTestTokens.token("reader", List.of()), "{\"message\":\"hello\"}");
        assertThat(roleless.statusCode()).isEqualTo(403);

        var invalid = send("POST", "/v1/skills/echoRest/executions",
                JwtTestTokens.token("reader", List.of("REST_USER")), "{}");
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.body()).contains("issues", "message");
    }

    @Test
    void rejectsOversizeAndMalformedBodiesAtHttpBoundaryWithoutAdmission() throws Exception {
        String token = JwtTestTokens.token("body-owner", List.of("REST_USER"));
        int retained = coordinator.retainedCount();
        int queued = coordinator.queuedCount();
        long queuedBytes = coordinator.queuedBytes();

        for (String body : List.of("", "null", "[]", "1", "{broken", "{} {}", "{}")) {
            var response = sendBytes("/v1/skills/echoRest/executions", token,
                    body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.headers().firstValue("content-type").orElse(""))
                    .contains("application/problem+json");
            assertThat(response.headers().firstValue("cache-control")).contains("no-store");
            assertThat(coordinator.retainedCount()).isEqualTo(retained);
            assertThat(coordinator.queuedCount()).isEqualTo(queued);
            assertThat(coordinator.queuedBytes()).isEqualTo(queuedBytes);
        }

        String exact = "{\"message\":\"" + "é".repeat(24) + "x".repeat(2) + "\"}";
        assertThat(exact.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(64);
        var accepted = sendBytes("/v1/skills/echoRest/executions", token,
                exact.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(accepted.statusCode()).isEqualTo(202);
        poll(mapper.readTree(accepted.body()).path("id").asText(), token);
        int retainedAfterAccepted = coordinator.retainedCount();

        byte[] oversized = new byte[65];
        java.util.Arrays.fill(oversized, (byte) '{');
        var response = sendBytes("/v1/skills/echoRest/executions", token, oversized);
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.headers().firstValue("content-type").orElse(""))
                .contains("application/problem+json");
        assertThat(response.headers().firstValue("cache-control")).contains("no-store");
        assertThat(coordinator.retainedCount()).isEqualTo(retainedAfterAccepted);
        assertThat(coordinator.queuedCount()).isZero();
        assertThat(coordinator.queuedBytes()).isZero();
    }

    @Test
    void acceptsAndPollsExactOwnedResultWithWorkerJwt() throws Exception {
        String token = JwtTestTokens.token("owner", List.of("REST_USER"));
        var accepted = send("POST", "/v1/skills/echoRest/executions", token,
                "{\"message\":\"héllo\\nworld\"}");
        assertThat(accepted.statusCode()).isEqualTo(202);
        assertThat(accepted.headers().firstValue("location")).isPresent();
        String id = mapper.readTree(accepted.body()).path("id").asText();

        JsonNode completed = poll(id, token);
        assertThat(completed.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.path("result").asText()).isEqualTo("REST: héllo\nworld");
        assertThat(CALLBACK.lastToken.get()).isEqualTo(token);

        var foreign = send("GET", "/v1/executions/" + id,
                JwtTestTokens.token("someone-else", List.of("REST_USER")), null);
        assertThat(foreign.statusCode()).isEqualTo(404);
        var renewed = send("GET", "/v1/executions/" + id,
                JwtTestTokens.token("owner", List.of("REST_USER")), null);
        assertThat(renewed.statusCode()).isEqualTo(200);
    }

    @Test
    void returnsOwnedSkillFailureAsHttp200WithoutAStackTrace() throws Exception {
        String token = JwtTestTokens.token("owner-failure", List.of("REST_USER"));
        var accepted = send("POST", "/v1/skills/echoRest/executions", token,
                "{\"message\":\"fail\"}");
        String id = mapper.readTree(accepted.body()).path("id").asText();
        var polled = send("GET", "/v1/executions/" + id, token, null);
        JsonNode terminal = poll(id, token);
        assertThat(polled.statusCode()).isEqualTo(200);
        assertThat(terminal.path("status").asText()).isEqualTo("FAILED");
        assertThat(terminal.path("failure").path("kind").asText()).isEqualTo("SKILL_FAILURE");
        assertThat(terminal.path("failure").path("message").asText()).contains("HTTP status 503");
        assertThat(terminal.path("failure").path("message").asText()).doesNotContain("fixture-secret");
        assertThat(terminal.toString()).doesNotContain("stackTrace", "java.lang");
    }

    @Test
    void acceptedWorkOutlivesTheHttpResponseAndWorkerContextDoesNotLeak() throws Exception {
        CALLBACK.blockEntered = new CountDownLatch(1);
        CALLBACK.blockRelease = new CountDownLatch(1);
        CALLBACK.seenTokens.clear();
        String firstToken = JwtTestTokens.token("blocker", List.of("REST_USER"));
        var first = send("POST", "/v1/skills/echoRest/executions", firstToken,
                "{\"message\":\"block\"}");
        assertThat(CALLBACK.blockEntered.await(2, TimeUnit.SECONDS)).isTrue();

        String queuedToken = JwtTestTokens.token("https://issuer.test", "sidecar",
                "queued-owner", List.of("REST_USER"), 1);
        var queued = send("POST", "/v1/skills/echoRest/executions", queuedToken,
                "{\"message\":\"queued\"}");
        String queuedId = mapper.readTree(queued.body()).path("id").asText();
        assertThat(mapper.readTree(send("GET", "/v1/executions/" + queuedId, queuedToken, null).body())
                .path("status").asText()).isEqualTo("QUEUED");
        awaitUnauthorized(queuedId, queuedToken);
        CALLBACK.blockRelease.countDown();
        String renewedToken = JwtTestTokens.token("queued-owner", List.of("REST_USER"));
        JsonNode expiredAtCallback = poll(queuedId, renewedToken);
        assertThat(expiredAtCallback.path("status").asText()).isEqualTo("FAILED");
        assertThat(expiredAtCallback.path("failure").path("kind").asText()).isEqualTo("SKILL_FAILURE");
        assertThat(expiredAtCallback.path("failure").path("message").asText()).contains("HTTP status 401");
        assertThat(CALLBACK.seenTokens).containsSubsequence(firstToken, queuedToken);
    }

    @Test
    void nestedExecutionExposesTheOriginalJwtToTheRestHandler() throws Exception {
        MODEL_RESPONSES.add(completion("""
                {"capabilityName":"nestedRest","createdAt":"2026-09-13T00:00:00Z","status":"VALID",\
                "tasks":[{"taskId":"rest-task","title":"Call REST leaf","status":"PENDING",\
                "capabilityName":"echoRest","intent":"Echo through REST","dependsOn":[],\
                "expectedOutputs":["REST echo"],"parallelGroup":null,"note":""}]}
                """));
        MODEL_RESPONSES.add(completion("""
                {"stepAction":"CALL_TOOL","taskId":"rest-task","toolName":"echoRest",\
                "toolArguments":{"message":"nested-message"}}
                """));
        MODEL_RESPONSES.add(completion("""
                {"stepAction":"FINAL_RESPONSE","finalResponse":"nested result"}
                """));

        String token = JwtTestTokens.token("nested-owner", List.of("REST_USER"));
        var accepted = send("POST", "/v1/skills/nestedRest/executions", token,
                "{\"message\":\"start\"}");
        assertThat(accepted.statusCode()).isEqualTo(202);
        String id = mapper.readTree(accepted.body()).path("id").asText();
        assertThat(poll(id, token).path("result").asText()).isEqualTo("nested result");
        assertThat(CALLBACK.lastToken).hasValue(token);
        assertThat(CALLBACK.lastIssuer).hasValue("https://issuer.test");
        assertThat(CALLBACK.lastSubject).hasValue("nested-owner");
        assertThat(CALLBACK.lastRoles).contains("REST_USER");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void ownerCloseImmediatelyRefusesTrafficAndDiscardsWaitingWork() throws Exception {
        CALLBACK.blockEntered = new CountDownLatch(1);
        CALLBACK.blockRelease = new CountDownLatch(1);
        String token = JwtTestTokens.token("shutdown-owner", List.of("REST_USER"));
        var active = send("POST", "/v1/skills/echoRest/executions", token, "{\"message\":\"block\"}");
        assertThat(active.statusCode()).isEqualTo(202);
        assertThat(CALLBACK.blockEntered.await(2, TimeUnit.SECONDS)).isTrue();
        var waiting = send("POST", "/v1/skills/echoRest/executions", token, "{\"message\":\"queued\"}");
        String activeId = mapper.readTree(active.body()).path("id").asText();
        String waitingId = mapper.readTree(waiting.body()).path("id").asText();
        assertThat(coordinator.queuedCount()).isEqualTo(1);

        coordinator.onApplicationEvent(new ContextClosedEvent(applicationContext));
        assertThat(send("POST", "/v1/skills/echoRest/executions", token, "{\"message\":\"new\"}").statusCode())
                .isEqualTo(503);
        assertThat(send("GET", "/v1/executions/" + waitingId, token, null).statusCode()).isEqualTo(404);
        assertThat(coordinator.queuedCount()).isZero();
        assertThat(coordinator.queuedBytes()).isZero();

        CALLBACK.blockRelease.countDown();
        assertThat(poll(activeId, token).path("status").asText()).isEqualTo("COMPLETED");
    }

    private static String completion(String content) {
        return SidecarApplicationFixture.completion(content);
    }

    private JsonNode poll(String id, String token) throws Exception {
        for (int attempt = 0; attempt < 500; attempt++) {
            var response = send("GET", "/v1/executions/" + id, token, null);
            JsonNode body = mapper.readTree(response.body());
            if (body.path("status").asText().matches("COMPLETED|FAILED")) return body;
            Thread.sleep(10);
        }
        throw new AssertionError("Execution did not finish");
    }

    private void awaitUnauthorized(String id, String token) throws Exception {
        for (int attempt = 0; attempt < 60; attempt++) {
            if (send("GET", "/v1/executions/" + id, token, null).statusCode() == 401) return;
            Thread.sleep(50);
        }
        throw new AssertionError("Queued token did not expire at the HTTP boundary");
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> sendBytes(String path, String token, byte[] body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
