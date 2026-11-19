package ai.loomspan.sidecar.execution;

import java.util.List;

public record ExecutionFailure(Kind kind, String message, String producingSkillName, List<Issue> issues) {
    public enum Kind { INPUT_VALIDATION, ACCESS_DENIED, SKILL_FAILURE, OUTPUT_SCHEMA }

    public record Issue(String path, String code, String message) { }

    public ExecutionFailure {
        issues = List.copyOf(issues);
    }
}
