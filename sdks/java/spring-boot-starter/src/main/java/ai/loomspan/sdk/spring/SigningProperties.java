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
        boolean hasProvided = p.keyLocation != null || p.jwksExport != null;
        return new SigningSettings(mode, colocated.keyDirectory,
                hasProvided ? new SigningSettings.Provided(p.keyLocation, p.jwksExport) : null);
    }

    public static class Colocated {
        private Path keyDirectory;
        public Path getKeyDirectory() { return keyDirectory; }
        public void setKeyDirectory(Path value) { keyDirectory = value; }
    }
    public static class Provided {
        private Path keyLocation;
        private Path jwksExport;
        public Path getKeyLocation() { return keyLocation; }
        public void setKeyLocation(Path value) { keyLocation = value; }
        public Path getJwksExport() { return jwksExport; }
        public void setJwksExport(Path value) { jwksExport = value; }
    }
}
