package ai.loomspan.sidecar.management;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/management/editing")
public final class ManagementEditingController {
    public record Tab(String tabId) {}
    public record Capability(String tabId, UUID grantId) {}
    public record Save(String tabId, UUID grantId, UUID expectedCandidateId,
            List<SkillDocument> skillDocuments, String restRoutesYaml) {}
    public record Candidate(String tabId, UUID grantId, UUID expectedCandidateId) {}
    public record ValidationResponse(UUID draftId, UUID candidateId, UUID baseSnapshotId,
            boolean applied, ConfigurationValidationResult validation) {}

    private final ManagementEditingService editing;
    private final ManagementSessionGuard sessions;

    public ManagementEditingController(ManagementEditingService editing, ManagementSessionGuard sessions) {
        this.editing = editing;
        this.sessions = sessions;
    }

    @GetMapping
    public ManagementEditingService.Status status(Authentication auth, HttpServletRequest request) {
        return editing.status(session(request), ManagementController.principal(auth));
    }

    @GetMapping("/draft")
    public ResponseEntity<ManagementEditingService.Draft> draft(Authentication auth, HttpServletRequest request) {
        var draft = editing.read(session(request), ManagementController.principal(auth));
        return draft == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(draft);
    }

    @PostMapping("/lease")
    public ManagementEditingService.Grant acquire(@RequestBody Tab body, Authentication auth, HttpServletRequest request) {
        return editing.acquire(session(request), ManagementController.principal(auth), body.tabId(), false);
    }

    @PostMapping("/lease/takeover")
    public ManagementEditingService.Grant takeover(@RequestBody Tab body, Authentication auth, HttpServletRequest request) {
        return editing.acquire(session(request), ManagementController.principal(auth), body.tabId(), true);
    }

    @PostMapping("/lease/activity")
    public ManagementEditingService.Grant activity(@RequestBody Capability body, Authentication auth,
            HttpServletRequest request) {
        synchronized (sessions) {
            var result = editing.activity(session(request), ManagementController.principal(auth),
                    body.tabId(), body.grantId(), sessions.lastReportAt(request));
            sessions.report(request);
            return result;
        }
    }

    @PostMapping("/lease/release")
    public ResponseEntity<Void> release(@RequestBody Capability body, Authentication auth, HttpServletRequest request) {
        editing.release(session(request), ManagementController.principal(auth), body.tabId(), body.grantId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/draft")
    public ManagementEditingService.Draft save(@RequestBody Save body, Authentication auth, HttpServletRequest request) {
        if (body.skillDocuments() == null || body.restRoutesYaml() == null)
            throw new IllegalArgumentException("Missing configuration content");
        ManagedConfiguration configuration;
        try { configuration = new ManagedConfiguration(body.skillDocuments(), body.restRoutesYaml()); }
        catch (NullPointerException invalid) { throw new IllegalArgumentException("Invalid configuration content"); }
        return editing.save(session(request), ManagementController.principal(auth), body.tabId(), body.grantId(),
                body.expectedCandidateId(), configuration);
    }

    @PostMapping("/draft/validate")
    public ValidationResponse validate(@RequestBody Candidate body, Authentication auth,
            HttpServletRequest request) {
        var draft = editing.validate(session(request), ManagementController.principal(auth), body.tabId(),
                body.grantId(), body.expectedCandidateId());
        return new ValidationResponse(draft.draftId(), draft.candidateId(), draft.baseSnapshotId(),
                true, draft.validation());
    }

    @DeleteMapping("/draft")
    public ResponseEntity<Void> discard(@RequestBody(required = false) Capability body,
            Authentication auth, HttpServletRequest request) {
        editing.discard(session(request), ManagementController.principal(auth),
                body == null ? null : body.tabId(), body == null ? null : body.grantId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ManagementEditingService.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(ManagementEditingService.Conflict conflict) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("code", conflict.code(),
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

    private static jakarta.servlet.http.HttpSession session(HttpServletRequest request) {
        return request.getSession(false);
    }
}
