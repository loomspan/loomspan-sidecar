package ai.loomspan.sidecar.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomspan-sidecar.management")
public class SidecarManagementProperties {
    private Duration sessionIdleTimeout = Duration.ofMinutes(30);
    private String mailFrom;
    private String externalBaseUrl;

    public Duration getSessionIdleTimeout() { return sessionIdleTimeout; }
    public void setSessionIdleTimeout(Duration value) {
        if (value == null || value.isNegative() || value.isZero()) throw new IllegalArgumentException("Invalid management idle timeout");
        sessionIdleTimeout = value;
    }
    public String getMailFrom() { return mailFrom; }
    public void setMailFrom(String value) { mailFrom = value; }
    public String getExternalBaseUrl() { return externalBaseUrl; }
    public void setExternalBaseUrl(String value) { externalBaseUrl = value; }
}
