package ai.loomspan.sdk;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/** Generates synthetic Java SDK credentials for the installed Python verifier. */
class JavaCallbackIssuanceFixtureTest {
    private static final String FIXTURE_ENV = "LOOMSPAN_JAVA_CALLBACK_FIXTURES";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_792_324_800L, 123_456_789), ZoneOffset.UTC);

    @Test
    @EnabledIfEnvironmentVariable(named = FIXTURE_ENV, matches = ".+")
    void issueForPython() throws IOException {
        Path directory = Path.of(System.getenv(FIXTURE_ENV));
        var key = SigningKeys.initialize(SigningSettings.colocated(directory));
        var identity = new AuthenticatedIdentity("java:alice", List.of("ROLE_ORDER_EDITOR", "ignored", "ORDER_EDITOR"));
        writeCredential(directory.resolve("regular.jwt"), key, identity, Duration.ofMinutes(15));
        writeCredential(directory.resolve("nanosecond.jwt"), key, identity, Duration.ofNanos(1));
    }

    private static void writeCredential(Path path, SigningKeySource key, AuthenticatedIdentity identity, Duration duration)
            throws IOException {
        var settings = new IssuanceSettings("loomspan-app", duration, List.of("callback-receiver"),
                List.of("ORDER_EDITOR", "ROLE_ORDER_EDITOR"));
        var credential = new ExecutionTokenIssuer(key, settings, CLOCK).issue(identity);
        Files.writeString(path, credential.authorization().substring("Bearer ".length()));
    }
}
