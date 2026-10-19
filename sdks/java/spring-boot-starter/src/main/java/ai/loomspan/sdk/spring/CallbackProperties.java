package ai.loomspan.sdk.spring;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Settings for the SDK-owned, literal callback URL boundary. */
@ConfigurationProperties("loomspan-sdk.callbacks")
public class CallbackProperties {
    private String pathPrefix = "/loomspan/callbacks";
    private String audience;
    private String overlapJwks;

    public String getPathPrefix() { return pathPrefix; }
    public void setPathPrefix(String value) { pathPrefix = value; }
    public String getAudience() { return audience; }
    public void setAudience(String value) { audience = value; }
    public String getOverlapJwks() { return overlapJwks; }
    public void setOverlapJwks(String value) { overlapJwks = value; }

    public String validatedPathPrefix() {
        String value = pathPrefix;
        if (value == null || !value.startsWith("/") || value.isBlank()
                || value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))
                || value.matches(".*[?\\#*{}\\\\%;].*"))
            throw invalid("path-prefix");
        if (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) throw invalid("path-prefix");
        for (String segment : value.substring(1).split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) throw invalid("path-prefix");
        return value;
    }

    public String audienceOrDefault(String issuer) {
        if (audience != null && audience.isBlank()) throw invalid("audience");
        return audience == null ? issuer : audience;
    }

    public Path overlapPath() {
        if (overlapJwks == null) return null;
        if (overlapJwks.isBlank()) throw invalid("overlap-jwks");
        try { return Path.of(overlapJwks); }
        catch (RuntimeException invalidPath) { throw invalid("overlap-jwks"); }
    }

    private static IllegalArgumentException invalid(String property) {
        return new IllegalArgumentException("Invalid loomspan-sdk.callbacks." + property);
    }
}
