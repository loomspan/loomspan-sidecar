package ai.loomspan.sidecar.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loomspan-sidecar.auth.jwt")
public class SidecarJwtProperties {
    private String audience = "loomspan-sidecar";
    public String getAudience() { return audience; }
    public void setAudience(String value) { audience = value; }
    public void validate() {
        if (audience == null || audience.isBlank()) throw new IllegalArgumentException("JWT audience must be nonblank");
    }
}
