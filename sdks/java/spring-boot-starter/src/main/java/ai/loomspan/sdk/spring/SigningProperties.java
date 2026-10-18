package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.*;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Startup signing settings. No mode is selected by the starter. */
@ConfigurationProperties("loomspan-sdk.signing")
public class SigningProperties {
    private String mode;
    private final Colocated colocated = new Colocated();
    private final Provided provided = new Provided();
    public String getMode() { return mode; }
    public void setMode(String value) { mode = value; }
    public Colocated getColocated() { return colocated; }
    public Provided getProvided() { return provided; }
    SigningSettings settings() {
        var p = provided;
        boolean hasProvided = p.format != null || p.keyLocation != null || p.keyAlias != null
                || p.keystorePasswordReference != null || p.keyPasswordReference != null
                || p.certificateLocation != null || p.jwksExport != null;
        return new SigningSettings(mode, colocated.keyDirectory, hasProvided ? new SigningSettings.Provided(
                p.format, p.keyLocation, p.keyAlias,
                p.keystorePasswordReference == null ? null : new SecretReference(p.keystorePasswordReference),
                p.keyPasswordReference == null ? null : new SecretReference(p.keyPasswordReference),
                p.certificateLocation, p.jwksExport) : null);
    }
    public static class Colocated {
        private Path keyDirectory;
        public Path getKeyDirectory() { return keyDirectory; }
        public void setKeyDirectory(Path value) { keyDirectory = value; }
    }
    public static class Provided {
        private String format;
        private Path keyLocation;
        private String keyAlias;
        private String keystorePasswordReference;
        private String keyPasswordReference;
        private Path certificateLocation;
        private Path jwksExport;
        public String getFormat() { return format; }
        public void setFormat(String value) { format = value; }
        public Path getKeyLocation() { return keyLocation; }
        public void setKeyLocation(Path value) { keyLocation = value; }
        public String getKeyAlias() { return keyAlias; }
        public void setKeyAlias(String value) { keyAlias = value; }
        public String getKeystorePasswordReference() { return keystorePasswordReference; }
        public void setKeystorePasswordReference(String value) { keystorePasswordReference = value; }
        public String getKeyPasswordReference() { return keyPasswordReference; }
        public void setKeyPasswordReference(String value) { keyPasswordReference = value; }
        public Path getCertificateLocation() { return certificateLocation; }
        public void setCertificateLocation(Path value) { certificateLocation = value; }
        public Path getJwksExport() { return jwksExport; }
        public void setJwksExport(Path value) { jwksExport = value; }
    }
}
