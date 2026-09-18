package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotRepository;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import ai.loomspan.sidecar.storage.StorageConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigurationRecoveryIntegrationTest {
    @TempDir Path directory;

    @Test
    void pendingCommitAndUnrevertedFailureRestartOntoCommittedB() {
        for (boolean failedPublish : new boolean[] {false, true}) {
            Path database = directory.resolve(failedPublish ? "unreverted.db" : "pending.db");
            var store = open(database);
            ConfigurationSnapshot a = store.current();
            ConfigurationSnapshot b;
            if (failedPublish) {
                try (var context = start(database)) {
                    var service = context.getBean(RuntimeConfigurationService.class);
                    var draft = validDraft(service, context.getBean(ConfigurationSnapshotStore.class).current(), "B");
                    service.hooks(new RuntimeConfigurationService.Hooks() {
                        @Override public void beforeFrameworkPublish() { throw new IllegalStateException("injected"); }
                        @Override public void beforeRevert() { throw new IllegalStateException("injected"); }
                    });
                    assertThatThrownBy(() -> service.publish(draft)).hasMessageContaining("could not be reverted");
                    b = context.getBean(ConfigurationSnapshotStore.class).current();
                    assertThat(service.inspect().publishedId()).isEqualTo(a.localId());
                }
            } else {
                b = store.submit(content("B"), null, a.localId());
            }
            assertThat(b.status()).isEqualTo(SnapshotStatus.PENDING);
            try (var restarted = start(database)) {
                var active = restarted.getBean(RuntimeConfigurationService.class).inspect();
                assertThat(active.publishedId()).isEqualTo(b.localId());
                assertThat(active.intendedId()).isEqualTo(b.localId());
                assertThat(active.intendedStatus()).isEqualTo(SnapshotStatus.PUBLISHED);
                assertThat(active.mutationFault()).isNull();
                assertThat(restarted.getBean(ConfigurationSnapshotStore.class).findByLocalId(a.localId())).isNotNull();
            }
        }
    }

    @Test
    void successfulRevertRestartsAAndStatusFaultRestartsPublishedB() {
        Path revertedDatabase = directory.resolve("reverted.db");
        var revertedStore = open(revertedDatabase);
        var a = revertedStore.current();
        var b = revertedStore.submit(content("B"), null, a.localId());
        revertedStore.revert(b.localId(), a.localId());
        try (var context = start(revertedDatabase)) {
            assertThat(context.getBean(RuntimeConfigurationService.class).inspect().publishedId()).isEqualTo(a.localId());
            assertThat(context.getBean(ConfigurationSnapshotStore.class).findByLocalId(b.localId()).status())
                    .isEqualTo(SnapshotStatus.FAILED);
        }

        Path statusDatabase = directory.resolve("status.db");
        ConfigurationSnapshot published;
        try (var context = start(statusDatabase)) {
            var service = context.getBean(RuntimeConfigurationService.class);
            var draft = validDraft(service, context.getBean(ConfigurationSnapshotStore.class).current(), "B");
            service.hooks(new RuntimeConfigurationService.Hooks() {
                @Override public void beforeStatus() { throw new IllegalStateException("injected"); }
            });
            assertThatThrownBy(() -> service.publish(draft)).hasMessageContaining("outcome could not be recorded");
            published = context.getBean(ConfigurationSnapshotStore.class).current();
            assertThat(published.status()).isEqualTo(SnapshotStatus.PENDING);
            assertThat(service.inspect().publishedId()).isEqualTo(published.localId());
        }
        try (var restarted = start(statusDatabase)) {
            assertThat(restarted.getBean(RuntimeConfigurationService.class).inspect().publishedId())
                    .isEqualTo(published.localId());
            assertThat(restarted.getBean(ConfigurationSnapshotStore.class).current().status())
                    .isEqualTo(SnapshotStatus.PUBLISHED);
        }
    }

    @Test
    void stoppedFullDatabaseBackupRestoresPriorSelectionAndHistory() throws Exception {
        Path original = directory.resolve("original.db");
        var setup = open(original);
        var a = setup.current();
        Path backup = directory.resolve("backup.db");
        copyDatabaseSet(original, backup);
        try (var context = start(original)) {
            var service = context.getBean(RuntimeConfigurationService.class);
            service.publish(validDraft(service, context.getBean(ConfigurationSnapshotStore.class).current(), "B"));
            assertThat(context.getBean(ConfigurationSnapshotStore.class).current().localId()).isNotEqualTo(a.localId());
        }
        Path restored = directory.resolve("restored.db");
        copyDatabaseSet(backup, restored);
        try (var context = start(restored)) {
            var store = context.getBean(ConfigurationSnapshotStore.class);
            assertThat(store.current().localId()).isEqualTo(a.localId());
            assertThat(context.getBean(RuntimeConfigurationService.class).inspect().publishedId()).isEqualTo(a.localId());
            assertThat(store.current().status()).isEqualTo(SnapshotStatus.PUBLISHED);
        }
    }

    @Test
    void selectedStatusWriteFailureAbortsStartupWithoutChangingCommittedPointer() {
        Path database = directory.resolve("startup-status.db");
        var setup = open(database);
        var selected = setup.current();
        new org.springframework.jdbc.core.JdbcTemplate(StorageConfiguration.dataSource(database)).execute(
                "CREATE TRIGGER fail_status BEFORE UPDATE ON configuration_snapshot_status "
                        + "BEGIN SELECT RAISE(ABORT, 'injected status failure'); END");
        assertThatThrownBy(() -> start(database)).hasMessageContaining("Selected configuration activation");
        assertThat(open(database).current().localId()).isEqualTo(selected.localId());
        assertThat(open(database).current().status()).isEqualTo(SnapshotStatus.PENDING);
    }

    @Test
    void invalidSelectedSkillReportsSafeAuthoredLocationAndKeepsPointer() {
        Path database = directory.resolve("invalid-skill.db");
        var store = open(database);
        var a = store.current();
        var selected = store.submit(new ManagedConfiguration(List.of(new SkillDocument("invalid-skill.yaml",
                "name: invalidSkill\nrest: true\nsecret: private-value\n")),
                ConfigurationSnapshotStore.EMPTY_REST_ROUTES), null, a.localId());
        assertThatThrownBy(() -> start(database))
                .hasMessageContaining("skill preparation", "invalid-skill.yaml")
                .hasMessageNotContaining("private-value");
        assertThat(open(database).current().localId()).isEqualTo(selected.localId());
    }

    @Test
    void failedAttemptBeforeCommitRestartsOriginalSelection() {
        Path database = directory.resolve("before-commit.db");
        ConfigurationSnapshot a;
        try (var context = start(database)) {
            var service = context.getBean(RuntimeConfigurationService.class);
            a = context.getBean(ConfigurationSnapshotStore.class).current();
            var draft = validDraft(service, a, "B");
            service.hooks(new RuntimeConfigurationService.Hooks() {
                @Override public void beforeCommit() { throw new IllegalStateException("injected"); }
            });
            assertThatThrownBy(() -> service.publish(draft)).hasMessageContaining("commit failed");
            assertThat(service.inspect().publishedId()).isEqualTo(a.localId());
        }
        try (var restarted = start(database)) {
            assertThat(restarted.getBean(RuntimeConfigurationService.class).inspect().publishedId())
                    .isEqualTo(a.localId());
            assertThat(restarted.getBean(ConfigurationSnapshotStore.class).current().status())
                    .isEqualTo(SnapshotStatus.PUBLISHED);
        }
    }

    private void copyDatabaseSet(Path source, Path destination) throws Exception {
        for (String suffix : List.of("", "-wal", "-shm")) {
            Path candidate = Path.of(source + suffix);
            if (Files.exists(candidate)) Files.copy(candidate, Path.of(destination + suffix), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private ConfigurationDraft validDraft(RuntimeConfigurationService service, ConfigurationSnapshot base, String label) {
        var draft = new ConfigurationDraft(base);
        draft.replaceContent(content(label));
        assertThat(service.validate(draft).successful()).isTrue();
        return draft;
    }

    private ManagedConfiguration content(String label) {
        return new ManagedConfiguration(List.of(), "# " + label + "\n" + ConfigurationSnapshotStore.EMPTY_REST_ROUTES);
    }

    private ConfigurationSnapshotStore open(Path path) {
        var source = StorageConfiguration.dataSource(path);
        StorageConfiguration.migrate(source);
        var store = new ConfigurationSnapshotStore(new ConfigurationSnapshotRepository(
                new NamedParameterJdbcTemplate(source)),
                new TransactionTemplate(new DataSourceTransactionManager(source)));
        store.initialize();
        return store;
    }

    private org.springframework.context.ConfigurableApplicationContext start(Path path) {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class).web(WebApplicationType.NONE).run(
                "--loomspan-sidecar.storage.database-path=" + path,
                "--loomspan.observability.enabled=false",
                "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
    }
}
