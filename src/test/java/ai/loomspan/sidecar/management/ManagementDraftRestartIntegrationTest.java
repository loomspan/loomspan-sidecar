package ai.loomspan.sidecar.management;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationDraftStore;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import static org.assertj.core.api.Assertions.assertThat;

class ManagementDraftRestartIntegrationTest {
    @TempDir Path directory;

    @Test void restartRetainsCompleteDraftButDropsEditingAndValidationAuthority() {
        Path db = directory.resolve("restart.db");
        long account;
        ConfigurationDraftStore.Saved saved;
        try (var context = start(db)) {
            var accounts = context.getBean(ManagementAccountRepository.class);
            account = accounts.insert("restart@example.test", "editor", 1);
            var drafts = context.getBean(ConfigurationDraftStore.class);
            var base = context.getBean(RuntimeConfigurationService.class).publishedSnapshot();
            var original = drafts.createIfAbsent(account, base);
            saved = drafts.replace(account, original.draftId(), original.revision(), base.localId(), base.localId(),
                    new ManagedConfiguration(List.of(new SkillDocument("one.yaml", "name: one"),
                            new SkillDocument("two.yaml", "name: two")),
                            "targets: {}\nroutes: {}\n# durable\n", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), null);
            var editing = context.getBean(ManagementEditingState.class);
            editing.lease = new ManagementEditingState.Lease(account, "old-session", "Console", Long.MAX_VALUE);
            editing.validation = new ManagementEditingState.Validation(saved.draftId(), saved.revision(),
                    saved.baseSnapshotId(), editing.lease.generation,
                    new ai.loomspan.sidecar.storage.ConfigurationValidationResult(true, List.of()));
        }
        try (var context = start(db)) {
            assertThat(context.getBean(ConfigurationDraftStore.class).read(account)).isEqualTo(saved);
            var editing = context.getBean(ManagementEditingState.class);
            assertThat(editing.lease).isNull();
            assertThat(editing.validation).isNull();
        }
    }

    private org.springframework.context.ConfigurableApplicationContext start(Path db) {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class).web(WebApplicationType.NONE).run(
                "--loomspan-sidecar.storage.database-path=" + db,
                "--loomspan.observability.enabled=false",
                "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
    }
}
