package ai.loomspan.sidecar.api;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NoStoreResponseFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        if (request.getRequestURI().startsWith("/management/")
                || request.getRequestURI().startsWith("/api/management/")) {
            response.setHeader("Referrer-Policy", "no-referrer");
        }
        filterChain.doFilter(request, response);
    }
}
