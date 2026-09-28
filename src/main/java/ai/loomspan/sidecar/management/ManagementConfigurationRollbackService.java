package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class ManagementConfigurationRollbackService {
    public record Review(UUID sourceId, String sourceStatus, long submissionSequence,
            int skillDocuments, ConfigurationValidationResult validation) {}
    public static final class SourceMissing extends RuntimeException {}

    private final RuntimeConfigurationService runtime;
    private final ManagementEditingService editing;

    public ManagementConfigurationRollbackService(RuntimeConfigurationService runtime, ManagementEditingService editing) {
        this.runtime = runtime; this.editing = editing;
    }

    public Review review(UUID sourceId) {
        runtime.requireDatabase();
        var source = runtime.history(sourceId);
        if (source == null) throw new SourceMissing();
        return new Review(source.localId(), source.status().name(), source.submissionSequence(),
                source.configuration().skillDocuments().size(), runtime.validateRetained(source));
    }

    public ManagementEditingService.Draft load(HttpSession session, ManagementUserDetailsService.Principal user,
            UUID sourceId, UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseId) {
        runtime.requireDatabase();
        var source = runtime.history(sourceId);
        if (source == null) throw new SourceMissing();
        return editing.loadRetained(session, user, editingSessionId, generation, draftId, revision, baseId,
                source.configuration(), source.localId(), runtime.retainedCredentials(source));
    }
}
