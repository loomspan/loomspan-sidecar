package ai.loomspan.sdk;

import java.time.Instant;
import tools.jackson.databind.JsonNode;
public record ExecutionEvent(Instant timestamp, String level, String type, JsonNode details, String frameId, String route) {
    public ExecutionEvent { details = details.deepCopy(); }
    @Override public JsonNode details() { return details.deepCopy(); }
}
