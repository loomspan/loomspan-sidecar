package ai.loomspan.sdk;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionTokenIssuerTest {
    @TempDir Path directory;
    final Instant now = Instant.parse("2026-10-18T12:00:00Z");
    @Test void issuedTokenMatchesProfileAndExactAuthorityIntersection() throws Exception {
        var source = SigningKeys.initialize(SigningSettings.colocated(directory));
        var authorities = new ArrayList<>(List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR", "EXTRA"));
        var allowlist = new ArrayList<>(List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR", "UNGRANTED"));
        var settings = new IssuanceSettings("loomspan-app", Duration.ofMinutes(15), List.of(), allowlist);
        var identity = new AuthenticatedIdentity("user-17", authorities);
        var issuer = new ExecutionTokenIssuer(source, settings, Clock.fixed(now, ZoneOffset.UTC));
        authorities.clear(); allowlist.clear();
        var credential = issuer.issue(identity);
        var token = parse(credential);
        assertTrue(token.verify(new RSASSAVerifier(source.publicKey())));
        assertEquals(JWSAlgorithm.RS256, token.getHeader().getAlgorithm());
        assertEquals("loomspan-execution+jwt", token.getHeader().getType().toString());
        assertEquals(new RSAKey.Builder(source.publicKey()).build().computeThumbprint().toString(), token.getHeader().getKeyID());
        var claims = token.getJWTClaimsSet();
        assertEquals("loomspan_execution", claims.getStringClaim("token_use"));
        assertEquals("loomspan-app", claims.getIssuer()); assertEquals("user-17", claims.getSubject());
        assertEquals(List.of("loomspan-sidecar", "loomspan-app"), claims.getAudience());
        assertEquals(now, claims.getIssueTime().toInstant()); assertEquals(now.plusSeconds(900), claims.getExpirationTime().toInstant());
        assertEquals(List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR"), claims.getStringListClaim("roles"));
        assertNotNull(UUID.fromString(claims.getJWTID()));
        assertNotEquals(claims.getJWTID(), parse(issuer.issue(identity)).getJWTClaimsSet().getJWTID());
        String raw = credential.authorization().substring(7);
        assertFalse(credential.toString().contains(raw));
        try { assertFalse(JsonMapper.builder().build().writeValueAsString(credential).contains(raw)); }
        catch (RuntimeException error) { assertFalse(error.toString().contains(raw)); }
    }
    @Test void defaultsAdditionalAudiencesAndEmptyRoles() throws Exception {
        var source = SigningKeys.initialize(SigningSettings.colocated(directory));
        var defaults = new ExecutionTokenIssuer(source, IssuanceSettings.defaults());
        assertTrue(parse(defaults.issue(new AuthenticatedIdentity("u", List.of("ROLE_X")))).getJWTClaimsSet().getStringListClaim("roles").isEmpty());
        var extra = new ArrayList<>(List.of("api", "api", "loomspan-sidecar"));
        var settings = new IssuanceSettings("trusted-group", Duration.ofHours(3), extra, List.of("ROLE_X"));
        extra.clear();
        var issuer = new ExecutionTokenIssuer(source, settings, Clock.fixed(now, ZoneOffset.UTC));
        var claims = parse(issuer.issue(new AuthenticatedIdentity("u", List.of()))).getJWTClaimsSet();
        assertEquals("trusted-group", claims.getIssuer());
        assertEquals(List.of("loomspan-sidecar", "trusted-group", "api"), claims.getAudience());
        assertEquals(now.plus(Duration.ofHours(3)), claims.getExpirationTime().toInstant());
        assertEquals(List.of(), claims.getStringListClaim("roles"));
    }
    @Test void positiveDurationsKeepPrecisionAndWarnOnlyAboveOneHour() throws Exception {
        var source = SigningKeys.initialize(SigningSettings.colocated(directory));
        var logger = Logger.getLogger(ExecutionTokenIssuer.class.getName());
        var messages = new ArrayList<String>();
        var handler = new Handler() { public void publish(LogRecord r) { messages.add(r.getMessage()); } public void flush() {} public void close() {} };
        logger.addHandler(handler);
        try {
            for (Duration duration : List.of(Duration.ofNanos(1), Duration.ofMillis(500), Duration.ofSeconds(1), Duration.ofHours(1), Duration.ofHours(2))) {
                var issuer = new ExecutionTokenIssuer(source, new IssuanceSettings("i", duration, List.of(), List.of()), Clock.fixed(now, ZoneOffset.UTC));
                String payload = parse(issuer.issue(new AuthenticatedIdentity("s", List.of()))).getPayload().toString();
                var matcher = java.util.regex.Pattern.compile("\"exp\":([0-9.]+)").matcher(payload);
                assertTrue(matcher.find(), payload);
                var exp = new BigDecimal(matcher.group(1));
                assertEquals(0, exp.subtract(BigDecimal.valueOf(now.getEpochSecond())).compareTo(BigDecimal.valueOf(duration.getSeconds()).add(BigDecimal.valueOf(duration.getNano(), 9))));
            }
            assertEquals(1, messages.size());
        } finally { logger.removeHandler(handler); }
        for (var duration : List.of(Duration.ZERO, Duration.ofNanos(-1)))
            assertThrows(IllegalArgumentException.class, () -> new IssuanceSettings("i", duration, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ExecutionTokenIssuer(source,
                new IssuanceSettings("i", Duration.ofSeconds(Long.MAX_VALUE), List.of(), List.of())));
        assertThrows(IllegalArgumentException.class, () -> new IssuanceSettings(" ", Duration.ofSeconds(1), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new IssuanceSettings("i", Duration.ofSeconds(1), List.of(" "), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedIdentity(" ", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedIdentity("u", List.of(" ")));
    }
    @Test void sharedIssuerDoesNotCrossContaminateIdentity() throws Exception {
        var issuer = new ExecutionTokenIssuer(SigningKeys.initialize(SigningSettings.colocated(directory)), IssuanceSettings.defaults());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> issuer.issue(new AuthenticatedIdentity("first", List.of())));
            var second = pool.submit(() -> issuer.issue(new AuthenticatedIdentity("second", List.of())));
            assertEquals("first", parse(first.get()).getJWTClaimsSet().getSubject());
            assertEquals("second", parse(second.get()).getJWTClaimsSet().getSubject());
        }
    }
    @Test void privateSourceAndSigningFailuresRemainRedacted() throws Exception {
        var source = SigningKeys.initialize(SigningSettings.colocated(directory));
        assertFalse(source.toString().contains("PRIVATE KEY"));
        try {
            String serialized = JsonMapper.builder().build().writeValueAsString(source);
            assertFalse(serialized.contains("privateKey")); assertFalse(serialized.contains("PRIVATE KEY"));
        } catch (RuntimeException refusal) { assertFalse(refusal.toString().contains("PRIVATE KEY")); }
        var broken = new SigningKeySource() {
            public byte[] signRs256(byte[] bytes) { throw new IllegalArgumentException("secret-token-marker PRIVATE KEY private-parameter-marker"); }
            public java.security.interfaces.RSAPublicKey publicKey() { return source.publicKey(); }
            public String kid() { return source.kid(); }
        };
        var issuer = new ExecutionTokenIssuer(broken, IssuanceSettings.defaults());
        var failure = assertThrows(IllegalStateException.class, () -> issuer.issue(new AuthenticatedIdentity("user", List.of())));
        var output = new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(output));
        assertFalse(output.toString().contains("secret-token-marker"));
        assertFalse(output.toString().contains("PRIVATE KEY")); assertNull(failure.getCause());
    }
    static SignedJWT parse(CredentialSnapshot snapshot) throws Exception { return SignedJWT.parse(snapshot.authorization().substring(7)); }
}
