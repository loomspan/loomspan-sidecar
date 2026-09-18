package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationDraftTest
{
    @Test
    void createsIndependentDraftsFromSuppliedRuntimeSnapshot()
    {
        var documents = new ArrayList<>(List.of(
                new SkillDocument("B.yaml", "# malformed on purpose\nname: [\n"),
                new SkillDocument("a.yml", "name: ${SKILL_NAME}\nliteral: raw-value\n")));
        var original = new ManagedConfiguration(documents,
                "# original routes\ntargets:\n  api: ${URL}\nroutes: {}\n");
        var snapshot = new ConfigurationSnapshot(UUID.randomUUID(), UUID.randomUUID(), 7,
                original, SnapshotStatus.PUBLISHED);
        documents.clear();

        var first = new ConfigurationDraft(snapshot);
        var second = new ConfigurationDraft(snapshot);
        first.replaceContent(new ManagedConfiguration(List.of(), "# edited\ntargets: {}\nroutes: {}\n"));

        assertThat(first.baseSnapshotId()).isEqualTo(snapshot.localId());
        assertThat(second.baseSnapshotId()).isEqualTo(snapshot.localId());
        assertThat(first.freeze().configuration().skillDocuments()).isEmpty();
        assertThat(second.freeze().configuration()).isEqualTo(original);
        assertThat(snapshot.configuration()).isEqualTo(original);
        assertThat(second.freeze().configuration().skillDocuments())
                .extracting(SkillDocument::sourceName).containsExactly("B.yaml", "a.yml");
        assertThat(second.freeze().configuration().skillDocuments().getFirst().yaml())
                .isEqualTo("# malformed on purpose\nname: [\n");
        assertThat(second.freeze().configuration().skillDocuments().get(1).yaml())
                .isEqualTo("name: ${SKILL_NAME}\nliteral: raw-value\n");
        assertThat(second.freeze().configuration().restRoutesYaml())
                .isEqualTo("# original routes\ntargets:\n  api: ${URL}\nroutes: {}\n");

        second.replaceContent(new ManagedConfiguration(List.of(new SkillDocument("second.yml", "invalid: [")),
                "routes: ${ROUTES}\n"));
        assertThat(first.freeze().configuration().skillDocuments()).isEmpty();
        assertThat(snapshot.configuration()).isEqualTo(original);
    }

    @Test
    void freezingRetainsExactContentAfterDraftChanges()
    {
        ConfigurationSnapshot snapshot = snapshot();
        ConfigurationDraft draft = new ConfigurationDraft(snapshot);
        FrozenConfigurationCandidate original = draft.freeze();
        assertThat(draft.freeze()).isSameAs(original);
        draft.replaceContent(original.configuration());
        assertThat(draft.freeze()).isNotSameAs(original);
        draft.replaceContent(new ManagedConfiguration(List.of(), "# now empty\n"));
        draft = null;

        assertThat(original.baseSnapshotId()).isEqualTo(snapshot.localId());
        assertThat(original.configuration().skillDocuments()).extracting(SkillDocument::sourceName)
                .containsExactly("B.yaml", "a.yml");
        assertThat(original.configuration().skillDocuments().getFirst().yaml()).isEqualTo("name: [\n");
        assertThat(original.configuration().restRoutesYaml()).isEqualTo("# routes\ntargets: ${URL}\n");
        assertThatThrownBy(() -> original.configuration().skillDocuments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validationBelongsOnlyToCurrentCandidate()
    {
        ConfigurationSnapshot snapshot = snapshot();
        ConfigurationDraft draft = new ConfigurationDraft(snapshot);
        FrozenConfigurationCandidate original = draft.freeze();
        var success = new ConfigurationValidationResult(true, List.of());
        var failure = new ConfigurationValidationResult(false, List.of());

        assertThat(draft.validationFor(original)).isNull();
        assertThat(draft.recordValidation(original, success)).isTrue();
        assertThat(draft.validationFor(original)).isSameAs(success);

        draft.replaceContent(new ManagedConfiguration(List.of(), "other: [\n"));
        FrozenConfigurationCandidate changed = draft.freeze();
        assertThat(draft.validationFor(original)).isNull();
        assertThat(draft.validationFor(changed)).isNull();
        assertThat(draft.recordValidation(original, failure)).isFalse();
        assertThat(draft.validationFor(changed)).isNull();

        draft.replaceContent(original.configuration());
        FrozenConfigurationCandidate sameTextAgain = draft.freeze();
        assertThat(sameTextAgain.configuration()).isEqualTo(original.configuration());
        assertThat(sameTextAgain).isNotSameAs(original);
        assertThat(draft.recordValidation(original, success)).isFalse();
        assertThat(draft.recordValidation(changed, failure)).isFalse();
        assertThat(draft.validationFor(sameTextAgain)).isNull();

        ConfigurationDraft otherDraft = new ConfigurationDraft(snapshot);
        assertThat(draft.recordValidation(otherDraft.freeze(), success)).isFalse();
        assertThat(draft.validationFor(otherDraft.freeze())).isNull();
        assertThat(draft.recordValidation(sameTextAgain, failure)).isTrue();
        assertThat(draft.validationFor(sameTextAgain)).isSameAs(failure);
        draft.replaceContent(sameTextAgain.configuration());
        assertThat(draft.validationFor(draft.freeze())).isNull();
    }

    @Test
    void preservesSourceLabelAndAvailableErrorLocation()
    {
        var callerIssues = new ArrayList<>(List.of(
                new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                        "B.yaml", null, "line 1, column 7", "invalid sequence"),
                new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                        "rest-routes.yaml", null, null, "missing target")));
        var result = new ConfigurationValidationResult(false, callerIssues);
        callerIssues.clear();
        ConfigurationDraft draft = new ConfigurationDraft(snapshot());
        FrozenConfigurationCandidate candidate = draft.freeze();
        assertThat(draft.recordValidation(candidate, result)).isTrue();

        assertThat(draft.validationFor(candidate).issues()).containsExactly(
                new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                        "B.yaml", null, "line 1, column 7", "invalid sequence"),
                new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                        "rest-routes.yaml", null, null, "missing target"));
        assertThatThrownBy(() -> result.issues().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ConfigurationValidationResult(false, List.of()).issues()).isEmpty();
        assertThatThrownBy(() -> new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                " ", null, null, "message"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                "source", null, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void validatedCandidateRequiresCurrentSuccessfulResult() {
        var draft = new ConfigurationDraft(snapshot());
        assertThatThrownBy(draft::validatedCandidate).hasMessageContaining("requires successful validation");
        var first = draft.freeze();
        draft.recordValidation(first, new ConfigurationValidationResult(true, List.of()));
        assertThat(draft.validatedCandidate().candidate()).isSameAs(first);
        draft.replaceContent(first.configuration());
        assertThatThrownBy(draft::validatedCandidate).hasMessageContaining("requires successful validation");
    }

    private static ConfigurationSnapshot snapshot()
    {
        return new ConfigurationSnapshot(UUID.randomUUID(), UUID.randomUUID(), 12,
                new ManagedConfiguration(List.of(new SkillDocument("B.yaml", "name: [\n"),
                        new SkillDocument("a.yml", "name: ${SKILL_NAME}\n")),
                        "# routes\ntargets: ${URL}\n"), SnapshotStatus.PUBLISHED);
    }
}
