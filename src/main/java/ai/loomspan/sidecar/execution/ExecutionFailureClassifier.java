package ai.loomspan.sidecar.execution;

import ai.loomspan.api.SkillInputValidationException;
import org.springframework.security.access.AccessDeniedException;

final class ExecutionFailureClassifier {
    private ExecutionFailureClassifier() { }

    static ExecutionFailure classify(Throwable failure) {
        if (failure instanceof SkillInputValidationException validation) {
            return new ExecutionFailure(ExecutionFailure.Kind.INPUT_VALIDATION,
                    validation.getMessage(), validation.getIssues());
        }
        if (failure instanceof AccessDeniedException) {
            return new ExecutionFailure(ExecutionFailure.Kind.ACCESS_DENIED, failure.getMessage(), null);
        }
        return new ExecutionFailure(ExecutionFailure.Kind.SKILL_FAILURE, failure.getMessage(), null);
    }
}
