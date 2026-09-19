package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.springframework.stereotype.Component;

/** Servlet expiry, logout and explicit invalidation all remove private editing state. */
@Component
public final class ManagementEditingSessionListener implements HttpSessionListener {
    private final ManagementEditingState editing;
    private final RuntimeConfigurationService runtime;

    public ManagementEditingSessionListener(ManagementEditingState editing,
            RuntimeConfigurationService runtime) {
        this.editing = editing; this.runtime = runtime;
    }

    @Override public void sessionDestroyed(HttpSessionEvent event) {
        runtime.withEditingTransition(() -> {
            synchronized (editing) { editing.clearSession(event.getSession().getId()); }
        });
    }
}
