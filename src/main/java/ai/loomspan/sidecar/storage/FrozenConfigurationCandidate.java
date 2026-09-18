package ai.loomspan.sidecar.storage;

import java.util.Objects;
import java.util.UUID;

/** Immutable authored content from one draft revision, with the supplied runtime base identity. */
public final class FrozenConfigurationCandidate
{
    private final UUID baseSnapshotId;
    private final ManagedConfiguration configuration;

    FrozenConfigurationCandidate(UUID baseSnapshotId, ManagedConfiguration configuration)
    {
        this.baseSnapshotId = Objects.requireNonNull(baseSnapshotId, "baseSnapshotId");
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public UUID baseSnapshotId()
    {
        return baseSnapshotId;
    }

    public ManagedConfiguration configuration()
    {
        return configuration;
    }
}
