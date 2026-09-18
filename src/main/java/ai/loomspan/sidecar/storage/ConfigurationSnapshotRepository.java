package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** SQL operations only; callers use the store for transaction boundaries. */
public final class ConfigurationSnapshotRepository
{
    private final NamedParameterJdbcTemplate jdbc;

    public ConfigurationSnapshotRepository(NamedParameterJdbcTemplate jdbc)
    {
        this.jdbc = jdbc;
    }

    public ConfigurationSnapshot insert(ManagedConfiguration configuration, UUID sourceId)
    {
        Objects.requireNonNull(configuration, "configuration");
        UUID localId = UUID.randomUUID();
        var params = new MapSqlParameterSource()
                .addValue("localId", localId.toString())
                .addValue("sourceId", sourceId == null ? null : sourceId.toString())
                .addValue("count", configuration.skillDocuments().size())
                .addValue("rest", configuration.restRoutesYaml());
        var key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO configuration_snapshot(local_id, source_id, document_count, rest_routes_yaml) "
                + "VALUES (:localId, :sourceId, :count, :rest)", params, key, new String[] {"submission_sequence"});
        long sequence = Objects.requireNonNull(key.getKey()).longValue();
        for (int ordinal = 0; ordinal < configuration.skillDocuments().size(); ordinal++)
        {
            SkillDocument document = configuration.skillDocuments().get(ordinal);
            jdbc.update("INSERT INTO configuration_skill_document(snapshot_sequence, ordinal, label, yaml) "
                    + "VALUES (:sequence, :ordinal, :label, :yaml)", new MapSqlParameterSource()
                    .addValue("sequence", sequence).addValue("ordinal", ordinal)
                    .addValue("label", document.sourceName()).addValue("yaml", document.yaml()));
        }
        jdbc.update("INSERT INTO configuration_snapshot_status(snapshot_sequence, status) VALUES (:sequence, :status)",
                new MapSqlParameterSource().addValue("sequence", sequence)
                        .addValue("status", SnapshotStatus.PENDING.databaseValue()));
        return new ConfigurationSnapshot(localId, sourceId, sequence, configuration, SnapshotStatus.PENDING);
    }

    public ConfigurationSnapshot findByLocalId(UUID localId)
    {
        Long sequence = jdbc.query("SELECT submission_sequence FROM configuration_snapshot WHERE local_id = :id",
                new MapSqlParameterSource("id", localId.toString()), rs -> rs.next() ? rs.getLong(1) : null);
        return sequence == null ? null : requireBySequence(sequence);
    }

    List<ConfigurationSnapshot> history() {
        return jdbc.query("SELECT submission_sequence FROM configuration_snapshot ORDER BY submission_sequence",
                (rs, row) -> rs.getLong(1)).stream().map(this::requireBySequence).toList();
    }

    ConfigurationSnapshot requireBySequence(long sequence)
    {
        var rows = jdbc.query("SELECT s.local_id, s.source_id, s.document_count, s.rest_routes_yaml, t.status "
                + "FROM configuration_snapshot s LEFT JOIN configuration_snapshot_status t "
                + "ON t.snapshot_sequence = s.submission_sequence WHERE s.submission_sequence = :sequence",
                new MapSqlParameterSource("sequence", sequence), (rs, row) -> new Header(
                        UUID.fromString(rs.getString(1)), rs.getString(2) == null ? null : UUID.fromString(rs.getString(2)),
                        rs.getInt(3), rs.getString(4), rs.getString(5)));
        if (rows.size() != 1 || rows.getFirst().status == null)
        {
            throw new IllegalStateException("Missing or incomplete configuration snapshot");
        }
        Header header = rows.getFirst();
        var documents = jdbc.query("SELECT ordinal, label, yaml FROM configuration_skill_document "
                + "WHERE snapshot_sequence = :sequence ORDER BY ordinal", new MapSqlParameterSource("sequence", sequence),
                (rs, row) -> new DocumentRow(rs.getInt(1), new SkillDocument(rs.getString(2), rs.getString(3))));
        if (documents.size() != header.documentCount)
        {
            throw new IllegalStateException("Incomplete configuration snapshot documents");
        }
        for (int i = 0; i < documents.size(); i++)
        {
            if (documents.get(i).ordinal != i)
            {
                throw new IllegalStateException("Invalid configuration document order");
            }
        }
        List<SkillDocument> content = documents.stream().map(DocumentRow::document).toList();
        return new ConfigurationSnapshot(header.localId, header.sourceId, sequence,
                new ManagedConfiguration(content, header.restYaml), SnapshotStatus.fromDatabase(header.status));
    }

    public void updateStatus(UUID localId, SnapshotStatus status)
    {
        Objects.requireNonNull(status, "status");
        int changed = jdbc.update("UPDATE configuration_snapshot_status SET status = :status "
                + "WHERE snapshot_sequence = (SELECT submission_sequence FROM configuration_snapshot WHERE local_id = :id)",
                new MapSqlParameterSource().addValue("status", status.databaseValue()).addValue("id", localId.toString()));
        if (changed != 1)
        {
            throw new IllegalArgumentException("Unknown configuration snapshot");
        }
    }

    Long sequenceFor(UUID localId)
    {
        return jdbc.query("SELECT submission_sequence FROM configuration_snapshot WHERE local_id = :id",
                new MapSqlParameterSource("id", localId.toString()), rs -> rs.next() ? rs.getLong(1) : null);
    }

    void switchCurrent(long expectedSequence, long nextSequence)
    {
        int changed = jdbc.update("UPDATE configuration_store_state SET current_snapshot_sequence = :next "
                + "WHERE singleton = 1 AND initialized = 1 AND current_snapshot_sequence = :expected",
                new MapSqlParameterSource().addValue("next", nextSequence).addValue("expected", expectedSequence));
        if (changed != 1)
        {
            throw new IllegalStateException("Current configuration snapshot changed unexpectedly");
        }
    }

    List<SnapshotId> snapshotIdsOldestFirst()
    {
        return jdbc.query("SELECT submission_sequence, local_id FROM configuration_snapshot ORDER BY submission_sequence",
                (rs, row) -> new SnapshotId(rs.getLong(1), UUID.fromString(rs.getString(2))));
    }

    void deleteBySequence(long sequence)
    {
        jdbc.update("DELETE FROM configuration_snapshot WHERE submission_sequence = :sequence",
                new MapSqlParameterSource("sequence", sequence));
    }

    State state()
    {
        var rows = jdbc.query("SELECT initialized, current_snapshot_sequence FROM configuration_store_state WHERE singleton = 1",
                (rs, row) -> new State(rs.getInt(1) == 1, rs.getObject(2) == null ? null : rs.getLong(2)));
        if (rows.size() != 1)
        {
            throw new IllegalStateException("Cannot load configuration store state; stop Sidecar and restore a consistent full database backup");
        }
        return rows.getFirst();
    }

    long snapshotCount()
    {
        return Objects.requireNonNull(jdbc.getJdbcTemplate().queryForObject(
                "SELECT COUNT(*) FROM configuration_snapshot", Long.class));
    }

    void setInitialCurrent(long sequence)
    {
        int changed = jdbc.update("UPDATE configuration_store_state SET initialized = 1, current_snapshot_sequence = :sequence "
                + "WHERE singleton = 1 AND initialized = 0 AND current_snapshot_sequence IS NULL",
                new MapSqlParameterSource("sequence", sequence));
        if (changed != 1)
        {
            throw new IllegalStateException("Configuration store already initialized");
        }
    }

    record State(boolean initialized, Long currentSequence) {}
    record SnapshotId(long sequence, UUID localId) {}
    private record Header(UUID localId, UUID sourceId, int documentCount, String restYaml, String status) {}
    private record DocumentRow(int ordinal, SkillDocument document) {}
}
