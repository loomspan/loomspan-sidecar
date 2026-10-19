package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.SigningKeySource;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.oauth2.jwt.BadJwtException;
import static org.assertj.core.api.Assertions.*;

class CallbackJwtDecoderTest {
    static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    static final KeyPair PAIR = pair();
    static final KeyPair OTHER = pair();
    CallbackJwtTrust trust;
    CallbackJwtDecoder decoder;

    @BeforeEach void setup() {
        trust = new CallbackJwtTrust(source(PAIR, "own"), new CallbackProperties());
        decoder = new CallbackJwtDecoder(trust, "issuer", "receiver", Clock.fixed(NOW, ZoneOffset.UTC));
    }
    @AfterEach void close() { trust.close(); }

    @Test void preservesVerifiedIdentityExactRolesAndBothAudienceShapes() {
        for (Object audience : List.of("receiver", List.of("other", "receiver"))) {
            var claims = claims(); claims.put("aud", audience);
            claims.put("scope", "admin"); claims.put("iat", NOW.minusSeconds(10).getEpochSecond());
            claims.put("jti", "id");
            var jwt = decoder.decode(token(headers(), claims, PAIR));
            assertThat(jwt.getClaimAsString("iss")).isEqualTo("issuer");
            assertThat(jwt.getSubject()).isEqualTo("alice");
            assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_ORDER_EDITOR", "ORDER_EDITOR", "other");
            assertThat(jwt.getAudience()).contains("receiver");
        }
        var defaults = new CallbackProperties();
        assertThat(defaults.audienceOrDefault("issuer")).isEqualTo("issuer");
        defaults.setAudience("receiver"); assertThat(defaults.audienceOrDefault("issuer")).isEqualTo("receiver");
        var byIssuer = new CallbackJwtDecoder(trust, "issuer", "issuer", Clock.fixed(NOW, ZoneOffset.UTC));
        var claims = claims(); claims.put("aud", "issuer");
        assertThat(byIssuer.decode(token(headers(), claims, PAIR)).getSubject()).isEqualTo("alice");
    }

    @Test void rejectsMalformedRawClaimShapesWithoutCoercionOrCredentialDisclosure() {
        var invalid = new LinkedHashMap<String, List<Object>>();
        invalid.put("iss", Arrays.asList(null, "", " ", 3, "different"));
        invalid.put("sub", Arrays.asList(null, "", " ", 3));
        invalid.put("token_use", Arrays.asList(null, "", "other", 3));
        invalid.put("aud", Arrays.asList(null, "wrong", "", List.of(), 3, List.of("receiver", 3), List.of("wrong")));
        invalid.put("roles", Arrays.asList(null, "ROLE_ORDER_EDITOR", 3, List.of("ROLE_ORDER_EDITOR", 3), List.of(""), List.of(" ")));
        invalid.put("exp", Arrays.asList(null, "123", "NaN", true, List.of(), new java.math.BigDecimal("1e40"), Long.MAX_VALUE));
        invalid.put("iat", Arrays.asList(null, "123", true, new java.math.BigDecimal("1e40")));
        invalid.put("nbf", Arrays.asList(null, "123", true, new java.math.BigDecimal("1e40")));
        invalid.put("jti", Arrays.asList(null, 123, List.of()));
        invalid.forEach((name, values) -> values.forEach(value -> {
            var claims = claims(); claims.put(name, value);
            rejects(token(headers(), claims, PAIR));
        }));
        for (String name : List.of("iss", "sub", "aud", "exp", "roles", "token_use")) {
            var claims = claims(); claims.remove(name); rejects(token(headers(), claims, PAIR));
        }
    }

    @Test void rejectsWrongHeadersSignaturesAndUnsignedTokens() {
        for (String name : List.of("alg", "typ", "kid")) {
            for (Object value : Arrays.asList(null, "", " ", 3, "wrong")) {
                var headers = headers(); headers.put(name, value); rejects(token(headers, claims(), PAIR));
            }
            var headers = headers(); headers.remove(name); rejects(token(headers, claims(), PAIR));
        }
        for (String algorithm : List.of("HS256", "RS512", "none")) {
            var headers = headers(); headers.put("alg", algorithm); rejects(token(headers, claims(), PAIR));
        }
        rejects(token(headers(), claims(), OTHER));
        String valid = token(headers(), claims(), PAIR);
        rejects(valid.substring(0, valid.lastIndexOf('.') + 1));
        rejects(valid.substring(0, valid.lastIndexOf('.') + 1) + "AAAA");
        rejects("not-a-jwt");
    }

    @Test void timestampSkewIsSixtySecondsWithIndependentFixedClock() {
        var within = claims(); within.put("exp", NOW.minusSeconds(59).getEpochSecond());
        assertThat(decoder.decode(token(headers(), within, PAIR))).isNotNull();
        var beyond = claims(); beyond.put("exp", NOW.minusSeconds(61).getEpochSecond());
        rejects(token(headers(), beyond, PAIR));
        within = claims(); within.put("nbf", NOW.plusSeconds(59).getEpochSecond());
        assertThat(decoder.decode(token(headers(), within, PAIR))).isNotNull();
        beyond = claims(); beyond.put("nbf", NOW.plusSeconds(61).getEpochSecond());
        rejects(token(headers(), beyond, PAIR));
        var fractional = claims(); fractional.put("exp", new java.math.BigDecimal(NOW.plusSeconds(10).getEpochSecond() + ".125"));
        assertThat(decoder.decode(token(headers(), fractional, PAIR)).getExpiresAt()).isEqualTo(NOW.plusSeconds(10).plusMillis(125));
    }

    @Test void propertiesRejectAmbiguousPathsAndBlankExplicitValues() {
        var properties = new CallbackProperties();
        assertThat(properties.validatedPathPrefix()).isEqualTo("/loomspan/callbacks");
        properties.setPathPrefix("/custom/hooks/"); assertThat(properties.validatedPathPrefix()).isEqualTo("/custom/hooks");
        for (String value : Arrays.asList(null, "", " ", "/", "//", "relative", "/a//b", "/a/./b", "/a/../b", "/a?b", "/a#b", "/a*b", "/a\\b", "/a/{id}", "/a%2fb", "/a;b", "/a b")) {
            properties.setPathPrefix(value);
            assertThatThrownBy(properties::validatedPathPrefix).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Invalid loomspan-sdk.callbacks.path-prefix");
        }
        properties.setAudience(" "); assertThatThrownBy(() -> properties.audienceOrDefault("issuer")).hasMessageContaining("callbacks.audience");
        properties.setOverlapJwks(" "); assertThatThrownBy(properties::overlapPath).hasMessageContaining("callbacks.overlap-jwks");
    }

    private void rejects(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class)
                .hasMessage("Invalid callback execution credential").hasNoCause();
    }
    static Map<String, Object> headers() {
        var headers = new LinkedHashMap<String, Object>();
        headers.put("alg", "RS256"); headers.put("typ", "loomspan-execution+jwt"); headers.put("kid", "own"); return headers;
    }
    static Map<String, Object> claims() {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("iss", "issuer"); claims.put("sub", "alice"); claims.put("aud", "receiver");
        claims.put("token_use", "loomspan_execution"); claims.put("exp", NOW.plusSeconds(300).getEpochSecond());
        claims.put("roles", List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR", "other")); return claims;
    }
    static String token(Map<String, Object> headers, Map<String, Object> claims, KeyPair pair) {
        try {
            var base64 = Base64.getUrlEncoder().withoutPadding();
            String input = base64.encodeToString(JSONObjectUtils.toJSONString(headers).getBytes(StandardCharsets.UTF_8)) + "."
                    + base64.encodeToString(JSONObjectUtils.toJSONString(claims).getBytes(StandardCharsets.UTF_8));
            var signature = Signature.getInstance("SHA256withRSA"); signature.initSign(pair.getPrivate());
            signature.update(input.getBytes(StandardCharsets.US_ASCII));
            return input + "." + base64.encodeToString(signature.sign());
        } catch (GeneralSecurityException error) { throw new AssertionError(error); }
    }
    static KeyPair pair() {
        try { var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (GeneralSecurityException error) { throw new AssertionError(error); }
    }
    static SigningKeySource source(KeyPair pair, String kid) {
        return new SigningKeySource() {
            public RSAPublicKey publicKey() { return (RSAPublicKey) pair.getPublic(); }
            public String kid() { return kid; }
            public byte[] signRs256(byte[] input) { throw new UnsupportedOperationException(); }
        };
    }
}
