package ai.loomspan.sdk;

import java.util.UUID;
public final class WaitTerminatedException extends SidecarException {
    WaitTerminatedException(UUID id, ExecutionSnapshot last) { super("Local wait terminated; server work may continue", id, last); }
}
