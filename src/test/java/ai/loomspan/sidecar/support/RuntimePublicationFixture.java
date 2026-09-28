package ai.loomspan.sidecar.support;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.management.ManagementAccountRepository;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationDraftStore;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Seeds a saved candidate for runtime-only tests; management admission is covered separately. */
@Component
public final class RuntimePublicationFixture {
    private final RuntimeConfigurationService runtime;
    private final ConfigurationDraftStore drafts;
    private final ManagementAccountRepository accounts;

    public RuntimePublicationFixture(RuntimeConfigurationService runtime, ConfigurationDraftStore drafts,
            ManagementAccountRepository accounts) {
        this.runtime = runtime; this.drafts = drafts; this.accounts = accounts;
    }

    public ConfigurationSnapshot publish(ConfigurationDraft draft) {
        return publish(draft, (UUID) null);
    }

    public ConfigurationSnapshot publish(ConfigurationDraft draft, java.util.List<ai.loomspan.sidecar.storage.EncryptedCredential> credentials) {
        return runtime.publish(() -> admission(draft, null, credentials));
    }

    public ConfigurationSnapshot publish(ConfigurationDraft draft, UUID sourceId) {
        return runtime.publish(() -> admission(draft, sourceId));
    }

    public RuntimeConfigurationService.PublicationAdmission admission(ConfigurationDraft draft) {
        return admission(draft, null);
    }

    private RuntimeConfigurationService.PublicationAdmission admission(ConfigurationDraft draft, UUID sourceId) {
        return admission(draft, sourceId, null);
    }
    private RuntimeConfigurationService.PublicationAdmission admission(ConfigurationDraft draft, UUID sourceId,
            java.util.List<ai.loomspan.sidecar.storage.EncryptedCredential> credentials) {
        draft.validatedCandidate();
        long account = accounts.insert(UUID.randomUUID() + "@fixture.test", "editor", 1);
        var original = drafts.createIfAbsent(account, runtime.publishedSnapshot());
        if (credentials != null) {
            for (String identifier : original.configuration().credentialIdentifiers())
                original = drafts.removeCredential(account, original.draftId(), original.revision(),
                        original.baseSnapshotId(), identifier);
            for (var credential : credentials)
                original = drafts.replaceCredential(account, original.draftId(), original.revision(),
                        original.baseSnapshotId(), credential);
        }
        var saved = drafts.replace(account, original.draftId(), original.revision(), original.baseSnapshotId(),
                draft.baseSnapshotId(), draft.freeze().configuration(), sourceId);
        return new RuntimeConfigurationService.PublicationAdmission(account,
                saved.draftId(), saved.revision(), saved.configuration(), sourceId, saved.baseSnapshotId(),
                runtime.validateWithCandidate(saved.configuration(), drafts.credentialVersions(account)).candidate(), false);
    }
}
