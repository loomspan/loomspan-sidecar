package ai.loomspan.sidecar.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public final class ManagementPersonalTokenService {
    private static final Logger audit = LoggerFactory.getLogger("sidecar.management.audit");
    private static final SecureRandom random = new SecureRandom();
    private static final long MAX_LIFETIME = Duration.ofDays(30).toMillis();
    public record Metadata(String id, String preset, Instant createdAt, Instant expiresAt, Instant revokedAt) {}
    public record Issued(Metadata token, String secret) {
        @Override public String toString() { return "IssuedPersonalToken[redacted]"; }
    }
    public record Verified(String id, long owner, String preset) {}
    public static final class Rejected extends RuntimeException {
        public Rejected() { super("Personal token request rejected"); }
    }
    private final ManagementPersonalTokenRepository repository;
    private final ManagementIdentityService identity;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ManagementEditingState editing;
    private final ai.loomspan.sidecar.configuration.RuntimeConfigurationService runtime;

    public ManagementPersonalTokenService(ManagementPersonalTokenRepository repository, ManagementIdentityService identity,
            TransactionTemplate tx, Clock clock, ManagementEditingState editing,
            ai.loomspan.sidecar.configuration.RuntimeConfigurationService runtime) {
        this.repository = repository; this.identity = identity; this.tx = tx; this.clock = clock;
        this.editing = editing; this.runtime = runtime;
    }
    public Issued issue(long owner, long credentialVersion, String preset, Instant expiry) {
        if (!("read".equals(preset) || "edit".equals(preset) || "publish".equals(preset))) throw new Rejected();
        long now = clock.millis();
        long until = expiry == null ? now + Duration.ofDays(7).toMillis() : expiry.toEpochMilli();
        if (until <= now || until - now > MAX_LIFETIME) throw new Rejected();
        String id = random(16), secret = random(32);
        var token = new ManagementPersonalTokenRepository.Token(id, owner, digest(secret), preset, now, until, null);
        runtime.withEditingTransition(() -> {
            synchronized (editing) {
                tx.executeWithoutResult(status -> {
                    var account = identity.account(owner);
                    if (account == null || !account.active() || account.version() != credentialVersion
                            || repository.activeCount(owner, now) >= 20
                            || repository.issuedSince(owner, now - Duration.ofHours(1).toMillis()) >= 5) throw new Rejected();
                    repository.insert(token);
                });
            }
        });
        audit.info("action=pat.issue actor={} token={} outcome=success", owner, id);
        return new Issued(metadata(token), "lspat_" + id + "." + secret);
    }
    public List<Metadata> list(long owner) { return repository.byOwner(owner).stream().map(ManagementPersonalTokenService::metadata).toList(); }
    public void revoke(long owner, String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{22}")) throw new Rejected();
        boolean[] changed = {false};
        runtime.withEditingTransition(() -> {
            synchronized (editing) {
                changed[0] = tx.execute(status -> repository.revoke(owner, id, clock.millis()));
                if (changed[0]) editing.clearOrigin("pat:" + id);
            }
        });
        audit.info("action=pat.revoke actor={} token={} outcome={}", owner, id, changed[0] ? "revoked" : "not_owned_or_already_revoked");
    }
    public Verified authenticate(String raw) {
        if (raw == null || raw.length() != 72 || !raw.matches("lspat_[A-Za-z0-9_-]{22}\\.[A-Za-z0-9_-]{43}")) return null;
        String id = raw.substring(6, 28), secret = raw.substring(29);
        var token = repository.byId(id);
        if (token == null || token.revokedAt() != null || token.expiresAt() <= clock.millis()) return null;
        if (!MessageDigest.isEqual(token.digest().getBytes(StandardCharsets.US_ASCII),
                digest(secret).getBytes(StandardCharsets.US_ASCII))) return null;
        var account = identity.account(token.accountId());
        return account == null || !account.active() ? null : new Verified(id, token.accountId(), token.preset());
    }
    public boolean valid(String id, long owner, String required) {
        var token = repository.byId(id);
        var account = identity.account(owner);
        return token != null && token.accountId() == owner && token.revokedAt() == null
                && token.expiresAt() > clock.millis() && account != null && account.active()
                && permits(token.preset(), required) && (!"edit".equals(required) && !"publish".equals(required)
                    || !"viewer".equals(account.role()));
    }
    public void revokeAll(long owner) { repository.revokeAll(owner, clock.millis()); }
    public static boolean permits(String actual, String required) {
        int level = switch (actual) { case "publish" -> 3; case "edit" -> 2; case "read" -> 1; default -> 0; };
        int need = switch (required) { case "publish" -> 3; case "edit" -> 2; case "read" -> 1; default -> 4; };
        return level >= need;
    }
    private static Metadata metadata(ManagementPersonalTokenRepository.Token token) {
        return new Metadata(token.id(), token.preset(), Instant.ofEpochMilli(token.createdAt()),
                Instant.ofEpochMilli(token.expiresAt()), token.revokedAt() == null ? null : Instant.ofEpochMilli(token.revokedAt()));
    }
    private static String random(int size) {
        byte[] bytes = new byte[size]; random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String digest(String secret) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
