package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationDraftStore;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagementEditingServiceTest {
    @Test void lateValidationCannotAttachAfterSavedRevisionChanges() throws Exception {
        var base = new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES), SnapshotStatus.PUBLISHED);
        var runtime = mock(RuntimeConfigurationService.class);
        when(runtime.withEditingState(anyBoolean(), any())).thenAnswer(call -> {
            Function<ConfigurationSnapshot, ?> action = call.getArgument(1); return action.apply(base);
        });
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(runtime.validate(any(ManagedConfiguration.class))).thenAnswer(call -> {
            entered.countDown(); assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            return new ConfigurationValidationResult(true, List.of());
        });
        var identity = mock(ManagementIdentityService.class);
        when(identity.account(1L)).thenReturn(new ManagementAccountRepository.Account(
                1, "editor@example.test", "editor", true, "hash", 1));
        var store = mock(ConfigurationDraftStore.class);
        var saved = new AtomicReference<>(new ConfigurationDraftStore.Saved(1, UUID.randomUUID(), base.localId(), null,
                1, base.configuration()));
        when(store.createIfAbsent(eq(1L), any())).thenAnswer(call -> saved.get());
        when(store.read(1L)).thenAnswer(call -> saved.get());
        when(store.replace(eq(1L), any(), anyLong(), any(), any(), any(), any())).thenAnswer(call -> {
            var old = saved.get();
            var next = new ConfigurationDraftStore.Saved(1, old.draftId(), old.baseSnapshotId(), null,
                    old.revision() + 1, call.getArgument(5));
            saved.set(next); return next;
        });
        var service = new ManagementEditingService(runtime, new ManagementEditingState(), store, identity,
                new SidecarManagementProperties(), Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), java.time.ZoneOffset.UTC));
        var user = new ManagementUserDetailsService.Principal(1, "editor@example.test", "editor", 1, "hash", List.of());
        var session = mock(HttpSession.class); when(session.getId()).thenReturn("session");
        when(session.getAttribute(ManagementSessionGuard.ACTIVITY)).thenReturn(Instant.parse("2026-09-18T00:00:00Z").toEpochMilli());
        var grant = service.acquire(session, user, "Console", false, false);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var validation = workers.submit(() -> service.validate(session, user, grant.editingSessionId(),
                    grant.generation(), saved.get().draftId(), saved.get().revision(), base.localId()));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            service.save(session, user, grant.editingSessionId(), grant.generation(), saved.get().draftId(),
                    saved.get().revision(), base.localId(), base.configuration());
            release.countDown();
            assertThatThrownBy(() -> validation.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(ManagementEditingService.Conflict.class);
        } finally { release.countDown(); }
        assertThat(service.read(session, user).validation()).isNull();
    }
}
