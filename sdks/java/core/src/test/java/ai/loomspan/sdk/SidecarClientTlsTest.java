package ai.loomspan.sdk;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class SidecarClientTlsTest {
    @Test
    void verifiesCertificatesAndHostnamesWithStandardTrustMaterial(@TempDir Path temporary) throws Exception {
        Path storePath = temporary.resolve("localhost.p12");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool.exe");
        if (!Files.exists(keytool)) keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        var process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "localhost",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost",
                "-ext", "SAN=dns:localhost", "-storetype", "PKCS12", "-keystore", storePath.toString(),
                "-storepass", "test-password", "-keypass", "test-password", "-noprompt")
                .redirectErrorStream(true).redirectOutput(temporary.resolve("keytool.log").toFile()).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        var store = KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(storePath)) { store.load(input, "test-password".toCharArray()); }
        var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keys.init(store, "test-password".toCharArray());
        var serverSsl = SSLContext.getInstance("TLS");
        serverSsl.init(keys.getKeyManagers(), null, null);
        var trusts = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trusts.init(store);
        var clientSsl = SSLContext.getInstance("TLS");
        clientSsl.init(null, trusts.getTrustManagers(), null);
        var attempts = new AtomicInteger();
        var server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverSsl));
        server.createContext("/v1/skills", exchange -> {
            attempts.incrementAndGet();
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        var credential = CredentialSnapshot.of("tls-test-token");
        URI correct = URI.create("https://localhost:" + server.getAddress().getPort());
        URI wrongHost = URI.create("https://127.0.0.1:" + server.getAddress().getPort());
        try {
            try (var client = SidecarClient.builder().baseUrl(correct).sslContext(clientSsl).build()) {
                assertTrue(client.listSkills(credential).isEmpty());
            }
            try (var client = SidecarClient.builder().baseUrl(correct).build()) {
                assertThrows(SidecarException.class, () -> client.listSkills(credential));
            }
            try (var client = SidecarClient.builder().baseUrl(wrongHost).sslContext(clientSsl).build()) {
                assertThrows(SidecarException.class, () -> client.listSkills(credential));
            }
            assertEquals(1, attempts.get(), "Untrusted and wrong-host TLS must fail before HTTP");
        } finally {
            server.stop(0);
        }
    }
}
