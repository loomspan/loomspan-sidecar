package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.SkillDocument;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.execution.ExecutionCoordinator;
import ai.loomspan.sidecar.execution.ExecutionUnavailableException;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotRepository;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
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
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeConfigurationIntegrationTest {
    @TempDir Path directory;

    @Test
    void newDatabaseActivatesPublishedEmptySelectionAndOpensDispatch() {
        try (var context = start(directory.resolve("new.db"))) {
            var store = context.getBean(ConfigurationSnapshotStore.class);
            var service = context.getBean(RuntimeConfigurationService.class);
            var reloader = context.getBean(SkillReloader.class);
            assertThat(reloader.snapshot().skills()).isEmpty();
            assertThat(store.current().status()).isEqualTo(SnapshotStatus.PUBLISHED);
            assertThat(service.inspect().publishedId()).isEqualTo(store.current().localId());
            assertThat(service.inspect().intendedId()).isEqualTo(store.current().localId());
        }
    }

    @Test
    void startupKeepsExecutionGateClosedUntilSelectedActivationFinishes() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var starting = new java.util.concurrent.atomic.AtomicReference<org.springframework.context.ConfigurableApplicationContext>();
        var activationHook = new RuntimeConfigurationService.Hooks() {
            @Override public void beforeStartupActivation() {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }
        };
        var builder = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .initializers(context -> {
                    starting.set(context);
                    context.addBeanFactoryPostProcessor(factory -> factory.addBeanPostProcessor(
                            new org.springframework.beans.factory.config.BeanPostProcessor() {
                                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                                    if (bean instanceof RuntimeConfigurationService service) service.hooks(activationHook);
                                    return bean;
                                }
                            }));
                });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var startup = workers.submit(() -> builder.run(
                    "--loomspan-sidecar.storage.database-path=" + directory.resolve("gated.db"),
                    "--loomspan.observability.enabled=false",
                    "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                    "--loomspan-sidecar.auth.jwt.audience=sidecar",
                    "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"));
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                var coordinator = starting.get().getBean(ExecutionCoordinator.class);
                assertThatThrownBy(() -> coordinator.admit("unknown", Map.of(), 0,
                        new ai.loomspan.sidecar.security.ExecutionOwner("issuer", "subject"), null))
                        .isInstanceOf(ExecutionUnavailableException.class);
                assertThat(startup.isDone()).isFalse();
            } finally { release.countDown(); }
            try (var context = startup.get(8, TimeUnit.SECONDS)) {
                assertThat(context.getBean(RuntimeConfigurationService.class).inspect().publishedId()).isNotNull();
            }
        }
    }

    @Test
    void selectedPendingContentActivatesAcrossRestartAndPublishesCompleteReplacement() {
        Path path = directory.resolve("selected.db");
        var store = openStore(path);
        ConfigurationSnapshot empty = store.current();
        ConfigurationSnapshot selected = store.submit(new ManagedConfiguration(List.of(restDocument("echoRest")), routes("echoRest")),
                null, empty.localId());
        try (var context = start(path)) {
            var service = context.getBean(RuntimeConfigurationService.class);
            var reloader = context.getBean(SkillReloader.class);
            assertThat(reloader.snapshot().skill("echoRest")).isPresent();
            assertThat(context.getBean(ConfigurationSnapshotStore.class).current().status()).isEqualTo(SnapshotStatus.PUBLISHED);
            assertThat(service.inspect().publishedId()).isEqualTo(selected.localId());
            var draft = new ConfigurationDraft(context.getBean(ConfigurationSnapshotStore.class).current());
            draft.replaceContent(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            assertThat(service.validate(draft).successful()).isTrue();
            ConfigurationSnapshot replacement = service.publish(draft);
            assertThat(reloader.snapshot().skills()).isEmpty();
            assertThat(service.inspect().publishedId()).isEqualTo(replacement.localId());
            assertThat(context.getBean(ConfigurationSnapshotStore.class).current().status()).isEqualTo(SnapshotStatus.PUBLISHED);
        }
        try (var restarted = start(path)) {
            assertThat(restarted.getBean(SkillReloader.class).snapshot().skills()).isEmpty();
            assertThat(restarted.getBean(RuntimeConfigurationService.class).inspect().publishedId())
                    .isEqualTo(openStore(path).current().localId());
        }
    }

    @Test
    void validationDoesNotSubmitAndEqualContentEditInvalidatesPublish() {
        Path path = directory.resolve("validation.db");
        try (var context = start(path)) {
            var store = context.getBean(ConfigurationSnapshotStore.class);
            var service = context.getBean(RuntimeConfigurationService.class);
            var before = store.current();
            var draft = new ConfigurationDraft(before);
            ManagedConfiguration content = new ManagedConfiguration(List.of(restDocument("echoRest")), routes("echoRest"));
            draft.replaceContent(content);
            assertThat(service.validate(draft).successful()).isTrue();
            assertThat(store.current().localId()).isEqualTo(before.localId());
            draft.replaceContent(content);
            assertThatThrownBy(() -> service.publish(draft)).hasMessageContaining("requires successful validation");
            assertThat(store.current().localId()).isEqualTo(before.localId());
            draft.replaceContent(new ManagedConfiguration(List.of(restDocument("echoRest")),
                    ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var invalid = service.validate(draft);
            assertThat(invalid.successful()).isFalse();
            assertThat(invalid.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.sourceLabel()).isEqualTo("rest-routes.yaml");
                assertThat(issue.message()).contains("has no route");
                assertThat(issue.location()).isEqualTo("routes.echoRest");
            });
            assertThat(store.current().localId()).isEqualTo(before.localId());
            draft.replaceContent(new ManagedConfiguration(List.of(new SkillDocument("bad.yaml", "name: [")),
                    ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var malformed = service.validate(draft);
            assertThat(malformed.successful()).isFalse();
            assertThat(malformed.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.sourceLabel()).isEqualTo("bad.yaml"));
            draft.replaceContent(new ManagedConfiguration(List.of(new SkillDocument("missing-description.yaml",
                    "name: missingDescription\nrest: true\n")), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var missingDescription = service.validate(draft);
            assertThat(missingDescription.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.sourceLabel()).isEqualTo("missing-description.yaml");
                assertThat(issue.location()).isEqualTo("description");
            });
            draft.replaceContent(new ManagedConfiguration(List.of(restDocument("echoRest")),
                    routes("echoRest").replace("http://127.0.0.1:9", "'${CALLBACK_URL}'")));
            var invalidBinding = service.validate(draft);
            assertThat(invalidBinding.successful()).isFalse();
            assertThat(invalidBinding.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.sourceLabel()).isEqualTo("rest-routes.yaml");
                assertThat(issue.location()).isEqualTo("targets.callback.base-url");
            });
            assertThat(store.current().localId()).isEqualTo(before.localId());
        }
    }

    @Test
    void clientStagingFailureReportsRestDocumentWithoutLeakingTheCause() {
        var store = org.mockito.Mockito.mock(ConfigurationSnapshotStore.class);
        var reloader = org.mockito.Mockito.mock(SkillReloader.class);
        var prepared = org.mockito.Mockito.mock(ai.loomspan.api.PreparedSkillUpdate.class);
        var resources = org.mockito.Mockito.mock(ai.loomspan.sidecar.rest.GenerationRestResources.class);
        var executions = org.mockito.Mockito.mock(ai.loomspan.sidecar.execution.ExecutionCoordinator.class);
        org.mockito.Mockito.when(reloader.prepare(org.mockito.ArgumentMatchers.anyCollection())).thenReturn(prepared);
        org.mockito.Mockito.when(resources.prepare(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("sensitive SSL configuration detail"));
        var service = new RuntimeConfigurationService(store, reloader, resources,
                new ai.loomspan.sidecar.rest.RestRouteCatalogValidator(), executions);
        var base = new ConfigurationSnapshot(java.util.UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES), SnapshotStatus.PUBLISHED);

        var result = service.validate(new ConfigurationDraft(base));

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.sourceLabel()).isEqualTo("rest-routes.yaml");
            assertThat(issue.message()).isEqualTo("REST client staging failed");
            assertThat(issue.message()).doesNotContain("sensitive SSL configuration detail");
        });
    }

    @Test
    void storedRestEnvironmentReferenceRemainsAuthoredText() {
        Path path = directory.resolve("authored.db");
        var store = openStore(path);
        var a = store.current();
        String authored = routes("echoRest").replace("http://127.0.0.1:9", "${CALLBACK_URL}");
        var b = store.submit(new ManagedConfiguration(List.of(restDocument("echoRest")), authored), null, a.localId());
        assertThat(openStore(path).current().localId()).isEqualTo(b.localId());
        assertThat(openStore(path).current().configuration().restRoutesYaml()).isEqualTo(authored);
    }

    @Test
    void publicationFaultsKeepRuntimePointerAndMutationGateTruthful() {
        for (String fault : List.of("commit", "publish", "revert", "status")) {
            Path path = directory.resolve(fault + ".db");
            try (var context = start(path)) {
                var service = context.getBean(RuntimeConfigurationService.class);
                var store = context.getBean(ConfigurationSnapshotStore.class);
                var reloader = context.getBean(SkillReloader.class);
                var a = store.current();
                var draft = validDraft(service, a, "echoRest");
                if (fault.equals("commit")) {
                    new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class)).execute(
                            "CREATE TRIGGER fail_switch BEFORE UPDATE ON configuration_store_state "
                                    + "BEGIN SELECT RAISE(ABORT, 'injected commit failure'); END");
                }
                service.hooks(new RuntimeConfigurationService.Hooks() {
                    @Override public void beforeFrameworkPublish() {
                        if (fault.equals("publish") || fault.equals("revert")) throw new IllegalStateException("injected");
                    }
                    @Override public void beforeRevert() { if (fault.equals("revert")) throw new IllegalStateException("injected"); }
                    @Override public void beforeStatus() { if (fault.equals("status")) throw new IllegalStateException("injected"); }
                });
                assertThatThrownBy(() -> service.publish(draft)).isInstanceOf(IllegalStateException.class);
                var inspection = service.inspect();
                if (fault.equals("commit") || fault.equals("publish")) {
                    assertThat(inspection.publishedId()).isEqualTo(a.localId());
                    assertThat(inspection.intendedId()).isEqualTo(a.localId());
                    assertThat(reloader.snapshot().skills()).isEmpty();
                    assertThat(inspection.mutationFault()).isNull();
                    assertThat(context.getBean(ai.loomspan.sidecar.rest.GenerationRestResources.class).protectedIds())
                            .containsExactly(a.localId());
                } else if (fault.equals("revert")) {
                    assertThat(inspection.publishedId()).isEqualTo(a.localId());
                    assertThat(inspection.intendedId()).isNotEqualTo(a.localId());
                    assertThat(inspection.intendedStatus()).isEqualTo(SnapshotStatus.PENDING);
                    assertThat(inspection.mutationFault()).isNotBlank();
                    assertThat(reloader.snapshot().skills()).isEmpty();
                    assertThat(context.getBean(ai.loomspan.sidecar.rest.GenerationRestResources.class).protectedIds())
                            .containsExactly(a.localId());
                } else {
                    assertThat(inspection.publishedId()).isEqualTo(inspection.intendedId());
                    assertThat(inspection.publishedId()).isNotEqualTo(a.localId());
                    assertThat(inspection.intendedStatus()).isEqualTo(SnapshotStatus.PENDING);
                    assertThat(inspection.mutationFault()).isNotBlank();
                    assertThat(reloader.snapshot().skill("echoRest")).isPresent();
                    assertThat(context.getBean(ai.loomspan.sidecar.rest.GenerationRestResources.class).protectedIds())
                            .contains(inspection.publishedId());
                }
                if (!fault.equals("commit")) {
                    var b = store.findByLocalId(fault.equals("publish") ?
                            latestOtherThan(context.getBean(javax.sql.DataSource.class), a.localId()) : inspection.intendedId());
                    assertThat(b).isNotNull();
                    assertThat(b.status()).isEqualTo(fault.equals("publish") ? SnapshotStatus.FAILED : SnapshotStatus.PENDING);
                }
                if (inspection.mutationFault() != null) {
                    assertThatThrownBy(() -> service.publish(draft)).hasMessageContaining("mutations are stopped");
                }
                if (fault.equals("revert")) {
                    new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class))
                            .execute("DROP TABLE configuration_store_state");
                    var unavailable = service.inspect();
                    assertThat(unavailable.publishedId()).isEqualTo(a.localId());
                    assertThat(unavailable.intendedId()).isEqualTo(inspection.intendedId());
                    assertThat(unavailable.intendedStatus()).isEqualTo(SnapshotStatus.PENDING);
                    assertThat(unavailable.mutationFault()).isNotBlank();
                }
            }
        }
    }

    @Test
    void competingPublicationRechecksBaseAndUsesAcceptedFrozenContent() throws Exception {
        Path path = directory.resolve("serialized.db");
        try (var context = start(path); var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var service = context.getBean(RuntimeConfigurationService.class);
            var store = context.getBean(ConfigurationSnapshotStore.class);
            var a = store.current();
            var first = validDraft(service, a, "firstRest");
            var second = validDraft(service, a, "secondRest");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            service.hooks(new RuntimeConfigurationService.Hooks() {
                @Override public void beforeCommit() {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                }
            });
            var accepted = workers.submit(() -> service.publish(first));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            first.replaceContent(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var waiting = workers.submit(() -> {
                try { service.publish(second); return "accepted"; }
                catch (IllegalStateException failure) { return failure.getMessage(); }
            });
            assertThat(waiting.isDone()).isFalse();
            release.countDown();
            var b = accepted.get(5, TimeUnit.SECONDS);
            assertThat(b.configuration().skillDocuments().getFirst().yaml()).contains("firstRest");
            assertThat(waiting.get(5, TimeUnit.SECONDS)).contains("stale runtime snapshot");
            assertThat(context.getBean(SkillReloader.class).snapshot().skill("firstRest")).isPresent();
        }
    }

    @Test
    void publicationPruningProtectsAdmittedGenerationsThenRemovesEligibleHistory() throws Exception {
        Path path = directory.resolve("protected-history.db");
        Path skill = directory.resolve("protectedRest.yaml");
        java.nio.file.Files.writeString(skill, restDocument("protectedRest").yaml());
        ai.loomspan.sidecar.support.SidecarApplicationFixture.seedDatabase(path, List.of(skill), routes("protectedRest"));
        try (var context = start(path, 2)) {
            var store = context.getBean(ConfigurationSnapshotStore.class);
            var service = context.getBean(RuntimeConfigurationService.class);
            var handoff = context.getBean(ai.loomspan.api.SkillInvocationHandoff.class);
            var resources = context.getBean(ai.loomspan.sidecar.rest.GenerationRestResources.class);
            var a = store.current();
            var admittedA = handoff.handoff("protectedRest", Map.of());
            ai.loomspan.api.AdmittedSkillInvocation admittedB = null;
            ai.loomspan.api.AdmittedSkillInvocation admittedC = null;
            try {
                var b = publishRouteOnly(service, store.current(), 10);
                admittedB = handoff.handoff("protectedRest", Map.of());
                var c = publishRouteOnly(service, store.current(), 11);
                admittedC = handoff.handoff("protectedRest", Map.of());

                assertThat(resources.protectedIds()).contains(a.localId(), b.localId(), c.localId());
                assertThat(store.findByLocalId(a.localId())).isNotNull();
                assertThat(store.findByLocalId(b.localId())).isNotNull();
                assertThat(store.findByLocalId(c.localId())).isNotNull();
                assertThat(snapshotCount(context)).isEqualTo(3).isGreaterThan(2);

                admittedA.release();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (resources.protectedIds().contains(a.localId()) && System.nanoTime() < deadline)
                    Thread.sleep(10);
                assertThat(resources.protectedIds()).doesNotContain(a.localId());
                var d = publishRouteOnly(service, store.current(), 12);
                assertThat(store.findByLocalId(a.localId())).isNull();
                assertThat(store.findByLocalId(b.localId())).isNotNull();
                assertThat(store.findByLocalId(c.localId())).isNotNull();
                assertThat(store.findByLocalId(d.localId())).isNotNull();
                assertThat(snapshotCount(context)).isEqualTo(3);
            } finally {
                admittedA.release();
                if (admittedB != null) admittedB.release();
                if (admittedC != null) admittedC.release();
            }
        }
    }

    private ConfigurationSnapshot publishRouteOnly(RuntimeConfigurationService service,
            ConfigurationSnapshot base, int port) {
        var draft = new ConfigurationDraft(base);
        draft.replaceContent(new ManagedConfiguration(base.configuration().skillDocuments(),
                routes("protectedRest").replace("127.0.0.1:9", "127.0.0.1:" + port)));
        assertThat(service.validate(draft).successful()).isTrue();
        return service.publish(draft);
    }

    private int snapshotCount(org.springframework.context.ConfigurableApplicationContext context) {
        return new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class))
                .queryForObject("SELECT COUNT(*) FROM configuration_snapshot", Integer.class);
    }

    private ConfigurationDraft validDraft(RuntimeConfigurationService service, ConfigurationSnapshot base, String name) {
        var draft = new ConfigurationDraft(base);
        draft.replaceContent(new ManagedConfiguration(List.of(restDocument(name)), routes(name)));
        assertThat(service.validate(draft).successful()).isTrue();
        return draft;
    }

    private java.util.UUID latestOtherThan(javax.sql.DataSource source, java.util.UUID a) {
        var ids = new org.springframework.jdbc.core.JdbcTemplate(source).queryForList(
                "SELECT local_id FROM configuration_snapshot ORDER BY submission_sequence DESC", String.class);
        return ids.stream().map(java.util.UUID::fromString).filter(id -> !id.equals(a)).findFirst().orElseThrow();
    }

    private org.springframework.context.ConfigurableApplicationContext start(Path path) {
        return start(path, 10);
    }

    private org.springframework.context.ConfigurableApplicationContext start(Path path, int maxRetained) {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                .web(WebApplicationType.NONE)
                .run("--loomspan-sidecar.storage.database-path=" + path,
                        "--loomspan-sidecar.snapshots.max-retained=" + maxRetained,
                        "--loomspan.observability.enabled=false",
                        "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
    }

    private ConfigurationSnapshotStore openStore(Path path) {
        var source = StorageConfiguration.dataSource(path);
        StorageConfiguration.migrate(source);
        var repository = new ConfigurationSnapshotRepository(new NamedParameterJdbcTemplate(source));
        var store = new ConfigurationSnapshotStore(repository, new TransactionTemplate(new DataSourceTransactionManager(source)));
        store.initialize();
        return store;
    }

    private SkillDocument restDocument(String name) {
        return new SkillDocument(name + ".yaml", "name: " + name + "\ndescription: Test REST skill.\nrest: true\n");
    }

    private String routes(String name) {
        return "targets:\n  callback: {base-url: http://127.0.0.1:9, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}\nroutes:\n  "
                + name + ": {target: callback, method: GET, path: /echo}\n";
    }
}
