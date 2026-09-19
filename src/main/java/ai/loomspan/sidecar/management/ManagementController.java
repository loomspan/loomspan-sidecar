package ai.loomspan.sidecar.management;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import ai.loomspan.sidecar.config.SidecarManagementProperties;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management")
public class ManagementController {
    public record Setup(String credential, String email) {
        @Override public String toString() { return "Setup[redacted]"; }
    }
    public record Email(String email) {
        @Override public String toString() { return "Email[redacted]"; }
    }
    public record Password(String token, String password) {
        @Override public String toString() { return "Password[redacted]"; }
    }
    public record Change(String currentPassword, String newPassword) {
        @Override public String toString() { return "Change[redacted]"; }
    }
    public record Invite(String email, String role) {}
    public record Alter(String role, Boolean enabled) {}
    public record Account(long id, String email, String role, boolean enabled, boolean activated) {
        static Account of(ManagementAccountRepository.Account value) {
            return new Account(value.id(), value.email(), value.role(), value.enabled(), value.passwordHash() != null);
        }
    }
    private final ManagementIdentityService identity;
    private final ManagementAttemptLimiter attempts;
    private final ManagementSessionGuard sessions;
    private final SidecarManagementProperties settings;

    public ManagementController(ManagementIdentityService identity, ManagementAttemptLimiter attempts,
            ManagementSessionGuard sessions, SidecarManagementProperties settings) {
        this.identity = identity; this.attempts = attempts; this.sessions = sessions; this.settings = settings;
    }
    @GetMapping("/session")
    public Map<String, Object> session(Authentication authentication, HttpServletRequest request) {
        var user = principal(authentication);
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return Map.of("id", user.id(), "email", user.email(), "role", user.role(),
                "permissions", user.getAuthorities().stream().map(a -> a.getAuthority()).toList(),
                "csrfToken", token.getToken(),
                "sessionIdleTimeoutSeconds", settings.getSessionIdleTimeout().toSeconds(),
                "editLeaseTimeoutSeconds", settings.getEditLeaseTimeout().toSeconds());
    }
    @PostMapping("/session/activity")
    public ResponseEntity<Void> activity(Authentication authentication, HttpServletRequest request) {
        principal(authentication);
        sessions.report(request);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/setup")
    public ResponseEntity<Void> setup(@RequestBody Setup body, HttpServletRequest request) {
        tokenAttempt(request);
        identity.setup(body.credential(), body.email());
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/password/forgot")
    public ResponseEntity<Void> forgot(@RequestBody Email body, HttpServletRequest request) {
        String address;
        try { address = ManagementPolicy.email(body.email()); }
        catch (IllegalArgumentException invalid) { address = "invalid"; }
        boolean permitted = attempts.record("forgot:email:" + address, 3, Duration.ofHours(1))
                && attempts.record("forgot:ip:" + request.getRemoteAddr(), 20, Duration.ofHours(1));
        if (permitted) identity.forgot(body.email());
        return ResponseEntity.accepted().build();
    }
    @PostMapping("/password/set")
    public ResponseEntity<Void> set(@RequestBody Password body, HttpServletRequest request) {
        tokenAttempt(request);
        identity.redeem("set", body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/password/reset")
    public ResponseEntity<Void> reset(@RequestBody Password body, HttpServletRequest request) {
        tokenAttempt(request);
        identity.redeem("reset", body.token(), body.password());
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/password/change")
    public ResponseEntity<Void> change(@RequestBody Change body, Authentication authentication, HttpServletRequest request) {
        identity.change(principal(authentication).id(), body.currentPassword(), body.newPassword());
        request.getSession(false).invalidate();
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/accounts")
    public List<Account> accounts() { return identity.accounts().stream().map(Account::of).toList(); }
    @PostMapping("/accounts")
    public ResponseEntity<Account> invite(@RequestBody Invite body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Account.of(identity.invite(body.email(), body.role())));
    }
    @PatchMapping("/accounts/{id}")
    public Account alter(@PathVariable("id") long id, @RequestBody Alter body) {
        var existing = identity.account(id);
        if (existing == null) throw new ManagementIdentityService.Rejected();
        identity.alter(id, body.role() == null ? existing.role() : body.role(),
                body.enabled() == null ? existing.enabled() : body.enabled());
        return Account.of(identity.account(id));
    }
    @PostMapping("/accounts/{id}/resend-invite")
    public ResponseEntity<Void> resend(@PathVariable("id") long id) {
        identity.resend(id); return ResponseEntity.accepted().build();
    }
    private void tokenAttempt(HttpServletRequest request) {
        if (!attempts.record("token:ip:" + request.getRemoteAddr(), 10, Duration.ofMinutes(15)))
            throw new ManagementIdentityService.Rejected();
    }
    static ManagementUserDetailsService.Principal principal(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof ManagementUserDetailsService.Principal user))
            throw new ManagementIdentityService.Rejected();
        return user;
    }
    @ExceptionHandler({ManagementIdentityService.Rejected.class, IllegalArgumentException.class})
    ResponseEntity<Map<String, String>> rejected() {
        return ResponseEntity.badRequest().body(Map.of("error", "Request could not be completed"));
    }
    @ExceptionHandler(ManagementMailService.Unavailable.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "Email delivery is unavailable; retry later"));
    }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> conflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Request could not be completed"));
    }
}
