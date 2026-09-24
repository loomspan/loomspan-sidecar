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
                    ? "<p>First administrator setup is pending for a reserved address. Complete it at <a href='/management/setup'>setup</a>.</p>"
                    : "<p>First administrator address is reserved, but setup is locked until an operator configures LOOMSPAN_SIDECAR_SETUP_TOKEN.</p>";
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
                    + "</p>");
        String status = "reserved".equals(state)
                ? "The initial administrator address is reserved. Only that address can complete setup."
                : "Enter the one-time operator setup credential, administrator email, and chosen login password.";
        return page("First administrator", "<p>" + status + "</p><form method='post' action='/management/setup'>"
                + csrf(request) + "<label>Setup credential <input name='credential' type='password' required></label>"
                + "<label>Email <input name='email' type='email' required></label>"
                + "<label>Password <input name='password' type='password' autocomplete='new-password' required></label>"
                + "<label>Confirm password <input name='confirmation' type='password' autocomplete='new-password' required></label>"
                + "<button>Create administrator</button></form>" + passwordGuidance());
    }
    @PostMapping("/management/setup")
    String setupSubmit(@RequestParam("credential") String credential, @RequestParam("email") String email,
            @RequestParam("password") String password, @RequestParam("confirmation") String confirmation,
            HttpServletRequest request) {
        try { tokenAttempt(request); identity.setup(credential, email, password, confirmation); return status("Administrator created. Sign in with the email and chosen password."); }
        catch (RuntimeException rejected) { return status("Setup could not be completed. Check the setup credential, reserved address, and password policy, then retry."); }
    }
    @GetMapping(value = "/management/forgot", produces = MediaType.TEXT_HTML_VALUE)
    String forgot(HttpServletRequest request) {
        return page("Forgot password", "<p>Enter your account email. If it has an active account, a reset link will be sent when email delivery is available. Without SMTP, ask an operator to issue a local reset credential and enter it on the <a href='/management/password/reset'>reset page</a>.</p><form method='post' action='/management/forgot'>" + csrf(request)
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
        return page("Password " + purpose, "<p>Credentials expire and can be used once. If this attempt fails, request a fresh credential through <a href='/management/forgot'>password recovery</a> or ask your administrator to resend an invitation.</p>"
                + "<form method='post' action='/management/password/" + purpose + "'>"
                + csrf(request) + ("reset".equals(purpose) && token.isEmpty()
                    ? "<label>Reset credential <input type='password' name='token' autocomplete='off' required></label>"
                    : "<input type='hidden' name='token' value='" + escape(token) + "'>")
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
                + "<form id='invite-form'><h2>Invite account</h2><p>Account email cannot be changed. The password setup link is emailed."
                + (identity.mailAvailable() ? "" : " Email delivery is not configured, so invitations and resend cannot be completed here.") + "</p>"
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
                + "<p>Downloaded bundles contain exact authored values, including sensitive secrets and placeholders. Store them securely.</p>"
                + "<button id='current-export' type='button'>Download current configuration</button>"
                + "<button id='current-refresh' type='button'>Refresh configuration</button><div id='current-content'></div></section>");
    }
    @GetMapping(value = "/management/configuration/history", produces = MediaType.TEXT_HTML_VALUE)
    String history(Authentication auth, HttpServletRequest request) {
        var user = ManagementController.principal(auth);
        String rollback = "viewer".equals(user.role()) ? "" :
                "<div id='history-rollback'><button id='rollback-review' type='button' disabled>Review rollback</button>"
                + "<p id='rollback-status' role='status' aria-live='polite' tabindex='-1'>Select a retained snapshot to review.</p>"
                + "<div id='rollback-summary' hidden><h3>Rollback review</h3><dl id='rollback-details'></dl>"
                + "<h4>Destination validation</h4><ul id='rollback-issues'></ul>"
                + "<p class='warning'>Confirming discards every private draft and breaks the editing lease, including another editor’s lease. The retained source status does not establish destination validity.</p>"
                + "<button id='rollback-confirm' type='button' disabled>Confirm and publish rollback</button></div></div>";
        return console("Configuration history", user, request,
                "<section data-console='history'><p>Retained submissions are shared with every management role and may contain authored secrets. Private unsubmitted drafts are not history.</p>"
                + "<p id='history-status' role='status' aria-live='polite' tabindex='-1'>Loading retained submissions…</p>"
                + "<div class='history-actions'><button id='history-refresh' type='button'>Refresh retained history</button>"
                + "<a href='/management/configuration/current'>View current configuration</a>"
                + "<a href='/management/configuration/recovery'>Operator recovery guidance</a></div>"
                + "<div id='history-current' class='history-current'></div>"
                + "<div class='history-layout'><section aria-labelledby='history-list-heading'><h2 id='history-list-heading'>Retained submissions</h2>"
                + "<div id='history-list'></div></section><section aria-labelledby='history-detail-heading'><h2 id='history-detail-heading'>Snapshot detail</h2>"
                + "<div id='history-detail'><p>Select a retained submission to load its exact content.</p></div>"
                + rollback + "</section></div></section>");
    }
    @GetMapping(value = "/management/configuration/recovery", produces = MediaType.TEXT_HTML_VALUE)
    String recovery(Authentication auth, HttpServletRequest request) {
        return console("Configuration recovery guidance", ManagementController.principal(auth), request,
                "<section data-console='recovery'><p>This page is read-only. It does not retry, cancel, roll back, import, export, or repair a publication. A disconnected import or rollback may already have completed: inspect current runtime and retained history before another attempt.</p>"
                + "<h2>When a mutation fault is present</h2><p>Configuration acquisition, save, activity renewal, validation, and publication are blocked. Configuration inspection, release or discard of private editing state, and administrator account management remain available.</p>"
                + "<h2>Offline operator recovery</h2><ol><li>Stop the sole Sidecar instance before changing storage.</li>"
                + "<li>Preserve a consistent stopped copy of the complete SQLite database set, including the main file and any -wal/-shm companions. Protect it: it contains management accounts and sensitive authored values.</li>"
                + "<li>If the intended selection is correct, repair the storage problem and restart so the intended snapshot is loaded.</li>"
                + "<li>If runtime and intended snapshots differ and the prior runtime must be recovered, restore a known-good full database backup made while Sidecar was stopped. Remove stale destination companions, set writable ownership for UID/GID 10001:10001, then restart.</li></ol>"
                + "<p>Startup activates the committed pointer from the restored backup. Verify readiness, sign in with a restored account, and inspect current configuration and history. ZIP import and local retained-history rollback cannot bypass a mutation fault or restore accounts.</p>"
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
    @GetMapping(value = "/management/configuration/import", produces = MediaType.TEXT_HTML_VALUE)
    String importPage(Authentication auth, HttpServletRequest request) {
        return console("Import configuration", ManagementController.principal(auth), request,
                "<section data-console='import'><p>Choose a format-1 current-configuration bundle exported from this or another Sidecar.</p>"
                + "<label>Configuration bundle <input id='import-file' type='file' accept='.zip,application/zip'></label>"
                + "<button id='import-review' type='button'>Review on this destination</button>"
                + "<p id='import-status' role='status' aria-live='polite'>Choose a bundle to review.</p>"
                + "<div id='import-summary' hidden><h2>Review</h2><dl id='import-details'></dl>"
                + "<h3>Validation feedback</h3><ul id='import-issues'></ul>"
                + "<p id='import-warning' class='warning'>Confirming discards every private draft and breaks the editing lease, including another editor’s lease. The imported content completely replaces the running configuration.</p>"
                + "<button id='import-confirm' type='button' disabled>Confirm and publish import</button></div>"
                + "<div id='import-outcome' role='status' aria-live='polite'></div>"
                + "<p><a href='/management/configuration/current'>Current runtime</a> · <a href='/management/configuration/history'>History</a> · <a href='/management/configuration/recovery'>Recovery guidance</a></p></section>"
                + "<script src='/management/assets/import.js' defer></script>");
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
                + ("viewer".equals(user.role()) ? "" : " <a href='/management/configuration/edit'>Edit configuration</a>"
                        + " <a href='/management/configuration/import'>Import configuration</a>")
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
