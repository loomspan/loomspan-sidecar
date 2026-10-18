package ai.loomspan.sdk;

public enum ExecutionStatus { QUEUED, RUNNING, COMPLETED, FAILED; public boolean terminal() { return this == COMPLETED || this == FAILED; } }
