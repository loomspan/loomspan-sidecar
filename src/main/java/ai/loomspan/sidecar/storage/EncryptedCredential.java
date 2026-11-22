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
        if (!validIdentifier(identifier)
                || version == null || !version.matches("[A-Za-z0-9_.-]{1,200}"))
            throw new IllegalArgumentException("Invalid credential metadata");
    }
    public static boolean validIdentifier(String name) {
        return name != null && !name.isEmpty()
                && !whitespace(name.codePointAt(0)) && !whitespace(name.codePointBefore(name.length()));
    }
    public static boolean blank(String value) { return value.codePoints().allMatch(EncryptedCredential::whitespace); }
    private static boolean whitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
    @Override public String toString() { return "Encrypted credential [redacted]"; }
}
