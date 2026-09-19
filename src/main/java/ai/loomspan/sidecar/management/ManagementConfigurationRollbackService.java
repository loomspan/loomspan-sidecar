package ai.loomspan.sidecar.management;

import ai.loomspan.api.SkillReloader;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ManagementConfigurationRollbackService {
    private static final String REVIEW = ManagementConfigurationRollbackService.class.getName() + ".review";
    public record Review(UUID reviewId, UUID sourceId, String sourceStatus, long submissionSequence,
            int skillDocuments, int validatedSkills, int routes, ConfigurationValidationResult validation,
            ManagementConfigurationImportService.Observation observation) {}
    private record Reviewed(UUID id, UUID sourceId, ManagementConfigurationImportService.Observation observation) {}
    public static final class Conflict extends RuntimeException {
        private final String code;
        private final ManagementConfigurationImportService.Observation observation;
        Conflict(String code, ManagementConfigurationImportService.Observation observation) {
            this.code = code; this.observation = observation;
        }
        public String code() { return code; }
        public ManagementConfigurationImportService.Observation observation() { return observation; }
    }

    private final RuntimeConfigurationService runtime;
    private final ManagementConfigurationImportService imports;
    private final SkillReloader reloader;
    private final Clock clock;

    public ManagementConfigurationRollbackService(RuntimeConfigurationService runtime,
            ManagementConfigurationImportService imports, SkillReloader reloader, Clock clock) {
        this.runtime = runtime; this.imports = imports; this.reloader = reloader; this.clock = clock;
    }

    public Review review(HttpSession session, ManagementUserDetailsService.Principal user, UUID sourceId) {
        admission(session, user);
        synchronized (session) { session.removeAttribute(REVIEW); }
        if (runtime.inspect().mutationFault() != null)
            throw new IllegalStateException("Configuration mutations are stopped");
        ConfigurationSnapshot source = runtime.history(sourceId);
        if (source == null) throw new Conflict("source_not_found", imports.observation());
        ConfigurationValidationResult validation = runtime.validate(source.configuration());
        var observation = imports.observation();
        UUID id = validation.successful() ? UUID.randomUUID() : null;
        if (id != null) synchronized (session) { session.setAttribute(REVIEW, new Reviewed(id, sourceId, observation)); }
        return new Review(id, source.localId(), source.status().name(), source.submissionSequence(),
                source.configuration().skillDocuments().size(),
                reloader.validate(source.configuration().skillDocuments()).skills().size(),
                routes(source), validation, observation);
    }

    public ConfigurationSnapshot confirm(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID sourceId, UUID reviewId, UUID expectedPublishedId, UUID expectedGrantId) {
        if (session == null || sourceId == null || reviewId == null || expectedPublishedId == null)
            throw new Conflict("review_required", null);
        admission(session, user);
        Reviewed reviewed;
        synchronized (session) { reviewed = (Reviewed) session.getAttribute(REVIEW); }
        if (reviewed == null || !reviewed.id.equals(reviewId) || !reviewed.sourceId.equals(sourceId))
            throw new Conflict("review_required", null);
        try {
            return runtime.rollbackConfiguration(sourceId, expectedPublishedId, expectedGrantId, clock::millis, () -> {
                admission(session, user);
                synchronized (session) {
                    if (!reviewed.equals(session.getAttribute(REVIEW))) throw new Conflict("review_required", null);
                    if (!Objects.equals(reviewed.observation.publishedId(), expectedPublishedId)
                            || !Objects.equals(reviewed.observation.grantId(), expectedGrantId))
                        throw new Conflict("confirmation_stale", null);
                    session.removeAttribute(REVIEW);
                }
            });
        } catch (RuntimeConfigurationService.SourceMissing missing) {
            throw new Conflict("source_not_found", imports.observation());
        } catch (RuntimeConfigurationService.SourceValidationChanged invalid) {
            throw new Conflict("validation_changed", imports.observation());
        } catch (RuntimeConfigurationService.ImportConflict stale) {
            throw refresh(session, reviewed);
        } catch (Conflict conflict) {
            if (!"confirmation_stale".equals(conflict.code())) throw conflict;
            throw refresh(session, reviewed);
        }
    }

    private static int routes(ConfigurationSnapshot source) {
        try {
            var parsed = new tools.jackson.dataformat.yaml.YAMLMapper()
                    .readTree(source.configuration().restRoutesYaml());
            var entries = parsed.get("routes");
            return entries == null ? 0 : entries.size();
        } catch (RuntimeException invalid) { return 0; }
    }

    private void admission(HttpSession session, ManagementUserDetailsService.Principal user) {
        try { imports.admission(session, user); }
        catch (ManagementConfigurationImportService.Conflict conflict) {
            throw new Conflict(conflict.code(), null);
        }
    }

    private Conflict refresh(HttpSession session, Reviewed reviewed) {
        var current = imports.observation();
        try { synchronized (session) {
            Object active = session.getAttribute(REVIEW);
            if (active != null && !active.equals(reviewed)) return new Conflict("review_required", current);
            session.setAttribute(REVIEW, new Reviewed(reviewed.id, reviewed.sourceId, current));
        }} catch (IllegalStateException invalidated) { return new Conflict("session_conflict", null); }
        return new Conflict("confirmation_stale", current);
    }
}
