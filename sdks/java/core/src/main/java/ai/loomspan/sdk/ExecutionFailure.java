package ai.loomspan.sdk;

import java.util.List;
public record ExecutionFailure(String kind, String message, String producingSkillName, List<Issue> issues) {
    public ExecutionFailure { issues = List.copyOf(issues); }
    public record Issue(String path, String code, String message) {}
}
