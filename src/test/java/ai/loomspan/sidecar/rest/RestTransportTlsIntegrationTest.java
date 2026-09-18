package ai.loomspan.sidecar.rest;

import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillException;
import ai.loomspan.sidecar.config.RestRoutesProperties;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestTransportTlsIntegrationTest {
    private static final char[] PASSWORD = "test-only".toCharArray();
    @TempDir Path temporaryDirectory;

    @Test
    void usesTheSelectedSslBundleAndClientIdentityPerTarget() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPair caKey = keyPair();
        X509Certificate ca = certificate("CN=Test CA", caKey, null, null, true, false, false);
        KeyPair serverKey = keyPair();
        X509Certificate serverCertificate = certificate("CN=localhost", serverKey, ca, caKey, false, true, false);
        KeyPair clientAKey = keyPair();
        X509Certificate clientA = certificate("CN=client-a", clientAKey, ca, caKey, false, false, true);
        KeyPair otherCaKey = keyPair();
        X509Certificate otherCa = certificate("CN=Other Test CA", otherCaKey, null, null, true, false, false);
        KeyPair clientBKey = keyPair();
        X509Certificate clientB = certificate("CN=client-b", clientBKey, otherCa, otherCaKey, false, false, true);

        SSLContext serverContext = context(keyStore("server", serverKey, serverCertificate, ca),
                trustStore("client-ca", ca));
        HttpsServer server = HttpsServer.create(new InetSocketAddress("localhost", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverContext) {
            @Override public void configure(com.sun.net.httpserver.HttpsParameters parameters) {
                var ssl = getSSLContext().getDefaultSSLParameters();
                ssl.setNeedClientAuth(true);
                parameters.setSSLParameters(ssl);
            }
        });
        server.createContext("/echo", exchange -> {
            byte[] body = "mutual-tls".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        RestTargetClients targetClients = null;
        try {
            Map<String, SslBundle> bundleMap = Map.of(
                    "client-a", bundle(keyStore("client-a", clientAKey, clientA, ca), trustStore("ca", ca)),
                    "client-b", bundle(keyStore("client-b", clientBKey, clientB, otherCa), trustStore("ca", ca)));
            SslBundles bundles = bundles(bundleMap);
            Path routes = temporaryDirectory.resolve("tls-routes.yaml");
            Files.writeString(routes, """
                    targets:
                      correct: {base-url: 'https://localhost:%d', auth: {mode: none}, ssl-bundle: client-a, connect-timeout: 1s, read-timeout: 2s, max-response-size: 1KB}
                      wrong: {base-url: 'https://localhost:%d', auth: {mode: none}, ssl-bundle: client-b, connect-timeout: 1s, read-timeout: 2s, max-response-size: 1KB}
                      absent: {base-url: 'https://localhost:%d', auth: {mode: none}, connect-timeout: 1s, read-timeout: 2s, max-response-size: 1KB}
                    routes:
                      correct: {target: correct, method: GET, path: /echo}
                      wrong: {target: wrong, method: GET, path: /echo}
                      absent: {target: absent, method: GET, path: /echo}
                    """.formatted(server.getAddress().getPort(), server.getAddress().getPort(), server.getAddress().getPort()));
            RestRoutesProperties properties = new RestRoutesProperties();
            var factory = new DefaultListableBeanFactory();
            factory.registerSingleton("sslBundles", bundles);
            var provider = factory.getBeanProvider(SslBundles.class);
            var loader = new RestRouteLoader(properties, new MockEnvironment(), provider);
            var registry = new GenerationRestResources(loader, provider);
            var staged = registry.prepare(Files.readString(routes));
            registry.stage("test-generation", staged, java.util.UUID.randomUUID());
            var handler = new GenericRestSkillHandler(registry);
            targetClients = staged.clients();
            assertThat(handler.handle(new RestSkillInvocation("correct", Map.of(), "test-generation"))).isEqualTo("mutual-tls");
            assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("wrong", Map.of(), "test-generation")))
                    .isInstanceOf(SkillException.class).hasMessageContaining("transport error");
            assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("absent", Map.of(), "test-generation")))
                    .isInstanceOf(SkillException.class).hasMessageContaining("transport error");
        } finally {
            if (targetClients != null) targetClients.close();
            server.stop(0);
        }
    }

    private static SslBundles bundles(Map<String, SslBundle> bundles) {
        return new SslBundles() {
            @Override public SslBundle getBundle(String name) {
                SslBundle bundle = bundles.get(name);
                if (bundle == null) throw new org.springframework.boot.ssl.NoSuchSslBundleException(name, "test bundle absent");
                return bundle;
            }
            @Override public void addBundleUpdateHandler(String name, Consumer<SslBundle> updateHandler) {}
            @Override public void addBundleRegisterHandler(BiConsumer<String, SslBundle> registerHandler) {}
            @Override public List<String> getBundleNames() { return List.copyOf(bundles.keySet()); }
        };
    }

    private static SslBundle bundle(KeyStore keys, KeyStore trust) {
        return SslBundle.of(SslStoreBundle.of(keys, new String(PASSWORD), trust),
                SslBundleKey.of(new String(PASSWORD)));
    }

    private static SSLContext context(KeyStore keys, KeyStore trust) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keys, PASSWORD);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    private static KeyStore keyStore(String alias, KeyPair key, X509Certificate certificate,
                                     X509Certificate ca) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, PASSWORD);
        store.setKeyEntry(alias, key.getPrivate(), PASSWORD, new java.security.cert.Certificate[] {certificate, ca});
        return store;
    }

    private static KeyStore trustStore(String alias, X509Certificate certificate) throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, PASSWORD);
        store.setCertificateEntry(alias, certificate);
        return store;
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(String subject, KeyPair key, X509Certificate issuerCertificate,
                                               KeyPair issuerKey, boolean ca, boolean server, boolean client)
            throws Exception {
        X500Name subjectName = new X500Name(subject);
        X500Name issuer = issuerCertificate == null ? subjectName
                : new X500Name(issuerCertificate.getSubjectX500Principal().getName());
        var builder = new JcaX509v3CertificateBuilder(issuer, new BigInteger(120, new SecureRandom()),
                Date.from(Instant.now().minusSeconds(60)), Date.from(Instant.now().plusSeconds(3600)),
                subjectName, key.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(ca ? KeyUsage.keyCertSign
                : KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        if (server) {
            builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth));
            builder.addExtension(Extension.subjectAlternativeName, false,
                    new GeneralNames(new GeneralName(GeneralName.dNSName, "localhost")));
        }
        if (client) builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
        KeyPair signer = issuerKey == null ? key : issuerKey;
        var holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA").setProvider("BC")
                .build(signer.getPrivate()));
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }
}
