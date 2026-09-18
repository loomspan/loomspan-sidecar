package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.storage.StorageConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.Duration;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementIdentityStoreTest {
    @TempDir Path directory;
    private static final String PASSWORD = "Long Password 123!";

    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-18T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration amount) { now = now.plus(amount); }
    }

    record Fixture(DataSource source, JdbcTemplate jdbc, ManagementIdentityService service,
            ManagementAccountRepository accounts, ManagementMailService mail, MutableClock clock,
            String credential) {
        String token() {
            var captor = ArgumentCaptor.forClass(String.class);
            verify(mail, atLeastOnce()).send(anyString(), anyString(), captor.capture());
            return captor.getValue();
        }
    }
    private Fixture fixture(Path database) {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var source = StorageConfiguration.dataSource(database);
        StorageConfiguration.migrate(source);
        var jdbc = new JdbcTemplate(source);
        var accounts = new ManagementAccountRepository(jdbc);
        var mail = mock(ManagementMailService.class);
        when(mail.configured()).thenReturn(true);
        var clock = new MutableClock();
        var service = new ManagementIdentityService(accounts,
                new TransactionTemplate(new DataSourceTransactionManager(source)), mail, clock, () -> credential,
                new ManagementEditingState());
        return new Fixture(source, jdbc, service, accounts, mail, clock, credential);
    }

    @Test
    void configurationInitializationDoesNotConsumeManagementBootstrap() {
        Path database = directory.resolve("identity.db");
        var source = StorageConfiguration.dataSource(database);
        StorageConfiguration.migrate(source);
        var jdbc = new JdbcTemplate(source);
        jdbc.update("UPDATE configuration_store_state SET initialized = 1 WHERE singleton = 1");
        assertThat(jdbc.queryForObject("SELECT state FROM management_bootstrap WHERE singleton = 1", String.class))
                .isEqualTo("unreserved");
        var accounts = new ManagementAccountRepository(jdbc);
        var mail = mock(ManagementMailService.class);
        when(mail.configured()).thenReturn(true);
        var clock = new MutableClock();
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(key);
        var service = new ManagementIdentityService(accounts,
                new TransactionTemplate(new DataSourceTransactionManager(source)), mail, clock, () -> credential,
                new ManagementEditingState());
        assertThatThrownBy(() -> service.setup("bad", "admin@example.test"))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
        service.setup(credential, " Admin@Example.Test ");
        assertThat(accounts.bootstrapState()).isEqualTo("reserved");
        assertThat(accounts.byEmail("admin@example.test").passwordHash()).isNull();
        var reopened = StorageConfiguration.dataSource(database);
        StorageConfiguration.migrate(reopened);
        var again = new ManagementAccountRepository(new JdbcTemplate(reopened));
        assertThat(again.bootstrapState()).isEqualTo("reserved");
        assertThatThrownBy(() -> service.setup(credential, "other@example.test"))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
    }

    @Test void accountEmailAndLastAdminInvariantsSurviveRestartAndRaces() throws Exception {
        var f = fixture(directory.resolve("accounts.db"));
        f.service.setup(f.credential, "Admin@Example.Test");
        f.service.redeem("set", f.token(), PASSWORD);
        assertThat(f.accounts.byEmail("admin@example.test").active()).isTrue();
        assertThatThrownBy(() -> f.service.invite(" ADMIN@example.test ", "viewer"))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
        var pending = f.service.invite("pending@example.test", "admin");
        assertThatThrownBy(() -> f.service.alter(1, "viewer", true))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
        assertThatThrownBy(() -> f.service.alter(1, "admin", false))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
        f.service.alter(pending.id(), "admin", false);
        assertThatThrownBy(() -> f.service.redeem("set", f.token(), PASSWORD))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
        f.service.alter(pending.id(), "admin", true);
        f.service.resend(pending.id());
        f.service.redeem("set", f.token(), PASSWORD);
        var other = f.accounts.byEmail("pending@example.test");
        assertThat(other.active()).isTrue();
        var barrier = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { barrier.await(); try { f.service.alter(1, "viewer", true); return true; } catch (RuntimeException rejected) { return false; } });
            var b = pool.submit(() -> { barrier.await(); try { f.service.alter(other.id(), "viewer", true); return true; } catch (RuntimeException rejected) { return false; } });
            barrier.countDown();
            assertThat((a.get(10, TimeUnit.SECONDS) ? 1 : 0) + (b.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
        assertThat(f.accounts.all().stream().filter(a -> a.active() && a.role().equals("admin")).count()).isEqualTo(1);
        var reopened = new ManagementAccountRepository(new JdbcTemplate(StorageConfiguration.dataSource(directory.resolve("accounts.db"))));
        assertThat(reopened.byEmail("admin@example.test")).isNotNull();
        assertThat(reopened.all()).hasSize(2);
    }

    @Test void concurrentSetupCannotCreateCompetingAdministrators() throws Exception {
        Path database = directory.resolve("setup-race.db");
        var f = fixture(database);
        var secondSource = StorageConfiguration.dataSource(database);
        var secondAccounts = new ManagementAccountRepository(new JdbcTemplate(secondSource));
        var second = new ManagementIdentityService(secondAccounts,
                new TransactionTemplate(new DataSourceTransactionManager(secondSource)), f.mail, f.clock,
                () -> f.credential, new ManagementEditingState());
        var barrier = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { barrier.await();
                try { f.service.setup(f.credential, "first@example.test"); return true; }
                catch (RuntimeException rejected) { return false; }
            });
            var other = pool.submit(() -> { barrier.await();
                try { second.setup(f.credential, "second@example.test"); return true; }
                catch (RuntimeException rejected) { return false; }
            });
            barrier.countDown();
            assertThat((first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (other.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
        assertThat(f.accounts.all()).hasSize(1);
        assertThat(f.accounts.bootstrapState()).isEqualTo("reserved");
        assertThat(f.accounts.all().getFirst().role()).isEqualTo("admin");
    }

    @Test void passwordLinksArePurposeBoundSingleUseAndTransactional() {
        var f = fixture(directory.resolve("tokens.db"));
        f.service.setup(f.credential, "admin@example.test");
        String set = f.token();
        assertThatThrownBy(() -> f.service.redeem("reset", set, PASSWORD)).isInstanceOf(ManagementIdentityService.Rejected.class);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM management_token WHERE digest=?", Integer.class, set)).isZero();
        f.service.redeem("set", set, PASSWORD);
        assertThatThrownBy(() -> f.service.redeem("set", set, PASSWORD)).isInstanceOf(ManagementIdentityService.Rejected.class);
        assertThat(f.accounts.bootstrapState()).isEqualTo("activated");
        var reopened = new ManagementAccountRepository(new JdbcTemplate(
                StorageConfiguration.dataSource(directory.resolve("tokens.db"))));
        assertThat(reopened.bootstrapState()).isEqualTo("activated");
        assertThatThrownBy(() -> f.service.setup(f.credential, "another@example.test"))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
        String hash = f.accounts.byEmail("admin@example.test").passwordHash();
        assertThat(hash).startsWith("{pbkdf2@SpringSecurity_v5_8}").doesNotContain(PASSWORD);
        f.service.forgot("admin@example.test");
        String expired = f.token();
        f.clock.advance(Duration.ofMinutes(31));
        assertThatThrownBy(() -> f.service.redeem("reset", expired, "Another Long Password 1!"))
                .isInstanceOf(ManagementIdentityService.Rejected.class);
        assertThat(f.accounts.byEmail("admin@example.test").passwordHash()).isEqualTo(hash);
        f.service.forgot("admin@example.test");
        String reset = f.token();
        f.service.redeem("reset", reset, "Another Long Password 1!");
        assertThat(f.service.matches(PASSWORD, f.accounts.byEmail("admin@example.test").passwordHash())).isFalse();
        assertThat(f.service.matches("Another Long Password 1!", f.accounts.byEmail("admin@example.test").passwordHash())).isTrue();
        assertThat(f.accounts.byEmail("admin@example.test").passwordHash()).isNotEqualTo(hash);
    }

    @Test void passwordPolicyAndStoredSecretsAreSafe() {
        assertThatThrownBy(() -> ManagementPolicy.password("Short Pass1!"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ManagementPolicy.password("Long Password 123 "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ManagementPolicy.password("A".repeat(125) + "a1!" + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        ManagementPolicy.password("A".repeat(125) + "a1!");
        ManagementPolicy.password("Åbcdefghijkl ١!");
        ManagementPolicy.password("Long Password 123!");
        assertThat(ManagementPolicy.email(" Admin@Example.Test ")).isEqualTo("admin@example.test");
        assertThatThrownBy(() -> ManagementPolicy.email("first..last@example.test"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
