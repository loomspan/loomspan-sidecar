package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
        "loomspan-sidecar.management.edit-lease-timeout=17m",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
@Import(ManagementEditorBrowserIntegrationTest.BrowserClockConfiguration.class)
class ManagementEditorBrowserIntegrationTest {
    @TestConfiguration(proxyBeanMethods = false)
    static class BrowserClockConfiguration {
        @Bean @Primary Clock browserClock() { return Clock.systemUTC(); }
    }
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ManagementEditingState editingState;
    @Autowired RuntimeConfigurationService runtime;
    @Autowired Clock browserClock;
    @LocalServerPort int port;

    @BeforeEach void clearEditingFixture() {
        synchronized (editingState) { editingState.clearLease(); }
    }

    @Test void readOnlySessionFollowsSavedRevisionAndHandoffKeepsUnsentText() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext firstContext = browser.newContext(); BrowserContext secondContext = browser.newContext()) {
            Page first = login(firstContext, email), second = login(secondContext, email);
            first.navigate(url("/management/configuration/edit"));
            second.navigate(url("/management/configuration/edit"));
            waitText(first, "editor-message", "Saved draft and published configuration loaded.");
            waitText(second, "editor-message", "Saved draft and published configuration loaded.");
            first.locator("#editor-acquire").click();
            waitText(first, "editor-owner", "You hold editing control.");
            first.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# saved-from-first\n");
            waitText(first, "editor-save", "Saved draft revision");
            second.waitForFunction("() => document.getElementById('editor-rest').value.includes('saved-from-first')");
            assertThat(second.locator("#editor-rest").isEditable()).isFalse();
            first.route("**/api/management/editing/draft", route -> {
                if (route.request().method().equals("PUT")) route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                        .setStatus(409).setContentType("application/json").setBody("{\"code\":\"revision_conflict\"}"));
                else route.resume();
            });
            first.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# unsent-local\n");
            waitText(first, "editor-save", "Unsaved local changes");
            second.locator("#editor-handoff").click();
            waitText(second, "editor-owner", "You hold editing control.");
            first.waitForFunction("() => document.getElementById('editor-rest').readOnly");
            assertThat(first.locator("#editor-rest").inputValue()).contains("unsent-local");
            assertThat(first.locator("#editor-saved-content").textContent()).contains("saved-from-first")
                    .doesNotContain("unsent-local");
            assertThat(second.locator("#editor-rest").inputValue()).contains("saved-from-first").doesNotContain("unsent-local");
            first.onceDialog(dialog -> dialog.accept());
            first.locator("#editor-handoff").click();
            first.locator("#editor-resume-local").waitFor();
            assertThat(first.locator("#editor-rest").isEditable()).isFalse();
            assertThat(first.locator("#editor-rest").inputValue()).contains("unsent-local");
            first.locator("#editor-resume-local").click();
            assertThat(first.locator("#editor-rest").isEditable()).isTrue();
        }
    }

    @Test void sameLoginSessionHandoffMakesPreviousTabReadOnly() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page first = login(context, email);
            first.navigate(url("/management/configuration/edit"));
            waitText(first, "editor-message", "Saved draft and published configuration loaded.");
            first.locator("#editor-acquire").click();
            waitText(first, "editor-owner", "You hold editing control.");
            Page second = context.newPage();
            second.navigate(url("/management/configuration/edit"));
            waitText(second, "editor-message", "Saved draft and published configuration loaded.");
            second.locator("#editor-handoff").click();
            waitText(second, "editor-owner", "You hold editing control.");
            first.waitForFunction("() => document.getElementById('editor-rest').readOnly");
            assertThat(first.locator("#editor-rest").isEditable()).isFalse();
            assertThat(second.locator("#editor-rest").isEditable()).isTrue();
        }
    }

    private static void waitText(Page page, String id, String expected) {
        page.waitForFunction("() => document.getElementById('" + id + "')?.textContent.includes('" + expected + "')");
    }

    private Page login(BrowserContext context, String email) {
        Page page = context.newPage();
        page.navigate(url("/management/login"));
        page.locator("input[name=email]").fill(email);
        page.locator("input[name=password]").fill("Long Password 123!");
        page.locator("button:has-text('Sign in')").click();
        page.waitForURL(url("/management/home"));
        return page;
    }
    private String seed(String role) {
        String email = "browser-" + UUID.randomUUID() + "-" + role + "@example.test";
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, role, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode("Long Password 123!"), Clock.systemUTC().millis());
        return email;
    }
    private String url(String path) { return "http://127.0.0.1:" + port + path; }
}
