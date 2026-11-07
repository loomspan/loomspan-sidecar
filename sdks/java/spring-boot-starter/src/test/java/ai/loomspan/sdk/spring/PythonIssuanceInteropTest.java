package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.SigningKeys;
import ai.loomspan.sdk.SigningSettings;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "LOOMSPAN_PYTHON_ISSUANCE_FIXTURES", matches = ".+")
class PythonIssuanceInteropTest {
    private static final Instant NOW = Instant.ofEpochSecond(1792324800L);
    private final Path fixtures = Path.of(System.getenv("LOOMSPAN_PYTHON_ISSUANCE_FIXTURES"));

    @TempDir Path directory;

    @Test void verifiesPythonIssuedCredentialsAndSameKeyPublicExport() throws Exception {
        Path javaExport = directory.resolve("java.json");
        var signer = SigningKeys.initialize(new SigningSettings("provided", null,
                new SigningSettings.Provided(fixtures.resolve("synthetic-rsa.pem"), javaExport)));
        assertThat(JSONObjectUtils.parse(Files.readString(javaExport)))
                .isEqualTo(JSONObjectUtils.parse(Files.readString(fixtures.resolve("provided.json"))));

        var properties = new CallbackProperties();
        properties.setOverlapJwks(fixtures.resolve("jwks.json").toString());
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        try (var trust = new CallbackJwtTrust(signer, properties)) {
            var defaults = new CallbackJwtDecoder(trust, "loomspan-app", "loomspan-app", clock);
            Jwt colocated = defaults.decode(readToken("default"));
            assertProfile(colocated, "bob", List.of("loomspan-sidecar", "loomspan-app"), List.of(), NOW.plusSeconds(900));
            assertThat(colocated.getHeaders().get("kid")).isNotEqualTo(signer.kid());

            var provided = new CallbackJwtDecoder(trust, "loomspan-app", "callbacks", clock);
            for (var sample : Map.of("eighth", NOW.plusNanos(125_000_000),
                    "nanos", NOW.plusNanos(123_456_700), "rounds-up", NOW.plusSeconds(1)).entrySet()) {
                Jwt jwt = provided.decode(readToken(sample.getKey()));
                assertProfile(jwt, "alice", List.of("loomspan-sidecar", "loomspan-app", "callbacks"),
                        List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR"), sample.getValue());
                assertThat(jwt.getHeaders().get("kid")).isEqualTo(signer.kid());
            }
            for (String rejected : List.of("collapses", "rounds-out-of-range")) {
                String token = readToken(rejected);
                assertThatThrownBy(() -> provided.decode(token)).isInstanceOf(BadJwtException.class);
            }
        }
    }

    private String readToken(String name) throws Exception {
        return Files.readString(fixtures.resolve(name + ".jwt"));
    }

    private void assertProfile(Jwt jwt, String subject, List<String> audiences, List<String> roles, Instant expires) {
        assertThat(jwt.getSubject()).isEqualTo(subject);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("loomspan-app");
        assertThat(jwt.getAudience()).containsExactlyElementsOf(audiences);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactlyElementsOf(roles);
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("loomspan_execution");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(expires);
    }
}
