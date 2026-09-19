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
        String setup = switch (identity.setupState()) {
            case "unreserved" -> identity.setupCredentialConfigured()
                    ? "<p>First administrator setup is available. <a href='/management/setup'>Start setup</a>.</p>"
                    : "<p>Management setup is locked. An operator must configure LOOMSPAN_SIDECAR_SETUP_TOKEN.</p>";
            case "reserved" -> identity.setupCredentialConfigured()
                    ? "<p>First administrator setup is awaiting an emailed password link. The reserved address may retry delivery from <a href='/management/setup'>setup</a>.</p>"
                    : "<p>First administrator address is reserved, but setup is locked until an operator configures LOOMSPAN_SIDECAR_SETUP_TOKEN. Existing emailed links can still be completed.</p>";
            default -> "";
        };
        return page("Sign in", setup + feedback + "<form method='post' action='/management/login'>"
                + csrf(request) + "<label>Email <input name='email' type='email' autocomplete='username' required></label>"
                + "<label>Password <input name='password' type='password' autocomplete='current-password' required></label>"
                + "<button>Sign in</button></form><p><a href='/management/forgot'>Forgot password?</a></p>");
    }
    @GetMapping(value = "/management/setup", produces = MediaType.TEXT_HTML_VALUE)
    String setup(HttpServletRequest request) {
        String state = identity.setupState();
        if (!"unreserved".equals(state) && !"reserved".equals(state))
            return page("First administrator", "<p>Management setup is complete. <a href='/management/login'>Sign in</a>.</p>");
        if (!identity.setupCredentialConfigured())
            return page("First administrator", "<p>Management setup is locked. An operator must configure a valid LOOMSPAN_SIDECAR_SETUP_TOKEN in the environment."
                    + ("reserved".equals(state) ? " The first administrator address is reserved; an existing emailed link can still be completed." : "") + "</p>");
        String status = "reserved".equals(state)
                ? "The initial administrator address is reserved. Only that address can retry email delivery until activation."
                : "Enter the one-time operator setup credential and first administrator email. The password setup link will be emailed; sign-in is available after that link is completed.";
        return page("First administrator", "<p>" + status + "</p><form method='post' action='/management/setup'>"
                + csrf(request) + "<label>Setup credential <input name='credential' type='password' required></label>"
                + "<label>Email <input name='email' type='email' required></label><button>Send password link</button></form>");
    }
    @PostMapping("/management/setup")
    String setupSubmit(@RequestParam("credential") String credential, @RequestParam("email") String email,
            HttpServletRequest request) {
        try { tokenAttempt(request); identity.setup(credential, email); return status("Check that mailbox for the setup link. Complete it before signing in."); }
        catch (RuntimeException rejected) { return status("Setup is locked or email delivery is unavailable; check configuration and retry setup with the reserved address. An account may be awaiting its email link."); }
    }
    @GetMapping(value = "/management/forgot", produces = MediaType.TEXT_HTML_VALUE)
    String forgot(HttpServletRequest request) {
        return page("Forgot password", "<p>Enter your account email. If it has an active account, a reset link will be sent when email delivery is available. You can safely retry later.</p><form method='post' action='/management/forgot'>" + csrf(request)
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
        return page("Password " + purpose, "<p>Links expire and can be used once. If this link fails, request a fresh link through <a href='/management/forgot'>password recovery</a> or ask your administrator to resend an invitation.</p>"
                + "<form method='post' action='/management/password/" + purpose + "'>"
                + csrf(request) + "<input type='hidden' name='token' value='" + escape(token) + "'>"
                + "<label>New password <input name='password' type='password' autocomplete='new-password' required></label>"
                + "<button>Save password</button></form>" + passwordGuidance());
    }
    @PostMapping({"/management/password/set", "/management/password/reset"})
    String passwordSubmit(@RequestParam("token") String token, @RequestParam("password") String password,
            HttpServletRequest request) {
        try {
            tokenAttempt(request);
            identity.redeem(request.getRequestURI().endsWith("/set") ? "set" : "reset", token, password);
            return status("Password saved. Sign in normally.");
        } catch (RuntimeException rejected) { return status("Link or password invalid or already used. Request a fresh link and retry with a password that meets the policy."); }
    }
    @GetMapping(value = "/management/home", produces = MediaType.TEXT_HTML_VALUE)
    String home(Authentication auth, HttpServletRequest request) {
        var user = ManagementController.principal(auth);
        return console("Management", user, request,
                "<p>View the configuration currently running in this Sidecar or inspect retained submitted history. Account actions and configuration access require a management login.</p>");
    }
    @GetMapping(value = "/management/accounts", produces = MediaType.TEXT_HTML_VALUE)
    String accounts(Authentication auth, HttpServletRequest request) {
        return console("Accounts", ManagementController.principal(auth), request,
                "<section data-console='accounts'><p id='accounts-status' role='status' aria-live='polite'>Loading accounts…</p>"
                + "<form id='invite-form'><h2>Invite account</h2><p>Account email cannot be changed. The password setup link is emailed.</p>"
                + "<label>Email <input name='email' type='email' autocomplete='off' required></label>"
                + "<label>Role <select name='role'><option value='viewer'>Viewer</option><option value='editor'>Editor</option><option value='admin'>Administrator</option></select></label>"
                + "<button>Send invitation</button></form><button id='accounts-refresh' type='button'>Refresh accounts</button>"
                + "<div id='accounts-list'></div></section>");
    }
    @GetMapping(value = "/management/configuration/current", produces = MediaType.TEXT_HTML_VALUE)
    String current(Authentication auth, HttpServletRequest request) {
        return console("Current configuration", ManagementController.principal(auth), request,
                "<section data-console='current'><p id='current-status' role='status' aria-live='polite'>Loading runtime snapshot…</p>"
                + "<p><a href='/management/configuration/history'>Inspect retained history</a></p>"
                + "<button id='current-refresh' type='button'>Refresh configuration</button><div id='current-content'></div></section>");
    }
    @GetMapping(value = "/management/configuration/history", produces = MediaType.TEXT_HTML_VALUE)
    String history(Authentication auth, HttpServletRequest request) {
        return console("Configuration history", ManagementController.principal(auth), request,
                "<section data-console='history'><p>Retained submissions are shared with every management role and may contain authored secrets. Private unsubmitted drafts are not history.</p>"
                + "<p id='history-status' role='status' aria-live='polite' tabindex='-1'>Loading retained submissions…</p>"
                + "<div class='history-actions'><button id='history-refresh' type='button'>Refresh retained history</button>"
                + "<a href='/management/configuration/current'>View current configuration</a>"
                + "<a href='/management/configuration/recovery'>Operator recovery guidance</a></div>"
                + "<div id='history-current' class='history-current'></div>"
                + "<div class='history-layout'><section aria-labelledby='history-list-heading'><h2 id='history-list-heading'>Retained submissions</h2>"
                + "<div id='history-list'></div></section><section aria-labelledby='history-detail-heading'><h2 id='history-detail-heading'>Snapshot detail</h2>"
                + "<div id='history-detail'><p>Select a retained submission to load its exact content.</p></div></section></div></section>");
    }
    @GetMapping(value = "/management/configuration/recovery", produces = MediaType.TEXT_HTML_VALUE)
    String recovery(Authentication auth, HttpServletRequest request) {
        return console("Configuration recovery guidance", ManagementController.principal(auth), request,
                "<section data-console='recovery'><p>This page is read-only. It does not retry, cancel, roll back, import, export, or repair a publication.</p>"
                + "<h2>When a mutation fault is present</h2><p>Configuration acquisition, save, activity renewal, validation, and publication are blocked. Configuration inspection, release or discard of private editing state, and administrator account management remain available.</p>"
                + "<h2>Offline operator recovery</h2><ol><li>Stop the sole Sidecar instance before changing storage.</li>"
                + "<li>Preserve a consistent copy of the complete SQLite database set, including its journal or WAL files.</li>"
                + "<li>If the intended selection is correct, repair the storage problem and restart so the intended snapshot is loaded.</li>"
                + "<li>If runtime and intended snapshots differ and the prior runtime must be recovered, restore a known-good full database backup made while Sidecar was stopped, then restart.</li></ol>"
                + "<p>Never treat the intended pointer as proof of what is currently executing. Review <a href='/management/configuration/current'>current runtime state</a> and <a href='/management/configuration/history'>retained history</a> before intervention.</p></section>");
    }
    @GetMapping(value = "/management/configuration/edit", produces = MediaType.TEXT_HTML_VALUE)
    String edit(Authentication auth, HttpServletRequest request) {
        return console("Edit configuration", ManagementController.principal(auth), request,
                "<section data-console='editor'><p id='editor-message' role='status' aria-live='polite'>Loading configuration…</p>"
                + "<p id='editor-owner'></p><p id='editor-deadline'></p>"
                + "<div class='editor-actions'><button id='editor-acquire' type='button'>Start editing or resume draft</button>"
                + "<button id='editor-takeover' type='button' hidden>Take over editing</button>"
                + "<button id='editor-release' type='button' hidden>Release editing</button>"
                + "<button id='editor-discard' type='button' hidden>Discard my draft</button>"
                + "<button id='editor-continue' type='button' hidden>Continue editing</button></div>"
                + "<div id='editor-fields' hidden><h2>Skill YAML documents</h2><p>Each source name is a diagnostic label. Edit complete YAML documents.</p>"
                + "<div id='editor-skills'></div><button id='editor-add' type='button'>Add skill document</button>"
                + "<label for='editor-rest'>REST targets and routes YAML</label><textarea id='editor-rest' spellcheck='false'></textarea></div>"
                + "<div class='editor-status'><span id='editor-save' role='status' aria-live='polite'>Not editing</span>"
                + "<button id='editor-retry' type='button' hidden>Retry save</button></div>"
                + "<div class='editor-validation'><span id='editor-validation-state' role='status' aria-live='polite'>Out of date</span>"
                + "<button id='editor-recheck' type='button' hidden>Retry validation</button>"
                + "<details id='editor-details'><summary>Validation details</summary><ul id='editor-issues'></ul></details></div>"
                + "<button id='editor-publish' type='button' disabled>Publish validated draft</button>"
                + "<div id='editor-outcome' role='status' aria-live='polite'></div></section>"
                + "<script src='/management/assets/editor.js' defer></script>");
    }
    @GetMapping(value = "/management/password/change", produces = MediaType.TEXT_HTML_VALUE)
    String change(Authentication auth, HttpServletRequest request) {
        return console("Change password", ManagementController.principal(auth), request,
                "<form method='post' action='/management/password/change'>" + csrf(request)
                + "<label>Current password <input name='current' type='password' autocomplete='current-password' required></label>"
                + "<label>New password <input name='replacement' type='password' autocomplete='new-password' required></label>"
                + "<button>Change password</button></form>" + passwordGuidance());
    }
    @PostMapping("/management/password/change")
    String changeSubmit(@RequestParam("current") String current, @RequestParam("replacement") String replacement,
            Authentication auth, HttpServletRequest request) {
        try {
            identity.change(ManagementController.principal(auth).id(), current, replacement);
            request.getSession(false).invalidate();
            return status("Password changed. Sign in normally.");
        } catch (RuntimeException rejected) { return status("Password could not be changed. Check the current password and policy, then retry."); }
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
    private static String passwordGuidance() {
        return "<p>Use 15–128 Unicode characters (code points), no more than 512 UTF-8 bytes, with uppercase and lowercase letters, a number, and a non-whitespace punctuation mark or symbol. Spaces are allowed. You can paste or use a password manager.</p>";
    }
    private static String console(String title, ManagementUserDetailsService.Principal user, HttpServletRequest request, String body) {
        String nav = "<nav aria-label='Management'><a href='/management/home'>Home</a> <a href='/management/configuration/current'>Current configuration</a>"
                + " <a href='/management/configuration/history'>History</a>"
                + " <a href='/management/configuration/edit'>Edit configuration</a>"
                + ("admin".equals(user.role()) ? " <a href='/management/accounts'>Accounts</a>" : "")
                + " <a href='/management/password/change'>Change password</a></nav>";
        return page(title, nav + "<p>Signed in as " + escape(user.email()) + " (" + escape(user.role()) + ").</p>"
                + "<form method='post' action='/management/logout'>" + csrf(request) + "<button>Sign out</button></form>"
                + body + "<script src='/management/assets/console.js' defer></script>");
    }
    private static String page(String title, String body) {
        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='referrer' content='no-referrer'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><title>" + escape(title)
                + "</title><link rel='stylesheet' href='/management/assets/console.css'></head><body><main><h1>" + escape(title) + "</h1>" + body + "</main></body></html>";
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
