package ai.loomspan.sidecar.http;
import ai.loomspan.api.*;
import com.sun.net.httpserver.*;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.*;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import static ai.loomspan.sidecar.http.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;
class HttpExecutorTlsIntegrationTest {
    private static final char[] PASSWORD="test-only".toCharArray();
    @TempDir Path directory;
    @Test void ordinaryJvmTrustAndHostnameVerificationRemainEnforced() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        var caKey=keyPair();var ca=certificate("CN=CA",caKey,null,null,true,false,false);
        var key=keyPair();var cert=certificate("CN=localhost",key,ca,caKey,false,true,false);
        var trust=trustStore("ca",ca);Path trustPath=directory.resolve("trust.p12");
        try(var out=Files.newOutputStream(trustPath)){trust.store(out,PASSWORD);}
        var server=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setHttpsConfigurator(new HttpsConfigurator(context(keyStore("server",key,cert,ca),trust)));
        server.createContext("/",e->respond(e,200,"text/plain","https"));server.start();
        String oldPath=System.getProperty("javax.net.ssl.trustStore"),oldPass=System.getProperty("javax.net.ssl.trustStorePassword");
        SSLContext previousDefault=SSLContext.getDefault();
        try {
            System.setProperty("javax.net.ssl.trustStore",trustPath.toString());System.setProperty("javax.net.ssl.trustStorePassword",new String(PASSWORD));
            // Reinitialize the standard JVM default from its trust-store properties so an
            // earlier test's cached SSLContext cannot control this certificate fixture.
            var jvmTrust=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            jvmTrust.init((KeyStore)null);
            var jvmDefault=SSLContext.getInstance("TLS");jvmDefault.init(null,jvmTrust.getTrustManagers(),new SecureRandom());
            SSLContext.setDefault(jvmDefault);
            try(var client=new HttpExecutorClient()) {
                var executor=new HttpSkillExecutor(client,new ai.loomspan.sidecar.configuration.ProviderCredentialCipher(null),Clock.systemUTC());
                var settings=map("targets",Map.of("app",map("base-url","https://localhost:"+server.getAddress().getPort(),"connect-timeout","1s","read-timeout","1s")));
                assertThat(executor.execute(new SkillExecutorInvocation(Map.of(),operation(),settings,"call","g"))).isEqualTo("https");
                var wrong=map("targets",Map.of("app",map("base-url","https://127.0.0.1:"+server.getAddress().getPort(),"connect-timeout","1s","read-timeout","1s")));
                assertThatThrownBy(()->executor.execute(new SkillExecutorInvocation(Map.of(),operation(),wrong,"call","g"))).isInstanceOf(SkillException.class).hasMessage("HTTP transport failed");
            }
            var otherKey=keyPair();var otherCa=certificate("CN=Other",otherKey,null,null,true,false,false);
            var otherServer=HttpsServer.create(new InetSocketAddress("127.0.0.1",0),0);
            var otherServerKey=keyPair();var otherCert=certificate("CN=localhost",otherServerKey,otherCa,otherKey,false,true,false);
            otherServer.setHttpsConfigurator(new HttpsConfigurator(context(keyStore("server",otherServerKey,otherCert,otherCa),trustStore("other",otherCa))));
            otherServer.createContext("/",e->respond(e,200,null,"untrusted"));otherServer.start();
            try(var client=new HttpExecutorClient()) {
                var executor=new HttpSkillExecutor(client,new ai.loomspan.sidecar.configuration.ProviderCredentialCipher(null),Clock.systemUTC());
                assertThatThrownBy(()->executor.execute(new SkillExecutorInvocation(Map.of(),operation(),map("targets",Map.of("app",map("base-url","https://localhost:"+otherServer.getAddress().getPort(),"connect-timeout","1s","read-timeout","1s"))),"call","g"))).isInstanceOf(SkillException.class).hasMessage("HTTP transport failed");
            }finally{otherServer.stop(0);}
        } finally {
            SSLContext.setDefault(previousDefault);
            if(oldPath==null)System.clearProperty("javax.net.ssl.trustStore");else System.setProperty("javax.net.ssl.trustStore",oldPath);
            if(oldPass==null)System.clearProperty("javax.net.ssl.trustStorePassword");else System.setProperty("javax.net.ssl.trustStorePassword",oldPass);
            server.stop(0);
        }
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
