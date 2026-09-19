package ai.loomspan.sidecar.management;

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
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
        "loomspan-sidecar.management.session-idle-timeout=30m",
        "loomspan-sidecar.management.edit-lease-timeout=7m",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
@Import(ManagementHttpIntegrationTest.TimeConfiguration.class)
class ManagementHttpIntegrationTest {
    private static final String SETUP = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
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
    }
    @TestConfiguration(proxyBeanMethods = false)
    static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean @Primary Supplier<String> testSetupCredential() { return () -> SETUP; }
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ManagementIdentityService identity;
    @Autowired MutableClock clock;
    @LocalServerPort int port;

    private static final String PASSWORD = "Long Password 123!";
    private static final Pattern CSRF = Pattern.compile("name='_csrf' value='([^']+)'", Pattern.CASE_INSENSITIVE);

    @Test void authenticatedViewerCanOpenCurrentConfigurationPage() throws Exception {
        seed("current-viewer@example.test", "viewer");
        var anonymous = new Browser();
        assertThat(anonymous.get("/management/configuration/current").statusCode()).isEqualTo(302);
        var viewer = new Browser();
        String csrf = csrf(viewer.get("/management/login").body());
        assertThat(viewer.postForm("/management/login", "email=current-viewer%40example.test&password="
                + encode(PASSWORD) + "&_csrf=" + encode(csrf)).statusCode()).isEqualTo(302);
        var current = viewer.get("/management/configuration/current");
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(current.body()).contains("Current configuration", "/management/assets/console.js");
        assertThat(current.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(current.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        assertThat(viewer.get("/management/accounts").statusCode()).isEqualTo(403);
        assertThat(viewer.get("/api/management/configuration/current").statusCode()).isEqualTo(200);
        assertThat(viewer.get("/management/assets/console.js").statusCode()).isEqualTo(200);
        assertThat(viewer.get("/management/assets/console.css").statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/management/assets/console.js").statusCode()).isEqualTo(200);
        assertThat(anonymous.get("/management/assets/console.css").headers().firstValue("Referrer-Policy"))
                .hasValue("no-referrer");
    }
    @Test void editorShellContainsNoAuthoredValuesAndSessionExposesConfiguredTimeouts() throws Exception {
        seed("shell-viewer@example.test", "viewer");
        var browser = new Browser();
        assertThat(browser.get("/management/configuration/edit").statusCode()).isEqualTo(302);
        String csrf = csrf(browser.get("/management/login").body());
        browser.postForm("/management/login", "email=shell-viewer%40example.test&password="
                + encode(PASSWORD) + "&_csrf=" + encode(csrf));
        var page = browser.get("/management/configuration/edit");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.body()).contains("id='editor-skills'", "id='editor-rest'", "/management/assets/editor.js")
                .doesNotContain("sourceName", "restRoutesYaml");
        assertThat(page.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(browser.get("/management/assets/editor.js").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/management/session").body())
                .contains("\"sessionIdleTimeoutSeconds\":1800", "\"editLeaseTimeoutSeconds\":420");
    }

    @Test void accountPageUsesAdminAuthorityAndPasswordFormsExplainExactPolicy() throws Exception {
        seed("console-admin@example.test", "admin");
        var browser = new Browser();
        String token = csrf(browser.get("/management/login").body());
        assertThat(browser.get("/management/accounts").statusCode()).isEqualTo(302);
        assertThat(browser.postForm("/management/login", "email=console-admin%40example.test&password="
                + encode(PASSWORD) + "&_csrf=" + encode(token)).statusCode()).isEqualTo(302);
        assertThat(browser.get("/management/accounts").body()).contains("Invite account", "name='email'", "id='accounts-list'");
        assertThat(browser.get("/management/home").body()).contains("/management/accounts", "/management/configuration/current");
        for (String route : List.of("/management/password/set?token=fixture", "/management/password/reset?token=fixture",
                "/management/password/change")) {
            String html = browser.get(route).body();
            assertThat(html).contains("15–128 Unicode characters", "512 UTF-8 bytes", "non-whitespace punctuation", "Spaces are allowed", "paste", "password manager", "name='_csrf'");
            assertThat(html).doesNotContain("maxlength=", "onpaste=");
        }
    }

    @Test void managementAuthenticationAndCsrfStaySeparateFromExecution() throws Exception {
        long admin = seed("admin@example.test", "admin");
        long viewer = seed("viewer@example.test", "viewer");
        var browser = new Browser();
        var page = browser.get("/management/login");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(page.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
        String preLoginSession = page.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(preLoginSession).contains("HttpOnly", "SameSite=Lax");
        assertThat(browser.postForm("/management/login", "email=admin%40example.test&password=wrong").statusCode()).isEqualTo(403);
        assertThat(browser.get("/management/setup").statusCode()).isEqualTo(200);
        assertThat(browser.get("/management/forgot").body()).contains("name='email'", "name='_csrf'");
        assertThat(browser.get("/management/password/set?token=fixture").body())
                .contains("autocomplete='new-password'", "name='_csrf'");
        var csrf = csrf(page.body());
        assertThat(browser.postForm("/management/forgot", "email=missing%40example.test&_csrf=" + encode(csrf)).body())
                .contains("If the address has an active account");
        assertThat(browser.postForm("/management/setup", "credential=bad&email=first%40example.test&_csrf=" + encode(csrf)).body())
                .contains("Setup is locked or email delivery is unavailable");
        assertThat(browser.postForm("/management/password/set", "token=bad&password=Long+Password+123%21&_csrf=" + encode(csrf)).body())
                .contains("Link or password invalid");
        assertThat(browser.postJson("/api/management/setup", "{\"credential\":\"bad\",\"email\":\"first@example.test\"}", csrf)
                .statusCode()).isEqualTo(400);
        assertThat(browser.postJson("/api/management/setup", "{\"credential\":\"" + SETUP
                + "\",\"email\":\"first@example.test\"}", csrf).statusCode()).isEqualTo(503);
        var login = browser.postForm("/management/login", "email=admin%40example.test&password=" + encode(PASSWORD)
                + "&_csrf=" + encode(csrf));
        assertThat(login.statusCode()).isEqualTo(302);
        assertThat(login.headers().firstValue("Set-Cookie")).isPresent();
        assertThat(login.headers().firstValue("Set-Cookie").orElseThrow()).isNotEqualTo(preLoginSession);
        var session = browser.get("/api/management/session");
        assertThat(session.statusCode()).isEqualTo(200);
        assertThat(session.body()).contains("admin@example.test", "MGT_ADMIN").doesNotContain(PASSWORD);
        String changePage = browser.get("/management/password/change").body();
        assertThat(changePage).contains("autocomplete='current-password'", "name='_csrf'");
        assertThat(browser.postForm("/management/password/change", "current=wrong&replacement=Another+Long+Password+1%21&_csrf="
                + encode(csrf(changePage))).body()).contains("Password could not be changed");
        assertThat(browser.get("/api/management/accounts").statusCode()).isEqualTo(200);
        assertThat(browser.get("/v1/skills").statusCode()).isEqualTo(401);
        assertThat(browser.postJson("/api/management/accounts", "{\"email\":\"other@example.test\",\"role\":\"viewer\"}", null)
                .statusCode()).isEqualTo(403);
        var jwtOnly = new Browser();
        assertThat(jwtOnly.getJwt("/api/management/session", JwtTestTokens.token("operator", List.of())).statusCode())
                .isEqualTo(401);
        var viewerBrowser = new Browser();
        String viewerCsrf = csrf(viewerBrowser.get("/management/login").body());
        assertThat(viewerBrowser.postForm("/management/login", "email=viewer%40example.test&password="
                + encode(PASSWORD) + "&_csrf=" + encode(viewerCsrf)).statusCode()).isEqualTo(302);
        assertThat(viewerBrowser.get("/api/management/accounts").statusCode()).isEqualTo(403);
        identity.alter(viewer, "editor", true);
        assertThat(viewerBrowser.get("/api/management/session").statusCode()).isEqualTo(401);
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(200);
        String logoutCsrf = jsonCsrf(browser.get("/api/management/session").body());
        assertThat(browser.postJson("/api/management/logout", "{}", logoutCsrf).statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(401);
        assertThat(admin).isPositive();
    }

    @Test void securityChangesAndIdleDeadlineInvalidateSessions() throws Exception {
        seed("idle@example.test", "viewer");
        var browser = new Browser();
        String token = csrf(browser.get("/management/login").body());
        browser.postForm("/management/login", "email=idle%40example.test&password=" + encode(PASSWORD)
                + "&_csrf=" + encode(token));
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(29));
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(200);
        assertThat(browser.get("/management/configuration/current").statusCode()).isEqualTo(200);
        assertThat(browser.get("/api/management/configuration/current").statusCode()).isEqualTo(200);
        String csrf = jsonCsrf(browser.get("/api/management/session").body());
        assertThat(browser.postJson("/api/management/session/activity", "{}", csrf).statusCode()).isEqualTo(204);
        clock.advance(Duration.ofMinutes(29));
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(200);
        clock.advance(Duration.ofMinutes(1));
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(401);
    }

    @Test void passwordChangeInvalidatesSessionAndNeedsNormalLogin() throws Exception {
        seed("change@example.test", "viewer");
        var browser = new Browser();
        String token = csrf(browser.get("/management/login").body());
        browser.postForm("/management/login", "email=change%40example.test&password=" + encode(PASSWORD)
                + "&_csrf=" + encode(token));
        String csrf = jsonCsrf(browser.get("/api/management/session").body());
        assertThat(browser.postJson("/api/management/password/change",
                "{\"currentPassword\":\"Long Password 123!\",\"newPassword\":\"Another Long Password 1!\"}", csrf)
                .statusCode()).isEqualTo(204);
        assertThat(browser.get("/api/management/session").statusCode()).isEqualTo(401);
        var relogin = new Browser();
        String nextCsrf = csrf(relogin.get("/management/login").body());
        assertThat(relogin.postForm("/management/login", "email=change%40example.test&password=" + encode(PASSWORD)
                + "&_csrf=" + encode(nextCsrf)).headers().firstValue("Location").orElseThrow())
                .endsWith("/management/login?error");
        assertThat(relogin.postForm("/management/login", "email=change%40example.test&password="
                + encode("Another Long Password 1!") + "&_csrf=" + encode(nextCsrf)).statusCode()).isEqualTo(302);
    }

    @Test void loginAttemptsAreBoundedUntilWindowExpires() throws Exception {
        seed("limited@example.test", "viewer");
        var browser = new Browser();
        String token = csrf(browser.get("/management/login").body());
        for (int i = 0; i < 5; i++) {
            assertThat(browser.postForm("/management/login", "email=limited%40example.test&password=wrong&_csrf="
                    + encode(token)).statusCode()).isEqualTo(302);
        }
        assertThat(browser.postForm("/management/login", "email=limited%40example.test&password="
                + encode(PASSWORD) + "&_csrf=" + encode(token)).headers().firstValue("Location").orElseThrow())
                .endsWith("/management/login?error");
        clock.advance(Duration.ofMinutes(15));
        assertThat(browser.postForm("/management/login", "email=limited%40example.test&password="
                + encode(PASSWORD) + "&_csrf=" + encode(token)).headers().firstValue("Location").orElseThrow())
                .endsWith("/management/home");
    }

    private long seed(String email, String role) {
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, role, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(PASSWORD), clock.millis());
        return jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email);
    }
    private static String csrf(String html) {
        var match = CSRF.matcher(html);
        assertThat(match.find()).isTrue();
        return match.group(1);
    }
    private static String jsonCsrf(String json) {
        var match = Pattern.compile("\"csrfToken\":\"([^\"]+)\"").matcher(json);
        assertThat(match.find()).isTrue();
        return match.group(1);
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> get(String path) throws Exception { return send(HttpRequest.newBuilder(uri(path)).GET().build()); }
        HttpResponse<String> getJwt(String path, String jwt) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + jwt).GET().build());
        }
        HttpResponse<String> postForm(String path, String body) throws Exception {
            return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }
        HttpResponse<String> postJson(String path, String body, String csrf) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json");
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            return send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build());
        }
        HttpResponse<String> send(HttpRequest request) throws Exception {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
