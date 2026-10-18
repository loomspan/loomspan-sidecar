package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.CredentialSnapshot;
import ai.loomspan.sdk.LocalFailureException;
import ai.loomspan.sdk.ProtocolException;
import ai.loomspan.sdk.SidecarClient;
import ai.loomspan.sdk.SidecarException;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidecarClientAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SslAutoConfiguration.class,
                    SidecarClientAutoConfiguration.class));
    private final CredentialSnapshot credential = CredentialSnapshot.of("test-token");

    @Test
    void registersAutoConfigurationAndBindsDefaults() {
        assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
                .contains(SidecarClientAutoConfiguration.class.getName());
        runner.withPropertyValues("loomspan-sdk.base-url=http://localhost:12345/prefix")
                .run(context -> {
                    assertThat(context).hasSingleBean(SidecarClient.class);
                    var properties = context.getBean(SidecarClientProperties.class);
                    assertThat(properties.getBaseUrl()).isEqualTo(URI.create("http://localhost:12345/prefix"));
                    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofSeconds(10));
                    assertThat(properties.getMaxResponseSize()).isEqualTo(8L * 1024 * 1024);
                    assertThat(properties.getSslBundle()).isNull();
                });
    }

    @Test
    void usesConfiguredValuesAndClosesTheClientWithTheContext() throws Exception {
        var attempts = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/prefix/v1/skills", exchange -> {
            attempts.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer test-token");
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        var created = new SidecarClient[1];
        try {
            runner.withPropertyValues("loomspan-sdk.base-url=http://localhost:" + server.getAddress().getPort() + "/prefix",
                    "loomspan-sdk.connect-timeout=700ms", "loomspan-sdk.response-timeout=900ms",
                    "loomspan-sdk.max-response-size=2048").run(context -> {
                assertThat(context).hasNotFailed();
                var properties = context.getBean(SidecarClientProperties.class);
                assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofMillis(700));
                assertThat(properties.getResponseTimeout()).isEqualTo(Duration.ofMillis(900));
                assertThat(properties.getMaxResponseSize()).isEqualTo(2048);
                created[0] = context.getBean(SidecarClient.class);
                assertThat(created[0].listSkills(credential)).isEmpty();
            });
            assertThatThrownBy(() -> created[0].listSkills(credential)).isInstanceOf(LocalFailureException.class);
            assertThat(attempts).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void configuredResponseLimitAndTimeoutHaveCoreSemantics() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/v1/skills", exchange -> {
            byte[] body = "[             ]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            runner.withPropertyValues("loomspan-sdk.base-url=http://localhost:" + server.getAddress().getPort(),
                    "loomspan-sdk.max-response-size=4").run(context -> {
                assertThat(context).hasNotFailed();
                assertThatThrownBy(() -> context.getBean(SidecarClient.class).listSkills(credential))
                        .isInstanceOf(ProtocolException.class);
            });
        } finally {
            server.stop(0);
        }

        var slow = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        slow.createContext("/v1/skills", exchange -> {
            try { Thread.sleep(500); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            exchange.close();
        });
        slow.start();
        try {
            runner.withPropertyValues("loomspan-sdk.base-url=http://localhost:" + slow.getAddress().getPort(),
                    "loomspan-sdk.response-timeout=50ms").run(context -> {
                assertThat(context).hasNotFailed();
                long start = System.nanoTime();
                assertThatThrownBy(() -> context.getBean(SidecarClient.class).listSkills(credential))
                        .isInstanceOf(SidecarException.class);
                assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            });
        } finally {
            slow.stop(0);
        }
    }

    @Test
    void invalidOrMissingConfigurationFailsStartup() {
        runner.run(context -> assertThat(context).hasFailed());
        for (String invalid : new String[] { "loomspan-sdk.base-url=http://user@localhost",
                "loomspan-sdk.connect-timeout=0ms", "loomspan-sdk.response-timeout=-1s",
                "loomspan-sdk.max-response-size=0", "loomspan-sdk.ssl-bundle=missing" }) {
            runner.withPropertyValues("loomspan-sdk.base-url=http://localhost", invalid)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test
    void userClientOverridesConfigurationWithoutRequiringAConfiguredBaseUrl() {
        try (var supplied = SidecarClient.builder().baseUrl(URI.create("http://localhost:12345")).build()) {
            runner.withBean(SidecarClient.class, () -> supplied)
                    .run(context -> assertThat(context.getBean(SidecarClient.class)).isSameAs(supplied));
        }
    }

    @Test
    void bootSslBundleTrustsCertificateAndStillChecksHostname(@TempDir Path temporary) throws Exception {
        Path storePath = temporary.resolve("localhost.p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool.exe");
        if (!Files.exists(keytool)) keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        var process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "localhost",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost", "-storetype", "PKCS12", "-keystore", storePath.toString(),
                "-storepass", "test-password", "-keypass", "test-password", "-noprompt")
                .redirectErrorStream(true).redirectOutput(temporary.resolve("keytool.log").toFile()).start();
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).isZero();
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(storePath)) { store.load(input, "test-password".toCharArray()); }
        var managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        managers.init(store, "test-password".toCharArray());
        var ssl = SSLContext.getInstance("TLS");
        ssl.init(managers.getKeyManagers(), null, null);
        var server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ssl));
        server.createContext("/v1/skills", exchange -> {
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            String[] bundle = { "loomspan-sdk.ssl-bundle=sidecar",
                    "spring.ssl.bundle.jks.sidecar.truststore.location=" + storePath.toUri(),
                    "spring.ssl.bundle.jks.sidecar.truststore.password=test-password",
                    "spring.ssl.bundle.jks.sidecar.truststore.type=PKCS12" };
            runner.withPropertyValues(bundle).withPropertyValues(
                    "loomspan-sdk.base-url=https://localhost:" + server.getAddress().getPort())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context.getBean(SidecarClient.class).listSkills(credential)).isEmpty();
                    });
            runner.withPropertyValues(bundle).withPropertyValues(
                    "loomspan-sdk.base-url=https://127.0.0.1:" + server.getAddress().getPort())
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThatThrownBy(() -> context.getBean(SidecarClient.class).listSkills(credential))
                                .isInstanceOf(SidecarException.class);
                    });
        } finally {
            server.stop(0);
        }
    }
}
