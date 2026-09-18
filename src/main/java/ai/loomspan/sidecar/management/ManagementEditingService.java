package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.FrozenConfigurationCandidate;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ManagementEditingService {
    public static final class Conflict extends RuntimeException {
        private final String code;
        public Conflict() { this("editing_conflict"); }
        public Conflict(String code) {
            super("Editing state changed or is unavailable");
            this.code = code;
        }
        public String code() { return code; }
    }
    public record Status(boolean held, boolean mine, Instant expiresAt) {}
    public record Grant(UUID grantId, Instant expiresAt, Draft draft) {}
    public record Draft(UUID draftId, UUID candidateId, UUID baseSnapshotId,
            ManagedConfiguration configuration, ConfigurationValidationResult validation) {}

    private final RuntimeConfigurationService runtime;
    private final ManagementEditingState state;
    private final ManagementIdentityService identity;
    private final SidecarManagementProperties settings;
    private final Clock clock;

    public ManagementEditingService(RuntimeConfigurationService runtime, ManagementEditingState state,
            ManagementIdentityService identity, SidecarManagementProperties settings, Clock clock) {
        this.runtime = runtime;
        this.state = state;
        this.identity = identity;
        this.settings = settings;
        this.clock = clock;
    }

    public Status status(HttpSession session, ManagementUserDetailsService.Principal user) {
        return runtime.withEditingState(false, base -> {
            synchronized (state) {
                String sessionId = active(session);
                checkAccount(user);
                expire();
                var lease = state.lease;
                return new Status(lease != null, lease != null && lease.sessionId.equals(sessionId),
                        lease == null ? null : at(lease.expiresAt));
            }
        });
    }

    public Draft read(HttpSession session, ManagementUserDetailsService.Principal user) {
        return runtime.withEditingState(false, base -> {
            synchronized (state) {
                String sessionId = active(session);
                checkAccount(user);
                var entry = eligible(sessionId, base.localId());
                return entry == null ? null : view(entry);
            }
        });
    }

    public Grant acquire(HttpSession session, ManagementUserDetailsService.Principal user, String tabId, boolean takeover) {
        requireTab(tabId);
        return mutation(base -> {
            String sessionId = active(session);
            checkAccount(user);
            if (takeover && !"admin".equals(user.role())) throw new Conflict();
            if (!takeover && "viewer".equals(user.role())) throw new Conflict();
            expire();
            if (!takeover && state.lease != null) throw new Conflict();
            ManagementEditingState.Entry entry = takeover ? null : eligible(sessionId, base.localId());
            if (entry == null) {
                entry = new ManagementEditingState.Entry(user.id(), new ConfigurationDraft(base));
                state.drafts.put(sessionId, entry);
            }
            state.lease = new ManagementEditingState.Lease(sessionId, tabId,
                    clock.millis() + settings.getEditLeaseTimeout().toMillis());
            return grant(state.lease, entry);
        });
    }

    public Grant activity(HttpSession session, ManagementUserDetailsService.Principal user,
            String tabId, UUID grantId, long lastReportAt) {
        return mutation(base -> {
            String sessionId = active(session);
            checkAccount(user);
            var entry = eligible(sessionId, base.localId());
            var lease = ownLease(sessionId, tabId, grantId);
            if (entry == null) throw new Conflict();
            if (clock.millis() - lastReportAt >= 30_000L)
                lease.expiresAt = clock.millis() + settings.getEditLeaseTimeout().toMillis();
            return grant(lease, entry);
        });
    }

    public Draft save(HttpSession session, ManagementUserDetailsService.Principal user, String tabId,
            UUID grantId, UUID expectedCandidateId, ManagedConfiguration configuration) {
        if (configuration == null || expectedCandidateId == null) throw new IllegalArgumentException("Missing candidate");
        return mutation(base -> {
            String sessionId = active(session);
            checkAccount(user);
            ownLease(sessionId, tabId, grantId);
            var entry = eligible(sessionId, base.localId());
            if (entry == null || !entry.candidateId.equals(expectedCandidateId))
                throw new Conflict("candidate_conflict");
            entry.draft.replaceContent(configuration);
            entry.candidateId = UUID.randomUUID();
            return view(entry);
        });
    }

    public void release(HttpSession session, ManagementUserDetailsService.Principal user, String tabId, UUID grantId) {
        runtime.withEditingState(false, base -> {
            synchronized (state) {
                String sessionId = active(session);
                checkAccount(user);
                ownLease(sessionId, tabId, grantId);
                state.lease = null;
                return null;
            }
        });
    }

    public void discard(HttpSession session, ManagementUserDetailsService.Principal user,
            String tabId, UUID grantId) {
        runtime.withEditingState(false, base -> {
            synchronized (state) {
                String sessionId = active(session);
                checkAccount(user);
                expire();
                if (state.lease != null && state.lease.sessionId.equals(sessionId))
                    ownLease(sessionId, tabId, grantId);
                state.clearSession(sessionId);
                return null;
            }
        });
    }

    public Draft validate(HttpSession session, ManagementUserDetailsService.Principal user,
            String tabId, UUID grantId, UUID expectedCandidateId) {
        var captured = runtime.withEditingState(true, base -> {
            synchronized (state) {
                var entry = authorized(session, user, tabId, grantId, expectedCandidateId, base);
                return new ValidationWork(entry, entry.candidateId, entry.draft.freeze());
            }
        });
        ConfigurationValidationResult result = runtime.validate(captured.candidate.configuration());
        return runtime.withEditingState(true, base -> {
            synchronized (state) {
                var entry = authorized(session, user, tabId, grantId, expectedCandidateId, base);
                if (entry != captured.entry || !entry.candidateId.equals(captured.candidateId)
                        || entry.draft.freeze() != captured.candidate) throw new Conflict();
                entry.draft.recordValidation(captured.candidate, result);
                return view(entry);
            }
        });
    }

    public ConfigurationSnapshot publish(HttpSession session, ManagementUserDetailsService.Principal user,
            String tabId, UUID grantId, UUID expectedCandidateId) {
        return runtime.publish(() -> runtime.withEditingState(true, base -> {
            synchronized (state) {
                var entry = authorized(session, user, tabId, grantId, expectedCandidateId, base);
                try { return entry.draft.validatedCandidate(); }
                catch (IllegalStateException missing) { throw new Conflict("validation_required"); }
            }
        }));
    }

    private record ValidationWork(ManagementEditingState.Entry entry, UUID candidateId,
            FrozenConfigurationCandidate candidate) {}

    private ManagementEditingState.Entry authorized(HttpSession session, ManagementUserDetailsService.Principal user,
            String tabId, UUID grantId, UUID expectedCandidateId, ConfigurationSnapshot base) {
        String sessionId = activeForAdmission(session);
        checkAccount(user);
        if ("viewer".equals(user.role())) throw new Conflict("role_conflict");
        ownLease(sessionId, tabId, grantId);
        var existing = state.drafts.get(sessionId);
        if (existing != null && !existing.draft.baseSnapshotId().equals(base.localId())) {
            state.clearSession(sessionId);
            throw new Conflict("base_conflict");
        }
        var entry = eligible(sessionId, base.localId());
        if (entry == null || expectedCandidateId == null || !entry.candidateId.equals(expectedCandidateId))
            throw new Conflict("candidate_conflict");
        return entry;
    }

    private <T> T mutation(java.util.function.Function<ai.loomspan.sidecar.storage.ConfigurationSnapshot, T> action) {
        return runtime.withEditingState(true, base -> {
            synchronized (state) { return action.apply(base); }
        });
    }

    private void checkAccount(ManagementUserDetailsService.Principal user) {
        var account = identity.account(user.id());
        if (account == null || !account.active() || account.version() != user.version()
                || !account.role().equals(user.role())) throw new Conflict("account_conflict");
    }

    private static String active(HttpSession session) {
        try {
            session.getCreationTime();
            return session.getId();
        }
        catch (IllegalStateException invalidated) { throw new Conflict(); }
    }

    private String activeForAdmission(HttpSession session) {
        String sessionId = active(session);
        try {
            Long activity = (Long) session.getAttribute(ManagementSessionGuard.ACTIVITY);
            if (activity == null || clock.millis() - activity >= settings.getSessionIdleTimeout().toMillis())
                throw new Conflict("session_conflict");
            return sessionId;
        } catch (IllegalStateException invalidated) { throw new Conflict(); }
    }

    private void expire() {
        if (state.lease != null && clock.millis() >= state.lease.expiresAt) state.lease = null;
    }

    private ManagementEditingState.Entry eligible(String sessionId, UUID baseId) {
        var entry = state.drafts.get(sessionId);
        if (entry != null && !entry.draft.baseSnapshotId().equals(baseId)) {
            state.clearSession(sessionId);
            return null;
        }
        return entry;
    }

    private ManagementEditingState.Lease ownLease(String sessionId, String tabId, UUID grantId) {
        requireTab(tabId);
        expire();
        var lease = state.lease;
        if (lease == null || grantId == null || !lease.sessionId.equals(sessionId)
                || !lease.tabId.equals(tabId) || !lease.grantId.equals(grantId)) throw new Conflict("grant_conflict");
        return lease;
    }

    private static void requireTab(String tabId) {
        try { UUID.fromString(tabId); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("Invalid tab ID"); }
    }

    private static Instant at(long millis) { return Instant.ofEpochMilli(millis); }
    private static Grant grant(ManagementEditingState.Lease lease, ManagementEditingState.Entry entry) {
        return new Grant(lease.grantId, at(lease.expiresAt), view(entry));
    }
    private static Draft view(ManagementEditingState.Entry entry) {
        var candidate = entry.draft.freeze();
        return new Draft(entry.draftId, entry.candidateId, entry.draft.baseSnapshotId(),
                candidate.configuration(), entry.draft.validationFor(candidate));
    }
}
