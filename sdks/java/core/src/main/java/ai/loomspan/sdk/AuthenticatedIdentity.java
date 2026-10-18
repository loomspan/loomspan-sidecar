package ai.loomspan.sdk;

import java.util.List;
import java.util.Collection;

/** Snapshot supplied only by trusted application authentication, never a business request. */
public record AuthenticatedIdentity(String subject, List<String> authorities) {
    public AuthenticatedIdentity(String subject, Collection<String> authorities) { this(subject, List.copyOf(authorities)); }
    public AuthenticatedIdentity {
        IssuanceSettings.nonblank(subject, "authenticated subject");
        authorities = IssuanceSettings.names(authorities, "authenticated authorities");
    }
}
