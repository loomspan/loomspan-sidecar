package ai.loomspan.sidecar.execution;

import java.time.Instant;
import java.util.UUID;

import ai.loomspan.sidecar.security.ExecutionOwner;

final class ExecutionRecord {
    final UUID id;
    final String skillName;
    final ExecutionOwner owner;
    final Instant createdAt;
    volatile ExecutionSnapshot snapshot;

    ExecutionRecord(UUID id, String skillName, ExecutionOwner owner, Instant createdAt) {
        this.id = id;
        this.skillName = skillName;
        this.owner = owner;
        this.createdAt = createdAt;
        this.snapshot = new ExecutionSnapshot(id, skillName, ExecutionStatus.QUEUED,
                createdAt, null, null, null, null);
    }
}
