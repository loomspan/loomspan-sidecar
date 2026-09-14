package ai.loomspan.sidecar.api;

import ai.loomspan.sidecar.execution.ExecutionUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    @Test
    void mapsClosedAdmissionToServiceUnavailableProblem() {
        var detail = new ApiExceptionHandler().unavailable(new ExecutionUnavailableException());

        assertThat(detail.getStatus()).isEqualTo(503);
        assertThat(detail.getTitle()).isEqualTo("Service Unavailable");
        assertThat(detail.getDetail()).isEqualTo("Execution admission is closed");
    }
}
