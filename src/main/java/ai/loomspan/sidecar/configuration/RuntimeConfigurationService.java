package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillValidationIssue;
import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import ai.loomspan.sidecar.management.ManagementEditingState;
import ai.loomspan.sidecar.rest.GenerationRestResources;
import ai.loomspan.sidecar.rest.RestRouteCatalogValidator;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ConfigurationValidationIssue;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import ai.loomspan.sidecar.storage.FrozenConfigurationCandidate;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes durable selection with framework publication; the database pointer is restart authority. */
@Component
public final class RuntimeConfigurationService implements ApplicationRunner, AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RuntimeConfigurationService.class);
    public record Inspection(UUID publishedId, UUID intendedId, SnapshotStatus intendedStatus, String mutationFault) {}
    public record Current(ConfigurationSnapshot published, UUID intendedId,
            SnapshotStatus intendedStatus, String mutationFault) {}
    public static final class PublicationFailure extends IllegalStateException {
        private final String code;
        public PublicationFailure(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }
    interface Hooks {
        default void beforeStartupActivation() {}
        default void beforePreparation() {}
        default void beforeCommit() {}
        default void beforeFrameworkPublish() {}
        default void beforeRevert() {}
        default void beforeStatus() {}
    }

    private final ConfigurationSnapshotStore store;
    private final SkillReloader reloader;
    private final GenerationRestResources resources;
    private final RestRouteCatalogValidator routes;
    private final ExecutionCoordinator executions;
    private final ManagementEditingState editing;
    private final ReentrantLock publication = new ReentrantLock(true);
    private final ReentrantLock transition = new ReentrantLock(true);
    private volatile ConfigurationSnapshot published;
    private volatile UUID intendedId;
    private volatile SnapshotStatus intendedStatus;
    private volatile String mutationFault;
    private AutoCloseable retirement;
    private volatile Hooks hooks = new Hooks() {};

    public RuntimeConfigurationService(ConfigurationSnapshotStore store, SkillReloader reloader,
            GenerationRestResources resources, RestRouteCatalogValidator routes, ExecutionCoordinator executions,
            ManagementEditingState editing) {
        this.store = store;
        this.reloader = reloader;
        this.resources = resources;
        this.routes = routes;
        this.executions = executions;
        this.editing = editing;
    }

    void hooks(Hooks hooks) { this.hooks = java.util.Objects.requireNonNull(hooks); }
    int queuedPublications() { return publication.getQueueLength(); }

    @Override
    public void run(ApplicationArguments args) {
        publication.lock();
        try {
            if (!reloader.snapshot().skills().isEmpty())
                throw new IllegalStateException("Framework startup catalog must be empty before database activation");
            retirement = reloader.onGenerationRetired(resources::retire);
            ConfigurationSnapshot selected = store.current();
            intendedId = selected.localId();
            intendedStatus = selected.status();
            String generation = null;
            String phase = "skill preparation";
            try {
                hooks.beforeStartupActivation();
                PreparedSkillUpdate prepared = prepare(selected.configuration());
                phase = "REST staging";
                GenerationRestResources.Resources staged = stage(prepared, selected.configuration());
                generation = prepared.generationId();
                resources.stage(generation, staged, selected.localId());
                phase = "framework publication";
                reloader.publish(prepared);
                published = selected;
                phase = "status recording";
                store.updateStatus(selected.localId(), SnapshotStatus.PUBLISHED);
                intendedStatus = SnapshotStatus.PUBLISHED;
                phase = "history pruning";
                prune();
                executions.openAfterActivation();
            } catch (RuntimeException failure) {
                if (published == null && generation != null) resources.discard(generation);
                ConfigurationValidationIssue issue = switch (phase) {
                    case "skill preparation" -> skillValidationIssue(failure, selected.configuration());
                    case "REST staging" -> validationIssue(failure);
                    default -> null;
                };
                String location = issue == null ? "" : " at " + issue.sourceLabel()
                        + (issue.location() == null ? "" : " [" + issue.location() + "]");
                LOG.error("Selected configuration {} failed during {}{}", selected.localId(), phase, location);
                throw new IllegalStateException("Selected configuration activation or bookkeeping failed for "
                        + selected.localId() + " during " + phase + location
                        + "; inspect the database selection and restore a consistent backup if needed");
            }
        } finally {
            publication.unlock();
        }
    }

    public ConfigurationValidationResult validate(ConfigurationDraft draft) {
        FrozenConfigurationCandidate candidate = draft.freeze();
        ConfigurationValidationResult result = validate(candidate.configuration());
        draft.recordValidation(candidate, result);
        return result;
    }

    public ConfigurationValidationResult validate(ManagedConfiguration configuration) {
        var checked = reloader.validate(configuration.skillDocuments());
        var issues = new java.util.ArrayList<ConfigurationValidationIssue>();
        checked.issues().forEach(issue -> issues.add(new ConfigurationValidationIssue(
                issue.severity() == SkillValidationIssue.Severity.WARNING
                        ? ConfigurationValidationIssue.Severity.WARNING : ConfigurationValidationIssue.Severity.ERROR,
                issue.sourceName(), issue.skillName(), issue.fieldPath(), issue.message())));
        if (!checked.valid()) return new ConfigurationValidationResult(false, issues);
        GenerationRestResources.Resources staged = null;
        try {
            staged = resources.prepare(configuration.restRoutesYaml());
            routes.validateCandidate(staged.routes(), checked.skills());
        } catch (RuntimeException failure) {
            issues.add(validationIssue(failure));
        } finally {
            if (staged != null) resources.release(staged);
        }
        return new ConfigurationValidationResult(issues.stream().noneMatch(issue ->
                issue.severity() == ConfigurationValidationIssue.Severity.ERROR), issues);
    }

    /** The callback runs after acquiring the publication lock, at the admission point. */
    public ConfigurationSnapshot publish(java.util.function.Supplier<ConfigurationDraft.ValidatedCandidate> admission) {
        publication.lock();
        try {
            requireHealthy();
            FrozenConfigurationCandidate candidate = admission.get().candidate();
            UUID predecessor = published == null ? null : published.localId();
            if (predecessor == null || !predecessor.equals(candidate.baseSnapshotId()))
                throw new IllegalStateException("Draft is based on a stale runtime snapshot");

            PreparedSkillUpdate prepared;
            GenerationRestResources.Resources staged;
            try {
                hooks.beforePreparation();
                prepared = prepare(candidate.configuration());
                staged = stage(prepared, candidate.configuration());
            } catch (RuntimeException failure) {
                throw new PublicationFailure("preparation_failed", "Configuration preparation failed before activation");
            }
            String generation = prepared.generationId();
            ConfigurationSnapshot submitted;
            try {
                resources.stage(generation, staged, null);
                hooks.beforeCommit();
                submitted = store.submit(candidate.configuration(), null, predecessor);
            } catch (RuntimeException failure) {
                resources.discard(generation);
                pruneAfterAttempt();
                LOG.warn("Configuration commit failed before framework publication; runtime remains {}", predecessor);
                throw new PublicationFailure("commit_failed", "Configuration commit failed before runtime publication");
            }
            intendedId = submitted.localId();
            intendedStatus = SnapshotStatus.PENDING;

            try {
                resources.bind(generation, submitted.localId());
                hooks.beforeFrameworkPublish();
                transition.lock();
                try {
                    reloader.publish(prepared);
                    published = submitted;
                    synchronized (editing) { editing.clearAll(); }
                } finally {
                    transition.unlock();
                }
            } catch (RuntimeException failure) {
                resources.discard(generation);
                try {
                    hooks.beforeRevert();
                    store.revert(submitted.localId(), predecessor);
                    intendedId = predecessor;
                    intendedStatus = SnapshotStatus.PUBLISHED;
                } catch (RuntimeException revertFailure) {
                    mutationFault = "Publication failed and intended selection could not be reverted";
                    LOG.error("Configuration mutation fault: runtime {}, intended {} could not be reverted",
                            predecessor, submitted.localId());
                }
                pruneAfterAttempt();
                if (mutationFault == null) LOG.warn("Configuration {} publication failed; intended selection restored to {}",
                        submitted.localId(), predecessor);
                throw new PublicationFailure(mutationFault == null ? "activation_failed" : "revert_failed",
                        mutationFault == null ? "Configuration publication failed; intended selection reverted" : mutationFault);
            }

            boolean statusRecorded = false;
            try {
                hooks.beforeStatus();
                store.updateStatus(submitted.localId(), SnapshotStatus.PUBLISHED);
                intendedStatus = SnapshotStatus.PUBLISHED;
                statusRecorded = true;
            } catch (RuntimeException failure) {
                mutationFault = "Published configuration outcome could not be recorded";
                LOG.error("Configuration mutation fault: runtime {} published but outcome was not recorded",
                        submitted.localId());
            }
            pruneAfterAttempt();
            if (mutationFault != null) throw new PublicationFailure(
                    statusRecorded ? "history_pruning_failed" : "outcome_recording_failed", mutationFault);
            return new ConfigurationSnapshot(submitted.localId(), submitted.sourceId(),
                    submitted.submissionSequence(), submitted.configuration(), SnapshotStatus.PUBLISHED);
        } finally {
            publication.unlock();
        }
    }

    public Inspection inspect() {
        publication.lock();
        try { return inspectLocked(); }
        finally { publication.unlock(); }
    }

    public Current current() {
        publication.lock();
        try {
            Inspection inspected = inspectLocked();
            ConfigurationSnapshot runtime = published;
            if (runtime == null) throw new IllegalStateException("Runtime configuration is unavailable");
            ConfigurationSnapshot stored;
            try { stored = store.findByLocalId(runtime.localId()); }
            catch (RuntimeException failure) {
                mutationFault = mutationFault == null ? "Configuration selection could not be inspected" : mutationFault;
                stored = null;
            }
            return new Current(stored == null ? runtime : stored,
                    inspected.intendedId(), inspected.intendedStatus(), mutationFault);
        } finally { publication.unlock(); }
    }

    /** Capture the running content once, independently of the intended database selection. */
    public ConfigurationSnapshot publishedSnapshot() {
        publication.lock();
        try {
            ConfigurationSnapshot snapshot = published;
            if (snapshot == null) throw new IllegalStateException("Runtime configuration is unavailable");
            return snapshot;
        } finally { publication.unlock(); }
    }

    public List<ConfigurationSnapshot> history() {
        publication.lock();
        try { return store.history(); }
        finally { publication.unlock(); }
    }

    public ConfigurationSnapshot history(UUID id) {
        publication.lock();
        try { return store.findByLocalId(id); }
        finally { publication.unlock(); }
    }

    private Inspection inspectLocked() {
        try {
            ConfigurationSnapshot intended = store.current();
            return new Inspection(published == null ? null : published.localId(), intended.localId(), intended.status(), mutationFault);
        } catch (RuntimeException failure) {
            if (intendedId == null) throw failure;
            mutationFault = mutationFault == null ? "Configuration selection could not be inspected" : mutationFault;
            LOG.error("Configuration mutation fault: intended selection could not be inspected");
            return new Inspection(published == null ? null : published.localId(), intendedId, intendedStatus, mutationFault);
        }
    }

    private PreparedSkillUpdate prepare(ManagedConfiguration configuration) {
        return reloader.prepare(configuration.skillDocuments());
    }

    private GenerationRestResources.Resources stage(PreparedSkillUpdate prepared, ManagedConfiguration configuration) {
        GenerationRestResources.Resources staged = resources.prepare(configuration.restRoutesYaml());
        try {
            routes.validate(staged.routes(), prepared.snapshot());
            return staged;
        } catch (RuntimeException failure) {
            resources.release(staged);
            throw failure;
        }
    }

    private void pruneAfterAttempt() {
        try { prune(); }
        catch (RuntimeException failure) {
            if (mutationFault == null) mutationFault = "Configuration history pruning failed";
            LOG.error("Configuration mutation fault: history pruning failed");
        }
    }

    private void prune() {
        HashSet<UUID> protectedIds = new HashSet<>(resources.protectedIds());
        if (published != null) protectedIds.add(published.localId());
        store.prune(protectedIds);
    }

    /** Editing operations and the actual framework switch share only this short gate. */
    public <T> T withEditingState(boolean mutation, Function<ConfigurationSnapshot, T> operation) {
        transition.lock();
        try {
            if (mutation) requireHealthy();
            if (published == null) throw new IllegalStateException("Runtime configuration is unavailable");
            return operation.apply(published);
        } finally {
            transition.unlock();
        }
    }

    private void requireHealthy() {
        if (mutationFault != null) throw new IllegalStateException("Configuration mutations are stopped: " + mutationFault);
    }

    private ConfigurationValidationIssue validationIssue(RuntimeException failure) {
        if (failure instanceof IllegalStateException && failure.getMessage() != null
                && failure.getMessage().startsWith("Invalid REST routes at rest-routes.yaml")) {
            String message = failure.getMessage();
            int open = message.indexOf('[');
            int close = message.indexOf(']', open + 1);
            String location = open >= 0 && close > open ? message.substring(open + 1, close) : null;
            return new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                    "rest-routes.yaml", null, location, message);
        }
        return new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                "rest-routes.yaml", null, null, "REST client staging failed");
    }

    private ConfigurationValidationIssue skillValidationIssue(RuntimeException failure, ManagedConfiguration configuration) {
        return new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                "candidate", null, null, "Skill preparation failed");
    }

    @Override @jakarta.annotation.PreDestroy public void close() {
        if (retirement != null) {
            try { retirement.close(); } catch (Exception ignored) { }
        }
    }
}
