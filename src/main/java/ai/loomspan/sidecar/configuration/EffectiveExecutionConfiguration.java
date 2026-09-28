package ai.loomspan.sidecar.configuration;

import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.EncryptedCredential;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Builds one reference-only framework candidate from complete database content. */
public final class EffectiveExecutionConfiguration {
    private static final YAMLMapper YAML = YAMLMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private static final YAMLMapper CREDENTIAL_SCAN = YAMLMapper.builder().build();
    private static final java.util.Set<String> DIRECT_CREDENTIAL_FIELDS = java.util.Set.of(
            "api-key", "headers", "credentials-json", "credentials-uri", "credentials-ref");
    private final ProviderCredentialCipher cipher;

    public EffectiveExecutionConfiguration(ProviderCredentialCipher cipher) {
        this.cipher = cipher;
    }

    /** Secret values are process-local and never serialized. */
    public static final class Candidate {
        private final String yaml;
        private final Map<String, String> values;
        private final List<EncryptedCredential> retained;

        Candidate(String yaml, Map<String, String> values, List<EncryptedCredential> retained) {
            this.yaml = yaml;
            this.values = Map.copyOf(values);
            this.retained = List.copyOf(retained);
        }
        public String yaml() { return yaml; }
        public Map<String, String> values() { return values; }
        public List<EncryptedCredential> retained() { return retained; }
        public boolean sameContent(Candidate other) {
            if (other == null || !yaml.equals(other.yaml) || !values.equals(other.values)) return false;
            Map<String, String> versions = new LinkedHashMap<>();
            retained.forEach(value -> versions.put(value.identifier(), value.version()));
            Map<String, String> otherVersions = new LinkedHashMap<>();
            other.retained.forEach(value -> otherVersions.put(value.identifier(), value.version()));
            return versions.equals(otherVersions);
        }
        @Override public String toString() { return "Effective execution candidate [redacted]"; }
    }

    public Candidate assemble(ManagedConfiguration authored, List<EncryptedCredential> managed) {
        Map<String, Object> root = yaml(authored.executionConfigurationYaml());
        Map<String, Object> loomspan = child(root, "loomspan");
        rejectAuthoredValues(loomspan);
        var retained = new ArrayList<EncryptedCredential>();
        Map<String, String> values = new LinkedHashMap<>();
        for (EncryptedCredential credential : managed) {
            if (values.put(credential.identifier(), cipher.decrypt(credential.identifier(),
                    credential.version(), credential.ciphertext())) != null)
                throw new IllegalArgumentException("Duplicate credential identifier");
            retained.add(credential);
        }
        if (!values.keySet().containsAll(authored.credentialIdentifiers()))
            throw new IllegalArgumentException("Required provider credentials are not configured");
        return new Candidate(authored.executionConfigurationYaml(), values, retained);
    }

    public void authenticate(List<EncryptedCredential> credentials) {
        for (var credential : credentials)
            cipher.decrypt(credential.identifier(), credential.version(), credential.ciphertext());
    }

    /** Require readable, reference-only YAML before storage; the framework validates its semantics. */
    public static void assertNoDirectAuthoredCredentials(String yaml) {
        boolean direct = false;
        // Scan every document and duplicate mapping before ordinary draft validation.
        // A tree parse can discard a secret behind a duplicate key or fail after it.
        try (var parser = CREDENTIAL_SCAN.createParser(yaml)) {
            while (parser.nextToken() != null) {
                if (parser.currentToken() != tools.jackson.core.JsonToken.PROPERTY_NAME) continue;
                var path = new ArrayList<String>();
                for (var context = parser.streamReadContext(); context != null; context = context.getParent())
                    if (context.currentName() != null) path.addFirst(context.currentName());
                // Exempt named aliases and referenced header names, while rejecting literal
                // credential fields even elsewhere in an invalid draft.
                boolean alias = path.size() == 3 && path.get(0).equals("loomspan")
                        && java.util.Set.of("connections", "models").contains(path.get(1));
                boolean headerName = path.size() == 5 && path.get(0).equals("loomspan")
                        && path.get(1).equals("connections") && path.get(3).equals("header-refs");
                if (DIRECT_CREDENTIAL_FIELDS.contains(parser.currentName()) && !alias && !headerName) {
                    direct = true;
                    break;
                }
            }
        } catch (RuntimeException invalidDraft) {
            // Once parsing fails, later fields cannot be classified safely (including
            // quoted or escaped YAML keys). Never persist content we could not inspect.
            throw new IllegalArgumentException("Execution configuration YAML must be syntactically valid before saving");
        }
        if (direct) throw new IllegalArgumentException("Authored provider credentials must use references");
    }

    public Candidate restore(ConfigurationSnapshot snapshot, List<EncryptedCredential> retained) {
        return assemble(new ManagedConfiguration(snapshot.configuration().skillDocuments(),
                snapshot.configuration().restRoutesYaml(), snapshot.effectiveExecutionYaml(),
                snapshot.configuration().credentialIdentifiers()), retained);
    }

    private static void rejectAuthoredValues(Map<String, Object> loomspan) {
        Object connections = loomspan.get("connections");
        if (!(connections instanceof Map<?, ?> map)) return;
        for (Object value : map.values()) {
            if (!(value instanceof Map<?, ?> fields)) continue;
            if (fields.containsKey("api-key") || fields.containsKey("headers"))
                throw new IllegalArgumentException("Authored provider credentials must use references");
            Object gemini = fields.get("gemini");
            if (gemini instanceof Map<?, ?> options
                    && (options.containsKey("credentials-json") || options.containsKey("credentials-uri")
                        || options.containsKey("credentials-ref")))
                throw new IllegalArgumentException("Authored provider credentials must use references");
            // Vertex otherwise uses the SDK's application-default credentials from
            // deployment files/environment, outside this database publication.
            if (gemini instanceof Map<?, ?> options && !options.containsKey("credentials-json-ref"))
                throw new IllegalArgumentException("Database Gemini configuration requires credentials-json-ref");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> yaml(String text) {
        try {
            Object parsed = YAML.readValue(text, Object.class);
            if (!(parsed instanceof Map<?, ?> map)) throw new IllegalArgumentException();
            Map<String, Object> root = new LinkedHashMap<>((Map<String, Object>) map);
            root.put("loomspan", new LinkedHashMap<>(child(root, "loomspan")));
            return root;
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid execution configuration YAML");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Execution configuration requires loomspan mapping");
        return (Map<String, Object>) map;
    }

}
