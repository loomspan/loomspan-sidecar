package ai.loomspan.sidecar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomspan-sidecar.snapshots")
public class SidecarSnapshotProperties
{
    private int maxRetained = 10;

    public int getMaxRetained()
    {
        return maxRetained;
    }

    public void setMaxRetained(int maxRetained)
    {
        if (maxRetained <= 0)
        {
            throw new IllegalArgumentException("Snapshot retention limit must be positive");
        }
        this.maxRetained = maxRetained;
    }
}
