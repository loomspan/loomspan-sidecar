package ai.loomspan.sidecar.management;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ManagementAttemptLimiterTest {
    static final class MutableClock extends Clock {
        Instant time = Instant.parse("2026-09-18T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time; }
    }
    @Test void attemptLimitsAreBoundedAndResponsesAreGeneric() {
        var clock = new MutableClock();
        var limiter = new ManagementAttemptLimiter(clock, 2);
        for (int i = 0; i < 5; i++) assertThat(limiter.record("login:account:a", 5, Duration.ofMinutes(15))).isTrue();
        assertThat(limiter.allowed("login:account:a", 5, Duration.ofMinutes(15))).isFalse();
        assertThat(limiter.record("login:account:b", 5, Duration.ofMinutes(15))).isTrue();
        assertThat(limiter.record("login:account:c", 5, Duration.ofMinutes(15))).isFalse();
        assertThat(limiter.size()).isEqualTo(2);
        limiter.clear("login:account:a");
        assertThat(limiter.record("login:account:c", 5, Duration.ofMinutes(15))).isTrue();
        clock.time = clock.time.plus(Duration.ofMinutes(15));
        assertThat(limiter.allowed("login:account:a", 5, Duration.ofMinutes(15))).isTrue();
        assertThat(limiter.size()).isZero();
    }
}
