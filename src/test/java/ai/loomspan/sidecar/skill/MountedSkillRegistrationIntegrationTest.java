package ai.loomspan.sidecar.skill;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillKind;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class MountedSkillRegistrationIntegrationTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void registersYamlAndYmlSkillsFromMountedTreeWithoutScanningRouteFile() throws IOException
    {
        Path skills = temporaryDirectory.resolve("sidecar/skills");
        copyFixture("fixtures/skills/mounted-yaml-skill.yaml", skills.resolve("mounted.yaml"));
        copyFixture("fixtures/skills/mounted-yml-skill.yml", skills.resolve("nested/mounted.yml"));
        copyFixture("fixtures/rest-routes-invalid-as-skill.yaml",
                temporaryDirectory.resolve("sidecar/rest-routes.yaml"));

        try (var context = runApplication(skillPatterns(skills), modelProperties()))
        {
            SkillCatalog catalog = context.getBean(SkillCatalog.class);
            assertThat(catalog.skills()).extracting(descriptor -> descriptor.name())
                    .containsExactly("mountedYamlSkill", "mountedYmlSkill");
            assertThat(catalog.skills()).extracting(descriptor -> descriptor.kind())
                    .containsOnly(SkillKind.YAML);
        }
    }

    @Test
    void registersSkillFromCustomLocation() throws IOException
    {
        Path custom = temporaryDirectory.resolve("custom");
        copyFixture("fixtures/skills/mounted-yml-skill.yml", custom.resolve("only.yml"));

        try (var context = runApplication(List.of(custom.toUri() + "**/*.yml"), modelProperties()))
        {
            assertThat(context.getBean(SkillCatalog.class).skills())
                    .extracting(descriptor -> descriptor.name())
                    .containsExactly("mountedYmlSkill");
        }
    }

    @Test
    void modelBackedManifestWithoutConfiguredModelFailsStartup() throws IOException
    {
        Path skills = temporaryDirectory.resolve("skills");
        copyFixture("fixtures/skills/mounted-yaml-skill.yaml", skills.resolve("mounted.yaml"));

        Throwable failure = catchThrowable(() ->
        {
            try (var ignored = runApplication(skillPatterns(skills), List.of()))
            {
                // A successful startup is the failure condition, but still close its resources.
            }
        });

        assertThat(failure).rootCause()
                .hasMessageContaining("YAML skill 'mountedYamlSkill'")
                .hasMessageContaining("unknown model 'fixture-model'")
                .hasMessageContaining("loomspan.models");
    }

    private org.springframework.context.ConfigurableApplicationContext runApplication(
            List<String> locations, List<String> additionalProperties)
    {
        var properties = new java.util.ArrayList<String>();
        properties.add("--loomspan.observability.enabled=false");
        properties.add("--loomspan.skills.locations=" + String.join(",", locations));
        additionalProperties = additionalProperties.stream().map(value -> "--" + value).toList();
        properties.addAll(additionalProperties);
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .run(properties.toArray(String[]::new));
    }

    private static List<String> skillPatterns(Path skills)
    {
        return List.of(skills.toUri() + "**/*.yaml", skills.toUri() + "**/*.yml");
    }

    private static List<String> modelProperties()
    {
        return List.of(
                "loomspan.connections.fixture.driver=ollama",
                "loomspan.connections.fixture.base-url=http://127.0.0.1:9",
                "loomspan.models.fixture-model.connection=fixture",
                "loomspan.models.fixture-model.provider-model=fixture-provider-model");
    }

    private static void copyFixture(String resource, Path destination) throws IOException
    {
        Files.createDirectories(destination.getParent());
        try (InputStream input = MountedSkillRegistrationIntegrationTest.class.getClassLoader()
                .getResourceAsStream(resource))
        {
            if (input == null) throw new IllegalStateException("Missing fixture " + resource);
            Files.copy(input, destination);
        }
    }
}
