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
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
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
            return new java.util.HashSet<>(retained).equals(new java.util.HashSet<>(other.retained));
        }
        @Override public String toString() { return "Effective execution candidate [redacted]"; }
    }

    public Candidate assemble(ManagedConfiguration authored, List<EncryptedCredential> managed) {
        assertNoDirectAuthoredCredentials(authored.executionConfigurationYaml());
        Map<String, Object> root = yaml(authored.executionConfigurationYaml());
        Map<String, Object> loomspan = child(root, "loomspan");
        rejectAuthoredValues(loomspan);
        var retained = new ArrayList<EncryptedCredential>();
        Map<String, String> decrypted = new LinkedHashMap<>();
        for (EncryptedCredential credential : managed.stream()
                .sorted(java.util.Comparator.comparing(EncryptedCredential::identifier)).toList()) {
            if (decrypted.put(credential.identifier(), cipher.decrypt(credential.identifier(),
                    credential.version(), credential.ciphertext())) != null)
                throw new IllegalArgumentException("Duplicate credential identifier");
            retained.add(credential);
        }
        if (!decrypted.keySet().containsAll(authored.credentialIdentifiers()))
            throw new IllegalArgumentException("Required credentials are not configured");
        Map<String, String> values = new LinkedHashMap<>();
        Object connections = loomspan.get("connections");
        if (connections instanceof Map<?, ?> providers) for (Object provider : providers.values()) {
            if (!(provider instanceof Map<?, ?> fields)) continue;
            reference(fields.get("api-key-ref"), decrypted, values);
            if (fields.get("header-refs") instanceof Map<?, ?> headers)
                headers.values().forEach(ref -> reference(ref, decrypted, values));
            if (fields.get("gemini") instanceof Map<?, ?> options)
                reference(options.get("credentials-json-ref"), decrypted, values);
        }
        boolean attached = false;
        if (!retained.isEmpty() && loomspan.get("executor-config") instanceof Map<?, ?> namespaces
                && namespaces.get("default") instanceof Map<?, ?> executors
                && executors.get("http") instanceof Map<?, ?> http) {
            var selected = new LinkedHashMap<String, Object>();
            http.forEach((key, value) -> selected.put((String) key, value));
            var executorCopy = new LinkedHashMap<Object, Object>(executors);
            executorCopy.put("http", ai.loomspan.sidecar.http.HttpExecutorConfiguration.withCredentials(selected, retained));
            var namespaceCopy = new LinkedHashMap<Object, Object>(namespaces);
            namespaceCopy.put("default", executorCopy);
            loomspan.put("executor-config", namespaceCopy);
            attached = true;
        }
        return new Candidate(attached ? YAML.writeValueAsString(root) : authored.executionConfigurationYaml(), values, retained);
    }

    private static void reference(Object ref, Map<String, String> records, Map<String, String> values) {
        if (ref instanceof String id && records.containsKey(id)) values.put(id, records.get(id));
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
                boolean httpTarget = path.size() >= 6 && path.get(0).equals("loomspan")
                        && path.get(1).equals("executor-config") && path.get(2).equals("default")
                        && path.get(3).equals("http") && path.get(4).equals("targets");
                boolean httpHeaders = httpTarget && path.size() == 7 && path.get(6).equals("headers");
                boolean httpTargetAlias = httpTarget && path.size() == 6;
                boolean httpHeaderName = httpTarget && ((path.size() == 8 && path.get(6).equals("headers"))
                        || (path.size() == 9 && path.get(6).equals("auth") && path.get(7).equals("header-refs")));
                boolean httpInventory = path.equals(List.of("loomspan", "executor-config", "default", "http", "credentials"));
                if (httpInventory || (DIRECT_CREDENTIAL_FIELDS.contains(parser.currentName())
                        && !alias && !headerName && !httpTargetAlias && !httpHeaders && !httpHeaderName)) {
                    direct = true;
                    break;
                }
            }
        } catch (RuntimeException invalidDraft) {
            // Once parsing fails, later fields cannot be classified safely (including
            // quoted or escaped YAML keys). Never persist content we could not inspect.
            throw new IllegalArgumentException("Execution configuration YAML must be syntactically valid before saving");
        }
        if (direct) throw new IllegalArgumentException("Authored credentials must use managed references");
    }

    public Candidate restore(ConfigurationSnapshot snapshot, List<EncryptedCredential> retained) {
        Candidate candidate = assemble(snapshot.configuration(), retained);
        // Mapping order is not configuration content; immutable map iteration may
        // differ across JVMs when generated credential fields are serialized.
        if (!yaml(candidate.yaml()).equals(yaml(snapshot.effectiveExecutionYaml())))
            throw new IllegalArgumentException("Stored effective configuration is inconsistent");
        return candidate;
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
