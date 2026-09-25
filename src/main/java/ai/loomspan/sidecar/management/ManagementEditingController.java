package ai.loomspan.sidecar.management;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/management/editing")
public final class ManagementEditingController {
    public record Capability(UUID editingSessionId, UUID generation) {}
    public record Candidate(UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseSnapshotId) {}
    public record Save(UUID editingSessionId, UUID generation, UUID draftId, long revision,
            UUID baseSnapshotId, List<SkillDocument> skillDocuments, String restRoutesYaml) {}

    private final ManagementEditingService editing;
    public ManagementEditingController(ManagementEditingService editing) { this.editing = editing; }

    @GetMapping
    public ManagementEditingService.Status status(Authentication auth, HttpServletRequest request) {
        return editing.status(request.getSession(false), ManagementController.principal(auth));
    }

    @GetMapping("/draft")
    public ResponseEntity<ManagementEditingService.Draft> draft(Authentication auth, HttpServletRequest request) {
        var found = editing.read(request.getSession(false), ManagementController.principal(auth));
        return found == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(found);
    }

    @PostMapping("/lease")
    public ManagementEditingService.Grant acquire(@RequestBody Map<String, Object> body, Authentication auth, HttpServletRequest request) {
        return editing.acquire(request.getSession(false), ManagementController.principal(auth), label(body), false, false);
    }

    @PostMapping("/lease/handoff")
    public ManagementEditingService.Grant handoff(@RequestBody Map<String, Object> body, Authentication auth, HttpServletRequest request) {
        return editing.acquire(request.getSession(false), ManagementController.principal(auth), label(body), false, true);
    }

    @PostMapping("/lease/takeover")
    public ManagementEditingService.Grant takeover(@RequestBody Map<String, Object> body, Authentication auth, HttpServletRequest request) {
        return editing.acquire(request.getSession(false), ManagementController.principal(auth), label(body), true, false);
    }

    @PostMapping("/lease/renew")
    public ManagementEditingService.Grant renew(@RequestBody Capability body, Authentication auth, HttpServletRequest request) {
        return editing.renew(request.getSession(false), ManagementController.principal(auth),
                body.editingSessionId(), body.generation());
    }

    @PostMapping("/lease/release")
    public ResponseEntity<Void> release(@RequestBody Capability body, Authentication auth, HttpServletRequest request) {
        editing.release(request.getSession(false), ManagementController.principal(auth),
                body.editingSessionId(), body.generation());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/draft")
    public ManagementEditingService.Draft save(@RequestBody Save body, Authentication auth, HttpServletRequest request) {
        return editing.save(request.getSession(false), ManagementController.principal(auth),
                body.editingSessionId(), body.generation(), body.draftId(), body.revision(), body.baseSnapshotId(),
                content(body));
    }

    @PostMapping("/draft/reconcile")
    public ManagementEditingService.Draft reconcile(@RequestBody Save body, Authentication auth, HttpServletRequest request) {
        return editing.reconcile(request.getSession(false), ManagementController.principal(auth),
                body.editingSessionId(), body.generation(), body.draftId(), body.revision(), body.baseSnapshotId(),
                content(body));
    }

    @PostMapping("/draft/validate")
    public ManagementEditingService.Draft validate(@RequestBody Candidate body, Authentication auth, HttpServletRequest request) {
        return editing.validate(request.getSession(false), ManagementController.principal(auth),
                body.editingSessionId(), body.generation(), body.draftId(), body.revision(), body.baseSnapshotId());
    }

    @DeleteMapping("/draft")
    public ResponseEntity<Void> discard(@RequestBody Candidate body, Authentication auth, HttpServletRequest request) {
        editing.discard(request.getSession(false), ManagementController.principal(auth),
                body.editingSessionId(), body.generation(), body.draftId(), body.revision());
        return ResponseEntity.noContent().build();
    }

    private static ManagedConfiguration content(Save body) {
        if (body.skillDocuments() == null || body.restRoutesYaml() == null)
            throw new IllegalArgumentException("Complete configuration required");
        return new ManagedConfiguration(body.skillDocuments(), body.restRoutesYaml());
    }

    private static String label(Map<String, Object> body) {
        if (!body.keySet().equals(java.util.Set.of("label")) || !(body.get("label") instanceof String label)
                || label.isBlank()) throw new IllegalArgumentException("Display label required");
        return label;
    }

    @ExceptionHandler(ManagementEditingService.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(ManagementEditingService.Conflict conflict) {
        return ResponseEntity.status(409).body(Map.of("code", conflict.code(),
                "error", "Editing state changed or is unavailable"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid() {
        return ResponseEntity.badRequest().body(Map.of("code", "invalid_request", "error", "Invalid editing request"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(503).body(Map.of("code", "configuration_unavailable",
                "error", "Configuration mutations are unavailable"));
    }
}
