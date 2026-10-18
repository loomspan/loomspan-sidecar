package ai.loomspan.sdk;

import java.util.UUID;
public final class ProtocolException extends SidecarException {
    ProtocolException(UUID id, ExecutionSnapshot last) { super("Invalid Sidecar response", id, last); }
}
