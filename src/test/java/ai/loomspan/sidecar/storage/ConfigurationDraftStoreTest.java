package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.management.ManagementAccountRepository;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class ConfigurationDraftStoreTest {
    @TempDir Path directory;

    @Test void oneCompleteDraftPerUserIsAtomicAndReopens() {
        Path db = directory.resolve("drafts.db");
        var data = StorageConfiguration.dataSource(db);
        StorageConfiguration.migrate(data);
        var jdbc = new JdbcTemplate(data);
        long a = new ManagementAccountRepository(jdbc).insert("a@example.test", "editor", 1);
        long b = new ManagementAccountRepository(jdbc).insert("b@example.test", "editor", 1);
        var store = new ConfigurationDraftStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(data)));
        var snapshot = new ConfigurationSnapshot(java.util.UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES, ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), SnapshotStatus.PUBLISHED);
        var initial = store.createIfAbsent(a, snapshot);
        assertThat(store.createIfAbsent(a, snapshot)).isEqualTo(initial);
        assertThat(store.read(b)).isNull();
        var content = new ManagedConfiguration(List.of(new SkillDocument("one.yaml", "name: one"),
                new SkillDocument("two.yaml", "name: two")), "targets: {}\nroutes: {}\n# saved\n", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION);
        var changed = store.replace(a, initial.draftId(), initial.revision(), snapshot.localId(),
                snapshot.localId(), content, null);
        assertThat(changed.revision()).isEqualTo(initial.revision() + 1);
        assertThat(changed.configuration()).isEqualTo(content);
        assertThatThrownBy(() -> store.replace(a, initial.draftId(), initial.revision(), snapshot.localId(),
                snapshot.localId(), content, null)).isInstanceOf(IllegalStateException.class);
        jdbc.execute("CREATE TRIGGER fail_draft_document BEFORE INSERT ON management_draft_document "
                + "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        assertThatThrownBy(() -> store.replace(a, changed.draftId(), changed.revision(), snapshot.localId(),
                snapshot.localId(), content, null)).isInstanceOf(RuntimeException.class);
        jdbc.execute("DROP TRIGGER fail_draft_document");
        assertThat(store.read(a)).isEqualTo(changed);
        var reopenedData = StorageConfiguration.dataSource(db);
        StorageConfiguration.migrate(reopenedData);
        var reopened = new ConfigurationDraftStore(new JdbcTemplate(reopenedData),
                new TransactionTemplate(new DataSourceTransactionManager(reopenedData)));
        assertThat(reopened.read(a)).isEqualTo(changed);
        assertThat(reopened.read(b)).isNull();
    }
}
