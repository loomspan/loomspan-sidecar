package ai.loomspan.sidecar.execution;

import java.time.Instant;
import java.util.UUID;

import ai.loomspan.sidecar.security.ExecutionOwner;

final class ExecutionRecord {
    final UUID id;
    final String namespace;
    final String localName;
    final ExecutionOwner owner;
    final Instant createdAt;
    volatile ExecutionSnapshot snapshot;

    ExecutionRecord(UUID id, String namespace, String localName, ExecutionOwner owner, Instant createdAt) {
        this.id = id;
        this.namespace = namespace;
        this.localName = localName;
        this.owner = owner;
        this.createdAt = createdAt;
        this.snapshot = new ExecutionSnapshot(id, namespace, localName, ExecutionStatus.QUEUED,
                createdAt, null, null, null, null, null);
    }
}
