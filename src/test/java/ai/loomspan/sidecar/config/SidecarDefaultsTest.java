package ai.loomspan.sidecar.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                                        .isEqualTo("classpath:/sidecar-empty-skills/*.yaml");
                        assertThat(environment.getProperty("loomspan.observability.enabled", Boolean.class)).isFalse();
                        assertThat(environment.getProperty("loomspan-sidecar.url-variables[0]")).isNull();
                        assertThat(environment.getProperty("loomspan-sidecar.auth.jwt.roles-claim")).isEqualTo("roles");
                        assertThat(environment.getProperty("loomspan-sidecar.auth.jwt.role-prefix")).isEqualTo("ROLE_");
                        assertThat(environment.getProperty("loomspan-sidecar.auth.jwt.clock-skew")).isEqualTo("60s");
                        assertThat(environment.getProperty("loomspan-sidecar.executions.max-input-size")).isEqualTo("1MB");
                        assertThat(environment.getProperty("loomspan-sidecar.snapshots.max-retained", Integer.class)).isEqualTo(10);
                        assertThat(environment.getProperty("loomspan-sidecar.management.edit-lease-timeout")).isEqualTo("15m");
                        assertThat(environment.getProperty("loomspan-sidecar.executions.max-retained", Integer.class)).isEqualTo(1000);
                        assertThat(environment.getProperty("loomspan-sidecar.executions.completed-ttl")).isEqualTo("15m");
                        assertThat(environment.getProperty("loomspan-sidecar.executions.max-concurrent", Integer.class)).isEqualTo(32);
                        assertThat(environment.getProperty("loomspan-sidecar.executions.max-queued", Integer.class)).isEqualTo(128);
                        assertThat(environment.getProperty("loomspan-sidecar.executions.max-queued-input-size")).isEqualTo("64MB");
                        assertThat(environment.getProperty("loomspan-sidecar.executions.diagnostics")).isEqualTo("NEVER");
                        assertThat(environment.getProperty("management.server.port", Integer.class)).isEqualTo(9091);
                        assertThat(environment.getProperty("management.endpoint.health.probes.enabled", Boolean.class))
                                        .isTrue();
                        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                                        .isEqualTo("health");
                }
        }

        @Test
        void editingLeaseTimeoutMustBePositive() {
                var properties = new SidecarManagementProperties();
                assertThat(properties.getEditLeaseTimeout()).isEqualTo(java.time.Duration.ofMinutes(15));
                assertThatThrownBy(() -> properties.setEditLeaseTimeout(java.time.Duration.ZERO))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> properties.setEditLeaseTimeout(java.time.Duration.ofSeconds(-1)))
                        .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void snapshotRetentionAcceptsPositiveValuesOnly() {
                var properties = new SidecarSnapshotProperties();
                assertThat(properties.getMaxRetained()).isEqualTo(10);
                properties.setMaxRetained(3);
                assertThat(properties.getMaxRetained()).isEqualTo(3);
                assertThatThrownBy(() -> properties.setMaxRetained(0)).isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> properties.setMaxRetained(-1)).isInstanceOf(IllegalArgumentException.class);
                assertThat(new Binder(new MapConfigurationPropertySource(
                        java.util.Map.of("loomspan-sidecar.snapshots.max-retained", "3")))
                        .bind("loomspan-sidecar.snapshots", SidecarSnapshotProperties.class).get().getMaxRetained())
                        .isEqualTo(3);
                for (String invalid : java.util.List.of("0", "-1")) {
                        assertThatThrownBy(() -> new Binder(new MapConfigurationPropertySource(
                                java.util.Map.of("loomspan-sidecar.snapshots.max-retained", invalid)))
                                .bind("loomspan-sidecar.snapshots", SidecarSnapshotProperties.class))
                                .isInstanceOf(org.springframework.boot.context.properties.bind.BindException.class);
                }
        }
}
