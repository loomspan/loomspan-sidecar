package ai.loomspan.sidecar.execution;

import ai.loomspan.api.SkillTemplate;
import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExecutionShutdownIntegrationTest {
    @Test
    void closeListenerOptsOutOfAsynchronousEventExecution() {
        var coordinator = new ExecutionCoordinator(mock(SkillTemplate.class),
                new SidecarExecutionProperties(), mock(ApplicationContext.class));
        try {
            assertThat(coordinator.supportsAsyncExecution()).isFalse();
        } finally {
            coordinator.destroy();
        }
    }

    @Test
    void ignoresAnotherContextAndPublishesRefusingTrafficForItsOwner() {
        var owningContext = mock(ApplicationContext.class);
        var otherContext = mock(ApplicationContext.class);
        var coordinator = new ExecutionCoordinator(mock(SkillTemplate.class),
                new SidecarExecutionProperties(), owningContext);
        try {
            coordinator.onApplicationEvent(new ContextClosedEvent(otherContext));
            verify(owningContext, never()).publishEvent(org.mockito.ArgumentMatchers.any(Object.class));

            coordinator.onApplicationEvent(new ContextClosedEvent(owningContext));
            verify(owningContext).publishEvent(argThat(event -> event instanceof AvailabilityChangeEvent<?> change
                    && change.getState() == ReadinessState.REFUSING_TRAFFIC));
        } finally {
            coordinator.destroy();
        }
    }
}
