package ai.loomspan.sidecar.management;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;

final class ManagementTokens {
    static final Duration RESET_LIFETIME = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private ManagementTokens() { }

    static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String issue(ManagementAccountRepository accounts, long accountId, String purpose,
            long now, Duration lifetime) {
        String raw = generate();
        accounts.consumeTokens(accountId, now);
        accounts.insertToken(digest(raw), accountId, purpose, now + lifetime.toMillis());
        return raw;
    }

    static String digest(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}")) throw new ManagementIdentityService.Rejected();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
