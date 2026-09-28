package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationSnapshotRepositoryTest
{
    @TempDir Path directory;

    @Test
    void reopensCompleteAuthoredSnapshotWithoutChangingEitherHalf()
    {
        Path file = directory.resolve("store.db");
        StoreFixture first = open(file);
        UUID source = UUID.randomUUID();
        String skills = "# original\nname: answer\nmodel: primary\n";
        String rest = "# authored\ntargets:\n  customer:\n    base-url: ${CUSTOMER_API_URL}\n"
                + "    auth: {mode: static, headers: {X-Secret: literal-secret-sentinel}}\nroutes: {}\n";
        var configuration = new ManagedConfiguration(List.of(new SkillDocument("a.yaml", skills),
                new SkillDocument("B.yml", "name: callback\nrest: true\n")), rest, ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION);
        ConfigurationSnapshot written = first.store.submit(configuration, source, first.store.current().localId());

        StoreFixture reopened = open(file);
        ConfigurationSnapshot read = reopened.store.findByLocalId(written.localId());
        assertThat(read).isEqualTo(written);
        assertThat(read.configuration().restRoutesYaml()).isEqualTo(rest);
        assertThat(read.configuration().skillDocuments()).extracting(SkillDocument::sourceName)
                .containsExactly("a.yaml", "B.yml");
        assertThat(read.configuration().skillDocuments().getFirst().yaml()).isEqualTo(skills);
        assertThat(read.status()).isEqualTo(SnapshotStatus.PENDING);
        assertThat(read.sourceId()).isEqualTo(source);
    }

    @Test
    void rejectsInvalidDocumentLabelsAndNullContent()
    {
        assertThatThrownBy(() -> new ManagedConfiguration(null, "", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(), null, ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(java.util.Arrays.asList((SkillDocument) null), "", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(new SkillDocument(" ", "")), "", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(new SkillDocument("same", ""),
                new SkillDocument("same", "")), "", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(new SkillDocument("x", null)), "", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ManagedConfiguration(List.of(new SkillDocument("x", ""), new SkillDocument("X", "")), "", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION)
                .skillDocuments()).hasSize(2);
    }

    @Test
    void allocatesFreshLocalIdentityAndKeepsContentImmutable()
    {
        StoreFixture fixture = open(directory.resolve("identity.db"));
        var mutable = new ArrayList<>(List.of(new SkillDocument("one.yaml", "first\n")));
        var configuration = new ManagedConfiguration(mutable, "targets: {}\nroutes: {}\n", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION);
        mutable.clear();
        UUID source = UUID.randomUUID();
        var one = fixture.store.submit(configuration, source, fixture.store.current().localId());
        var two = fixture.store.submit(configuration, source, one.localId());
        fixture.store.updateStatus(one.localId(), SnapshotStatus.FAILED);

        assertThat(one.localId()).isNotEqualTo(two.localId());
        assertThat(two.submissionSequence()).isGreaterThan(one.submissionSequence());
        assertThat(fixture.store.findByLocalId(one.localId()).sourceId()).isEqualTo(source);
        assertThat(fixture.store.findByLocalId(one.localId()).configuration()).isEqualTo(configuration);
        assertThat(fixture.store.findByLocalId(one.localId()).status()).isEqualTo(SnapshotStatus.FAILED);
        assertThat(fixture.store.findByLocalId(two.localId()).status()).isEqualTo(SnapshotStatus.PENDING);
        assertThatThrownBy(() -> fixture.store.findByLocalId(one.localId()).configuration().skillDocuments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(fixture.store.current().status()).isEqualTo(SnapshotStatus.PENDING);
        var jdbc = new JdbcTemplate(fixture.source);
        assertThatThrownBy(() -> jdbc.update("UPDATE configuration_snapshot SET rest_routes_yaml = 'changed' "
                + "WHERE submission_sequence = ?", one.submissionSequence())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE configuration_skill_document SET yaml = 'changed' "
                + "WHERE snapshot_sequence = ?", one.submissionSequence())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE configuration_snapshot_status SET status = 'unknown' "
                + "WHERE snapshot_sequence = ?", one.submissionSequence())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void statusRecordingDoesNotChangeSelectionOrOlderPendingHistory()
    {
        StoreFixture fixture = open(directory.resolve("status-recording.db"));
        var initial = fixture.store.current();
        var a = fixture.store.submit(new ManagedConfiguration(List.of(new SkillDocument("a", "name: A")),
                "routes: {a: true}", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), null, initial.localId());
        var b = fixture.store.submit(new ManagedConfiguration(List.of(new SkillDocument("b", "name: B")),
                "routes: {b: true}", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), null, a.localId());
        fixture.store.updateStatus(b.localId(), SnapshotStatus.PUBLISHED);
        assertThat(fixture.store.current().localId()).isEqualTo(b.localId());
        assertThat(fixture.store.current().configuration()).isEqualTo(b.configuration());
        assertThat(fixture.store.current().status()).isEqualTo(SnapshotStatus.PUBLISHED);
        assertThat(fixture.store.findByLocalId(a.localId()).status()).isEqualTo(SnapshotStatus.PENDING);
        assertThatThrownBy(() -> fixture.store.updateStatus(UUID.randomUUID(), SnapshotStatus.FAILED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retainedCredentialIsCiphertextOnlyAndBundleKeepsIdentifier() throws Exception {
        StoreFixture fixture = open(directory.resolve("retained-credential.db"));
        String key = java.util.Base64.getEncoder().encodeToString(new byte[32]);
        var cipher = new ai.loomspan.sidecar.configuration.ProviderCredentialCipher(key);
        String version = UUID.randomUUID().toString();
        String sentinel = "snapshot-secret-sentinel";
        var encrypted = new EncryptedCredential("provider.snapshot.key", version,
                cipher.encrypt("provider.snapshot.key", version, sentinel));
        var authored = new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES,
                "loomspan:\n  connections:\n    primary:\n      driver: openai\n      api-key-ref: provider.snapshot.key\n", List.of("provider.snapshot.key"));
        var snapshot = fixture.store.submit(authored, null, fixture.store.current().localId(),
                authored.executionConfigurationYaml(), List.of(encrypted));
        assertThat(fixture.store.credentialVersions(snapshot)).containsExactly(encrypted);
        var jdbc = new JdbcTemplate(fixture.source);
        assertThat(jdbc.queryForObject("SELECT ciphertext FROM configuration_snapshot_credential WHERE snapshot_sequence = ?",
                String.class, snapshot.submissionSequence())).isEqualTo(encrypted.ciphertext()).doesNotContain(sentinel);
        assertThatThrownBy(() -> jdbc.update("UPDATE configuration_snapshot_credential SET ciphertext = 'changed' "
                + "WHERE snapshot_sequence = ?", snapshot.submissionSequence())).isInstanceOf(RuntimeException.class);
        Path bundle = ai.loomspan.sidecar.bundle.ConfigurationBundleV3.write(snapshot);
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(bundle);
            assertThat(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(sentinel,
                    encrypted.ciphertext());
            var imported = ai.loomspan.sidecar.bundle.ConfigurationBundleV3.read(bundle);
            assertThat(imported.configuration().credentialIdentifiers()).containsExactly("provider.snapshot.key");
        } finally { java.nio.file.Files.deleteIfExists(bundle); }
    }

    private StoreFixture open(Path path)
    {
        DataSource source = StorageConfiguration.dataSource(path);
        StorageConfiguration.migrate(source);
        var repository = new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(source));
        var store = new ConfigurationSnapshotStore(repository,
                new TransactionTemplate(new DataSourceTransactionManager(source)));
        store.initialize();
        return new StoreFixture(store, source);
    }

    private record StoreFixture(ConfigurationSnapshotStore store, DataSource source) {}
}
