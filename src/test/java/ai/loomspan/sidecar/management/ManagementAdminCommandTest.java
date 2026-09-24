package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.storage.StorageConfiguration;
import ai.loomspan.sidecar.storage.StorageLock;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ManagementAdminCommandTest {
    @TempDir Path directory;
    private static final String PASSWORD = "Long Password 123!";

    private record Result(int status, String stdout, String stderr) { }

    private Result run(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        int status = ManagementAdminCommand.run(args, new PrintStream(out), new PrintStream(err));
        return new Result(status, out.toString(), err.toString());
    }

    @Test void generationIsCanonicalFreshAndCaptureSafe() {
        var first = run("admin", "generate-setup-token");
        var second = run("admin", "generate-setup-token");
        assertThat(first.status()).isZero();
        assertThat(first.stderr()).isEmpty();
        assertThat(first.stdout()).matches("[A-Za-z0-9_-]{43}\\R").isNotEqualTo(second.stdout());
        assertThat(Base64.getUrlDecoder().decode(first.stdout().trim())).hasSize(32);
        var invalid = run("admin", "generate-setup-token", "unexpected");
        assertThat(invalid.status()).isNotZero();
        assertThat(invalid.stdout()).isEmpty();
    }

    @Test void issuanceRequiresAnEligibleAdminAndExistingStore() throws Exception {
        Path missing = directory.resolve("missing.db");
        assertThat(run("admin", "issue-password-reset", "--database", missing.toString(),
                "--email", "admin@example.test").status()).isNotZero();
        assertThat(Files.exists(missing)).isFalse();
        Path database = directory.resolve("admin.db");
        var source = StorageConfiguration.dataSource(database);
        StorageConfiguration.migrate(source);
        var jdbc = new JdbcTemplate(source);
        var accounts = new ManagementAccountRepository(jdbc);
        var editing = new ManagementEditingState();
        var identity = new ManagementIdentityService(accounts,
                new TransactionTemplate(new DataSourceTransactionManager(source)), mock(ManagementMailService.class),
                Clock.systemUTC(), () -> "A".repeat(43), editing);
        identity.setup("A".repeat(43), "admin@example.test", PASSWORD, PASSWORD);
        var before = accounts.byEmail("admin@example.test");
        var issued = run("admin", "issue-password-reset", "--database", database.toString(),
                "--email", "ADMIN@example.test");
        assertThat(issued.status()).isZero();
        assertThat(issued.stderr()).isEmpty();
        assertThat(issued.stdout()).matches("[A-Za-z0-9_-]{43}\\R");
        assertThat(accounts.byEmail("admin@example.test").passwordHash()).isEqualTo(before.passwordHash());
        assertThat(accounts.byEmail("admin@example.test").version()).isEqualTo(before.version());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM management_token WHERE digest=?", Integer.class,
                issued.stdout().trim())).isZero();
        var second = run("admin", "issue-password-reset", "--database", database.toString(),
                "--email", "admin@example.test");
        assertThat(second.status()).isZero();
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM management_token WHERE digest=?", Long.class,
                ManagementTokens.digest(issued.stdout().trim()))).isNotNull();
        assertThat(run("admin", "issue-password-reset", "--database", database.toString(),
                "--email", "missing@example.test").stdout()).isEmpty();
        long viewer = accounts.insert("viewer@example.test", "viewer", Clock.systemUTC().millis());
        accounts.password(viewer, before.passwordHash());
        long disabled = accounts.insert("disabled@example.test", "admin", Clock.systemUTC().millis());
        accounts.password(disabled, before.passwordHash());
        assertThat(accounts.alter(disabled, "admin", false)).isTrue();
        accounts.insert("pending@example.test", "admin", Clock.systemUTC().millis());
        for (String ineligible : new String[] { "viewer@example.test", "disabled@example.test", "pending@example.test" }) {
            var failure = run("admin", "issue-password-reset", "--database", database.toString(),
                    "--email", ineligible);
            assertThat(failure.status()).isNotZero();
            assertThat(failure.stdout()).isEmpty();
        }
        Path foreign = directory.resolve("foreign.db");
        Files.writeString(foreign, "not sqlite");
        var incompatible = run("admin", "issue-password-reset", "--database", foreign.toString(),
                "--email", "admin@example.test");
        assertThat(incompatible.status()).isNotZero();
        assertThat(incompatible.stdout()).isEmpty();
        try (StorageLock ignored = StorageLock.acquire(database)) {
            var busy = run("admin", "issue-password-reset", "--database", database.toString(),
                    "--email", "admin@example.test");
            assertThat(busy.status()).isNotZero();
            assertThat(busy.stdout()).isEmpty();
        }
        editing.drafts.put("old-session", new ManagementEditingState.Entry(before.id(), null));
        editing.lease = new ManagementEditingState.Lease("old-session", "tab", Clock.systemUTC().millis() + 10000);
        identity.redeem("reset", second.stdout().trim(), "Another Long Password 1!");
        assertThat(accounts.byEmail("admin@example.test").version()).isGreaterThan(before.version());
        assertThat(editing.drafts).isEmpty();
        assertThat(editing.lease).isNull();
    }
}
