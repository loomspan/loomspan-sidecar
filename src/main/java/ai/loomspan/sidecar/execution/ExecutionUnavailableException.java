package ai.loomspan.sidecar.execution;

public class ExecutionUnavailableException extends RuntimeException {
    public ExecutionUnavailableException() { super("Execution admission is closed"); }
}
