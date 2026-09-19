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
        assertThat(Math.abs(browserClock.millis() - Clock.systemUTC().millis())).isLessThan(5_000);
        synchronized (editingState) {
            editingState.lease = null;
            editingState.drafts.clear();
        }
        var publishedConfiguration = runtime.current().published().configuration();
        if (!publishedConfiguration.skillDocuments().isEmpty()
                || !publishedConfiguration.restRoutesYaml().equals("targets: {}\nroutes: {}\n")) {
            var draft = new ConfigurationDraft(runtime.current().published());
            draft.replaceContent(new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n"));
            var frozen = draft.freeze();
            draft.recordValidation(frozen, runtime.validate(frozen.configuration()));
            runtime.publish(draft::validatedCandidate);
        }
    }

    @Test void autoSaveAndValidateCurrentCompleteDraft() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-add").click();
            page.locator("#editor-skills input").fill("</textarea><script>window.__injected=1</script>");
            page.locator("#editor-skills textarea").fill("name: browser-draft\ndescription: Draft skill");
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# ${REST_BASE_URL}");
            page.locator("#editor-save").getByText("Saved to private draft").waitFor();
            page.locator("#editor-validation-state").getByText("Validation errors").waitFor();
            assertThat(page.locator("#editor-details").getAttribute("open")).isNull();
            assertThat(page.evaluate("document.activeElement.id")).isEqualTo("editor-rest");
            assertThat(page.locator("#editor-publish").isDisabled()).isTrue();
            assertThat(page.locator("#editor-rest").inputValue()).contains("${REST_BASE_URL}");
            assertThat(page.locator("#editor-skills textarea").inputValue()).contains("browser-draft");
            assertThat(page.evaluate("window.__injected")).isNull();
            page.locator("#editor-details summary").focus();
            page.locator("#editor-details summary").press("Enter");
            assertThat(page.locator("#editor-details").getAttribute("open")).isNotNull();
            assertThat(page.locator("#editor-issues").textContent()).contains("ERROR");
            page.locator("#editor-release").click();
            page.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            context.close();
        }
    }

    @Test void separateTabsCannotShareGrantAndViewerIsReadOnly() {
        String editor = seed("editor");
        String viewer = seed("viewer");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page first = login(context, editor);
            first.navigate(url("/management/configuration/edit"));
            first.locator("#editor-acquire").click();
            first.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            Page second = context.newPage();
            second.navigate(url("/management/configuration/edit"));
            second.locator("#editor-owner").getByText("Another tab in this session is editing.").waitFor();
            assertThat(second.locator("#editor-rest").isEditable()).isFalse();
            second.locator("#editor-acquire").click();
            assertThat(second.locator("#editor-rest").isEditable()).isFalse();
            first.locator("#editor-release").click();
            first.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            context.close();

            BrowserContext viewerContext = browser.newContext();
            Page viewerPage = login(viewerContext, viewer);
            viewerPage.navigate(url("/management/configuration/edit"));
            viewerPage.locator("#editor-message").getByText("Runtime configuration is read-only for viewers.").waitFor();
            assertThat(viewerPage.locator("#editor-acquire").isHidden()).isTrue();
            assertThat(viewerPage.locator("#editor-rest").isEditable()).isFalse();
            viewerContext.close();
        }
    }

    @Test void failedSaveRetainsTextAndExplicitRetryUsesCurrentContent() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            AtomicBoolean failed = new AtomicBoolean();
            AtomicInteger attempts = new AtomicInteger();
            page.route("**/api/management/editing/draft", route -> {
                if (route.request().method().equals("PUT")) {
                    attempts.incrementAndGet();
                    if (failed.compareAndSet(false, true)) {
                        route.fulfill(new com.microsoft.playwright.Route.FulfillOptions().setStatus(503)
                                .setContentType("application/json").setBody("{\"code\":\"fixture_unavailable\",\"error\":\"Fixture failure\"}"));
                        return;
                    }
                }
                route.resume();
            });
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# first");
            page.locator("#editor-save").getByText("Save failed — unsaved edits").waitFor();
            assertThat(page.locator("#editor-rest").inputValue()).contains("# first");
            page.waitForTimeout(800);
            assertThat(attempts.get()).isEqualTo(1);
            page.locator("#editor-retry").click();
            page.locator("#editor-save").getByText("Saved to private draft").waitFor();
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# latest");
            page.locator("#editor-save").getByText("Saved to private draft").waitFor();
            assertThat(page.locator("#editor-rest").inputValue()).contains("# latest");
            page.locator("#editor-release").click();
            page.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            context.close();
        }
    }

    @Test void validDraftPublishesOnlyAfterValidation() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# valid browser draft");
            assertThat(page.locator("#editor-publish").isDisabled()).isTrue();
            page.locator("#editor-validation-state").getByText("Valid").waitFor();
            assertThat(page.locator("#editor-publish").isEnabled()).isTrue();
            page.locator("#editor-publish").click();
            page.locator("#editor-outcome").getByText("Published successfully.", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#editor-publish").isDisabled()).isTrue();
            assertThat(page.locator("#editor-outcome").textContent()).contains("Runtime:", "Intended:");
            context.close();
        }
    }

    @Test void completeSkillAndRestYamlCanBeAddedRemovedAndPublished() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-add").click();
            page.locator("#editor-skills input").nth(0).fill("model.yaml");
            page.locator("#editor-skills textarea").nth(0).fill("name: modelDraft\ndescription: Model-backed draft.\nmodel: fixture-model\nplanning_mode: false\n");
            page.locator("#editor-add").click();
            page.locator("#editor-skills input").nth(1).fill("rest.yaml");
            page.locator("#editor-skills textarea").nth(1).fill("name: echoRest\ndescription: REST draft.\nrest: true\n");
            page.locator(".editor-remove").nth(0).click();
            assertThat(page.locator("#editor-skills textarea").count()).isEqualTo(1);
            assertThat(page.locator("#editor-skills textarea").inputValue()).contains("echoRest");
            page.locator("#editor-rest").fill("""
                    targets:
                      callback: {base-url: http://127.0.0.1:9, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                    routes:
                      echoRest: {target: callback, method: GET, path: /echo}
                    # ${CALLBACK_URL}
                    """);
            page.locator("#editor-validation-state").getByText("Valid").waitFor();
            page.locator("#editor-publish").click();
            page.locator("#editor-outcome").getByText("Published successfully.", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            String published = (String) page.evaluate("""
                    async () => JSON.stringify(await (await fetch('/api/management/configuration/current')).json())
                    """);
            assertThat(published).contains("echoRest", "rest.yaml", "callback", "/echo", "${CALLBACK_URL}")
                    .doesNotContain("modelDraft");
            context.close();
        }
    }

    @Test void lateSaveAcknowledgementCannotMarkNewerTextSaved() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.addInitScript("""
                (() => {
                  const original = window.fetch;
                  window.fetch = async (...args) => {
                    const response = await original(...args);
                    if (!window.__heldSave && String(args[0]).endsWith('/editing/draft') && args[1]?.method === 'PUT') {
                      window.__heldSave = true;
                      await new Promise(resolve => { window.__releaseSave = resolve; });
                    }
                    return response;
                  };
                })();
                """);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# first revision");
            page.waitForFunction("() => typeof window.__releaseSave === 'function'");
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# newest revision");
            assertThat(page.locator("#editor-save").textContent()).isNotEqualTo("Saved to private draft");
            assertThat(page.locator("#editor-publish").isDisabled()).isTrue();
            page.evaluate("window.__releaseSave()");
            page.locator("#editor-save").getByText("Saved to private draft").waitFor();
            assertThat(page.locator("#editor-rest").inputValue()).contains("newest revision");
            page.locator("#editor-validation-state").getByText("Valid").waitFor();
            page.locator("#editor-release").click();
            page.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            context.close();
        }
    }

    @Test void releaseResumeAndDiscardFollowPrivateDraftLifecycle() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# private draft");
            page.locator("#editor-save").getByText("Saved to private draft").waitFor();
            page.locator("#editor-release").click();
            page.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            assertThat(page.locator("#editor-rest").inputValue()).contains("private draft");
            assertThat(page.locator("#editor-rest").isEditable()).isFalse();
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            assertThat(page.locator("#editor-rest").inputValue()).contains("private draft");
            page.onDialog(dialog -> dialog.accept());
            page.locator("#editor-discard").click();
            page.locator("#editor-message").getByText("Draft discarded.", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            assertThat(page.locator("#editor-rest").inputValue()).doesNotContain("private draft");
            page.locator("#editor-release").click();
            page.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            context.close();
        }
    }

    @Test void otherUserCannotReadDraftAndAdminTakeoverStartsFromRuntime() {
        String firstEmail = seed("editor"), otherEmail = seed("editor"), adminEmail = seed("admin");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext firstContext = browser.newContext(), otherContext = browser.newContext(), adminContext = browser.newContext();
            Page first = login(firstContext, firstEmail);
            first.navigate(url("/management/configuration/edit"));
            first.locator("#editor-acquire").click();
            first.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            first.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# private first user");
            first.locator("#editor-save").getByText("Saved to private draft").waitFor();

            Page other = login(otherContext, otherEmail);
            other.navigate(url("/management/configuration/edit"));
            other.locator("#editor-owner").getByText("Another editor is editing.").waitFor();
            assertThat(other.locator("#editor-rest").inputValue()).doesNotContain("private first user");
            assertThat(other.locator("#editor-rest").isEditable()).isFalse();

            Page admin = login(adminContext, adminEmail);
            admin.navigate(url("/management/configuration/edit"));
            admin.locator("#editor-takeover").waitFor();
            admin.onDialog(dialog -> dialog.accept());
            admin.locator("#editor-takeover").click();
            admin.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            assertThat(admin.locator("#editor-rest").inputValue()).doesNotContain("private first user");
            admin.locator("#editor-release").click();
            admin.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            firstContext.close(); otherContext.close(); adminContext.close();
        }
    }

    @Test void disconnectedPublishShowsUnknownOutcomeWithoutRetry() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# candidate for uncertain result");
            page.locator("#editor-validation-state").getByText("Valid").waitFor();
            AtomicInteger attempts = new AtomicInteger();
            page.route("**/api/management/configuration/publish", route -> {
                attempts.incrementAndGet(); route.abort();
            });
            page.locator("#editor-publish").click();
            page.locator("#editor-outcome").getByText("outcome is unknown", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#editor-publish").isDisabled()).isTrue();
            assertThat(attempts.get()).isEqualTo(1);
            assertThat(page.locator("#editor-outcome").textContent()).contains("Runtime:", "Intended:");
            page.locator("#editor-release").click();
            page.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            context.close();
        }
    }

    @Test void knownActivationFailureExplainsThatThePreviousRuntimeRemainsActive() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# failed activation");
            page.locator("#editor-validation-state").getByText("Valid").waitFor();
            page.route("**/api/management/configuration/publish", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(503)
                            .setContentType("application/json")
                            .setBody("{\"code\":\"activation_failed\",\"error\":\"Publication failed\"}")));
            page.locator("#editor-publish").click();
            page.locator("#editor-outcome").getByText("Activation failed; the previous runtime remains active.",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#editor-rest").inputValue()).contains("failed activation");
            assertThat(page.locator("#editor-rest").isEditable()).isTrue();
            assertThat(page.locator("#editor-outcome").textContent()).contains("Mutation fault: none");
            context.close();
        }
    }

    @Test void bookkeepingFailureExplainsActiveRuntimeAndDisablesMutations() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# active despite bookkeeping fault");
            page.locator("#editor-validation-state").getByText("Valid").waitFor();
            String activatedId = UUID.randomUUID().toString();
            page.route("**/api/management/configuration/publish", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setStatus(503)
                            .setContentType("application/json")
                            .setBody("{\"code\":\"outcome_recording_failed\",\"error\":\"Outcome unavailable\"}")));
            page.route("**/api/management/configuration/current", route -> route.fulfill(
                    new com.microsoft.playwright.Route.FulfillOptions().setContentType("application/json")
                            .setBody("{\"published\":{\"localId\":\"" + activatedId
                                    + "\",\"status\":\"PENDING\"},\"intendedId\":\"" + activatedId
                                    + "\",\"intendedStatus\":\"PENDING\",\"mutationFault\":\"Outcome recording failed\"}")));
            page.locator("#editor-publish").click();
            page.locator("#editor-outcome").getByText("The new configuration is active, but its outcome could not be recorded.",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#editor-outcome").textContent()).contains("Runtime: " + activatedId,
                    "Intended: " + activatedId, "Mutation fault: Outcome recording failed");
            assertThat(page.locator("#editor-acquire").isHidden()).isTrue();
            assertThat(page.locator("#editor-publish").isDisabled()).isTrue();
            context.close();
        }
    }

    @Test void meaningfulActivityUsesOneReportPerCadenceAndBackgroundTrafficDoesNotReport() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.addInitScript("""
                (() => {
                  window.__activityPaths = [];
                  const original = window.fetch;
                  window.fetch = (...args) => {
                    if (String(args[0]).endsWith('/activity')) window.__activityPaths.push(String(args[0]));
                    return original(...args);
                  };
                })();
                """);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            int before = ((Number) page.evaluate("window.__activityPaths.length")).intValue();
            page.evaluate("""
                () => {
                  const original = Date.now;
                  Date.now = () => original() + 31000;
                  document.dispatchEvent(new Event('mousemove'));
                }
                """);
            assertThat(((Number) page.evaluate("window.__activityPaths.length")).intValue()).isEqualTo(before);
            page.evaluate("document.dispatchEvent(new Event('paste'))");
            page.waitForFunction("count => window.__activityPaths.length > count", before);
            int afterPaste = ((Number) page.evaluate("window.__activityPaths.length")).intValue();
            assertThat((String) page.evaluate("window.__activityPaths.at(-1)")).endsWith("/editing/lease/activity");
            page.evaluate("document.dispatchEvent(new Event('scroll'))");
            assertThat(((Number) page.evaluate("window.__activityPaths.length")).intValue()).isEqualTo(afterPaste);
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# background requests");
            page.locator("#editor-validation-state").getByText("Valid").waitFor();
            assertThat(((Number) page.evaluate("window.__activityPaths.length")).intValue()).isEqualTo(afterPaste);
            page.locator("#editor-release").click();
            page.locator("#editor-release").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.HIDDEN));
            context.close();
        }
    }

    @Test void leaseWarningAndLossPreserveLocalTextButStopWrites() {
        String email = seed("editor");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext();
            Page page = login(context, email);
            page.navigate(url("/management/configuration/edit"));
            page.locator("#editor-acquire").click();
            page.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            synchronized (editingState) {
                editingState.lease.expiresAt = Clock.systemUTC().millis() + 90_000;
            }
            page.locator("#editor-deadline").getByText("expires within two minutes", new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(page.locator("#editor-continue").isVisible()).isTrue();
            synchronized (editingState) { editingState.lease = null; }
            page.locator("#editor-rest").fill("targets: {}\nroutes: {}\n# retained on lease loss");
            page.locator("#editor-rest").waitFor(new com.microsoft.playwright.Locator.WaitForOptions()
                    .setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            page.locator("#editor-owner").getByText("No tab currently holds the editing lease.").waitFor();
            assertThat(page.locator("#editor-rest").inputValue()).contains("retained on lease loss");
            assertThat(page.locator("#editor-rest").isEditable()).isFalse();
            context.close();
        }
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
