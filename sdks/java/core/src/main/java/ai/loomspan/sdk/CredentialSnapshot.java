package ai.loomspan.sdk;

/** An already-issued execution credential. Never exposes its transport value. */
public final class CredentialSnapshot {
    private final String token;
    private CredentialSnapshot(String token) { this.token = token; }
    public static CredentialSnapshot of(String token) {
        if (token == null || token.isBlank() || token.chars().anyMatch(c -> c <= 0x20 || c >= 0x7f))
            throw new LocalFailureException("Invalid execution credential");
        return new CredentialSnapshot(token);
    }
    String authorization() { return "Bearer " + token; }
    String redact(String value) { return value.replace(token, "[redacted]"); }
    @Override public String toString() { return "CredentialSnapshot[redacted]"; }
}
