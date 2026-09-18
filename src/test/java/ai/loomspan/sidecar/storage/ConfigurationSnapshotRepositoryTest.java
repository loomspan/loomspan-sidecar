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
                new SkillDocument("B.yml", "name: callback\nrest: true\n")), rest);
        ConfigurationSnapshot written = first.store.submit(configuration, source);

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
        assertThatThrownBy(() -> new ManagedConfiguration(null, "")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(), null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(java.util.Arrays.asList((SkillDocument) null), ""))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(new SkillDocument(" ", "")), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(new SkillDocument("same", ""),
                new SkillDocument("same", "")), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ManagedConfiguration(List.of(new SkillDocument("x", null)), ""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new ManagedConfiguration(List.of(new SkillDocument("x", ""), new SkillDocument("X", "")), "")
                .skillDocuments()).hasSize(2);
    }

    @Test
    void allocatesFreshLocalIdentityAndKeepsContentImmutable()
    {
        StoreFixture fixture = open(directory.resolve("identity.db"));
        var mutable = new ArrayList<>(List.of(new SkillDocument("one.yaml", "first\n")));
        var configuration = new ManagedConfiguration(mutable, "targets: {}\nroutes: {}\n");
        mutable.clear();
        UUID source = UUID.randomUUID();
        var one = fixture.store.submit(configuration, source);
        var two = fixture.store.submit(configuration, source);
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
