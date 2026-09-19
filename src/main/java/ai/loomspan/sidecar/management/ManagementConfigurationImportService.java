package ai.loomspan.sidecar.management;

import ai.loomspan.api.SkillReloader;
import ai.loomspan.sidecar.config.SidecarManagementProperties;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV1;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Service
public final class ManagementConfigurationImportService {
    private static final String REVIEW = ManagementConfigurationImportService.class.getName() + ".review";
    public record Observation(UUID publishedId, UUID grantId, String leaseOwner) {}
    public record Review(UUID reviewId, UUID sourceSnapshotId, String sidecarVersion, String frameworkVersion,
            int skillDocuments, int validatedSkills, int routes, ConfigurationValidationResult validation,
            Observation observation) {}
    private record Reviewed(UUID id, String digest, Observation observation) {}
    public static final class Conflict extends RuntimeException {
        private final String code;
        private final Observation observation;
        public Conflict(String code) { this(code, null); }
        public Conflict(String code, Observation observation) {
            this.code = code; this.observation = observation;
        }
        public String code() { return code; }
        public Observation observation() { return observation; }
    }
    public static final class InvalidUpload extends RuntimeException {}

    private final RuntimeConfigurationService runtime;
    private final ManagementEditingState editing;
    private final ManagementIdentityService identity;
    private final SkillReloader reloader;
    private final Clock clock;
    private final SidecarManagementProperties settings;

    public ManagementConfigurationImportService(RuntimeConfigurationService runtime, ManagementEditingState editing,
            ManagementIdentityService identity, SkillReloader reloader, Clock clock,
            SidecarManagementProperties settings) {
        this.runtime = runtime; this.editing = editing; this.identity = identity; this.reloader = reloader;
        this.clock = clock; this.settings = settings;
    }

    public Review review(HttpSession session, MultipartFile file) {
        if (session == null) throw new Conflict("session_conflict");
        synchronized (session) { session.removeAttribute(REVIEW); }
        ConfigurationBundleV1.Bundle bundle;
        String digest;
        try (Upload upload = read(file)) {
            bundle = ConfigurationBundleV1.read(upload.path);
            digest = upload.digest;
        } catch (IOException failure) { throw new InvalidUpload(); }
        var validation = runtime.validate(bundle.configuration());
        int skills = reloader.validate(bundle.configuration().skillDocuments()).skills().size();
        int routes = new YAMLMapper().readTree(bundle.configuration().restRoutesYaml()).get("routes").size();
        Observation observation = observation();
        UUID id = validation.successful() ? UUID.randomUUID() : null;
        if (id != null) synchronized (session) { session.setAttribute(REVIEW, new Reviewed(id, digest, observation)); }
        return new Review(id, bundle.sourceSnapshotId(), bundle.sidecarVersion(), bundle.frameworkVersion(),
                bundle.configuration().skillDocuments().size(), skills, routes, validation, observation);
    }

    public ConfigurationSnapshot confirm(HttpSession session, ManagementUserDetailsService.Principal user,
            MultipartFile file, UUID reviewId, UUID expectedPublishedId, UUID expectedGrantId) {
        if (session == null || reviewId == null || expectedPublishedId == null) throw new Conflict("review_required");
        admission(session, user);
        Reviewed reviewed;
        synchronized (session) { reviewed = (Reviewed) session.getAttribute(REVIEW); }
        if (reviewed == null || !reviewed.id.equals(reviewId)) throw new Conflict("review_required");
        ConfigurationBundleV1.Bundle bundle;
        try (Upload upload = read(file)) {
            if (!MessageDigest.isEqual(reviewed.digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    upload.digest.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
                throw new Conflict("content_changed");
            bundle = ConfigurationBundleV1.read(upload.path);
        } catch (IOException failure) { throw new InvalidUpload(); }
        if (!runtime.validate(bundle.configuration()).successful()) throw new Conflict("validation_changed");
        synchronized (session) {
            if (!reviewed.equals(session.getAttribute(REVIEW))) throw new Conflict("review_required");
        }
        try {
            return runtime.importConfiguration(bundle.configuration(), bundle.sourceSnapshotId(),
                    expectedPublishedId, expectedGrantId, clock::millis, () -> {
                        admission(session, user);
                        synchronized (session) {
                            if (!reviewed.equals(session.getAttribute(REVIEW))) throw new Conflict("review_required");
                            if (!java.util.Objects.equals(reviewed.observation.publishedId(), expectedPublishedId)
                                    || !java.util.Objects.equals(reviewed.observation.grantId(), expectedGrantId))
                                throw new Conflict("confirmation_stale");
                            session.removeAttribute(REVIEW);
                        }
                    });
        } catch (RuntimeConfigurationService.ImportConflict conflict) {
            throw refresh(session, reviewed);
        } catch (Conflict conflict) {
            if (!"confirmation_stale".equals(conflict.code())) throw conflict;
            throw refresh(session, reviewed);
        }
    }

    private Conflict refresh(HttpSession session, Reviewed reviewed) {
        Observation current = observation();
        try { synchronized (session) {
            Object active = session.getAttribute(REVIEW);
            if (active != null && !active.equals(reviewed)) return new Conflict("review_required", current);
            session.setAttribute(REVIEW, new Reviewed(reviewed.id, reviewed.digest, current));
        }} catch (IllegalStateException invalidated) { return new Conflict("session_conflict"); }
        return new Conflict("confirmation_stale", current);
    }

    private void admission(HttpSession session, ManagementUserDetailsService.Principal user) {
        try {
            session.getCreationTime();
            Long activity = (Long) session.getAttribute(ManagementSessionGuard.ACTIVITY);
            if (activity == null || clock.millis() - activity >= settings.getSessionIdleTimeout().toMillis())
                throw new Conflict("session_conflict");
        } catch (IllegalStateException expired) { throw new Conflict("session_conflict"); }
        var account = identity.account(user.id());
        if (account == null || !account.active() || account.version() != user.version()
                || !account.role().equals(user.role()) || "viewer".equals(user.role()))
            throw new Conflict("account_conflict");
    }

    public Observation observation() {
        return runtime.withEditingState(false, base -> {
            synchronized (editing) {
                var lease = editing.lease;
                if (lease == null || clock.millis() >= lease.expiresAt) return new Observation(base.localId(), null, null);
                var draft = editing.drafts.get(lease.sessionId);
                var owner = draft == null ? null : identity.account(draft.accountId);
                return new Observation(base.localId(), lease.grantId, owner == null ? null : owner.email());
            }
        });
    }

    private static Upload read(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new InvalidUpload();
        Path path = Files.createTempFile("sidecar-configuration-import-", ".zip");
        boolean complete = false;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long total = 0;
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(path)) {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = input.read(chunk)) != -1) {
                    total += n;
                    if (total > ConfigurationBundleV1.MAX_ZIP_BYTES) throw new ConfigurationBundleV1.BundleTooLarge();
                    digest.update(chunk, 0, n);
                    output.write(chunk, 0, n);
                }
            }
            complete = true;
            return new Upload(path, HexFormat.of().formatHex(digest.digest()));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        finally { if (!complete) Files.deleteIfExists(path); }
    }

    private record Upload(Path path, String digest) implements AutoCloseable {
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }
}
