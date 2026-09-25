package ai.loomspan.sidecar.management;

import ai.loomspan.sidecar.bundle.ConfigurationBundleV1;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.storage.ConfigurationValidationResult;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public final class ManagementConfigurationImportService {
    public record Review(UUID sourceSnapshotId, String sidecarVersion, String frameworkVersion,
            int skillDocuments, ConfigurationValidationResult validation) {}
    public static final class InvalidUpload extends RuntimeException {}

    private final RuntimeConfigurationService runtime;
    private final ManagementEditingService editing;

    public ManagementConfigurationImportService(RuntimeConfigurationService runtime, ManagementEditingService editing) {
        this.runtime = runtime; this.editing = editing;
    }

    public Review review(MultipartFile file) {
        var bundle = parse(file);
        return new Review(bundle.sourceSnapshotId(), bundle.sidecarVersion(), bundle.frameworkVersion(),
                bundle.configuration().skillDocuments().size(), runtime.validate(bundle.configuration()));
    }

    public ManagementEditingService.Draft load(HttpSession session, ManagementUserDetailsService.Principal user,
            MultipartFile file, UUID editingSessionId, UUID generation, UUID draftId, long revision, UUID baseId) {
        var bundle = parse(file);
        return editing.load(session, user, editingSessionId, generation, draftId, revision, baseId,
                bundle.configuration(), bundle.sourceSnapshotId());
    }

    private static ConfigurationBundleV1.Bundle parse(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new InvalidUpload();
        Path path = null;
        try {
            path = Files.createTempFile("sidecar-configuration-import-", ".zip");
            long total = 0;
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(path)) {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = input.read(chunk)) != -1) {
                    total += n;
                    if (total > ConfigurationBundleV1.MAX_ZIP_BYTES) throw new ConfigurationBundleV1.BundleTooLarge();
                    output.write(chunk, 0, n);
                }
            }
            return ConfigurationBundleV1.read(path);
        } catch (IOException failure) { throw new InvalidUpload(); }
        finally {
            if (path != null) try { Files.deleteIfExists(path); } catch (IOException ignored) {}
        }
    }
}
