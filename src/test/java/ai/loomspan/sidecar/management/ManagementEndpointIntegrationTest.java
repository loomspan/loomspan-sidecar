package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "loomspan.observability.enabled=false",
        "loomspan.skills.locations=classpath:/management-test-empty/**/*.yaml",
        "loomspan-sidecar.rest-routes-location=classpath:fixtures/rest-routes/empty.yaml",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
class ManagementEndpointIntegrationTest
{
    @LocalServerPort
    int applicationPort;

    @LocalManagementPort
    int managementPort;

    private final HttpClient client = HttpClient.newHttpClient();

    @Autowired
    WebEndpointsSupplier webEndpointsSupplier;

    @Autowired
    ExecutionCoordinator coordinator;

    @Autowired
    ConfigurableApplicationContext applicationContext;

    @Test
    void healthAndReadinessAreExposedOnlyOnSeparateManagementPort() throws Exception
    {
        assertThat(managementPort).isPositive().isNotEqualTo(applicationPort);

        HttpResponse<String> health = get(managementPort, "/actuator/health");
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");
        HttpResponse<String> liveness = get(managementPort, "/actuator/health/liveness");
        HttpResponse<String> info = get(managementPort, "/actuator/info");
        HttpResponse<String> applicationHealth = get(applicationPort, "/actuator/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"");
        assertThat(liveness.statusCode()).isEqualTo(200);
        assertThat(liveness.body()).contains("\"status\":\"UP\"");
        assertThat(info.statusCode()).isNotEqualTo(200);
        assertThat(applicationHealth.statusCode()).isNotEqualTo(200);
        assertThat(webEndpointsSupplier.getEndpoints())
                .extracting(endpoint -> endpoint.getEndpointId().toString())
                .containsExactly("health");

        coordinator.onApplicationEvent(new ContextClosedEvent(applicationContext));
        HttpResponse<String> refusing = get(managementPort, "/actuator/health/readiness");
        HttpResponse<String> liveDuringRefusal = get(managementPort, "/actuator/health/liveness");
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
