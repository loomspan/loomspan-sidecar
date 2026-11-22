package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.configuration.ProviderCredentialCipher;
import ai.loomspan.sidecar.management.ManagementAccountRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Base64;
import java.util.UUID;
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
                new ManagedConfiguration(List.of(), ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), SnapshotStatus.PUBLISHED);
        var initial = store.createIfAbsent(a, snapshot);
        assertThat(store.createIfAbsent(a, snapshot)).isEqualTo(initial);
        assertThat(store.read(b)).isNull();
        var content = new ManagedConfiguration(List.of(new SkillDocument("one.yaml", "name: one"),
                new SkillDocument("two.yaml", "name: two")), ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION);
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

    @Test void credentialReplacementPersistsCiphertextAndRevisesDraft() {
        Path db = directory.resolve("credentials.db");
        var data = StorageConfiguration.dataSource(db);
        StorageConfiguration.migrate(data);
        var jdbc = new JdbcTemplate(data);
        long account = new ManagementAccountRepository(jdbc).insert("a@example.test", "editor", 1);
        var store = new ConfigurationDraftStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(data)));
        var snapshot = new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(),
                        ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), SnapshotStatus.PUBLISHED);
        var draft = store.createIfAbsent(account, snapshot);
        var cipher = new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        String version = UUID.randomUUID().toString();
        String sealed = cipher.encrypt("provider.key", version, "sentinel-provider-secret");
        var updated = store.replaceCredential(account, draft.draftId(), draft.revision(), draft.baseSnapshotId(),
                new EncryptedCredential("provider.key", version, sealed));
        assertThat(updated.revision()).isEqualTo(draft.revision() + 1);
        assertThat(updated.configuration().credentialIdentifiers()).containsExactly("provider.key");
        assertThat(jdbc.queryForObject("SELECT ciphertext FROM management_draft_credential", String.class))
                .isEqualTo(sealed).doesNotContain("sentinel-provider-secret");
        assertThat(store.credentialVersions(account)).containsExactly(
                new EncryptedCredential("provider.key", version, sealed));
        assertThatThrownBy(() -> store.replaceCredential(account, draft.draftId(), draft.revision(),
                draft.baseSnapshotId(), new EncryptedCredential("provider.key", version, sealed)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void valueInventorySurvivesDocumentChangesAndImportMergesByExactName() {
        Path db = directory.resolve("import.db");
        var data = StorageConfiguration.dataSource(db);
        StorageConfiguration.migrate(data);
        var jdbc = new JdbcTemplate(data);
        long account = new ManagementAccountRepository(jdbc).insert("destination@example.test", "editor", 1);
        var store = new ConfigurationDraftStore(jdbc, new TransactionTemplate(new DataSourceTransactionManager(data)));
        var base = new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION),
                SnapshotStatus.PUBLISHED);
        var cipher = new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var kept = new EncryptedCredential("region/Name", "v1", cipher.encrypt("region/Name", "v1", "west"));
        var added = new EncryptedCredential("region/name", "v1", cipher.encrypt("region/name", "v1", "east"));
        var loaded = store.createIfAbsent(account, base);
        loaded = store.replaceCredential(account, loaded.draftId(), loaded.revision(), base.localId(), kept);
        var imported = new ManagedConfiguration(List.of(), "loomspan: {}\\n", List.of("unconfigured"));
        loaded = store.replace(account, loaded.draftId(), loaded.revision(), base.localId(),
                base.localId(), imported, UUID.randomUUID());
        assertThat(loaded.configuration().credentialIdentifiers()).containsExactly("region/Name");
        loaded = store.replace(account, loaded.draftId(), loaded.revision(), base.localId(),
                base.localId(), imported, UUID.randomUUID(), List.of(added), false);
        assertThat(loaded.configuration().credentialIdentifiers()).containsExactly("region/Name", "region/name");
        var rolledBack = store.replace(account, loaded.draftId(), loaded.revision(), base.localId(),
                base.localId(), base.configuration(), UUID.randomUUID(), List.of(kept), true);
        assertThat(rolledBack.configuration().credentialIdentifiers()).containsExactly("region/Name");
        assertThat(store.credentialVersions(account)).containsExactly(kept);
    }
}
