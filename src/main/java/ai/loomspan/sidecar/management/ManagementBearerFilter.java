package ai.loomspan.sidecar.management;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authenticates PATs only on the closed shared authoring route map. */
public final class ManagementBearerFilter extends OncePerRequestFilter {
    private static final Logger audit = LoggerFactory.getLogger("sidecar.management.audit");
    private final ManagementPersonalTokenService tokens;
    private final ManagementUserDetailsService users;
    private final ManagementIdentityService identity;
    private final ManagementAttemptLimiter attempts;
    public ManagementBearerFilter(ManagementPersonalTokenService tokens, ManagementUserDetailsService users,
            ManagementIdentityService identity, ManagementAttemptLimiter attempts) {
        this.tokens = tokens; this.users = users; this.identity = identity; this.attempts = attempts;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.isEmpty()) { chain.doFilter(request, response); return; }
        String ip = "pat:ip:" + request.getRemoteAddr();
        if (!attempts.allowed(ip, 20, Duration.ofMinutes(15))) { deny(response, 429); return; }
        if (headers.size() != 1 || !ManagementCredential.bearerOnly(request)) { fail(ip, response); return; }
        String header = headers.getFirst();
        if (header.length() > 128 || !header.startsWith("Bearer ") || header.indexOf(' ', 7) >= 0) {
            fail(ip, response); return;
        }
        var verified = tokens.authenticate(header.substring(7));
        if (verified == null) { fail(ip, response); return; }
        var account = identity.account(verified.owner());
        if (account == null || !account.active()) { fail(ip, response); return; }
        String required = required(request.getMethod(), request.getRequestURI());
        if (required == null || !ManagementPersonalTokenService.permits(verified.preset(), required)
                || ("edit".equals(required) || "publish".equals(required))
                    && "viewer".equals(account.role())) {
            audit.info("action=pat.authorize actor={} token={} outcome=denied", verified.owner(), verified.id());
            deny(response, 403); return;
        }
        ManagementUserDetailsService.Principal principal;
        try { principal = (ManagementUserDetailsService.Principal) users.loadUserByUsername(account.email()); }
        catch (org.springframework.security.core.userdetails.UsernameNotFoundException changed) {
            fail(ip, response); return;
        }
        var auth = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
        auth.setDetails(new ManagementCredential(verified.id(), verified.preset()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
    private void fail(String ip, HttpServletResponse response) throws IOException {
        attempts.record(ip, 20, Duration.ofMinutes(15));
        audit.info("action=pat.authenticate outcome=denied");
        deny(response, 401);
    }
    private static void deny(HttpServletResponse response, int code) throws IOException {
        ManagementSecurityConfiguration.problem(response, code, code == 429 ? "Too Many Requests"
                : code == 403 ? "Forbidden" : "Unauthorized");
    }
    static String required(String method, String path) {
        if ("GET".equals(method)) {
            if (path.equals("/api/management/configuration/current") || path.equals("/api/management/configuration/export")
                    || path.equals("/api/management/configuration/history")
                    || path.matches("/api/management/configuration/history/[0-9a-fA-F-]{36}")
                    || path.equals("/api/management/editing") || path.equals("/api/management/editing/draft")) return "read";
        } else if ("POST".equals(method)) {
            if (path.equals("/api/management/configuration/publish")) return "publish";
            if (path.equals("/api/management/editing/lease") || path.equals("/api/management/editing/lease/handoff")
                    || path.equals("/api/management/editing/lease/renew") || path.equals("/api/management/editing/lease/release")
                    || path.equals("/api/management/editing/draft/reconcile") || path.equals("/api/management/editing/draft/validate")
                    || path.equals("/api/management/configuration/import/review") || path.equals("/api/management/configuration/import/load")
                    || path.matches("/api/management/configuration/rollback/[0-9a-fA-F-]{36}/(review|load)")) return "edit";
        } else if (("PUT".equals(method) || "DELETE".equals(method)) && path.equals("/api/management/editing/draft")) return "edit";
        return null;
    }
}
