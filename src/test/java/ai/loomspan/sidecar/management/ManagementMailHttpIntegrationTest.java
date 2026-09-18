package ai.loomspan.sidecar.management;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
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
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem",
        "loomspan-sidecar.management.mail-from=operator@example.test",
        "loomspan-sidecar.management.external-base-url=https://console.example.test",
        "spring.mail.properties.mail.smtp.starttls.enable=false"
})
@Import(ManagementMailHttpIntegrationTest.CredentialConfiguration.class)
class ManagementMailHttpIntegrationTest {
    @TempDir static Path storageDirectory;
    private static final String SETUP;
    private static ManagementMailIntegrationTest.LocalSmtp smtp;
    static {
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        SETUP = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) throws Exception {
        smtp = new ManagementMailIntegrationTest.LocalSmtp();
        registry.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("mail-http.db").toString());
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", smtp::port);
    }
    @AfterAll static void closeSmtp() throws Exception { if (smtp != null) smtp.close(); }
    @TestConfiguration(proxyBeanMethods = false)
    static class CredentialConfiguration {
        @Bean @Primary Supplier<String> testSetupCredential() { return () -> SETUP; }
    }
    @Autowired ManagementAccountRepository accounts;
    @LocalServerPort int port;

    @Test void setupInvitationAndRecoveryUseConfiguredMailOrigin() throws Exception {
        var browser = new Browser();
        String csrf = formCsrf(browser.get("/management/setup").body());
        var setup = browser.post("/api/management/setup", "{\"credential\":\"" + SETUP
                + "\",\"email\":\"Admin@Example.Test\"}", csrf);
        assertThat(setup.statusCode()).isEqualTo(202);
        assertThat(smtp.messages()).hasSize(1);
        assertThat(smtp.messages().getFirst()).contains("From: operator@example.test",
                "To: admin@example.test", "https://console.example.test/management/password/set?token=")
                .doesNotContain("evil.example");
        String token = linkToken(smtp.messages().getFirst());
        assertThat(accounts.byEmail("admin@example.test").active()).isFalse();
        var prelogin = browser.postForm("/management/login", "email=admin%40example.test&password=Long+Password+123%21&_csrf=" + csrf);
        assertThat(prelogin.headers().firstValue("Location").orElseThrow()).endsWith("/management/login?error");
        assertThat(browser.post("/api/management/password/set", "{\"token\":\"" + token
                + "\",\"password\":\"Long Password 123!\"}", csrf).statusCode()).isEqualTo(204);
        assertThat(browser.post("/api/management/password/set", "{\"token\":\"" + token
                + "\",\"password\":\"Long Password 123!\"}", csrf).statusCode()).isEqualTo(400);
        assertThat(browser.postForm("/management/login", "email=admin%40example.test&password=Long+Password+123%21&_csrf=" + csrf)
                .statusCode()).isEqualTo(302);
        String sessionCsrf = jsonCsrf(browser.get("/api/management/session").body());
        assertThat(browser.post("/api/management/accounts", "{\"email\":\"viewer@example.test\",\"role\":\"viewer\"}", sessionCsrf)
                .statusCode()).isEqualTo(201);
        assertThat(smtp.messages().getLast()).contains("To: viewer@example.test", "/management/password/set?token=");
        long invitedId = accounts.byEmail("viewer@example.test").id();
        var altered = browser.patch("/api/management/accounts/" + invitedId,
                "{\"role\":\"editor\",\"enabled\":false}", sessionCsrf);
        assertThat(altered.statusCode()).as(altered.body()).isEqualTo(200);
        assertThat(accounts.byId(invitedId).enabled()).isFalse();
        assertThat(browser.patch("/api/management/accounts/" + invitedId,
                "{\"enabled\":true}", sessionCsrf).statusCode()).isEqualTo(200);
        assertThat(accounts.byId(invitedId).role()).isEqualTo("editor");
        assertThat(browser.post("/api/management/accounts/" + invitedId + "/resend-invite", "{}", sessionCsrf)
                .statusCode()).isEqualTo(202);
        assertThat(smtp.messages().getLast()).contains("To: viewer@example.test", "/management/password/set?token=");
        var known = browser.post("/api/management/password/forgot", "{\"email\":\"admin@example.test\"}", sessionCsrf);
        var unknown = browser.post("/api/management/password/forgot", "{\"email\":\"missing@example.test\"}", sessionCsrf);
        assertThat(known.statusCode()).isEqualTo(202);
        assertThat(unknown.statusCode()).isEqualTo(202);
        assertThat(known.body()).isEqualTo(unknown.body());
        assertThat(smtp.messages().getLast()).contains("/management/password/reset?token=");
        String reset = linkToken(smtp.messages().getLast());
        assertThat(browser.post("/api/management/password/reset", "{\"token\":\"" + reset
                + "\",\"password\":\"Another Long Password 1!\"}", sessionCsrf).statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(401);
    }
    private static String formCsrf(String html) {
        var matcher = Pattern.compile("name='_csrf' value='([^']+)'").matcher(html);
        assertThat(matcher.find()).isTrue(); return matcher.group(1);
    }
    private static String jsonCsrf(String json) {
        var matcher = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).isTrue(); return matcher.group(1);
    }
    private static String linkToken(String message) {
        var matcher = Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(message);
        assertThat(matcher.find()).isTrue(); return matcher.group(1);
    }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> get(String path) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).GET().build());
        }
        HttpResponse<String> post(String path, String body, String csrf) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf).header("X-Forwarded-Host", "evil.example")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }
        HttpResponse<String> patch(String path, String body, String csrf) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf)
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build());
        }
        HttpResponse<String> postForm(String path, String body) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }
        private HttpResponse<String> send(HttpRequest request) throws Exception {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
