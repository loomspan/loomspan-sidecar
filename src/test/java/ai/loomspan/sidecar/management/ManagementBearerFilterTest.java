package ai.loomspan.sidecar.management;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ManagementBearerFilterTest {
    @Test void routePolicyIsClosedAndMapsEverySharedOperation() {
        for (String path : new String[]{"/api/management/configuration/current", "/api/management/configuration/export",
                "/api/management/configuration/history", "/api/management/configuration/history/00000000-0000-0000-0000-000000000001",
                "/api/management/editing", "/api/management/editing/draft"})
            assertThat(ManagementBearerFilter.required("GET", path)).isEqualTo("read");
        for (String path : new String[]{"/api/management/editing/lease", "/api/management/editing/lease/handoff",
                "/api/management/editing/lease/renew", "/api/management/editing/lease/release",
                "/api/management/editing/draft/reconcile", "/api/management/editing/draft/validate",
                "/api/management/configuration/import/review", "/api/management/configuration/import/load",
                "/api/management/configuration/rollback/00000000-0000-0000-0000-000000000001/review",
                "/api/management/configuration/rollback/00000000-0000-0000-0000-000000000001/load"})
            assertThat(ManagementBearerFilter.required("POST", path)).isEqualTo("edit");
        assertThat(ManagementBearerFilter.required("PUT", "/api/management/editing/draft")).isEqualTo("edit");
        assertThat(ManagementBearerFilter.required("DELETE", "/api/management/editing/draft")).isEqualTo("edit");
        assertThat(ManagementBearerFilter.required("POST", "/api/management/configuration/publish")).isEqualTo("publish");
        for (String path : new String[]{"/api/management/accounts", "/api/management/personal-tokens",
                "/api/management/session", "/api/management/logout", "/api/management/editing/lease/takeover",
                "/api/management/password/change"})
            assertThat(ManagementBearerFilter.required("GET", path)).isNull();
    }

    @Test void malformedAttemptsAndOversizedHeadersAreBoundedPerIp() throws Exception {
        var now = new AtomicReference<>(Instant.parse("2026-09-18T00:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        var attempts = new ManagementAttemptLimiter(clock, 1);
        var filter = new ManagementBearerFilter(mock(ManagementPersonalTokenService.class),
                mock(ManagementUserDetailsService.class), mock(ManagementIdentityService.class), attempts);
        for (int i = 0; i < 20; i++) assertThat(request(filter, "192.0.2.1", "Bearer invalid")).isEqualTo(401);
        assertThat(request(filter, "192.0.2.1", "Bearer invalid")).isEqualTo(429);
        assertThat(request(filter, "192.0.2.2", "Bearer invalid")).isEqualTo(429);
        now.set(now.get().plus(Duration.ofMinutes(16)));
        assertThat(request(filter, "192.0.2.1", "Bearer " + "x".repeat(129))).isEqualTo(401);
    }
    private static int request(ManagementBearerFilter filter, String ip, String header) throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/management/configuration/current");
        request.setRemoteAddr(ip); request.addHeader("Authorization", header);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }
}
