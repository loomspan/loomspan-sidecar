package ai.loomspan.sdk;

import java.nio.file.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

final class SigningTestMaterial {
    static final String PASSWORD = "synthetic-secret-marker";
    final Path storePath, secret, pem, certificate;
    final KeyStore store;
    SigningTestMaterial(Path directory, String format, String algorithm, int bits) throws Exception {
        Files.createDirectories(directory);
        storePath = directory.resolve("signing.store"); secret = directory.resolve("password");
        pem = directory.resolve("signing.pem"); certificate = directory.resolve("signing.crt");
        Files.writeString(secret, PASSWORD + "\r\n");
        Path keytool = Path.of(System.getProperty("java.home"), "bin", "keytool.exe");
        if (!Files.exists(keytool)) keytool = Path.of(System.getProperty("java.home"), "bin", "keytool");
        var process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "signer", "-keyalg", algorithm,
                "-keysize", "" + bits, "-validity", "90", "-dname", "CN=Synthetic", "-storetype", format,
                "-keystore", storePath.toString(), "-storepass", PASSWORD, "-keypass", PASSWORD, "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        store = KeyStore.getInstance(format);
        try (var input = Files.newInputStream(storePath)) { store.load(input, PASSWORD.toCharArray()); }
        Files.writeString(pem, "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10})
                .encodeToString(store.getKey("signer", PASSWORD.toCharArray()).getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        Files.write(certificate, store.getCertificate("signer").getEncoded());
    }
    SigningSettings settings(String format, Path export, boolean withCertificate) {
        boolean isPem = format.equals("PEM");
        return new SigningSettings("provided", null, new SigningSettings.Provided(format, isPem ? pem : storePath,
                isPem ? null : "signer", isPem ? null : new SecretReference("file:" + secret.toAbsolutePath()),
                null, isPem && withCertificate ? certificate : null, export));
    }
    X509Certificate certificate() throws Exception { return (X509Certificate) store.getCertificate("signer"); }
}
