package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.storage.ConfigurationDraft;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Ephemeral private state. Callers serialize access through the runtime transition gate. */
@Component
public final class ManagementEditingState {
    public static final class Entry {
        public final long accountId;
        public final UUID draftId = UUID.randomUUID();
        public UUID candidateId = UUID.randomUUID();
        public final ConfigurationDraft draft;

        public Entry(long accountId, ConfigurationDraft draft) {
            this.accountId = accountId;
            this.draft = draft;
        }
    }

    public static final class Lease {
        public final String sessionId;
        public final String tabId;
        public final UUID grantId = UUID.randomUUID();
        public long expiresAt;

        public Lease(String sessionId, String tabId, long expiresAt) {
            this.sessionId = sessionId;
            this.tabId = tabId;
            this.expiresAt = expiresAt;
        }
    }

    public final Map<String, Entry> drafts = new HashMap<>();
    public Lease lease;

    public void clearSession(String sessionId) {
        drafts.remove(sessionId);
        if (lease != null && lease.sessionId.equals(sessionId)) lease = null;
    }

    public void clearAccount(long accountId) {
        drafts.entrySet().removeIf(entry -> entry.getValue().accountId == accountId);
        if (lease != null && !drafts.containsKey(lease.sessionId)) lease = null;
    }

    public void clearAll() {
        drafts.clear();
        lease = null;
    }
}
