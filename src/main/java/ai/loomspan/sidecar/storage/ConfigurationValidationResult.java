package ai.loomspan.sidecar.storage;

import java.util.List;
import java.util.Objects;

/** Supplied validation outcome; validation execution belongs to later integration. */
public record ConfigurationValidationResult(boolean successful, List<ConfigurationValidationIssue> issues)
{
    public ConfigurationValidationResult
    {
        Objects.requireNonNull(issues, "issues");
        issues = List.copyOf(issues);
    }
}
