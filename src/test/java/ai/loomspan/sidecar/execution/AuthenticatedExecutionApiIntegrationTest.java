package ai.loomspan.sidecar.execution;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.api.io.TempDir;
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
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem",
        "loomspan-sidecar.auth.jwt.clock-skew=0s",
        "loomspan-sidecar.executions.max-concurrent=1",
        "loomspan-sidecar.executions.max-queued=2",
        "loomspan-sidecar.executions.max-input-size=64B",
        "loomspan-sidecar.executions.max-queued-input-size=32B"
})
class AuthenticatedExecutionApiIntegrationTest {
    @TempDir static Path storageDirectory;
    private static final SidecarApplicationFixture FIXTURE = new SidecarApplicationFixture();
    private static final ConcurrentLinkedQueue<String> MODEL_RESPONSES = FIXTURE.modelResponses();
    private static final SidecarApplicationFixture.CallbackFixture CALLBACK = FIXTURE.callback();
    private static final Path ROUTES = FIXTURE.routes();
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    @Autowired ExecutionCoordinator coordinator;
    @Autowired ai.loomspan.sidecar.storage.ConfigurationSnapshotStore snapshots;
    @Autowired ai.loomspan.sidecar.configuration.RuntimeConfigurationService runtimeConfiguration;
    @Autowired ai.loomspan.sidecar.rest.GenerationRestResources restGenerations;
    @Autowired ConfigurableApplicationContext applicationContext;

    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> SidecarApplicationFixture.seedDatabase(
                storageDirectory.resolve("sidecar.db"), List.of(
                        SidecarApplicationFixture.resourceFile("fixtures/execution-skills/echo-rest.yml"),
                        SidecarApplicationFixture.resourceFile("fixtures/execution-skills/nested-rest.yml")),
                readRoutes()).toString());
        properties.add("loomspan.connections.fixture.driver", () -> "openai");
        properties.add("loomspan.connections.fixture.base-url",
                () -> "http://127.0.0.1:" + FIXTURE.modelPort() + "/v1");
        properties.add("loomspan.connections.fixture.api-key", () -> "local-test-key");
        properties.add("loomspan.models.fixture-model.connection", () -> "fixture");
        properties.add("loomspan.models.fixture-model.provider-model", () -> "fixture-provider-model");
    }

    private static String readRoutes() {
        try { return Files.readString(ROUTES); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
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
    void preservesMvcProblemStatusesWithoutWeakeningAuthentication() throws Exception {
        String token = JwtTestTokens.token("reader", List.of());
        var invalidId = send("GET", "/v1/executions/not-a-uuid", token, null);
        var wrongMethod = send("DELETE", "/v1/skills", token, null);
        assertThat(invalidId.statusCode()).isEqualTo(400);
        assertThat(wrongMethod.statusCode()).isEqualTo(405);
        for (var response : List.of(invalidId, wrongMethod)) {
            assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/problem+json");
            assertThat(response.headers().firstValue("cache-control")).contains("no-store");
            assertThat(mapper.readTree(response.body()).path("status").asInt()).isEqualTo(response.statusCode());
        }
        assertThat(send("GET", "/v1/executions/not-a-uuid", null, null).statusCode()).isEqualTo(401);
        assertThat(send("GET", "/error", token, null).statusCode()).isEqualTo(403);
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
    void chunkedRequestsEnforceTheRawUtf8CapWithoutContentLength() throws Exception {
        String token = JwtTestTokens.token("chunked-owner", List.of("REST_USER"));
        String exact = "{\"message\":\"" + "é".repeat(25) + "\"}";
        assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(64);
        var accepted = sendChunked(token, exact);
        assertThat(accepted.statusCode()).isEqualTo(202);
        poll(mapper.readTree(accepted.body()).path("id").asText(), token);

        int retained = coordinator.retainedCount();
        var oversized = sendChunked(token, "{\"message\":\"" + "é".repeat(26) + "\"}");
        assertThat(oversized.statusCode()).isEqualTo(413);
        assertThat(oversized.headers().firstValue("content-type").orElse(""))
                .contains("application/problem+json");
        assertThat(coordinator.retainedCount()).isEqualTo(retained);
    }

    @Test
    void httpUtf8SerializationUsesBytesForQueuedCapacity() throws Exception {
        CALLBACK.blockEntered = new CountDownLatch(1);
        CALLBACK.blockRelease = new CountDownLatch(1);
        String token = JwtTestTokens.token("utf8-owner", List.of("REST_USER"));
        try {
            var active = send("POST", "/v1/skills/echoRest/executions", token, "{\"message\":\"block\"}");
            assertThat(active.statusCode()).isEqualTo(202);
            assertThat(CALLBACK.blockEntered.await(2, TimeUnit.SECONDS)).isTrue();

            String exact = "{\"message\":\"" + "é".repeat(9) + "\"}";
            assertThat(exact.length()).isLessThan(32);
            assertThat(exact.getBytes(StandardCharsets.UTF_8)).hasSize(32);
            String padded = "{  \"message\" : \"" + "é".repeat(9) + "\"  }";
            assertThat(padded.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(32);
            var queued = send("POST", "/v1/skills/echoRest/executions", token, padded);
            assertThat(queued.statusCode()).isEqualTo(202);
            assertThat(coordinator.queuedCount()).isEqualTo(1);
            assertThat(coordinator.queuedBytes()).isEqualTo(32);

            int retained = coordinator.retainedCount();
            var excess = send("POST", "/v1/skills/echoRest/executions", token,
                    "{\"message\":\"" + "é".repeat(10) + "\"}");
            assertThat(excess.statusCode()).isEqualTo(429);
            assertThat(coordinator.retainedCount()).isEqualTo(retained);
            assertThat(coordinator.queuedBytes()).isEqualTo(32);

            CALLBACK.blockRelease.countDown();
            assertThat(poll(mapper.readTree(queued.body()).path("id").asText(), token)
                    .path("result").asText()).isEqualTo("REST: " + "é".repeat(9));
            poll(mapper.readTree(active.body()).path("id").asText(), token);
        } finally {
            CALLBACK.blockRelease.countDown();
        }
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
        assertThat(completed.path("configurationSnapshotId").asText()).isEqualTo(snapshots.current().localId().toString());
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
        assertThat(mapper.readTree(send("GET", "/v1/executions/" + queuedId, queuedToken, null).body())
                .path("configurationSnapshotId").isNull()).isTrue();
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
        JsonNode nested = poll(id, token);
        assertThat(nested.path("result").asText()).isEqualTo("nested result");
        assertThat(nested.path("configurationSnapshotId").asText()).isEqualTo(snapshots.current().localId().toString());
        assertThat(CALLBACK.lastToken).hasValue(token);
        assertThat(CALLBACK.lastIssuer).hasValue("https://issuer.test");
        assertThat(CALLBACK.lastSubject).hasValue("nested-owner");
        assertThat(CALLBACK.lastRoles).contains("REST_USER");
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void nestedRestCallAfterPublicationKeepsItsAdmittedGeneration() throws Exception {
        var original = snapshots.current();
        MODEL_RESPONSES.add(completion("""
                {"capabilityName":"nestedRest","createdAt":"2026-09-13T00:00:00Z","status":"VALID",\
                "tasks":[{"taskId":"rest-task","title":"Call REST leaf","status":"PENDING",\
                "capabilityName":"echoRest","intent":"Echo through REST","dependsOn":[],\
                "expectedOutputs":["REST echo"],"parallelGroup":null,"note":""}]}
                """));
        FIXTURE.blockNextModelResponse();
        MODEL_RESPONSES.add(completion("""
                {"stepAction":"CALL_TOOL","taskId":"rest-task","toolName":"echoRest",\
                "toolArguments":{"message":"nested-after-publication"}}
                """));
        MODEL_RESPONSES.add(completion("""
                {"stepAction":"FINAL_RESPONSE","finalResponse":"nested result"}
                """));
        String token = JwtTestTokens.token("nested-publication-owner", List.of("REST_USER"));
        HttpServer second = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var newCalls = new java.util.concurrent.atomic.AtomicInteger();
        second.createContext("/echo", exchange -> {
            newCalls.incrementAndGet();
            byte[] body = "new-generation".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        second.start();
        try {
            var accepted = send("POST", "/v1/skills/nestedRest/executions", token, "{\"message\":\"start\"}");
            assertThat(accepted.statusCode()).isEqualTo(202);
            assertThat(FIXTURE.awaitModelBlock(5, TimeUnit.SECONDS)).isTrue();
            String newRoutes = Files.readString(ROUTES).replace(
                    "http://127.0.0.1:" + FIXTURE.callbackPort(),
                    "http://127.0.0.1:" + second.getAddress().getPort());
            var draft = new ai.loomspan.sidecar.storage.ConfigurationDraft(original);
            draft.replaceContent(new ai.loomspan.sidecar.storage.ManagedConfiguration(
                    original.configuration().skillDocuments(), newRoutes));
            assertThat(runtimeConfiguration.validate(draft).successful()).isTrue();
            var published = runtimeConfiguration.publish(draft::validatedCandidate);
            assertThat(restGenerations.protectedIds()).contains(original.localId(), published.localId());
            FIXTURE.releaseModelBlock();
            var nested = poll(mapper.readTree(accepted.body()).path("id").asText(), token);
            assertThat(nested.path("status").asText()).isEqualTo("COMPLETED");
            assertThat(nested.path("configurationSnapshotId").asText()).isEqualTo(original.localId().toString());
            assertThat(CALLBACK.lastToken).hasValue(token);
            assertThat(newCalls).hasValue(0);
            var next = send("POST", "/v1/skills/echoRest/executions", token,
                    "{\"message\":\"new\"}");
            assertThat(poll(mapper.readTree(next.body()).path("id").asText(), token)
                    .path("result").asText()).isEqualTo("new-generation");
            assertThat(newCalls).hasValue(1);
        } finally {
            FIXTURE.releaseModelBlock();
            restoreConfiguration(original.configuration());
            second.stop(0);
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void admittedRestKeepsOldGenerationAndDurableIdAcrossRoutePublication() throws Exception {
        CALLBACK.blockEntered = new CountDownLatch(1);
        CALLBACK.blockRelease = new CountDownLatch(1);
        String token = JwtTestTokens.token("publication-owner", List.of("REST_USER"));
        var old = snapshots.current();
        var first = send("POST", "/v1/skills/echoRest/executions", token, "{\"message\":\"block\"}");
        assertThat(first.statusCode()).isEqualTo(202);
        assertThat(CALLBACK.blockEntered.await(2, TimeUnit.SECONDS)).isTrue();

        HttpServer second = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        second.createContext("/echo", exchange -> {
            byte[] body = "new-generation".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        second.start();
        try {
            String newRoutes = Files.readString(ROUTES).replace(
                    "http://127.0.0.1:" + FIXTURE.callbackPort(),
                    "http://127.0.0.1:" + second.getAddress().getPort());
            var draft = new ai.loomspan.sidecar.storage.ConfigurationDraft(old);
            draft.replaceContent(new ai.loomspan.sidecar.storage.ManagedConfiguration(
                    old.configuration().skillDocuments(), newRoutes));
            assertThat(runtimeConfiguration.validate(draft).successful()).isTrue();
            runtimeConfiguration.publish(draft::validatedCandidate);
            for (int index = 0; index < 11; index++) {
                var nextDraft = new ai.loomspan.sidecar.storage.ConfigurationDraft(snapshots.current());
                nextDraft.replaceContent(new ai.loomspan.sidecar.storage.ManagedConfiguration(
                        old.configuration().skillDocuments(), newRoutes));
                assertThat(runtimeConfiguration.validate(nextDraft).successful()).isTrue();
                runtimeConfiguration.publish(nextDraft::validatedCandidate);
            }
            assertThat(snapshots.findByLocalId(old.localId())).isNotNull();
            var currentPublished = snapshots.current().localId();
            CALLBACK.blockRelease.countDown();
            JsonNode prior = poll(mapper.readTree(first.body()).path("id").asText(), token);
            assertThat(prior.path("result").asText()).isEqualTo("REST: block");
            assertThat(prior.path("configurationSnapshotId").asText()).isEqualTo(old.localId().toString());
            var next = send("POST", "/v1/skills/echoRest/executions", token, "{\"message\":\"new\"}");
            JsonNode current = poll(mapper.readTree(next.body()).path("id").asText(), token);
            assertThat(current.path("result").asText()).isEqualTo("new-generation");
            assertThat(current.path("configurationSnapshotId").asText()).isEqualTo(currentPublished.toString());
            long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
            while (restGenerations.protectedIds().contains(old.localId()) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(restGenerations.protectedIds()).doesNotContain(old.localId());
            var finalDraft = new ai.loomspan.sidecar.storage.ConfigurationDraft(snapshots.current());
            finalDraft.replaceContent(new ai.loomspan.sidecar.storage.ManagedConfiguration(
                    old.configuration().skillDocuments(), newRoutes));
            assertThat(runtimeConfiguration.validate(finalDraft).successful()).isTrue();
            runtimeConfiguration.publish(finalDraft::validatedCandidate);
            assertThat(snapshots.findByLocalId(old.localId())).isNull();
        } finally {
            CALLBACK.blockRelease.countDown();
            restoreConfiguration(old.configuration());
            second.stop(0);
        }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void discoveryAndPostPrecheckFollowPublishedCompleteReplacement() throws Exception {
        String token = JwtTestTokens.token("current-catalog-owner", List.of("REST_USER"));
        var original = snapshots.current().configuration();
        assertThat(send("GET", "/v1/skills", token, null).body()).contains("echoRest");
        try {
            var draft = new ai.loomspan.sidecar.storage.ConfigurationDraft(snapshots.current());
            draft.replaceContent(new ai.loomspan.sidecar.storage.ManagedConfiguration(List.of(),
                    ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            assertThat(runtimeConfiguration.validate(draft).successful()).isTrue();
            runtimeConfiguration.publish(draft::validatedCandidate);
            assertThat(mapper.readTree(send("GET", "/v1/skills", token, null).body()).isEmpty()).isTrue();
            assertThat(send("POST", "/v1/skills/echoRest/executions", token, "{}").statusCode()).isEqualTo(404);
        } finally { restoreConfiguration(original); }
    }

    private void restoreConfiguration(ai.loomspan.sidecar.storage.ManagedConfiguration original) {
        if (snapshots.current().configuration().equals(original)) return;
        var restore = new ai.loomspan.sidecar.storage.ConfigurationDraft(snapshots.current());
        restore.replaceContent(original);
        if (!runtimeConfiguration.validate(restore).successful()) throw new AssertionError("Fixture restore validation failed");
        runtimeConfiguration.publish(restore::validatedCandidate);
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

    private HttpResponse<String> sendChunked(String token, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
        assertThat(publisher.contentLength()).isEqualTo(-1);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/v1/skills/echoRest/executions"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(publisher).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

}
