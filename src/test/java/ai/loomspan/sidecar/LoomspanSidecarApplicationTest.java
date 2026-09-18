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
    void contextStartsWithDatabaseSelectedEmptyConfiguration()
    {
        try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .run("--loomspan-sidecar.storage.database-path=" + temporaryDirectory.resolve("sidecar.db"),
                        "--loomspan.observability.enabled=false",
                        "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"))
        {
            assertThat(context.getBean(SkillCatalog.class).skills()).isEmpty();
        }
    }
}
