package ai.loomspan.sidecar.skill;

import ai.loomspan.api.SkillReloader;
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
    void activatesNamedYamlAndYmlDocumentsFromDatabaseWithoutScanningRouteFile() throws IOException
    {
        Path skills = temporaryDirectory.resolve("sidecar/skills");
        copyFixture("fixtures/skills/mounted-yaml-skill.yaml", skills.resolve("mounted.yaml"));
        copyFixture("fixtures/skills/mounted-yml-skill.yml", skills.resolve("nested/mounted.yml"));
        copyFixture("fixtures/rest-routes-invalid-as-skill.yaml",
                temporaryDirectory.resolve("sidecar/rest-routes.yaml"));

        ai.loomspan.sidecar.support.SidecarApplicationFixture.seedDatabase(temporaryDirectory.resolve("sidecar.db"),
                List.of(skills.resolve("mounted.yaml"), skills.resolve("nested/mounted.yml")),
                ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_REST_ROUTES);
        try (var context = runApplication(modelProperties()))
        {
            var catalog = context.getBean(SkillReloader.class).snapshot();
            assertThat(catalog.skills()).extracting(descriptor -> descriptor.name())
                    .containsExactly("mountedYamlSkill", "mountedYmlSkill");
            assertThat(catalog.skills()).extracting(descriptor -> descriptor.kind())
                    .containsOnly(SkillKind.YAML);
        }
    }

    @Test
    void activatesSingleNamedDocumentFromDatabase() throws IOException
    {
        Path custom = temporaryDirectory.resolve("custom");
        copyFixture("fixtures/skills/mounted-yml-skill.yml", custom.resolve("only.yml"));

        ai.loomspan.sidecar.support.SidecarApplicationFixture.seedDatabase(temporaryDirectory.resolve("sidecar.db"),
                List.of(custom.resolve("only.yml")),
                ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_REST_ROUTES);
        try (var context = runApplication(modelProperties()))
        {
            assertThat(context.getBean(SkillReloader.class).snapshot().skills())
                    .extracting(descriptor -> descriptor.name())
                    .containsExactly("mountedYmlSkill");
        }
    }

    @Test
    void modelBackedManifestWithoutConfiguredModelFailsStartup() throws IOException
    {
        Path skills = temporaryDirectory.resolve("skills");
        copyFixture("fixtures/skills/mounted-yaml-skill.yaml", skills.resolve("mounted.yaml"));
        ai.loomspan.sidecar.support.SidecarApplicationFixture.seedDatabase(temporaryDirectory.resolve("sidecar.db"),
                List.of(skills.resolve("mounted.yaml")),
                ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_REST_ROUTES);

        Throwable failure = catchThrowable(() ->
        {
            try (var ignored = runApplication(List.of()))
            {
                // A successful startup is the failure condition, but still close its resources.
            }
        });

        assertThat(failure).hasMessageContaining("Selected configuration activation");
    }

    @Test
    void rejectsNonemptyConfiguredFrameworkStartupSource() throws IOException {
        Path skill = temporaryDirectory.resolve("external-rest.yaml");
        Files.writeString(skill, """
                name: externalRest
                description: Must not bypass database selection.
                rest: true
                input_schema: {type: object, properties: {}}
                """);
        Throwable failure = catchThrowable(() -> {
            try (var ignored = runApplication(List.of("loomspan.skills.locations=" + skill.toUri()))) { }
        });
        assertThat(failure).hasMessageContaining("Framework startup catalog must be empty");
    }

    private org.springframework.context.ConfigurableApplicationContext runApplication(List<String> additionalProperties)
    {
        var properties = new java.util.ArrayList<String>();
        properties.add("--loomspan.observability.enabled=false");
        properties.add("--loomspan-sidecar.storage.database-path=" + temporaryDirectory.resolve("sidecar.db"));
        properties.add("--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test");
        properties.add("--loomspan-sidecar.auth.jwt.audience=sidecar");
        properties.add("--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
        additionalProperties = additionalProperties.stream().map(value -> "--" + value).toList();
        properties.addAll(additionalProperties);
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .run(properties.toArray(String[]::new));
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
