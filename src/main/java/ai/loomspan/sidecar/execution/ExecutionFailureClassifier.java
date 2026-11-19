package ai.loomspan.sidecar.execution;

import java.util.List;

import ai.loomspan.api.SkillContractStage;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillInputValidationException;
import org.springframework.security.access.AccessDeniedException;

final class ExecutionFailureClassifier {
    private ExecutionFailureClassifier() { }

    static ExecutionFailure classify(Throwable failure) {
        if (failure instanceof SkillInputValidationException validation) {
            return new ExecutionFailure(ExecutionFailure.Kind.INPUT_VALIDATION,
                    validation.getMessage(), null, validation.getIssues().stream()
                            .map(issue -> new ExecutionFailure.Issue(issue.path(), issue.code(), issue.message()))
                            .toList());
        }
        if (failure instanceof AccessDeniedException) {
            return new ExecutionFailure(ExecutionFailure.Kind.ACCESS_DENIED, failure.getMessage(), null, List.of());
        }
        if (failure instanceof SkillException skillFailure) {
            var diagnosis = skillFailure.getFailureDiagnosis()
                    .filter(detail -> detail.stage() == SkillContractStage.OUTPUT_SCHEMA)
                    .orElse(null);
            if (diagnosis != null) {
                return new ExecutionFailure(ExecutionFailure.Kind.OUTPUT_SCHEMA,
                        failure.getMessage(), diagnosis.skillName(), diagnosis.issues().stream()
                                .map(issue -> new ExecutionFailure.Issue(issue.path(), issue.code(), issue.message()))
                                .toList());
            }
        }
        return new ExecutionFailure(ExecutionFailure.Kind.SKILL_FAILURE, failure.getMessage(), null, List.of());
    }
}
