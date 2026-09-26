package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV2;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import ai.loomspan.api.SkillDocument;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
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
        "management.server.port=0", "server.servlet.session.cookie.secure=false",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
class ManagementHistoryBrowserIntegrationTest {
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RuntimeConfigurationService runtime;
    @Autowired ConfigurationSnapshotStore store;
    @Autowired ManagementEditingState editingState;
    @LocalServerPort int port;

    @BeforeEach void reset() { synchronized (editingState) { editingState.clearLease(); } }

    @Test void retainedHistoryLoadsIntoPrivateDraftWithoutRuntimeCutover() throws Exception {
        String email = seed("editor");
        var before = runtime.inspect().publishedId();
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = login(context, email);
            page.navigate(url("/management/configuration/history"));
            page.locator("#history-list button").first().click();
            page.locator("#rollback-review").click();
            page.locator("#rollback-confirm").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            page.onceDialog(dialog -> dialog.accept());
            page.locator("#rollback-confirm").click();
            page.waitForFunction("() => document.getElementById('rollback-status').textContent.includes('Loaded saved draft revision')");
            assertThat(runtime.inspect().publishedId()).isEqualTo(before);
            page.navigate(url("/management/configuration/edit"));
            page.waitForFunction("() => document.getElementById('editor-save').textContent.includes('Saved draft revision')");
        }
    }

    private String seed(String role) {
        String email = "history-" + UUID.randomUUID() + "-" + role + "@example.test";
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, role, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode("Long Password 123!"), Clock.systemUTC().millis());
        return email;
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

    private String url(String path) { return "http://127.0.0.1:" + port + path; }
}
