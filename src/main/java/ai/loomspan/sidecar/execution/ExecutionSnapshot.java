package ai.loomspan.sidecar.execution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ai.loomspan.api.SkillExecutionEvent;

public record ExecutionSnapshot(UUID id, String skillName, ExecutionStatus status,
        Instant createdAt, Instant completedAt, String result, ExecutionFailure failure,
        List<SkillExecutionEvent> events) {
    public ExecutionSnapshot {
        events = events == null ? null : List.copyOf(events);
    }
}
