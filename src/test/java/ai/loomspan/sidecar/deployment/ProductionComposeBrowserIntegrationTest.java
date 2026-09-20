package ai.loomspan.sidecar.deployment;

import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionComposeBrowserIntegrationTest {
    private static Page signIn(BrowserContext context, String origin, String email, String password) {
        Page page = context.newPage();
        page.navigate(origin + "/management/login");
        page.locator("input[name=email]").fill(email);
        page.locator("input[name=password]").fill(password);
        page.locator("button").first().click();
        page.waitForURL("**/management/home");
        return page;
    }

    @Test
    void productionHttpsPasswordLinkAndLogin() {
        String origin = System.getenv("PRODUCTION_TEST_ORIGIN");
        Assumptions.assumeTrue(origin != null && !origin.isBlank(), "run through verify-production.py");
        String email = System.getenv("PRODUCTION_TEST_EMAIL");
        String password = System.getenv("PRODUCTION_TEST_PASSWORD");
        String setupLink = System.getenv("PRODUCTION_SETUP_LINK");
        String recoveryLink = System.getenv("PRODUCTION_RECOVERY_LINK");
        assertThat(origin).startsWith("https://");
        assertThat(email).endsWith(".test");
        try (Playwright playwright = Playwright.create(); var browser = playwright.chromium().launch()) {
            var context = browser.newContext(new com.microsoft.playwright.Browser.NewContextOptions()
                    .setIgnoreHTTPSErrors(true));
            var page = context.newPage();
            String passwordLink = setupLink != null && !setupLink.isBlank() ? setupLink : recoveryLink;
            if (passwordLink != null && !passwordLink.isBlank()) {
                assertThat(passwordLink).startsWith(origin + "/management/password/");
                page.navigate(passwordLink);
                page.locator("input[name=password]").fill(password);
                page.locator("button").first().click();
                assertThat(page.locator("body").textContent()).contains("Password saved. Sign in normally.");
            }
            page.navigate(origin + "/management/login");
            page.locator("input[name=email]").fill(email);
            page.locator("input[name=password]").fill(password);
            page.locator("button").first().click();
            page.waitForURL("**/management/home");
            assertThat(page.locator("body").textContent()).contains("Current configuration");
            assertThat(context.cookies()).anySatisfy(cookie -> {
                assertThat(cookie.name).isEqualTo("JSESSIONID");
                assertThat(cookie.secure).isTrue();
            });
        }
    }

    @Test
    void productionHttpsInvitationAndConcurrentEditing() {
        String origin = System.getenv("PRODUCTION_TEST_ORIGIN");
        String editorEmail = System.getenv("PRODUCTION_EDITOR_EMAIL");
        Assumptions.assumeTrue(origin != null && editorEmail != null && !editorEmail.isBlank(),
                "run concurrent scenario through verify-production.py");
        String adminEmail = System.getenv("PRODUCTION_TEST_EMAIL");
        String password = System.getenv("PRODUCTION_TEST_PASSWORD");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext editor = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
            BrowserContext admin = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
            Page editing = signIn(editor, origin, editorEmail, password);
            editing.navigate(origin + "/management/configuration/edit");
            editing.locator("#editor-acquire").click();
            editing.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            String original = editing.locator("#editor-rest").inputValue();
            editing.locator("#editor-rest").fill(original + "\n# private compose browser edit");
            editing.locator("#editor-save").getByText("Saved to private draft").waitFor();

            Page takeover = signIn(admin, origin, adminEmail, password);
            takeover.navigate(origin + "/management/configuration/edit");
            takeover.locator("#editor-owner").getByText("Another editor is editing.").waitFor();
            assertThat(takeover.locator("#editor-rest").inputValue()).doesNotContain("private compose browser edit");
            assertThat(takeover.locator("#editor-rest").isEditable()).isFalse();
            assertThat(takeover.evaluate("""
                    async () => (await fetch('/api/management/editing/draft')).status
                    """)).isEqualTo(404);
            takeover.onDialog(dialog -> dialog.accept());
            takeover.locator("#editor-takeover").click();
            takeover.locator("#editor-owner").getByText("You are editing in this tab.").waitFor();
            editing.locator("#editor-rest").fill(original + "\n# stale browser write");
            editing.locator("#editor-message").getByText("Editing state changed", new com.microsoft.playwright.Locator.GetByTextOptions()
                    .setExact(false)).waitFor();
            takeover.locator("#editor-rest").fill(original + "\n# accepted compose browser update");
            takeover.locator("#editor-validation-state").getByText("Valid").waitFor();
            takeover.locator("#editor-publish").click();
            takeover.locator("#editor-outcome").getByText("Published successfully.",
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).waitFor();
            assertThat(takeover.locator("#editor-publish").isDisabled()).isTrue();
            assertThat(takeover.evaluate("""
                    async () => (await fetch('/api/management/editing/draft')).status
                    """)).isEqualTo(404);
        }
    }

    @Test
    void productionHistoryAndImportInspection() {
        String origin = System.getenv("PRODUCTION_TEST_ORIGIN");
        String historyId = System.getenv("PRODUCTION_HISTORY_ID");
        Assumptions.assumeTrue(origin != null && historyId != null && !historyId.isBlank(),
                "run destination inspection through verify-production.py");
        try (Playwright playwright = Playwright.create(); Browser browser = playwright.chromium().launch()) {
            BrowserContext context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
            Page page = signIn(context, origin, System.getenv("PRODUCTION_TEST_EMAIL"),
                    System.getenv("PRODUCTION_TEST_PASSWORD"));
            page.navigate(origin + "/management/configuration/history");
            page.locator("#history-list").getByText(historyId,
                    new com.microsoft.playwright.Locator.GetByTextOptions().setExact(false)).first().waitFor();
            assertThat(page.locator("#history-rollback").isVisible()).isTrue();
            page.navigate(origin + "/management/configuration/import");
            assertThat(page.locator("#import-file").isVisible()).isTrue();
            assertThat(page.locator("#import-warning").textContent())
                    .contains("discards every private draft", "breaks the editing lease");
        }
    }
}
