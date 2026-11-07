package ai.loomspan.sdk;

import java.nio.file.Path;

/** Explicit signing configuration; contains paths, never private material. */
public record SigningSettings(String mode, Path keyDirectory, Provided provided) {
    public record Provided(Path keyLocation, Path jwksExport) {}

    public SigningSettings {
        if (!"colocated".equals(mode) && !"provided".equals(mode))
            throw new IllegalArgumentException("loomspan-sdk.signing.mode is required: colocated or provided; see sdks/java/README.md colocated quickstart");
        if ("colocated".equals(mode)) {
            if (provided != null) throw new IllegalArgumentException("provided settings are forbidden in colocated mode");
            if (keyDirectory == null) throw new IllegalArgumentException("loomspan-sdk.signing.colocated.key-directory is required: <unset>");
        } else {
            if (keyDirectory != null) throw new IllegalArgumentException("colocated settings are forbidden in provided mode");
            if (provided == null) throw new IllegalArgumentException("loomspan-sdk.signing.provided settings are required");
            if (provided.keyLocation() == null || provided.jwksExport() == null)
                throw new IllegalArgumentException("provided.key-location and provided.jwks-export are required");
        }
    }
    public static SigningSettings colocated(Path directory) { return new SigningSettings("colocated", directory, null); }
}
