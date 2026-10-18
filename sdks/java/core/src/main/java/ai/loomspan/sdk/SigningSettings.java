package ai.loomspan.sdk;

import java.nio.file.Path;

/** Explicit signing configuration; contains references, never passwords. */
public record SigningSettings(String mode, Path keyDirectory, Provided provided) {
    public record Provided(String format, Path keyLocation, String keyAlias,
            SecretReference keystorePasswordReference, SecretReference keyPasswordReference,
            Path certificateLocation, Path jwksExport) {}

    public SigningSettings {
        if (!"colocated".equals(mode) && !"provided".equals(mode))
            throw new IllegalArgumentException("loomspan-sdk.signing.mode is required: colocated or provided; see sdks/java/README.md colocated quickstart");
        if ("colocated".equals(mode)) {
            if (provided != null) throw new IllegalArgumentException("provided settings are forbidden in colocated mode");
            if (keyDirectory == null) throw new IllegalArgumentException("loomspan-sdk.signing.colocated.key-directory is required: <unset>");
        } else {
            if (keyDirectory != null) throw new IllegalArgumentException("colocated settings are forbidden in provided mode");
            if (provided == null) throw new IllegalArgumentException("loomspan-sdk.signing.provided settings are required");
            if (!java.util.Set.of("PKCS12", "JKS", "PEM").contains(provided.format() == null ? "" : provided.format()))
                throw new IllegalArgumentException("provided.format must be PKCS12, JKS or PEM");
            if (provided.keyLocation() == null || provided.jwksExport() == null)
                throw new IllegalArgumentException("provided.key-location and provided.jwks-export are required");
            if ("PEM".equals(provided.format())) {
                if (provided.keyAlias() != null || provided.keystorePasswordReference() != null || provided.keyPasswordReference() != null)
                    throw new IllegalArgumentException("PEM forbids key-alias and password references; use unencrypted PKCS#8 PRIVATE KEY");
            } else if (provided.keyAlias() == null || provided.keyAlias().isBlank()
                    || provided.keystorePasswordReference() == null || provided.certificateLocation() != null)
                throw new IllegalArgumentException("Keystores require key-alias and keystore-password-reference; certificate-location is PEM only");
        }
    }
    public static SigningSettings colocated(Path directory) { return new SigningSettings("colocated", directory, null); }
}
