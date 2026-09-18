package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManagementSessionGuardTest {
    @Test
    void activityReportCannotReviveLoginThatExpiredAfterRequestAdmission() {
        class MutableClock extends Clock {
            Instant now = Instant.parse("2026-09-18T00:00:00Z");
            @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now; }
        }
        var clock = new MutableClock();
        var session = mock(HttpSession.class);
        var request = mock(HttpServletRequest.class);
        var attributes = new HashMap<String, Object>();
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute(anyString())).thenAnswer(call -> attributes.get(call.getArgument(0)));
        doAnswer(call -> {
            attributes.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(session).setAttribute(anyString(), org.mockito.ArgumentMatchers.any());
        var guard = new ManagementSessionGuard(mock(ManagementIdentityService.class),
                new SidecarManagementProperties(), clock);
        guard.mark(request);

        // The security filter may have admitted the request before this deadline.
        clock.now = clock.now.plus(Duration.ofMinutes(30));
        assertThatThrownBy(() -> guard.report(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(failure -> org.assertj.core.api.Assertions.assertThat(
                        ((ResponseStatusException) failure).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(session).invalidate();
    }
}
