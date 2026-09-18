package ai.loomspan.sidecar.management;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Single-node bounded rolling-window counters. Capacity exhaustion denies requests. */
@Component
public class ManagementAttemptLimiter {
    private record Entry(long start, int count) {}
    private final Map<String, Entry> entries = new HashMap<>();
    private final Clock clock;
    private final int capacity;

    @Autowired public ManagementAttemptLimiter(Clock clock) { this(clock, 10_000); }
    ManagementAttemptLimiter(Clock clock, int capacity) { this.clock = clock; this.capacity = capacity; }

    public synchronized boolean allowed(String key, int limit, Duration window) {
        long now = clock.millis();
        Entry current = current(key, now, window);
        return current == null ? entries.size() < capacity : current.count < limit;
    }
    public synchronized boolean record(String key, int limit, Duration window) {
        long now = clock.millis();
        Entry current = current(key, now, window);
        if (current == null) {
            if (entries.size() >= capacity) return false;
            entries.put(key, new Entry(now, 1));
            return true;
        }
        if (current.count >= limit) return false;
        entries.put(key, new Entry(current.start, current.count + 1));
        return true;
    }
    public synchronized void clear(String key) { entries.remove(key); }
    private Entry current(String key, long now, Duration window) {
        entries.entrySet().removeIf(entry -> now - entry.getValue().start >= window.toMillis());
        return entries.get(key);
    }
    public synchronized int size() { return entries.size(); }
}
