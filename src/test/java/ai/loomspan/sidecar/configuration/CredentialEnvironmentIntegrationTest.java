package ai.loomspan.sidecar.configuration;

import ai.loomspan.sidecar.storage.*;
import ai.loomspan.sidecar.support.SidecarApplicationFixture;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

class CredentialEnvironmentIntegrationTest {
    @TempDir Path directory;
    @Test void productionKeyEnvironmentRestoresOrFailsWithoutChangingCiphertext() throws Exception {
        var database = directory.resolve("retained.db");
        SidecarApplicationFixture.seedDatabase(database, List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES,
                "loomspan:\n  connections:\n    fixture:\n      driver: openai\n      base-url: http://127.0.0.1:9/v1\n      api-key-ref: fixture.key\n",
                Map.of("fixture.key", "retained-test-secret"));
        var jdbc = new JdbcTemplate(StorageConfiguration.dataSource(database));
        var before = jdbc.queryForList("SELECT * FROM configuration_snapshot_credential");
        assertThat(probe(database, null, "database").exit).isNotZero();
        assertThat(probe(database, Base64.getEncoder().encodeToString(new byte[32]), "database").exit).isZero();
        byte[] other = new byte[32]; other[0] = 9;
        var wrong = probe(database, Base64.getEncoder().encodeToString(other), "database");
        assertThat(wrong.exit).isNotZero();
        assertThat(wrong.output).contains("LOOMSPAN_SIDECAR_CREDENTIAL_KEY").doesNotContain("retained-test-secret");
        assertThat(jdbc.queryForList("SELECT * FROM configuration_snapshot_credential")).isEqualTo(before);
        assertThat(probe(database, "malformed-irrelevant-key", "file").exit).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM configuration_snapshot_credential")).isEqualTo(before);
    }
    private record Result(int exit, String output) {}
    private Result probe(Path database, String key, String mode) throws Exception {
        Path output = directory.resolve(UUID.randomUUID() + ".log");
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                "ai.loomspan.sidecar.support.ConfigurationStartupProbe",
                "--loomspan-sidecar.storage.database-path=" + database,
                "--loomspan.skills.locations=classpath:/sidecar-empty-skills/*.yaml",
                "--loomspan.observability.enabled=false", "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"));
        var builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().put("LOOMSPAN_SIDECAR_CONFIGURATION_MODE", mode);
        if (key == null) builder.environment().remove("LOOMSPAN_SIDECAR_CREDENTIAL_KEY");
        else builder.environment().put("LOOMSPAN_SIDECAR_CREDENTIAL_KEY", key);
        var process = builder.start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            return new Result(process.exitValue(), Files.readString(output));
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }
}
