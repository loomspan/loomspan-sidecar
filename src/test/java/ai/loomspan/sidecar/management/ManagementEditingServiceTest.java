package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagementEditingServiceTest {
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-18T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Test void exactCandidateAndLeaseLifecycleRemainSessionAndTabScoped() {
        var base = new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES), SnapshotStatus.PUBLISHED);
        var runtime = mock(RuntimeConfigurationService.class);
        when(runtime.withEditingState(anyBoolean(), any())).thenAnswer(call -> {
            Function<ConfigurationSnapshot, ?> operation = call.getArgument(1);
            return operation.apply(base);
        });
        var identity = mock(ManagementIdentityService.class);
        when(identity.account(1L)).thenReturn(new ManagementAccountRepository.Account(
                1, "editor@example.test", "editor", true, "hash", 1));
        var state = new ManagementEditingState();
        var clock = new MutableClock();
        var service = new ManagementEditingService(runtime, state, identity, new SidecarManagementProperties(), clock);
        var editor = new ManagementUserDetailsService.Principal(1, "editor@example.test", "editor", 1, "hash", List.of());
        var session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session");
        var foreign = mock(HttpSession.class);
        when(foreign.getId()).thenReturn("foreign");
        String tab = UUID.randomUUID().toString();
        String otherTab = UUID.randomUUID().toString();
        var first = service.acquire(session, editor, tab, false);
        assertThat(first.draft().configuration()).isEqualTo(base.configuration());
        assertThat(service.read(foreign, editor)).isNull();
        assertThatThrownBy(() -> service.save(session, editor, otherTab, first.grantId(),
                first.draft().candidateId(), base.configuration())).isInstanceOf(ManagementEditingService.Conflict.class);
        var entry = state.drafts.get("session");
        var oldCandidate = entry.draft.freeze();
        var replaced = service.save(session, editor, tab, first.grantId(), first.draft().candidateId(),
                base.configuration());
        assertThat(replaced.candidateId()).isNotEqualTo(first.draft().candidateId());
        assertThat(entry.draft.recordValidation(oldCandidate, new ConfigurationValidationResult(true, List.of()))).isFalse();
        assertThat(replaced.validation()).isNull();
        clock.advance(Duration.ofMinutes(15));
        assertThat(service.status(session, editor).held()).isFalse();
        assertThat(service.read(session, editor).draftId()).isEqualTo(first.draft().draftId());
        var resumed = service.acquire(session, editor, tab, false);
        assertThat(resumed.grantId()).isNotEqualTo(first.grantId());
        assertThat(resumed.draft().draftId()).isEqualTo(first.draft().draftId());
        var destroyed = mock(HttpSession.class);
        when(destroyed.getCreationTime()).thenThrow(new IllegalStateException("destroyed"));
        assertThatThrownBy(() -> service.read(destroyed, editor)).isInstanceOf(ManagementEditingService.Conflict.class);
        assertThatThrownBy(() -> service.save(destroyed, editor, tab, resumed.grantId(),
                replaced.candidateId(), base.configuration())).isInstanceOf(ManagementEditingService.Conflict.class);
        assertThatThrownBy(() -> service.save(session, editor, tab, first.grantId(),
                replaced.candidateId(), base.configuration())).isInstanceOf(ManagementEditingService.Conflict.class);
        service.release(session, editor, tab, resumed.grantId());
        service.discard(session, editor, null, null);
        assertThat(service.read(session, editor)).isNull();
    }

    @Test void lateValidationAfterEqualContentSaveCannotAttachOrReturnOldResult() throws Exception {
        var base = new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES), SnapshotStatus.PUBLISHED);
        var runtime = mock(RuntimeConfigurationService.class);
        when(runtime.withEditingState(anyBoolean(), any())).thenAnswer(call -> {
            Function<ConfigurationSnapshot, ?> operation = call.getArgument(1);
            return operation.apply(base);
        });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(runtime.validate(any(ManagedConfiguration.class))).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return new ConfigurationValidationResult(true, List.of());
        });
        var identity = mock(ManagementIdentityService.class);
        when(identity.account(1L)).thenReturn(new ManagementAccountRepository.Account(
                1, "editor@example.test", "editor", true, "hash", 1));
        var state = new ManagementEditingState();
        var clock = new MutableClock();
        var service = new ManagementEditingService(runtime, state, identity, new SidecarManagementProperties(), clock);
        var editor = new ManagementUserDetailsService.Principal(1, "editor@example.test", "editor", 1, "hash", List.of());
        var session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session");
        when(session.getAttribute(ManagementSessionGuard.ACTIVITY)).thenReturn(clock.millis());
        String tab = UUID.randomUUID().toString();
        var grant = service.acquire(session, editor, tab, false);
        UUID candidate = grant.draft().candidateId();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var checking = workers.submit(() -> service.validate(session, editor, tab, grant.grantId(), candidate));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var changed = service.save(session, editor, tab, grant.grantId(), candidate, base.configuration());
            release.countDown();
            assertThatThrownBy(() -> checking.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ManagementEditingService.Conflict.class);
            assertThat(changed.candidateId()).isNotEqualTo(candidate);
            assertThat(service.read(session, editor).validation()).isNull();
        } finally { release.countDown(); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "discard", "destroy", "expire"})
    void lateValidationAfterOwnershipLossCannotAttach(String loss) throws Exception {
        var base = new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES), SnapshotStatus.PUBLISHED);
        var runtime = mock(RuntimeConfigurationService.class);
        when(runtime.withEditingState(anyBoolean(), any())).thenAnswer(call -> {
            Function<ConfigurationSnapshot, ?> operation = call.getArgument(1);
            return operation.apply(base);
        });
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(runtime.validate(any(ManagedConfiguration.class))).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return new ConfigurationValidationResult(true, List.of());
        });
        var identity = mock(ManagementIdentityService.class);
        when(identity.account(1L)).thenReturn(new ManagementAccountRepository.Account(
                1, "editor@example.test", "editor", true, "hash", 1));
        var state = new ManagementEditingState();
        var clock = new MutableClock();
        var service = new ManagementEditingService(runtime, state, identity, new SidecarManagementProperties(), clock);
        var editor = new ManagementUserDetailsService.Principal(1, "editor@example.test", "editor", 1, "hash", List.of());
        var session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session");
        when(session.getAttribute(ManagementSessionGuard.ACTIVITY)).thenReturn(clock.millis());
        String tab = UUID.randomUUID().toString();
        var grant = service.acquire(session, editor, tab, false);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var checking = workers.submit(() -> service.validate(session, editor, tab,
                    grant.grantId(), grant.draft().candidateId()));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            switch (loss) {
                case "release" -> service.release(session, editor, tab, grant.grantId());
                case "discard" -> service.discard(session, editor, tab, grant.grantId());
                case "destroy" -> when(session.getCreationTime()).thenThrow(new IllegalStateException("destroyed"));
                case "expire" -> clock.advance(Duration.ofMinutes(16));
                default -> throw new AssertionError(loss);
            }
            release.countDown();
            assertThatThrownBy(() -> checking.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ManagementEditingService.Conflict.class);
            if (!"discard".equals(loss) && !"destroy".equals(loss))
                assertThat(service.read(session, editor).validation()).isNull();
        } finally { release.countDown(); }
    }
}
