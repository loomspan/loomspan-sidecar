package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV1;
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

    @BeforeEach void reset() {
        synchronized (editingState) { editingState.clearAll(); }
        var current = runtime.current().published();
        store.updateStatus(current.localId(), SnapshotStatus.PUBLISHED);
        if (!current.configuration().skillDocuments().isEmpty()
                || !current.configuration().restRoutesYaml().equals("targets: {}\nroutes: {}\n")) {
            publish(new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n"));
        }
    }

    @Test void viewerDownloadsCurrentBundleWithSensitiveContentNotice() throws Exception {
        String email = seed("viewer");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext(new Browser.NewContextOptions().setAcceptDownloads(true))) {
            Page page = login(context, email);
            page.navigate(url("/management/configuration/current"));
            assertThat(page.textContent("[data-console=current]")).contains("sensitive secrets and placeholders");
            var download = page.waitForDownload(() -> page.locator("#current-export").click());
            assertThat(download.suggestedFilename()).isEqualTo("sidecar-current-configuration.zip");
            var bundle = ConfigurationBundleV1.read(download.path());
            assertThat(bundle.sourceSnapshotId()).isEqualTo(runtime.publishedSnapshot().localId());
            assertThat(bundle.configuration()).isEqualTo(runtime.publishedSnapshot().configuration());
            assertThat(page.locator("#current-status").textContent()).contains("downloaded");
            page.route("**/api/management/configuration/export", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(413)
                            .setContentType("application/json")
                            .setBody("{\"code\":\"export_too_large\",\"error\":\"Runtime configuration exceeds format 1 bundle limits\"}")));
            page.locator("#current-export").click();
            page.locator("#current-status").getByText("exceeds format 1 bundle limits",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#current-status").getAttribute("class")).contains("error");
        }
    }

    @Test void viewerBrowsesOrderedHistoryAndExactSnapshotDetail() {
        String marker = "<script>window.__historyInjected=1</script> fixture-secret-value ${HISTORY_TARGET}";
        var initial = runtime.current().published();
        UUID sourceId = UUID.randomUUID();
        var failed = store.submit(new ManagedConfiguration(
                List.of(new SkillDocument("history.yaml", "name: retained\n# " + marker + "\n")),
                "targets: {}\nroutes: {}\n# failed retained\n"), sourceId, initial.localId());
        store.revert(failed.localId(), initial.localId());
        var published = publish(new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n# current marker\n"));
        String email = seed("viewer");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = login(context, email);
            page.locator("nav a:has-text('History')").focus();
            page.locator("nav a:has-text('History')").press("Enter");
            page.waitForURL(url("/management/configuration/history"));
            var failedButton = page.locator("#history-list button").filter(
                    new com.microsoft.playwright.Locator.FilterOptions().setHasText(failed.localId().toString()));
            failedButton.focus();
            assertThat(page.evaluate("document.activeElement.textContent").toString()).contains(failed.localId().toString());
            failedButton.press("Enter");
            page.locator("#history-detail").getByText(marker, new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            String listText = page.locator("#history-list").textContent();
            int failedPosition = listText.indexOf("Sequence " + failed.submissionSequence() + " — " + failed.localId());
            int publishedPosition = listText.indexOf("Sequence " + published.submissionSequence() + " — " + published.localId());
            assertThat(failedPosition).isGreaterThanOrEqualTo(0);
            assertThat(publishedPosition).isGreaterThan(failedPosition);
            assertThat(page.locator("#history-detail").textContent()).contains(failed.localId().toString(), "Recorded status", "FAILED",
                    sourceId.toString(), "history.yaml", "fixture-secret-value", "${HISTORY_TARGET}");
            assertThat(page.locator("#history-list").textContent()).contains(published.localId().toString(),
                    "Current runtime-published snapshot");
            assertThat(page.evaluate("window.__historyInjected")).isNull();
        }
    }

    @Test void editorReviewsAndConfirmsRollbackFromHistoryWhileViewerCannot() {
        var source = publish(new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n# rollback-browser-source\n"));
        publish(new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n# newer-runtime\n"));
        String editorEmail = seed("editor"), viewerEmail = seed("viewer");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext editorContext = browser.newContext(); BrowserContext viewerContext = browser.newContext()) {
            Page viewer = login(viewerContext, viewerEmail);
            viewer.navigate(url("/management/configuration/history"));
            assertThat(viewer.locator("#rollback-review, #rollback-confirm").count()).isZero();

            Page editor = login(editorContext, editorEmail);
            editor.navigate(url("/management/configuration/history"));
            editor.locator("#history-list button").filter(
                    new com.microsoft.playwright.Locator.FilterOptions().setHasText(source.localId().toString())).click();
            editor.locator("#history-detail").getByText("rollback-browser-source",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            editor.locator("#rollback-review").focus();
            editor.locator("#rollback-review").press("Enter");
            editor.locator("#rollback-status").getByText("Review passed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(editor.locator("#rollback-summary").textContent())
                    .contains("PUBLISHED", "Runtime-published snapshot", "Lease owner", "discards every private draft");
            editor.locator("#history-list button").last().click();
            assertThat(editor.locator("#rollback-confirm").isDisabled()).isTrue();
            editor.locator("#history-list button").filter(
                    new com.microsoft.playwright.Locator.FilterOptions().setHasText(source.localId().toString())).click();
            editor.locator("#rollback-review").click();
            editor.locator("#rollback-confirm").click();
            editor.locator("#rollback-status").getByText("published as new local snapshot",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(runtime.publishedSnapshot().sourceId()).isEqualTo(source.localId());
            assertThat(runtime.publishedSnapshot().configuration()).isEqualTo(source.configuration());
        }
    }

    @Test void allRolesReachHistoryAndInspectionDoesNotPublishOrPoll() {
        for (String role : List.of("viewer", "editor", "admin")) {
            String email = seed(role);
            try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                    BrowserContext context = browser.newContext()) {
                Page page = login(context, email);
                AtomicInteger publications = new AtomicInteger();
                AtomicInteger activity = new AtomicInteger();
                page.onRequest(request -> {
                    if (request.url().endsWith("/configuration/publish")) publications.incrementAndGet();
                    if (request.url().endsWith("/session/activity")) activity.incrementAndGet();
                });
                page.navigate(url("/management/configuration/history"));
                page.locator("#history-status").getByText("Retained submissions loaded", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
                assertThat(page.locator("#history-list button").count()).isPositive();
                assertThat(page.locator("#rollback-review").count()).isEqualTo("viewer".equals(role) ? 0 : 1);
                assertThat(page.locator("a[href='/management/configuration/recovery']").count()).isPositive();
                page.waitForTimeout(250);
                assertThat(publications).hasValue(0);
                assertThat(activity).hasValue(0);
            }
        }
    }

    @Test void rollbackBrowserReportsStaleMissingAndDisconnectedOutcomes() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = login(context, email);
            page.navigate(url("/management/configuration/history"));
            page.locator("#history-list button").last().click();
            page.locator("#rollback-review").click();
            page.locator("#rollback-confirm").waitFor();
            String confirm = "**/api/management/configuration/rollback/confirm";
            page.route(confirm, route -> route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                    .setStatus(409).setContentType("application/json")
                    .setBody("{\"code\":\"confirmation_stale\",\"error\":\"stale\"}")));
            page.locator("#rollback-confirm").click();
            page.locator("#rollback-status").getByText("Review the selected source again",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#rollback-confirm").isDisabled()).isTrue();
            page.unroute(confirm);

            page.locator("#rollback-review").click();
            page.locator("#rollback-status").getByText("Review passed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            page.route(confirm, route -> route.abort());
            page.locator("#rollback-confirm").click();
            page.locator("#rollback-status").getByText("outcome is unknown",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#history-current").textContent()).contains("Runtime-published local ID");
            page.unroute(confirm);

            page.route("**/api/management/configuration/rollback/*/review", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(409)
                            .setContentType("application/json")
                            .setBody("{\"code\":\"source_not_found\",\"error\":\"missing\"}")));
            page.locator("#rollback-review").click();
            page.locator("#rollback-status").getByText("no longer retained",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#rollback-review").isDisabled()).isTrue();
            assertThat(page.locator("#history-list button").count()).isPositive();
        }
    }

    @Test void expiredSelectedSnapshotClearsDetailAndOffersFreshState() {
        String email = seed("viewer");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = login(context, email);
            page.navigate(url("/management/configuration/history"));
            page.locator("#history-list button").first().click();
            page.locator("#history-detail pre").first().waitFor();
            page.route("**/api/management/configuration/history/*", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(404).setContentType("application/json")
                            .setBody("{\"code\":\"history_not_found\",\"error\":\"Snapshot is unknown or no longer retained\"}")));
            page.locator("#history-list button").first().click();
            page.locator("#history-status").getByText("expired", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#history-detail pre").count()).isZero();
            assertThat(page.locator("#history-list button").count()).isPositive();
            assertThat(page.locator("#history-current").textContent()).contains("Runtime-published local ID");
            assertThat(page.locator("a[href='/management/configuration/current']").count()).isPositive();

            page.route("**/api/management/configuration/history", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(503).setContentType("application/json")
                            .setBody("{\"error\":\"History refresh unavailable\"}")));
            page.locator("#history-list button").first().click();
            page.locator("#history-status").getByText("could not be refreshed",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#history-detail pre").count()).isZero();
            assertThat(page.locator("#history-list").textContent())
                    .contains("Retained submissions could not be loaded")
                    .doesNotContain("Loading retained submissions");
        }
    }

    @Test void failedCurrentRefreshDoesNotPresentStaleRuntimeAsCurrent() {
        String email = seed("viewer");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = login(context, email);
            page.navigate(url("/management/configuration/current"));
            page.locator("#current-content").getByText("Runtime-published snapshot").waitFor();
            page.route("**/api/management/configuration/current", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(503).setContentType("application/json")
                            .setBody("{\"error\":\"Current inspection unavailable\"}")));
            page.locator("#current-refresh").click();
            page.locator("#current-status").getByText("Current inspection unavailable").waitFor();
            assertThat(page.locator("#current-content").textContent()).isEmpty();
        }
    }

    @Test void olderCurrentResponseCannotReplaceNewerRefresh() {
        String email = seed("viewer");
        String currentId = runtime.current().published().localId().toString();
        String staleId = UUID.randomUUID().toString();
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page page = login(context, email);
            page.addInitScript("""
                    const originalFetch = window.fetch.bind(window);
                    window.fetch = (resource, options) => {
                      if (String(resource).endsWith('/api/management/configuration/current') && !window.__firstCurrentHeld) {
                        window.__firstCurrentHeld = true;
                        return new Promise(resolve => {
                          window.__releaseStaleCurrent = () => resolve(new Response(JSON.stringify({
                            published: {localId: '%s', sourceId: null, submissionSequence: 1,
                              status: 'PUBLISHED', configuration: {skillDocuments: [], restRoutesYaml: ''}},
                            intendedId: '%s', intendedStatus: 'PUBLISHED', mutationFault: null
                          }), {status: 200, headers: {'Content-Type': 'application/json'}}));
                        });
                      }
                      return originalFetch(resource, options);
                    };
                    """.formatted(staleId, staleId));
            page.navigate(url("/management/configuration/current"));
            page.waitForFunction("window.__releaseStaleCurrent !== undefined");
            page.locator("#current-refresh").click();
            page.locator("#current-content").getByText(currentId,
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).first().waitFor();
            page.evaluate("window.__releaseStaleCurrent()");
            page.waitForTimeout(150);
            assertThat(page.locator("#current-content").textContent()).contains(currentId).doesNotContain(staleId);
        }
    }

    @Test void pendingRuntimeAndMutationFaultUseTruthfulRecoveryLanguage() {
        String email = seed("viewer");
        String running = runtime.current().published().localId().toString();
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext context = browser.newContext()) {
            Page matching = login(context, email);
            matching.route("**/api/management/configuration/current", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setContentType("application/json").setBody(
                            "{\"published\":{\"localId\":\"" + running
                                    + "\",\"sourceId\":null,\"submissionSequence\":1,\"status\":\"PENDING\","
                                    + "\"configuration\":{\"skillDocuments\":[],\"restRoutesYaml\":\"targets: {}\\nroutes: {}\\n\"}},"
                                    + "\"intendedId\":\"" + running + "\",\"intendedStatus\":\"PENDING\","
                                    + "\"mutationFault\":\"Outcome bookkeeping failed\"}")));
            matching.navigate(url("/management/configuration/history"));
            matching.locator("#history-current").getByText("outcome unknown", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).first().waitFor();
            assertThat(matching.locator("#history-current").textContent()).contains(
                    "Runtime-published and intended restart snapshots match",
                    "does not change or resolve the recorded status", "acquisition", "save", "activity renewal",
                    "validation", "publication are blocked", "Inspection", "release or discard");
            assertThat(matching.locator("#history-current a").getAttribute("href"))
                    .isEqualTo("/management/configuration/recovery");

            Page mismatch = context.newPage();
            mismatch.route("**/api/management/configuration/current", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setContentType("application/json").setBody(
                            "{\"published\":{\"localId\":\"" + running
                                    + "\",\"sourceId\":null,\"submissionSequence\":1,\"status\":\"PUBLISHED\","
                                    + "\"configuration\":{\"skillDocuments\":[],\"restRoutesYaml\":\"targets: {}\\nroutes: {}\\n\"}},"
                                    + "\"intendedId\":\"" + UUID.randomUUID() + "\",\"intendedStatus\":\"PENDING\","
                                    + "\"mutationFault\":\"Revert bookkeeping failed\"}")));
            mismatch.navigate(url("/management/configuration/history"));
            mismatch.locator("#history-current").getByText("differ", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(mismatch.locator("#history-current").textContent()).contains(
                    "A restart follows the intended selection", "does not prove the outcome of an earlier operation");
        }
    }

    @Test void reloginInspectsAcceptedSubmissionWithoutRetryOrCancellation() {
        var accepted = publish(new ManagedConfiguration(List.of(),
                "targets: {}\nroutes: {}\n# accepted-after-disconnect\n"));
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            try (BrowserContext first = browser.newContext()) {
                Page page = login(first, email);
                page.navigate(url("/management/configuration/current"));
                page.locator("#current-content").getByText(accepted.localId().toString(),
                        new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).first().waitFor();
            }
            try (BrowserContext reconnected = browser.newContext()) {
                Page page = login(reconnected, email);
                AtomicInteger publications = new AtomicInteger();
                page.onRequest(request -> {
                    if (request.url().endsWith("/configuration/publish")) publications.incrementAndGet();
                });
                page.navigate(url("/management/configuration/history"));
                var acceptedButton = page.locator("#history-list button").filter(
                        new com.microsoft.playwright.Locator.FilterOptions().setHasText(accepted.localId().toString()));
                acceptedButton.click();
                page.locator("#history-detail").getByText("accepted-after-disconnect",
                        new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
                assertThat(page.locator("#history-detail").textContent()).contains(accepted.localId().toString());
                assertThat(page.locator("button:has-text('Cancel'), button:has-text('Retry publication')").count()).isZero();
                assertThat(publications).hasValue(0);
            }
        }
    }

    @Test void otherSessionsPrivateDraftNeverAppearsInHistory() {
        String editorEmail = seed("editor");
        String viewerEmail = seed("viewer");
        String adminEmail = seed("admin");
        String privateMarker = "private-history-marker-" + UUID.randomUUID();
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch();
                BrowserContext editorContext = browser.newContext()) {
            Page editor = login(editorContext, editorEmail);
            editor.navigate(url("/management/configuration/edit"));
            editor.locator("#editor-acquire").click();
            editor.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            editor.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# " + privateMarker);
            editor.locator("#editor-save").getByText("Saved to private draft").waitFor();

            for (String email : List.of(viewerEmail, adminEmail)) {
                try (BrowserContext inspecting = browser.newContext()) {
                    Page page = login(inspecting, email);
                    page.navigate(url("/management/configuration/history"));
                    page.locator("#history-status").getByText("Retained submissions loaded",
                            new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
                    int count = page.locator("#history-list button").count();
                    for (int index = 0; index < count; index++) {
                        page.locator("#history-list button").nth(index).click();
                        page.locator("#history-detail pre").first().waitFor();
                        assertThat(page.locator("#history-detail").textContent()).doesNotContain(privateMarker);
                    }
                }
            }
            editor.locator("#editor-release").click();
        }
    }

    private ai.loomspan.sidecar.storage.ConfigurationSnapshot publish(ManagedConfiguration configuration) {
        var draft = new ConfigurationDraft(runtime.current().published());
        draft.replaceContent(configuration);
        var frozen = draft.freeze();
        draft.recordValidation(frozen, runtime.validate(frozen.configuration()));
        return runtime.publish(draft::validatedCandidate);
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
