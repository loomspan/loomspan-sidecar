package ai.loomspan.sidecar.bundle;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigurationBundleV4Test {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void rejectsSupersededFormatFixture() throws Exception {
        byte[] bytes = execution("loomspan: {executor-config: {default: {http: {}}}}\n");
        rejects(zip(Map.of("execution.json", bytes, "manifest.json", manifest(3, List.of(item("execution.json", bytes, null))))));
    }

    @Test void roundTripsExactAuthoredConfigurationAndEmptyRuntime() throws Exception {
        var configuration = new ManagedConfiguration(List.of(
                new SkillDocument("../source α.yaml", "name: example\n# secret-literal ${UNRESOLVED}\n"),
                new SkillDocument("other\\label.yml", "name: second\n")), ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION);
        var snapshot = snapshot(configuration);
        Path path = ConfigurationBundleV4.write(snapshot);
        try {
            var bundle = ConfigurationBundleV4.read(path);
            assertThat(bundle.sourceSnapshotId()).isEqualTo(snapshot.localId());
            assertThat(bundle.configuration()).isEqualTo(configuration);
            assertThat(bundle.sidecarVersion()).isNotBlank();
            assertThat(bundle.frameworkVersion()).isNotBlank();
            try (var zip = new java.util.zip.ZipFile(path.toFile())) {
                assertThat(zip.getEntry("../source α.yaml")).isNull();
                assertThat(zip.getEntry("skills/00000.yaml")).isNotNull();
                assertThat(zip.getEntry("rest.json")).isNull();
            }
        } finally { Files.deleteIfExists(path); }

        Path empty = ConfigurationBundleV4.write(snapshot(new ManagedConfiguration(List.of(), ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION)));
        try {
            assertThat(ConfigurationBundleV4.read(empty).configuration().skillDocuments()).isEmpty();
        } finally { Files.deleteIfExists(empty); }
    }

    @Test void generatedArchivePathsUseAsciiDigitsAcrossLocales() throws Exception {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"));
            Path file = ConfigurationBundleV4.write(snapshot(new ManagedConfiguration(
                    List.of(new SkillDocument("source", "name: example\n")), ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION)));
            try {
                assertThat(ConfigurationBundleV4.read(file).configuration().skillDocuments()).hasSize(1);
                try (var zip = new java.util.zip.ZipFile(file.toFile())) {
                    assertThat(zip.getEntry("skills/00000.yaml")).isNotNull();
                }
            } finally { Files.deleteIfExists(file); }
        } finally { java.util.Locale.setDefault(original); }
    }

    @Test void rejectsUnsupportedIncompleteAmbiguousUnsafeAndCorruptBundles() throws Exception {
        byte[] yaml = "name: example\n".getBytes(StandardCharsets.UTF_8);
        var payloads = new ArrayList<Map<String, Object>>();
        byte[] execution = execution("loomspan: {executor-config: {default: {http: {}}}}\n");
        payloads.add(item("execution.json", execution, null));
        payloads.add(item("skills/00000.yaml", yaml, "source.yaml"));
        var content = new LinkedHashMap<String, byte[]>();
        content.put("execution.json", execution);
        content.put("skills/00000.yaml", yaml);
        content.put("manifest.json", manifest(4, payloads));
        assertThat(ConfigurationBundleV4.read(new ByteArrayInputStream(zip(content))).configuration().skillDocuments()).hasSize(1);

        content.put("manifest.json", manifest(3, payloads));
        rejects(zip(content));
        content.put("manifest.json", manifest(4, payloads));
        content.remove("execution.json");
        rejects(zip(content));
        content.put("execution.json", execution);
        content.put("skills/00000.yaml", "name: changed\n".getBytes(StandardCharsets.UTF_8));
        rejects(zip(content));
        content.put("skills/00000.yaml", yaml);
        content.put("../outside", yaml);
        rejects(zip(content));
        content.remove("../outside");
        content.put("skills/00001.yaml", yaml);
        rejects(zip(content));
        content.remove("skills/00001.yaml");
        payloads.add(item("skills/00000.yaml", yaml, "source.yaml"));
        content.put("manifest.json", manifest(4, payloads));
        rejects(zip(content));
        payloads.removeLast();
        content.put("skills/00001.yaml", yaml);
        payloads.add(item("skills/00001.yaml", yaml, "source.yaml"));
        content.put("manifest.json", manifest(4, payloads));
        rejects(zip(content));
        payloads.removeLast();
        content.remove("skills/00001.yaml");
        byte[] corrupt = zip(content);
        rejects(java.util.Arrays.copyOf(corrupt, corrupt.length - 22));
    }

    @Test void rejectsInvalidStructuresAndDuplicateZipEntries() throws Exception {
        byte[] invalidRest = execution("[not, a, mapping]");
        var payloads = List.of(item("execution.json", invalidRest, null));
        rejects(zip(Map.of("execution.json", invalidRest, "manifest.json", manifest(4, payloads))));
        byte[] malformedYaml = "[not, a, mapping]".getBytes(StandardCharsets.UTF_8);
        var badPayloads = List.of(item("execution.json", execution("loomspan: {executor-config: {default: {http: {}}}}\n"), null),
                item("skills/00000.yaml", malformedYaml, "x"));
        rejects(zip(Map.of("execution.json", execution("loomspan: {executor-config: {default: {http: {}}}}\n"),
                "skills/00000.yaml", malformedYaml, "manifest.json", manifest(4, badPayloads))));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("execution.json")); zip.write(invalidRest); zip.closeEntry();
            // ZipOutputStream itself refuses duplicate names; craft an unsafe sibling instead.
            zip.putNextEntry(new ZipEntry("skills/../execution.json")); zip.write(invalidRest); zip.closeEntry();
        }
        rejects(bytes.toByteArray());
        byte[] trailing = (new String(execution("loomspan: {executor-config: {default: {http: {}}}}\n"), StandardCharsets.UTF_8)
                + " {} ").getBytes(StandardCharsets.UTF_8);
        rejects(zip(Map.of("execution.json", trailing, "manifest.json",
                manifest(4, List.of(item("execution.json", trailing, null))))));
        byte[] multipleYaml = execution("loomspan: {executor-config: {default: {http: {}}}}\n---\nloomspan: {executor-config: {default: {http: {}}}}\n");
        rejects(zip(Map.of("execution.json", multipleYaml, "manifest.json",
                manifest(4, List.of(item("execution.json", multipleYaml, null))))));
    }

    @Test void representativeLargeConfigurationRemainsWithinReaderAndWriterLimits() throws Exception {
        String repeated = "# large authored content ${PLACEHOLDER} secret-literal\n".repeat(120_000);
        var configuration = new ManagedConfiguration(List.of(new SkillDocument("large.yaml", "name: large\n" + repeated)), ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION);
        Path path = ConfigurationBundleV4.write(snapshot(configuration));
        try { assertThat(ConfigurationBundleV4.read(path).configuration()).isEqualTo(configuration); }
        finally { Files.deleteIfExists(path); }
    }

    @Test void inclusiveExpandedAndEntryCountBoundaries() throws Exception {
        assertThat(ConfigurationBundleV4.checkedTotal(ConfigurationBundleV4.MAX_EXPANDED_BYTES - 1, 1))
                .isEqualTo(ConfigurationBundleV4.MAX_EXPANDED_BYTES);
        assertThatThrownBy(() -> ConfigurationBundleV4.checkedTotal(ConfigurationBundleV4.MAX_EXPANDED_BYTES, 1))
                .isInstanceOf(ConfigurationBundleV4.BundleTooLarge.class);
        var allowed = new ArrayList<SkillDocument>();
        for (int i = 0; i < ConfigurationBundleV4.MAX_ENTRIES - 2; i++)
            allowed.add(new SkillDocument("source-" + i, "name: example\n"));
        Path path = ConfigurationBundleV4.write(snapshot(new ManagedConfiguration(allowed, ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION)));
        try { assertThat(ConfigurationBundleV4.read(path).configuration().skillDocuments()).hasSize(allowed.size()); }
        finally { Files.deleteIfExists(path); }
        allowed.add(new SkillDocument("one-too-many", "name: example\n"));
        assertThatThrownBy(() -> ConfigurationBundleV4.write(snapshot(new ManagedConfiguration(allowed, ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION))))
                .isInstanceOf(ConfigurationBundleV4.BundleTooLarge.class);
    }

    @Test void compressedInputLimitCountsBytesActuallyRead() {
        assertThatThrownBy(() -> ConfigurationBundleV4.read(zeroBytes(ConfigurationBundleV4.MAX_ZIP_BYTES)))
                .isInstanceOf(ConfigurationBundleV4.InvalidBundle.class);
        assertThatThrownBy(() -> ConfigurationBundleV4.read(zeroBytes(ConfigurationBundleV4.MAX_ZIP_BYTES + 1)))
                .isInstanceOf(ConfigurationBundleV4.BundleTooLarge.class);
    }

    private static java.io.InputStream zeroBytes(long size) {
        return new java.io.InputStream() {
            long remaining = size;
            @Override public int read() { if (remaining-- <= 0) return -1; return 0; }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (remaining == 0) return -1;
                int count = (int) Math.min(length, remaining);
                java.util.Arrays.fill(bytes, offset, offset + count, (byte) 0);
                remaining -= count;
                return count;
            }
        };
    }

    @Test void rejectsMisleadingCentralDirectorySizeAndDuplicateEntryNames() throws Exception {
        byte[] rest = execution("loomspan: {executor-config: {default: {http: {}}}}\n");
        byte[] archive = zip(Map.of("execution.json", rest,
                "manifest.json", manifest(4, List.of(item("execution.json", rest, null)))));
        byte[] exaggerated = archive.clone();
        for (int i = 0; i < exaggerated.length - 28; i++) {
            if (exaggerated[i] == 0x50 && exaggerated[i + 1] == 0x4b
                    && exaggerated[i + 2] == 1 && exaggerated[i + 3] == 2) {
                // An untrusted size near the format maximum must not reserve that much memory.
                exaggerated[i + 24] = (byte) 0xff;
                exaggerated[i + 25] = (byte) 0xff;
                exaggerated[i + 26] = (byte) 0xff;
                exaggerated[i + 27] = (byte) 0x1f;
                break;
            }
        }
        rejects(exaggerated);
        for (int i = 0; i < archive.length - 28; i++) {
            if (archive[i] == 0x50 && archive[i + 1] == 0x4b && archive[i + 2] == 1 && archive[i + 3] == 2) {
                archive[i + 24] = 0; archive[i + 25] = 0; archive[i + 26] = 0; archive[i + 27] = 0;
                break;
            }
        }
        rejects(archive);

        byte[] yaml = "name: example\n".getBytes(StandardCharsets.UTF_8);
        var payloads = List.of(item("execution.json", rest, null), item("skills/00000.yaml", yaml, "a"),
                item("skills/00001.yaml", yaml, "b"));
        archive = zip(Map.of("execution.json", rest, "skills/00000.yaml", yaml,
                "skills/00001.yaml", yaml, "manifest.json", manifest(4, payloads)));
        byte[] oldName = "skills/00001.yaml".getBytes(StandardCharsets.UTF_8);
        byte[] newName = "skills/00000.yaml".getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i <= archive.length - oldName.length; i++) {
            boolean match = true;
            for (int j = 0; j < oldName.length; j++) if (archive[i + j] != oldName[j]) { match = false; break; }
            if (match) System.arraycopy(newName, 0, archive, i, oldName.length);
        }
        rejects(archive);
    }

    @Test void rejectsEncryptedArchiveFlag() throws Exception {
        byte[] rest = execution("loomspan: {executor-config: {default: {http: {}}}}\n");
        byte[] archive = zip(Map.of("execution.json", rest,
                "manifest.json", manifest(4, List.of(item("execution.json", rest, null)))));
        for (int i = 0; i < archive.length - 10; i++) {
            if (archive[i] == 0x50 && archive[i + 1] == 0x4b && archive[i + 2] == 3 && archive[i + 3] == 4)
                archive[i + 6] |= 1;
            if (archive[i] == 0x50 && archive[i + 1] == 0x4b && archive[i + 2] == 1 && archive[i + 3] == 2)
                archive[i + 8] |= 1;
        }
        rejects(archive);
    }

    @Test void rejectsManifestMetadataOutsideDocumentedSchema() throws Exception {
        byte[] rest = execution("loomspan: {executor-config: {default: {http: {}}}}\n");
        var payloads = List.of(item("execution.json", rest, null));
        var manifest = new LinkedHashMap<String, Object>();
        manifest.put("formatVersion", 4);
        manifest.put("sourceSnapshotId", UUID.randomUUID().toString());
        manifest.put("producer", Map.of("sidecarVersion", "", "frameworkVersion", "test"));
        manifest.put("payloads", payloads);
        rejects(zip(Map.of("execution.json", rest, "manifest.json", JSON.writeValueAsBytes(manifest))));
        manifest.put("producer", Map.of("sidecarVersion", "test", "frameworkVersion", "test"));
        manifest.put("sourceSnapshotId", "not-a-uuid");
        rejects(zip(Map.of("execution.json", rest, "manifest.json", JSON.writeValueAsBytes(manifest))));
        manifest.put("sourceSnapshotId", "1-1-1-1-1");
        rejects(zip(Map.of("execution.json", rest, "manifest.json", JSON.writeValueAsBytes(manifest))));
    }

    private static byte[] execution(String yaml) {
        return JSON.writeValueAsBytes(Map.of("executionConfigurationYaml", yaml, "credentialIdentifiers", List.of()));
    }
    private static ConfigurationSnapshot snapshot(ManagedConfiguration configuration) {
        return new ConfigurationSnapshot(UUID.randomUUID(), null, 1, configuration, SnapshotStatus.PUBLISHED);
    }
    private static void rejects(byte[] bytes) {
        assertThatThrownBy(() -> ConfigurationBundleV4.read(new ByteArrayInputStream(bytes)))
                .isInstanceOf(ConfigurationBundleV4.InvalidBundle.class);
    }
    private static Map<String, Object> item(String path, byte[] content, String label) throws Exception {
        var item = new LinkedHashMap<String, Object>();
        item.put("path", path);
        item.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)));
        if (label != null) item.put("sourceLabel", label);
        return item;
    }
    private static byte[] manifest(int version, List<Map<String, Object>> payloads) throws Exception {
        return JSON.writeValueAsBytes(Map.of("formatVersion", version,
                "sourceSnapshotId", UUID.randomUUID().toString(),
                "producer", Map.of("sidecarVersion", "test", "frameworkVersion", "test"), "payloads", payloads));
    }
    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (var item : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(item.getKey()));
                zip.write(item.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
