package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import ai.loomspan.sidecar.management.ManagementAccountRepository.Account;

@Service
public class ManagementIdentityService {
    public static final class Rejected extends RuntimeException {
        public Rejected() { super("Request could not be completed"); }
    }
    private record Issued(long accountId, String email, String purpose, String token) {
        @Override public String toString() { return "Issued[redacted]"; }
    }
    private final ManagementAccountRepository accounts;
    private final TransactionTemplate tx;
    private final ManagementMailService mail;
    private final Clock clock;
    private final Supplier<String> setupCredential;
    private final ManagementEditingState editing;
    private RuntimeConfigurationService runtime;
    private final SecureRandom random = new SecureRandom();
    private final Pbkdf2PasswordEncoder encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
            Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

    public ManagementIdentityService(ManagementAccountRepository accounts, TransactionTemplate tx,
            ManagementMailService mail, Clock clock, Supplier<String> setupCredential, ManagementEditingState editing) {
        this.accounts = accounts;
        this.tx = tx;
        this.mail = mail;
        this.clock = clock;
        this.setupCredential = setupCredential;
        this.editing = editing;
    }

    @org.springframework.beans.factory.annotation.Autowired
    void runtime(RuntimeConfigurationService runtime) { this.runtime = runtime; }

    private void withEditingLock(Runnable action) {
        Runnable locked = () -> { synchronized (editing) { action.run(); } };
        if (runtime == null) locked.run();
        else runtime.withEditingTransition(locked);
    }

    @Configuration(proxyBeanMethods = false)
    static class Wiring {
        @Bean Clock managementClock() { return Clock.systemUTC(); }
        @Bean Supplier<String> managementSetupCredential() { return () -> System.getenv("LOOMSPAN_SIDECAR_SETUP_TOKEN"); }
        @Bean TransactionTemplate managementTransactionTemplate(org.springframework.jdbc.datasource.DataSourceTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    public boolean setupAvailable() { return "unreserved".equals(accounts.bootstrapState()); }
    public String setupState() { return accounts.bootstrapState(); }
    public boolean setupCredentialConfigured() { return decoded(setupCredential.get()) != null; }
    public Account account(long id) { return accounts.byId(id); }
    public Account account(String email) {
        try { return accounts.byEmail(ManagementPolicy.email(email)); }
        catch (IllegalArgumentException invalid) { return null; }
    }
    public List<Account> accounts() { return accounts.all(); }

    public void setup(String suppliedCredential, String email) {
        if (!credentialValid(suppliedCredential)) throw new Rejected();
        String normalized = ManagementPolicy.email(email);
        if (!mail.configured()) throw new ManagementMailService.Unavailable();
        Issued issued;
        try {
            issued = tx.execute(status -> {
                String state = accounts.bootstrapState();
                long id;
                if ("unreserved".equals(state)) {
                    id = accounts.insert(normalized, "admin", clock.millis());
                    if (!accounts.reserve(id)) throw new Rejected();
                } else if ("reserved".equals(state)) {
                    id = accounts.reservedId();
                    Account reserved = accounts.byId(id);
                    if (reserved == null || !reserved.email().equals(normalized) || reserved.passwordHash() != null)
                        throw new Rejected();
                } else throw new Rejected();
                return issue(id, normalized, "set", Duration.ofHours(24));
            });
        } catch (org.springframework.dao.DataAccessException conflict) { throw new Rejected(); }
        mail.send(issued.email(), issued.purpose(), issued.token());
    }

    public Account invite(String email, String role) {
        String normalized = ManagementPolicy.email(email);
        validRole(role);
        if (!mail.configured()) throw new ManagementMailService.Unavailable();
        Issued issued;
        try {
            issued = tx.execute(status -> {
                long id = accounts.insert(normalized, role, clock.millis());
                return issue(id, normalized, "set", Duration.ofHours(24));
            });
        } catch (org.springframework.dao.DataAccessException conflict) { throw new Rejected(); }
        mail.send(issued.email(), issued.purpose(), issued.token());
        return accounts.byId(issued.accountId());
    }

    public void resend(long id) {
        if (!mail.configured()) throw new ManagementMailService.Unavailable();
        Issued issued = tx.execute(status -> {
            Account account = accounts.byId(id);
            if (account == null || !account.enabled() || account.passwordHash() != null) throw new Rejected();
            return issue(id, account.email(), "set", Duration.ofHours(24));
        });
        mail.send(issued.email(), issued.purpose(), issued.token());
    }

    public void forgot(String email) {
        // The controller always returns the same response, including absent or malformed addresses.
        try {
            Account account = account(email);
            if (account == null || !account.active() || !mail.configured()) return;
            Issued issued = tx.execute(status -> issue(account.id(), account.email(), "reset", Duration.ofMinutes(30)));
            mail.send(issued.email(), issued.purpose(), issued.token());
        } catch (org.springframework.dao.DataAccessException | ManagementMailService.Unavailable ignored) {
            // Database contention and SMTP failure must not reveal whether this address exists.
        }
    }

    public void redeem(String purpose, String token, String password) {
        ManagementPolicy.password(password);
        String digest = digest(token);
        withEditingLock(() -> {
            long changed = tx.execute(status -> {
                var entry = accounts.token(digest);
                if (entry == null || !entry.purpose().equals(purpose) || entry.consumedAt() != null
                        || entry.expiresAt() <= clock.millis()) throw new Rejected();
                Account account = accounts.byId(entry.accountId());
                if (account == null || !account.enabled() || ("set".equals(purpose) && account.passwordHash() != null)
                        || ("reset".equals(purpose) && account.passwordHash() == null)) throw new Rejected();
                if (!accounts.consume(digest, clock.millis())) throw new Rejected();
                accounts.password(account.id(), "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(password));
                accounts.consumeTokens(account.id(), clock.millis());
                if ("set".equals(purpose)) accounts.activateBootstrap(account.id());
                return account.id();
            });
            editing.clearAccount(changed);
        });
    }

    public void change(long id, String current, String replacement) {
        ManagementPolicy.password(replacement);
        withEditingLock(() -> {
            tx.executeWithoutResult(status -> {
                Account account = accounts.byId(id);
                if (account == null || !account.active() || !matches(current, account.passwordHash())) throw new Rejected();
                accounts.password(id, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(replacement));
                accounts.consumeTokens(id, clock.millis());
            });
            editing.clearAccount(id);
        });
    }

    public void alter(long id, String role, boolean enabled) {
        validRole(role);
        withEditingLock(() -> {
            tx.executeWithoutResult(status -> {
                if (accounts.byId(id) == null || !accounts.alter(id, role, enabled)) throw new Rejected();
                if (!enabled) accounts.consumeTokens(id, clock.millis());
            });
            editing.clearAccount(id);
        });
    }

    public boolean matches(String raw, String stored) {
        return raw != null && raw.getBytes(StandardCharsets.UTF_8).length <= 512
                && stored != null && stored.startsWith("{pbkdf2@SpringSecurity_v5_8}")
                && encoder.matches(raw, stored.substring("{pbkdf2@SpringSecurity_v5_8}".length()));
    }

    private Issued issue(long id, String email, String purpose, Duration duration) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // A new issuance supersedes earlier links of the same account and purpose.
        accounts.consumeTokens(id, clock.millis());
        accounts.insertToken(digest(raw), id, purpose, clock.millis() + duration.toMillis());
        return new Issued(id, email, purpose, raw);
    }

    private static String digest(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw new Rejected();
        try {
            byte[] value = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII));
            return java.util.HexFormat.of().formatHex(value);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private boolean credentialValid(String supplied) {
        byte[] expected = decoded(setupCredential.get());
        byte[] actual = decoded(supplied);
        return expected != null && actual != null && MessageDigest.isEqual(expected, actual);
    }
    private static byte[] decoded(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{43}")) return null;
        try {
            byte[] result = Base64.getUrlDecoder().decode(value);
            return result.length == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(result).equals(value)
                    ? result : null;
        } catch (IllegalArgumentException invalid) { return null; }
    }
    private static void validRole(String role) {
        if (!("viewer".equals(role) || "editor".equals(role) || "admin".equals(role))) throw new Rejected();
    }
}
