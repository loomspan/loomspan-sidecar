package ai.loomspan.sidecar.storage;

/** A validator-supplied error, retaining its authored source and optional location. */
public record ConfigurationValidationIssue(String sourceLabel, String message, String location)
{
    public ConfigurationValidationIssue
    {
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
