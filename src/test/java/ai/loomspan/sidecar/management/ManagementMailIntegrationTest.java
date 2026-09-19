package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import ai.loomspan.sidecar.storage.StorageConfiguration;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ManagementMailIntegrationTest {
    @TempDir Path directory;

    @Test void requiredStartTlsRejectsPlaintextSmtp() throws Exception {
        try (var smtp = new LocalSmtp()) {
            var sender = new JavaMailSenderImpl();
            sender.setHost("127.0.0.1"); sender.setPort(smtp.port());
            sender.getJavaMailProperties().put("mail.smtp.starttls.enable", "true");
            sender.getJavaMailProperties().put("mail.smtp.starttls.required", "true");
            sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "1000");
            sender.getJavaMailProperties().put("mail.smtp.timeout", "1000");
            var message = new SimpleMailMessage();
            message.setFrom("operator@example.test");
            message.setTo("admin@example.test");
            message.setSubject("test");
            message.setText("test");
            assertThatThrownBy(() -> sender.send(message)).isInstanceOf(org.springframework.mail.MailException.class);
            assertThat(smtp.messages()).isEmpty();
        }
    }

    @Test void failedInitialMailKeepsReservationRetryable() throws Exception {
        var settings = new SidecarManagementProperties();
        settings.setMailFrom("operator@example.test");
        settings.setExternalBaseUrl("https://console.example.test");
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String credential = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        var source = StorageConfiguration.dataSource(directory.resolve("retry.db"));
        StorageConfiguration.migrate(source);
        var accounts = new ManagementAccountRepository(new JdbcTemplate(source));
        var tx = new TransactionTemplate(new DataSourceTransactionManager(source));
        try (var failed = new LocalSmtp(true)) {
            var first = new ManagementIdentityService(accounts, tx, mail(settings, failed.port()),
                    Clock.systemUTC(), () -> credential, new ManagementEditingState());
            assertThatThrownBy(() -> first.setup(credential, "admin@example.test"))
                    .isInstanceOf(ManagementMailService.Unavailable.class);
        }
        assertThat(accounts.bootstrapState()).isEqualTo("reserved");
        assertThat(accounts.byEmail("admin@example.test").active()).isFalse();
        try (var recovered = new LocalSmtp()) {
            var retry = new ManagementIdentityService(accounts, tx, mail(settings, recovered.port()),
                    Clock.systemUTC(), () -> credential, new ManagementEditingState());
            assertThatThrownBy(() -> retry.setup(credential, "other@example.test"))
                    .isInstanceOf(ManagementIdentityService.Rejected.class);
            retry.setup(credential, "admin@example.test");
            assertThat(recovered.messages()).hasSize(1);
            retry.redeem("set", linkToken(recovered.messages().getFirst()), "Long Password 123!");
            assertThat(accounts.bootstrapState()).isEqualTo("activated");
        }
    }

    private static ManagementMailService mail(SidecarManagementProperties settings, int port) {
        var sender = new JavaMailSenderImpl();
        sender.setHost("127.0.0.1"); sender.setPort(port);
        sender.getJavaMailProperties().put("mail.smtp.connectiontimeout", "1000");
        sender.getJavaMailProperties().put("mail.smtp.timeout", "1000");
        sender.getJavaMailProperties().put("mail.smtp.writetimeout", "1000");
        @SuppressWarnings("unchecked") ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        when(provider.getObject()).thenReturn(sender);
        return new ManagementMailService(provider, settings, "127.0.0.1");
    }

    @Test void setupAndInvitationMailAreTrustedAndRetryable() throws Exception {
        try (var smtp = new LocalSmtp()) {
            var properties = new SidecarManagementProperties();
            properties.setMailFrom("operator@example.test");
            properties.setExternalBaseUrl("https://console.example.test/base");
            var mail = mail(properties, smtp.port());
            byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
            String setup = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            var source = StorageConfiguration.dataSource(directory.resolve("mail.db"));
            StorageConfiguration.migrate(source);
            var accounts = new ManagementAccountRepository(new JdbcTemplate(source));
            var identity = new ManagementIdentityService(accounts,
                    new TransactionTemplate(new DataSourceTransactionManager(source)), mail, Clock.systemUTC(), () -> setup,
                    new ManagementEditingState());
            identity.setup(setup, "admin@example.test");
            assertThat(smtp.messages()).hasSize(1);
            assertThat(smtp.messages().getFirst()).contains("From: operator@example.test", "To: admin@example.test",
                    "https://console.example.test/base/management/password/set?token=")
                    .doesNotContain("localhost", "127.0.0.1");
            assertThat(accounts.byEmail("admin@example.test").active()).isFalse();
            identity.setup(setup, "admin@example.test");
            assertThat(smtp.messages()).hasSize(2);
            String token = linkToken(smtp.messages().getLast());
            identity.redeem("set", token, "Long Password 123!");
            assertThat(accounts.byEmail("admin@example.test").active()).isTrue();
            assertThatThrownBy(() -> identity.setup(setup, "other@example.test"))
                    .isInstanceOf(ManagementIdentityService.Rejected.class);
            var invite = identity.invite("viewer@example.test", "viewer");
            assertThat(invite.active()).isFalse();
            identity.redeem("set", linkToken(smtp.messages().getLast()), "Long Password 123!");
            assertThat(accounts.byEmail("viewer@example.test").active()).isTrue();
            identity.forgot("viewer@example.test");
            assertThat(smtp.messages().getLast()).contains("/management/password/reset?token=");
            identity.redeem("reset", linkToken(smtp.messages().getLast()), "Another Long Password 1!");
            assertThat(identity.matches("Another Long Password 1!", accounts.byEmail("viewer@example.test").passwordHash())).isTrue();
            smtp.close();
            String currentHash = accounts.byEmail("admin@example.test").passwordHash();
            identity.forgot("admin@example.test");
            assertThat(accounts.byEmail("admin@example.test").passwordHash()).isEqualTo(currentHash);
            assertThatThrownBy(() -> identity.invite("new@example.test", "viewer"))
                    .isInstanceOf(ManagementMailService.Unavailable.class);
            assertThat(accounts.byEmail("new@example.test").active()).isFalse();
            assertThat(identity.matches("Long Password 123!", accounts.byEmail("admin@example.test").passwordHash())).isTrue();
        }
    }
    private static String linkToken(String message) {
        var match = java.util.regex.Pattern.compile("token=([A-Za-z0-9_-]{43})").matcher(message);
        assertThat(match.find()).isTrue();
        return match.group(1);
    }
    static final class LocalSmtp implements AutoCloseable {
        private final ServerSocket server;
        private final Thread worker;
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private final boolean reject;
        private volatile boolean open = true;
        LocalSmtp() throws Exception { this(false); }
        LocalSmtp(boolean reject) throws Exception {
            this.reject = reject;
            server = new ServerSocket(0, 10, java.net.InetAddress.getLoopbackAddress());
            worker = Thread.ofVirtual().start(() -> {
                while (open) {
                    try (Socket client = server.accept()) { handle(client); }
                    catch (Exception stopped) { if (!open) return; }
                }
            });
        }
        int port() { return server.getLocalPort(); }
        List<String> messages() { return messages; }
        private void handle(Socket socket) throws Exception {
            socket.setSoTimeout(3000);
            var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            var output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
            reply(output, "220 local test smtp");
            String line; boolean data = false; StringBuilder message = new StringBuilder();
            while ((line = input.readLine()) != null) {
                if (data) {
                    if (line.equals(".")) { messages.add(message.toString()); data = false; reply(output, "250 queued"); }
                    else message.append(line).append('\n');
                } else if (line.startsWith("DATA")) { data = true; reply(output, "354 end with dot"); }
                else if (line.startsWith("QUIT")) { reply(output, "221 bye"); return; }
                else if (line.startsWith("EHLO")) { reply(output, "250 local test smtp"); }
                else if (reject && line.startsWith("RCPT")) { reply(output, "550 local fixture rejection"); }
                else reply(output, "250 ok");
            }
        }
        private static void reply(BufferedWriter output, String line) throws Exception {
            output.write(line); output.write("\r\n"); output.flush();
        }
        @Override public void close() throws Exception { open = false; server.close(); worker.join(2000); }
    }
}
