package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationSnapshotStoreTest
{
    @TempDir Path directory;

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
        assertThatThrownBy(() -> store.submit(content, null)).isInstanceOf(RuntimeException.class);
        assertThat(count(source, "configuration_snapshot")).isEqualTo(1);
        assertThat(count(source, "configuration_snapshot_status")).isEqualTo(1);
        assertThat(count(source, "configuration_skill_document")).isZero();
        jdbc.execute("DROP TRIGGER fail_document");
        assertThat(store.submit(content, null).configuration()).isEqualTo(content);
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
        DataSource source = StorageConfiguration.dataSource(path);
        StorageConfiguration.migrate(source);
        var repository = new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(source));
        var store = new ConfigurationSnapshotStore(repository,
                new TransactionTemplate(new DataSourceTransactionManager(source)));
        store.initialize();
        return new Fixture(store, source);
    }

    private int count(DataSource source, String table)
    {
        return new JdbcTemplate(source).queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private record Fixture(ConfigurationSnapshotStore store, DataSource source) {}
}
