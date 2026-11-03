package ai.loomspan.sidecar.config;

import ai.loomspan.sidecar.security.SidecarJwtProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationReferenceTest {
    @Test
    void configurationReferenceMatchesBoundPropertiesAndExamples() throws Exception {
        Set<String> expected = new LinkedHashSet<>();
        add(expected, "loomspan-sidecar.storage.", SidecarStorageProperties.class);
        add(expected, "loomspan-sidecar.snapshots.", SidecarSnapshotProperties.class);
        add(expected, "loomspan-sidecar.management.", SidecarManagementProperties.class);
        add(expected, "loomspan-sidecar.auth.jwt.", SidecarJwtProperties.class);
        add(expected, "loomspan-sidecar.trust.", ai.loomspan.sidecar.security.SidecarTrustProperties.class);
        add(expected, "loomspan-sidecar.executions.", SidecarExecutionProperties.class);

        String referenceGuide = Files.readString(Path.of("docs/operations.md"));
        String reference = referenceGuide.substring(referenceGuide.indexOf("<!-- configuration-reference:start -->"),
                referenceGuide.indexOf("<!-- configuration-reference:end -->"));
        for (String key : expected) {
            assertThat(occurrences(reference, "`" + key + "`")).as(key).isEqualTo(1);
        }
        assertThat(reference).contains("`loomspan-sidecar`", "`NEVER`", "`1MB`", "`64MB`", "required");

        String defaults = Files.readString(Path.of("src/main/resources/application.yml"));
        String compose = Files.readString(Path.of("examples/quickstart/compose.yaml"));
        String production = Files.readString(Path.of("examples/production/compose.yaml"));
        String productionEnv = Files.readString(Path.of("examples/production/production.env.example"));
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(defaults).doesNotContain("url-variables:").doesNotContain("port: 9091");
        assertThat(defaults).contains("mail.smtp.starttls.required: ${LOOMSPAN_SIDECAR_SMTP_STARTTLS_REQUIRED:false}");
        assertThat(defaults).contains("database-path: /sidecar/data/sidecar.db");
        assertThat(defaults).contains("max-retained: 10");
        assertThat(defaults).contains("session-idle-timeout: 30m", "edit-lease-timeout: 15m", "mail-from:", "external-base-url:",
                "mail.smtp.connectiontimeout: 5000", "mail.smtp.timeout: 5000", "mail.smtp.writetimeout: 5000");
        assertThat(defaults).contains("http-only: true", "same-site: lax",
                "secure: ${LOOMSPAN_SIDECAR_SECURE_COOKIE:true}");
        assertThat(defaults).doesNotContain("trust:", "roles-claim:", "role-prefix:", "clock-skew:", "issuer-uri:");
        assertThat(compose).contains("sidecar-keys:/sidecar/keys:ro", "LOOMSPAN_SIDECAR_TRUST_MODE: colocated",
                "network_mode: \"service:sidecar\"",
                "127.0.0.1:${QUICKSTART_HOST_PORT:-8081}:18081", "127.0.0.1:${SIDECAR_API_PORT:-8080}:18080",
                "sidecar-data:/sidecar/data");
        assertThat(production).contains("user: \"10001:10001\"", "sidecar-data:/sidecar/data",
                "SERVER_SSL_ENABLED: \"false\"", "SERVER_FORWARD_HEADERS_STRATEGY: framework",
                "caddy-data:/data", "caddy-config:/config", "LOOMSPAN_SIDECAR_TRUST_MODE: colocated",
                "SERVER_ADDRESS: 127.0.0.1",
                "network_mode: \"service:sidecar\"", "/sidecar/keys:ro", "stop_grace_period: 45s")
                .doesNotContain("9091:9091", "8080:8080", "JWT_PUBLIC_KEY_FILE", "JWT_ISSUER_URI");
        assertThat(production).contains("LOOMSPAN_SIDECAR_CONFIGURATION_MODE: ${LOOMSPAN_SIDECAR_CONFIGURATION_MODE:-database}");
        assertThat(productionEnv).contains("JWT_KEY_DIRECTORY=", "LOOMSPAN_SIDECAR_CREDENTIAL_KEY=", "LOOMSPAN_SIDECAR_CONFIGURATION_MODE=database", "SMTP_HOST=",
                "EXTERNAL_BASE_URL=https://");
        assertThat(Files.exists(Path.of("examples/kubernetes/deployment.yaml"))).isFalse();
        assertThat(dockerfile).contains("mkdir -p /sidecar/data", "chown loomspan:loomspan /sidecar/data")
                .doesNotContain("VOLUME [\"/sidecar\"]");
    }

    @Test
    void eachReferenceRowDescribesItsOwnDefaultAndConstraints() throws Exception {
        String referenceGuide = Files.readString(Path.of("docs/operations.md"));
        var rows = new java.util.LinkedHashMap<String, String>();
        referenceGuide.lines().filter(line -> line.startsWith("| `loomspan-sidecar.")).forEach(line -> {
            String[] cells = line.split("\\|", -1);
            rows.put(cells[1].trim().replace("`", ""), cells[2]);
        });
        var execution = new SidecarExecutionProperties();
        var jwt = new SidecarJwtProperties();
        assertThat(rows).doesNotContainKey("loomspan-sidecar.url-variables");
        assertThat(rows.get("loomspan-sidecar.storage.database-path"))
                .contains(new SidecarStorageProperties().getDatabasePath(), "writable", "persistent", "startup");
        assertThat(rows.get("loomspan-sidecar.snapshots.max-retained"))
                .contains("`" + new SidecarSnapshotProperties().getMaxRetained() + "`", "positive", "snapshot");
        assertThat(rows.get("loomspan-sidecar.management.session-idle-timeout"))
                .contains("`30m`", "positive", "activity");
        assertThat(rows.get("loomspan-sidecar.management.edit-lease-timeout"))
                .contains("`15m`", "positive", "activity");
        assertThat(rows.get("loomspan-sidecar.management.mail-from")).contains("sender", "email");
        assertThat(rows.get("loomspan-sidecar.management.external-base-url")).contains("HTTPS", "link");
        assertThat(rows.get("loomspan-sidecar.auth.jwt.audience")).contains(jwt.getAudience(), "nonblank");
        assertThat(rows.get("loomspan-sidecar.trust.mode")).contains("required", "colocated", "file");
        assertThat(rows.get("loomspan-sidecar.trust.colocated.key-directory")).contains("required", "jwks.json");
        assertThat(rows.get("loomspan-sidecar.trust.file.jwks")).contains("required", "filesystem");
        assertThat(rows.get("loomspan-sidecar.executions.max-input-size"))
                .contains("`" + execution.getMaxInputSize().toMegabytes() + "MB`", "positive", "raw HTTP body");
        assertThat(rows.get("loomspan-sidecar.executions.max-retained"))
                .contains("`" + execution.getMaxRetained() + "`", "positive", "queued, running, and terminal");
        assertThat(rows.get("loomspan-sidecar.executions.completed-ttl"))
                .contains("`" + execution.getCompletedTtl().toMinutes() + "m`", "positive", "terminal-record");
        assertThat(rows.get("loomspan-sidecar.executions.max-concurrent"))
                .contains("`" + execution.getMaxConcurrent() + "`", "positive", "worker");
        assertThat(rows.get("loomspan-sidecar.executions.max-queued"))
                .contains("`" + execution.getMaxQueued() + "`", "positive", "waiting-record");
        assertThat(rows.get("loomspan-sidecar.executions.max-queued-input-size"))
                .contains("`" + execution.getMaxQueuedInputSize().toMegabytes() + "MB`", "positive", "serialized", "waiting work");
        assertThat(rows.get("loomspan-sidecar.executions.diagnostics"))
                .contains("`" + execution.getDiagnostics() + "`", "`NEVER`, `ONERROR`, and `ALWAYS`");
    }

    private static void add(Set<String> keys, String prefix, Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                String name = prefix + kebab(method.getName().substring(3));
                if (method.getReturnType().getEnclosingClass() == ai.loomspan.sidecar.security.SidecarTrustProperties.class) {
                    add(keys, name + ".", method.getReturnType());
                } else keys.add(name);
            }
        }
    }

    private static String kebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT);
    }

    private static int occurrences(String text, String value) {
        int result = 0;
        for (int at = 0; (at = text.indexOf(value, at)) >= 0; at += value.length()) result++;
        return result;
    }
}
