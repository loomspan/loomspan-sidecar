package ai.loomspan.sidecar.api;

final class ResourceNotFoundException extends RuntimeException {
    ResourceNotFoundException(String message) { super(message); }
}
