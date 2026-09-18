package ai.loomspan.sidecar.security;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import ai.loomspan.sidecar.support.JwtTestTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "loomspan.observability.enabled=true",
        "loomspan.observability.auth.api-key=0123456789abcdef0123456789abcdef",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
class ConsoleSecurityIntegrationTest {
    @TempDir static Path storageDirectory;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }

    @LocalServerPort int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void consoleApiKeyAndExecutionJwtRemainIndependent() throws Exception {
        var console = request("/_loomspan/observability/v1/skills")
                .header("X-loomspan-Api-Key", "0123456789abcdef0123456789abcdef").build();
        assertThat(send(console).statusCode()).isEqualTo(200);

        var apiWithKey = request("/v1/skills")
                .header("X-loomspan-Api-Key", "0123456789abcdef0123456789abcdef").build();
        assertThat(send(apiWithKey).statusCode()).isEqualTo(401);

        var consoleWithJwt = request("/_loomspan/observability/v1/skills")
                .header("Authorization", "Bearer " + JwtTestTokens.token("operator", List.of())).build();
        assertThat(send(consoleWithJwt).statusCode()).isEqualTo(401);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
