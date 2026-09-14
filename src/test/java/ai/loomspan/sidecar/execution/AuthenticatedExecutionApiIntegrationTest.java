package ai.loomspan.sidecar.execution;

import java.net.URI;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpServer;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillException;
import ai.loomspan.sidecar.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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
        "loomspan-sidecar.executions.max-queued=2"
})
@Import(AuthenticatedExecutionApiIntegrationTest.HandlerConfiguration.class)
class AuthenticatedExecutionApiIntegrationTest {
    private static final ConcurrentLinkedQueue<String> MODEL_RESPONSES = new ConcurrentLinkedQueue<>();
    private static final HttpServer MODEL_SERVER = modelServer();
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @DynamicPropertySource
    static void modelProperties(DynamicPropertyRegistry properties) {
        properties.add("loomspan.connections.fixture.driver", () -> "openai");
        properties.add("loomspan.connections.fixture.base-url",
                () -> "http://127.0.0.1:" + MODEL_SERVER.getAddress().getPort() + "/v1");
        properties.add("loomspan.connections.fixture.api-key", () -> "local-test-key");
        properties.add("loomspan.models.fixture-model.connection", () -> "fixture");
        properties.add("loomspan.models.fixture-model.provider-model", () -> "fixture-provider-model");
    }

    @AfterAll
    static void stopModelServer() {
        MODEL_SERVER.stop(0);
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
        assertThat(HandlerConfiguration.lastToken.get()).isEqualTo(token);

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
        assertThat(terminal.path("failure").path("message").asText()).isEqualTo("fixture skill failure");
        assertThat(terminal.toString()).doesNotContain("stackTrace", "java.lang");
    }

    @Test
    void queuedWorkKeepsItsOriginalJwtAfterExpiryWithoutContextLeakage() throws Exception {
        HandlerConfiguration.blockEntered = new CountDownLatch(1);
        HandlerConfiguration.blockRelease = new CountDownLatch(1);
        HandlerConfiguration.seenTokens.clear();
        String firstToken = JwtTestTokens.token("blocker", List.of("REST_USER"));
        var first = send("POST", "/v1/skills/echoRest/executions", firstToken,
                "{\"message\":\"block\"}");
        assertThat(HandlerConfiguration.blockEntered.await(2, TimeUnit.SECONDS)).isTrue();

        String queuedToken = JwtTestTokens.token("https://issuer.test", "sidecar",
                "queued-owner", List.of("REST_USER"), 1);
        var queued = send("POST", "/v1/skills/echoRest/executions", queuedToken,
                "{\"message\":\"queued\"}");
        String queuedId = mapper.readTree(queued.body()).path("id").asText();
        assertThat(mapper.readTree(send("GET", "/v1/executions/" + queuedId, queuedToken, null).body())
                .path("status").asText()).isEqualTo("QUEUED");
        awaitUnauthorized(queuedId, queuedToken);
        HandlerConfiguration.blockRelease.countDown();
        String renewedToken = JwtTestTokens.token("queued-owner", List.of("REST_USER"));
        assertThat(poll(queuedId, renewedToken).path("result").asText()).isEqualTo("REST: queued");
        assertThat(HandlerConfiguration.seenTokens).containsSubsequence(firstToken, queuedToken);
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
        assertThat(HandlerConfiguration.lastToken).hasValue(token);
    }

    private static String completion(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "").replace("\n", "");
        return "{\"id\":\"fixture\",\"object\":\"chat.completion\",\"created\":1,"
                + "\"model\":\"fixture-provider-model\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped
                + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    private static HttpServer modelServer() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                String response = MODEL_RESPONSES.poll();
                if (response == null) response = "{}";
                byte[] body = response.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            server.start();
            return server;
        } catch (java.io.IOException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private JsonNode poll(String id, String token) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
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

    @TestConfiguration(proxyBeanMethods = false)
    static class HandlerConfiguration {
        static final AtomicReference<String> lastToken = new AtomicReference<>();
        static final CopyOnWriteArrayList<String> seenTokens = new CopyOnWriteArrayList<>();
        static volatile CountDownLatch blockEntered = new CountDownLatch(0);
        static volatile CountDownLatch blockRelease = new CountDownLatch(0);

        @Bean
        RestSkillHandler restSkillHandler() {
            return invocation -> {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                lastToken.set(((JwtAuthenticationToken) authentication).getToken().getTokenValue());
                seenTokens.add(lastToken.get());
                if ("block".equals(invocation.input().get("message"))) {
                    blockEntered.countDown();
                    try {
                        if (!blockRelease.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("fixture interrupted", failure);
                    }
                }
                if ("fail".equals(invocation.input().get("message"))) {
                    throw new SkillException("fixture skill failure");
                }
                return "REST: " + invocation.input().get("message");
            };
        }
    }
}
