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
import ai.loomspan.sidecar.storage.ConfigurationValidationIssue;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import ai.loomspan.sidecar.storage.StorageConfiguration;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV1;
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
            assertThat(ai.loomspan.sidecar.support.TestDrafts.validate(service, draft).successful()).isTrue();
            ConfigurationSnapshot replacement = context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(draft);
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
            String generationBefore = context.getBean(SkillReloader.class).snapshot().generationId();
            int historyBefore = store.history().size();
            var draft = new ConfigurationDraft(before);
            ManagedConfiguration content = new ManagedConfiguration(List.of(restDocument("echoRest")), routes("echoRest"));
            draft.replaceContent(content);
            assertThat(ai.loomspan.sidecar.support.TestDrafts.validate(service, draft).successful()).isTrue();
            assertThat(store.current().localId()).isEqualTo(before.localId());
            assertThat(store.history()).hasSize(historyBefore);
            assertThat(context.getBean(SkillReloader.class).snapshot().generationId()).isEqualTo(generationBefore);
            draft.replaceContent(content);
            assertThatThrownBy(() -> context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(draft)).hasMessageContaining("requires successful validation");
            assertThat(store.current().localId()).isEqualTo(before.localId());
            draft.replaceContent(new ManagedConfiguration(List.of(restDocument("echoRest")),
                    ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var invalid = ai.loomspan.sidecar.support.TestDrafts.validate(service, draft);
            assertThat(invalid.successful()).isFalse();
            assertThat(invalid.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.sourceLabel()).isEqualTo("rest-routes.yaml");
                assertThat(issue.severity()).isEqualTo(ConfigurationValidationIssue.Severity.ERROR);
                assertThat(issue.message()).contains("has no route");
                assertThat(issue.location()).isEqualTo("routes.echoRest");
            });
            assertThat(store.current().localId()).isEqualTo(before.localId());
            draft.replaceContent(new ManagedConfiguration(List.of(restDocument("one"), restDocument("two")),
                    routes("one")));
            var incompleteRoutes = ai.loomspan.sidecar.support.TestDrafts.validate(service, draft);
            assertThat(incompleteRoutes.successful()).isFalse();
            assertThat(incompleteRoutes.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.message()).contains("two", "has no route"));
            draft.replaceContent(new ManagedConfiguration(List.of(), routes("orphan")));
            var orphanRoute = ai.loomspan.sidecar.support.TestDrafts.validate(service, draft);
            assertThat(orphanRoute.successful()).isFalse();
            assertThat(orphanRoute.issues()).singleElement().satisfies(issue ->
                    assertThat(issue.message()).contains("orphan", "unknown skill"));
            draft.replaceContent(new ManagedConfiguration(List.of(new SkillDocument("bad.yaml", "name: [")),
                    ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var malformed = ai.loomspan.sidecar.support.TestDrafts.validate(service, draft);
            assertThat(malformed.successful()).isFalse();
            assertThat(malformed.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.sourceLabel()).isEqualTo("bad.yaml");
                assertThat(issue.severity()).isEqualTo(ConfigurationValidationIssue.Severity.ERROR);
            });
            draft.replaceContent(new ManagedConfiguration(List.of(new SkillDocument("missing-description.yaml",
                    "name: missingDescription\nrest: true\n")), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var missingDescription = ai.loomspan.sidecar.support.TestDrafts.validate(service, draft);
            assertThat(missingDescription.issues()).singleElement().satisfies(issue -> {
                assertThat(issue.sourceLabel()).isEqualTo("missing-description.yaml");
                assertThat(issue.location()).isEqualTo("description");
                assertThat(issue.severity()).isEqualTo(ConfigurationValidationIssue.Severity.ERROR);
            });
            draft.replaceContent(new ManagedConfiguration(List.of(restDocument("echoRest")),
                    routes("echoRest").replace("http://127.0.0.1:9", "'${CALLBACK_URL}'")));
            var invalidBinding = ai.loomspan.sidecar.support.TestDrafts.validate(service, draft);
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
        org.mockito.Mockito.when(reloader.validate(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(new ai.loomspan.api.SkillValidationResult(List.of(), List.of()));
        org.mockito.Mockito.when(resources.prepare(org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new IllegalStateException("sensitive SSL configuration detail"));
        var service = new RuntimeConfigurationService(store, reloader, resources,
                new ai.loomspan.sidecar.rest.RestRouteCatalogValidator(), executions,
                new ai.loomspan.sidecar.management.ManagementEditingState(),
                org.mockito.Mockito.mock(ai.loomspan.sidecar.storage.ConfigurationDraftStore.class));
        var base = new ConfigurationSnapshot(java.util.UUID.randomUUID(), null, 1,
                new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES), SnapshotStatus.PUBLISHED);

        var result = service.validate(base.configuration());

        assertThat(result.successful()).isFalse();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.sourceLabel()).isEqualTo("rest-routes.yaml");
            assertThat(issue.message()).isEqualTo("REST client staging failed");
            assertThat(issue.message()).doesNotContain("sensitive SSL configuration detail");
        });
    }

    @Test
    void warningOnlyFrameworkValidationPreservesIssueAndReleasesTemporaryResources() {
        var store = org.mockito.Mockito.mock(ConfigurationSnapshotStore.class);
        var reloader = org.mockito.Mockito.mock(SkillReloader.class);
        var resources = org.mockito.Mockito.mock(ai.loomspan.sidecar.rest.GenerationRestResources.class);
        var staged = org.mockito.Mockito.mock(ai.loomspan.sidecar.rest.GenerationRestResources.Resources.class);
        var executions = org.mockito.Mockito.mock(ai.loomspan.sidecar.execution.ExecutionCoordinator.class);
        org.mockito.Mockito.when(reloader.validate(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(new ai.loomspan.api.SkillValidationResult(List.of(
                        new ai.loomspan.api.SkillValidationIssue(ai.loomspan.api.SkillValidationIssue.Severity.WARNING,
                                "warning.yaml", "checkedSkill", "description", "Needs review")), List.of()));
        org.mockito.Mockito.when(resources.prepare(org.mockito.ArgumentMatchers.anyString())).thenReturn(staged);
        org.mockito.Mockito.when(staged.routes()).thenReturn(new ai.loomspan.sidecar.rest.RestRouteConfiguration(
                "rest-routes.yaml", java.util.Map.of(), java.util.Map.of()));
        var service = new RuntimeConfigurationService(store, reloader, resources,
                new ai.loomspan.sidecar.rest.RestRouteCatalogValidator(), executions,
                new ai.loomspan.sidecar.management.ManagementEditingState(),
                org.mockito.Mockito.mock(ai.loomspan.sidecar.storage.ConfigurationDraftStore.class));
        var result = service.validate(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
        assertThat(result.successful()).isTrue();
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(ConfigurationValidationIssue.Severity.WARNING);
            assertThat(issue.sourceLabel()).isEqualTo("warning.yaml");
            assertThat(issue.skillName()).isEqualTo("checkedSkill");
            assertThat(issue.location()).isEqualTo("description");
        });
        org.mockito.Mockito.verify(resources).release(staged);
        org.mockito.Mockito.verify(reloader, org.mockito.Mockito.never())
                .prepare(org.mockito.ArgumentMatchers.anyCollection());
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
    void editingCaptureWaitsForPublicationTransition() throws Exception {
        Path path = directory.resolve("pending-editing.db");
        Path skill = directory.resolve("runtimeA.yaml");
        java.nio.file.Files.writeString(skill, restDocument("runtimeA").yaml());
        ai.loomspan.sidecar.support.SidecarApplicationFixture.seedDatabase(path, List.of(skill), routes("runtimeA"));
        try (var context = start(path);
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var service = context.getBean(RuntimeConfigurationService.class);
            var store = context.getBean(ConfigurationSnapshotStore.class);
            var a = store.current();
            var candidate = validDraft(service, a, "pendingRest");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            service.hooks(new RuntimeConfigurationService.Hooks() {
                @Override public void beforeFrameworkPublish() {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                }
            });
            var publishing = workers.submit(() -> context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(candidate));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(store.current().localId()).isNotEqualTo(a.localId());
            var captured = workers.submit(() -> service.withEditingState(false, snapshot -> snapshot));
            assertThatThrownBy(() -> captured.get(150, TimeUnit.MILLISECONDS))
                    .isInstanceOf(java.util.concurrent.TimeoutException.class);
            release.countDown();
            var b = publishing.get(5, TimeUnit.SECONDS);
            assertThat(captured.get(5, TimeUnit.SECONDS).localId()).isEqualTo(b.localId());
        }
    }

    @Test
    void exportCaptureWaitsForPublicationAndSurvivesLaterPruning() throws Exception {
        try (var context = start(directory.resolve("export-capture.db"), 1);
                var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var service = context.getBean(RuntimeConfigurationService.class);
            var store = context.getBean(ConfigurationSnapshotStore.class);
            var initial = service.publishedSnapshot();
            var draft = validDraft(service, initial, "exportedRest");
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            service.hooks(new RuntimeConfigurationService.Hooks() {
                @Override public void beforeFrameworkPublish() {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                }
            });
            var publishing = workers.submit(() -> context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(draft));
            try {
                assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
                var capturing = workers.submit(service::publishedSnapshot);
                assertThat(capturing.isDone()).isFalse();
                release.countDown();
                var accepted = publishing.get(5, TimeUnit.SECONDS);
                var captured = capturing.get(5, TimeUnit.SECONDS);
                assertThat(captured.localId()).isEqualTo(accepted.localId());
                assertThat(captured.configuration()).isEqualTo(accepted.configuration());
                service.hooks(new RuntimeConfigurationService.Hooks() {});
                var next = validDraft(service, store.current(), "afterExport");
                context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(next);
                assertThat(store.findByLocalId(captured.localId())).isNull();
                Path bundle = ConfigurationBundleV1.write(captured);
                try {
                    var read = ConfigurationBundleV1.read(bundle);
                    assertThat(read.sourceSnapshotId()).isEqualTo(captured.localId());
                    assertThat(read.configuration()).isEqualTo(captured.configuration());
                } finally { java.nio.file.Files.deleteIfExists(bundle); }
            } finally { release.countDown(); service.hooks(new RuntimeConfigurationService.Hooks() {}); }
        }
    }

    @Test
    void bundleTransfersCompleteAuthoredContentBetweenInstancesWithLocalProvenance() throws Exception {
        Path bundle;
        ConfigurationSnapshot source;
        try (var a = start(directory.resolve("import-source.db"))) {
            var runtimeA = a.getBean(RuntimeConfigurationService.class);
            source = a.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(validDraft(runtimeA, runtimeA.publishedSnapshot(), "echoRest"));
            bundle = ConfigurationBundleV1.write(source);
        }
        try {
            var imported = ConfigurationBundleV1.read(bundle);
            try (var b = start(directory.resolve("import-destination.db"))) {
                var runtimeB = b.getBean(RuntimeConfigurationService.class);
                var storeB = b.getBean(ConfigurationSnapshotStore.class);
                var initial = runtimeB.publishedSnapshot();
                assertThat(initial.localId()).isNotEqualTo(source.localId());
                for (int i = 0; i < 2; i++) {
                    var before = runtimeB.publishedSnapshot();
                    var local = b.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(
                            validDraft(runtimeB, before, "echoRest"), imported.sourceSnapshotId());
                    assertThat(local.localId()).isNotEqualTo(source.localId()).isNotEqualTo(before.localId());
                    assertThat(local.sourceId()).isEqualTo(source.localId());
                    assertThat(local.configuration()).isEqualTo(source.configuration());
                    assertThat(runtimeB.inspect().publishedId()).isEqualTo(local.localId());
                    assertThat(storeB.current().localId()).isEqualTo(local.localId());
                }
                assertThat(storeB.history()).hasSize(3);
                assertThat(storeB.history()).noneMatch(snapshot -> snapshot.localId().equals(source.localId()));
            }
        } finally { java.nio.file.Files.deleteIfExists(bundle); }
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
            var fixture = context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class);
            var firstAdmission = fixture.admission(first);
            var secondAdmission = fixture.admission(second);
            var entered = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            service.hooks(new RuntimeConfigurationService.Hooks() {
                @Override public void beforeCommit() {
                    entered.countDown();
                    try { if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                    catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
                }
            });
            var accepted = workers.submit(() -> service.publish(() -> firstAdmission));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            first.replaceContent(new ManagedConfiguration(List.of(), ConfigurationSnapshotStore.EMPTY_REST_ROUTES));
            var waiting = workers.submit(() -> {
                try { service.publish(() -> secondAdmission); return "accepted"; }
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
                var b = publishRouteOnly(context, service, store.current(), 10);
                admittedB = handoff.handoff("protectedRest", Map.of());
                var c = publishRouteOnly(context, service, store.current(), 11);
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
                var d = publishRouteOnly(context, service, store.current(), 12);
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

    private ConfigurationSnapshot publishRouteOnly(org.springframework.context.ConfigurableApplicationContext context, RuntimeConfigurationService service,
            ConfigurationSnapshot base, int port) {
        var draft = new ConfigurationDraft(base);
        draft.replaceContent(new ManagedConfiguration(base.configuration().skillDocuments(),
                routes("protectedRest").replace("127.0.0.1:9", "127.0.0.1:" + port)));
        assertThat(ai.loomspan.sidecar.support.TestDrafts.validate(service, draft).successful()).isTrue();
        return context.getBean(ai.loomspan.sidecar.support.RuntimePublicationFixture.class).publish(draft);
    }

    private int snapshotCount(org.springframework.context.ConfigurableApplicationContext context) {
        return new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class))
                .queryForObject("SELECT COUNT(*) FROM configuration_snapshot", Integer.class);
    }

    private ConfigurationDraft validDraft(RuntimeConfigurationService service, ConfigurationSnapshot base, String name) {
        var draft = new ConfigurationDraft(base);
        draft.replaceContent(new ManagedConfiguration(List.of(restDocument(name)), routes(name)));
        assertThat(ai.loomspan.sidecar.support.TestDrafts.validate(service, draft).successful()).isTrue();
        return draft;
    }

    private java.util.UUID latestOtherThan(javax.sql.DataSource source, java.util.UUID a) {
        var ids = new org.springframework.jdbc.core.JdbcTemplate(source).queryForList(
                "SELECT local_id FROM configuration_snapshot ORDER BY submission_sequence DESC", String.class);
        return ids.stream().map(java.util.UUID::fromString).filter(id -> !id.equals(a)).findFirst().orElseThrow();
    }

    private ai.loomspan.sidecar.management.ManagementUserDetailsService.Principal editor(
            org.springframework.context.ConfigurableApplicationContext context, String email) {
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(context.getBean(javax.sql.DataSource.class));
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, "editor", "fixture-hash", System.currentTimeMillis());
        return (ai.loomspan.sidecar.management.ManagementUserDetailsService.Principal)
                context.getBean(ai.loomspan.sidecar.management.ManagementUserDetailsService.class)
                        .loadUserByUsername(email);
    }

    private jakarta.servlet.http.HttpSession session(String id) {
        var session = org.mockito.Mockito.mock(jakarta.servlet.http.HttpSession.class);
        org.mockito.Mockito.when(session.getId()).thenReturn(id);
        return session;
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
