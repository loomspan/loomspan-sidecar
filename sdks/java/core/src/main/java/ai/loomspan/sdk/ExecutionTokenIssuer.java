package ai.loomspan.sdk;

import com.nimbusds.jose.*;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.logging.Logger;

/** One profile-construction path for trusted, already authenticated identity. */
public final class ExecutionTokenIssuer {
    private static final Logger LOG = Logger.getLogger(ExecutionTokenIssuer.class.getName());
    private final SigningKeySource source;
    private final IssuanceSettings settings;
    private final Clock clock;
    public ExecutionTokenIssuer(SigningKeySource source, IssuanceSettings settings) { this(source, settings, Clock.systemUTC()); }
    ExecutionTokenIssuer(SigningKeySource source, IssuanceSettings settings, Clock clock) {
        this.source = Objects.requireNonNull(source); this.settings = Objects.requireNonNull(settings); this.clock = Objects.requireNonNull(clock);
        expiry(clock.instant());
        if (settings.lifetime().compareTo(Duration.ofHours(1)) > 0)
            LOG.warning("loomspan-sdk.issuance.lifetime exceeds one hour; roles remain snapshots until expiry");
    }
    public CredentialSnapshot issue(AuthenticatedIdentity identity) {
        Objects.requireNonNull(identity, "authenticated identity is required");
        Instant now = clock.instant();
        var audiences = new LinkedHashSet<String>();
        audiences.add("loomspan-sidecar"); audiences.add(settings.issuer()); audiences.addAll(settings.additionalAudiences());
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", settings.issuer()); claims.put("sub", identity.subject()); claims.put("aud", new ArrayList<>(audiences));
        claims.put("iat", numericDate(now)); claims.put("exp", numericDate(expiry(now)));
        claims.put("jti", UUID.randomUUID().toString()); claims.put("token_use", "loomspan_execution");
        claims.put("roles", identity.authorities().stream().filter(settings.roles()::contains).distinct().toList());
        var object = new JWSObject(new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(new JOSEObjectType("loomspan-execution+jwt")).keyID(source.kid()).build(), new Payload(claims));
        try {
            object.sign(new JWSSigner() {
                public Set<JWSAlgorithm> supportedJWSAlgorithms() { return Set.of(JWSAlgorithm.RS256); }
                public JCAContext getJCAContext() { return new JCAContext(); }
                public Base64URL sign(JWSHeader header, byte[] input) { return Base64URL.encode(source.signRs256(input)); }
            });
            return CredentialSnapshot.of(object.serialize());
        } catch (Exception ignored) { throw new IllegalStateException("Execution credential issuance failed"); }
    }
    private Instant expiry(Instant now) {
        try { return now.plus(settings.lifetime()); }
        catch (DateTimeException | ArithmeticException ignored) { throw new IllegalArgumentException("loomspan-sdk.issuance.lifetime exceeds representable timestamp range"); }
    }
    private static BigDecimal numericDate(Instant instant) {
        return BigDecimal.valueOf(instant.getEpochSecond()).add(BigDecimal.valueOf(instant.getNano(), 9));
    }
}
