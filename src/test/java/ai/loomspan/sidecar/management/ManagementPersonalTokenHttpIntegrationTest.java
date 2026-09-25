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
    "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test", "loomspan-sidecar.auth.jwt.audience=sidecar",
    "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
@Import(ManagementPersonalTokenHttpIntegrationTest.TimeConfiguration.class)
class ManagementPersonalTokenHttpIntegrationTest {
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
    @Autowired ManagementPersonalTokenService tokens;
    @LocalServerPort int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String PASSWORD = "Long Password 123!";
    @BeforeEach void reset() { synchronized (state) { state.clearLease(); } clock.reset(); }

    @Test void readTokenUsesSharedManagementApiAndCannotAcquireLease() throws Exception {
        String email = seed("editor"); Browser browser = login(email);
        String token = issue(browser, "read").path("secret").asText();
        var current = bearer(token, "GET", "/api/management/configuration/current", null);
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(current.headers().allValues("Set-Cookie")).isEmpty();
        assertThat(bearer(token, "GET", "/api/management/editing/draft", null).statusCode()).isEqualTo(404);
        assertThat(bearer(token, "POST", "/api/management/editing/lease", "{\"label\":\"Read\"}").statusCode()).isEqualTo(403);
        assertThat(state.lease).isNull();
    }

    @Test void lifecycleIsOwnerScopedAndSecretIsOneTime() throws Exception {
        Browser owner = login(seed("editor")); Browser other = login(seed("editor"));
        JsonNode issued = issue(owner, "edit"); String token = issued.path("secret").asText();
        String id = issued.path("token").path("id").asText();
        assertThat(owner.get("/management/personal-tokens").body()).contains("data-console='tokens'").doesNotContain(token);
        assertThat(owner.get("/management/personal-tokens").body()).doesNotContain(token);
        assertThat(token).startsWith("lspat_");
        assertThat(jdbc.queryForObject("SELECT secret_digest FROM management_personal_token WHERE id=?", String.class, id))
                .doesNotContain(token).hasSize(64);
        assertThat(owner.get("/api/management/personal-tokens").body()).contains(id).doesNotContain(token);
        assertThat(other.get("/api/management/personal-tokens").body()).doesNotContain(id);
        assertThat(other.delete("/api/management/personal-tokens/" + id).statusCode()).isEqualTo(204);
        assertThat(bearer(token, "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(200);
        assertThat(owner.delete("/api/management/personal-tokens/" + id).statusCode()).isEqualTo(204);
        assertThat(bearer(token, "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(401);
    }

    @Test void mixedCredentialsAndCookieCsrfStayClosed() throws Exception {
        Browser browser = login(seed("editor")); String token = issue(browser, "edit").path("secret").asText();
        assertThat(browser.postNoCsrf("/api/management/editing/lease", "{\"label\":\"Browser\"}").statusCode()).isEqualTo(403);
        assertThat(browser.send("GET", "/api/management/configuration/current", null, false, token).statusCode()).isEqualTo(401);
        assertThat(browser.send("GET", "/api/management/configuration/current", null, false, "invalid").statusCode()).isEqualTo(401);
        assertThat(bearer(token, "GET", "/api/management/personal-tokens", null).statusCode()).isEqualTo(403);
        assertThat(bearer("invalid", "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(401);
    }

    @Test void publishPresetAndLiveRoleLimitEditing() throws Exception {
        String email = seed("editor"); Browser browser = login(email);
        String edit = issue(browser, "edit").path("secret").asText();
        String publish = issue(browser, "publish").path("secret").asText();
        assertThat(bearer(edit, "POST", "/api/management/configuration/publish", "{}").statusCode()).isEqualTo(403);
        var grant = mapper.readTree(bearer(edit, "POST", "/api/management/editing/lease", "{\"label\":\"Remote\"}").body());
        assertThat(grant.path("editingSessionId").asText()).isNotBlank();
        assertThat(bearer(publish, "POST", "/api/management/editing/lease/handoff", "{\"label\":\"Remote 2\"}").statusCode()).isEqualTo(200);
        identity.alter(jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email), "viewer", true);
        assertThat(bearer(edit, "POST", "/api/management/editing/lease", "{\"label\":\"Remote\"}").statusCode()).isEqualTo(403);
        assertThat(bearer(publish, "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(200);
    }

    @Test void expiryAndIssuanceLimitsApply() throws Exception {
        Browser browser = login(seed("editor"));
        var issued = issue(browser, "read");
        assertThat(Instant.parse(issued.path("token").path("expiresAt").asText()))
                .isEqualTo(clock.instant().plus(Duration.ofDays(7)));
        assertThat(browser.post("/api/management/personal-tokens", "{\"preset\":\"read\",\"expiresAt\":\"2026-09-18T00:00:00Z\"}").statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/management/personal-tokens", "{\"preset\":\"read\",\"expiresAt\":\"2026-09-17T23:59:59Z\"}").statusCode()).isEqualTo(400);
        assertThat(browser.post("/api/management/personal-tokens", "{\"preset\":\"read\",\"expiresAt\":\"2026-10-19T00:00:01Z\"}").statusCode()).isEqualTo(400);
        var maxLifetime = browser.post("/api/management/personal-tokens",
                "{\"preset\":\"read\",\"expiresAt\":\"2026-10-18T00:00:00Z\"}");
        assertThat(maxLifetime.statusCode()).isEqualTo(201);
        for (int i = 0; i < 3; i++) issue(browser, "read");
        assertThat(browser.post("/api/management/personal-tokens", "{\"preset\":\"read\"}").statusCode()).isEqualTo(400);
        clock.advance(Duration.ofDays(8));
        assertThat(bearer(issued.path("secret").asText(), "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(401);
    }

    @Test void publishTokenUsesExactCandidateAndEditTokenCannotActivate() throws Exception {
        Browser browser = login(seed("editor"));
        String edit = issue(browser, "edit").path("secret").asText();
        String publish = issue(browser, "publish").path("secret").asText();
        var grant = mapper.readTree(bearer(edit, "POST", "/api/management/editing/lease", "{\"label\":\"Remote\"}").body());
        var draft = grant.path("draft");
        String candidate = candidate(grant, draft);
        assertThat(bearer(publish, "POST", "/api/management/configuration/publish", candidate).statusCode()).isEqualTo(409);
        assertThat(bearer(edit, "POST", "/api/management/editing/draft/validate", candidate).statusCode()).isEqualTo(200);
        assertThat(bearer(edit, "POST", "/api/management/configuration/publish", candidate).statusCode()).isEqualTo(403);
        assertThat(bearer(publish, "POST", "/api/management/configuration/publish", candidate).statusCode()).isEqualTo(409);
        var handoff = mapper.readTree(bearer(publish, "POST", "/api/management/editing/lease/handoff", "{\"label\":\"Publisher\"}").body());
        candidate = candidate(handoff, handoff.path("draft"));
        assertThat(bearer(publish, "POST", "/api/management/configuration/publish", candidate).statusCode()).isEqualTo(409);
        assertThat(bearer(publish, "POST", "/api/management/editing/draft/validate", candidate).statusCode()).isEqualTo(200);
        assertThat(bearer(publish, "POST", "/api/management/configuration/publish", candidate).statusCode()).isEqualTo(200);
    }

    @Test void passwordChangeRevokesTokenAndSavedDraftSurvives() throws Exception {
        String email = seed("editor"); Browser browser = login(email);
        var issued = issue(browser, "edit"); String token = issued.path("secret").asText();
        assertThat(bearer(token, "POST", "/api/management/editing/lease", "{\"label\":\"Remote\"}").statusCode()).isEqualTo(200);
        long id = jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email);
        identity.change(id, PASSWORD, "Another Long Password 123!");
        assertThat(bearer(token, "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT revoked_at FROM management_personal_token WHERE id=?", Long.class,
                issued.path("token").path("id").asText())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM management_draft WHERE account_id=?", Integer.class, id)).isEqualTo(1);
    }

    @Test void adminTokenCannotUseAccountOrTakeoverRoutesAndExecutionRejectsIt() throws Exception {
        Browser admin = login(seed("admin")); String token = issue(admin, "publish").path("secret").asText();
        assertThat(bearer(token, "GET", "/api/management/accounts", null).statusCode()).isEqualTo(403);
        assertThat(bearer(token, "POST", "/api/management/editing/lease/takeover", "{\"label\":\"Admin\"}").statusCode()).isEqualTo(403);
        assertThat(bearer(token, "GET", "/api/management/session", null).statusCode()).isEqualTo(403);
        assertThat(bearer(token, "GET", "/management/home", null).statusCode()).isEqualTo(403);
        assertThat(bearer(token, "GET", "/v1/executions", null).statusCode()).isEqualTo(401);
    }

    @Test void activeCountLimitRemainsAfterIssuanceWindowRolls() {
        String email = seed("editor");
        long id = jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email);
        for (int hour = 0; hour < 4; hour++) {
            for (int i = 0; i < 5; i++) tokens.issue(id, 0, "read", null);
            clock.advance(Duration.ofHours(1).plusMillis(1));
        }
        assertThat(tokens.list(id)).hasSize(20);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> tokens.issue(id, 0, "read", null)))
                .isInstanceOf(ManagementPersonalTokenService.Rejected.class);
    }

    @Test void disableAndReenableNeverReviveOldTokens() throws Exception {
        String email = seed("editor"); Browser browser = login(email);
        String token = issue(browser, "read").path("secret").asText();
        long id = jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email);
        identity.alter(id, "editor", false);
        assertThat(bearer(token, "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(401);
        identity.alter(id, "editor", true);
        assertThat(bearer(token, "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(401);
    }

    @Test void passwordRecoveryRevokesExistingTokens() throws Exception {
        String email = seed("editor"); Browser browser = login(email);
        String token = issue(browser, "read").path("secret").asText();
        long id = jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email);
        String reset = ManagementTokens.issue(new ManagementAccountRepository(jdbc), id, "reset", clock.millis(),
                Duration.ofMinutes(30));
        identity.redeem("reset", reset, "Recovered Long Password 123!");
        assertThat(bearer(token, "GET", "/api/management/configuration/current", null).statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM management_personal_token WHERE account_id=? AND revoked_at IS NULL",
                Integer.class, id)).isZero();
    }

    @Test void expiredTokenReleasesItsLeaseButKeepsTheDraft() throws Exception {
        Browser owner = login(seed("editor")); Browser other = login(seed("editor"));
        String expiry = clock.instant().plus(Duration.ofMinutes(1)).toString();
        var response = owner.post("/api/management/personal-tokens",
                "{\"preset\":\"edit\",\"expiresAt\":\"" + expiry + "\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        String token = mapper.readTree(response.body()).path("secret").asText();
        assertThat(bearer(token, "POST", "/api/management/editing/lease", "{\"label\":\"Remote\"}").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(2));
        assertThat(bearer(token, "GET", "/api/management/editing/draft", null).statusCode()).isEqualTo(401);
        assertThat(other.post("/api/management/editing/lease", "{\"label\":\"Other\"}").statusCode()).isEqualTo(200);
        assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
    }

    @Test void auditEventsContainIdentifiersButNeverTheSecret() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger("sidecar.management.audit");
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start(); logger.addAppender(events);
        try {
            Browser browser = login(seed("editor"));
            var issued = issue(browser, "read");
            String secret = issued.path("secret").asText();
            String id = issued.path("token").path("id").asText();
            assertThat(bearer(secret, "POST", "/api/management/editing/lease", "{\"label\":\"No\"}").statusCode()).isEqualTo(403);
            String messages = events.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                    .collect(java.util.stream.Collectors.joining("\n"));
            assertThat(messages).contains("action=pat.issue", "action=pat.authorize", "token=" + id)
                    .doesNotContain(secret, "Authorization", secret.substring(secret.indexOf('.') + 1));
        } finally { logger.detachAppender(events); events.stop(); }
    }

    private String candidate(JsonNode grant, JsonNode draft) throws Exception {
        return mapper.writeValueAsString(java.util.Map.of("editingSessionId", grant.path("editingSessionId").asText(),
                "generation", grant.path("generation").asText(), "draftId", draft.path("draftId").asText(),
                "revision", draft.path("revision").asLong(), "baseSnapshotId", draft.path("baseSnapshotId").asText()));
    }

    private String seed(String role) {
        String email = UUID.randomUUID() + "@example.test";
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, role, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(PASSWORD), clock.millis());
        return email;
    }
    private Browser login(String email) throws Exception {
        Browser browser = new Browser();
        String csrf = browser.get("/management/login").body().split("name='_csrf' value='")[1].split("'")[0];
        assertThat(browser.form("/management/login", "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8)
                + "&_csrf=" + URLEncoder.encode(csrf, StandardCharsets.UTF_8)).statusCode()).isEqualTo(302);
        browser.csrf = mapper.readTree(browser.get("/api/management/session").body()).path("csrfToken").asText();
        return browser;
    }
    private JsonNode issue(Browser browser, String preset) throws Exception {
        var response = browser.post("/api/management/personal-tokens", "{\"preset\":\"" + preset + "\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return mapper.readTree(response.body());
    }
    private HttpResponse<String> bearer(String token, String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return HttpClient.newHttpClient().send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private String csrf;
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, true, null); }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body, true, null); }
        HttpResponse<String> postNoCsrf(String path, String body) throws Exception { return send("POST", path, body, false, null); }
        HttpResponse<String> delete(String path) throws Exception { return send("DELETE", path, null, true, null); }
        HttpResponse<String> send(String method, String path, String body, boolean csrfOn, String bearer) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (body != null) builder.header("Content-Type", "application/json");
            if (csrfOn && csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> form(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
