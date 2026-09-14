package ai.loomspan.sidecar.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SidecarDefaultsTest {
        @Test
        void loadsExactProductionDefaults() {
                try (var context = new GenericApplicationContext()) {
                        context.getEnvironment().getPropertySources()
                                        .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                        context.getEnvironment().getPropertySources()
                                        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                        new ConfigDataApplicationContextInitializer().initialize(context);
                        context.refresh();
                        var environment = context.getEnvironment();
                        assertThat(environment.getProperty("loomspan.skills.locations[0]"))
                                        .isEqualTo("file:/sidecar/skills/**/*.yaml");
                        assertThat(environment.getProperty("loomspan.skills.locations[1]"))
                                        .isEqualTo("file:/sidecar/skills/**/*.yml");
                        assertThat(environment.getProperty("loomspan.observability.enabled", Boolean.class)).isFalse();
                        assertThat(environment.getProperty("loomspan-sidecar.rest-routes-location"))
                                        .isEqualTo("file:/sidecar/rest-routes.yaml");
                        assertThat(environment.getProperty("management.server.port", Integer.class)).isEqualTo(9091);
                        assertThat(environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class))
                                        .isTrue();
                        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                                        .isEqualTo("health");
                }
        }
}
