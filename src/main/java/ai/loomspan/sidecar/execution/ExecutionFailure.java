package ai.loomspan.sidecar.execution;

import java.util.List;

import ai.loomspan.api.SkillInputValidationIssue;

public record ExecutionFailure(Kind kind, String message, List<SkillInputValidationIssue> issues) {
    public enum Kind { INPUT_VALIDATION, ACCESS_DENIED, SKILL_FAILURE }

    public ExecutionFailure {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
