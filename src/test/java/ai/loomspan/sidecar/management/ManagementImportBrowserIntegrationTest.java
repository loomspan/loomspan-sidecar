package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.bundle.ConfigurationBundleV1;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import ai.loomspan.api.SkillDocument;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.List;
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
class ManagementImportBrowserIntegrationTest {
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired RuntimeConfigurationService runtime;
    @Autowired ConfigurationSnapshotStore store;
    @Autowired ManagementEditingState editing;
    @LocalServerPort int port;

    @Test void editorExplicitlyReviewsAndConfirmsWhileViewerCannotImport() throws Exception {
        synchronized (editing) { editing.clearAll(); }
        Path bundle = ConfigurationBundleV1.write(runtime.publishedSnapshot());
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            try (BrowserContext viewer = browser.newContext()) {
                Page page = login(viewer, seed("viewer"));
                assertThat(page.locator("nav a:has-text('Import configuration')").count()).isZero();
                assertThat(page.navigate(url("/management/configuration/import")).status()).isEqualTo(403);
            }
            try (BrowserContext editor = browser.newContext()) {
                Page page = login(editor, seed("editor"));
                page.navigate(url("/management/configuration/import"));
                assertThat(page.locator("#import-confirm").isDisabled()).isTrue();
                assertThat(page.locator("#import-warning").textContent()).contains("every private draft", "breaks the editing lease");
                page.locator("#import-file").setInputFiles(bundle);
                Object rejectedWithoutCsrf = page.evaluate("""
                        async () => {
                          const form = new FormData();
                          form.append('bundle', document.querySelector('#import-file').files[0]);
                          return (await fetch('/api/management/configuration/import/review',
                              {method: 'POST', credentials: 'same-origin', body: form})).status;
                        }
                        """);
                assertThat(rejectedWithoutCsrf).isEqualTo(403);
                page.locator("#import-review").click();
                page.locator("#import-status").getByText("Review passed", new com.microsoft.playwright.Locator.GetByTextOptions()
                        .setExact(false)).waitFor();
                assertThat(page.locator("#import-details").textContent()).contains(runtime.inspect().publishedId().toString(),
                        "Skill documents", "Validated skills", "REST routes");
                assertThat(page.locator("#import-confirm").isEnabled()).isTrue();
                int history = store.history().size();
                page.locator("#import-file").setInputFiles(new Path[0]);
                assertThat(page.locator("#import-confirm").isDisabled()).isTrue();
                assertThat(store.history()).hasSize(history);
                page.locator("#import-file").setInputFiles(bundle);
                page.evaluate("""
                        () => {
                          const original = window.fetch.bind(window);
                          window.fetch = (...args) => {
                            window.fetch = original;
                            return original(...args).then(response => new Promise(resolve => {
                              window.releaseImportReview = () => resolve(response);
                            }));
                          };
                        }
                        """);
                page.locator("#import-review").click();
                page.waitForFunction("() => typeof window.releaseImportReview === 'function'");
                page.locator("#import-file").setInputFiles(new Path[0]);
                page.evaluate("() => window.releaseImportReview()");
                page.waitForTimeout(100);
                assertThat(page.locator("#import-confirm").isDisabled()).isTrue();
                assertThat(page.locator("#import-summary").isHidden()).isTrue();
                page.locator("#import-file").setInputFiles(bundle);
                page.locator("#import-review").click();
                page.locator("#import-confirm").click();
                page.locator("#import-outcome").getByText("Import published as local snapshot",
                        new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
                assertThat(store.history()).hasSize(history + 1);
                assertThat(page.locator("#import-confirm").isDisabled()).isTrue();
            }
        } finally { Files.deleteIfExists(bundle); }
    }

    @Test void reviewRendersHostileLabelsAsTextAndDisconnectedConfirmIsUnknown() throws Exception {
        synchronized (editing) { editing.clearAll(); }
        String marker = "</dd><script>window.__importInjected=1</script>";
        Path invalid = ConfigurationBundleV1.write(new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(new SkillDocument(marker, "name: invalid\n")),
                        "targets: {}\nroutes: {}\n"), SnapshotStatus.PUBLISHED));
        Path valid = ConfigurationBundleV1.write(runtime.publishedSnapshot());
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = login(context, seed("admin"));
            page.navigate(url("/management/configuration/import"));
            page.locator("#import-file").setInputFiles(invalid);
            page.locator("#import-review").click();
            page.locator("#import-status").getByText("Destination validation failed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#import-issues").textContent()).contains(marker);
            assertThat(page.evaluate("window.__importInjected")).isNull();
            page.locator("#import-file").setInputFiles(valid);
            page.locator("#import-review").click();
            page.locator("#import-status").getByText("Review passed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            int history = store.history().size();
            page.route("**/api/management/configuration/import/confirm", route -> route.abort());
            page.locator("#import-confirm").click();
            page.locator("#import-outcome").getByText("outcome is unknown",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#import-confirm").isDisabled()).isTrue();
            assertThat(store.history()).hasSize(history);
            page.unroute("**/api/management/configuration/import/confirm");
            page.locator("#import-review").click();
            page.locator("#import-status").getByText("Review passed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            page.route("**/api/management/configuration/import/confirm", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(503)
                            .setContentType("application/json")
                            .setBody("{\"code\":\"activation_failed\",\"error\":\"Publication failed\"}")));
            page.locator("#import-confirm").click();
            page.locator("#import-outcome").getByText("Import activation failed; the intended pointer was restored",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            page.unroute("**/api/management/configuration/import/confirm");
            page.locator("#import-review").click();
            page.locator("#import-status").getByText("Review passed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            page.route("**/api/management/configuration/import/confirm", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(503)
                            .setContentType("application/json")
                            .setBody("{\"code\":\"outcome_recording_failed\",\"error\":\"Outcome recording failed\"}")));
            page.locator("#import-confirm").click();
            page.locator("#import-outcome").getByText("Import activated, but bookkeeping failed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            page.unroute("**/api/management/configuration/import/confirm");
            page.locator("#import-review").click();
            page.locator("#import-status").getByText("Review passed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            page.route("**/api/management/configuration/import/confirm", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(409)
                            .setContentType("application/json")
                            .setBody("{\"code\":\"confirmation_stale\",\"observation\":{\"publishedId\":\""
                                    + runtime.inspect().publishedId() + "\",\"grantId\":null,\"leaseOwner\":null}}")));
            page.locator("#import-confirm").click();
            page.locator("#import-outcome").getByText("Confirmation changed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#import-details").textContent()).contains("Framework producer version", "REST routes");
            assertThat(page.locator("#import-confirm").isEnabled()).isTrue();
        } finally { Files.deleteIfExists(invalid); Files.deleteIfExists(valid); }
    }

    private String seed(String role) {
        String email = "import-browser-" + UUID.randomUUID() + "-" + role + "@example.test";
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
