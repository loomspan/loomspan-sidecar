package ai.loomspan.sidecar.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ai.loomspan.sidecar.support.JwtTestTokens;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0", "server.servlet.session.cookie.secure=false",
        "loomspan-sidecar.management.session-idle-timeout=30m",
        "loomspan-sidecar.management.edit-lease-timeout=15m",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
@Import(ManagementEditingHttpIntegrationTest.TimeConfiguration.class)
class ManagementEditingHttpIntegrationTest {
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-18T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration amount) { now = now.plus(amount); }
        void reset() { now = Instant.parse("2026-09-18T00:00:00Z"); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired ManagementIdentityService identity;
    @Autowired ManagementEditingState editing;
    @Autowired ai.loomspan.sidecar.configuration.RuntimeConfigurationService runtime;
    private final ObjectMapper json = new ObjectMapper();
    @LocalServerPort int port;
    private static final String PASSWORD = "Long Password 123!";

    @BeforeEach void resetEditing() {
        synchronized (editing) { editing.clearAll(); }
        clock.reset();
    }

    @Test void draftAcquisitionIsPrivateToItsManagementSession() throws Exception {
        seed("private-a@example.test", "editor");
        seed("private-b@example.test", "editor");
        seed("private-admin@example.test", "admin");
        Browser a = login("private-a@example.test");
        Browser b = login("private-b@example.test");
        Browser sameAccount = login("private-a@example.test");
        Browser admin = login("private-admin@example.test");
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        String tab = UUID.randomUUID().toString();
        JsonNode grant = body(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab
                + "\",\"accountId\":999999}"));
        assertThat(grant.path("grantId").asText()).isNotBlank();
        String candidate = grant.path("draft").path("candidateId").asText();
        var runningBeforeSave = runtime.inspect().publishedId();
        String secret = "private-${SECRET}-literal";
        String save = "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + grant.path("grantId").asText()
                + "\",\"expectedCandidateId\":\"" + candidate
                + "\",\"skillDocuments\":[{\"sourceName\":\"literal.yaml\",\"yaml\":\"" + secret
                + "\"}],\"restRoutesYaml\":\"routes: {}\\n" + secret + "\"}";
        assertThat(a.put("/api/management/editing/draft", save).statusCode()).isEqualTo(200);
        assertThat(runtime.inspect().publishedId()).isEqualTo(runningBeforeSave);
        synchronized (editing) {
            var entry = editing.drafts.values().stream().filter(value -> value.accountId == identity.account("private-a@example.test").id())
                    .findFirst().orElseThrow();
            var frozen = entry.draft.freeze();
            assertThat(entry.draft.recordValidation(frozen, new ai.loomspan.sidecar.storage.ConfigurationValidationResult(false,
                    java.util.List.of(new ai.loomspan.sidecar.storage.ConfigurationValidationIssue(
                            ai.loomspan.sidecar.storage.ConfigurationValidationIssue.Severity.ERROR,
                            "literal.yaml", null, null, "private-validation-marker"))))).isTrue();
        }
        assertThat(a.get("/api/management/editing/draft").body()).contains(secret, "private-validation-marker");
        for (Browser other : new Browser[] {b, sameAccount, admin}) {
            assertThat(other.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
            var status = other.get("/api/management/editing");
            assertThat(status.statusCode()).isEqualTo(200);
            assertThat(status.body()).contains("\"held\":true", "\"mine\":false")
                    .doesNotContain(secret, candidate, grant.path("grantId").asText(), "private-validation-marker");
            assertThat(other.post("/api/management/editing/lease", "{\"tabId\":\"" + UUID.randomUUID()
                    + "\"}").statusCode()).isEqualTo(409);
        }
        assertThat(a.get("/api/management/editing/draft").headers().firstValue("Cache-Control")).hasValue("no-store");
    }

    @Test void staleTabGrantCandidateAndTakeoverCannotMutateForeignDraft() throws Exception {
        seed("stale-a@example.test", "editor");
        seed("stale-admin@example.test", "admin");
        Browser a = login("stale-a@example.test");
        Browser admin = login("stale-admin@example.test");
        String tab = UUID.randomUUID().toString();
        JsonNode first = body(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String grant = first.path("grantId").asText();
        String candidate = first.path("draft").path("candidateId").asText();
        assertThat(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}").statusCode()).isEqualTo(409);
        assertThat(a.post("/api/management/editing/lease/takeover", "{\"tabId\":\"" + tab + "\"}")
                .statusCode()).isEqualTo(403);
        assertThat(a.put("/api/management/editing/draft", save(UUID.randomUUID().toString(), grant, candidate, "wrong-tab"))
                .statusCode()).isEqualTo(409);
        JsonNode edited = body(a.put("/api/management/editing/draft", save(tab, grant, candidate, "kept-literal")));
        assertThat(edited.path("candidateId").asText()).isNotEqualTo(candidate);
        JsonNode equalReplacement = body(a.put("/api/management/editing/draft", save(tab, grant,
                edited.path("candidateId").asText(), "kept-literal")));
        assertThat(equalReplacement.path("candidateId").asText()).isNotEqualTo(edited.path("candidateId").asText());
        assertThat(equalReplacement.path("validation").isNull()).isTrue();
        assertThat(a.put("/api/management/editing/draft", save(tab, grant, candidate, "stale-edit"))
                .statusCode()).isEqualTo(409);
        assertThat(a.get("/api/management/editing/draft").body()).contains("kept-literal").doesNotContain("stale-edit");
        assertThat(a.post("/api/management/editing/lease/release", cap(tab, grant)).statusCode()).isEqualTo(204);
        JsonNode resumed = body(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        assertThat(resumed.path("grantId").asText()).isNotEqualTo(grant);
        assertThat(resumed.path("draft").path("draftId").asText()).isEqualTo(first.path("draft").path("draftId").asText());
        assertThat(a.put("/api/management/editing/draft", save(tab, grant,
                equalReplacement.path("candidateId").asText(), "old-grant")).statusCode()).isEqualTo(409);
        JsonNode taken = body(admin.post("/api/management/editing/lease/takeover",
                "{\"tabId\":\"" + UUID.randomUUID() + "\"}"));
        assertThat(taken.path("draft").path("configuration").toString()).doesNotContain("kept-literal");
        assertThat(a.get("/api/management/editing/draft").body()).contains("kept-literal");
        assertThat(a.put("/api/management/editing/draft", save(tab, resumed.path("grantId").asText(),
                equalReplacement.path("candidateId").asText(), "after-takeover")).statusCode()).isEqualTo(409);
        assertThat(a.delete("/api/management/editing/draft", null).statusCode()).isEqualTo(204);
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
    }

    @Test void activityExpiryLogoutAndRolesAreEnforced() throws Exception {
        long editorId = seed("activity-a@example.test", "editor");
        seed("activity-viewer@example.test", "viewer");
        Browser a = login("activity-a@example.test");
        Browser viewer = login("activity-viewer@example.test");
        String tab = UUID.randomUUID().toString();
        JsonNode grant = body(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String grantId = grant.path("grantId").asText();
        assertThat(viewer.post("/api/management/editing/lease", "{\"tabId\":\"" + UUID.randomUUID()
                + "\"}").statusCode()).isEqualTo(403);
        assertThat(viewer.get("/api/management/editing").statusCode()).isEqualTo(200);
        assertThat(viewer.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        assertThat(a.postWithoutCsrf("/api/management/editing/lease/activity", cap(tab, grantId)).statusCode())
                .isEqualTo(403);
        assertThat(a.put("/api/management/editing/draft", "{\"tabId\":\"" + tab
                + "\",\"grantId\":\"" + grantId + "\",\"expectedCandidateId\":\""
                + grant.path("draft").path("candidateId").asText() + "\"}").statusCode()).isEqualTo(400);
        assertThat(new Browser().get("/api/management/editing").statusCode()).isEqualTo(401);
        assertThat(new Browser().getJwt("/api/management/editing", JwtTestTokens.token("operator", java.util.List.of()))
                .statusCode()).isEqualTo(401);
        clock.advance(Duration.ofSeconds(29));
        assertThat(a.post("/api/management/editing/lease/activity", cap(tab, grantId)).statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(14).plusSeconds(31));
        assertThat(a.get("/api/management/editing").body()).contains("\"held\":false");
        assertThat(a.post("/api/management/editing/lease/activity", cap(tab, grantId)).statusCode()).isEqualTo(409);
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
        JsonNode resumed = body(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        assertThat(resumed.path("grantId").asText()).isNotEqualTo(grantId);
        assertThat(a.post("/api/management/session/activity", "{}").statusCode()).isEqualTo(204);
        clock.advance(Duration.ofMinutes(29));
        assertThat(a.get("/api/management/editing").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(1));
        assertThat(a.post("/api/management/editing/lease/activity", cap(tab,
                resumed.path("grantId").asText())).statusCode()).isIn(401, 403);
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(401);
        assertThat(editorId).isPositive();
    }

    @Test void acceptedActivityRenewsBothDeadlinesAndPollingDoesNot() throws Exception {
        seed("renew-a@example.test", "editor");
        Browser a = login("renew-a@example.test");
        String tab = UUID.randomUUID().toString();
        JsonNode first = body(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = cap(tab, first.path("grantId").asText());
        String originalExpiry = first.path("expiresAt").asText();
        clock.advance(Duration.ofMinutes(14));
        JsonNode renewed = body(a.post("/api/management/editing/lease/activity", cap));
        assertThat(renewed.path("grantId").asText()).isEqualTo(first.path("grantId").asText());
        assertThat(renewed.path("expiresAt").asText()).isNotEqualTo(originalExpiry);
        clock.advance(Duration.ofSeconds(10));
        assertThat(body(a.post("/api/management/editing/lease/activity", cap)).path("expiresAt").asText())
                .isEqualTo(renewed.path("expiresAt").asText());
        synchronized (editing) {
            var draft = editing.drafts.values().iterator().next().draft;
            assertThat(runtime.validate(draft).successful()).isTrue();
        }
        clock.advance(Duration.ofMinutes(14).plusSeconds(49));
        assertThat(a.get("/api/management/editing").body()).contains("\"held\":true");
        clock.advance(Duration.ofSeconds(1));
        assertThat(a.get("/api/management/editing").body()).contains("\"held\":false");
        assertThat(a.post("/api/management/editing/lease/activity", cap).statusCode()).isEqualTo(409);
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(15));
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(401);
    }

    @Test void accountChangeAndLogoutClearEditingWithoutOwnerRequest() throws Exception {
        long changed = seed("changed-a@example.test", "editor");
        seed("changed-b@example.test", "editor");
        Browser a = login("changed-a@example.test");
        Browser b = login("changed-b@example.test");
        String tab = UUID.randomUUID().toString();
        body(a.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        identity.alter(changed, "viewer", true);
        synchronized (editing) {
            assertThat(editing.drafts).isEmpty();
            assertThat(editing.lease).isNull();
        }
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(401);
        String bTab = UUID.randomUUID().toString();
        body(b.post("/api/management/editing/lease", "{\"tabId\":\"" + bTab + "\"}"));
        assertThat(b.post("/api/management/logout", "{}").statusCode()).isEqualTo(204);
        synchronized (editing) {
            assertThat(editing.drafts).isEmpty();
            assertThat(editing.lease).isNull();
        }
        assertThat(b.get("/api/management/editing/draft").statusCode()).isEqualTo(401);
        Browser form = login("changed-b@example.test");
        body(form.post("/api/management/editing/lease", "{\"tabId\":\"" + UUID.randomUUID() + "\"}"));
        assertThat(form.postForm("/management/logout", "_csrf=" + URLEncoder.encode(form.csrf, StandardCharsets.UTF_8))
                .statusCode()).isEqualTo(302);
        synchronized (editing) {
            assertThat(editing.drafts).isEmpty();
            assertThat(editing.lease).isNull();
        }
    }

    private long seed(String email, String role) {
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, role, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(PASSWORD), clock.millis());
        return jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email);
    }
    private Browser login(String email) throws Exception {
        Browser browser = new Browser();
        String page = browser.get("/management/login").body();
        String csrf = page.split("name='_csrf' value='")[1].split("'")[0];
        assertThat(browser.postForm("/management/login", "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)).statusCode()).isEqualTo(302);
        browser.csrf = body(browser.get("/api/management/session")).path("csrfToken").asText();
        return browser;
    }
    private JsonNode body(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }
    private static String cap(String tab, String grant) {
        return "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + grant + "\"}";
    }
    private static String save(String tab, String grant, String candidate, String yaml) {
        return "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + grant
                + "\",\"expectedCandidateId\":\"" + candidate
                + "\",\"skillDocuments\":[{\"sourceName\":\"literal.yaml\",\"yaml\":\"" + yaml
                + "\"}],\"restRoutesYaml\":\"routes: {}\"}";
    }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private String csrf;
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, true); }
        HttpResponse<String> getJwt(String path, String jwt) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + jwt)
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body, true); }
        HttpResponse<String> postWithoutCsrf(String path, String body) throws Exception { return send("POST", path, body, false); }
        HttpResponse<String> put(String path, String body) throws Exception { return send("PUT", path, body, true); }
        HttpResponse<String> delete(String path, String body) throws Exception { return send("DELETE", path, body, true); }
        HttpResponse<String> postForm(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method, String path, String body, boolean token) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path));
            if (body != null) builder.header("Content-Type", "application/json");
            if (token && csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
