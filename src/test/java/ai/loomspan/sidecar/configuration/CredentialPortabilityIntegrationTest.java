package ai.loomspan.sidecar.configuration;

import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV3;
import ai.loomspan.sidecar.management.*;
import ai.loomspan.sidecar.storage.*;
import ai.loomspan.sidecar.support.SidecarApplicationFixture;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class CredentialPortabilityIntegrationTest {
    @TempDir Path directory;
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
    static class Keys {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary
        ProviderCredentialCipher portableCipher(org.springframework.core.env.Environment environment) {
            return new ProviderCredentialCipher(environment.getProperty("fixture.key"));
        }
    }
    @Test void removedImportedProviderRequiresExplicitIdentifierRemovalBeforePublication() throws Exception {
        var configuration = new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES,
                "loomspan:\n  connections:\n    primary: {driver: openai, api-key-ref: source.key}\n",
                List.of("source.key"));
        Path zip = ConfigurationBundleV3.write(new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                configuration, SnapshotStatus.PUBLISHED));
        byte[] archive;
        try { archive = Files.readAllBytes(zip); } finally { Files.delete(zip); }
        try (var destination = start("removed-provider", "")) {
            var operator = operator(destination);
            var grant = operator.editing.acquire(operator.session, operator.user, "destination", false, false);
            var loaded = load(destination, operator, grant, archive, "configuration-only");
            var edited = operator.editing.save(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                    loaded.draftId(), loaded.revision(), loaded.baseSnapshotId(), new ManagedConfiguration(List.of(),
                            ConfigurationSnapshotStore.EMPTY_REST_ROUTES, "loomspan: {}\n"));
            assertThat(operator.editing.validate(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                    edited.draftId(), edited.revision(), edited.baseSnapshotId()).validation().successful()).isFalse();
            var removed = operator.editing.removeCredential(operator.session, operator.user, grant.editingSessionId(),
                    grant.generation(), edited.draftId(), edited.revision(), edited.baseSnapshotId(), "source.key");
            var published = publish(operator, grant, removed);
            assertThat(published.configuration().credentialIdentifiers()).isEmpty();
            var capture = destination.getBean(RuntimeConfigurationService.class).captureExport(true, () -> {});
            Path exported = ConfigurationBundleV3.write(capture.snapshot(), capture.credentials(), true);
            try { assertThat(ConfigurationBundleV3.read(exported).credentials()).isEmpty(); }
            finally { Files.delete(exported); }
        }
    }

    @Test void sameKeyImportsCiphertextAndDifferentKeyRequiresExplicitReplacement() throws Exception {
        try (var provider = new SidecarApplicationFixture()) {
        byte[] archive;
        String execution = "loomspan:\n  connections:\n    primary:\n      driver: openai\n      base-url: http://127.0.0.1:9/v1\n      api-key-ref: provider.key\n  models:\n    fixture-model: {connection: primary, provider-model: portable-model}\n"
                .replace("127.0.0.1:9", "127.0.0.1:" + provider.modelPort());
        try (var source = start("source", SidecarApplicationFixture.CREDENTIAL_KEY)) {
            var operator = operator(source);
            var grant = operator.editing.acquire(operator.session, operator.user, "source", false, false);
            var draft = operator.editing.save(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                    grant.draft().draftId(), grant.draft().revision(), grant.draft().baseSnapshotId(),
                    new ManagedConfiguration(List.of(new ai.loomspan.api.SkillDocument("model.yaml", Files.readString(
                            SidecarApplicationFixture.resourceFile("fixtures/skills/mounted-yaml-skill.yaml")))), ConfigurationSnapshotStore.EMPTY_REST_ROUTES, execution));
            draft = operator.editing.replaceCredential(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                    draft.draftId(), draft.revision(), draft.baseSnapshotId(), "provider.key", "portable-secret");
            var published = publish(operator, grant, draft);
            var runtime = source.getBean(RuntimeConfigurationService.class);
            var capture = runtime.captureExport(true, () -> operator.editing.authorizeExport(operator.session, operator.user, true));
            Path zip = ConfigurationBundleV3.write(capture.snapshot(), capture.credentials(), true);
            archive = Files.readAllBytes(zip); Files.delete(zip);
            assertThat(ConfigurationBundleV3.read(new java.io.ByteArrayInputStream(archive)).credentials())
                    .isEqualTo(runtime.retainedCredentials(published));
            assertThat(new JdbcTemplate(source.getBean(javax.sql.DataSource.class)).queryForObject(
                    "SELECT ciphertext FROM configuration_snapshot_credential LIMIT 1", String.class)).doesNotContain("portable-secret");
        }
        try (var destination = start("same", SidecarApplicationFixture.CREDENTIAL_KEY)) {
            var operator = operator(destination);
            var grant = operator.editing.acquire(operator.session, operator.user, "destination", false, false);
            var before = destination.getBean(RuntimeConfigurationService.class).inspect().publishedId();
            var imported = ConfigurationBundleV3.read(new java.io.ByteArrayInputStream(archive));
            var original = imported.credentials().getFirst();
            var altered = new EncryptedCredential(original.identifier(), "tampered-version", original.ciphertext());
            var tamperedFile = ConfigurationBundleV3.write(new ConfigurationSnapshot(imported.sourceSnapshotId(), null, 1,
                    imported.configuration(), SnapshotStatus.PUBLISHED), List.of(altered), true);
            byte[] tampered = Files.readAllBytes(tamperedFile); Files.delete(tamperedFile);
            assertThatThrownBy(() -> load(destination, operator, grant, tampered, "included"))
                    .isInstanceOf(ProviderCredentialCipher.Failure.class);
            assertThat(operator.editing.read(operator.session, operator.user).revision()).isEqualTo(grant.draft().revision());
            var loaded = load(destination, operator, grant, archive, "included");
            assertThat(destination.getBean(RuntimeConfigurationService.class).inspect().publishedId()).isEqualTo(before);
            assertThat(loaded.configuredCredentialIdentifiers()).containsExactly("provider.key");
            publish(operator, grant, loaded);
            execute(destination, provider, "portable-secret");
        }
        try (var destination = start("missing", "")) {
            var operator = operator(destination);
            var grant = operator.editing.acquire(operator.session, operator.user, "destination", false, false);
            assertThatThrownBy(() -> load(destination, operator, grant, archive, "included"))
                    .isInstanceOf(ProviderCredentialCipher.Failure.class).hasMessageContaining("required");
            assertThat(operator.editing.read(operator.session, operator.user).revision()).isEqualTo(grant.draft().revision());
        }
        try (var restarted = start("same", SidecarApplicationFixture.CREDENTIAL_KEY)) {
            assertThat(restarted.getBean(RuntimeConfigurationService.class).publishedSnapshot()
                    .configuration().executionConfigurationYaml()).isEqualTo(execution);
            execute(restarted, provider, "portable-secret");
        }
        byte[] other = new byte[32]; other[0] = 1;
        try (var destination = start("different", Base64.getEncoder().encodeToString(other))) {
            var operator = operator(destination);
            var grant = operator.editing.acquire(operator.session, operator.user, "destination", false, false);
            var runtime = destination.getBean(RuntimeConfigurationService.class);
            var before = runtime.inspect().publishedId();
            assertThatThrownBy(() -> load(destination, operator, grant, archive, "included"))
                    .isInstanceOf(ProviderCredentialCipher.Failure.class).hasMessageContaining("LOOMSPAN_SIDECAR_CREDENTIAL_KEY");
            assertThat(operator.editing.read(operator.session, operator.user).revision()).isEqualTo(grant.draft().revision());
            assertThat(runtime.inspect().publishedId()).isEqualTo(before);
            var loaded = load(destination, operator, grant, archive, "configuration-only");
            assertThat(loaded.configuredCredentialIdentifiers()).isEmpty();
            assertThat(operator.editing.validate(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                    loaded.draftId(), loaded.revision(), loaded.baseSnapshotId()).validation().successful()).isFalse();
            loaded = operator.editing.replaceCredential(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                    loaded.draftId(), loaded.revision(), loaded.baseSnapshotId(), "provider.key", "replacement-secret");
            publish(operator, grant, loaded);
            execute(destination, provider, "replacement-secret");
        }
        }
    }
    private void execute(ConfigurableApplicationContext context, SidecarApplicationFixture provider, String expectedKey) throws Exception {
        provider.modelResponses().add(SidecarApplicationFixture.completion("portable-result"));
        var jwt = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("fixture").header("alg", "none")
                .issuer("https://issuer.test").subject("owner").issuedAt(java.time.Instant.now())
                .expiresAt(java.time.Instant.now().plusSeconds(60)).build();
        var auth = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt);
        var owner = ai.loomspan.sidecar.security.ExecutionOwner.from(auth);
        var coordinator = context.getBean(ai.loomspan.sidecar.execution.ExecutionCoordinator.class);
        var id = coordinator.admit("mountedYamlSkill", Map.of(), 2, owner, auth);
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            var result = coordinator.find(id, owner).orElseThrow();
            if (result.completedAt() != null) {
                assertThat(result.result()).isEqualTo("portable-result");
                assertThat(provider.modelAuthorizations().stream().toList().getLast()).isEqualTo("Bearer " + expectedKey);
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Portable model execution did not complete");
    }
    private ManagementEditingService.Draft load(ConfigurableApplicationContext context, Operator operator,
            ManagementEditingService.Grant grant, byte[] archive, String policy) {
        return context.getBean(ManagementConfigurationImportService.class).load(operator.session, operator.user,
                new MockMultipartFile("bundle", "configuration.zip", "application/zip", archive),
                grant.editingSessionId(), grant.generation(), grant.draft().draftId(), grant.draft().revision(),
                grant.draft().baseSnapshotId(), policy);
    }
    private ConfigurationSnapshot publish(Operator operator, ManagementEditingService.Grant grant, ManagementEditingService.Draft draft) {
        var validated = operator.editing.validate(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                draft.draftId(), draft.revision(), draft.baseSnapshotId());
        assertThat(validated.validation().successful()).isTrue();
        return operator.editing.publish(operator.session, operator.user, grant.editingSessionId(), grant.generation(),
                draft.draftId(), draft.revision(), draft.baseSnapshotId());
    }
    private record Operator(ManagementEditingService editing, MockHttpSession session, ManagementUserDetailsService.Principal user) {}
    private Operator operator(ConfigurableApplicationContext context) {
        String email = UUID.randomUUID() + "@fixture.test";
        var jdbc = new JdbcTemplate(context.getBean(javax.sql.DataSource.class));
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,'editor',1,'fixture-hash',?)",
                email, System.currentTimeMillis());
        var user = (ManagementUserDetailsService.Principal)context.getBean(ManagementUserDetailsService.class).loadUserByUsername(email);
        var session = new MockHttpSession();
        session.setAttribute("management.activity", System.currentTimeMillis());
        return new Operator(context.getBean(ManagementEditingService.class), session, user);
    }
    private ConfigurableApplicationContext start(String name, String key) {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class, Keys.class).web(WebApplicationType.NONE).run(
                "--loomspan-sidecar.storage.database-path=" + directory.resolve(name + ".db"), "--fixture.key=" + key,
                "--loomspan.observability.enabled=false", "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
    }
}
