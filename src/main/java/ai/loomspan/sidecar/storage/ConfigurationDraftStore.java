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
            long revision, ManagedConfiguration configuration, boolean retainedSource) {
        public Saved(long accountId, UUID draftId, UUID baseSnapshotId, UUID sourceSnapshotId,
                long revision, ManagedConfiguration configuration) {
            this(accountId, draftId, baseSnapshotId, sourceSnapshotId, revision, configuration, false);
        }
    }

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public ConfigurationDraftStore(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    public Saved read(long accountId) {
        return transactions.execute(ignored -> {
            var rows = jdbc.query("SELECT draft_id, base_snapshot_id, source_snapshot_id, revision, "
                    + "document_count, rest_routes_yaml, execution_configuration_yaml, retained_source "
                    + "FROM management_draft WHERE account_id = ?",
                    (rs, row) -> new Header(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)),
                            rs.getString(3) == null ? null : UUID.fromString(rs.getString(3)),
                            rs.getLong(4), rs.getInt(5), rs.getString(6), rs.getString(7), rs.getInt(8) == 1), accountId);
            if (rows.isEmpty()) return null;
            Header header = rows.getFirst();
            var documents = jdbc.query("SELECT ordinal, label, yaml FROM management_draft_document "
                    + "WHERE account_id = ? ORDER BY ordinal",
                    (rs, row) -> new Document(rs.getInt(1), new SkillDocument(rs.getString(2), rs.getString(3))),
                    accountId);
            if (documents.size() != header.count) throw new IllegalStateException("Incomplete saved draft");
            for (int i = 0; i < documents.size(); i++)
                if (documents.get(i).ordinal != i) throw new IllegalStateException("Invalid saved draft document order");
            var identifiers = requiredCredentialIdentifiers(accountId);
            return new Saved(accountId, header.id, header.baseId, header.sourceId, header.revision,
                    new ManagedConfiguration(documents.stream().map(Document::content).toList(),
                            header.rest, header.execution, identifiers), header.retainedSource);
        });
    }

    public Saved createIfAbsent(long accountId, ConfigurationSnapshot base) {
        transactions.executeWithoutResult(ignored -> {
            int inserted = jdbc.update("INSERT OR IGNORE INTO management_draft(account_id, draft_id, base_snapshot_id, "
                    + "source_snapshot_id, revision, document_count, rest_routes_yaml, "
                    + "execution_configuration_yaml) VALUES (?, ?, ?, NULL, 1, ?, ?, ?)",
                    accountId, UUID.randomUUID().toString(), base.localId().toString(),
                    base.configuration().skillDocuments().size(), base.configuration().restRoutesYaml(),
                    base.configuration().executionConfigurationYaml());
            if (inserted == 1) {
                insertDocuments(accountId, base.configuration());
                    jdbc.update("INSERT INTO management_draft_credential(account_id, identifier, version, ciphertext) "
                            + "SELECT ?, identifier, version, ciphertext FROM configuration_snapshot_credential "
                            + "WHERE snapshot_sequence = ?", accountId, base.submissionSequence());
                for (String identifier : base.configuration().credentialIdentifiers())
                    jdbc.update("INSERT INTO management_draft_credential_requirement(account_id, identifier) VALUES (?, ?)",
                            accountId, identifier);
            }
        });
        return read(accountId);
    }

    public Saved replace(long accountId, UUID draftId, long expectedRevision, UUID expectedBaseId,
            UUID nextBaseId, ManagedConfiguration configuration, UUID sourceId) {
        return replace(accountId, draftId, expectedRevision, expectedBaseId, nextBaseId,
                configuration, sourceId, null);
    }

    public Saved replace(long accountId, UUID draftId, long expectedRevision, UUID expectedBaseId,
            UUID nextBaseId, ManagedConfiguration configuration, UUID sourceId,
            List<EncryptedCredential> credentials) {
        Objects.requireNonNull(configuration);
        transactions.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE management_draft SET base_snapshot_id = ?, source_snapshot_id = ?, "
                    + "revision = revision + 1, document_count = ?, rest_routes_yaml = ?, "
                    + "execution_configuration_yaml = ?, retained_source = ? "
                    + "WHERE account_id = ? AND draft_id = ? AND revision = ? AND base_snapshot_id = ?",
                    nextBaseId.toString(), sourceId == null ? null : sourceId.toString(),
                    configuration.skillDocuments().size(), configuration.restRoutesYaml(),
                    configuration.executionConfigurationYaml(),
                    credentials == null ? 0 : 1,
                    accountId, draftId.toString(), expectedRevision, expectedBaseId.toString());
            if (changed != 1) throw new IllegalStateException("Saved draft changed");
            jdbc.update("DELETE FROM management_draft_document WHERE account_id = ?", accountId);
            insertDocuments(accountId, configuration);
            if (credentials != null) {
                jdbc.update("DELETE FROM management_draft_credential WHERE account_id = ?", accountId);
                for (EncryptedCredential credential : credentials)
                    jdbc.update("INSERT INTO management_draft_credential(account_id, identifier, version, ciphertext) "
                            + "VALUES (?, ?, ?, ?)", accountId, credential.identifier(),
                            credential.version(), credential.ciphertext());
            }
            if (sourceId != null) {
                if (credentials == null) {
                    for (var retained : credentialVersions(accountId))
                        if (!configuration.credentialIdentifiers().contains(retained.identifier()))
                            jdbc.update("DELETE FROM management_draft_credential WHERE account_id = ? AND identifier = ?",
                                    accountId, retained.identifier());
                }
                jdbc.update("DELETE FROM management_draft_credential_requirement WHERE account_id = ?", accountId);
                for (String identifier : configuration.credentialIdentifiers())
                    jdbc.update("INSERT INTO management_draft_credential_requirement(account_id, identifier) VALUES (?, ?)",
                            accountId, identifier);
            }
        });
        return read(accountId);
    }

    public boolean delete(long accountId, UUID draftId, long revision) {
        return Boolean.TRUE.equals(transactions.execute(ignored -> jdbc.update(
                "DELETE FROM management_draft WHERE account_id = ? AND draft_id = ? AND revision = ?",
                accountId, draftId.toString(), revision) == 1));
    }

    public List<EncryptedCredential> credentialVersions(long accountId) {
        return jdbc.query("SELECT identifier, version, ciphertext FROM management_draft_credential "
                + "WHERE account_id = ? ORDER BY identifier",
                (rs, row) -> new EncryptedCredential(rs.getString(1), rs.getString(2), rs.getString(3)), accountId);
    }

    public List<String> requiredCredentialIdentifiers(long accountId) {
        return jdbc.queryForList("SELECT identifier FROM management_draft_credential_requirement "
                + "WHERE account_id = ? ORDER BY identifier", String.class, accountId);
    }

    public Saved replaceCredential(long accountId, UUID draftId, long revision, UUID baseId,
            EncryptedCredential credential) {
        transactions.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE management_draft SET revision = revision + 1 "
                    + "WHERE account_id = ? AND draft_id = ? AND revision = ? AND base_snapshot_id = ?",
                    accountId, draftId.toString(), revision, baseId.toString());
            if (changed != 1) throw new IllegalStateException("Saved draft changed");
            jdbc.update("INSERT INTO management_draft_credential(account_id, identifier, version, ciphertext) "
                    + "VALUES (?, ?, ?, ?) ON CONFLICT(account_id, identifier) DO UPDATE SET "
                    + "version = excluded.version, ciphertext = excluded.ciphertext",
                    accountId, credential.identifier(), credential.version(), credential.ciphertext());
            jdbc.update("INSERT OR IGNORE INTO management_draft_credential_requirement(account_id, identifier) "
                    + "VALUES (?, ?)", accountId, credential.identifier());
        });
        return read(accountId);
    }

    public Saved removeCredential(long accountId, UUID draftId, long revision, UUID baseId,
            String identifier) {
        transactions.executeWithoutResult(ignored -> {
            int changed = jdbc.update("UPDATE management_draft SET revision = revision + 1 "
                    + "WHERE account_id = ? AND draft_id = ? AND revision = ? AND base_snapshot_id = ?",
                    accountId, draftId.toString(), revision, baseId.toString());
            if (changed != 1) throw new IllegalStateException("Saved draft changed");
            int removed = jdbc.update("DELETE FROM management_draft_credential WHERE account_id = ? AND identifier = ?",
                    accountId, identifier);
            removed += jdbc.update("DELETE FROM management_draft_credential_requirement WHERE account_id = ? AND identifier = ?",
                    accountId, identifier);
            if (removed == 0) throw new IllegalArgumentException("Credential identifier is not configured or required");
        });
        return read(accountId);
    }

    private void insertDocuments(long accountId, ManagedConfiguration configuration) {
        List<SkillDocument> documents = configuration.skillDocuments();
        for (int i = 0; i < documents.size(); i++)
            jdbc.update("INSERT INTO management_draft_document(account_id, ordinal, label, yaml) VALUES (?, ?, ?, ?)",
                    accountId, i, documents.get(i).sourceName(), documents.get(i).yaml());
    }

    private record Header(UUID id, UUID baseId, UUID sourceId, long revision, int count,
            String rest, String execution, boolean retainedSource) {}
    private record Document(int ordinal, SkillDocument content) {}
}
