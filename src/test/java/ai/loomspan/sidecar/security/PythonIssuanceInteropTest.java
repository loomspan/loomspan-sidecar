package ai.loomspan.sidecar.security;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "LOOMSPAN_PYTHON_ISSUANCE_FIXTURES", matches = ".+")
class PythonIssuanceInteropTest {
    private static final Instant NOW = Instant.ofEpochSecond(1792324800L);
    private final Path fixtures = Path.of(System.getenv("LOOMSPAN_PYTHON_ISSUANCE_FIXTURES"));

    @Test void verifiesPythonIssuedCredentialsThroughCurrentFileTrust() throws Exception {
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        try (var trust = new CurrentFileJwtTrust("file", fixtures.resolve("jwks.json"))) {
            var decoder = new ExecutionJwtDecoder(trust, "loomspan-sidecar", clock);
            assertProfile(decoder.decode(readToken("default")), "bob", List.of("loomspan-sidecar", "loomspan-app"),
                    List.of(), NOW.plusSeconds(900));
        }

        try (var trust = new CurrentFileJwtTrust("file", fixtures.resolve("provided.json"))) {
            var decoder = new ExecutionJwtDecoder(trust, "loomspan-sidecar", clock);
            for (var sample : Map.of("eighth", NOW.plusNanos(125_000_000),
                    "nanos", NOW.plusNanos(123_456_700), "rounds-up", NOW.plusSeconds(1)).entrySet()) {
                assertProfile(decoder.decode(readToken(sample.getKey())), "alice",
                        List.of("loomspan-sidecar", "loomspan-app", "callbacks"),
                        List.of("ROLE_ORDER_EDITOR", "ORDER_EDITOR"), sample.getValue());
            }
            for (String rejected : List.of("collapses", "rounds-out-of-range")) {
                String token = readToken(rejected);
                assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(BadJwtException.class);
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
