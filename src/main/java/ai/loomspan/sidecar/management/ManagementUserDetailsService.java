package ai.loomspan.sidecar.management;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class ManagementUserDetailsService implements UserDetailsService {
    public static final class Principal implements UserDetails, CredentialsContainer {
        private final long id;
        private final String email;
        private final String role;
        private final long version;
        private final Collection<? extends GrantedAuthority> authorities;
        private String hash;

        Principal(long id, String email, String role, long version, String hash,
                Collection<? extends GrantedAuthority> authorities) {
            this.id = id; this.email = email; this.role = role; this.version = version;
            this.hash = hash; this.authorities = authorities;
        }
        public long id() { return id; }
        public String email() { return email; }
        public String role() { return role; }
        public long version() { return version; }
        @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
        @Override public String getPassword() { return hash; }
        @Override public String getUsername() { return email; }
        @Override public boolean isEnabled() { return true; }
        @Override public void eraseCredentials() { hash = null; }
        @Override public String toString() { return "ManagementPrincipal[id=" + id + ", redacted]"; }
    }
    private final ManagementIdentityService identity;
    public ManagementUserDetailsService(ManagementIdentityService identity) { this.identity = identity; }
    @Override public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var account = identity.account(username);
        if (account == null || !account.active()) throw new UsernameNotFoundException("Invalid credentials");
        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("MGT_VIEWER"));
        if ("editor".equals(account.role()) || "admin".equals(account.role()))
            roles.add(new SimpleGrantedAuthority("MGT_EDITOR"));
        if ("admin".equals(account.role())) roles.add(new SimpleGrantedAuthority("MGT_ADMIN"));
        return new Principal(account.id(), account.email(), account.role(), account.version(),
                account.passwordHash(), List.copyOf(roles));
    }
}
