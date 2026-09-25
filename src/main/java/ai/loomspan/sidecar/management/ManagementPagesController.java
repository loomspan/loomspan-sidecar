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
public class ManagementPagesController
{
    private static final String ICON_HOME = "<path d='M3 10.5 12 3l9 7.5V21h-6v-6H9v6H3z'/>";
    private static final String ICON_CURRENT = "<path d='M12 3 2 8l10 5 10-5z'/><path d='m2 13 10 5 10-5'/>";
    private static final String ICON_HISTORY = "<circle cx='12' cy='12' r='9'/><path d='M12 7v5l3 2'/>";
    private static final String ICON_EDIT = "<path d='M4 20h4L19 9l-4-4L4 16z'/><path d='m13.5 6.5 4 4'/>";
    private static final String ICON_IMPORT = "<path d='M12 3v12'/><path d='m7 10 5 5 5-5'/><path d='M4 20h16'/>";
    private static final String ICON_RECOVERY = "<path d='M12 3 4 6v6c0 4.5 3.4 8.1 8 9 4.6-.9 8-4.5 8-9V6z'/><path d='m9 12 2 2 4-4'/>";
    private static final String ICON_ACCOUNTS = "<circle cx='9' cy='8' r='3.5'/><path d='M2.5 20c.8-3.6 3.4-5.5 6.5-5.5s5.7 1.9 6.5 5.5'/>"
            + "<path d='M16 4.8a3.5 3.5 0 0 1 0 6.4M18 14.8c1.8.8 3 2.6 3.5 5.2'/>";
    private static final String ICON_KEY = "<circle cx='8' cy='15' r='4'/><path d='m11 12 9-9M17 6l3 3'/>";
    private static final String ICON_SIGN_OUT = "<path d='M9 4H5v16h4'/><path d='m15 8 4 4-4 4M19 12H9'/>";

    private final ManagementIdentityService identity;
    private final ManagementAttemptLimiter attempts;

    public ManagementPagesController(ManagementIdentityService identity, ManagementAttemptLimiter attempts)
    {
        this.identity = identity;
        this.attempts = attempts;
    }

    @GetMapping(value = "/management/login", produces = MediaType.TEXT_HTML_VALUE)
    String login(HttpServletRequest request)
    {
        String feedback = request.getParameter("error") != null ? notice("error", "Sign-in failed. Check the address and password, then retry.")
                : request.getParameter("logout") != null ? notice("success", "Signed out.") : "";
        String setup = switch (identity.setupState())
        {
            case "unreserved" -> identity.setupCredentialConfigured()
                    ? "<p class='notice'>First administrator setup is available. <a href='/management/setup'>Start setup</a>.</p>"
                    : "<p class='notice warning'>Management setup is locked. An operator must configure LOOMSPAN_SIDECAR_SETUP_TOKEN.</p>";
            case "reserved" -> identity.setupCredentialConfigured()
                    ? "<p class='notice'>First administrator setup is pending for a reserved address. Complete it at <a href='/management/setup'>setup</a>.</p>"
                    : "<p class='notice warning'>First administrator address is reserved, but setup is locked until an operator configures LOOMSPAN_SIDECAR_SETUP_TOKEN.</p>";
            default -> "";
        };
        return page("Sign in", "Use your management account to open the Sidecar console.", setup + feedback
                + "<form class='stack' method='post' action='/management/login'>"
                + csrf(request) + field("Email", "<input name='email' type='email' autocomplete='username' required>")
                + field("Password", "<input name='password' type='password' autocomplete='current-password' required>")
                + "<button class='btn-primary btn-block'>Sign in</button></form>"
                + "<p class='auth-links'><a href='/management/forgot'>Forgot password?</a></p>");
    }

    @GetMapping(value = "/management/setup", produces = MediaType.TEXT_HTML_VALUE)
    String setup(HttpServletRequest request)
    {
        String state = identity.setupState();
        if (!"unreserved".equals(state) && !"reserved".equals(state))
            return page("First administrator", null, "<p class='notice success'>Management setup is complete. <a href='/management/login'>Sign in</a>.</p>");
        if (!identity.setupCredentialConfigured())
            return page("First administrator", null, "<p class='notice warning'>Management setup is locked. An operator must configure a valid LOOMSPAN_SIDECAR_SETUP_TOKEN in the environment."
                    + "</p>");
        String status = "reserved".equals(state)
                ? "The initial administrator address is reserved. Only that address can complete setup."
                : "Enter the one-time operator setup credential, administrator email, and chosen login password.";
        return page("First administrator", status, "<form class='stack' method='post' action='/management/setup'>"
                + csrf(request) + field("Setup credential", "<input name='credential' type='password' required>")
                + field("Email", "<input name='email' type='email' required>")
                + field("Password", "<input name='password' type='password' autocomplete='new-password' required>")
                + field("Confirm password", "<input name='confirmation' type='password' autocomplete='new-password' required>")
                + passwordGuidance() + "<button class='btn-primary btn-block'>Create administrator</button></form>");
    }

    @PostMapping("/management/setup")
    String setupSubmit(@RequestParam("credential") String credential, @RequestParam("email") String email,
            @RequestParam("password") String password, @RequestParam("confirmation") String confirmation,
            HttpServletRequest request)
    {
        try
        {
            tokenAttempt(request);
            identity.setup(credential, email, password, confirmation);
            return status("Administrator created. Sign in with the email and chosen password.", false);
        }
        catch (RuntimeException rejected)
        {
            return status("Setup could not be completed. Check the setup credential, reserved address, and password policy, then retry.", true);
        }
    }

    @GetMapping(value = "/management/forgot", produces = MediaType.TEXT_HTML_VALUE)
    String forgot(HttpServletRequest request)
    {
        return page("Forgot password", "Enter your account email. If it has an active account, a reset link will be sent when email delivery is available.",
                "<form class='stack' method='post' action='/management/forgot'>" + csrf(request)
                        + field("Email", "<input name='email' type='email' autocomplete='username' required>")
                        + "<button class='btn-primary btn-block'>Request link</button></form>"
                        + "<p class='hint'>Without SMTP, ask an operator to issue a local reset credential and enter it on the <a href='/management/password/reset'>reset page</a>.</p>"
                        + "<p class='auth-links'><a href='/management/login'>Back to sign in</a></p>");
    }

    @PostMapping("/management/forgot")
    String forgotSubmit(@RequestParam("email") String email, HttpServletRequest request)
    {
        String address;
        try
        {
            address = ManagementPolicy.email(email);
        }
        catch (IllegalArgumentException invalid)
        {
            address = "invalid";
        }
        if (attempts.record("forgot:email:" + address, 3, Duration.ofHours(1))
                && attempts.record("forgot:ip:" + request.getRemoteAddr(), 20, Duration.ofHours(1)))
            identity.forgot(email);
        return status("If the address has an active account, a link will be sent.", false);
    }

    @GetMapping(value = {"/management/password/set", "/management/password/reset"}, produces = MediaType.TEXT_HTML_VALUE)
    String passwordPage(@RequestParam(name = "token", defaultValue = "") String token, HttpServletRequest request)
    {
        String purpose = request.getRequestURI().endsWith("/set") ? "set" : "reset";
        return page("set".equals(purpose) ? "Set your password" : "Reset your password",
                "Credentials expire and can be used once.",
                "<form class='stack' method='post' action='/management/password/" + purpose + "'>"
                        + csrf(request) + ("reset".equals(purpose) && token.isEmpty()
                                ? field("Reset credential", "<input type='password' name='token' autocomplete='off' required>")
                                : "<input type='hidden' name='token' value='" + escape(token) + "'>")
                        + field("New password", "<input name='password' type='password' autocomplete='new-password' required>")
                        + passwordGuidance() + "<button class='btn-primary btn-block'>Save password</button></form>"
                        + "<p class='hint'>If this attempt fails, request a fresh credential through <a href='/management/forgot'>password recovery</a> or ask your administrator to resend an invitation.</p>");
    }

    @PostMapping({"/management/password/set", "/management/password/reset"})
    String passwordSubmit(@RequestParam("token") String token, @RequestParam("password") String password,
            HttpServletRequest request)
    {
        try
        {
            tokenAttempt(request);
            identity.redeem(request.getRequestURI().endsWith("/set") ? "set" : "reset", token, password);
            return status("Password saved. Sign in normally.", false);
        }
        catch (RuntimeException rejected)
        {
            return status("Link or password invalid or already used. Request a fresh link and retry with a password that meets the policy.", true);
        }
    }

    @GetMapping(value = "/management/home", produces = MediaType.TEXT_HTML_VALUE)
    String home(Authentication auth, HttpServletRequest request)
    {
        var user = ManagementController.principal(auth);
        boolean editor = !"viewer".equals(user.role());
        String actions = action("/management/configuration/current", ICON_CURRENT, "Inspect runtime",
                "Review the skills and REST routes this Sidecar is executing now.")
                + action("/management/configuration/history", ICON_HISTORY, "Browse history",
                        "Compare retained submissions" + (editor ? " and roll back to a known-good snapshot." : "."))
                + (editor ? action("/management/configuration/edit", ICON_EDIT, "Edit configuration",
                        "Draft, validate, and publish skill and route changes.")
                        + action("/management/configuration/import", ICON_IMPORT, "Import a bundle",
                                "Promote a configuration exported from another Sidecar.")
                        : "")
                + ("admin".equals(user.role()) ? action("/management/accounts", ICON_ACCOUNTS, "Manage accounts",
                        "Invite people and control their access.") : "")
                + action("/management/configuration/recovery", ICON_RECOVERY, "Recovery guide",
                        "What to do when configuration changes are blocked.");
        return console("Overview", "Runtime health and configuration at a glance.", user, request, "",
                "<section data-console='home'>"
                        + "<p id='home-status' class='notice' role='status' aria-live='polite'>Loading runtime overview…</p>"
                        + "<div class='stat-grid'>"
                        + stat("home-runtime", "Runtime snapshot") + stat("home-restart", "Restart target")
                        + stat("home-skills", "Skill documents") + stat("home-history", "Retained history")
                        + "</div><div id='home-alerts'></div>"
                        + "<h2 class='section-title'>Quick actions</h2><div class='action-grid'>" + actions + "</div></section>");
    }

    @GetMapping(value = "/management/accounts", produces = MediaType.TEXT_HTML_VALUE)
    String accounts(Authentication auth, HttpServletRequest request)
    {
        return console("Accounts", "Invite people to the console and control what they can change.", ManagementController.principal(auth), request,
                "<button id='accounts-refresh' type='button'>Refresh accounts</button>",
                "<section data-console='accounts'><p id='accounts-status' class='notice' role='status' aria-live='polite'>Loading accounts…</p>"
                        + "<div class='split'><div class='card'><div class='card-head'><h2>Team members</h2></div><div id='accounts-list' class='account-list'></div></div>"
                        + "<form id='invite-form' class='card stack'><h2>Invite account</h2><p class='muted'>Account email cannot be changed. The password setup link is emailed."
                        + (identity.mailAvailable() ? "" : " Email delivery is not configured, so invitations and resend cannot be completed here.") + "</p>"
                        + field("Email", "<input name='email' type='email' autocomplete='off' required>")
                        + field("Role", "<select name='role'><option value='viewer'>Viewer</option><option value='editor'>Editor</option><option value='admin'>Administrator</option></select>")
                        + "<dl class='role-guide'><dt>Viewer</dt><dd>Inspect runtime and history.</dd><dt>Editor</dt><dd>Also edit, import, and roll back.</dd>"
                        + "<dt>Administrator</dt><dd>Also manage accounts and take over editing.</dd></dl>"
                        + "<button class='btn-primary'>Send invitation</button></form></div></section>");
    }

    @GetMapping(value = "/management/configuration/current", produces = MediaType.TEXT_HTML_VALUE)
    String current(Authentication auth, HttpServletRequest request)
    {
        return console("Current configuration", "The skills and REST routes this Sidecar is running right now.", ManagementController.principal(auth), request,
                "<button id='current-refresh' type='button'>Refresh configuration</button>"
                        + "<button id='current-export' class='btn-primary' type='button'>Download current configuration</button>",
                "<section data-console='current'><p id='current-status' class='notice' role='status' aria-live='polite'>Loading runtime snapshot…</p>"
                        + "<p class='notice warning'>Downloaded bundles contain exact authored values, including sensitive secrets and placeholders. Store them securely.</p>"
                        + "<div id='current-content' class='stack-lg'></div></section>");
    }

    @GetMapping(value = "/management/configuration/history", produces = MediaType.TEXT_HTML_VALUE)
    String history(Authentication auth, HttpServletRequest request)
    {
        var user = ManagementController.principal(auth);
        String rollback = "viewer".equals(user.role()) ? ""
                : "<section id='history-rollback' class='card rollback' aria-labelledby='rollback-heading'><div class='card-head'><h3 id='rollback-heading'>Roll back to this snapshot</h3>"
                        + "<button id='rollback-review' type='button' disabled>Review rollback</button></div>"
                        + "<p id='rollback-status' class='notice' role='status' aria-live='polite' tabindex='-1'>Select a retained snapshot to review.</p>"
                        + "<div id='rollback-summary' hidden><h4>Rollback review</h4><dl id='rollback-details' class='facts'></dl>"
                        + "<h4>Destination validation</h4><ul id='rollback-issues' class='issues'></ul>"
                        + "<p class='notice warning'>Loading replaces only your saved draft. Review, validate, then publish it from the editor. The retained source status does not establish destination validity.</p>"
                        + "<button id='rollback-confirm' type='button' disabled>Load into my draft</button></div></section>";
        return console("Configuration history", "Retained submissions, oldest first. Select one to inspect its exact content.", user, request,
                "<button id='history-refresh' type='button'>Refresh retained history</button>",
                "<section data-console='history'>"
                        + "<p id='history-status' class='notice' role='status' aria-live='polite' tabindex='-1'>Loading retained submissions…</p>"
                        + "<div id='history-current' class='history-current'></div>"
                        + "<div class='history-layout'><section class='history-column' aria-labelledby='history-list-heading'><h2 id='history-list-heading' class='section-title'>Retained submissions</h2>"
                        + "<div id='history-list' class='history-list'></div>"
                        + "<p class='hint'>Retained submissions are shared with every management role and may contain authored secrets. Private unsubmitted drafts are not history. "
                        + "See the <a href='/management/configuration/recovery'>recovery guide</a> if changes are blocked.</p></section>"
                        + "<section class='history-column' aria-labelledby='history-detail-heading'><h2 id='history-detail-heading' class='section-title'>Snapshot detail</h2>"
                        + rollback + "<div id='history-detail' class='stack-lg'><p class='empty'>Select a retained submission to load its exact content.</p></div>"
                        + "</section></div></section>");
    }

    @GetMapping(value = "/management/configuration/recovery", produces = MediaType.TEXT_HTML_VALUE)
    String recovery(Authentication auth, HttpServletRequest request)
    {
        return console("Recovery guide", "Offline operator steps for when configuration mutation is blocked.", ManagementController.principal(auth), request, "",
                "<section data-console='recovery' class='prose'><p class='notice'>This page is read-only. It does not retry, cancel, roll back, import, export, or repair a publication. A disconnected import or rollback may already have completed: inspect current runtime and retained history before another attempt.</p>"
                        + "<div class='card'><h2>When a mutation fault is present</h2><p>Configuration acquisition, save, activity renewal, validation, and publication are blocked. Configuration inspection, release or discard of private editing state, and administrator account management remain available.</p></div>"
                        + "<div class='card'><h2>Offline operator recovery</h2><ol class='steps'><li>Stop the sole Sidecar instance before changing storage.</li>"
                        + "<li>Preserve a consistent stopped copy of the complete SQLite database set, including the main file and any -wal/-shm companions. Protect it: it contains management accounts and sensitive authored values.</li>"
                        + "<li>If the intended selection is correct, repair the storage problem and restart so the intended snapshot is loaded.</li>"
                        + "<li>If runtime and intended snapshots differ and the prior runtime must be recovered, restore a known-good full database backup made while Sidecar was stopped. Remove stale destination companions, set writable ownership for UID/GID 10001:10001, then restart.</li></ol>"
                        + "<p>Startup activates the committed pointer from the restored backup. Verify readiness, sign in with a restored account, and inspect current configuration and history. ZIP import and local retained-history rollback cannot bypass a mutation fault or restore accounts.</p></div>"
                        + "<p class='notice warning'>Never treat the intended pointer as proof of what is currently executing. Review <a href='/management/configuration/current'>current runtime state</a> and <a href='/management/configuration/history'>retained history</a> before intervention.</p></section>");
    }

    @GetMapping(value = "/management/configuration/edit", produces = MediaType.TEXT_HTML_VALUE)
    String edit(Authentication auth, HttpServletRequest request)
    {
        return console("Edit configuration", "Change skills and REST routes in a private draft, validate, then publish.", ManagementController.principal(auth), request, "",
                "<section data-console='editor' class='stack-lg'>"
                        + "<div class='card editor-lease'><div class='editor-lease-info'><p id='editor-message' class='notice' role='status' aria-live='polite'>Loading configuration…</p>"
                        + "<p id='editor-owner' class='editor-owner'></p><p id='editor-deadline' class='hint'></p></div>"
                        + "<div class='editor-actions'><button id='editor-acquire' class='btn-primary' type='button'>Start editing or resume draft</button>"
                        + "<button id='editor-handoff' type='button' hidden>Take control of my draft</button>"
                        + "<button id='editor-takeover' type='button' hidden>Administrator takeover</button>"
                        + "<button id='editor-continue' type='button' hidden>Continue editing</button>"
                        + "<button id='editor-release' type='button' hidden>Release editing</button>"
                        + "<button id='editor-resume-local' type='button' hidden>Resume editing unsaved local text</button>"
                        + "<button id='editor-discard' class='btn-ghost-danger' type='button' hidden>Discard my draft</button></div></div>"
                        + "<div class='card'><p>Your saved draft is private and durable. Published configuration is shown separately below. A stale draft requires a complete current-base submission and fresh validation.</p>"
                        + "<button id='editor-reconcile' type='button' hidden>Submit complete draft against current base</button></div>"
                        + "<details id='editor-saved-preview' class='card' hidden><summary>Server-saved draft content</summary>"
                        + "<pre id='editor-saved-content' class='code'></pre></details>"
                        + "<div id='editor-fields' class='stack-lg' hidden><div><div class='section-head'><div><h2 class='section-title'>Skill YAML documents</h2>"
                        + "<p class='hint'>Each source name is a diagnostic label. Edit complete YAML documents.</p></div>"
                        + "<button id='editor-add' type='button'>Add skill document</button></div><div id='editor-skills' class='stack'></div></div>"
                        + "<div class='editor-document'><label for='editor-rest' class='section-title'>REST targets and routes YAML</label>"
                        + "<textarea id='editor-rest' class='code' spellcheck='false'></textarea></div></div>"
                        + "<div class='editor-bar'><div class='editor-bar-state'>"
                        + "<span class='editor-bar-item'><span class='editor-bar-label'>Draft</span><span id='editor-save' class='badge' role='status' aria-live='polite'>Not editing</span>"
                        + "<button id='editor-retry' class='btn-sm' type='button' hidden>Retry save</button></span>"
                        + "<span class='editor-bar-item'><span class='editor-bar-label'>Validation</span><span id='editor-validation-state' class='badge' role='status' aria-live='polite'>Out of date</span>"
                        + "<button id='editor-recheck' class='btn-sm' type='button' hidden>Retry validation</button></span></div>"
                        + "<button id='editor-publish' class='btn-primary' type='button' disabled>Publish validated draft</button></div>"
                        + "<details id='editor-details' class='card'><summary>Validation details</summary><ul id='editor-issues' class='issues'></ul></details>"
                        + "<div id='editor-outcome' class='notice' role='status' aria-live='polite'></div></section>"
                        + "<script src='/management/assets/editor.js' defer></script>");
    }

    @GetMapping(value = "/management/configuration/import", produces = MediaType.TEXT_HTML_VALUE)
    String importPage(Authentication auth, HttpServletRequest request)
    {
        return console("Import configuration", "Load an exported bundle into your saved draft, then validate and publish it.", ManagementController.principal(auth), request, "",
                "<section data-console='import' class='stack-lg'><ol class='stepper'><li>Choose bundle</li><li>Review on this Sidecar</li><li>Load into draft</li></ol>"
                        + "<div class='card stack'><h2>Configuration bundle</h2><p class='muted'>Choose a format-1 current-configuration bundle exported from this or another Sidecar.</p>"
                        + "<label class='dropzone'><span>Bundle file (.zip)</span><input id='import-file' type='file' accept='.zip,application/zip'></label>"
                        + "<div class='row'><button id='import-review' class='btn-primary' type='button'>Review on this destination</button></div>"
                        + "<p id='import-status' class='notice' role='status' aria-live='polite'>Choose a bundle to review.</p></div>"
                        + "<div id='import-summary' class='card stack' hidden><h2>Review</h2><dl id='import-details' class='facts'></dl>"
                        + "<h3>Validation feedback</h3><ul id='import-issues' class='issues'></ul>"
                        + "<p id='import-warning' class='notice warning'>Loading replaces only your saved draft. It does not change the running configuration until you validate and publish from the editor.</p>"
                        + "<div class='row'><button id='import-confirm' type='button' disabled>Load into my draft</button></div></div>"
                        + "<div id='import-outcome' class='notice' role='status' aria-live='polite'></div></section>"
                        + "<script src='/management/assets/import.js' defer></script>");
    }

    @GetMapping(value = "/management/password/change", produces = MediaType.TEXT_HTML_VALUE)
    String change(Authentication auth, HttpServletRequest request)
    {
        return console("Change password", "Changing your password signs you out of this session.", ManagementController.principal(auth), request, "",
                "<form class='card stack narrow' method='post' action='/management/password/change'>" + csrf(request)
                        + field("Current password", "<input name='current' type='password' autocomplete='current-password' required>")
                        + field("New password", "<input name='replacement' type='password' autocomplete='new-password' required>")
                        + passwordGuidance() + "<div class='row'><button class='btn-primary'>Change password</button></div></form>");
    }

    @PostMapping("/management/password/change")
    String changeSubmit(@RequestParam("current") String current, @RequestParam("replacement") String replacement,
            Authentication auth, HttpServletRequest request)
    {
        try
        {
            identity.change(ManagementController.principal(auth).id(), current, replacement);
            request.getSession(false).invalidate();
            return status("Password changed. Sign in normally.", false);
        }
        catch (RuntimeException rejected)
        {
            return status("Password could not be changed. Check the current password and policy, then retry.", true);
        }
    }

    private void tokenAttempt(HttpServletRequest request)
    {
        if (!attempts.record("token:ip:" + request.getRemoteAddr(), 10, Duration.ofMinutes(15)))
            throw new ManagementIdentityService.Rejected();
    }

    private static String csrf(HttpServletRequest request)
    {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return "<input type='hidden' name='" + escape(token.getParameterName()) + "' value='" + escape(token.getToken()) + "'>";
    }

    private static String status(String message, boolean error)
    {
        return page(error ? "Something went wrong" : "All set", null, notice(error ? "error" : "success", message)
                + "<a class='btn btn-primary btn-block' href='/management/login'>Sign in</a>");
    }

    private static String notice(String kind, String message)
    {
        return "<p class='notice " + kind + "'>" + escape(message) + "</p>";
    }

    private static String field(String label, String control)
    {
        return "<label class='field'><span>" + label + "</span>" + control + "</label>";
    }

    private static String passwordGuidance()
    {
        return "<p class='hint'>Use 15–128 Unicode characters (code points), no more than 512 UTF-8 bytes, with uppercase and lowercase letters, a number, and a non-whitespace punctuation mark or symbol. Spaces are allowed. You can paste or use a password manager.</p>";
    }

    private static String icon(String paths)
    {
        return "<svg class='icon' viewBox='0 0 24 24' aria-hidden='true' focusable='false'>" + paths + "</svg>";
    }

    private static String stat(String id, String label)
    {
        return "<article class='stat'><p class='stat-label'>" + label + "</p><p id='" + id + "' class='stat-value'>—</p>"
                + "<p id='" + id + "-meta' class='stat-meta'></p></article>";
    }

    private static String action(String href, String icon, String title, String description)
    {
        return "<a class='action-card' href='" + href + "'>" + icon(icon) + "<span><strong>" + title + "</strong><span>" + description + "</span></span></a>";
    }

    private static String roleLabel(String role)
    {
        return "admin".equals(role) ? "Administrator" : "editor".equals(role) ? "Editor" : "Viewer";
    }

    private static String console(String title, String description, ManagementUserDetailsService.Principal user,
            HttpServletRequest request, String actions, String body)
    {
        String path = request.getRequestURI();
        boolean editor = !"viewer".equals(user.role());
        String nav = "<nav class='nav' aria-label='Management'>"
                + link(path, "/management/home", ICON_HOME, "Overview")
                + "<p class='nav-label'>Configuration</p>"
                + link(path, "/management/configuration/current", ICON_CURRENT, "Current configuration")
                + link(path, "/management/configuration/history", ICON_HISTORY, "History")
                + (editor ? link(path, "/management/configuration/edit", ICON_EDIT, "Edit configuration")
                        + link(path, "/management/configuration/import", ICON_IMPORT, "Import configuration") : "")
                + link(path, "/management/configuration/recovery", ICON_RECOVERY, "Recovery guide")
                + ("admin".equals(user.role()) ? "<p class='nav-label'>Administration</p>"
                        + link(path, "/management/accounts", ICON_ACCOUNTS, "Accounts") : "")
                + "</nav>";
        String email = escape(user.email());
        String account = "<div class='account-panel'><div class='account-who'><span class='avatar' aria-hidden='true'>"
                + escape(user.email().substring(0, 1).toUpperCase()) + "</span><span class='account-text'><span class='account-email' title='" + email + "'>"
                + email + "</span><span class='role role-" + escape(user.role()) + "'>" + roleLabel(user.role()) + "</span></span></div>"
                + "<div class='account-actions'>" + link(path, "/management/password/change", ICON_KEY, "Change password")
                + "<form method='post' action='/management/logout'>" + csrf(request)
                + "<button class='nav-button'>" + icon(ICON_SIGN_OUT) + "<span>Sign out</span></button></form></div></div>";
        String header = "<header class='page-header'><div><h1>" + escape(title) + "</h1>"
                + (description == null ? "" : "<p class='lede'>" + escape(description) + "</p>") + "</div>"
                + (actions.isEmpty() ? "" : "<div class='page-actions'>" + actions + "</div>") + "</header>";
        return document(title, "app", "<a class='skip-link' href='#main'>Skip to content</a><div class='layout'><aside class='sidebar'>"
                + "<a class='brand' href='/management/home'><img src='/management/assets/loomspan-logo.png' alt='Loomspan' width='520' height='134'></a>"
                + "<p class='product'>Sidecar console</p>" + nav + account + "</aside>"
                + "<main id='main' class='main'>" + header + body + "</main></div>"
                + "<script src='/management/assets/console.js' defer></script>");
    }

    private static String link(String path, String href, String icon, String label)
    {
        return "<a href='" + href + "'" + (path.equals(href) ? " aria-current='page'" : "") + ">" + icon(icon) + "<span>" + label + "</span></a>";
    }

    private static String page(String title, String description, String body)
    {
        return document(title, "auth", "<main class='auth-shell'><div class='auth-card'>"
                + "<img class='auth-logo' src='/management/assets/loomspan-logo.png' alt='Loomspan' width='520' height='134'>"
                + "<p class='product'>Sidecar console</p><h1>" + escape(title) + "</h1>"
                + (description == null ? "" : "<p class='lede'>" + escape(description) + "</p>") + body + "</div></main>");
    }

    private static String document(String title, String bodyClass, String body)
    {
        return "<!doctype html><html lang='en'><head><meta charset='utf-8'><meta name='referrer' content='no-referrer'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><meta name='color-scheme' content='dark'><title>" + escape(title)
                + " · Loomspan Sidecar</title><link rel='icon' type='image/png' href='/management/assets/loomspan-mark.png'>"
                + "<link rel='stylesheet' href='/management/assets/console.css'></head><body class='" + bodyClass + "'>" + body + "</body></html>";
    }

    private static String escape(String value)
    {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
