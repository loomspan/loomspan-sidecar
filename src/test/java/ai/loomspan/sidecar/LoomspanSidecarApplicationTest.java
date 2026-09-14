package ai.loomspan.sidecar;

import ai.loomspan.api.SkillCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoomspanSidecarApplicationTest
{
    @TempDir
    Path temporaryDirectory;

    @Test
    void contextStartsWithEmptyMountedLocation()
    {
        String missingPattern = temporaryDirectory.toUri() + "missing/**/*.yaml";
        try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .run("--loomspan.skills.locations=" + missingPattern,
                        "--loomspan.observability.enabled=false"))
        {
            assertThat(context.getBean(SkillCatalog.class).skills()).isEmpty();
        }
    }
}
