package ai.loomspan.sidecar.storage;

/** A validator issue retaining severity, authored source and optional skill/field location. */
public record ConfigurationValidationIssue(Severity severity, String sourceLabel, String skillName,
        String location, String message)
{
    public enum Severity { ERROR, WARNING }

    public ConfigurationValidationIssue
    {
        if (severity == null) throw new IllegalArgumentException("severity must be nonnull");
        if (sourceLabel == null || sourceLabel.isBlank())
        {
            throw new IllegalArgumentException("sourceLabel must be nonblank");
        }
        if (message == null || message.isBlank())
        {
            throw new IllegalArgumentException("message must be nonblank");
        }
    }
}
