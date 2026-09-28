package ai.loomspan.sidecar.configuration;

import ai.loomspan.sidecar.storage.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EffectiveExecutionConfigurationTest {
    private final ProviderCredentialCipher cipher = new ProviderCredentialCipher(
            Base64.getEncoder().encodeToString(new byte[32]));
    @Test void completeCandidateUsesOnlyEncryptedVersionsAndExactYaml() {
        var assembler = new EffectiveExecutionConfiguration(cipher);
        var authored = new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n",
                "loomspan:\n  connections:\n    primary:\n      driver: openai\n      api-key-ref: primary.key\n");
        String version = UUID.randomUUID().toString();
        var credential = new EncryptedCredential("primary.key", version, cipher.encrypt("primary.key", version, "fixture-key"));
        var candidate = assembler.assemble(authored, List.of(credential));
        assertThat(candidate.yaml()).isEqualTo(authored.executionConfigurationYaml());
        assertThat(candidate.values()).containsExactlyEntriesOf(Map.of("primary.key", "fixture-key"));
        var snapshot = new ConfigurationSnapshot(UUID.randomUUID(), null, 1, authored, candidate.yaml(), SnapshotStatus.PUBLISHED);
        assertThat(assembler.restore(snapshot, List.of(credential)).sameContent(candidate)).isTrue();
        String replacement = UUID.randomUUID().toString();
        assertThat(assembler.assemble(authored, List.of(new EncryptedCredential("primary.key", replacement,
                cipher.encrypt("primary.key", replacement, "fixture-key")))).sameContent(candidate)).isFalse();
        assertThat(assembler.assemble(authored, List.of()).values()).isEmpty();
    }
    @Test void rejectsDirectSecretsAndFileCredentialReferences() {
        for (String field : List.of("api-key", "headers", "credentials-json", "credentials-uri", "credentials-ref"))
            assertThatThrownBy(() -> EffectiveExecutionConfiguration.assertNoDirectAuthoredCredentials(
                    "loomspan:\n  connections:\n    primary:\n      " + field + ": secret\n"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
    }

    @Test void vertexCandidatesRequireExplicitManagedCredentialsBeforeProviderPreparation() {
        var assembler = new EffectiveExecutionConfiguration(cipher);
        String yaml = "loomspan:\n  connections:\n    vertex:\n      driver: gemini\n"
                + "      gemini:\n        vertex-ai: true\n        project-id: fixture\n        location: us-central1\n";
        var missing = new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n", yaml);
        assertThatThrownBy(() -> assembler.assemble(missing, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("credentials-json-ref");
        var referenced = new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n",
                yaml + "        credentials-json-ref: vertex.credentials\n", List.of("vertex.credentials"));
        var credential = new EncryptedCredential("vertex.credentials", "v1",
                cipher.encrypt("vertex.credentials", "v1", "synthetic-json-material"));
        assertThat(assembler.assemble(referenced, List.of(credential)).values())
                .containsExactlyEntriesOf(Map.of("vertex.credentials", "synthetic-json-material"));
    }

    @Test void rejectsQuotedAndInlineSecretsEvenInInvalidDrafts() {
        for (String yaml : List.of(
                "loomspan: {connections: {primary: {\"api-key\": secret}}}\nloomspan: {}\n",
                "loomspan: {connections: {primary: {'api-key': secret}}\n",
                "loomspan: [}\nconnections: {primary: {'api-key': secret}}\n",
                "loomspan: [}\nconnections: {primary: {\"api-key\": secret}}\n",
                "loomspan: [}\nconnections: {primary: {api-key: secret}}\n",
                "loomspan: [}\nconnections: {primary: {\"api\\x2dkey\": secret}}\n",
                "loomspan: {connections: {primary: {\"api\\x2dkey\": secret}}}\n",
                "loomspan: {}\n---\nloomspan: {connections: {primary: {api-key: secret}}}\n",
                "defaults: {'api-key': secret}\nloomspan: {}\n"))
            assertThatThrownBy(() -> EffectiveExecutionConfiguration.assertNoDirectAuthoredCredentials(yaml))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        assertThatThrownBy(() -> EffectiveExecutionConfiguration.assertNoDirectAuthoredCredentials(
                "loomspan: {connections: {primary: {api-key-ref: provider.key}}\n"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("syntactically valid");
        assertThatCode(() -> EffectiveExecutionConfiguration.assertNoDirectAuthoredCredentials(
                "loomspan: {connections: []}\n"))
                .doesNotThrowAnyException(); // Semantic validity still belongs to the framework.
    }

    @Test void credentialFieldNamesAreAllowedAsAliasesAndReferencedHeaderNames() {
        String yaml = "loomspan:\n  connections:\n    headers:\n      driver: openai\n"
                + "      header-refs:\n        api-key: provider.key\n"
                + "  models:\n    credentials-json:\n      connection: headers\n      provider-model: fixture\n";
        assertThatCode(() -> EffectiveExecutionConfiguration.assertNoDirectAuthoredCredentials(yaml))
                .doesNotThrowAnyException();
    }

    @Test void unresolvedImportedRequirementsCannotBecomePublicationMetadata() {
        var authored = new ManagedConfiguration(List.of(), "targets: {}\nroutes: {}\n",
                "loomspan: {}\n", List.of("removed.provider"));
        assertThatThrownBy(() -> new EffectiveExecutionConfiguration(cipher).assemble(authored, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
