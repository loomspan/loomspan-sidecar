package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Process-local lease and exact validation proof; saved content is durable elsewhere. */
@Component
public final class ManagementEditingState {
    public static final class Lease {
        public final long accountId;
        public final String origin;
        public final UUID editingSessionId = UUID.randomUUID();
        public final UUID generation = UUID.randomUUID();
        public final String label;
        public long expiresAt;

        public Lease(long accountId, String origin, String label, long expiresAt) {
            this.accountId = accountId;
            this.origin = origin;
            this.label = label;
            this.expiresAt = expiresAt;
        }
    }

    public record Validation(UUID draftId, long revision, UUID baseId, UUID generation,
            ConfigurationValidationResult result) {}

    public Lease lease;
    public Validation validation;

    public void clearSession(String sessionId) {
        clearOrigin("browser:" + sessionId);
    }

    public void clearOrigin(String origin) {
        if (lease != null && lease.origin.equals(origin)) clearLease();
    }

    public void clearAccount(long accountId) {
        if (lease != null && lease.accountId == accountId) clearLease();
    }

    public void clearLease() {
        lease = null;
        validation = null;
    }
}
