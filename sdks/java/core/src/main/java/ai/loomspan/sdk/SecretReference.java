package ai.loomspan.sdk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/** A reference to an environment variable or an absolute UTF-8 secret file. */
public final class SecretReference {
    private final String reference;
    public SecretReference(String reference) { this.reference = reference; }
    Path filePath() {
        try { return reference != null && reference.startsWith("file:") ? Path.of(reference.substring(5)) : null; }
        catch (java.nio.file.InvalidPathException ignored) { return null; }
    }
    char[] resolve() { return resolve(System::getenv); }
    char[] resolve(Function<String, String> environment) {
        try {
            String value;
            if (reference != null && reference.startsWith("env:") && reference.length() > 4) {
                value = environment.apply(reference.substring(4));
            } else if (reference != null && reference.startsWith("file:")
                    && Path.of(reference.substring(5)).isAbsolute()) {
                value = Files.readString(Path.of(reference.substring(5)));
                if (value.endsWith("\r\n")) value = value.substring(0, value.length() - 2);
                else if (value.endsWith("\n")) value = value.substring(0, value.length() - 1);
            } else throw new IllegalArgumentException();
            if (value == null || value.isEmpty()) throw new IllegalArgumentException();
            return value.toCharArray();
        } catch (Exception ignored) {
            throw new IllegalArgumentException("Password reference must resolve using env:NAME or file:/absolute/path");
        }
    }
    @Override public String toString() { return "SecretReference[redacted]"; }
}
