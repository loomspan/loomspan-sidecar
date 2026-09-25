package ai.loomspan.sidecar.management;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ManagementPersonalTokenRepository {
    public record Token(String id, long accountId, String digest, String preset, long createdAt,
            long expiresAt, Long revokedAt) {
        @Override public String toString() { return "PersonalToken[id=" + id + ", redacted]"; }
    }
    private final JdbcTemplate jdbc;
    public ManagementPersonalTokenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Token byId(String id) {
        return jdbc.query("SELECT id,account_id,secret_digest,preset,created_at,expires_at,revoked_at FROM management_personal_token WHERE id=?",
                (rs, row) -> new Token(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getLong(6), rs.getObject(7) == null ? null : rs.getLong(7)), id)
                .stream().findFirst().orElse(null);
    }
    public List<Token> byOwner(long owner) {
        return jdbc.query("SELECT id,account_id,secret_digest,preset,created_at,expires_at,revoked_at FROM management_personal_token WHERE account_id=? ORDER BY created_at DESC,id DESC",
                (rs, row) -> new Token(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4),
                        rs.getLong(5), rs.getLong(6), rs.getObject(7) == null ? null : rs.getLong(7)), owner);
    }
    public int activeCount(long owner, long now) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM management_personal_token WHERE account_id=? AND revoked_at IS NULL AND expires_at>?",
                Integer.class, owner, now);
    }
    public int issuedSince(long owner, long since) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM management_personal_token WHERE account_id=? AND created_at>?",
                Integer.class, owner, since);
    }
    public void insert(Token token) {
        jdbc.update("INSERT INTO management_personal_token(id,account_id,secret_digest,preset,created_at,expires_at) VALUES (?,?,?,?,?,?)",
                token.id(), token.accountId(), token.digest(), token.preset(), token.createdAt(), token.expiresAt());
    }
    public boolean revoke(long owner, String id, long now) {
        return jdbc.update("UPDATE management_personal_token SET revoked_at=? WHERE account_id=? AND id=? AND revoked_at IS NULL",
                now, owner, id) == 1;
    }
    public void revokeAll(long owner, long now) {
        jdbc.update("UPDATE management_personal_token SET revoked_at=? WHERE account_id=? AND revoked_at IS NULL", now, owner);
    }
}
