package ai.loomspan.sidecar.management;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ManagementPagesController {
    private final ManagementIdentityService identity;
    private final ManagementAttemptLimiter attempts;
    public ManagementPagesController(ManagementIdentityService identity, ManagementAttemptLimiter attempts) {
        this.identity = identity; this.attempts = attempts;
    }
    @GetMapping(value = "/management/login", produces = MediaType.TEXT_HTML_VALUE)
    String login(HttpServletRequest request) {
        String feedback = request.getParameter("error") != null ? "<p>Sign-in failed. Check the address and password, then retry.</p>"
                : request.getParameter("logout") != null ? "<p>Signed out.</p>" : "";
        return page("Sign in", feedback + "<form method='post' action='/management/login'>"
                + csrf(request) + "<label>Email <input name='email' type='email' autocomplete='username' required></label>"
                + "<label>Password <input name='password' type='password' autocomplete='current-password' required></label>"
                + "<button>Sign in</button></form><p><a href='/management/forgot'>Forgot password?</a></p>");
    }
    @GetMapping(value = "/management/setup", produces = MediaType.TEXT_HTML_VALUE)
    String setup(HttpServletRequest request) {
        String status = switch (identity.setupState()) {
            case "unreserved" -> identity.setupCredentialConfigured()
                    ? "Enter the operator setup credential and first administrator email."
                    : "Management setup is locked: configure a valid LOOMSPAN_SIDECAR_SETUP_TOKEN in the environment.";
            case "reserved" -> "Initial administrator email is already reserved. Only that address can retry until activation.";
            default -> "Management setup is complete.";
        };
        return page("First administrator", "<p>" + status + "</p><form method='post' action='/management/setup'>"
                + csrf(request) + "<label>Setup credential <input name='credential' type='password' required></label>"
                + "<label>Email <input name='email' type='email' required></label><button>Send password link</button></form>");
    }
    @PostMapping("/management/setup")
    String setupSubmit(@RequestParam("credential") String credential, @RequestParam("email") String email,
            HttpServletRequest request) {
        try { tokenAttempt(request); identity.setup(credential, email); return status("Check that mailbox for the setup link."); }
        catch (RuntimeException rejected) { return status("Setup is locked or email delivery is unavailable; check configuration and retry."); }
    }
    @GetMapping(value = "/management/forgot", produces = MediaType.TEXT_HTML_VALUE)
    String forgot(HttpServletRequest request) {
        return page("Forgot password", "<form method='post' action='/management/forgot'>" + csrf(request)
                + "<label>Email <input name='email' type='email' autocomplete='username' required></label>"
                + "<button>Request link</button></form>");
    }
    @PostMapping("/management/forgot")
    String forgotSubmit(@RequestParam("email") String email, HttpServletRequest request) {
        String address;
        try { address = ManagementPolicy.email(email); } catch (IllegalArgumentException invalid) { address = "invalid"; }
        if (attempts.record("forgot:email:" + address, 3, Duration.ofHours(1))
                && attempts.record("forgot:ip:" + request.getRemoteAddr(), 20, Duration.ofHours(1))) identity.forgot(email);
        return status("If the address has an active account, a link will be sent.");
    }
    @GetMapping(value = {"/management/password/set", "/management/password/reset"}, produces = MediaType.TEXT_HTML_VALUE)
    String passwordPage(@RequestParam(name = "token", defaultValue = "") String token, HttpServletRequest request) {
        String purpose = request.getRequestURI().endsWith("/set") ? "set" : "reset";
        return page("Password " + purpose, "<form method='post' action='/management/password/" + purpose + "'>"
                + csrf(request) + "<input type='hidden' name='token' value='" + escape(token) + "'>"
                + "<label>New password <input name='password' type='password' autocomplete='new-password' required></label>"
                + "<button>Save password</button></form><p>Use 15–128 characters with uppercase, lowercase, a number and a symbol.</p>");
    }
    @PostMapping({"/management/password/set", "/management/password/reset"})
    String passwordSubmit(@RequestParam("token") String token, @RequestParam("password") String password,
            HttpServletRequest request) {
        try {
            tokenAttempt(request);
            identity.redeem(request.getRequestURI().endsWith("/set") ? "set" : "reset", token, password);
            return status("Password saved. Sign in normally.");
        } catch (RuntimeException rejected) { return status("Link or password invalid. Request a new link and retry."); }
    }
    @GetMapping(value = "/management/home", produces = MediaType.TEXT_HTML_VALUE)
    String home(Authentication auth, HttpServletRequest request) {
        var user = ManagementController.principal(auth);
        return page("Management", "<p>Signed in as " + escape(user.email()) + " (" + escape(user.role()) + ").</p>"
                + "<form method='post' action='/management/logout'>" + csrf(request) + "<button>Sign out</button></form>"
                + "<p><a href='/management/password/change'>Change password</a></p>");
    }
    @GetMapping(value = "/management/password/change", produces = MediaType.TEXT_HTML_VALUE)
    String change(HttpServletRequest request) {
        return page("Change password", "<form method='post' action='/management/password/change'>" + csrf(request)
                + "<label>Current password <input name='current' type='password' autocomplete='current-password' required></label>"
                + "<label>New password <input name='replacement' type='password' autocomplete='new-password' required></label>"
                + "<button>Change password</button></form>");
    }
    @PostMapping("/management/password/change")
    String changeSubmit(@RequestParam("current") String current, @RequestParam("replacement") String replacement,
            Authentication auth, HttpServletRequest request) {
        try {
            identity.change(ManagementController.principal(auth).id(), current, replacement);
            request.getSession(false).invalidate();
            return status("Password changed. Sign in normally.");
        } catch (RuntimeException rejected) { return status("Password could not be changed."); }
    }
    private void tokenAttempt(HttpServletRequest request) {
        if (!attempts.record("token:ip:" + request.getRemoteAddr(), 10, Duration.ofMinutes(15)))
            throw new ManagementIdentityService.Rejected();
    }
    private static String csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return "<input type='hidden' name='" + escape(token.getParameterName()) + "' value='" + escape(token.getToken()) + "'>";
    }
    private static String status(String message) { return page("Status", "<p>" + escape(message) + "</p><p><a href='/management/login'>Sign in</a></p>"); }
    private static String page(String title, String body) {
        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='referrer' content='no-referrer'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>" + escape(title)
                + "</title></head><body><main><h1>" + escape(title) + "</h1>" + body + "</main></body></html>";
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
