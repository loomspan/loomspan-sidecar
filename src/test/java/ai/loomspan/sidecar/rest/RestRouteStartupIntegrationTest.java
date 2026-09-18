package ai.loomspan.sidecar.rest;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.SkillCatalog;
import ai.loomspan.api.SkillKind;
import ai.loomspan.api.SkillDescriptor;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestRouteStartupIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void startsWithRestSkillsUsingOneProductionHandlerAndCompletedCatalog() throws IOException {
        Path skill = temporaryDirectory.resolve("skills/echo-rest.yml");
        Files.createDirectories(skill.getParent());
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("fixtures/execution-skills/echo-rest.yml")) {
            if (input == null) throw new IllegalStateException("Missing REST fixture");
            Files.copy(input, skill);
        }
        Path routes = temporaryDirectory.resolve("rest-routes.yaml");
        Files.writeString(routes, """
                targets:
                  callback:
                    base-url: http://127.0.0.1:9/api
                    auth:
                      mode: none
                    connect-timeout: 100ms
                    read-timeout: 100ms
                    max-response-size: 1KB
                routes:
                  echoRest:
                    target: callback
                    method: POST
                    path: /echo
                """);
        ai.loomspan.sidecar.support.SidecarApplicationFixture.seedDatabase(temporaryDirectory.resolve("sidecar.db"),
                java.util.List.of(skill), Files.readString(routes));

        try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .run(
                        "--loomspan-sidecar.storage.database-path=" + temporaryDirectory.resolve("sidecar.db"),
                        "--loomspan.observability.enabled=false",
                        "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem")) {
            assertThat(context.getBeansOfType(RestSkillHandler.class)).hasSize(1);
            assertThat(context.getBean(ai.loomspan.api.SkillReloader.class).snapshot().skills())
                    .anySatisfy(skillDescriptor -> {
                        assertThat(skillDescriptor.name()).isEqualTo("echoRest");
                        assertThat(skillDescriptor.kind()).isEqualTo(SkillKind.REST);
                    });
        }
    }

    @Test
    void validatesExactRoutesAgainstTheCompletedPublicCatalog() throws Exception {
        Path routes = temporaryDirectory.resolve("catalog-routes.yaml");
        Files.writeString(routes, """
                targets:
                  callback: {base-url: http://127.0.0.1:9, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                routes:
                  yamlSkill: {target: callback, method: GET, path: /call}
                """);
        var properties = new ai.loomspan.sidecar.config.RestRoutesProperties();
        var factory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var loader = new RestRouteLoader(properties, new org.springframework.mock.env.MockEnvironment(),
                factory.getBeanProvider(org.springframework.boot.ssl.SslBundles.class));
        var parsed = loader.parse(Files.readString(routes), "catalog-routes.yaml");
        var nonRestCatalog = catalog(java.util.List.of(
                new SkillDescriptor("yamlSkill", "YAML", SkillKind.YAML, "{}")));
        assertThatThrownBy(() -> new RestRouteCatalogValidator().validate(parsed, nonRestCatalog))
                .hasMessageContaining("non-REST skill", "yamlSkill");

        var unknownCatalog = catalog(java.util.List.of());
        assertThatThrownBy(() -> new RestRouteCatalogValidator().validate(parsed, unknownCatalog))
                .hasMessageContaining("unknown skill", "yamlSkill");
    }

    private static SkillCatalog catalog(java.util.List<SkillDescriptor> descriptors) {
        return new SkillCatalog() {
            @Override public String generationId() { return "test-generation"; }
            @Override public java.util.List<SkillDescriptor> skills() { return descriptors; }
            @Override public java.util.Optional<SkillDescriptor> skill(String name) {
                return descriptors.stream().filter(item -> item.name().equals(name)).findFirst();
            }
        };
    }
}
