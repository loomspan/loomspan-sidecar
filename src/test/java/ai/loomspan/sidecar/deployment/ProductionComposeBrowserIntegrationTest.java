package ai.loomspan.sidecar.deployment;

import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionComposeBrowserIntegrationTest {
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
}
