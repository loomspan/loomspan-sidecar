package ai.loomspan.sidecar.support;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;

/** Test-only authoring candidate helper for runtime fixture setup. */
public final class TestDrafts {
    private TestDrafts() {}

    public static ConfigurationValidationResult validate(RuntimeConfigurationService runtime, ConfigurationDraft draft) {
        return validate(runtime, draft, runtime.retainedCredentials(runtime.publishedSnapshot()));
    }
    public static ConfigurationValidationResult validate(RuntimeConfigurationService runtime, ConfigurationDraft draft,
            java.util.List<ai.loomspan.sidecar.storage.EncryptedCredential> credentials) {
        var frozen = draft.freeze();
        var result = runtime.validateWithCandidate(frozen.configuration(), credentials).result();
        draft.recordValidation(frozen, result);
        return result;
    }
}
