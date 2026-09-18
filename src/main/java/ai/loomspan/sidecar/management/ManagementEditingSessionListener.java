package ai.loomspan.sidecar.management;

import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

/** Servlet expiry, logout and explicit invalidation all remove private editing state. */
@Component
public final class ManagementEditingSessionListener implements HttpSessionListener {
    private final ManagementEditingState editing;

    public ManagementEditingSessionListener(ManagementEditingState editing) { this.editing = editing; }

    @Override public void sessionDestroyed(HttpSessionEvent event) {
        synchronized (editing) { editing.clearSession(event.getSession().getId()); }
    }
}
