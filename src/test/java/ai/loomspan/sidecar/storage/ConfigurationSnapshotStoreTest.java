package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.config.SidecarSnapshotProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationSnapshotStoreTest
{
    @TempDir Path directory;

    @Test
    void committedCandidateBecomesCurrentAndReopensPending()
    {
        Path path = directory.resolve("committed.db");
        var first = open(path);
        var initial = first.store.current();
        var candidate = new ManagedConfiguration(List.of(new SkillDocument("b.yaml", "name: B\n")),
                "targets: {b: {base-url: https://b.example}}\nroutes: {}\n");
        var submitted = first.store.submit(candidate, null, initial.localId());

        var reopened = open(path);
        assertThat(reopened.store.current()).isEqualTo(submitted);
        assertThat(reopened.store.current().status()).isEqualTo(SnapshotStatus.PENDING);
        assertThat(reopened.store.findByLocalId(initial.localId())).isEqualTo(initial);
    }

    @Test
    void submitAndRevertAreGuardedAndAtomic()
    {
        var fixture = open(directory.resolve("revert.db"));
        var jdbc = new JdbcTemplate(fixture.source);
        var a = fixture.store.current();
        var bContent = content("B");
        assertThatThrownBy(() -> fixture.store.submit(bContent, null, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(fixture.source, "configuration_snapshot")).isEqualTo(1);

        jdbc.execute("CREATE TRIGGER fail_switch BEFORE UPDATE ON configuration_store_state "
                + "BEGIN SELECT RAISE(ABORT, 'forced switch failure'); END");
        assertThatThrownBy(() -> fixture.store.submit(bContent, null, a.localId()))
                .isInstanceOf(RuntimeException.class);
        assertThat(count(fixture.source, "configuration_snapshot")).isEqualTo(1);
        jdbc.execute("DROP TRIGGER fail_switch");
        var b = fixture.store.submit(bContent, null, a.localId());
        assertThat(open(directory.resolve("revert.db")).store.current()).isEqualTo(b);
        assertThatThrownBy(() -> fixture.store.revert(a.localId(), a.localId()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fixture.store.revert(b.localId(), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);

        jdbc.execute("CREATE TRIGGER fail_status BEFORE UPDATE ON configuration_snapshot_status "
                + "BEGIN SELECT RAISE(ABORT, 'forced status failure'); END");
        assertThatThrownBy(() -> fixture.store.revert(b.localId(), a.localId()))
                .isInstanceOf(RuntimeException.class);
        assertThat(fixture.store.current()).isEqualTo(b);
        jdbc.execute("DROP TRIGGER fail_status");
        fixture.store.revert(b.localId(), a.localId());
        var reopened = open(directory.resolve("revert.db"));
        assertThat(reopened.store.current()).isEqualTo(a);
        assertThat(reopened.store.findByLocalId(b.localId()).configuration()).isEqualTo(bContent);
        assertThat(reopened.store.findByLocalId(b.localId()).status()).isEqualTo(SnapshotStatus.FAILED);
    }

    @Test
    void interruptedUncommittedWriteReopensAtCompleteA() throws Exception
    {
        Path path = directory.resolve("interrupted.db");
        var fixture = open(path);
        var a = fixture.store.submit(content("A"), null, fixture.store.current().localId());
        UUID interruptedId = UUID.randomUUID();
        Path ready = directory.resolve("ready.txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var process = new ProcessBuilder(java, "-cp", classpath, InterruptedSnapshotWrite.class.getName(),
                path.toString(), ready.toString(), interruptedId.toString(), Long.toString(a.submissionSequence()))
                .redirectErrorStream(true).start();
        try
        {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready) && process.isAlive() && System.nanoTime() < deadline)
            {
                Thread.sleep(20);
            }
            assertThat(Files.exists(ready)).as("child wrote candidate and pointer; output: "
                    + new String(process.getInputStream().readNBytes(process.isAlive() ? 0 : 4096))).isTrue();
        }
        finally
        {
            process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
        Thread.sleep(500);
        var reopened = open(path);
        assertThat(reopened.store.current()).isEqualTo(a);
        assertThat(reopened.store.findByLocalId(interruptedId)).isNull();
        assertThat(count(reopened.source, "configuration_snapshot")).isEqualTo(2);
    }

    @Test
    void selectedDamageFailsWithoutFallbackOrSecretLeak()
    {
        Path path = directory.resolve("damaged.db");
        var fixture = open(path);
        var a = fixture.store.current();
        var b = fixture.store.submit(new ManagedConfiguration(
                List.of(new SkillDocument("secret.yaml", "secret: literal-secret-sentinel")),
                "routes: {secret: literal-secret-sentinel}"), null, a.localId());
        new JdbcTemplate(fixture.source).update("DELETE FROM configuration_snapshot_status WHERE snapshot_sequence = ?",
                b.submissionSequence());
        assertThatThrownBy(() -> open(path)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restore a consistent full database backup")
                .hasMessageNotContaining("literal-secret-sentinel")
                .hasNoCause();
        assertThat(fixture.store.findByLocalId(a.localId())).isEqualTo(a);

        Path missingDocumentPath = directory.resolve("missing-document.db");
        var missingDocument = open(missingDocumentPath);
        var predecessor = missingDocument.store.current();
        var selected = missingDocument.store.submit(new ManagedConfiguration(
                List.of(new SkillDocument("secret.yaml", "secret: literal-secret-sentinel")),
                "routes: {secret: literal-secret-sentinel}"), null, predecessor.localId());
        new JdbcTemplate(missingDocument.source).update(
                "DELETE FROM configuration_skill_document WHERE snapshot_sequence = ?", selected.submissionSequence());
        assertThatThrownBy(() -> open(missingDocumentPath)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restore a consistent full database backup")
                .hasMessageNotContaining("literal-secret-sentinel")
                .hasNoCause();
    }

    @Test
    void pruningCountsAllStatusesAndProtectedSnapshots()
    {
        var fixture = open(directory.resolve("prune.db"), 2);
        var first = fixture.store.current();
        var second = fixture.store.submit(content("second"), null, first.localId());
        fixture.store.updateStatus(second.localId(), SnapshotStatus.PUBLISHED);
        var third = fixture.store.submit(content("third"), null, second.localId());
        fixture.store.updateStatus(third.localId(), SnapshotStatus.FAILED);
        var fourth = fixture.store.submit(content("fourth"), null, third.localId());
        fixture.store.prune(Set.of(first.localId(), second.localId(), third.localId()));
        assertThat(count(fixture.source, "configuration_snapshot")).isEqualTo(4);
        fixture.store.prune(Set.of(second.localId()));
        assertThat(fixture.store.findByLocalId(first.localId())).isNull();
        assertThat(fixture.store.findByLocalId(third.localId())).isNull();
        assertThat(fixture.store.findByLocalId(second.localId()).status()).isEqualTo(SnapshotStatus.PUBLISHED);
        assertThat(fixture.store.current()).isEqualTo(fourth);
        assertThat(count(fixture.source, "configuration_snapshot_status")).isEqualTo(2);
        assertThat(count(fixture.source, "configuration_skill_document")).isEqualTo(2);
        fixture.store.prune(Set.of(second.localId()));
        assertThat(count(fixture.source, "configuration_snapshot")).isEqualTo(2);
    }

    @Test
    void defaultRetentionKeepsLatestTenIncludingCurrent()
    {
        var fixture = open(directory.resolve("default-retention.db"));
        var snapshots = new java.util.ArrayList<ConfigurationSnapshot>();
        snapshots.add(fixture.store.current());
        for (int i = 1; i <= 10; i++)
        {
            snapshots.add(fixture.store.submit(content("v" + i), null, snapshots.getLast().localId()));
        }
        fixture.store.prune(Set.of());
        assertThat(fixture.store.findByLocalId(snapshots.getFirst().localId())).isNull();
        assertThat(fixture.store.current()).isEqualTo(snapshots.getLast());
        assertThat(count(fixture.source, "configuration_snapshot")).isEqualTo(10);
    }

    @Test
    void startupPrunesOnlyAfterSelectedLoad()
    {
        Path path = directory.resolve("startup-prune.db");
        var fixture = open(path);
        var a = fixture.store.current();
        var b = fixture.store.submit(content("B"), null, a.localId());
        var c = fixture.store.submit(content("C"), null, b.localId());
        var properties = new SidecarSnapshotProperties();
        properties.setMaxRetained(2);
        var repository = new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(fixture.source));
        var configuration = new StorageConfiguration();
        var manager = new DataSourceTransactionManager(fixture.source);
        var startup = configuration.configurationSnapshotStore(repository, manager, properties);
        assertThat(startup.current()).isEqualTo(c);
        assertThat(startup.findByLocalId(a.localId())).isNull();
        new JdbcTemplate(fixture.source).update("DELETE FROM configuration_snapshot_status WHERE snapshot_sequence = ?",
                c.submissionSequence());
        assertThatThrownBy(() -> configuration.configurationSnapshotStore(repository, manager, properties))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count(fixture.source, "configuration_snapshot")).isEqualTo(2);
    }

    @Test
    void stoppedDatabaseCopyRestoresCompleteCurrentAndHistory() throws Exception
    {
        Path original = directory.resolve("original.db");
        var source = open(original);
        var a = source.store.current();
        var b = source.store.submit(content("B"), UUID.randomUUID(), a.localId());
        source.store.revert(b.localId(), a.localId());
        var c = source.store.submit(content("C"), UUID.randomUUID(), a.localId());
        source.store.prune(Set.of());
        Path backup = directory.resolve("backup.db");
        copyDatabaseSet(original, backup);
        new JdbcTemplate(source.source).update("DELETE FROM configuration_snapshot_status WHERE snapshot_sequence = ?",
                c.submissionSequence());
        assertThatThrownBy(() -> open(original)).isInstanceOf(IllegalStateException.class);
        Path restored = directory.resolve("restored.db");
        copyDatabaseSet(backup, restored);
        var recovery = open(restored);
        assertThat(recovery.store.current()).isEqualTo(c);
        assertThat(recovery.store.findByLocalId(a.localId())).isEqualTo(a);
        assertThat(recovery.store.findByLocalId(b.localId()).status()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(recovery.store.findByLocalId(b.localId()).configuration()).isEqualTo(content("B"));
        assertThat(snapshotHistory(recovery.source)).containsExactly(
                a.localId() + ":pending", b.localId() + ":failed", c.localId() + ":pending");
        assertThat(snapshotHistory(recovery.source)).isEqualTo(snapshotHistory(StorageConfiguration.dataSource(backup)));
    }

    @Test
    void freshStoreInitializesExactlyOnceAndReopenPreservesCurrent()
    {
        Path path = directory.resolve("fresh.db");
        var first = open(path);
        var initial = first.store.initialize();
        assertThat(initial.configuration()).isEqualTo(new ManagedConfiguration(List.of(),
                ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
        assertThat(initial.status()).isEqualTo(SnapshotStatus.PENDING);
        assertThat(count(first.source, "configuration_snapshot")).isEqualTo(1);
        assertThat(count(first.source, "configuration_skill_document")).isZero();
        assertThat(count(first.source, "flyway_schema_history")).isEqualTo(1);
        var second = open(path);
        assertThat(second.store.current()).isEqualTo(initial);
        assertThat(count(second.source, "configuration_snapshot")).isEqualTo(1);
        assertThat(new JdbcTemplate(second.source).queryForObject(
                "SELECT initialized FROM configuration_store_state", Integer.class)).isEqualTo(1);
    }

    @Test
    void rejectsCorruptInitializedOrHalfInitializedState()
    {
        var missingState = open(directory.resolve("state.db"));
        new JdbcTemplate(missingState.source).update("DELETE FROM configuration_store_state");
        assertThatThrownBy(missingState.store::initialize).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restore a consistent full database backup")
                .hasNoCause();

        var missingPointer = open(directory.resolve("pointer.db"));
        new JdbcTemplate(missingPointer.source).update("UPDATE configuration_store_state SET current_snapshot_sequence = NULL");
        assertThatThrownBy(missingPointer.store::initialize).isInstanceOf(IllegalStateException.class);

        Path halfPath = directory.resolve("half.db");
        DataSource halfSource = StorageConfiguration.dataSource(halfPath);
        StorageConfiguration.migrate(halfSource);
        var repository = new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(halfSource));
        repository.insert(new ManagedConfiguration(List.of(), ""), null);
        var half = new ConfigurationSnapshotStore(repository,
                new TransactionTemplate(new DataSourceTransactionManager(halfSource)));
        assertThatThrownBy(half::initialize).isInstanceOf(IllegalStateException.class);

        var missingStatus = open(directory.resolve("status.db"));
        new JdbcTemplate(missingStatus.source).update("DELETE FROM configuration_snapshot_status");
        assertThatThrownBy(missingStatus.store::initialize).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rollsBackIncompleteSnapshotAndInitialPointer() throws Exception
    {
        Path path = directory.resolve("rollback.db");
        DataSource source = StorageConfiguration.dataSource(path);
        StorageConfiguration.migrate(source);
        var jdbc = new JdbcTemplate(source);
        var repository = new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(source));
        var store = new ConfigurationSnapshotStore(repository,
                new TransactionTemplate(new DataSourceTransactionManager(source)));
        jdbc.execute("CREATE TRIGGER fail_pointer BEFORE UPDATE ON configuration_store_state "
                + "BEGIN SELECT RAISE(ABORT, 'forced pointer failure'); END");
        assertThatThrownBy(store::initialize).isInstanceOf(RuntimeException.class);
        assertThat(count(source, "configuration_snapshot")).isZero();
        assertThat(count(source, "configuration_snapshot_status")).isZero();
        assertThat(count(source, "configuration_skill_document")).isZero();
        assertThat(jdbc.queryForObject("SELECT initialized FROM configuration_store_state", Integer.class)).isZero();
        jdbc.execute("DROP TRIGGER fail_pointer");
        store.initialize();

        jdbc.execute("CREATE TRIGGER fail_document BEFORE INSERT ON configuration_skill_document "
                + "WHEN NEW.ordinal = 1 BEGIN SELECT RAISE(ABORT, 'forced document failure'); END");
        var content = new ManagedConfiguration(List.of(new SkillDocument("a", "a"),
                new SkillDocument("b", "b")), "routes: {}\n");
        var initial = store.current();
        assertThatThrownBy(() -> store.submit(content, null, initial.localId())).isInstanceOf(RuntimeException.class);
        assertThat(count(source, "configuration_snapshot")).isEqualTo(1);
        assertThat(count(source, "configuration_snapshot_status")).isEqualTo(1);
        assertThat(count(source, "configuration_skill_document")).isZero();
        jdbc.execute("DROP TRIGGER fail_document");
        assertThat(store.submit(content, null, initial.localId()).configuration()).isEqualTo(content);
    }

    @Test
    void migrationFailurePreventsRepositoryUse()
    {
        Path path = directory.resolve("migration.db");
        DataSource source = StorageConfiguration.dataSource(path);
        StorageConfiguration.migrate(source);
        new JdbcTemplate(source).update("UPDATE flyway_schema_history SET checksum = checksum + 1 WHERE version = '1'");
        assertThatThrownBy(() -> StorageConfiguration.migrate(StorageConfiguration.dataSource(path)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void existingEmptyDatabaseIsNotReseededAsFreshInstallation() throws Exception
    {
        Path path = directory.resolve("truncated.db");
        Files.createFile(path);
        assertThatThrownBy(() -> StorageConfiguration.dataSource(path))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no migration history");
        assertThat(Files.size(path)).isZero();

        Path unmigrated = directory.resolve("unmigrated.db");
        StorageConfiguration.dataSource(unmigrated);
        assertThatThrownBy(() -> StorageConfiguration.dataSource(unmigrated))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no migration history");
    }

    @Test
    void appliesDurabilityPragmasAndTimesOutOnWriteContention() throws Exception
    {
        Path path = directory.resolve("contention.db");
        var fixture = open(path);
        try (Connection first = fixture.source.getConnection(); Connection second = fixture.source.getConnection())
        {
            for (Connection connection : List.of(first, second))
            {
                try (Statement statement = connection.createStatement())
                {
                    assertThat(pragma(statement, "foreign_keys")).isEqualTo("1");
                    assertThat(pragma(statement, "journal_mode")).isEqualToIgnoringCase("wal");
                    assertThat(pragma(statement, "synchronous")).isEqualTo("2");
                    assertThat(pragma(statement, "busy_timeout")).isEqualTo("5000");
                }
            }
            first.setAutoCommit(false);
            first.createStatement().executeUpdate("INSERT INTO configuration_snapshot(local_id, document_count, rest_routes_yaml) "
                    + "VALUES ('held', 0, '')");
            long start = System.nanoTime();
            assertThatThrownBy(() -> second.createStatement().executeUpdate("INSERT INTO configuration_snapshot"
                    + "(local_id, document_count, rest_routes_yaml) VALUES ('blocked', 0, '')"))
                    .isInstanceOf(java.sql.SQLException.class);
            long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertThat(elapsed).isBetween(4000L, 15000L);
            first.rollback();
        }
        assertThat(count(fixture.source, "configuration_snapshot")).isEqualTo(1);
        assertThat(open(path).store.current()).isEqualTo(fixture.store.current());
    }

    private String pragma(Statement statement, String name) throws Exception
    {
        try (var rs = statement.executeQuery("PRAGMA " + name))
        {
            rs.next();
            return rs.getString(1);
        }
    }

    private Fixture open(Path path)
    {
        return open(path, 10);
    }

    private Fixture open(Path path, int maxRetained)
    {
        DataSource source = StorageConfiguration.dataSource(path);
        StorageConfiguration.migrate(source);
        var repository = new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(source));
        var store = new ConfigurationSnapshotStore(repository,
                new TransactionTemplate(new DataSourceTransactionManager(source)), maxRetained);
        store.initialize();
        return new Fixture(store, source);
    }

    private ManagedConfiguration content(String version)
    {
        return new ManagedConfiguration(List.of(new SkillDocument(version + ".yaml", "name: " + version + "\n")),
                "targets: {" + version + ": {base-url: https://" + version + ".example}}\nroutes: {}\n");
    }

    private void copyDatabaseSet(Path source, Path destination) throws Exception
    {
        for (String suffix : List.of("", "-wal", "-shm"))
        {
            Path member = Path.of(source + suffix);
            if (Files.exists(member))
            {
                Files.copy(member, Path.of(destination + suffix), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private List<String> snapshotHistory(DataSource source)
    {
        return new JdbcTemplate(source).query("SELECT s.local_id || ':' || t.status FROM configuration_snapshot s "
                + "JOIN configuration_snapshot_status t ON t.snapshot_sequence = s.submission_sequence "
                + "ORDER BY s.submission_sequence", (rs, row) -> rs.getString(1));
    }

    private int count(DataSource source, String table)
    {
        return new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private record Fixture(ConfigurationSnapshotStore store, DataSource source) {}
}
