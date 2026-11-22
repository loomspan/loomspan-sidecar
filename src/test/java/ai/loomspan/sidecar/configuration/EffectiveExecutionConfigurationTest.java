package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.storage.EncryptedCredential;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EffectiveExecutionConfigurationTest {
    private final ProviderCredentialCipher cipher = new ProviderCredentialCipher(
            Base64.getEncoder().encodeToString(new byte[32]));
    private final EffectiveExecutionConfiguration effective = new EffectiveExecutionConfiguration(cipher);

    @Test void readsExactNamesFromSelectedNodesWhileKeepingAllEncryptedRecords() {
        String yaml = """
                loomspan:
                  connections:
                    openai:
                      api-key: { $var: Service/Key }
                      headers: { X-Generation: fixed }
                    gemini:
                      gemini:
                        credentials-json: { $var: gemini-json }
                  executor-config:
                    default:
                      http:
                        targets:
                          example:
                            headers:
                              Authorization: { $var: Service/Key }
                        untyped:
                          - { ordinary: true, $var: ignored, nested: { $var: Also.Key } }
                          - "${region}"
                  models:
                    some-model: { unrelated: { $var: ignored } }
                """;
        var skill = new SkillDocument("call.yaml", """
                executor: http
                ignored: { $var: ignored }
                config:
                  ? { $var: region/name }
                  : [ { $var: Service/Key }, { $var: service/key }, { $var: skill.only } ]
                """);
        var nonExecutor = new SkillDocument("model.yaml", "config: { nested: { $var: ignored } }");
        var records = List.of(record("Service/Key", "upper"), record("service/key", "lower"),
                record("region/name", "west"), record("gemini-json", "{}"), record("Also.Key", "x"),
                record("skill.only", "path"), new EncryptedCredential("unused", "v1",
                        Base64.getEncoder().encodeToString(new byte[29])));
        var candidate = effective.assemble(new ManagedConfiguration(List.of(skill, nonExecutor), yaml), records);
        assertThat(candidate.yaml()).isEqualTo(yaml);
        assertThat(candidate.retained()).containsExactlyElementsOf(records);
        assertThat(candidate.values()).containsExactlyInAnyOrderEntriesOf(Map.of("Service/Key", "upper",
                "service/key", "lower", "region/name", "west", "gemini-json", "{}",
                "Also.Key", "x", "skill.only", "path"));
        assertThat(candidate.toString()).doesNotContain("upper");
        assertThat(effective.assemble(new ManagedConfiguration(List.of(), "loomspan: {}\n"), records)
                .values()).isEmpty();
    }

    @Test void reportsEachUnresolvedAuthoredLocationWithoutValues() {
        var authored = new ManagedConfiguration(List.of(new SkillDocument("call.yaml",
                "executor: http\nconfig: { path: { $var: service.key }, extras: [{ $var: service.key }] }\n")),
                "loomspan: { connections: { primary: { api-key: { $var: blank } } } }\n");
        var blank = record("blank", "\u00a0");
        var failure = catchThrowableOfType(EffectiveExecutionConfiguration.UnavailableValues.class,
                () -> effective.assemble(authored, List.of(blank)));
        assertThat(failure.issues()).satisfiesExactly(
                issue -> {
                    assertThat(issue.location()).isEqualTo("loomspan.connections.primary.api-key");
                    assertThat(issue.message()).isEqualTo("blank-value for variable blank");
                },
                issue -> {
                    assertThat(issue.sourceLabel()).isEqualTo("call.yaml");
                    assertThat(issue.location()).isEqualTo("config.path");
                    assertThat(issue.message()).isEqualTo("missing-value for variable service.key");
                },
                issue -> assertThat(issue.location()).isEqualTo("config.extras[0]"));
    }

    @Test void collisionOriginsKeepFrameworkEscapingForQuotedKeys() {
        var authored = new ManagedConfiguration(List.of(new SkillDocument("call.yaml", """
                executor: http
                config:
                  "some/key\\u001b": true
                  ? { $var: match/key }
                  : false
                """)), "loomspan: {}\n");
        assertThat(EffectiveExecutionConfiguration.collision(authored,
                "Failed to prepare skill generation: key-collision at [Location[source=call.yaml, "
                        + "path=config[\"some/key\\u001b\"]], Location[source=call.yaml, path=config{key#1}]]"))
                .extracting(ai.loomspan.sidecar.storage.ConfigurationValidationIssue::location)
                .containsExactly("config[\"some/key\\u001b\"]", "config{key#1}");
    }

    private EncryptedCredential record(String name, String value) {
        return new EncryptedCredential(name, "v1", cipher.encrypt(name, "v1", value));
    }
}
