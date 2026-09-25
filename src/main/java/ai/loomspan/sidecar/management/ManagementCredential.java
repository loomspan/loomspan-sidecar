package ai.loomspan.sidecar.management;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;

/** Trusted request-local credential origin, never read from an authoring payload. */
public record ManagementCredential(String tokenId, String preset) {
    public static ManagementCredential current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getDetails() instanceof ManagementCredential credential ? credential : null;
    }
    public static boolean bearerOnly(HttpServletRequest request) {
        if (request.getHeader("Authorization") == null) return false;
        var cookies = request.getCookies();
        return cookies == null || java.util.Arrays.stream(cookies)
                .noneMatch(cookie -> "JSESSIONID".equals(cookie.getName()));
    }
}
