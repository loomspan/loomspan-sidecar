package ai.loomspan.sdk;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ExecutionSnapshot(UUID id, String skillName, ExecutionStatus status, Instant createdAt,
        Instant completedAt, String result, ExecutionFailure failure, List<ExecutionEvent> events,
        UUID configurationSnapshotId) {
    public ExecutionSnapshot { events = events == null ? null : List.copyOf(events); }
}
