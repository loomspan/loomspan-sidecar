package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/configuration")
public final class ManagementConfigurationController {
    public record Publish(String tabId, UUID grantId, UUID expectedCandidateId) {}
    public record Problem(String code, String error, UUID publishedId, UUID intendedId,
            String intendedStatus, String mutationFault) {}

    private final RuntimeConfigurationService runtime;
    private final ManagementEditingService editing;

    public ManagementConfigurationController(RuntimeConfigurationService runtime, ManagementEditingService editing) {
        this.runtime = runtime;
        this.editing = editing;
    }

    @GetMapping("/current")
    public RuntimeConfigurationService.Current current() { return runtime.current(); }

    @GetMapping("/history")
    public List<ConfigurationSnapshot> history() { return runtime.history(); }

    @GetMapping("/history/{id}")
    public ResponseEntity<?> history(@PathVariable("id") UUID id) {
        var snapshot = runtime.history(id);
        return snapshot == null ? ResponseEntity.status(404).body(Map.of("code", "history_not_found",
                "error", "Snapshot is unknown or no longer retained")) : ResponseEntity.ok(snapshot);
    }

    @PostMapping("/publish")
    public ConfigurationSnapshot publish(@RequestBody Publish body, Authentication auth, HttpServletRequest request) {
        return editing.publish(request.getSession(false), ManagementController.principal(auth),
                body.tabId(), body.grantId(), body.expectedCandidateId());
    }

    @ExceptionHandler(ManagementEditingService.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(ManagementEditingService.Conflict conflict) {
        return ResponseEntity.status(409).body(Map.of("code", conflict.code(),
                "error", "Editing grant, candidate, validation, or runtime base is no longer current"));
    }

    @ExceptionHandler(RuntimeConfigurationService.PublicationFailure.class)
    ResponseEntity<Problem> publicationFailure(RuntimeConfigurationService.PublicationFailure failure) {
        var state = runtime.inspect();
        return ResponseEntity.status(503).body(new Problem(failure.code(), failure.getMessage(),
                state.publishedId(), state.intendedId(),
                state.intendedStatus() == null ? null : state.intendedStatus().name(), state.mutationFault()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("code", "invalid_request", "error", "Invalid configuration request"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(503).body(Map.of("code", "configuration_unavailable",
                "error", "Configuration storage or mutations are unavailable; inspect status before retrying"));
    }
}
