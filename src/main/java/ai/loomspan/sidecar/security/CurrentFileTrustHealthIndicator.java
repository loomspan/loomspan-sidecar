package ai.loomspan.sidecar.security;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

public final class CurrentFileTrustHealthIndicator implements HealthIndicator {
    private final CurrentFileJwtTrust trust;
    public CurrentFileTrustHealthIndicator(CurrentFileJwtTrust trust) { this.trust = trust; }
    @Override public Health health() {
        var snapshot = trust.snapshot();
        return (snapshot.ready() ? Health.up() : Health.down())
                .withDetail("mode", snapshot.mode()).withDetail("path", snapshot.path().toString())
                .withDetail("kids", snapshot.keys().keySet()).withDetail("lastLoadTime", snapshot.lastLoadTime())
                .withDetail("state", snapshot.state()).build();
    }
}
