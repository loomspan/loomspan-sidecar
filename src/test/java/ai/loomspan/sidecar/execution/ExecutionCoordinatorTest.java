package ai.loomspan.sidecar.execution;

import java.time.Instant;
import java.time.Duration;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import ai.loomspan.api.SkillExecutionView;
import ai.loomspan.api.SkillException;
import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.api.SkillTemplate;
import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import ai.loomspan.sidecar.security.ExecutionOwner;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExecutionCoordinatorTest {
    @Test
    void directHandoffDoesNotConsumeWaitingBudgetAndHandoffReleasesBeforeCompletion() throws Exception {
        var firstEntered = new CountDownLatch(1);
        var firstRelease = new CountDownLatch(1);
        var secondEntered = new CountDownLatch(1);
        var secondRelease = new CountDownLatch(1);
        var invocations = new AtomicInteger();
        var seenTokens = new java.util.concurrent.CopyOnWriteArrayList<String>();
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            seenTokens.add(((JwtAuthenticationToken) org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication()).getToken().getTokenValue());
            if (invocations.incrementAndGet() == 1) {
                firstEntered.countDown();
                firstRelease.await(5, TimeUnit.SECONDS);
            } else {
                secondEntered.countDown();
                secondRelease.await(5, TimeUnit.SECONDS);
            }
            return "done";
        });
        var context = mock(ApplicationContext.class);
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties(), context);
        var firstAuthentication = authentication("token-one", "owner");
        var secondAuthentication = authentication("token-two", "owner");
        var owner = ExecutionOwner.from(firstAuthentication);
        try {
            var first = coordinator.admit("skill", Map.of(), 2, owner, firstAuthentication);
            assertThat(firstEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.queuedCount()).isZero();
            assertThat(coordinator.queuedBytes()).isZero();

            var second = coordinator.admit("skill", Map.of(), 10, owner, secondAuthentication);
            assertThat(coordinator.queuedCount()).isEqualTo(1);
            assertThat(coordinator.queuedBytes()).isEqualTo(10);
            firstRelease.countDown();
            assertThat(secondEntered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(coordinator.queuedCount()).isZero();
            assertThat(coordinator.queuedBytes()).isZero();
            assertThat(seenTokens).containsExactly("token-one", "token-two");
            secondRelease.countDown();
            awaitTerminal(coordinator, first, owner);
            awaitTerminal(coordinator, second, owner);
        } finally {
            firstRelease.countDown();
            secondRelease.countDown();
            coordinator.destroy();
        }
    }

    @Test
    void boundsWorkersQueueCountBytesAndRetainedRecords() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var workerToken = new AtomicReference<String>();
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            workerToken.set(((JwtAuthenticationToken) org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication()).getToken().getTokenValue());
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "exact-result";
        });
        var properties = properties();
        ApplicationContext context = mock(ApplicationContext.class);
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties, context);
        var authentication = authentication("token-a", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var first = coordinator.admit("skill", Map.of("x", "one"), 5, owner, authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var second = coordinator.admit("skill", Map.of("x", "two"), 10, owner, authentication);
            assertThat(coordinator.find(second, owner).orElseThrow().status()).isEqualTo(ExecutionStatus.QUEUED);
            assertThat(coordinator.queuedCount()).isEqualTo(1);
            assertThat(coordinator.queuedBytes()).isEqualTo(10);
            assertThatThrownBy(() -> coordinator.admit("skill", Map.of("x", "three"), 1, owner, authentication))
                    .isInstanceOf(ExecutionCapacityException.class);
            release.countDown();
            awaitTerminal(coordinator, first, owner);
            awaitTerminal(coordinator, second, owner);
            assertThat(coordinator.find(first, new ExecutionOwner("https://other.test", "owner"))).isEmpty();
            assertThat(coordinator.find(first, new ExecutionOwner("https://issuer.test", "other"))).isEmpty();
            assertThat(coordinator.queuedCount()).isZero();
            assertThat(workerToken.get()).isEqualTo("token-a");
        } finally {
            release.countDown();
            coordinator.destroy();
        }
    }

    @Test
    void shutdownDiscardsQueuedWorkAndClosesAdmissionWithoutStoppingActiveWork() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "done";
        });
        var context = mock(ApplicationContext.class);
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties(), context);
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var active = coordinator.admit("skill", Map.of(), 2, owner, authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = coordinator.admit("skill", Map.of(), 2, owner, authentication);
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
            assertThat(coordinator.find(queued, owner)).isEmpty();
            assertThat(coordinator.queuedCount()).isZero();
            assertThatThrownBy(() -> coordinator.admit("skill", Map.of(), 2, owner, authentication))
                    .isInstanceOf(ExecutionUnavailableException.class);
            release.countDown();
            assertThat(awaitTerminal(coordinator, active, owner).result()).isEqualTo("done");
        } finally {
            release.countDown();
            coordinator.destroy();
        }
    }

    @Test
    void completionExpiryReclaimsWholeRecordWithoutPollingAndReadsDoNotExtendTtl() throws Exception {
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenReturn("done");
        var properties = properties();
        properties.setCompletedTtl(Duration.ofMillis(40));
        var clock = new MutableClock(Instant.parse("2026-09-13T00:00:00Z"));
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties,
                mock(ApplicationContext.class), clock);
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var id = coordinator.admit("skill", Map.of(), 2, owner, authentication);
            awaitTerminal(coordinator, id, owner);
            assertThat(coordinator.find(id, owner)).isPresent();
            clock.advance(Duration.ofMillis(41));
            for (int count = 0; count < 100 && coordinator.retainedCount() != 0; count++) {
                Thread.sleep(5);
            }
            assertThat(coordinator.retainedCount()).isZero();
            assertThat(coordinator.find(id, owner)).isEmpty();
        } finally {
            coordinator.destroy();
        }
    }

    @Test
    void simultaneousAdmissionsCannotExceedWorkerQueueOrRetainedCapacity() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "done";
        });
        var properties = new SidecarExecutionProperties();
        properties.setMaxConcurrent(1);
        properties.setMaxQueued(2);
        properties.setMaxQueuedInputSize(DataSize.ofBytes(100));
        properties.setMaxRetained(3);
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties,
                mock(ApplicationContext.class));
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        var accepted = new ConcurrentLinkedQueue<java.util.UUID>();
        var rejected = new AtomicInteger();
        var start = new CountDownLatch(1);
        var submissions = Executors.newFixedThreadPool(8);
        try {
            var active = coordinator.admit("skill", Map.of(), 2, owner, authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            for (int count = 0; count < 8; count++) {
                submissions.submit(() -> {
                    try {
                        start.await();
                        accepted.add(coordinator.admit("skill", Map.of(), 10, owner, authentication));
                    } catch (ExecutionCapacityException expected) {
                        rejected.incrementAndGet();
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            submissions.shutdown();
            assertThat(submissions.awaitTermination(3, TimeUnit.SECONDS)).isTrue();
            assertThat(accepted).hasSize(2);
            assertThat(rejected).hasValue(6);
            assertThat(coordinator.retainedCount()).isEqualTo(3);
            assertThat(coordinator.queuedCount()).isEqualTo(2);
            release.countDown();
            awaitTerminal(coordinator, active, owner);
            for (var id : accepted) awaitTerminal(coordinator, id, owner);
        } finally {
            release.countDown();
            submissions.shutdownNow();
            coordinator.destroy();
        }
    }

    @Test
    void queuedByteLimitAcceptsEqualityAndRejectsIndependentExcessWithoutAnOrphan() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "done";
        });
        var properties = new SidecarExecutionProperties();
        properties.setMaxConcurrent(1);
        properties.setMaxQueued(2);
        properties.setMaxQueuedInputSize(DataSize.ofBytes(10));
        properties.setMaxRetained(10);
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties,
                mock(ApplicationContext.class));
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var active = coordinator.admit("skill", Map.of(), 1, owner, authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            var queued = coordinator.admit("skill", Map.of(), 10, owner, authentication);
            assertThat(coordinator.queuedBytes()).isEqualTo(10);
            assertThatThrownBy(() -> coordinator.admit("skill", Map.of(), 1, owner, authentication))
                    .isInstanceOf(ExecutionCapacityException.class);
            assertThat(coordinator.retainedCount()).isEqualTo(2);
            assertThat(coordinator.queuedCount()).isEqualTo(1);
            assertThat(coordinator.queuedBytes()).isEqualTo(10);
            release.countDown();
            awaitTerminal(coordinator, active, owner);
            awaitTerminal(coordinator, queued, owner);
        } finally {
            release.countDown();
            coordinator.destroy();
        }
    }

    @Test
    void queueRejectionRollsBackRecordAndReservationExactlyOnce() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return "done";
        });
        var context = mock(ApplicationContext.class);
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties(), context);
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var active = coordinator.admit("skill", Map.of(), 1, owner, authentication);
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            coordinator.admit("skill", Map.of(), 10, owner, authentication);
            assertThatThrownBy(() -> coordinator.admit("skill", Map.of(), 1, owner, authentication))
                    .isInstanceOf(ExecutionCapacityException.class);
            assertThat(coordinator.retainedCount()).isEqualTo(2);
            assertThat(coordinator.queuedCount()).isEqualTo(1);
            assertThat(coordinator.queuedBytes()).isEqualTo(10);
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
            assertThat(coordinator.queuedCount()).isZero();
            assertThat(coordinator.queuedBytes()).isZero();
            assertThat(coordinator.retainedCount()).isEqualTo(1);
            release.countDown();
            awaitTerminal(coordinator, active, owner);
        } finally {
            release.countDown();
            coordinator.destroy();
        }
    }

    @Test
    void expiryReclaimsCapacityOnAdmissionWithoutExpiringActiveWork() throws Exception {
        var activeEntered = new CountDownLatch(1);
        var activeRelease = new CountDownLatch(1);
        var invocation = new AtomicInteger();
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(call -> {
            if (invocation.incrementAndGet() == 1) return "first";
            activeEntered.countDown();
            activeRelease.await(5, TimeUnit.SECONDS);
            return "second";
        });
        var properties = properties();
        properties.setMaxRetained(1);
        properties.setCompletedTtl(Duration.ofMinutes(1));
        var clock = new MutableClock(Instant.parse("2026-09-13T00:00:00Z"));
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties,
                mock(ApplicationContext.class), clock);
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var expired = coordinator.admit("skill", Map.of(), 1, owner, authentication);
            awaitTerminal(coordinator, expired, owner);
            clock.advance(Duration.ofMinutes(1));
            var active = coordinator.admit("skill", Map.of(), 1, owner, authentication);
            assertThat(activeEntered.await(2, TimeUnit.SECONDS)).isTrue();
            clock.advance(Duration.ofHours(1));
            assertThat(coordinator.find(active, owner)).isPresent();
            assertThat(coordinator.retainedCount()).isEqualTo(1);
        } finally {
            activeRelease.countDown();
            coordinator.destroy();
        }
    }

    @Test
    void preventsFacadeEntryWhenBeginLosesTheCloseRace() {
        SkillTemplate template = mock(SkillTemplate.class);
        var context = mock(ApplicationContext.class);
        var coordinator = new ExecutionCoordinator(TestSkillInvocationHandoff.from(template), properties(), context);
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
            var record = new ExecutionRecord(java.util.UUID.randomUUID(), "skill", owner, Instant.now());
            new ExecutionCoordinator.ExecutionTask(coordinator, record, Map.of("secret", "value"), 2,
                    authentication).run();
            verify(template, never()).invoke(anyString(), anyMap(), any());
            assertThat(coordinator.queuedCount()).isZero();
            assertThat(coordinator.queuedBytes()).isZero();
        } finally {
            coordinator.destroy();
        }
    }

    @Test
    void dequeuedTaskDoesNotEnterFacadeWhenCloseWinsDispatchHandoff() throws Exception {
        var activeEntered = new CountDownLatch(1);
        var activeRelease = new CountDownLatch(1);
        var handoffEntered = new CountDownLatch(1);
        var handoffRelease = new CountDownLatch(1);
        var handedOffTask = new AtomicReference<ExecutionCoordinator.ExecutionTask>();
        var hookCalls = new AtomicInteger();
        var frameworkHandoffs = new AtomicInteger();
        var invocations = new AtomicInteger();
        SkillTemplate template = mock(SkillTemplate.class);
        when(template.invoke(anyString(), anyMap(), any())).thenAnswer(call -> {
            invocations.incrementAndGet();
            activeEntered.countDown();
            activeRelease.await(5, TimeUnit.SECONDS);
            return "done";
        });
        var context = mock(ApplicationContext.class);
        SkillInvocationHandoff delegate = TestSkillInvocationHandoff.from(template);
        SkillInvocationHandoff handoff = new SkillInvocationHandoff() {
            @Override public ai.loomspan.api.AdmittedSkillInvocation handoff(String skillName, Object input) {
                frameworkHandoffs.incrementAndGet();
                return delegate.handoff(skillName, input);
            }
            @Override public ai.loomspan.api.AdmittedSkillInvocation handoff(String skillName, Map<String, Object> input) {
                frameworkHandoffs.incrementAndGet();
                return delegate.handoff(skillName, input);
            }
        };
        var coordinator = new ExecutionCoordinator(handoff, properties(), context,
                Clock.systemUTC(), task -> {
            if (hookCalls.incrementAndGet() == 1) return;
            handedOffTask.set(task);
            handoffEntered.countDown();
            try {
                handoffRelease.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        });
        var authentication = authentication("secret-token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var activeId = coordinator.admit("skill", Map.of("active", "value"), 1, owner, authentication);
            assertThat(activeEntered.await(2, TimeUnit.SECONDS)).isTrue();
            var id = coordinator.admit("skill", Map.of("secret", "value"), 10, owner, authentication);
            assertThat(coordinator.queuedCount()).isEqualTo(1);
            assertThat(coordinator.queuedBytes()).isEqualTo(10);

            activeRelease.countDown();
            awaitTerminal(coordinator, activeId, owner);
            assertThat(handoffEntered.await(2, TimeUnit.SECONDS)).isTrue();
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
            handoffRelease.countDown();
            for (int count = 0; count < 100 && coordinator.find(id, owner).isPresent(); count++) Thread.sleep(5);

            assertThat(invocations).hasValue(1);
            assertThat(frameworkHandoffs).hasValue(1);
            assertThat(coordinator.find(id, owner)).isEmpty();
            assertThat(coordinator.queuedCount()).isZero();
            assertThat(coordinator.queuedBytes()).isZero();
            assertThat(handedOffTask.get().referencesCleared()).isTrue();
            assertThatThrownBy(() -> coordinator.admit("skill", Map.of(), 1, owner, authentication))
                    .isInstanceOf(ExecutionUnavailableException.class);
        } finally {
            activeRelease.countDown();
            handoffRelease.countDown();
            coordinator.destroy();
        }
    }

    @Test
    void frameworkAdmissionClosingFirstFailsDequeuedWorkWithoutInvokingIt() throws Exception {
        var context = mock(ApplicationContext.class);
        SkillInvocationHandoff handoff = mock(SkillInvocationHandoff.class);
        when(handoff.handoff(anyString(), anyMap()))
                .thenThrow(new SkillException("Framework execution admission is closed."));
        var task = new AtomicReference<ExecutionCoordinator.ExecutionTask>();
        var coordinator = new ExecutionCoordinator(handoff, properties(), context,
                Clock.systemUTC(), task::set);
        var authentication = authentication("token", "owner");
        var owner = ExecutionOwner.from(authentication);
        try {
            var id = coordinator.admit("skill", Map.of("message", "value"), 5, owner, authentication);

            var terminal = awaitTerminal(coordinator, id, owner);

            assertThat(terminal.status()).isEqualTo(ExecutionStatus.FAILED);
            assertThat(terminal.failure()).isNotNull();
            assertThat(coordinator.find(id, owner)).isPresent();
            assertThat(task.get()).isNotNull();
            for (int count = 0; count < 100 && !task.get().referencesCleared(); count++) Thread.sleep(5);
            assertThat(task.get().referencesCleared()).isTrue();
            coordinator.onApplicationEvent(new ContextClosedEvent(context));
            assertThat(coordinator.queuedCount()).isZero();
            assertThat(coordinator.queuedBytes()).isZero();
        } finally {
            coordinator.destroy();
        }
    }

    private ExecutionSnapshot awaitTerminal(ExecutionCoordinator coordinator, java.util.UUID id,
            ExecutionOwner owner) throws InterruptedException {
        for (int count = 0; count < 200; count++) {
            var snapshot = coordinator.find(id, owner).orElseThrow();
            if (snapshot.completedAt() != null) return snapshot;
            Thread.sleep(5);
        }
        throw new AssertionError("execution did not terminate");
    }

    private SidecarExecutionProperties properties() {
        var properties = new SidecarExecutionProperties();
        properties.setMaxConcurrent(1);
        properties.setMaxQueued(1);
        properties.setMaxQueuedInputSize(DataSize.ofBytes(10));
        properties.setMaxRetained(2);
        return properties;
    }

    private JwtAuthenticationToken authentication(String tokenValue, String subject) {
        var jwt = Jwt.withTokenValue(tokenValue).header("alg", "none")
                .issuer("https://issuer.test").subject(subject)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
