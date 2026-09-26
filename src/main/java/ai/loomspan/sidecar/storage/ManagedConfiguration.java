package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact authored content, without parsing or resolving placeholders. */
public record ManagedConfiguration(List<SkillDocument> skillDocuments, String restRoutesYaml,
        String executionConfigurationYaml)
{
    public ManagedConfiguration
    {
        Objects.requireNonNull(skillDocuments, "skillDocuments");
        Objects.requireNonNull(restRoutesYaml, "restRoutesYaml");
        Objects.requireNonNull(executionConfigurationYaml, "executionConfigurationYaml");
        skillDocuments = List.copyOf(skillDocuments);
        Set<String> labels = new HashSet<>();
        for (SkillDocument document : skillDocuments)
        {
            Objects.requireNonNull(document, "skillDocument");
            String label = document.sourceName();
            if (label == null || label.isBlank() || document.yaml() == null || !labels.add(label))
            {
                throw new IllegalArgumentException("Skill documents require unique nonblank labels and non-null YAML");
            }
        }
    }
}
