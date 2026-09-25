package ai.loomspan.sidecar.storage;

import ai.loomspan.api.SkillDocument;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Transactional, account-owned saved content. Editing grants and validation never enter this store. */
public final class ConfigurationDraftStore {
    public record Saved(long accountId, UUID draftId, UUID baseSnapshotId, UUID sourceSnapshotId,
            long revision, ManagedConfiguration configuration) {}

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public ConfigurationDraftStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public Saved read(long accountId) {
        return transactions.execute(ignored -> {
            var rows = jdbc.query("SELECT draft_id, base_snapshot_id, source_snapshot_id, revision, "
                    + "document_count, rest_routes_yaml FROM management_draft WHERE account_id = ?",
                    (rs, row) -> new Header(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                            rs.getString(3) == null ? null : UUID.fromString(rs.getString(3)),
                            rs.getLong(4), rs.getInt(5), rs.getString(6)), accountId);
            if (rows.isEmpty()) return null;
            Header header = rows.getFirst();
            var documents = jdbc.query("SELECT ordinal, label, yaml FROM management_draft_document "
                    + "WHERE account_id = ? ORDER BY ordinal",
                    (rs, row) -> new Document(rs.getInt(1), new SkillDocument(rs.getString(2), rs.getString(3))),
                    accountId);
            if (documents.size() != header.count) throw new IllegalStateException("Incomplete saved draft");
            for (int i = 0; i < documents.size(); i++)
                if (documents.get(i).ordinal != i) throw new IllegalStateException("Invalid saved draft document order");
            return new Saved(accountId, header.id, header.baseId, header.sourceId, header.revision,
                    new ManagedConfiguration(documents.stream().map(Document::content).toList(), header.rest));
        });
    }

    public Saved createIfAbsent(long accountId, ConfigurationSnapshot base) {
        transactions.executeWithoutResult(ignored -> {
            int inserted = jdbc.update("INSERT OR IGNORE INTO management_draft(account_id, draft_id, base_snapshot_id, "
                    + "source_snapshot_id, revision, document_count, rest_routes_yaml) VALUES (?, ?, ?, NULL, 1, ?, ?)",
                    accountId, UUID.randomUUID().toString(), base.localId().toString(),
                    base.configuration().skillDocuments().size(), base.configuration().restRoutesYaml());
            if (inserted == 1) insertDocuments(accountId, base.configuration());
        });
        return read(accountId);
    }

    public Saved replace(long accountId, UUID draftId, long expectedRevision, UUID expectedBaseId,
            UUID nextBaseId, ManagedConfiguration configuration, UUID sourceId) {
        Objects.requireNonNull(configuration);
        transactions.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE management_draft SET base_snapshot_id = ?, source_snapshot_id = ?, "
                    + "revision = revision + 1, document_count = ?, rest_routes_yaml = ? "
                    + "WHERE account_id = ? AND draft_id = ? AND revision = ? AND base_snapshot_id = ?",
                    nextBaseId.toString(), sourceId == null ? null : sourceId.toString(),
                    configuration.skillDocuments().size(), configuration.restRoutesYaml(),
                    accountId, draftId.toString(), expectedRevision, expectedBaseId.toString());
            if (changed != 1) throw new IllegalStateException("Saved draft changed");
            jdbc.update("DELETE FROM management_draft_document WHERE account_id = ?", accountId);
            insertDocuments(accountId, configuration);
        });
        return read(accountId);
    }

    public boolean delete(long accountId, UUID draftId, long revision) {
        return Boolean.TRUE.equals(transactions.execute(ignored -> jdbc.update(
                "DELETE FROM management_draft WHERE account_id = ? AND draft_id = ? AND revision = ?",
                accountId, draftId.toString(), revision) == 1));
    }

    private void insertDocuments(long accountId, ManagedConfiguration configuration) {
        List<SkillDocument> documents = configuration.skillDocuments();
        for (int i = 0; i < documents.size(); i++)
            jdbc.update("INSERT INTO management_draft_document(account_id, ordinal, label, yaml) VALUES (?, ?, ?, ?)",
                    accountId, i, documents.get(i).sourceName(), documents.get(i).yaml());
    }

    private record Header(UUID id, UUID baseId, UUID sourceId, long revision, int count, String rest) {}
    private record Document(int ordinal, SkillDocument content) {}
}
