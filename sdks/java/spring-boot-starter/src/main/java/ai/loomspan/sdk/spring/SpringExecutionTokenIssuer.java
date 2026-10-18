package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.*;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.context.SecurityContextHolder;

/** Capture authenticated identity at operation entry, before processing business inputs. */
public final class SpringExecutionTokenIssuer {
    private final ExecutionTokenIssuer issuer;
    public SpringExecutionTokenIssuer(ExecutionTokenIssuer issuer) { this.issuer = java.util.Objects.requireNonNull(issuer); }
    public CredentialSnapshot issueCurrent() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || new AuthenticationTrustResolverImpl().isAnonymous(authentication))
            throw new IllegalStateException("Authenticated non-anonymous identity is required for execution issuance");
        String subject = authentication.getName();
        var authorities = authentication.getAuthorities().stream().map(authority -> authority.getAuthority()).toList();
        return issuer.issue(new AuthenticatedIdentity(subject, authorities));
    }
}
