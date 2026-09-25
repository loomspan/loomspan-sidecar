package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationDraftStore;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public final class ManagementEditingService {
    private static final Logger audit = LoggerFactory.getLogger("sidecar.management.audit");
    public static final class Conflict extends RuntimeException {
        private final String code;
        public Conflict(String code) { super("Editing state changed or is unavailable"); this.code = code; }
        public String code() { return code; }
    }
    public record Status(boolean held, boolean mine, boolean sameUser, String holderLabel, Instant expiresAt,
            UUID editingSessionId) {}
    public record Grant(UUID editingSessionId, UUID generation, Instant expiresAt, Draft draft) {}
    public record Draft(UUID draftId, long revision, UUID baseSnapshotId, UUID sourceSnapshotId,
            ManagedConfiguration configuration, boolean stale, ConfigurationValidationResult validation) {}

    private final RuntimeConfigurationService runtime;
    private final ManagementEditingState state;
    private final ConfigurationDraftStore drafts;
    private final ManagementIdentityService identity;
    private final ManagementPersonalTokenService tokens;
    private final SidecarManagementProperties settings;
    private final Clock clock;

    public ManagementEditingService(RuntimeConfigurationService runtime, ManagementEditingState state,
            ConfigurationDraftStore drafts, ManagementIdentityService identity,
            ManagementPersonalTokenService tokens, SidecarManagementProperties settings, Clock clock) {
        this.runtime = runtime; this.state = state; this.drafts = drafts; this.identity = identity;
        this.tokens = tokens; this.settings = settings; this.clock = clock;
    }

    public Status status(HttpSession session, ManagementUserDetailsService.Principal user) {
        return runtime.withEditingState(false, base -> {
            synchronized (state) {
                String id = active(session); checkAccount(user); expire();
                var lease = state.lease;
                return new Status(lease != null, lease != null && lease.origin.equals(id),
                        lease != null && lease.accountId == user.id(), lease == null ? null : lease.label,
                        lease == null ? null : at(lease.expiresAt),
                        lease != null && lease.origin.equals(id) ? lease.editingSessionId : null);
            }
        });
    }

    public Draft read(HttpSession session, ManagementUserDetailsService.Principal user) {
        return runtime.withEditingState(false, base -> {
            synchronized (state) {
                active(session); checkAccount(user); expire();
                return view(drafts.read(user.id()), base);
            }
        });
    }

    public Grant acquire(HttpSession session, ManagementUserDetailsService.Principal user,
            String label, boolean takeover, boolean handoff) {
        var grant = mutation(base -> {
            String id = activeForAdmission(session); checkEditor(user); expire();
            if (takeover && (ManagementCredential.current() != null || !"admin".equals(user.role()))) throw new Conflict("role_conflict");
            if (!takeover && state.lease != null && (!handoff || state.lease.accountId != user.id()))
                throw new Conflict("grant_conflict");
            if (handoff && state.lease == null) throw new Conflict("grant_conflict");
            var saved = drafts.createIfAbsent(user.id(), base);
            state.clearLease();
            state.lease = new ManagementEditingState.Lease(user.id(), id,
                    label == null || label.isBlank() ? "Editor" : label.substring(0, Math.min(label.length(), 80)),
                    clock.millis() + settings.getEditLeaseTimeout().toMillis());
            return grant(state.lease, view(saved, base));
        });
        audited(takeover ? "lease.takeover" : handoff ? "lease.handoff" : "lease.acquire", user,
                grant.draft().draftId(), grant.draft().baseSnapshotId(), null);
        return grant;
    }

    public Grant renew(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation) {
        return mutation(base -> {
            var lease = ownLease(session, user, editingSessionId, generation);
            lease.expiresAt = clock.millis() + settings.getEditLeaseTimeout().toMillis();
            return grant(lease, view(drafts.read(user.id()), base));
        });
    }

    public void release(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation) {
        transition(false, base -> { ownLease(session, user, editingSessionId, generation); state.clearLease(); return null; });
        audited("lease.release", user, null, null, null);
    }

    public Draft save(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID expectedBaseId,
            ManagedConfiguration configuration) {
        var saved = replace(session, user, editingSessionId, generation, draftId, revision, expectedBaseId,
                configuration, null, false);
        audited("draft.save", user, saved.draftId(), saved.baseSnapshotId(), null);
        return saved;
    }

    public Draft reconcile(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID currentBaseId,
            ManagedConfiguration configuration) {
        var saved = replace(session, user, editingSessionId, generation, draftId, revision, currentBaseId,
                configuration, null, true);
        audited("draft.reconcile", user, saved.draftId(), saved.baseSnapshotId(), null);
        return saved;
    }

    public Draft load(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID currentBaseId,
            ManagedConfiguration configuration, UUID sourceId) {
        var saved = replace(session, user, editingSessionId, generation, draftId, revision, currentBaseId,
                configuration, sourceId, true);
        audited("draft.load", user, saved.draftId(), saved.baseSnapshotId(), null);
        return saved;
    }

    private Draft replace(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseId,
            ManagedConfiguration configuration, UUID sourceId, boolean rebase) {
        if (configuration == null || draftId == null || baseId == null || revision < 1)
            throw new IllegalArgumentException("Missing complete draft submission");
        return mutation(base -> {
            ownLease(session, user, editingSessionId, generation);
            var saved = requireDraft(user.id(), draftId, revision);
            if (!base.localId().equals(baseId)) throw new Conflict("base_conflict");
            if (!rebase && !saved.baseSnapshotId().equals(baseId)) throw new Conflict("base_conflict");
            try {
                saved = drafts.replace(user.id(), draftId, revision, saved.baseSnapshotId(), baseId,
                        configuration, sourceId);
            } catch (IllegalStateException changed) { throw new Conflict("revision_conflict"); }
            state.validation = null;
            return view(saved, base);
        });
    }

    public void discard(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation, UUID draftId, long revision) {
        transition(false, base -> {
            ownLease(session, user, editingSessionId, generation);
            if (!drafts.delete(user.id(), draftId, revision)) throw new Conflict("revision_conflict");
            state.clearLease(); return null;
        });
        audited("draft.discard", user, draftId, null, null);
    }

    public Draft validate(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseId) {
        var captured = mutation(base -> {
            ownLease(session, user, editingSessionId, generation);
            var saved = exact(user.id(), draftId, revision, baseId, base);
            return saved;
        });
        ConfigurationValidationResult result = runtime.validate(captured.configuration());
        var validated = mutation(base -> {
            ownLease(session, user, editingSessionId, generation);
            var saved = exact(user.id(), draftId, revision, baseId, base);
            if (!saved.equals(captured)) throw new Conflict("revision_conflict");
            state.validation = new ManagementEditingState.Validation(draftId, revision, baseId, generation, result);
            return view(saved, base);
        });
        var credential = ManagementCredential.current();
        audit.info("action=draft.validate actor={} token={} candidate={} base={} outcome={}",
                user.id(), credential == null ? "browser" : credential.tokenId(), draftId, baseId,
                result.successful() ? "valid" : "invalid");
        return validated;
    }

    public ConfigurationSnapshot publish(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseId) {
        try {
            var published = runtime.publish(() -> runtime.withEditingState(true, base -> {
                synchronized (state) {
                    ownLease(session, user, editingSessionId, generation);
                    checkPublish(user);
                    var saved = exact(user.id(), draftId, revision, baseId, base);
                    var validated = state.validation;
                    if (validated == null || !validated.draftId().equals(draftId)
                            || validated.revision() != revision || !validated.baseId().equals(baseId)
                            || !validated.generation().equals(generation) || !validated.result().successful())
                        throw new Conflict("validation_required");
                    return new RuntimeConfigurationService.PublicationAdmission(saved.accountId(), draftId,
                            revision, saved.configuration(), saved.sourceSnapshotId(), baseId);
                }
            }));
            audited("configuration.publish", user, draftId, baseId, published.localId());
            return published;
        } catch (RuntimeException failure) {
            var credential = ManagementCredential.current();
            String outcome = "denied";
            UUID published = null;
            if (failure instanceof RuntimeConfigurationService.PublicationFailure fault) {
                outcome = switch (fault.code()) {
                    case "draft_cleanup_failed", "history_pruning_failed", "outcome_recording_failed" -> "activated_fault";
                    default -> "failed";
                };
                if ("activated_fault".equals(outcome)) published = runtime.inspect().publishedId();
            }
            audit.info("action=configuration.publish actor={} token={} candidate={} base={} published={} outcome={} failure={}",
                    user.id(), credential == null ? "browser" : credential.tokenId(), draftId, baseId, published,
                    outcome, failure instanceof Conflict conflict ? conflict.code()
                            : failure instanceof RuntimeConfigurationService.PublicationFailure fault ? fault.code()
                            : failure.getClass().getSimpleName());
            throw failure;
        }
    }

    private ConfigurationDraftStore.Saved exact(long accountId, UUID draftId, long revision,
            UUID baseId, ConfigurationSnapshot base) {
        var saved = requireDraft(accountId, draftId, revision);
        if (!saved.baseSnapshotId().equals(baseId) || !base.localId().equals(baseId))
            throw new Conflict("base_conflict");
        return saved;
    }

    private ConfigurationDraftStore.Saved requireDraft(long accountId, UUID draftId, long revision) {
        var saved = drafts.read(accountId);
        if (saved == null || !saved.draftId().equals(draftId) || saved.revision() != revision)
            throw new Conflict("revision_conflict");
        return saved;
    }

    private ManagementEditingState.Lease ownLease(HttpSession session,
            ManagementUserDetailsService.Principal user, UUID editingSessionId, UUID generation) {
        String id = activeForAdmission(session); checkEditor(user); expire();
        var lease = state.lease;
        if (lease == null || editingSessionId == null || generation == null
                || !lease.origin.equals(id) || lease.accountId != user.id()
                || !lease.editingSessionId.equals(editingSessionId) || !lease.generation.equals(generation))
            throw new Conflict("grant_conflict");
        return lease;
    }

    private void expire() {
        if (state.lease == null) return;
        var lease = state.lease;
        if (clock.millis() >= lease.expiresAt || lease.origin.startsWith("pat:")
                && !tokens.valid(lease.origin.substring(4), lease.accountId, "edit")) state.clearLease();
    }

    private void checkEditor(ManagementUserDetailsService.Principal user) {
        checkAccount(user);
        if ("viewer".equals(user.role())) throw new Conflict("role_conflict");
        var credential = ManagementCredential.current();
        if (credential != null && !tokens.valid(credential.tokenId(), user.id(), "edit"))
            throw new Conflict("credential_conflict");
    }

    private void checkPublish(ManagementUserDetailsService.Principal user) {
        var credential = ManagementCredential.current();
        if (credential != null && !tokens.valid(credential.tokenId(), user.id(), "publish"))
            throw new Conflict("credential_conflict");
    }

    private void checkAccount(ManagementUserDetailsService.Principal user) {
        var account = identity.account(user.id());
        if (account == null || !account.active() || account.version() != user.version()
                || !account.role().equals(user.role())) throw new Conflict("account_conflict");
    }

    private static String active(HttpSession session) {
        var credential = ManagementCredential.current();
        if (credential != null) return "pat:" + credential.tokenId();
        if (session == null) throw new Conflict("session_conflict");
        try { session.getCreationTime(); return "browser:" + session.getId(); }
        catch (IllegalStateException invalidated) { throw new Conflict("session_conflict"); }
    }

    private String activeForAdmission(HttpSession session) {
        String id = active(session);
        if (ManagementCredential.current() != null) return id;
        try {
            Long activity = (Long) session.getAttribute(ManagementSessionGuard.ACTIVITY);
            if (activity == null || clock.millis() - activity >= settings.getSessionIdleTimeout().toMillis())
                throw new Conflict("session_conflict");
            return id;
        } catch (IllegalStateException invalidated) { throw new Conflict("session_conflict"); }
    }

    private <T> T mutation(java.util.function.Function<ConfigurationSnapshot, T> action) {
        return transition(true, action);
    }

    private <T> T transition(boolean mutating, java.util.function.Function<ConfigurationSnapshot, T> action) {
        return runtime.withEditingState(mutating, base -> { synchronized (state) { return action.apply(base); } });
    }

    private Draft view(ConfigurationDraftStore.Saved saved, ConfigurationSnapshot base) {
        if (saved == null) return null;
        var proof = state.validation;
        ConfigurationValidationResult validation = proof != null && proof.draftId().equals(saved.draftId())
                && proof.revision() == saved.revision() && proof.baseId().equals(saved.baseSnapshotId())
                && saved.baseSnapshotId().equals(base.localId())
                && state.lease != null && proof.generation().equals(state.lease.generation)
                ? proof.result() : null;
        return new Draft(saved.draftId(), saved.revision(), saved.baseSnapshotId(), saved.sourceSnapshotId(),
                saved.configuration(), !saved.baseSnapshotId().equals(base.localId()), validation);
    }

    private static Instant at(long millis) { return Instant.ofEpochMilli(millis); }
    private static void audited(String action, ManagementUserDetailsService.Principal user,
            UUID candidate, UUID base, UUID published) {
        var credential = ManagementCredential.current();
        audit.info("action={} actor={} token={} candidate={} base={} published={} outcome=success", action,
                user.id(), credential == null ? "browser" : credential.tokenId(), candidate, base, published);
    }
    private static Grant grant(ManagementEditingState.Lease lease, Draft draft) {
        return new Grant(lease.editingSessionId, lease.generation, at(lease.expiresAt), draft);
    }
}
