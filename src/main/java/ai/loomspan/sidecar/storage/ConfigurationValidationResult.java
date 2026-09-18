package ai.loomspan.sidecar.storage;

import java.util.List;
import java.util.Objects;

/** Immutable validation outcome for one exact frozen candidate. */
public record ConfigurationValidationResult(boolean successful, List<ConfigurationValidationIssue> issues)
{
    public ConfigurationValidationResult
    {
        Objects.requireNonNull(issues, "issues");
        issues = List.copyOf(issues);
    }
}
