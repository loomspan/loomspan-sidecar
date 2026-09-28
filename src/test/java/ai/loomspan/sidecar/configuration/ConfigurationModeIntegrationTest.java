package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.SkillReloader;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationModeIntegrationTest {
    @TempDir Path directory;

    @Test
    void databaseModeBootstrapsDespiteInvalidDeploymentProviders() {
        try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE).run(
                    "--loomspan-sidecar.storage.database-path=" + directory.resolve("poison.db"),
                    "--loomspan.connections.poison.driver=definitely-invalid",
                    "--loomspan.connections.poison.api-key=${MISSING_POISON_SECRET}",
                    "--loomspan.observability.enabled=false",
                    "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                    "--loomspan-sidecar.auth.jwt.audience=sidecar",
                    "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem")) {
            assertThat(context.getBean(SkillReloader.class).snapshot().skills()).isEmpty();
            assertThat(context.getBean(ConfigurationSnapshotStore.class).current()
                    .configuration().skillDocuments()).isEmpty();
        }
    }

    @Test void databaseSkipsNumericExecutionBindingButPreservesStrictProcessBinding() {
        try (var context = start("numeric.db", "--loomspan.session.max-depth=not-a-number",
                "--loomspan.execution-trace.persistence=invalid", "--loomspan.models.poison.temperature=invalid",
                "--loomspan.shutdown.timeout=2s")) {
            assertThat(context.getBean(RuntimeConfigurationService.class).mode()).isEqualTo(ConfigurationMode.DATABASE);
        }
        assertThatThrownBy(() -> start("unknown.db", "--loomspan.unknown-process-setting=invalid"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> start("invalid.db", "--loomspan-sidecar.configuration.mode=automatic"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test void fileStartupDoesNotInitializeDatabaseSelectionAndRecordsDurableIdentity() throws Exception {
        var skill = directory.resolve("file-skill.yaml");
        java.nio.file.Files.writeString(skill, "name: fileRest\ndescription: File fixture\nrest: true\n");
        var routes = directory.resolve("routes.yaml");
        java.nio.file.Files.writeString(routes, "targets:\n  local: {base-url: 'http://127.0.0.1:9', auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}\nroutes:\n  fileRest: {target: local, method: GET, path: /echo}\n");
        try (var context = start("file.db", "--loomspan-sidecar.configuration.mode=file",
                "--loomspan.skills.locations=" + skill.toUri(), "--loomspan-sidecar.rest-routes.location=" + routes.toUri())) {
            var runtime = context.getBean(RuntimeConfigurationService.class);
            var catalog = context.getBean(SkillReloader.class).snapshot();
            assertThat(catalog.skill("fileRest")).isPresent();
            assertThat(runtime.current().readOnly()).isTrue();
            var id = runtime.current().fileStartup().localId();
            assertThat(context.getBean(ai.loomspan.sidecar.rest.GenerationRestResources.class)
                    .snapshotId(catalog.generationId())).isEqualTo(id);
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class));
            assertThat(jdbc.queryForObject("SELECT initialized FROM configuration_store_state", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT local_id FROM configuration_file_startup", String.class)).isEqualTo(id.toString());
            java.nio.file.Files.writeString(skill, "invalid changed file");
            assertThat(context.getBean(SkillReloader.class).snapshot().skill("fileRest")).isPresent();
            assertThatThrownBy(() -> runtime.validate(new ai.loomspan.sidecar.storage.ManagedConfiguration(
                    java.util.List.of(), "targets: {}\nroutes: {}\n", "loomspan: {}\n")))
                    .isInstanceOf(ai.loomspan.sidecar.management.ManagementEditingService.Conflict.class);
        }
        assertThatThrownBy(() -> start("file.db", "--loomspan-sidecar.configuration.mode=file",
                "--loomspan.skills.locations=" + skill.toUri(), "--loomspan-sidecar.rest-routes.location=" + routes.toUri()))
                .isInstanceOf(RuntimeException.class);
        try (var restored = start("file.db")) {
            assertThat(restored.getBean(ConfigurationSnapshotStore.class).current().configuration().skillDocuments()).isEmpty();
        }
    }

    @Test void environmentModeIsCapturedOnce() {
        var environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.SystemEnvironmentPropertySource(
                "fixture-environment", java.util.Map.of("LOOMSPAN_SIDECAR_CONFIGURATION_MODE", "file")));
        try (var context = builder().environment(environment).run(arguments("env.db",
                "--loomspan.skills.locations=classpath:/sidecar-empty-skills/*.yaml"))) {
            assertThat(context.getBean(RuntimeConfigurationService.class).mode()).isEqualTo(ConfigurationMode.FILE);
            environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
                    "changed", java.util.Map.of("loomspan-sidecar.configuration.mode", "database")));
            assertThat(context.getBean(RuntimeConfigurationService.class).mode()).isEqualTo(ConfigurationMode.FILE);
        }
    }

    @Test void fileModelCredentialsChangeOnlyAtRestartAndPreserveDatabaseAuthority() throws Exception {
        java.util.UUID selected;
        try (var context = start("populated.db")) {
            selected = context.getBean(RuntimeConfigurationService.class).publishedSnapshot().localId();
        }
        try (var provider = new ai.loomspan.sidecar.support.SidecarApplicationFixture()) {
            var settings = directory.resolve("file-settings.yaml");
            String yaml = "loomspan:\n  connections:\n    fixture:\n      driver: openai\n      base-url: http://127.0.0.1:"
                    + provider.modelPort() + "/v1\n      api-key: literal-file-one\n  models:\n    fixture-model:\n      connection: fixture\n      provider-model: fixture-provider-model\n  session:\n    max-depth: 8\n";
            java.nio.file.Files.writeString(settings, yaml);
            String[] options = {"--loomspan-sidecar.configuration.mode=file",
                    "--spring.config.additional-location=" + settings.toUri(),
                    "--loomspan.skills.locations=classpath:fixtures/skills/mounted-yaml-skill.yaml"};
            try (var context = start("populated.db", options)) {
                runModel(context, provider);
                java.nio.file.Files.writeString(settings, yaml.replace("literal-file-one", "literal-file-two"));
                runModel(context, provider);
                assertThat(provider.modelAuthorizations()).containsExactly("Bearer literal-file-one", "Bearer literal-file-one");
                String safe = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                        context.getBean(RuntimeConfigurationService.class).current());
                assertThat(safe).doesNotContain("literal-file-one", "literal-file-two");
                assertThat(context.getBean(ConfigurationSnapshotStore.class).current().localId()).isEqualTo(selected);
            }
            try (var context = start("populated.db", options)) {
                runModel(context, provider);
                assertThat(provider.modelAuthorizations()).containsExactly("Bearer literal-file-one", "Bearer literal-file-one", "Bearer literal-file-two");
            }
        }
        try (var context = start("populated.db")) {
            assertThat(context.getBean(RuntimeConfigurationService.class).publishedSnapshot().localId()).isEqualTo(selected);
            assertThat(context.getBean(SkillReloader.class).snapshot().skills()).isEmpty();
        }
    }

    @Test void fileRestRoutesRemainCapturedUntilRestart() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        for (String value : java.util.List.of("first", "second")) server.createContext("/" + value, exchange -> {
            byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            var skill = directory.resolve("rest.yaml");
            java.nio.file.Files.writeString(skill, "name: fileRest\ndescription: File route fixture\nrest: true\n");
            var routes = directory.resolve("rest-routes.yaml");
            String yaml = "targets:\n  local: {base-url: 'http://127.0.0.1:" + server.getAddress().getPort()
                    + "', auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}\n"
                    + "routes:\n  fileRest: {target: local, method: GET, path: /first}\n";
            java.nio.file.Files.writeString(routes, yaml);
            String[] options = {"--loomspan-sidecar.configuration.mode=file", "--loomspan.skills.locations=" + skill.toUri(),
                    "--loomspan-sidecar.rest-routes.location=" + routes.toUri()};
            try (var context = start("rest.db", options)) {
                runSkill(context, "fileRest", "first");
                java.nio.file.Files.writeString(routes, yaml.replace("/first", "/second"));
                runSkill(context, "fileRest", "first");
            }
            try (var context = start("rest.db", options)) { runSkill(context, "fileRest", "second"); }
        } finally { server.stop(0); }
    }

    private void runModel(org.springframework.context.ConfigurableApplicationContext context,
            ai.loomspan.sidecar.support.SidecarApplicationFixture provider) throws Exception {
        provider.modelResponses().add(ai.loomspan.sidecar.support.SidecarApplicationFixture.completion("file-result"));
        runSkill(context, "mountedYamlSkill", "file-result");
    }
    private void runSkill(org.springframework.context.ConfigurableApplicationContext context, String skill, String expected) throws Exception {
        var coordinator = context.getBean(ai.loomspan.sidecar.execution.ExecutionCoordinator.class);
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "none")
                .issuer("https://issuer.test").subject("owner").issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60)).build();
        var auth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
        var owner = ai.loomspan.sidecar.security.ExecutionOwner.from(auth);
        var id = coordinator.admit(skill, java.util.Map.of(), 2, owner, auth);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var result = coordinator.find(id, owner).orElseThrow();
            if (result.status() == ai.loomspan.sidecar.execution.ExecutionStatus.COMPLETED) {
                assertThat(result.result()).isEqualTo(expected);
                assertThat(result.configurationSnapshotId()).isEqualTo(context.getBean(RuntimeConfigurationService.class).current().fileStartup().localId());
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("File model execution did not complete");
    }

    private SpringApplicationBuilder builder() {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class).web(WebApplicationType.NONE);
    }
    private org.springframework.context.ConfigurableApplicationContext start(String database, String... extra) {
        return builder().run(arguments(database, extra));
    }
    private String[] arguments(String database, String... extra) {
        var args = new java.util.ArrayList<>(java.util.List.of(
                "--loomspan-sidecar.storage.database-path=" + directory.resolve(database),
                "--loomspan.observability.enabled=false",
                "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"));
        args.addAll(java.util.List.of(extra)); return args.toArray(String[]::new);
    }
}
