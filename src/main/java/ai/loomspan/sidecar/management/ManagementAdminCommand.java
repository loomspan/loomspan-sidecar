package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.storage.StorageConfiguration;
import ai.loomspan.sidecar.storage.StorageLock;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Narrow offline commands. No Spring application context is constructed. */
public final class ManagementAdminCommand {
    private ManagementAdminCommand() { }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        try {
            if (Arrays.equals(args, new String[] { "admin", "generate-setup-token" })) {
                out.println(ManagementTokens.generate());
                return 0;
            }
            if (args.length == 6 && "admin".equals(args[0]) && "issue-password-reset".equals(args[1])
                    && "--database".equals(args[2]) && "--email".equals(args[4])) {
                var root = ((ch.qos.logback.classic.LoggerContext) org.slf4j.LoggerFactory.getILoggerFactory())
                        .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
                var previous = root.getLevel();
                String raw;
                try {
                    root.setLevel(ch.qos.logback.classic.Level.OFF);
                    raw = issue(Path.of(args[3]), args[5]);
                } finally {
                    root.setLevel(previous);
                }
                out.println(raw);
                return 0;
            }
        } catch (RuntimeException failure) {
            err.println("Administrator command failed. Check arguments, store access, and target eligibility.");
            return 2;
        }
        err.println("Usage: admin generate-setup-token | admin issue-password-reset --database <path> --email <address>");
        return 2;
    }

    static String issue(Path database, String email) {
        String normalized = ManagementPolicy.email(email);
        Path absolute = database.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) throw new IllegalStateException("Missing store");
        try {
            if (Files.size(absolute) == 0) throw new IllegalStateException("Empty store");
        } catch (java.io.IOException failure) { throw new IllegalStateException("Unreadable store", failure); }
        try (StorageLock ignored = StorageLock.acquire(absolute)) {
            // Recheck under the lock. Do not run migrations in maintenance mode.
            if (!Files.isRegularFile(absolute)) throw new IllegalStateException("Missing store");
            var source = StorageConfiguration.dataSource(absolute);
            Flyway.configure().dataSource(source).load().validate();
            var accounts = new ManagementAccountRepository(new JdbcTemplate(source));
            var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            return tx.execute(status -> {
                if (!"activated".equals(accounts.bootstrapState())) throw new IllegalStateException("Inactive bootstrap");
                var account = accounts.byEmail(normalized);
                if (account == null || !account.active() || !"admin".equals(account.role()))
                    throw new IllegalStateException("Ineligible account");
                long now = Clock.systemUTC().millis();
                return ManagementTokens.issue(accounts, account.id(), "reset", now,
                        ManagementTokens.RESET_LIFETIME);
            });
        } catch (java.io.IOException failure) { throw new IllegalStateException("Storage lock failure", failure); }
    }
}
