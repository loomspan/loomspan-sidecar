package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ManagementSessionGuard extends OncePerRequestFilter {
    static final String ACTIVITY = "management.activity";
    static final String REPORT = "management.activity.report";
    private final ManagementIdentityService identity;
    private final SidecarManagementProperties settings;
    private final Clock clock;
    public ManagementSessionGuard(ManagementIdentityService identity, SidecarManagementProperties settings, Clock clock) {
        this.identity = identity; this.settings = settings; this.clock = clock;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof ManagementCredential) {
            chain.doFilter(request, response);
            return;
        }
        if (authentication != null && authentication.getPrincipal() instanceof ManagementUserDetailsService.Principal principal) {
            HttpSession session = request.getSession(false);
            var account = identity.account(principal.id());
            Long activity = session == null ? null : (Long) session.getAttribute(ACTIVITY);
            if (session == null || account == null || !account.active() || account.version() != principal.version()
                    || activity == null || clock.millis() - activity >= settings.getSessionIdleTimeout().toMillis()) {
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
    public synchronized void mark(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(ACTIVITY, clock.millis());
            session.setAttribute(REPORT, clock.millis());
        }
    }
    public synchronized long lastReportAt(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long last = session == null ? null : (Long) session.getAttribute(REPORT);
        return last == null ? clock.millis() : last;
    }
    public synchronized boolean report(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) throw expired();
        try {
            long now = clock.millis();
            Long activity = (Long) session.getAttribute(ACTIVITY);
            if (activity == null || now - activity >= settings.getSessionIdleTimeout().toMillis()) {
                session.invalidate();
                throw expired();
            }
            Long last = (Long) session.getAttribute(REPORT);
            if (last == null || now - last < 30_000L) return false;
            session.setAttribute(ACTIVITY, now);
            session.setAttribute(REPORT, now);
            return true;
        } catch (IllegalStateException invalidated) {
            throw expired();
        }
    }
    private static org.springframework.web.server.ResponseStatusException expired() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                "Management session expired");
    }
}
