package ai.loomspan.sidecar.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("loomspan-sidecar.executions")
public class SidecarExecutionProperties {
    public enum Diagnostics { NEVER, ONERROR, ALWAYS }

    private DataSize maxInputSize = DataSize.ofMegabytes(1);
    private int maxRetained = 1000;
    private Duration completedTtl = Duration.ofMinutes(15);
    private int maxConcurrent = 32;
    private int maxQueued = 128;
    private DataSize maxQueuedInputSize = DataSize.ofMegabytes(64);
    private Diagnostics diagnostics = Diagnostics.NEVER;

    public DataSize getMaxInputSize() { return maxInputSize; }
    public void setMaxInputSize(DataSize value) { maxInputSize = value; }
    public int getMaxRetained() { return maxRetained; }
    public void setMaxRetained(int value) { maxRetained = value; }
    public Duration getCompletedTtl() { return completedTtl; }
    public void setCompletedTtl(Duration value) { completedTtl = value; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int value) { maxConcurrent = value; }
    public int getMaxQueued() { return maxQueued; }
    public void setMaxQueued(int value) { maxQueued = value; }
    public DataSize getMaxQueuedInputSize() { return maxQueuedInputSize; }
    public void setMaxQueuedInputSize(DataSize value) { maxQueuedInputSize = value; }
    public Diagnostics getDiagnostics() { return diagnostics; }
    public void setDiagnostics(Diagnostics value) { diagnostics = value; }

    public void validate() {
        if (maxInputSize == null || maxInputSize.toBytes() <= 0 || maxRetained <= 0
                || completedTtl == null || completedTtl.isZero() || completedTtl.isNegative()
                || maxConcurrent <= 0 || maxQueued <= 0
                || maxQueuedInputSize == null || maxQueuedInputSize.toBytes() <= 0
                || diagnostics == null) {
            throw new IllegalArgumentException("Execution sizes, counts and completed TTL must be positive");
        }
    }
}
