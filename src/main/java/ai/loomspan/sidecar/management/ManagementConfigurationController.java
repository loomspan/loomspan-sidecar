package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV2;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.ServletException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/management/configuration")
public final class ManagementConfigurationController {
    public record Publish(UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseSnapshotId) {}
    public record Problem(String code, String error, UUID publishedId, UUID intendedId,
            String intendedStatus, String mutationFault) {}

    private final RuntimeConfigurationService runtime;
    private final ManagementEditingService editing;
    private final ManagementConfigurationImportService imports;
    private final ManagementConfigurationRollbackService rollbacks;

    public ManagementConfigurationController(RuntimeConfigurationService runtime, ManagementEditingService editing,
            ManagementConfigurationImportService imports, ManagementConfigurationRollbackService rollbacks) {
        this.runtime = runtime;
        this.editing = editing;
        this.imports = imports;
        this.rollbacks = rollbacks;
    }

    @GetMapping("/current")
    public RuntimeConfigurationService.Current current() { return runtime.current(); }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export() throws IOException {
        ConfigurationSnapshot captured;
        try { captured = runtime.publishedSnapshot(); }
        catch (IllegalStateException absent) {
            throw new ExportUnavailable();
        }
        Path bundle;
        bundle = ConfigurationBundleV2.write(captured);
        StreamingResponseBody body = output -> {
            try { Files.copy(bundle, output); }
            finally { Files.deleteIfExists(bundle); }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .contentLength(Files.size(bundle))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=sidecar-current-configuration.zip")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private static final class ExportUnavailable extends RuntimeException {}

    @ExceptionHandler(ExportUnavailable.class)
    ResponseEntity<Map<String, String>> exportUnavailable() {
        return ResponseEntity.status(503).body(Map.of("code", "export_unavailable",
                "error", "No runtime-published configuration is available for export"));
    }

    @ExceptionHandler(IOException.class)
    ResponseEntity<Map<String, String>> exportIoFailure() {
        return ResponseEntity.status(503).body(Map.of("code", "export_unavailable",
                "error", "Configuration export could not be prepared"));
    }

    @ExceptionHandler(ConfigurationBundleV2.BundleTooLarge.class)
    ResponseEntity<Map<String, String>> bundleTooLarge(HttpServletRequest request) {
        boolean imported = request.getRequestURI().contains("/import/");
        return ResponseEntity.status(413).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("code", imported ? "import_too_large" : "export_too_large",
                        "error", imported ? "Uploaded bundle exceeds format 2 limits"
                                : "Runtime configuration exceeds format 2 bundle limits"));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> multipartTooLarge() {
        return ResponseEntity.status(413).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("code", "import_too_large", "error", "Uploaded bundle exceeds format 2 limits"));
    }

    @GetMapping("/history")
    public List<ConfigurationSnapshot> history() { return runtime.history(); }

    @GetMapping("/history/{id}")
    public ResponseEntity<?> history(@PathVariable("id") UUID id) {
        var snapshot = runtime.history(id);
        return snapshot == null ? ResponseEntity.status(404).body(Map.of("code", "history_not_found",
                "error", "Snapshot is unknown or no longer retained")) : ResponseEntity.ok(snapshot);
    }

    public record Load(UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseSnapshotId) {}

    @PostMapping("/rollback/{id}/review")
    public ManagementConfigurationRollbackService.Review rollbackReview(@PathVariable("id") UUID id,
            HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return rollbacks.review(id);
    }

    @PostMapping("/rollback/{id}/load")
    public ManagementEditingService.Draft rollbackLoad(@PathVariable("id") UUID id, @RequestBody Load body,
            Authentication auth, HttpServletRequest request) {
        return rollbacks.load(request.getSession(false), ManagementController.principal(auth), id,
                body.editingSessionId(), body.generation(), body.draftId(), body.revision(), body.baseSnapshotId());
    }

    @ExceptionHandler(ManagementConfigurationRollbackService.SourceMissing.class)
    ResponseEntity<Map<String, String>> rollbackMissing() {
        return ResponseEntity.status(404).body(Map.of("code", "source_not_found", "error", "Snapshot is not retained"));
    }

    @PostMapping("/publish")
    public ConfigurationSnapshot publish(@RequestBody Publish body, Authentication auth, HttpServletRequest request) {
        return editing.publish(request.getSession(false), ManagementController.principal(auth),
                body.editingSessionId(), body.generation(), body.draftId(), body.revision(), body.baseSnapshotId());
    }

    @PostMapping(value = "/import/review", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ManagementConfigurationImportService.Review review(@RequestPart("bundle") MultipartFile bundle,
            HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        requireParts(request, "bundle");
        return imports.review(bundle);
    }

    @PostMapping(value = "/import/load", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ManagementEditingService.Draft importLoad(@RequestPart("bundle") MultipartFile bundle,
            @RequestParam("editingSessionId") UUID editingSessionId, @RequestParam("generation") UUID generation,
            @RequestParam("draftId") UUID draftId, @RequestParam("revision") long revision,
            @RequestParam("baseSnapshotId") UUID baseId, Authentication auth, HttpServletRequest request)
            throws IOException, ServletException {
        requireParts(request, "bundle", "editingSessionId", "generation", "draftId", "revision", "baseSnapshotId");
        return imports.load(request.getSession(false), ManagementController.principal(auth), bundle,
                editingSessionId, generation, draftId, revision, baseId);
    }

    private static void requireParts(HttpServletRequest request, String... allowed) throws IOException, ServletException {
        var names = java.util.Set.of(allowed);
        var seen = new java.util.HashSet<String>();
        for (var part : request.getParts()) {
            if (!names.contains(part.getName()) || !seen.add(part.getName())
                    || ("bundle".equals(part.getName()) != (part.getSubmittedFileName() != null)))
                throw new ManagementConfigurationImportService.InvalidUpload();
        }
        if (!seen.contains("bundle")) throw new ManagementConfigurationImportService.InvalidUpload();
    }

    @ExceptionHandler(ManagementConfigurationImportService.InvalidUpload.class)
    ResponseEntity<Map<String, String>> invalidUpload() {
        return ResponseEntity.badRequest().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("code", "invalid_upload", "error", "Exactly one valid bundle upload is required"));
    }

    @ExceptionHandler(ConfigurationBundleV2.InvalidBundle.class)
    ResponseEntity<Map<String, String>> invalidBundle() {
        return ResponseEntity.badRequest().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("code", "invalid_bundle", "error", "Bundle format or content is invalid"));
    }

    @ExceptionHandler(ManagementEditingService.Conflict.class)
    ResponseEntity<Map<String, String>> conflict(ManagementEditingService.Conflict conflict) {
        return ResponseEntity.status(409).body(Map.of("code", conflict.code(),
                "error", "Editing grant, candidate, validation, or runtime base is no longer current"));
    }

    @ExceptionHandler(RuntimeConfigurationService.PublicationFailure.class)
    ResponseEntity<Problem> publicationFailure(RuntimeConfigurationService.PublicationFailure failure) {
        var state = runtime.inspect();
        return ResponseEntity.status(503).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new Problem(failure.code(), failure.getMessage(),
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
