package ai.loomspan.sdk;

public final class LocalFailureException extends SidecarException {
    LocalFailureException(String message) { super(message, null, null); }
}
