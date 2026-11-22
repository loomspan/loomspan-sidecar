package ai.loomspan.sidecar.configuration;

import ai.loomspan.sidecar.storage.ConfigurationValidationIssue;
import ai.loomspan.sidecar.storage.EncryptedCredential;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.schema.CoreSchema;
import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads authored markers after Framework validation. Framework owns substitution and binding. */
public final class EffectiveExecutionConfiguration {
    private static final String EXECUTION_SOURCE = "<configuration>";
    private static final LoadSettings YAML = LoadSettings.builder().setSchema(new CoreSchema())
            .setAllowNonScalarKeys(true).setMaxAliasesForCollections(Integer.MAX_VALUE)
            .setCodePointLimit(Integer.MAX_VALUE).build();
    private static final JsonMapper PATH_KEYS = JsonMapper.builder()
            .disable(JsonWriteFeature.WRITE_HEX_UPPER_CASE, JsonWriteFeature.ESCAPE_FORWARD_SLASHES).build();

    private final ProviderCredentialCipher cipher;

    public EffectiveExecutionConfiguration(ProviderCredentialCipher cipher) {
        this.cipher = cipher;
    }

    record Location(String source, String path) {
        ConfigurationValidationIssue error(String message) {
            return new ConfigurationValidationIssue(ConfigurationValidationIssue.Severity.ERROR,
                    source, null, path, message);
        }
    }

    static final class UnavailableValues extends IllegalArgumentException {
        private final List<ConfigurationValidationIssue> issues;

        UnavailableValues(List<ConfigurationValidationIssue> issues) {
            super("Managed variable values are unavailable");
            this.issues = List.copyOf(issues);
        }

        List<ConfigurationValidationIssue> issues() { return issues; }
    }

    /** Preparation state is short lived and must never be serialized. */
    public record Candidate(String yaml, Map<String, String> values, List<EncryptedCredential> retained) {
        public Candidate {
            values = Map.copyOf(values);
            retained = List.copyOf(retained);
        }

        @Override public String toString() { return "Effective execution candidate [redacted]"; }
    }

    public Candidate assemble(ManagedConfiguration authored, List<EncryptedCredential> managed) {
        var references = new LinkedHashMap<String, List<Location>>();
        scan(authored, references, null);
        var records = new LinkedHashMap<String, EncryptedCredential>();
        for (var credential : managed) {
            if (records.put(credential.identifier(), credential) != null)
                throw new IllegalArgumentException("Duplicate managed value identifier");
        }
        var issues = new ArrayList<ConfigurationValidationIssue>();
        var values = new LinkedHashMap<String, String>();
        references.forEach((name, locations) -> {
            var record = records.get(name);
            String value = record == null ? null : cipher.decrypt(name, record.version(), record.ciphertext());
            if (value == null || EncryptedCredential.blank(value)) {
                String message = (value == null ? "missing-value" : "blank-value") + " for variable " + name;
                locations.forEach(location -> issues.add(location.error(message)));
            } else {
                values.put(name, value);
            }
        });
        if (!issues.isEmpty()) throw new UnavailableValues(issues);
        return new Candidate(authored.executionConfigurationYaml(), values, managed);
    }

    /** Accept only the fixed Framework diagnostic with key origins from these documents. */
    static List<ConfigurationValidationIssue> collision(ManagedConfiguration authored, String diagnostic) {
        String prefix = "Failed to prepare skill generation: key-collision at [";
        if (diagnostic == null || !diagnostic.startsWith(prefix) || !diagnostic.endsWith("]")) return List.of();
        String pair = diagnostic.substring(prefix.length(), diagnostic.length() - 1);
        var origins = new LinkedHashSet<Location>();
        scan(authored, new LinkedHashMap<>(), origins);
        var known = new LinkedHashMap<String, Location>();
        origins.forEach(location -> known.put(location.toString(), location));
        for (var origin : known.entrySet()) {
            String first = origin.getKey() + ", ";
            if (pair.startsWith(first)) {
                Location second = known.get(pair.substring(first.length()));
                if (second != null) return List.of(origin.getValue().error("key-collision"),
                        second.error("key-collision"));
            }
        }
        return List.of();
    }

    public void authenticate(List<EncryptedCredential> credentials) {
        for (var credential : credentials)
            cipher.decrypt(credential.identifier(), credential.version(), credential.ciphertext());
    }

    private static void scan(ManagedConfiguration authored, Map<String, List<Location>> references,
            Set<Location> keyOrigins) {
        var yaml = new Compose(YAML);
        Node execution = yaml.composeString(authored.executionConfigurationYaml()).orElse(null);
        Node loomspan = field(execution, "loomspan");
        for (String section : List.of("connections", "executor-config")) {
            if (!(field(loomspan, section) instanceof MappingNode items)) continue;
            for (int i = 0; i < items.getValue().size(); i++) {
                var item = items.getValue().get(i);
                walk(item.getValueNode(), EXECUTION_SOURCE, valuePath("loomspan." + section, item.getKeyNode(), i),
                        references, keyOrigins);
            }
        }
        for (var document : authored.skillDocuments()) {
            Node root = yaml.composeString(document.yaml()).orElse(null);
            if (field(root, "executor") != null && field(root, "config") instanceof MappingNode config)
                walk(config, document.sourceName(), "config", references, keyOrigins);
        }
    }

    private static void walk(Node node, String source, String path, Map<String, List<Location>> references,
            Set<Location> keyOrigins) {
        // The authored validator has already checked names, cycles and expanded document size.
        if (node instanceof MappingNode map) {
            var entries = map.getValue();
            if (entries.size() == 1 && "$var".equals(string(entries.getFirst().getKeyNode()))) {
                String name = string(entries.getFirst().getValueNode());
                references.computeIfAbsent(name, ignored -> new ArrayList<>()).add(new Location(source, path));
                return;
            }
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                Node key = entry.getKeyNode();
                String keyPath = path + "{key#" + i + "}";
                String nextPath = valuePath(path, key, i);
                if (keyOrigins != null) keyOrigins.add(new Location(source, string(key) == null ? keyPath : nextPath));
                walk(key, source, keyPath, references, keyOrigins);
                walk(entry.getValueNode(), source, nextPath, references, keyOrigins);
            }
        } else if (node instanceof SequenceNode sequence) {
            for (int i = 0; i < sequence.getValue().size(); i++)
                walk(sequence.getValue().get(i), source, path + "[" + i + "]", references, keyOrigins);
        }
    }

    private static Node field(Node node, String name) {
        if (node instanceof MappingNode map) {
            for (var entry : map.getValue())
                if (name.equals(string(entry.getKeyNode()))) return entry.getValueNode();
        }
        return null;
    }

    private static String string(Node node) {
        return node instanceof ScalarNode scalar && Tag.STR.equals(scalar.getTag()) ? scalar.getValue() : null;
    }

    private static String valuePath(String path, Node key, int index) {
        String name = string(key);
        if (name == null) return path + "{value#" + index + "}";
        if (name.matches("[A-Za-z_][A-Za-z0-9_-]*")) return path + "." + name;
        return path + "[" + PATH_KEYS.writeValueAsString(name) + "]";
    }
}
