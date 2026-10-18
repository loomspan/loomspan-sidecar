package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "loomspan.observability.enabled=false",
        "loomspan-sidecar.trust.mode=file",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json"
})
class ManagementEndpointIntegrationTest
{
    @TempDir static Path storageDirectory;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry properties)
    {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }

    @LocalServerPort
    int applicationPort;

    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    WebEndpointsSupplier webEndpointsSupplier;

    @Autowired
    ExecutionCoordinator coordinator;

    @Autowired
    ConfigurableApplicationContext applicationContext;

    @Test
    void healthAndReadinessShareTheApplicationListener() throws Exception
    {
        assertThat(applicationContext.getEnvironment().getProperty("management.server.port")).isNull();

        HttpResponse<String> health = get(applicationPort, "/actuator/health");
        HttpResponse<String> readiness = get(applicationPort, "/actuator/health/readiness");
        HttpResponse<String> liveness = get(applicationPort, "/actuator/health/liveness");
        HttpResponse<String> info = get(applicationPort, "/actuator/info");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).contains("\"status\":\"UP\"");
        assertThat(info.statusCode()).isNotEqualTo(200);
        assertThat(health.body()).doesNotContain("components", "details", "kids", "path");
        assertThat(webEndpointsSupplier.getEndpoints())
                .extracting(endpoint -> endpoint.getEndpointId().toString())
                .containsExactly("health");

        coordinator.onApplicationEvent(new ContextClosedEvent(applicationContext));
        HttpResponse<String> refusing = get(applicationPort, "/actuator/health/readiness");
        HttpResponse<String> liveDuringRefusal = get(applicationPort, "/actuator/health/liveness");
        assertThat(refusing.statusCode()).isEqualTo(503);
        assertThat(refusing.body()).contains("OUT_OF_SERVICE");
        assertThat(liveDuringRefusal.statusCode()).isEqualTo(200);
        assertThat(liveDuringRefusal.body()).contains("\"status\":\"UP\"");
    }

    private HttpResponse<String> get(int port, String path) throws Exception
    {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
