package ai.loomspan.sidecar.rest;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotRepository;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.StorageConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestRouteValidationIntegrationTest {
    @TempDir Path directory;

    @Test
    void invalidSelectedRoutesAbortStartupWithoutSelectingEarlierContent() {
        Path database = directory.resolve("invalid-routes.db");
        var source = StorageConfiguration.dataSource(database);
        StorageConfiguration.migrate(source);
        var store = new ConfigurationSnapshotStore(new ConfigurationSnapshotRepository(
                new NamedParameterJdbcTemplate(source)),
                new TransactionTemplate(new DataSourceTransactionManager(source)));
        var a = store.initialize();
        var b = store.submit(new ManagedConfiguration(List.of(new SkillDocument("leaf.yaml", """
                name: leaf
                description: A REST skill.
                rest: true
                input_schema: {type: object, properties: {}}
                """)), "targets: {}\nroutes: {}\n", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), null, a.localId());
        assertThatThrownBy(() -> application(database)).hasMessageContaining("Selected configuration activation");
        assertThat(store.current().localId()).isEqualTo(b.localId());
        assertThat(store.current().status()).isEqualTo(ai.loomspan.sidecar.storage.SnapshotStatus.PENDING);
    }

    @Test
    void malformedSelectedYamlDoesNotLeakAuthoredValuesInStartupError() {
        Path database = directory.resolve("malformed-routes.db");
        var source = StorageConfiguration.dataSource(database);
        StorageConfiguration.migrate(source);
        var store = new ConfigurationSnapshotStore(new ConfigurationSnapshotRepository(
                new NamedParameterJdbcTemplate(source)),
                new TransactionTemplate(new DataSourceTransactionManager(source)));
        var a = store.initialize();
        store.submit(new ManagedConfiguration(List.of(), "targets: [private-secret", ai.loomspan.sidecar.storage.ConfigurationSnapshotStore.EMPTY_EXECUTION_CONFIGURATION), null, a.localId());
        assertThatThrownBy(() -> application(database))
                .hasMessageContaining("Selected configuration activation")
                .hasMessageNotContaining("private-secret");
    }

    private org.springframework.context.ConfigurableApplicationContext application(Path database) {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class).web(WebApplicationType.NONE).run(
                "--loomspan-sidecar.storage.database-path=" + database,
                "--loomspan.observability.enabled=false",
                "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
    }
}
