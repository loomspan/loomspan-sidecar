package ai.loomspan.sidecar.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Durable execution correlation only; Framework owns generations and retained work. */
@Component
public final class ConfigurationGenerationRegistry {
    private final Map<String, UUID> snapshots = new HashMap<>();

    public synchronized void stage(String generation, UUID snapshot) {
        java.util.Objects.requireNonNull(generation);
        java.util.Objects.requireNonNull(snapshot);
        if (snapshots.putIfAbsent(generation, snapshot) != null)
            throw new IllegalStateException("Configuration generation already correlated");
    }

    public synchronized UUID snapshotId(String generation) {
        UUID snapshot = snapshots.get(generation);
        if (snapshot == null) throw new IllegalStateException("Configuration generation correlation unavailable");
        return snapshot;
    }

    public synchronized Set<UUID> protectedIds() { return Set.copyOf(snapshots.values()); }
    public synchronized void discard(String generation) { snapshots.remove(generation); }
    public synchronized void retire(String generation) { snapshots.remove(generation); }
}
