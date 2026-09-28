package ai.loomspan.sidecar.configuration;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Encrypts retained provider credentials with key material supplied outside SQLite. */
public final class ProviderCredentialCipher {
    public static final class Failure extends IllegalStateException {
        public Failure(String message) { super(message); }
    }
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] key;

    public ProviderCredentialCipher(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            key = null;
            return;
        }
        try {
            key = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException invalid) {
            throw new Failure("LOOMSPAN_SIDECAR_CREDENTIAL_KEY must be a base64-encoded 32-byte key");
        }
        if (key.length != 32)
            throw new Failure("LOOMSPAN_SIDECAR_CREDENTIAL_KEY must be a base64-encoded 32-byte key");
    }

    public String encrypt(String identifier, String version, String value) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) throw new IllegalArgumentException("Provider credential cannot be blank");
        byte[] nonce = new byte[12];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(128, nonce));
            cipher.updateAAD(aad(identifier, version));
            byte[] sealed = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + sealed.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(sealed, 0, payload, nonce.length, sealed.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException failure) {
            throw new Failure("Provider credential encryption failed");
        }
    }

    public String decrypt(String identifier, String version, String encoded) {
        secretKey();
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);
            if (payload.length < 29) throw new IllegalArgumentException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, payload, 0, 12));
            cipher.updateAAD(aad(identifier, version));
            return new String(cipher.doFinal(payload, 12, payload.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException failure) {
            throw new Failure("Provider credential cannot be decrypted; restore the original LOOMSPAN_SIDECAR_CREDENTIAL_KEY and database backup");
        }
    }

    private SecretKeySpec secretKey() {
        if (key == null)
            throw new Failure("LOOMSPAN_SIDECAR_CREDENTIAL_KEY is required for encrypted provider credentials");
        return new SecretKeySpec(key, "AES");
    }

    private static byte[] aad(String identifier, String version) {
        ai.loomspan.sidecar.storage.EncryptedCredential.requireMetadata(identifier, version);
        return (Objects.requireNonNull(identifier) + "\u0000" + Objects.requireNonNull(version))
                .getBytes(StandardCharsets.UTF_8);
    }
}
