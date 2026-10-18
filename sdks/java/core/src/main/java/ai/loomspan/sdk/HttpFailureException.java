package ai.loomspan.sdk;

import java.util.UUID;
import tools.jackson.databind.JsonNode;
public final class HttpFailureException extends SidecarException {
    private final int status;
    private final JsonNode problem;
    HttpFailureException(int status, JsonNode problem, UUID id, ExecutionSnapshot last) {
        super("Sidecar HTTP failure (status " + status + ")", id, last);
        this.status = status; this.problem = problem == null ? null : problem.deepCopy();
    }
    public int status() { return status; }
    public JsonNode problem() { return problem == null ? null : problem.deepCopy(); }
}
