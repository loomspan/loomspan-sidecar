package ai.loomspan.sidecar.configuration;

/** Deployment selection captured once, before framework binding. */
public enum ConfigurationMode {
    FILE, DATABASE;
    public String value() { return name().toLowerCase(java.util.Locale.ROOT); }
    public void requireDatabase() {
        if (this != DATABASE)
            throw new ai.loomspan.sidecar.management.ManagementEditingService.Conflict("configuration_read_only");
    }
}
