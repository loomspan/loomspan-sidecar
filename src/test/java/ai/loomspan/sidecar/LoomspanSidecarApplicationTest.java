package ai.loomspan.sidecar;

import ai.loomspan.api.SkillReloader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoomspanSidecarApplicationTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void contextStartsWithDatabaseSelectedEmptyConfiguration()
    {
        try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .run("--loomspan-sidecar.storage.database-path=" + temporaryDirectory.resolve("sidecar.db"),
                        "--loomspan.observability.enabled=false",
                        "--loomspan-sidecar.trust.mode=file",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json"))
        {
            assertThat(context.getBean(SkillReloader.class).snapshot("default").skills()).isEmpty();
        }
    }

    @Test
    void typesafeProfileRegistersFileModeExecutorLeaf() throws IOException
    {
        var leaf = Files.writeString(temporaryDirectory.resolve("triage.yaml"), """
                name: triage
                description: Assess a support contact
                executor: typesafe
                config:
                  request:
                    model: jev-1.13.0
                    state: contact
                    questions:
                      repeat: {type: noul, instructions: "Is this a repeat contact?"}
                """);
        var configuration = Files.writeString(temporaryDirectory.resolve("execution.yaml"), """
                loomspan:
                  executor-config:
                    default:
                      http: {}
                      typesafe: {}
                """);
        try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .profiles("typesafe").web(WebApplicationType.NONE)
                .run("--spring.config.additional-location=" + configuration.toUri(),
                        "--loomspan-sidecar.configuration.mode=file",
                        "--loomspan.skills.locations=" + leaf.toUri(),
                        "--loomspan-sidecar.storage.database-path=" + temporaryDirectory.resolve("sidecar.db"),
                        "--loomspan-sidecar.trust.mode=file",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json"))
        {
            assertThat(context.getBean(SkillReloader.class).snapshot("default").skills())
                    .extracting(skill -> skill.qualifiedName()).containsExactly("default:triage");
        }
    }
}
