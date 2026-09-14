package ai.loomspan.sidecar.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidecarExecutionPropertiesTest {
    @Test
    void rejectsInvalidLimitsAndTtl() {
        var properties = new SidecarExecutionProperties();
        properties.setMaxConcurrent(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        properties.setMaxConcurrent(1);
        properties.setCompletedTtl(Duration.ZERO);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
        properties.setCompletedTtl(Duration.ofSeconds(1));
        properties.setMaxQueued(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
