package ai.loomspan.sdk;

import java.util.UUID;
public final class AcceptanceUnknownException extends SidecarException {
    AcceptanceUnknownException(UUID id, ExecutionSnapshot last) { super("Submission acceptance is unknown; server work may continue", id, last); }
}
