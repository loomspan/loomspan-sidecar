package ai.loomspan.sidecar.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("loomspan-sidecar.auth.jwt")
public class SidecarJwtProperties {
    private String issuerUri;
    private String audience;
    private String jwkSetUri;
    private Resource publicKeyLocation;
    private Duration clockSkew = Duration.ofSeconds(60);
    private String rolesClaim = "roles";
    private String rolePrefix = "ROLE_";

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String value) { issuerUri = value; }
    public String getAudience() { return audience; }
    public void setAudience(String value) { audience = value; }
    public String getJwkSetUri() { return jwkSetUri; }
    public void setJwkSetUri(String value) { jwkSetUri = value; }
    public Resource getPublicKeyLocation() { return publicKeyLocation; }
    public void setPublicKeyLocation(Resource value) { publicKeyLocation = value; }
    public Duration getClockSkew() { return clockSkew; }
    public void setClockSkew(Duration value) { clockSkew = value; }
    public String getRolesClaim() { return rolesClaim; }
    public void setRolesClaim(String value) { rolesClaim = value; }
    public String getRolePrefix() { return rolePrefix; }
    public void setRolePrefix(String value) { rolePrefix = value; }

    public void validate() {
        if (blank(issuerUri) || blank(audience) || blank(rolesClaim) || rolePrefix == null
                || clockSkew == null || clockSkew.isNegative()) {
            throw new IllegalArgumentException("JWT issuer-uri, audience and roles-claim are required and clock-skew must not be negative");
        }
        if (!blank(jwkSetUri) && publicKeyLocation != null) {
            throw new IllegalArgumentException("Configure at most one of jwk-set-uri and public-key-location");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
