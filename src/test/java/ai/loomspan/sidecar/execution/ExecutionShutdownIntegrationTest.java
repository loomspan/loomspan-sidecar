package ai.loomspan.sidecar.execution;

import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.ApplicationListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ExecutionShutdownIntegrationTest {
    @Test
    void closeListenerOptsOutOfAsynchronousEventExecution() {
        var coordinator = new ExecutionCoordinator(mock(SkillInvocationHandoff.class),
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
        var coordinator = new ExecutionCoordinator(mock(SkillInvocationHandoff.class),
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

    @Test
    void asynchronousMulticasterKeepsCoordinatorCloseSynchronousRegardlessRegistrationPosition() throws Exception {
        for (boolean coordinatorFirst : new boolean[] {true, false}) {
            var owningContext = mock(ApplicationContext.class);
            var coordinator = new ExecutionCoordinator(mock(SkillInvocationHandoff.class),
                    new SidecarExecutionProperties(), owningContext);
            var asyncEntered = new CountDownLatch(1);
            var asyncRelease = new CountDownLatch(1);
            ApplicationListener<ContextClosedEvent> asyncListener = event -> {
                asyncEntered.countDown();
                try { asyncRelease.await(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            };
            try (var executor = Executors.newSingleThreadExecutor()) {
                var multicaster = new SimpleApplicationEventMulticaster();
                multicaster.setTaskExecutor(executor);
                if (coordinatorFirst) {
                    multicaster.addApplicationListener(coordinator);
                    multicaster.addApplicationListener(asyncListener);
                } else {
                    multicaster.addApplicationListener(asyncListener);
                    multicaster.addApplicationListener(coordinator);
                }
                multicaster.multicastEvent(new ContextClosedEvent(owningContext));
                verify(owningContext).publishEvent(argThat(event -> event instanceof AvailabilityChangeEvent<?> change
                        && change.getState() == ReadinessState.REFUSING_TRAFFIC));
                assertThat(asyncEntered.await(1, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                asyncRelease.countDown();
            } finally {
                asyncRelease.countDown();
                coordinator.destroy();
            }
        }
    }
}
