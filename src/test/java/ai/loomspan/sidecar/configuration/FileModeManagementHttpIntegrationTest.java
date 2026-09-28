package ai.loomspan.sidecar.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "loomspan-sidecar.configuration.mode=file", "loomspan.skills.locations=classpath:sidecar-managed-empty-skills/", "management.server.port=0", "server.servlet.session.cookie.secure=false",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
class FileModeManagementHttpIntegrationTest {
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "Long Password 123!";

    @Test void everyConfigurationMutationIsReadOnlyAndPagesExposeOnlyInspection() throws Exception {
        seed("file-admin@example.test", "admin");
        Browser browser = login("file-admin@example.test");
        String id = UUID.randomUUID().toString();
        String capability = "{\"editingSessionId\":\"" + id + "\",\"generation\":\"" + id + "\"}";
        String candidate = capability.substring(0, capability.length() - 1)
                + ",\"draftId\":\"" + id + "\",\"revision\":1,\"baseSnapshotId\":\"" + id + "\"}";
        String save = candidate.substring(0, candidate.length() - 1)
                + ",\"skillDocuments\":[],\"restRoutesYaml\":\"targets: {}\",\"executionConfigurationYaml\":\"loomspan: {}\"}";
        String credential = candidate.substring(0, candidate.length() - 1) + ",\"identifier\":\"fixture\"}";
        for (String action : new String[] {"", "/handoff", "/takeover"})
            readOnly(browser.post("/api/management/editing/lease" + action, "{\"label\":\"fixture\"}"));
        for (String action : new String[] {"renew", "release"})
            readOnly(browser.post("/api/management/editing/lease/" + action, capability));
        readOnly(browser.put("/api/management/editing/draft", save));
        readOnly(browser.post("/api/management/editing/draft/reconcile", save));
        readOnly(browser.post("/api/management/editing/draft/validate", candidate));
        readOnly(browser.delete("/api/management/editing/draft", candidate));
        readOnly(browser.delete("/api/management/editing/draft/credentials", credential));
        readOnly(browser.put("/api/management/editing/draft/credentials",
                credential.substring(0, credential.length() - 1) + ",\"value\":\"secret-marker\"}"));
        readOnly(browser.post("/api/management/configuration/publish", candidate));
        readOnly(browser.post("/api/management/configuration/rollback/" + id + "/review", "{}"));
        readOnly(browser.post("/api/management/configuration/rollback/" + id + "/load", candidate));
        readOnly(browser.multipart("/api/management/configuration/import/review", new byte[0], java.util.Map.of(), true));
        readOnly(browser.multipart("/api/management/configuration/import/load", new byte[0], java.util.Map.of(
                "editingSessionId", id, "generation", id, "draftId", id, "revision", "1", "baseSnapshotId", id), true));
        assertThat(ok(browser.get("/api/management/configuration/current")).path("readOnly").asBoolean()).isTrue();
        for (String page : new String[] {"/management/home", "/management/configuration/current", "/management/configuration/edit", "/management/configuration/import"}) {
            var response = browser.get(page);
            assertThat(response.statusCode()).as(page).isEqualTo(200);
            assertThat(response.body()).contains("File configuration").doesNotContain("id='editor-root'", "id='import-root'", "secret-marker");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM management_draft", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT initialized FROM configuration_store_state", Integer.class)).isZero();
        try (var playwright = com.microsoft.playwright.Playwright.create();
                var chromium = playwright.chromium().launch(new com.microsoft.playwright.BrowserType.LaunchOptions().setHeadless(true))) {
            var page = chromium.newPage();
            page.navigate(uri("/management/login").toString());
            page.locator("input[name=email]").fill("file-admin@example.test");
            page.locator("input[name=password]").fill(PASSWORD);
            page.locator("button:has-text('Sign in')").click();
            page.waitForURL("**/management/home");
            page.navigate(uri("/management/configuration/edit").toString());
            assertThat(page.textContent("body")).contains("Mode: file", "read-only", "restart");
            assertThat(page.locator("#editor-root, #import-root, #publish, #acquire").count()).isZero();
        }
    }
    private void readOnly(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("configuration_read_only");
    }

    private void seed(String email, String role) {
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, role, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(PASSWORD), Clock.systemUTC().millis());
    }
    private Browser login(String email) throws Exception {
        Browser browser = new Browser();
        String csrf = browser.get("/management/login").body().split("name='_csrf' value='")[1].split("'")[0];
        String body = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password="
                + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8) + "&_csrf="
                + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        assertThat(browser.postForm("/management/login", body).statusCode()).isEqualTo(302);
        browser.csrf = ok(browser.get("/api/management/session")).path("csrfToken").asText();
        return browser;
    }
    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private String csrf;
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, true); }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body, true); }
        HttpResponse<String> put(String path, String body) throws Exception { return send("PUT", path, body, true); }
        HttpResponse<String> delete(String path, String body) throws Exception { return send("DELETE", path, body, true); }
        HttpResponse<String> postForm(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> multipart(String path, byte[] bundle, java.util.Map<String, String> fields,
                boolean token) throws Exception {
            return client.send(multipartRequest(path, bundle, fields, token), HttpResponse.BodyHandlers.ofString());
        }
        private HttpRequest multipartRequest(String path, byte[] bundle, java.util.Map<String, String> fields,
                boolean token) throws Exception {
            String boundary = "sidecar-" + UUID.randomUUID();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"bundle.zip\"\r\n"
                    + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            bytes.write(bundle);
            bytes.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            for (var field : fields.entrySet()) {
                bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                        + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
            }
            bytes.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (token && csrf != null) request.header("X-CSRF-TOKEN", csrf);
            return request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
        }
        private HttpResponse<String> send(String method, String path, String body, boolean token) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path));
            if (body != null) builder.header("Content-Type", "application/json");
            if (token && csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
