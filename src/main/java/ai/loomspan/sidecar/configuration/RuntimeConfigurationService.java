package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.ExecutionConfiguration;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.api.SkillReloadException;
import ai.loomspan.api.SkillValidationIssue;
import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import ai.loomspan.sidecar.management.ManagementEditingState;
import ai.loomspan.sidecar.storage.ConfigurationDraftStore;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ConfigurationValidationIssue;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.EncryptedCredential;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/** Serializes durable selection with framework publication; the database pointer is restart authority. */
@Component
public final class RuntimeConfigurationService implements ApplicationRunner, AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RuntimeConfigurationService.class);
    public record Inspection(UUID publishedId, UUID intendedId, SnapshotStatus intendedStatus, String mutationFault) {}
    public record Current(ConfigurationSnapshot published, UUID intendedId,
            SnapshotStatus intendedStatus, String mutationFault, String mode, boolean readOnly,
            ai.loomspan.sidecar.storage.FileConfigurationStartupStore.Startup fileStartup) {}
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
    private final ConfigurationGenerationRegistry generations;
    private final ExecutionCoordinator executions;
    private final ManagementEditingState editing;
    private final ConfigurationDraftStore drafts;
    private final EffectiveExecutionConfiguration effective;
    private final ConfigurationMode mode;
    private final ai.loomspan.sidecar.storage.FileConfigurationStartupStore fileStarts;
    private final org.springframework.core.env.Environment environment;
    private volatile ai.loomspan.sidecar.storage.FileConfigurationStartupStore.Startup fileStartup;
    private final ReentrantLock publication = new ReentrantLock(true);
    private final ReentrantLock transition = new ReentrantLock(true);
    private volatile ConfigurationSnapshot published;
    private volatile UUID intendedId;
    private volatile SnapshotStatus intendedStatus;
    private volatile String mutationFault;
    private AutoCloseable retirement;
    private volatile Hooks hooks = new Hooks() {};

    public RuntimeConfigurationService(ConfigurationSnapshotStore store, SkillReloader reloader,
            ConfigurationGenerationRegistry generations, ExecutionCoordinator executions,
            ManagementEditingState editing, ConfigurationDraftStore drafts,
            EffectiveExecutionConfiguration effective, ConfigurationMode mode,
            ai.loomspan.sidecar.storage.FileConfigurationStartupStore fileStarts,
            org.springframework.core.env.Environment environment) {
        this.store = store;
        this.reloader = reloader;
        this.generations = generations;
        this.executions = executions;
        this.editing = editing;
        this.drafts = drafts;
        this.effective = effective;
        this.mode = mode; this.fileStarts = fileStarts;
        this.environment = environment;
    }

    void hooks(Hooks hooks) { this.hooks = java.util.Objects.requireNonNull(hooks); }
    int queuedPublications() { return publication.getQueueLength(); }

    @Override
    public void run(ApplicationArguments args) {
        publication.lock();
        try {
            retirement = reloader.onGenerationRetired(generations::retire);
            if (mode == ConfigurationMode.FILE) { startFiles(); return; }
            if (!reloader.snapshot("default").skills().isEmpty())
                throw new IllegalStateException("Framework startup catalog must be empty before database activation");
            ConfigurationSnapshot selected = store.current();
            intendedId = selected.localId();
            intendedStatus = selected.status();
            String generation = null;
            PreparedSkillUpdate prepared = null;
            String phase = "skill preparation";
            try {
                hooks.beforeStartupActivation();
                List<ConfigurationValidationIssue> issues = authoredIssues(selected.configuration());
                if (hasErrors(issues)) throw new InvalidAuthored(issues);
                prepared = prepare(selected.configuration(),
                        effective.assemble(selected.configuration(), store.credentialVersions(selected)));
                generation = prepared.generationId();
                generations.stage(generation, selected.localId());
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
                if (published == null && generation != null) generations.discard(generation);
                String locations = "skill preparation".equals(phase)
                        ? preparationIssues(failure, selected.configuration()).stream().map(issue -> issue.sourceLabel()
                                + (issue.location() == null ? "" : " [" + issue.location() + "]")
                                + (failure instanceof EffectiveExecutionConfiguration.UnavailableValues
                                        ? ": " + issue.message() : ""))
                                .collect(java.util.stream.Collectors.joining(", ", " at ", ""))
                        : "";
                LOG.error("Selected configuration {} failed during {}{}", selected.localId(), phase, locations);
                if (failure instanceof ProviderCredentialCipher.Failure keyFailure)
                    throw new IllegalStateException("Selected configuration " + selected.localId()
                            + " cannot be restored: " + keyFailure.getMessage());
                throw new IllegalStateException("Selected configuration activation or bookkeeping failed for "
                        + selected.localId() + " during " + phase + locations
                        + "; inspect the database selection and restore a consistent backup if needed");
            } finally {
                if (published == null && prepared != null) prepared.close();
            }
        } finally {
            publication.unlock();
        }
    }

    public ConfigurationValidationResult validate(ManagedConfiguration configuration) {
        return validateWithCandidate(configuration, List::of).result();
    }

    public ConfigurationMode mode() { return mode; }
    public void requireDatabase() { mode.requireDatabase(); }

    public record ExportCapture(ConfigurationSnapshot snapshot, List<EncryptedCredential> credentials) {}
    public ExportCapture captureExport(boolean includeCredentials, Runnable authorize) {
        requireDatabase();
        publication.lock();
        try {
            authorize.run();
            var snapshot = publishedSnapshot();
            var credentials = includeCredentials ? store.credentialVersions(snapshot) : List.<EncryptedCredential>of();
            authorize.run();
            return new ExportCapture(snapshot, credentials);
        } finally { publication.unlock(); }
    }

    public void authenticateCredentials(List<EncryptedCredential> credentials) {
        effective.authenticate(credentials);
    }

    private void startFiles() {
        hooks.beforeStartupActivation();
        var catalog = reloader.snapshot("default");
        try {
            var inspection = new java.util.LinkedHashMap<String, Object>();
            inspection.put("mode", "file");
            inspection.put("skills", catalog.skills().stream().map(skill -> java.util.Map.of(
                    "namespace", skill.namespace(), "localName", skill.localName(), "kind", skill.kind().name())).toList());
            var binder = org.springframework.boot.context.properties.bind.Binder.get(environment);
            inspection.put("models", binder.bind("loomspan.models",
                    org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, Object.class))
                    .orElse(java.util.Map.of()));
            inspection.put("session", binder.bind("loomspan.session",
                    org.springframework.boot.context.properties.bind.Bindable.mapOf(String.class, Object.class))
                    .orElse(java.util.Map.of()));
            inspection.put("tracePersistence", environment.getProperty("loomspan.execution-trace.persistence", "Framework default"));
            inspection.put("defaults", "Omitted execution settings use framework defaults");
            fileStartup = fileStarts.record(inspection);
            generations.stage(catalog.generationId(), fileStartup.localId());
            executions.openAfterActivation();
        } catch (Exception failure) {
            generations.discard(catalog.generationId());
            throw new IllegalStateException("File configuration startup failed; check skills and execution settings");
        }
    }

    public List<ai.loomspan.sidecar.storage.FileConfigurationStartupStore.Startup> fileHistory() {
        return mode == ConfigurationMode.FILE ? fileStarts.history() : List.of();
    }

    public record ValidationAttempt(ConfigurationValidationResult result, ValidationProof candidate) {}

    /** The validation proof retains authored content and encrypted identities only. */
    public record ValidationProof(ManagedConfiguration configuration, List<EncryptedCredential> credentials) {
        public ValidationProof {
            credentials = List.copyOf(credentials);
        }

        @Override public String toString() { return "Configuration validation proof [redacted]"; }
    }

    public ValidationAttempt validateWithCandidate(ManagedConfiguration configuration,
            Supplier<List<EncryptedCredential>> credentials) {
        requireDatabase();
        var issues = new ArrayList<>(authoredIssues(configuration));
        if (hasErrors(issues)) return new ValidationAttempt(new ConfigurationValidationResult(false, issues), null);
        try {
            var candidate = effective.assemble(configuration, credentials.get());
            try (PreparedSkillUpdate ignored = prepare(configuration, candidate)) {
                return new ValidationAttempt(new ConfigurationValidationResult(true, issues),
                        new ValidationProof(configuration, candidate.retained()));
            }
        } catch (RuntimeException failure) {
            issues.addAll(preparationIssues(failure, configuration));
        }
        return new ValidationAttempt(new ConfigurationValidationResult(false, issues), null);
    }

    private List<ConfigurationValidationIssue> authoredIssues(ManagedConfiguration configuration) {
        try {
            return reloader.validate("default", configuration.skillDocuments(),
                    new ExecutionConfiguration(configuration.executionConfigurationYaml())).issues().stream()
                    .map(issue -> new ConfigurationValidationIssue(
                            issue.severity() == SkillValidationIssue.Severity.WARNING
                                    ? ConfigurationValidationIssue.Severity.WARNING
                                    : ConfigurationValidationIssue.Severity.ERROR,
                            issue.sourceName(), issue.skillName(), issue.fieldPath(), issue.message()))
                    .toList();
        } catch (RuntimeException failure) {
            return List.of(error("execution-configuration.yaml", "Execution configuration validation failed"));
        }
    }

    private static boolean hasErrors(List<ConfigurationValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == ConfigurationValidationIssue.Severity.ERROR);
    }

    private static final class InvalidAuthored extends IllegalArgumentException {
        private final List<ConfigurationValidationIssue> issues;

        private InvalidAuthored(List<ConfigurationValidationIssue> issues) {
            this.issues = issues.stream()
                    .filter(issue -> issue.severity() == ConfigurationValidationIssue.Severity.ERROR).toList();
        }
    }

    public record PublicationAdmission(long accountId, UUID draftId, long revision,
            ManagedConfiguration configuration, UUID sourceId, UUID baseId, ValidationProof validated) {}

    /** Admission is re-evaluated after the publication gate is acquired. */
    public ConfigurationSnapshot publish(java.util.function.Supplier<PublicationAdmission> admission) {
        publication.lock();
        try {
            requireHealthy();
            transition.lock();
            try {
                PublicationAdmission candidate = admission.get();
                UUID predecessor = published == null ? null : published.localId();
                if (predecessor == null || !predecessor.equals(candidate.baseId()))
                    throw new IllegalStateException("Draft is based on a stale runtime snapshot");
                var fresh = new ValidationProof(candidate.configuration(), drafts.credentialVersions(candidate.accountId()));
                if (!fresh.equals(candidate.validated()))
                    throw new ai.loomspan.sidecar.management.ManagementEditingService.Conflict("validation_required");
                return publishCandidate(candidate.configuration(), candidate.sourceId(), predecessor, candidate);
            } finally { transition.unlock(); }
        } finally { publication.unlock(); }
    }

    private ConfigurationSnapshot publishCandidate(ManagedConfiguration configuration, UUID sourceId,
            UUID predecessor, PublicationAdmission admission) {

            PreparedSkillUpdate prepared = null;
            try {
                hooks.beforePreparation();
                prepared = prepare(configuration, effective.assemble(configuration, admission.validated().credentials()));
            } catch (RuntimeException failure) {
                if (prepared != null) prepared.close();
                throw new PublicationFailure("preparation_failed", "Configuration preparation failed before activation");
            }
            String generation = prepared.generationId();
            ConfigurationSnapshot submitted;
            try {
                hooks.beforeCommit();
                submitted = store.submit(configuration, sourceId, predecessor,
                        admission.validated().credentials());
            } catch (RuntimeException failure) {
                generations.discard(generation);
                prepared.close();
                pruneAfterAttempt();
                LOG.warn("Configuration commit failed before framework publication; runtime remains {}", predecessor);
                throw new PublicationFailure("commit_failed", "Configuration commit failed before runtime publication");
            }
            intendedId = submitted.localId();
            intendedStatus = SnapshotStatus.PENDING;

            try {
                generations.stage(generation, submitted.localId());
                hooks.beforeFrameworkPublish();
                transition.lock();
                try {
                    reloader.publish(prepared);
                    published = submitted;
                } finally {
                    transition.unlock();
                }
            } catch (RuntimeException failure) {
                generations.discard(generation);
                prepared.close();
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

            // Activation has happened. A cleanup fault is a mutation fault, never a rollback claim.
            boolean draftCleared = false;
            try {
                if (!drafts.delete(admission.accountId(), admission.draftId(), admission.revision()))
                    throw new IllegalStateException("Published draft changed during activation");
                synchronized (editing) { editing.clearLease(); }
                draftCleared = true;
            } catch (RuntimeException cleanupFailure) {
                mutationFault = "Published configuration draft could not be cleared";
                LOG.error("Configuration mutation fault: published draft cleanup failed", cleanupFailure);
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
                    !draftCleared ? "draft_cleanup_failed"
                            : statusRecorded ? "history_pruning_failed" : "outcome_recording_failed", mutationFault);
            return new ConfigurationSnapshot(submitted.localId(), submitted.sourceId(),
                    submitted.submissionSequence(), submitted.configuration(), SnapshotStatus.PUBLISHED);
    }

    public Inspection inspect() {
        publication.lock();
        try { return inspectLocked(); }
        finally { publication.unlock(); }
    }

    public Current current() {
        publication.lock();
        try {
            if (mode == ConfigurationMode.FILE)
                return new Current(null, null, null, null, mode.value(), true, fileStartup);
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
                    inspected.intendedId(), inspected.intendedStatus(), mutationFault, mode.value(), false, null);
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
        try { return mode == ConfigurationMode.FILE ? List.of() : store.history(); }
        finally { publication.unlock(); }
    }

    public ConfigurationSnapshot history(UUID id) {
        publication.lock();
        try { return mode == ConfigurationMode.FILE ? null : store.findByLocalId(id); }
        finally { publication.unlock(); }
    }

    private Inspection inspectLocked() {
        if (mode == ConfigurationMode.FILE)
            return new Inspection(fileStartup == null ? null : fileStartup.localId(), null, null, null);
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

    private PreparedSkillUpdate prepare(ManagedConfiguration configuration,
            EffectiveExecutionConfiguration.Candidate candidate) {
        return reloader.prepare("default", configuration.skillDocuments(),
                new ExecutionConfiguration(candidate.yaml()), candidate.values());
    }

    public List<EncryptedCredential> retainedCredentials(ConfigurationSnapshot snapshot) {
        return store.credentialVersions(snapshot);
    }

    public ConfigurationValidationResult validateRetained(ConfigurationSnapshot snapshot) {
        return withEditingState(false, current -> validateWithCandidate(snapshot.configuration(),
                () -> store.credentialVersions(current)).result());
    }

    private void pruneAfterAttempt() {
        try { prune(); }
        catch (RuntimeException failure) {
            if (mutationFault == null) mutationFault = "Configuration history pruning failed";
            LOG.error("Configuration mutation fault: history pruning failed");
        }
    }

    private void prune() {
        HashSet<UUID> protectedIds = new HashSet<>(generations.protectedIds());
        if (published != null) protectedIds.add(published.localId());
        store.prune(protectedIds);
    }

    /** Editing operations share the transition gate with framework switches and confirmed imports. */
    public <T> T withEditingState(boolean mutation, Function<ConfigurationSnapshot, T> operation) {
        requireDatabase();
        transition.lock();
        try {
            if (mutation) requireHealthy();
            if (published == null) throw new IllegalStateException("Runtime configuration is unavailable");
            return operation.apply(published);
        } finally {
            transition.unlock();
        }
    }

    /** Serialize session and account invalidation with the publication transition. */
    public void withEditingTransition(Runnable operation) {
        transition.lock();
        try { operation.run(); }
        finally { transition.unlock(); }
    }

    private void requireHealthy() {
        requireDatabase();
        if (mutationFault != null) throw new IllegalStateException("Configuration mutations are stopped: " + mutationFault);
    }

    private static List<ConfigurationValidationIssue> preparationIssues(RuntimeException failure,
            ManagedConfiguration configuration) {
        if (failure instanceof InvalidAuthored invalid) return invalid.issues;
        if (failure instanceof EffectiveExecutionConfiguration.UnavailableValues unavailable) return unavailable.issues();
        if (failure instanceof ProviderCredentialCipher.Failure keyFailure)
            return List.of(error("execution-configuration.yaml", keyFailure.getMessage()));
        if (failure instanceof SkillReloadException) {
            var collision = EffectiveExecutionConfiguration.collision(configuration, failure.getMessage());
            if (!collision.isEmpty()) return collision;
        }
        return List.of(error("candidate", "Skill preparation failed"));
    }

    private static ConfigurationValidationIssue error(String source, String message) {
        return new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR, source, null, null, message);
    }

    @Override @jakarta.annotation.PreDestroy public void close() {
        if (retirement != null) {
            try { retirement.close(); } catch (Exception ignored) { }
        }
    }
}
