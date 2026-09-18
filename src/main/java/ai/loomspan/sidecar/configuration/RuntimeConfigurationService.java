package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.PreparedSkillUpdate;
import ai.loomspan.api.SkillReloader;
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
import java.util.regex.Pattern;

/** Serializes durable selection with framework publication; the database pointer is restart authority. */
@Component
public final class RuntimeConfigurationService implements ApplicationRunner, AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RuntimeConfigurationService.class);
    private static final Pattern SKILL_FIELD = Pattern.compile("for field '([A-Za-z0-9_.-]+)'");
    public record Inspection(UUID publishedId, UUID intendedId, SnapshotStatus intendedStatus, String mutationFault) {}
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
        ConfigurationValidationResult result;
        GenerationRestResources.Resources staged = null;
        boolean preparingSkills = true;
        try {
            PreparedSkillUpdate prepared = prepare(candidate.configuration());
            preparingSkills = false;
            staged = stage(prepared, candidate.configuration());
            result = new ConfigurationValidationResult(true, List.of());
        } catch (RuntimeException failure) {
            result = new ConfigurationValidationResult(false, List.of(
                    preparingSkills ? skillValidationIssue(failure, candidate.configuration()) : validationIssue(failure)));
        } finally {
            if (staged != null) resources.release(staged);
        }
        draft.recordValidation(candidate, result);
        return result;
    }

    public ConfigurationSnapshot publish(ConfigurationDraft draft) {
        publication.lock();
        try {
            requireHealthy();
            FrozenConfigurationCandidate candidate = draft.freeze();
            ConfigurationValidationResult validation = draft.validationFor(candidate);
            if (validation == null || !validation.successful())
                throw new IllegalStateException("Exact draft candidate requires successful validation");
            UUID predecessor = published == null ? null : published.localId();
            if (predecessor == null || !predecessor.equals(candidate.baseSnapshotId()))
                throw new IllegalStateException("Draft is based on a stale runtime snapshot");

            hooks.beforePreparation();
            PreparedSkillUpdate prepared = prepare(candidate.configuration());
            GenerationRestResources.Resources staged = stage(prepared, candidate.configuration());
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
                throw new IllegalStateException("Configuration commit failed before runtime publication");
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
                throw new IllegalStateException(mutationFault == null
                        ? "Configuration publication failed; intended selection reverted"
                        : mutationFault);
            }

            try {
                hooks.beforeStatus();
                store.updateStatus(submitted.localId(), SnapshotStatus.PUBLISHED);
                intendedStatus = SnapshotStatus.PUBLISHED;
            } catch (RuntimeException failure) {
                mutationFault = "Published configuration outcome could not be recorded";
                LOG.error("Configuration mutation fault: runtime {} published but outcome was not recorded",
                        submitted.localId());
            }
            pruneAfterAttempt();
            if (mutationFault != null) throw new IllegalStateException(mutationFault);
            return store.findByLocalId(submitted.localId());
        } finally {
            publication.unlock();
        }
    }

    public Inspection inspect() {
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
            return new ConfigurationValidationIssue("rest-routes.yaml", message, location);
        }
        return new ConfigurationValidationIssue("rest-routes.yaml", "REST client staging failed", null);
    }

    private ConfigurationValidationIssue skillValidationIssue(RuntimeException failure, ManagedConfiguration configuration) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) continue;
            for (var document : configuration.skillDocuments()) {
                if (!message.contains("'" + document.sourceName() + "'")
                        && !message.contains("[" + document.sourceName() + "]")) continue;
                var field = SKILL_FIELD.matcher(message);
                return new ConfigurationValidationIssue(document.sourceName(), "Skill preparation failed",
                        field.find() ? field.group(1) : null);
            }
        }
        return new ConfigurationValidationIssue("candidate", "Skill preparation failed", null);
    }

    @Override @jakarta.annotation.PreDestroy public void close() {
        if (retirement != null) {
            try { retirement.close(); } catch (Exception ignored) { }
        }
    }
}
