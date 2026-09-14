package ai.loomspan.sidecar.management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0",
        "loomspan.observability.enabled=false",
        "loomspan.skills.locations=classpath:/management-test-empty/**/*.yaml"
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

    @Test
    void healthAndReadinessAreExposedOnlyOnSeparateManagementPort() throws Exception
    {
        assertThat(managementPort).isPositive().isNotEqualTo(applicationPort);

        HttpResponse<String> health = get(managementPort, "/actuator/health");
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");
        HttpResponse<String> info = get(managementPort, "/actuator/info");
        HttpResponse<String> applicationHealth = get(applicationPort, "/actuator/health");

        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"");
        assertThat(info.statusCode()).isNotEqualTo(200);
        assertThat(applicationHealth.statusCode()).isNotEqualTo(200);
        assertThat(webEndpointsSupplier.getEndpoints())
                .extracting(endpoint -> endpoint.getEndpointId().toString())
                .containsExactly("health");
    }

    private HttpResponse<String> get(int port, String path) throws Exception
    {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
