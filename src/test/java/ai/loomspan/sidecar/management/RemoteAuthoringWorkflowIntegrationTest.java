package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.support.JwtTestTokens;
import ai.loomspan.sidecar.support.RemoteAuthoringApplicationFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Playwright;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
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
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
@Import(RemoteAuthoringWorkflowIntegrationTest.FixtureClockConfiguration.class)
class RemoteAuthoringWorkflowIntegrationTest {
    static final class MutableClock extends Clock {
        private Instant now = Instant.now();
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
        void reset() { now = Instant.now(); }
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class FixtureClockConfiguration { @Bean @Primary MutableClock fixtureClock() { return new MutableClock(); } }
    @TempDir static Path database;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("loomspan-sidecar.storage.database-path", () -> database.resolve("sidecar.db").toString());
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired ManagementIdentityService identity;
    @Autowired ManagementEditingState editingState;
    @LocalServerPort int port;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private static final Path CLIENT = Path.of("agent-skills/loomspan-sidecar-authoring/client/sidecar_authoring.py");
    private static final Path EXAMPLE = Path.of("examples/remote-authoring");

    @BeforeEach void clearPriorLease() {
        synchronized (editingState) { editingState.clearLease(); }
        clock.reset();
    }

    @Test void remoteAuthoringFromEndpointThroughBothPublicationPaths() throws Exception {
        String email = seed();
        Browser session = login(email);
        String edit = issue(session, "edit");
        String publish = issue(session, "publish");
        try (var app = new RemoteAuthoringApplicationFixture(); Playwright playwright = Playwright.create();
                var browser = playwright.chromium().launch()) {
            var page = browser.newPage();
            page.navigate(url("/management/login"));
            page.locator("input[name=email]").fill(email);
            page.locator("input[name=password]").fill("Long Password 123!");
            page.locator("button:has-text('Sign in')").click();
            page.waitForURL(url("/management/home"));
            page.navigate(url("/management/configuration/edit"));
            page.waitForFunction("() => document.getElementById('editor-message')?.textContent.includes('Saved draft')");

            JsonNode first = cli(edit, "acquire", null, "--label", "Agent author").json();
            JsonNode draft = first.path("draft");
            JsonNode current = cli(edit, "current", null).json();
            String valid = Files.readString(EXAMPLE.resolve("record.yaml"));
            String routes = Files.readString(EXAMPLE.resolve("rest-routes.yaml"))
                    .replace("${RECORDS_BASE_URL}", app.baseUrl());
            JsonNode malformed = cli(edit, "save", save(first, draft, current, valid.replace("rest: true", "rest: false"), routes)).json();
            JsonNode invalid = cli(edit, "validate", candidate(first, malformed)).json();
            assertThat(invalid.path("validation").path("successful").asBoolean()).isFalse();
            assertThat(invalid.path("validation").path("issues").size()).isGreaterThan(0);
            assertThat(cli(edit, "draft", null).json().path("revision").asLong()).isEqualTo(malformed.path("revision").asLong());
            JsonNode saved = cli(edit, "save", save(first, malformed, current, valid, routes)).json();
            JsonNode checked = cli(edit, "validate", candidate(first, saved)).json();
            assertThat(checked.path("validation").path("successful").asBoolean()).as(checked.toPrettyString()).isTrue();
            assertThat(app.allowed.get() + app.denied.get()).isZero(); // validation is advisory
            page.waitForFunction("() => document.getElementById('editor-saved-content')?.textContent.includes('readRecord')");
            assertThat(page.locator("#editor-rest").isEditable()).isFalse();
            page.locator("#editor-handoff").click();
            page.waitForFunction("() => document.getElementById('editor-owner')?.textContent.includes('You hold editing control')");
            assertThat(cli(edit, "save", save(first, saved, current, valid, routes)).code()).isNotZero();
            page.locator("#editor-recheck").click();
            page.waitForFunction("() => document.getElementById('editor-validation-state')?.textContent.includes('Valid')");
            page.locator("#editor-publish").click();
            page.waitForFunction("() => document.getElementById('editor-message')?.textContent.includes('Published configuration')");
            assertThat(cli(edit, "draft", null).code()).isNotZero();
            execute("alice-record", true, app);
            execute("bob-record", false, app);

            // A Publish token can activate without a mandatory console approval.
            JsonNode secondEditor = cli(edit, "acquire", null, "--label", "Agent prepares second draft").json();
            JsonNode secondCurrent = cli(edit, "current", null).json();
            JsonNode secondSaved = cli(edit, "save", save(secondEditor, secondEditor.path("draft"), secondCurrent,
                    valid + "\n# second publication\n", routes)).json();
            JsonNode second = cli(publish, "handoff", null, "--label", "Remote publisher").json();
            assertThat(second.path("draft").path("revision").asLong()).isEqualTo(secondSaved.path("revision").asLong());
            assertThat(second.path("current").path("published").path("localId").asText()).isEqualTo(
                    secondCurrent.path("published").path("localId").asText());
            assertThat(cli(edit, "save", save(secondEditor, secondSaved, secondCurrent, valid, routes)).stderr())
                    .contains("grant_conflict");
            JsonNode secondGrant = second.path("grant");
            JsonNode secondChecked = cli(publish, "validate", candidate(secondGrant, second.path("draft"))).json();
            assertThat(secondChecked.path("validation").path("successful").asBoolean()).isTrue();
            JsonNode published = cli(publish, "publish", candidate(secondGrant, second.path("draft"))).json();
            assertThat(published.path("localId").asText()).isNotBlank();
            assertThat(cli(publish, "draft", null).code()).isNotZero();
            execute("alice-record", true, app);
            assertThat(send("GET", "/v1/skills", edit, null).statusCode()).isEqualTo(401);
        }
    }

    @Test void clientRecoveryAcrossCompetitionExpiryStalenessAndRevocation() throws Exception {
        String firstEmail = seed(), secondEmail = seed();
        Browser firstSession = login(firstEmail), secondSession = login(secondEmail);
        String firstToken = issue(firstSession, "publish"), secondToken = issue(secondSession, "publish");
        JsonNode first = cli(firstToken, "acquire", null, "--label", "First agent").json();
        JsonNode initial = cli(firstToken, "current", null).json();
        String empty = "targets: {}\nroutes: {}\n# first draft\n";
        JsonNode firstSaved = cli(firstToken, "save", save(first, first.path("draft"), initial, "", empty)).json();
        CliResult competing = cli(secondToken, "acquire", null, "--label", "Second agent");
        assertThat(competing.code()).isNotZero();
        assertThat(competing.stderr()).contains("grant_conflict");
        cli(firstToken, "release", capability(first)).json();

        JsonNode second = cli(secondToken, "acquire", null, "--label", "Second agent").json();
        JsonNode secondSaved = cli(secondToken, "save", save(second, second.path("draft"), initial,
                "", "targets: {}\nroutes: {}\n# retained second draft\n")).json();
        cli(secondToken, "release", capability(second)).json();
        JsonNode handed = cli(firstToken, "acquire", null, "--label", "First again").json();
        assertThat(cli(secondToken, "save", save(second, secondSaved, initial, "", empty)).stderr())
                .contains("grant_conflict");
        assertThat(handed.path("draft").path("revision").asLong()).isEqualTo(firstSaved.path("revision").asLong());
        JsonNode checked = cli(firstToken, "validate", candidate(handed, handed.path("draft"))).json();
        assertThat(checked.path("validation").path("successful").asBoolean()).isTrue();
        cli(firstToken, "publish", candidate(handed, handed.path("draft"))).json();

        JsonNode resumed = cli(secondToken, "acquire", null, "--label", "Second resumes").json();
        assertThat(resumed.path("draft").path("stale").asBoolean()).isTrue();
        assertThat(cli(secondToken, "save", save(resumed, resumed.path("draft"), initial,
                "", "targets: {}\nroutes: {}\n# stale write\n")).stderr()).contains("base_conflict");
        JsonNode fresh = cli(secondToken, "current", null).json();
        JsonNode reconciled = cli(secondToken, "reconcile", save(resumed, resumed.path("draft"), fresh,
                "", "targets: {}\nroutes: {}\n# reconciled after reviewing current\n")).json();
        assertThat(reconciled.path("stale").asBoolean()).isFalse();
        assertThat(cli(secondToken, "publish", candidate(resumed, reconciled)).stderr()).contains("validation_required");
        assertThat(cli(secondToken, "draft", null).json().path("revision").asLong()).isEqualTo(reconciled.path("revision").asLong());
        clock.advance(Duration.ofMinutes(16));
        assertThat(cli(secondToken, "validate", candidate(resumed, reconciled)).stderr()).contains("grant_conflict");
        assertThat(cli(secondToken, "draft", null).json().path("revision").asLong()).isEqualTo(reconciled.path("revision").asLong());
        JsonNode reacquired = cli(secondToken, "acquire", null, "--label", "After expiry").json();
        assertThat(reacquired.path("draft").path("revision").asLong()).isEqualTo(reconciled.path("revision").asLong());
        assertThat(secondSession.delete("/api/management/personal-tokens/" + secondToken.substring(6, 28)).statusCode()).isEqualTo(204);
        assertThat(cli(secondToken, "draft", null).stderr()).contains("HTTP 401");
        String replacement = issue(secondSession, "publish");
        assertThat(cli(replacement, "draft", null).json().path("revision").asLong()).isEqualTo(reconciled.path("revision").asLong());
        long accountId = jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, secondEmail);
        identity.alter(accountId, "viewer", true);
        assertThat(cli(replacement, "acquire", null, "--label", "Forbidden").stderr()).contains("HTTP 403");
    }

    private void execute(String record, boolean allowed, RemoteAuthoringApplicationFixture app) throws Exception {
        String jwt = JwtTestTokens.token("alice", List.of("RECORD_READER"));
        var response = send("POST", "/v1/skills/readRecord/executions", jwt,
                mapper.writeValueAsString(Map.of("recordId", record)));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
        String id = mapper.readTree(response.body()).path("id").asText();
        JsonNode terminal = null;
        for (int n = 0; n < 500; n++) {
            terminal = mapper.readTree(send("GET", "/v1/executions/" + id, jwt, null).body());
            if (List.of("COMPLETED", "FAILED").contains(terminal.path("status").asText())) break;
            Thread.sleep(10);
        }
        assertThat(terminal).isNotNull();
        assertThat(terminal.path("status").asText()).isEqualTo(allowed ? "COMPLETED" : "FAILED");
        if (allowed) {
            assertThat(terminal.path("result").asText()).contains(record);
            assertThat(app.allowed.get()).isGreaterThan(0);
        } else {
            assertThat(terminal.path("failure").path("message").asText()).contains("HTTP status 403");
            assertThat(app.denied.get()).isGreaterThan(0);
        }
    }

    private String candidate(JsonNode grant, JsonNode draft) throws Exception {
        return mapper.writeValueAsString(Map.of("editingSessionId", grant.path("editingSessionId").asText(),
                "generation", grant.path("generation").asText(), "draftId", draft.path("draftId").asText(),
                "revision", draft.path("revision").asLong(), "baseSnapshotId", draft.path("baseSnapshotId").asText()));
    }

    private String capability(JsonNode grant) throws Exception {
        return mapper.writeValueAsString(Map.of("editingSessionId", grant.path("editingSessionId").asText(),
                "generation", grant.path("generation").asText()));
    }

    private String save(JsonNode grant, JsonNode draft, JsonNode current, String skill, String routes) throws Exception {
        return mapper.writeValueAsString(Map.of("editingSessionId", grant.path("editingSessionId").asText(),
                "generation", grant.path("generation").asText(), "draftId", draft.path("draftId").asText(),
                "revision", draft.path("revision").asLong(),
                "baseSnapshotId", current.path("published").path("localId").asText(),
                "skillDocuments", skill.isEmpty() ? List.of() : List.of(Map.of("sourceName", "record.yaml", "yaml", skill)),
                "restRoutesYaml", routes));
    }

    private record CliResult(int code, String stdout, String stderr) {
        JsonNode json() throws Exception {
            assertThat(code).as(stderr).isZero();
            return new ObjectMapper().readTree(stdout);
        }
    }
    private CliResult cli(String token, String command, String input, String... options) throws Exception {
        var args = new java.util.ArrayList<String>();
        args.add("python"); args.add(CLIENT.toString()); args.add("--url"); args.add(url(""));
        args.add(command); args.addAll(List.of(options));
        var builder = new ProcessBuilder(args).redirectErrorStream(false);
        builder.environment().put("LOOMSPAN_SIDECAR_MANAGEMENT_TOKEN", token);
        var process = builder.start();
        process.getOutputStream().write((input == null ? "" : input).getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().close();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(stderr + stdout).doesNotContain(token);
        return new CliResult(process.waitFor(), stdout, stderr);
    }

    private String seed() {
        String email = "remote-" + UUID.randomUUID() + "@example.test";
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, "editor", "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode("Long Password 123!"), Clock.systemUTC().millis());
        return email;
    }

    private Browser login(String email) throws Exception {
        Browser session = new Browser();
        String csrf = session.get("/management/login").body().split("name='_csrf' value='")[1].split("'")[0];
        String body = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password=" +
                URLEncoder.encode("Long Password 123!", StandardCharsets.UTF_8) + "&_csrf=" +
                URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        assertThat(session.form("/management/login", body).statusCode()).isEqualTo(302);
        session.csrf = mapper.readTree(session.get("/api/management/session").body()).path("csrfToken").asText();
        return session;
    }
    private String issue(Browser session, String preset) throws Exception {
        var response = session.post("/api/management/personal-tokens", "{\"preset\":\"" + preset + "\"}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        return mapper.readTree(response.body()).path("secret").asText();
    }
    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(url(path))).timeout(Duration.ofSeconds(5));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private String url(String path) { return "http://127.0.0.1:" + port + path; }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        String csrf;
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null); }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body); }
        HttpResponse<String> delete(String path) throws Exception { return send("DELETE", path, null); }
        HttpResponse<String> send(String method, String path, String body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(url(path)));
            if (body != null) builder.header("Content-Type", "application/json");
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> form(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(URI.create(url(path)))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
