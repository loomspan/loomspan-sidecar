package ai.loomspan.sdk.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import java.nio.file.*;
import java.time.Duration;
import ai.loomspan.sdk.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class ExecutionTokenAutoConfigurationTest {
    @TempDir Path directory;
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SidecarClientAutoConfiguration.class, ExecutionTokenAutoConfiguration.class))
            .withPropertyValues("loomspan-sdk.base-url=http://localhost:12345");
    @Test void missingModeNamesBothOptionsAndQuickstart() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SidecarClientAutoConfiguration.class, ExecutionTokenAutoConfiguration.class))
                .withPropertyValues("loomspan-sdk.base-url=http://localhost:12345")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining("loomspan-sdk.signing.mode")
                            .hasStackTraceContaining("colocated").hasStackTraceContaining("provided")
                            .hasStackTraceContaining("quickstart");
                });
    }

    @Test void explicitModeBindsDefaultsAndDiscoversConfiguration() throws Exception {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(ExecutionTokenAutoConfiguration.class.getName(), SidecarClientAutoConfiguration.class.getName());
        runner.withPropertyValues("loomspan-sdk.signing.mode=colocated",
                "loomspan-sdk.signing.colocated.key-directory=" + directory,
                "spring.application.name=some-app").run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(SigningKeySource.class)
                    .hasSingleBean(ExecutionTokenIssuer.class).hasSingleBean(SpringExecutionTokenIssuer.class);
            assertThat(context.getBean(IssuanceProperties.class).getIssuer()).isEqualTo("loomspan-app");
            assertThat(context.getBean(IssuanceProperties.class).getLifetime()).isEqualTo(Duration.ofMinutes(15));
            assertThat(context.getBean(IssuanceProperties.class).getRoles()).isEmpty();
            assertThat(Files.exists(directory.resolve("jwks.json"))).isTrue();
        });
        runner.withPropertyValues("loomspan-sdk.signing.mode=colocated", "loomspan-sdk.signing.colocated.key-directory=" + directory,
                "loomspan-sdk.issuance.issuer=group", "loomspan-sdk.issuance.lifetime=2h",
                "loomspan-sdk.issuance.roles=ROLE_X,X", "loomspan-sdk.issuance.additional-audiences=other")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(IssuanceProperties.class).getRoles()).containsExactly("ROLE_X", "X");
                    assertThat(context.getBean(IssuanceProperties.class).getAdditionalAudiences()).containsExactly("other");
                });
    }
    @Test void invalidAndMixedSettingsFailWithoutEchoingLiteralPasswords(CapturedOutput output) {
        String prefix = "loomspan-sdk.signing.";
        for (String[] settings : new String[][] {
                {prefix + "mode=unknown"}, {prefix + "mode=colocated"},
                {prefix + "mode=colocated", prefix + "colocated.key-directory=" + directory.resolve("absent")},
                {prefix + "mode=colocated", prefix + "colocated.key-directory=" + directory, prefix + "provided.format=PEM"},
                {prefix + "mode=provided", prefix + "colocated.key-directory=" + directory},
                {prefix + "mode=provided", prefix + "provided.format=PEM"},
                {prefix + "mode=colocated", prefix + "colocated.key-directory=" + directory, "loomspan-sdk.issuance.lifetime=0s"},
                {prefix + "mode=colocated", prefix + "colocated.key-directory=" + directory, "loomspan-sdk.issuance.issuer="},
                {prefix + "mode=colocated", prefix + "colocated.key-directory=" + directory, prefix + "provided.keystore-password=forbidden-secret-marker"}}) {
            runner.withPropertyValues(settings).run(context -> {
                assertThat(context).hasFailed();
                var diagnostic = new java.io.StringWriter();
                context.getStartupFailure().printStackTrace(new java.io.PrintWriter(diagnostic));
                assertThat(diagnostic.toString()).doesNotContain("forbidden-secret-marker");
            });
        }
        runner.withBean(SidecarClient.class, () -> SidecarClient.builder().baseUrl(java.net.URI.create("http://localhost:12345")).build())
                .run(context -> assertThat(context).hasFailed());
        assertThat(output.getAll()).doesNotContain("forbidden-secret-marker");
    }
    @Test void providedPemUsesCoreLoaderAndExportsPublicOnly() throws Exception {
        var generator = java.security.KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var pem = directory.resolve("signer.pem");
        Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n" + java.util.Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        var export = directory.resolve("public.json");
        runner.withPropertyValues("loomspan-sdk.signing.mode=provided", "loomspan-sdk.signing.provided.format=PEM",
                "loomspan-sdk.signing.provided.key-location=" + pem, "loomspan-sdk.signing.provided.jwks-export=" + export)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SigningKeySource.class).publicKey()).isEqualTo(pair.getPublic());
                });
        assertThat(Files.readString(export)).doesNotContain("PRIVATE", "\"d\"");
    }
}
