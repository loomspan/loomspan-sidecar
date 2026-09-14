package ai.loomspan.sidecar.api;

final class PayloadTooLargeException extends RuntimeException {
    PayloadTooLargeException() { super("Request body exceeds max-input-size"); }
}
