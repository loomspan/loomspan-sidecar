package ai.loomspan.sdk.spring;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings for the blocking SDK client. */
@ConfigurationProperties("loomspan-sdk")
public class SidecarClientProperties {
    private URI baseUrl;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration responseTimeout = Duration.ofSeconds(10);
    private long maxResponseSize = 8L * 1024 * 1024;
    private String sslBundle;

    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getResponseTimeout() { return responseTimeout; }
    public void setResponseTimeout(Duration responseTimeout) { this.responseTimeout = responseTimeout; }
    public long getMaxResponseSize() { return maxResponseSize; }
    public void setMaxResponseSize(long maxResponseSize) { this.maxResponseSize = maxResponseSize; }
    public String getSslBundle() { return sslBundle; }
    public void setSslBundle(String sslBundle) { this.sslBundle = sslBundle; }
}
