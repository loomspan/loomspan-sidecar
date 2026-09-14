package ai.loomspan.sidecar.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidecarExecutionPropertiesTest {
    @Test
    void rejectsEveryInvalidExecutionLimitCategory() {
        assertInvalid(properties -> properties.setMaxInputSize(null));
        assertInvalid(properties -> properties.setMaxInputSize(DataSize.ofBytes(0)));
        assertInvalid(properties -> properties.setMaxRetained(0));
        assertInvalid(properties -> properties.setCompletedTtl(null));
        assertInvalid(properties -> properties.setCompletedTtl(Duration.ZERO));
        assertInvalid(properties -> properties.setCompletedTtl(Duration.ofSeconds(-1)));
        assertInvalid(properties -> properties.setMaxConcurrent(0));
        assertInvalid(properties -> properties.setMaxQueued(0));
        assertInvalid(properties -> properties.setMaxQueuedInputSize(null));
        assertInvalid(properties -> properties.setMaxQueuedInputSize(DataSize.ofBytes(0)));
        assertInvalid(properties -> properties.setDiagnostics(null));

        var minimum = new SidecarExecutionProperties();
        minimum.setMaxInputSize(DataSize.ofBytes(1));
        minimum.setMaxRetained(1);
        minimum.setCompletedTtl(Duration.ofNanos(1));
        minimum.setMaxConcurrent(1);
        minimum.setMaxQueued(1);
        minimum.setMaxQueuedInputSize(DataSize.ofBytes(1));
        assertThatCode(minimum::validate).doesNotThrowAnyException();
    }

    private void assertInvalid(java.util.function.Consumer<SidecarExecutionProperties> mutation) {
        var properties = new SidecarExecutionProperties();
        mutation.accept(properties);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
