package ai.loomspan.sdk;

import java.util.UUID;
/** Safe diagnostic; explicit structured accessors may contain server business details. */
public abstract class SidecarException extends RuntimeException {
    private final UUID executionId;
    private final ExecutionSnapshot lastSnapshot;
    SidecarException(String message, UUID executionId, ExecutionSnapshot lastSnapshot) {
        super(message); this.executionId = executionId; this.lastSnapshot = lastSnapshot;
    }
    public final UUID executionId() { return executionId; }
    public final ExecutionSnapshot lastSnapshot() { return lastSnapshot; }
}
