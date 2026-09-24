package ai.loomspan.sidecar.management;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/** SQL operations; the service owns transaction boundaries and policy. */
public final class ManagementAccountRepository {
    public record Account(long id, String email, String role, boolean enabled, String passwordHash, long version) {
        public boolean active() { return enabled && passwordHash != null; }
        @Override public String toString() { return "Account[id=" + id + ", redacted]"; }
    }
    public record Token(long accountId, String purpose, long expiresAt, Long consumedAt) {}
    private final JdbcTemplate jdbc;

    public ManagementAccountRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Account byEmail(String email) {
        return jdbc.query("SELECT id,email,role,enabled,password_hash,credential_version FROM management_account WHERE email=?",
                (rs, row) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4) == 1,
                        rs.getString(5), rs.getLong(6)), email).stream().findFirst().orElse(null);
    }
    public Account byId(long id) {
        return jdbc.query("SELECT id,email,role,enabled,password_hash,credential_version FROM management_account WHERE id=?",
                (rs, row) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4) == 1,
                        rs.getString(5), rs.getLong(6)), id).stream().findFirst().orElse(null);
    }
    public List<Account> all() {
        return jdbc.query("SELECT id,email,role,enabled,password_hash,credential_version FROM management_account ORDER BY id",
                (rs, row) -> new Account(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4) == 1,
                        rs.getString(5), rs.getLong(6)));
    }
    public String bootstrapState() {
        return jdbc.queryForObject("SELECT state FROM management_bootstrap WHERE singleton=1", String.class);
    }
    public Long reservedId() {
        return jdbc.queryForObject("SELECT account_id FROM management_bootstrap WHERE singleton=1", Long.class);
    }
    public long insert(String email, String role, long now) {
        jdbc.update("INSERT INTO management_account(email,role,created_at) VALUES (?,?,?)", email, role, now);
        return byEmail(email).id();
    }
    public boolean reserve(long id) {
        return jdbc.update("UPDATE management_bootstrap SET state='reserved',account_id=? WHERE singleton=1 AND state='unreserved'", id) == 1;
    }
    public boolean activateBootstrap(long id) {
        return jdbc.update("UPDATE management_bootstrap SET state='activated' WHERE singleton=1 AND state='reserved' AND account_id=?", id) == 1;
    }
    public void insertToken(String digest, long id, String purpose, long expiry) {
        jdbc.update("INSERT INTO management_token(digest,account_id,purpose,expires_at) VALUES (?,?,?,?)",
                digest, id, purpose, expiry);
    }
    public Token token(String digest) {
        return jdbc.query("SELECT account_id,purpose,expires_at,consumed_at FROM management_token WHERE digest=?",
                (rs, row) -> new Token(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getObject(4) == null ? null : rs.getLong(4)), digest).stream().findFirst().orElse(null);
    }
    public void consumeTokens(long id, long now) {
        jdbc.update("UPDATE management_token SET consumed_at=? WHERE account_id=? AND consumed_at IS NULL", now, id);
    }
    public boolean consume(String digest, long now) {
        return jdbc.update("UPDATE management_token SET consumed_at=? WHERE digest=? AND consumed_at IS NULL", now, digest) == 1;
    }
    public void password(long id, String hash) {
        jdbc.update("UPDATE management_account SET password_hash=?,credential_version=credential_version+1 WHERE id=?", hash, id);
    }
    public boolean alter(long id, String role, boolean enabled) {
        // The write statement itself guards the last activated administrator, including concurrent writers.
        return jdbc.update("UPDATE management_account SET role=?,enabled=?,credential_version=credential_version+1 "
                + "WHERE id=? AND (NOT (role='admin' AND enabled=1 AND password_hash IS NOT NULL AND (?<>'admin' OR ?=0)) "
                + "OR (SELECT COUNT(*) FROM management_account WHERE role='admin' AND enabled=1 AND password_hash IS NOT NULL)>1)",
                role, enabled ? 1 : 0, id, role, enabled ? 1 : 0) == 1;
    }
}
