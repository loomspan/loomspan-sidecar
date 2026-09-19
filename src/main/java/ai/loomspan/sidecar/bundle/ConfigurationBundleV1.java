package ai.loomspan.sidecar.bundle;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Portable configuration-only ZIP contract. This parser never extracts or activates content. */
public final class ConfigurationBundleV1 {
    public static final long MAX_ZIP_BYTES = 100L * 1_048_576;
    public static final long MAX_EXPANDED_BYTES = 512L * 1_048_576;
    public static final int MAX_ENTRIES = 10_000;
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    public record Bundle(UUID sourceSnapshotId, String sidecarVersion, String frameworkVersion,
            ManagedConfiguration configuration) {}

    public static final class InvalidBundle extends IllegalArgumentException {
        public InvalidBundle(String reason) { super(reason); }
        public InvalidBundle(String reason, Throwable cause) { super(reason, cause); }
    }
    public static final class BundleTooLarge extends IllegalArgumentException {
        public BundleTooLarge() { super("Configuration exceeds format 1 bundle limits"); }
    }

    private ConfigurationBundleV1() {}

    /** The caller owns and deletes the returned file. No partial file is returned. */
    public static Path write(ConfigurationSnapshot snapshot) throws IOException {
        Path file = Files.createTempFile("sidecar-configuration-export-", ".zip");
        boolean complete = false;
        try {
            var inventory = new ArrayList<Map<String, Object>>();
            long expanded = 0;
            int count = snapshot.configuration().skillDocuments().size() + 2;
            if (count > MAX_ENTRIES) throw new BundleTooLarge();
            try (OutputStream output = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(output)) {
                String rest = snapshot.configuration().restRoutesYaml();
                byte[] restBytes = JSON.writeValueAsBytes(Map.of("restRoutesYaml", rest));
                expanded = add(zip, inventory, "rest.json", restBytes, null, expanded);
                int ordinal = 0;
                for (SkillDocument document : snapshot.configuration().skillDocuments()) {
                    String path = String.format(Locale.ROOT, "skills/%05d.yaml", ordinal++);
                    expanded = add(zip, inventory, path, document.yaml().getBytes(StandardCharsets.UTF_8),
                            document.sourceName(), expanded);
                }
                Map<String, Object> manifest = new LinkedHashMap<>();
                manifest.put("formatVersion", 1);
                manifest.put("sourceSnapshotId", snapshot.localId().toString());
                manifest.put("producer", Map.of("sidecarVersion", version("sidecarVersion"),
                        "frameworkVersion", version("frameworkVersion")));
                manifest.put("payloads", inventory);
                byte[] manifestBytes = JSON.writeValueAsBytes(manifest);
                expanded = checkedTotal(expanded, manifestBytes.length);
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifestBytes);
                zip.closeEntry();
            }
            if (Files.size(file) > MAX_ZIP_BYTES) throw new BundleTooLarge();
            complete = true;
            return file;
        } finally {
            if (!complete) Files.deleteIfExists(file);
        }
    }

    /** Stage compressed bytes under the v1 bound before ZIP central-directory validation. */
    public static Bundle read(InputStream input) {
        Path file = null;
        try {
            file = Files.createTempFile("sidecar-configuration-read-", ".zip");
            try (OutputStream output = Files.newOutputStream(file)) {
                byte[] chunk = new byte[8192];
                long total = 0;
                int n;
                while ((n = input.read(chunk)) != -1) {
                    total += n;
                    if (total > MAX_ZIP_BYTES) throw new BundleTooLarge();
                    output.write(chunk, 0, n);
                }
            }
            return read(file);
        } catch (IOException failure) {
            throw new InvalidBundle("Unreadable bundle", failure);
        } finally {
            if (file != null) try { Files.deleteIfExists(file); } catch (IOException ignored) { }
        }
    }

    public static Bundle read(Path file) {
        try {
            if (Files.size(file) > MAX_ZIP_BYTES) throw new BundleTooLarge();
            try (ZipFile zip = new ZipFile(file.toFile(), StandardCharsets.UTF_8)) {
                if (zip.size() > MAX_ENTRIES) throw new BundleTooLarge();
                Map<String, ZipEntry> entriesByName = new HashMap<>();
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !safePath(name) || entriesByName.putIfAbsent(name, entry) != null)
                        throw new InvalidBundle("Duplicate or unsafe ZIP entry");
                }
                ZipEntry manifestEntry = entriesByName.remove("manifest.json");
                if (manifestEntry == null) throw new InvalidBundle("Missing manifest.json");
                long[] expanded = {0};
                JsonNode manifest = json(readEntry(zip, manifestEntry, expanded));
                fields(manifest, Set.of("formatVersion", "sourceSnapshotId", "producer", "payloads"));
                JsonNode format = manifest.get("formatVersion");
                if (format == null || !format.isIntegralNumber()
                        || !format.bigIntegerValue().equals(java.math.BigInteger.ONE))
                    throw new InvalidBundle("Unsupported bundle format version");
                UUID source = uuid(text(manifest, "sourceSnapshotId"));
                JsonNode producer = manifest.get("producer");
                fields(producer, Set.of("sidecarVersion", "frameworkVersion"));
                String sidecar = text(producer, "sidecarVersion");
                String framework = text(producer, "frameworkVersion");
                if (sidecar.isBlank() || framework.isBlank())
                    throw new InvalidBundle("Invalid producer version");
                JsonNode payloads = manifest.get("payloads");
                if (payloads == null || !payloads.isArray() || payloads.size() != entriesByName.size())
                    throw new InvalidBundle("Incomplete payload inventory");
                Set<String> labels = new HashSet<>();
                Set<String> paths = new HashSet<>();
                Map<Integer, SkillDocument> documents = new HashMap<>();
                String rest = null;
                for (JsonNode item : payloads) {
                    if (item == null || !item.isObject()) throw new InvalidBundle("Invalid payload inventory");
                    String path = text(item, "path");
                    boolean skill = path.matches("skills/[0-9]{5}\\.yaml");
                    fields(item, skill ? Set.of("path", "sha256", "sourceLabel") : Set.of("path", "sha256"));
                    if (!paths.add(path) || !(skill || path.equals("rest.json")))
                        throw new InvalidBundle("Duplicate or unexpected payload path");
                    ZipEntry entry = entriesByName.remove(path);
                    if (entry == null) throw new InvalidBundle("Missing or checksum-mismatched payload");
                    byte[] bytes = readEntry(zip, entry, expanded);
                    if (!sha256(bytes).equals(text(item, "sha256")))
                        throw new InvalidBundle("Missing or checksum-mismatched payload");
                    if (skill) {
                        int ordinal = Integer.parseInt(path.substring(7, 12));
                        String label = text(item, "sourceLabel");
                        if (label.isBlank() || !labels.add(label)) throw new InvalidBundle("Duplicate or blank source label");
                        String yaml = utf8(bytes);
                        yamlObject(yaml);
                        documents.put(ordinal, new SkillDocument(label, yaml));
                    } else {
                        JsonNode restNode = json(bytes);
                        fields(restNode, Set.of("restRoutesYaml"));
                        rest = text(restNode, "restRoutesYaml");
                        JsonNode parsed = yamlObject(rest);
                        if (parsed.get("targets") == null || !parsed.get("targets").isObject()
                                || parsed.get("routes") == null || !parsed.get("routes").isObject())
                            throw new InvalidBundle("Invalid REST configuration structure");
                    }
                }
                if (rest == null || !entriesByName.isEmpty() || documents.size() != paths.size() - 1)
                    throw new InvalidBundle("Missing REST payload");
                var ordered = new ArrayList<SkillDocument>();
                for (int i = 0; i < documents.size(); i++) {
                    SkillDocument document = documents.get(i);
                    if (document == null) throw new InvalidBundle("Incomplete skill sequence");
                    ordered.add(document);
                }
                return new Bundle(source, sidecar, framework, new ManagedConfiguration(ordered, rest));
            }
        } catch (BundleTooLarge | InvalidBundle failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new InvalidBundle("Corrupt or invalid bundle", failure);
        }
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry, long[] expanded) throws IOException {
        if (entry.getSize() < 0) throw new InvalidBundle("Missing ZIP entry size");
        if (entry.getSize() > MAX_EXPANDED_BYTES) throw new BundleTooLarge();
        try (InputStream payload = zip.getInputStream(entry)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(entry.getSize(), 8192));
            byte[] chunk = new byte[8192];
            CRC32 crc = new CRC32();
            long used = 0;
            int n;
            while ((n = payload.read(chunk)) != -1) {
                expanded[0] = checkedTotal(expanded[0], n);
                if (used + n > entry.getSize()) throw new InvalidBundle("Corrupt ZIP entry");
                crc.update(chunk, 0, n);
                bytes.write(chunk, 0, n);
                used += n;
            }
            if (used != entry.getSize() || entry.getCrc() != crc.getValue())
                throw new InvalidBundle("Corrupt ZIP entry");
            return bytes.toByteArray();
        }
    }

    private static long add(ZipOutputStream zip, List<Map<String, Object>> inventory, String path,
            byte[] bytes, String label, long expanded) throws IOException {
        expanded = checkedTotal(expanded, bytes.length);
        zip.putNextEntry(new ZipEntry(path));
        zip.write(bytes);
        zip.closeEntry();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", path);
        item.put("sha256", sha256(bytes));
        if (label != null) item.put("sourceLabel", label);
        inventory.add(item);
        return expanded;
    }

    static long checkedTotal(long previous, long added) {
        if (added < 0 || previous > MAX_EXPANDED_BYTES - added) throw new BundleTooLarge();
        return previous + added;
    }
    private static boolean safePath(String path) {
        return path.equals("manifest.json") || path.equals("rest.json")
                || path.matches("skills/[0-9]{5}\\.yaml");
    }
    private static String version(String key) {
        try (InputStream input = ConfigurationBundleV1.class.getResourceAsStream("/bundle-producer.properties")) {
            if (input == null) throw new IllegalStateException("Missing bundle producer metadata");
            Properties properties = new Properties();
            properties.load(input);
            String value = properties.getProperty(key);
            if (value == null || value.isBlank() || value.contains("${"))
                throw new IllegalStateException("Invalid bundle producer metadata");
            return value;
        } catch (IOException failure) { throw new IllegalStateException("Unreadable bundle producer metadata", failure); }
    }
    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String utf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException failure) { throw new InvalidBundle("Payload is not UTF-8", failure); }
    }
    private static JsonNode json(byte[] bytes) {
        try { return JSON.readTree(utf8(bytes)); }
        catch (RuntimeException failure) { throw new InvalidBundle("Malformed JSON payload", failure); }
    }
    private static JsonNode yamlObject(String yaml) {
        try {
            JsonNode node = YAML.readTree(yaml);
            if (node == null || !node.isObject()) throw new InvalidBundle("YAML root must be an object");
            return node;
        } catch (RuntimeException failure) { throw new InvalidBundle("Invalid YAML payload", failure); }
    }
    private static void fields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || node.size() != expected.size()
                || !node.propertyNames().containsAll(expected))
            throw new InvalidBundle("Invalid JSON object fields");
    }
    private static String text(JsonNode object, String name) {
        JsonNode node = object.get(name);
        if (node == null || !node.isTextual()) throw new InvalidBundle("Invalid " + name);
        return node.asText();
    }
    private static UUID uuid(String value) {
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw new InvalidBundle("Invalid source snapshot identity");
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException failure) { throw new InvalidBundle("Invalid source snapshot identity"); }
    }
}
