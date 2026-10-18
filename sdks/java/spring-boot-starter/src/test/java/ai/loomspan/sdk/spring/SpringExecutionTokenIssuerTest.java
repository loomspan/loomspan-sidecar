package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.*;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.authentication.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class SpringExecutionTokenIssuerTest {
    @TempDir Path directory;
    @AfterEach void clearIdentity() { SecurityContextHolder.clearContext(); }
    @Test void springCapturesAuthenticatedIdentityOnceAndMatchesCustomIssuance() throws Exception {
        var source = SigningKeys.initialize(SigningSettings.colocated(directory));
        var issuer = new ExecutionTokenIssuer(source, new IssuanceSettings("loomspan-app", java.time.Duration.ofMinutes(15), List.of(), List.of("ROLE_EDITOR")));
        var adapter = new SpringExecutionTokenIssuer(issuer);
        int[] counts = {0, 0};
        var authentication = new UsernamePasswordAuthenticationToken("real-user", "not-used", List.of(new SimpleGrantedAuthority("ROLE_EDITOR"))) {
            @Override public String getName() { counts[0]++; return super.getName(); }
            @Override public java.util.Collection<org.springframework.security.core.GrantedAuthority> getAuthorities() { counts[1]++; return super.getAuthorities(); }
        };
        SecurityContextHolder.getContext().setAuthentication(authentication);
        var snapshot = adapter.issueCurrent();
        assertThat(counts).containsExactly(1, 1);
        SecurityContextHolder.clearContext();
        var captured = new AtomicReference<String>();
        var body = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/skills/skill/executions", exchange -> {
            captured.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            byte[] response = ("{\"id\":\"" + UUID.randomUUID() + "\"}").getBytes();
            exchange.sendResponseHeaders(202, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try (var client = SidecarClient.builder().baseUrl(URI.create("http://localhost:" + server.getAddress().getPort())).build()) {
            var hostile = JsonMapper.builder().build().createObjectNode().put("sub", "forged").put("roles", "ADMIN").put("token", "forged-token");
            client.submit("skill", hostile, snapshot);
            var spring = SignedJWT.parse(captured.get().substring(7));
            assertThat(spring.verify(new RSASSAVerifier(source.publicKey()))).isTrue();
            var first = spring.getJWTClaimsSet();
            assertThat(first.getSubject()).isEqualTo("real-user"); assertThat(first.getStringListClaim("roles")).containsExactly("ROLE_EDITOR");
            assertThat(body.get()).contains("forged", "ADMIN", "forged-token");
            client.submit("skill", hostile, issuer.issue(new AuthenticatedIdentity("real-user", List.of("ROLE_EDITOR"))));
            var custom = SignedJWT.parse(captured.get().substring(7)).getJWTClaimsSet();
            assertThat(custom.getSubject()).isEqualTo(first.getSubject()); assertThat(custom.getAudience()).isEqualTo(first.getAudience());
            assertThat(custom.getIssuer()).isEqualTo(first.getIssuer()); assertThat(custom.getStringListClaim("roles")).isEqualTo(first.getStringListClaim("roles"));
            assertThat(custom.getJWTID()).isNotEqualTo(first.getJWTID());
        } finally { server.stop(0); }
        assertThatThrownBy(adapter::issueCurrent).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsUnauthenticatedAnonymousNullAndBlankIdentity() {
        var source = SigningKeys.initialize(SigningSettings.colocated(directory));
        var adapter = new SpringExecutionTokenIssuer(new ExecutionTokenIssuer(source, IssuanceSettings.defaults()));
        assertThatThrownBy(adapter::issueCurrent).isInstanceOf(IllegalStateException.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.unauthenticated("user", "password"));
        assertThatThrownBy(adapter::issueCurrent).isInstanceOf(IllegalStateException.class);
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymous", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThatThrownBy(adapter::issueCurrent).isInstanceOf(IllegalStateException.class);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(" ", "password", List.of()));
        assertThatThrownBy(adapter::issueCurrent).isInstanceOf(IllegalArgumentException.class);
    }
}
