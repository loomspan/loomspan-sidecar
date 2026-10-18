package ai.loomspan.sdk;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/** Immutable token profile settings. Authority names are exact. */
public record IssuanceSettings(String issuer, Duration lifetime, List<String> additionalAudiences, List<String> roles) {
    public IssuanceSettings {
        nonblank(issuer, "loomspan-sdk.issuance.issuer");
        if (lifetime == null || lifetime.isNegative() || lifetime.isZero())
            throw new IllegalArgumentException("loomspan-sdk.issuance.lifetime must be positive");
        additionalAudiences = names(additionalAudiences, "additional-audiences");
        roles = names(roles, "roles");
    }
    public static IssuanceSettings defaults() { return new IssuanceSettings("loomspan-app", Duration.ofMinutes(15), List.of(), List.of()); }
    static void nonblank(String value, String property) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(property + " must be nonblank");
    }
    static List<String> names(Collection<String> values, String property) {
        if (values == null) throw new IllegalArgumentException(property + " must be a collection");
        values.forEach(value -> nonblank(value, property));
        return List.copyOf(values);
    }
}
