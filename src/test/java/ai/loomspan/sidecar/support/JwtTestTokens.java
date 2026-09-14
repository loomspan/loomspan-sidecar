package ai.loomspan.sidecar.support;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

public final class JwtTestTokens {
    private JwtTestTokens() { }

    public static String token(String subject, List<String> roles) {
        return token("https://issuer.test", "sidecar", subject, roles);
    }

    public static String token(String issuer, String audience, String subject, List<String> roles) {
        return token(issuer, audience, subject, roles, 300);
    }

    public static String token(String issuer, String audience, String subject, List<String> roles,
            long lifetimeSeconds) {
        return token(issuer, audience, subject, roles, lifetimeSeconds, true);
    }

    public static String tokenWithoutExpiration(String issuer, String audience, String subject,
            List<String> roles) {
        return token(issuer, audience, subject, roles, 0, false);
    }

    private static String token(String issuer, String audience, String subject, List<String> roles,
            long lifetimeSeconds, boolean includeExpiration) {
        try {
            Instant now = Instant.now();
            Instant issuedAt = lifetimeSeconds < 0 ? now.minusSeconds(300) : now;
            var builder = new JWTClaimsSet.Builder().issuer(issuer).subject(subject)
                    .audience(audience).issueTime(Date.from(issuedAt)).claim("roles", roles);
            if (includeExpiration) builder.expirationTime(Date.from(now.plusSeconds(lifetimeSeconds)));
            var claims = builder.build();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(privateKey()));
            return jwt.serialize();
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static RSAPrivateKey privateKey() throws Exception {
        try (var input = JwtTestTokens.class.getClassLoader().getResourceAsStream("fixtures/jwt-private.pem")) {
            String pem = new String(input.readAllBytes(), StandardCharsets.US_ASCII)
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        }
    }
}
