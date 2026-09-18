package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.support.JwtTestTokens;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0", "server.servlet.session.cookie.secure=false",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
@Import(ManagementLockedExecutionIntegrationTest.LockedConfiguration.class)
class ManagementLockedExecutionIntegrationTest {
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("locked.db").toString());
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class LockedConfiguration {
        @Bean @Primary Supplier<String> testSetupCredential() { return () -> null; }
    }
    @LocalServerPort int port;

    @Test void lockedManagementDoesNotLockJwtExecution() throws Exception {
        var client = HttpClient.newHttpClient();
        var login = client.send(HttpRequest.newBuilder(uri("/management/login")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("Management setup is locked");
        var setup = client.send(HttpRequest.newBuilder(uri("/management/setup")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(setup.statusCode()).isEqualTo(200);
        assertThat(setup.body()).contains("Management setup is locked").doesNotContain("name='credential'");
        var token = JwtTestTokens.token("execution-fixture", List.of());
        var execution = client.send(HttpRequest.newBuilder(uri("/v1/skills"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(execution.statusCode()).isEqualTo(200);
        assertThat(execution.body()).isEqualTo("[]");
        var management = client.send(HttpRequest.newBuilder(uri("/api/management/session"))
                .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(management.statusCode()).isEqualTo(401);
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
