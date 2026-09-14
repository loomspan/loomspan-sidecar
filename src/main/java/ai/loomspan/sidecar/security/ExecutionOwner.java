package ai.loomspan.sidecar.security;

import java.util.Objects;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record ExecutionOwner(String issuer, String subject) {
    public ExecutionOwner {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and subject must be nonblank");
        }
    }

    public static ExecutionOwner from(JwtAuthenticationToken authentication) {
        Objects.requireNonNull(authentication, "authentication");
        var issuer = authentication.getToken().getIssuer();
        return new ExecutionOwner(issuer == null ? null : issuer.toString(), authentication.getToken().getSubject());
    }
}
