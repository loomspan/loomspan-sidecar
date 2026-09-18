package ai.loomspan.sidecar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomspan-sidecar.storage")
public class SidecarStorageProperties
{
    private String databasePath = "/sidecar/data/sidecar.db";

    public String getDatabasePath()
    {
        return databasePath;
    }

    public void setDatabasePath(String databasePath)
    {
        if (databasePath == null || databasePath.isBlank())
        {
            throw new IllegalArgumentException("Storage database path must be nonblank");
        }
        this.databasePath = databasePath;
    }
}
