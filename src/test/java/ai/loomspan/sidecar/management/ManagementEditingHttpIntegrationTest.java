package ai.loomspan.sidecar.management;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    "loomspan-sidecar.management.session-idle-timeout=30m", "loomspan-sidecar.management.edit-lease-timeout=15m",
    "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
    "loomspan-sidecar.auth.jwt.audience=sidecar",
    "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
@Import(ManagementEditingHttpIntegrationTest.TimeConfiguration.class)
class ManagementEditingHttpIntegrationTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("loomspan-sidecar.storage.database-path", () -> directory.resolve("sidecar.db").toString());
    }
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-18T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
        void reset() { now = Instant.parse("2026-09-18T00:00:00Z"); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration { @Bean @Primary MutableClock clock() { return new MutableClock(); } }
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired ManagementEditingState state;
    @Autowired ManagementIdentityService identity;
    @LocalServerPort int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String PASSWORD = "Long Password 123!";

    @BeforeEach void reset() { synchronized (state) { state.clearLease(); } clock.reset(); }

    @Test void sharedPrivateDraftSurvivesLogoutAndHandoffRejectsDelayedWrite() throws Exception {
        String email = "owner-" + UUID.randomUUID() + "@example.test";
        String foreign = "foreign-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor"); seed(foreign, "editor");
        Browser first = login(email), second = login(email), other = login(foreign);
        JsonNode grant = ok(first.post("/api/management/editing/lease", "{\"label\":\"First\"}"));
        JsonNode draft = grant.path("draft");
        JsonNode saved = ok(first.put("/api/management/editing/draft", save(grant, draft, "# secret")));
        assertThat(saved.path("revision").asLong()).isEqualTo(draft.path("revision").asLong() + 1);
        assertThat(ok(second.get("/api/management/editing/draft")).path("configuration").toString()).contains("secret");
        assertThat(other.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        assertThat(other.put("/api/management/editing/draft", save(grant, saved, "# forged")).statusCode())
                .isEqualTo(409);
        JsonNode handoff = ok(second.post("/api/management/editing/lease/handoff", "{\"label\":\"Second\"}"));
        assertThat(handoff.path("generation").asText()).isNotEqualTo(grant.path("generation").asText());
        assertThat(first.put("/api/management/editing/draft", save(grant, saved, "# delayed")).statusCode()).isEqualTo(409);
        assertThat(second.put("/api/management/editing/draft", save(handoff, saved, "# next")).statusCode()).isEqualTo(200);
        assertThat(first.post("/api/management/logout", "{}").statusCode()).isEqualTo(204);
        Browser resumed = login(email);
        assertThat(ok(resumed.get("/api/management/editing/draft")).path("configuration").toString()).contains("next");
        assertThat(ok(resumed.get("/api/management/editing")).path("mine").asBoolean()).isFalse();
    }

    @Test void staleBaseRequiresCompleteReconciliationAndFreshValidation() throws Exception {
        String aEmail = "a-" + UUID.randomUUID() + "@example.test", bEmail = "b-" + UUID.randomUUID() + "@example.test";
        seed(aEmail, "editor"); seed(bEmail, "editor");
        Browser a = login(aEmail), b = login(bEmail);
        JsonNode ga = ok(a.post("/api/management/editing/lease", "{\"label\":\"A\"}"));
        JsonNode da = ok(a.put("/api/management/editing/draft", save(ga, ga.path("draft"), "# A")));
        assertThat(a.post("/api/management/editing/lease/release", cap(ga)).statusCode()).isEqualTo(204);
        JsonNode gb = ok(b.post("/api/management/editing/lease", "{\"label\":\"B\"}"));
        JsonNode db = ok(b.put("/api/management/editing/draft", save(gb, gb.path("draft"), "# B")));
        assertThat(b.post("/api/management/editing/lease/release", cap(gb)).statusCode()).isEqualTo(204);
        ga = ok(a.post("/api/management/editing/lease", "{\"label\":\"A\"}"));
        ok(a.post("/api/management/editing/draft/validate", candidate(ga, da)));
        JsonNode published = ok(a.post("/api/management/configuration/publish", candidate(ga, da)));
        assertThat(published.path("localId").asText()).isNotBlank();
        assertThat(a.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        assertThat(ok(a.get("/api/management/editing")).path("held").asBoolean()).isFalse();
        assertThat(ok(b.get("/api/management/editing/draft")).path("stale").asBoolean()).isTrue();
        gb = ok(b.post("/api/management/editing/lease", "{\"label\":\"B\"}"));
        assertThat(b.post("/api/management/editing/draft/validate", candidate(gb, db)).statusCode()).isEqualTo(409);
        assertThat(b.put("/api/management/editing/draft", save(gb, db, "# B2")).statusCode()).isEqualTo(409);
        assertThat(b.post("/api/management/editing/draft/reconcile",
                candidate(gb, db).replace(db.path("baseSnapshotId").asText(), published.path("localId").asText()))
                .statusCode()).isEqualTo(400);
        assertThat(ok(b.get("/api/management/editing/draft")).path("stale").asBoolean()).isTrue();
        JsonNode reconciled = ok(b.post("/api/management/editing/draft/reconcile",
                save(gb, db, "# B", published.path("localId").asText())));
        assertThat(reconciled.path("revision").asLong()).isGreaterThan(db.path("revision").asLong());
        assertThat(b.post("/api/management/configuration/publish", candidate(gb, reconciled)).statusCode()).isEqualTo(409);
        ok(b.post("/api/management/editing/draft/validate", candidate(gb, reconciled)));
        ok(b.post("/api/management/configuration/publish", candidate(gb, reconciled)));
        assertThat(b.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
    }

    @Test void pollingAndSavingDoNotRenewLeaseAndCsrfIsRequired() throws Exception {
        String email = "expiry-" + UUID.randomUUID() + "@example.test"; seed(email, "editor");
        Browser browser = login(email);
        assertThat(browser.postWithoutCsrf("/api/management/editing/lease", "{\"label\":\"X\"}").statusCode()).isEqualTo(403);
        assertThat(browser.post("/api/management/editing/lease", "{\"tabId\":\"" + UUID.randomUUID() + "\"}").statusCode())
                .isEqualTo(400);
        JsonNode grant = ok(browser.post("/api/management/editing/lease", "{\"label\":\"X\"}"));
        clock.advance(Duration.ofMinutes(14));
        ok(browser.get("/api/management/editing"));
        ok(browser.get("/api/management/editing/draft"));
        JsonNode saved = ok(browser.put("/api/management/editing/draft", save(grant, grant.path("draft"), "# before-expiry")));
        clock.advance(Duration.ofMinutes(2));
        assertThat(browser.put("/api/management/editing/draft", save(grant, saved, "# late")).statusCode()).isEqualTo(409);
        assertThat(browser.post("/api/management/editing/lease/renew", cap(grant)).statusCode()).isEqualTo(409);
        assertThat(ok(browser.get("/api/management/editing/draft")).path("configuration").toString()).contains("before-expiry");
    }

    @Test void administratorTakeoverPreservesDisplacedPrivateDraft() throws Exception {
        String ownerEmail = "displaced-" + UUID.randomUUID() + "@example.test";
        String adminEmail = "admin-" + UUID.randomUUID() + "@example.test";
        seed(ownerEmail, "editor"); seed(adminEmail, "admin");
        Browser owner = login(ownerEmail), admin = login(adminEmail);
        JsonNode grant = ok(owner.post("/api/management/editing/lease", "{\"label\":\"Owner\"}"));
        JsonNode saved = ok(owner.put("/api/management/editing/draft", save(grant, grant.path("draft"), "# owner-private")));
        JsonNode takeover = ok(admin.post("/api/management/editing/lease/takeover", "{\"label\":\"Admin\"}"));
        assertThat(takeover.path("draft").path("configuration").toString()).doesNotContain("owner-private");
        assertThat(owner.put("/api/management/editing/draft", save(grant, saved, "# delayed")).statusCode()).isEqualTo(409);
        assertThat(ok(owner.get("/api/management/editing/draft")).path("configuration").toString()).contains("owner-private");
        assertThat(ok(admin.get("/api/management/editing/draft")).path("configuration").toString()).doesNotContain("owner-private");
    }

    @Test void explicitLeaseRenewalDoesNotExtendLoginIdleDeadline() throws Exception {
        String email = "idle-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor"); Browser browser = login(email);
        JsonNode grant = ok(browser.post("/api/management/editing/lease", "{\"label\":\"Idle\"}"));
        clock.advance(Duration.ofMinutes(14));
        ok(browser.post("/api/management/editing/lease/renew", cap(grant)));
        clock.advance(Duration.ofMinutes(14));
        ok(browser.post("/api/management/editing/lease/renew", cap(grant)));
        clock.advance(Duration.ofMinutes(3));
        assertThat(browser.get("/api/management/editing/draft").statusCode()).isEqualTo(401);
        assertThat(ok(login(email).get("/api/management/editing/draft")).path("draftId").asText())
                .isEqualTo(grant.path("draft").path("draftId").asText());
    }

    @Test void viewerCanReadOwnSavedDraftButCannotAcquireOrPublish() throws Exception {
        String email = "viewer-" + UUID.randomUUID() + "@example.test";
        long id = seed(email, "editor");
        Browser first = login(email);
        JsonNode grant = ok(first.post("/api/management/editing/lease", "{\"label\":\"Editor\"}"));
        JsonNode draft = ok(first.put("/api/management/editing/draft", save(grant, grant.path("draft"), "# saved")));
        identity.alter(id, "viewer", true);
        Browser viewer = login(email);
        assertThat(ok(viewer.get("/api/management/editing/draft")).path("draftId").asText())
                .isEqualTo(draft.path("draftId").asText());
        assertThat(viewer.post("/api/management/editing/lease", "{\"label\":\"Viewer\"}").statusCode())
                .isEqualTo(403);
        assertThat(viewer.post("/api/management/configuration/publish", candidate(grant, draft)).statusCode())
                .isEqualTo(403);
    }

    @Test void concurrentExpectedRevisionSavesHaveOneWinner() throws Exception {
        String email = "concurrent-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor"); Browser browser = login(email);
        JsonNode grant = ok(browser.post("/api/management/editing/lease", "{\"label\":\"Concurrent\"}"));
        JsonNode initial = grant.path("draft");
        var first = browser.putAsync("/api/management/editing/draft", save(grant, initial, "# first"));
        var second = browser.putAsync("/api/management/editing/draft", save(grant, initial, "# second"));
        var outcomes = java.util.List.of(first.get().statusCode(), second.get().statusCode());
        assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        assertThat(ok(browser.get("/api/management/editing/draft")).path("revision").asLong())
                .isEqualTo(initial.path("revision").asLong() + 1);
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
        String csrf = browser.get("/management/login").body().split("name='_csrf' value='")[1].split("'")[0];
        assertThat(browser.postForm("/management/login", "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)).statusCode()).isEqualTo(302);
        browser.csrf = ok(browser.get("/api/management/session")).path("csrfToken").asText();
        return browser;
    }
    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return mapper.readTree(response.body());
    }
    private String cap(JsonNode grant) throws Exception {
        return mapper.writeValueAsString(Map.of("editingSessionId", grant.path("editingSessionId").asText(),
                "generation", grant.path("generation").asText()));
    }
    private String candidate(JsonNode grant, JsonNode draft) throws Exception {
        return mapper.writeValueAsString(Map.of("editingSessionId", grant.path("editingSessionId").asText(),
                "generation", grant.path("generation").asText(), "draftId", draft.path("draftId").asText(),
                "revision", draft.path("revision").asLong(), "baseSnapshotId", draft.path("baseSnapshotId").asText()));
    }
    private String save(JsonNode grant, JsonNode draft, String marker) throws Exception {
        return save(grant, draft, marker, draft.path("baseSnapshotId").asText());
    }
    private String save(JsonNode grant, JsonNode draft, String marker, String baseId) throws Exception {
        return mapper.writeValueAsString(Map.of("editingSessionId", grant.path("editingSessionId").asText(),
                "generation", grant.path("generation").asText(), "draftId", draft.path("draftId").asText(),
                "revision", draft.path("revision").asLong(), "baseSnapshotId", baseId,
                "skillDocuments", java.util.List.of(), "restRoutesYaml", "targets: {}\nroutes: {}\n" + marker + "\n"));
    }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private String csrf;
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, true); }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body, true); }
        HttpResponse<String> postWithoutCsrf(String path, String body) throws Exception { return send("POST", path, body, false); }
        HttpResponse<String> put(String path, String body) throws Exception { return send("PUT", path, body, true); }
        java.util.concurrent.CompletableFuture<HttpResponse<String>> putAsync(String path, String body) {
            return client.sendAsync(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", csrf)
                    .PUT(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> send(String method, String path, String body, boolean token) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (body != null) builder.header("Content-Type", "application/json");
            if (token && csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> postForm(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
