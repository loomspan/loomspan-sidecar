package ai.loomspan.sidecar.execution;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import ai.loomspan.sidecar.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "loomspan.observability.enabled=false",
        "loomspan.skills.locations=classpath*:fixtures/execution-skills/echo-rest.yml",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem",
        "loomspan-sidecar.auth.jwt.roles-claim=groups",
        "loomspan-sidecar.auth.jwt.role-prefix=APP_"
})
@Import(AuthenticatedExecutionApiIntegrationTest.HandlerConfiguration.class)
class CustomRoleExecutionIntegrationTest {
    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesCustomRoleClaimAndPrefixForValidationAndInvocation() throws Exception {
        String allowed = JwtTestTokens.tokenWithAuthoritiesClaim("custom-owner", "groups", List.of("REST_USER"));
        var accepted = post(allowed);
        assertThat(accepted.statusCode()).isEqualTo(202);
        String id = mapper.readTree(accepted.body()).path("id").asText();
        assertThat(poll(id, allowed).path("result").asText()).isEqualTo("REST: custom-role");

        for (String denied : List.of(
                JwtTestTokens.tokenWithAuthoritiesClaim("wrong", "groups", List.of("OTHER")),
                JwtTestTokens.tokenWithAuthoritiesClaim("empty", "groups", List.of()),
                JwtTestTokens.token("legacy-claim", List.of("REST_USER")))) {
            assertThat(post(denied).statusCode()).isEqualTo(403);
        }
    }

    private HttpResponse<String> post(String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/v1/skills/echoRest/executions"))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"message\":\"custom-role\"}"))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode poll(String id, String token) throws Exception {
        for (int attempt = 0; attempt < 100; attempt++) {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/executions/" + id))
                    .timeout(Duration.ofSeconds(5)).header("Authorization", "Bearer " + token).GET().build();
            JsonNode body = mapper.readTree(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (body.path("status").asText().matches("COMPLETED|FAILED")) return body;
            Thread.sleep(10);
        }
        throw new AssertionError("Execution did not finish");
    }
}
