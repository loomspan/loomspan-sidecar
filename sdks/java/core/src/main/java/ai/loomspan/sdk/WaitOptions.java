package ai.loomspan.sdk;

import java.time.Duration;
public record WaitOptions(Duration timeout, Duration pollInterval) {
    public WaitOptions { SidecarClient.nanos(timeout); SidecarClient.nanos(pollInterval); }
    public static WaitOptions defaults() { return new WaitOptions(Duration.ofSeconds(60), Duration.ofMillis(250)); }
}
