package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.config.SidecarManagementProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class ManagementMailService {
    public static final class Unavailable extends RuntimeException {
        public Unavailable() { super("Email delivery is unavailable; retry later"); }
    }
    private final ObjectProvider<JavaMailSender> sender;
    private final SidecarManagementProperties settings;
    private final String host;

    public ManagementMailService(ObjectProvider<JavaMailSender> sender, SidecarManagementProperties settings,
            @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.settings = settings;
        this.host = host;
    }

    public boolean configured() {
        try {
            URI base = URI.create(settings.getExternalBaseUrl());
            boolean trusted = "https".equalsIgnoreCase(base.getScheme()) ||
                    ("http".equalsIgnoreCase(base.getScheme()) &&
                            ("localhost".equalsIgnoreCase(base.getHost()) || "127.0.0.1".equals(base.getHost())));
            return host != null && !host.isBlank() && sender.getIfAvailable() != null && settings.getMailFrom() != null
                    && !settings.getMailFrom().isBlank() && trusted && base.getHost() != null
                    && base.getUserInfo() == null && base.getQuery() == null && base.getFragment() == null;
        } catch (RuntimeException badConfiguration) {
            return false;
        }
    }

    public void send(String email, String purpose, String token) {
        if (!configured()) throw new Unavailable();
        String base = settings.getExternalBaseUrl().replaceAll("/+$", "");
        String link = base + "/management/password/" + purpose + "?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        var message = new SimpleMailMessage();
        message.setFrom(settings.getMailFrom());
        message.setTo(email);
        message.setSubject("Loomspan management password " + ("set".equals(purpose) ? "setup" : "reset"));
        message.setText("Use this one-time link to " + ("set".equals(purpose) ? "set" : "reset")
                + " your management password:\n" + link + "\nIf you did not request this, ignore this email.\n");
        try {
            sender.getObject().send(message);
        } catch (RuntimeException deliveryFailure) {
            // Do not expose mail credentials, token or transport diagnostics to callers/logs.
            throw new Unavailable();
        }
    }
}
