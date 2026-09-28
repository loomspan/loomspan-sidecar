package ai.loomspan.sidecar.storage;

/** Retained encrypted version. Serialized only in explicitly authorized encrypted bundles. */
public record EncryptedCredential(String identifier, String version, String ciphertext) {
    public EncryptedCredential {
        requireMetadata(identifier, version);
        if (ciphertext == null || ciphertext.length() > 200000)
            throw new IllegalArgumentException("Invalid encrypted credential");
        try {
            if (java.util.Base64.getDecoder().decode(ciphertext).length < 29)
                throw new IllegalArgumentException("Invalid encrypted credential");
        } catch (IllegalArgumentException failure) { throw new IllegalArgumentException("Invalid encrypted credential"); }
    }
    public static void requireMetadata(String identifier, String version) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_.-]{0,199}")
                || version == null || !version.matches("[A-Za-z0-9_.-]{1,200}"))
            throw new IllegalArgumentException("Invalid credential metadata");
    }
    @Override public String toString() { return "Encrypted credential [redacted]"; }
}
