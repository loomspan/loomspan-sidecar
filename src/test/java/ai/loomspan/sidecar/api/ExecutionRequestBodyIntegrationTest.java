package ai.loomspan.sidecar.api;

import java.nio.charset.StandardCharsets;

import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutionRequestBodyIntegrationTest {
    @Test
    void rejectsMissingMalformedNullAndNonObjectBodies() throws Exception {
        var reader = reader(100);
        for (String body : new String[] { "", "null", "[]", "1", "{broken", "{} {}" }) {
            assertThatThrownBy(() -> reader.read(request(body))).isInstanceOf(ApiInputException.class);
        }
    }

    @Test
    void allowsExactRawUtf8LimitAndRejectsOneByteExcess() throws Exception {
        String exact = "{\"x\":\"é\"}";
        int bytes = exact.getBytes(StandardCharsets.UTF_8).length;
        assertThat(reader(bytes).read(request(exact))).containsEntry("x", "é");
        assertThatThrownBy(() -> reader(bytes - 1).read(request(exact)))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    private LimitedJsonObjectReader reader(long bytes) {
        var properties = new SidecarExecutionProperties();
        properties.setMaxInputSize(DataSize.ofBytes(bytes));
        return new LimitedJsonObjectReader(new ObjectMapper(), properties);
    }

    private MockHttpServletRequest request(String body) {
        var request = new MockHttpServletRequest();
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
