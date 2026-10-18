package ai.loomspan.sidecar.security;

import java.net.InetAddress;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

@ConfigurationProperties("loomspan-sidecar.trust")
public class SidecarTrustProperties {
    private String mode;
    private final Colocated colocated = new Colocated();
    private final File file = new File();
    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public Colocated getColocated() { return colocated; }
    public File getFile() { return file; }
    public static class Colocated {
        private String keyDirectory;
        public String getKeyDirectory() { return keyDirectory; }
        public void setKeyDirectory(String value) { keyDirectory = value; }
    }
    public static class File {
        private String jwks;
        public String getJwks() { return jwks; }
        public void setJwks(String value) { jwks = value; }
    }
    public Path resolve(Environment environment) {
        if (!"colocated".equals(mode) && !"file".equals(mode)) throw new IllegalArgumentException(
                "loomspan-sidecar.trust.mode is required: choose colocated or file; see examples/quickstart");
        String value;
        if ("colocated".equals(mode)) {
            if (file.jwks != null) throw new IllegalArgumentException("loomspan-sidecar.trust.file.jwks belongs to the other trust mode");
            value = colocated.keyDirectory;
            requirePath(value, "colocated.key-directory");
            loopback(environment.getProperty("server.address"), "server.address");
            Integer managementPort = environment.getProperty("management.server.port", Integer.class);
            Integer serverPort = environment.getProperty("server.port", Integer.class, 8080);
            if (managementPort != null && managementPort != -1
                    && (managementPort == 0 || !managementPort.equals(serverPort))) {
                loopback(environment.getProperty("management.server.address"), "management.server.address");
            }
            return Path.of(value).toAbsolutePath().normalize().resolve("jwks.json");
        }
        if (colocated.keyDirectory != null) throw new IllegalArgumentException("loomspan-sidecar.trust.colocated.key-directory belongs to the other trust mode");
        value = file.jwks;
        requirePath(value, "file.jwks");
        return Path.of(value).toAbsolutePath().normalize();
    }
    private static void requirePath(String value, String property) {
        if (value == null || value.isBlank() || value.matches("(?i)^[a-z][a-z0-9+.-]*:.*")
                && !(value.length() > 2 && value.charAt(1) == ':'
                    && (value.charAt(2) == '/' || value.charAt(2) == '\\'))) {
            throw new IllegalArgumentException("loomspan-sidecar.trust." + property + " requires a filesystem path");
        }
    }
    private static void loopback(String value, String property) {
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            for (var address : InetAddress.getAllByName(value)) if (!address.isLoopbackAddress()) throw new IllegalArgumentException();
        } catch (Exception failure) {
            throw new IllegalArgumentException("colocated trust requires explicit loopback " + property);
        }
    }
}
