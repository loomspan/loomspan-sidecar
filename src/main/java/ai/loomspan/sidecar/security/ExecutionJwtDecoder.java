package ai.loomspan.sidecar.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.util.JSONObjectUtils;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

/** Validates raw claim shapes before any registered-claim conversion. */
final class ExecutionJwtDecoder implements JwtDecoder {
    private final CurrentFileJwtTrust trust;
    private final String audience;
    private final JwtTimestampValidator timestamps;
    ExecutionJwtDecoder(CurrentFileJwtTrust trust, String audience, Clock clock) {
        this.trust = trust; this.audience = audience;
        timestamps = new JwtTimestampValidator(Duration.ofSeconds(60));
        timestamps.setClock(clock);
    }
    @Override public Jwt decode(String token) {
        try {
            var jws = JWSObject.parse(token);
            Map<String, Object> headers = JSONObjectUtils.parse(jws.getParsedParts()[0].decodeToString());
            Map<String, Object> claims = JSONObjectUtils.parse(jws.getPayload().toString());
            require("RS256".equals(headers.get("alg")) && "loomspan-execution+jwt".equals(headers.get("typ")));
            String kid = nonblank(headers.get("kid"));
            nonblank(claims.get("iss")); nonblank(claims.get("sub"));
            require("loomspan_execution".equals(claims.get("token_use")));
            require(claims.get("roles") instanceof List<?> roles && roles.stream().allMatch(String.class::isInstance));
            Object aud = claims.get("aud");
            List<?> audiences = aud instanceof String text ? List.of(text) : aud instanceof List<?> list ? list : List.of();
            require(!audiences.isEmpty() && audiences.stream().allMatch(String.class::isInstance) && audiences.contains(audience));
            Instant expiration = numericDate(claims.get("exp"));
            var converted = new LinkedHashMap<>(claims);
            converted.put("exp", expiration); converted.put("aud", audiences);
            for (String name : List.of("iat", "nbf")) if (claims.containsKey(name)) converted.put(name, numericDate(claims.get(name)));
            if (claims.containsKey("jti")) require(claims.get("jti") instanceof String);
            var key = trust.forKid(kid).keys().get(kid);
            require(key != null && jws.verify(new RSASSAVerifier(key)));
            var jwt = Jwt.withTokenValue(token).headers(values -> values.putAll(headers))
                    .claims(values -> values.putAll(converted)).build();
            require(!timestamps.validate(jwt).hasErrors());
            return jwt;
        } catch (Exception invalid) {
            // Neither parser text nor the unverified payload belongs in logs or API errors.
            throw new BadJwtException("Invalid execution credential");
        }
    }
    private static String nonblank(Object value) {
        require(value instanceof String text && !text.isBlank());
        return (String) value;
    }
    private static Instant numericDate(Object value) {
        require(value instanceof Number);
        BigDecimal number = new BigDecimal(value.toString());
        BigDecimal seconds = number.setScale(0, RoundingMode.FLOOR);
        long whole = seconds.longValueExact();
        long nanos = number.subtract(seconds).movePointRight(9).longValue();
        return Instant.ofEpochSecond(whole, nanos);
    }
    private static void require(boolean valid) { if (!valid) throw new IllegalArgumentException(); }
}
