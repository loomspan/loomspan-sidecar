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
        add(expected, "loomspan-sidecar.", RestRoutesProperties.class);
        add(expected, "loomspan-sidecar.auth.jwt.", SidecarJwtProperties.class);
        add(expected, "loomspan-sidecar.executions.", SidecarExecutionProperties.class);

        String readme = Files.readString(Path.of("README.md"));
        String reference = readme.substring(readme.indexOf("<!-- configuration-reference:start -->"),
                readme.indexOf("<!-- configuration-reference:end -->"));
        for (String key : expected) {
            assertThat(occurrences(reference, "`" + key + "`")).as(key).isEqualTo(1);
        }
        assertThat(reference).contains("`60s`", "`ROLE_`", "`NEVER`", "`1MB`", "`64MB`", "required");

        String defaults = Files.readString(Path.of("src/main/resources/application.yml"));
        String compose = Files.readString(Path.of("examples/quickstart/compose.yaml"));
        String kubernetes = Files.readString(Path.of("examples/kubernetes/deployment.yaml"));
        assertThat(defaults).contains("file:/sidecar/skills/**/*.yaml", "file:/sidecar/rest-routes.yaml", "port: 9091");
        assertThat(compose).contains("./sidecar:/sidecar:ro", "LOOMSPAN_SIDECAR_AUTH_JWT_AUDIENCE",
                "${QUICKSTART_HOST_PORT:-8081}:8081", "${SIDECAR_API_PORT:-8080}:8080",
                "${SIDECAR_MANAGEMENT_PORT:-9091}:9091");
        assertThat(kubernetes).contains("/sidecar/skills/", "/sidecar/rest-routes.yaml", "readOnly: true", "secretKeyRef:");
    }

    private static void add(Set<String> keys, String prefix, Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                keys.add(prefix + kebab(method.getName().substring(3)));
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
