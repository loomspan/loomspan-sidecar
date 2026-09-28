package ai.loomspan.sidecar.storage;

import java.util.Objects;
import java.util.UUID;

/** Content and identity remain fixed; status is read from separate bookkeeping. */
public record ConfigurationSnapshot(UUID localId, UUID sourceId, long submissionSequence,
        ManagedConfiguration configuration, String effectiveExecutionYaml, SnapshotStatus status)
{
    public ConfigurationSnapshot(UUID localId, UUID sourceId, long submissionSequence,
            ManagedConfiguration configuration, SnapshotStatus status) {
        this(localId, sourceId, submissionSequence, configuration,
                configuration.executionConfigurationYaml(), status);
    }

    public ConfigurationSnapshot
    {
        Objects.requireNonNull(localId, "localId");
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(effectiveExecutionYaml, "effectiveExecutionYaml");
        Objects.requireNonNull(status, "status");
        if (submissionSequence <= 0)
        {
            throw new IllegalArgumentException("submissionSequence must be positive");
        }
    }
}
