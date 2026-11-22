package ai.loomspan.sidecar.storage;

import java.util.Objects;
import java.util.UUID;

/** Content and identity remain fixed; status is read from separate bookkeeping. */
public record ConfigurationSnapshot(UUID localId, UUID sourceId, long submissionSequence,
        ManagedConfiguration configuration, SnapshotStatus status)
{
    public ConfigurationSnapshot
    {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(status, "status");
        if (submissionSequence <= 0)
        {
            throw new IllegalArgumentException("submissionSequence must be positive");
        }
    }
}
