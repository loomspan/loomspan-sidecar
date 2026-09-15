package ai.loomspan.sidecar.api;

import ai.loomspan.api.SkillInputValidationException;
import ai.loomspan.sidecar.execution.ExecutionCapacityException;
import ai.loomspan.sidecar.execution.ExecutionUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
final class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(ApiInputException.class)
    ProblemDetail badInput(ApiInputException failure) { return problem(HttpStatus.BAD_REQUEST, failure); }

    @ExceptionHandler(SkillInputValidationException.class)
    ProblemDetail validation(SkillInputValidationException failure) {
        var detail = problem(HttpStatus.BAD_REQUEST, failure);
        detail.setProperty("issues", failure.getIssues());
        return detail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail denied(AccessDeniedException failure) { return problem(HttpStatus.FORBIDDEN, failure); }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail missing(ResourceNotFoundException failure) { return problem(HttpStatus.NOT_FOUND, failure); }

    @ExceptionHandler(PayloadTooLargeException.class)
    ProblemDetail oversized(PayloadTooLargeException failure) { return problem(HttpStatus.PAYLOAD_TOO_LARGE, failure); }

    @ExceptionHandler(ExecutionCapacityException.class)
    ProblemDetail capacity(ExecutionCapacityException failure) { return problem(HttpStatus.TOO_MANY_REQUESTS, failure); }

    @ExceptionHandler(ExecutionUnavailableException.class)
    ProblemDetail unavailable(ExecutionUnavailableException failure) { return problem(HttpStatus.SERVICE_UNAVAILABLE, failure); }

    private ProblemDetail problem(HttpStatus status, RuntimeException failure) {
        var detail = ProblemDetail.forStatusAndDetail(status, failure.getMessage());
        detail.setTitle(status.getReasonPhrase());
        return detail;
    }
}
