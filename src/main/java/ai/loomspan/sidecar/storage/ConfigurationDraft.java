package ai.loomspan.sidecar.storage;

import java.util.Objects;
import java.util.UUID;

/** Ephemeral authored content and validation association for a caller-supplied runtime snapshot. */
public final class ConfigurationDraft
{
    private final UUID baseSnapshotId;
    private FrozenConfigurationCandidate current;
    private ConfigurationValidationResult validation;

    public ConfigurationDraft(ConfigurationSnapshot runtimeSnapshot)
    {
        Objects.requireNonNull(runtimeSnapshot, "runtimeSnapshot");
        baseSnapshotId = runtimeSnapshot.localId();
        current = new FrozenConfigurationCandidate(baseSnapshotId, runtimeSnapshot.configuration());
    }

    public UUID baseSnapshotId()
    {
        return baseSnapshotId;
    }

    public synchronized void replaceContent(ManagedConfiguration configuration)
    {
        current = new FrozenConfigurationCandidate(baseSnapshotId, configuration);
        validation = null;
    }

    public synchronized FrozenConfigurationCandidate freeze()
    {
        return current;
    }

    /** Accepts only a result for the precise current candidate, including after equal-content edits. */
    public synchronized boolean recordValidation(FrozenConfigurationCandidate candidate,
            ConfigurationValidationResult result)
    {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(result, "result");
        if (candidate != current)
        {
            return false;
        }
        validation = result;
        return true;
    }

    /** Returns null when the candidate is stale, foreign, or has no attached result. */
    public synchronized ConfigurationValidationResult validationFor(FrozenConfigurationCandidate candidate)
    {
        return candidate == current ? validation : null;
    }
}
