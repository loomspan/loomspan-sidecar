package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.IssuanceSettings;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loomspan-sdk.issuance")
public class IssuanceProperties {
    private String issuer = "loomspan-app";
    private Duration lifetime = Duration.ofMinutes(15);
    private List<String> additionalAudiences = List.of();
    private List<String> roles = List.of();
    IssuanceSettings settings() { return new IssuanceSettings(issuer, lifetime, additionalAudiences, roles); }
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer = value; }
    public Duration getLifetime() { return lifetime; }
    public void setLifetime(Duration value) { lifetime = value; }
    public List<String> getAdditionalAudiences() { return additionalAudiences; }
    public void setAdditionalAudiences(List<String> value) { additionalAudiences = value; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> value) { roles = value; }
}
