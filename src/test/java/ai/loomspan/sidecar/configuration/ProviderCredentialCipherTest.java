package ai.loomspan.sidecar.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class ProviderCredentialCipherTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test void rejectsAmbiguousOrUnboundedAuthenticatedMetadata() {
        var cipher = new ProviderCredentialCipher(KEY);
        for (String identifier : java.util.List.of("", "bad\u0000id", "x".repeat(201)))
            assertThatThrownBy(() -> cipher.encrypt(identifier, "v1", "fixture-secret"))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("fixture-secret");
        assertThatThrownBy(() -> cipher.encrypt("valid.key", "bad\u0000version", "fixture-secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void ciphertextIsRandomizedAndBoundToIdentifierAndVersion() {
        var cipher = new ProviderCredentialCipher(KEY);
        String first = cipher.encrypt("provider.key", "v1", "secret-sentinel");
        String second = cipher.encrypt("provider.key", "v1", "secret-sentinel");
        assertThat(first).isNotEqualTo(second).doesNotContain("secret-sentinel");
        assertThat(cipher.decrypt("provider.key", "v1", first)).isEqualTo("secret-sentinel");
        assertThatThrownBy(() -> cipher.decrypt("other", "v1", first)).hasMessageContaining("cannot be decrypted");
        assertThatThrownBy(() -> cipher.decrypt("provider.key", "v2", first)).hasMessageContaining("cannot be decrypted");
    }

    @Test void absentWrongAndTamperedKeysFailWithoutPlaintextFallback() {
        var cipher = new ProviderCredentialCipher(KEY);
        String sealed = cipher.encrypt("provider.key", "v1", "secret-sentinel");
        assertThatThrownBy(() -> new ProviderCredentialCipher(null).decrypt("provider.key", "v1", sealed))
                .hasMessageContaining("LOOMSPAN_SIDECAR_CREDENTIAL_KEY is required");
        assertThatThrownBy(() -> new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]))
                .decrypt("provider.key", "v1", "not base64***")).hasMessageContaining("cannot be decrypted");
        byte[] different = new byte[32]; different[0] = 1;
        assertThatThrownBy(() -> new ProviderCredentialCipher(Base64.getEncoder().encodeToString(different))
                .decrypt("provider.key", "v1", sealed)).hasMessageContaining("cannot be decrypted");
        byte[] modified = Base64.getDecoder().decode(sealed); modified[modified.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.decrypt("provider.key", "v1", Base64.getEncoder().encodeToString(modified)))
                .hasMessageContaining("cannot be decrypted");
        assertThatThrownBy(() -> new ProviderCredentialCipher("invalid")).hasMessageContaining("32-byte key");
    }
}
