package ai.loomspan.sidecar.rest;

import ai.loomspan.api.SkillInvocationHandoff;
import ai.loomspan.api.SkillReloader;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV1;
import ai.loomspan.sidecar.storage.ConfigurationDraft;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.support.SidecarApplicationFixture;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.ConfigurableEnvironment;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RestGenerationIntegrationTest {
    @TempDir Path directory;

    @TestConfiguration(proxyBeanMethods = false)
    static class DestinationBinding {
        @Bean @Primary RestRouteLoader destinationRouteLoader(ai.loomspan.sidecar.config.RestRoutesProperties properties,
                ConfigurableEnvironment environment, ObjectProvider<SslBundles> bundles) {
            return new RestRouteLoader(properties, environment, bundles.getIfAvailable(),
                    name -> "TARGET_URL".equals(name) ? environment.getProperty("test.target-url") : null);
        }
    }

    @Test
    void transferredPlaceholderUsesEachDestinationsOwnBinding() throws Exception {
        HttpServer first = server("first");
        HttpServer second = server("second");
        Path bundle = null;
        try {
            Path skill = directory.resolve("placeholder-skill.yaml");
            Files.writeString(skill, """
                    name: echoRest
                    description: Transfer fixture.
                    rest: true
                    input_schema: {type: object, properties: {}}
                    """);
            String authored = routes(first.getAddress().getPort()).replace(
                    "http://127.0.0.1:" + first.getAddress().getPort(), "${TARGET_URL}");
            Path sourceDb = directory.resolve("placeholder-source.db");
            SidecarApplicationFixture.seedDatabase(sourceDb, List.of(skill), authored);
            ai.loomspan.sidecar.storage.ConfigurationSnapshot source;
            try (var sourceContext = boundContext(sourceDb, first.getAddress().getPort())) {
                var sourceRuntime = sourceContext.getBean(RuntimeConfigurationService.class);
                source = sourceRuntime.publishedSnapshot();
                bundle = ConfigurationBundleV1.write(source);
                assertThat(invoke(sourceContext)).isEqualTo("first");
            }
            Path destinationDb = directory.resolve("placeholder-destination.db");
            try (var destination = boundContext(destinationDb, second.getAddress().getPort())) {
                var runtime = destination.getBean(RuntimeConfigurationService.class);
                var imported = ConfigurationBundleV1.read(bundle);
                var local = runtime.importConfiguration(imported.configuration(), imported.sourceSnapshotId(),
                        runtime.publishedSnapshot().localId(), null, System::currentTimeMillis);
                assertThat(local.sourceId()).isEqualTo(source.localId());
                assertThat(local.configuration().restRoutesYaml()).isEqualTo(authored).contains("${TARGET_URL}");
                assertThat(invoke(destination)).isEqualTo("second");
            }
        } finally {
            if (bundle != null) Files.deleteIfExists(bundle);
            first.stop(0); second.stop(0);
        }
    }

    private org.springframework.context.ConfigurableApplicationContext boundContext(Path database, int port) {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class, DestinationBinding.class)
                .web(WebApplicationType.NONE).run(
                        "--loomspan-sidecar.storage.database-path=" + database,
                        "--loomspan-sidecar.url-variables=TARGET_URL",
                        "--test.target-url=http://127.0.0.1:" + port,
                        "--loomspan.observability.enabled=false",
                        "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                        "--loomspan-sidecar.auth.jwt.audience=sidecar",
                        "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem");
    }

    private String invoke(org.springframework.context.ApplicationContext context) {
        var reloader = context.getBean(SkillReloader.class);
        return context.getBean(ai.loomspan.api.RestSkillHandler.class).handle(
                new ai.loomspan.api.RestSkillInvocation("echoRest", Map.of(), reloader.snapshot().generationId()));
    }

    @Test
    void clientConstructionFaultLeavesNoStagedGeneration() {
        var factory = new org.springframework.beans.factory.support.DefaultListableBeanFactory();
        var bundles = org.mockito.Mockito.mock(org.springframework.boot.ssl.SslBundles.class);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        org.mockito.Mockito.when(bundles.getBundle("unstable")).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) return org.mockito.Mockito.mock(org.springframework.boot.ssl.SslBundle.class);
            throw new IllegalStateException("injected client construction failure");
        });
        factory.registerSingleton("sslBundles", bundles);
        var provider = factory.getBeanProvider(org.springframework.boot.ssl.SslBundles.class);
        var loader = new RestRouteLoader(new ai.loomspan.sidecar.config.RestRoutesProperties(),
                new org.springframework.mock.env.MockEnvironment(), provider);
        var registry = new GenerationRestResources(loader, provider);
        try {
            assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> registry.prepare("""
                    targets:
                      first: {base-url: http://localhost, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                      second: {base-url: https://localhost, auth: {mode: none}, ssl-bundle: unstable, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
                    routes: {}
                    """))).isInstanceOf(IllegalStateException.class);
            assertThat(registry.ownedCount()).isZero();
            assertThat(registry.protectedIds()).isEmpty();
        } finally { registry.stop(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void pendingAdmissionUsesOldRestResourcesAfterPublicationAndRetiresAfterRelease(boolean imported) throws Exception {
        HttpServer first = server("first");
        HttpServer second = server("second");
        try {
            Path skill = directory.resolve("echoRest.yaml");
            Files.writeString(skill, """
                    name: echoRest
                    description: Generation fixture.
                    rest: true
                    input_schema: {type: object, properties: {}}
                    """);
            Path database = directory.resolve(imported ? "sidecar-import.db" : "sidecar-publish.db");
            SidecarApplicationFixture.seedDatabase(database, List.of(skill), routes(first.getAddress().getPort()));
            try (var context = new SpringApplicationBuilder(LoomspanSidecarApplication.class)
                    .web(WebApplicationType.NONE).run(
                            "--loomspan-sidecar.storage.database-path=" + database,
                            "--loomspan.observability.enabled=false",
                            "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                            "--loomspan-sidecar.auth.jwt.audience=sidecar",
                            "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem")) {
                var store = context.getBean(ConfigurationSnapshotStore.class);
                var service = context.getBean(RuntimeConfigurationService.class);
                var registry = context.getBean(GenerationRestResources.class);
                var reloader = context.getBean(SkillReloader.class);
                var a = store.current();
                assertThat(registry.ownedCount()).isEqualTo(1);
                var validationOnly = new ConfigurationDraft(a);
                validationOnly.replaceContent(new ManagedConfiguration(a.configuration().skillDocuments(),
                        routes(second.getAddress().getPort())));
                assertThat(service.validate(validationOnly).successful()).isTrue();
                assertThat(registry.ownedCount()).isEqualTo(1);
                var oldGeneration = reloader.snapshot().generationId();
                var admitted = context.getBean(SkillInvocationHandoff.class).handoff("echoRest", Map.of());
                try {
                    var draft = new ConfigurationDraft(a);
                    draft.replaceContent(new ManagedConfiguration(a.configuration().skillDocuments(),
                            routes(second.getAddress().getPort())));
                    assertThat(service.validate(draft).successful()).isTrue();
                    var b = imported
                            ? service.importConfiguration(draft.freeze().configuration(), java.util.UUID.randomUUID(),
                                    a.localId(), null, System::currentTimeMillis)
                            : service.publish(draft::validatedCandidate);
                    assertThat(registry.protectedIds()).contains(a.localId(), b.localId());
                    assertThat(registry.require(oldGeneration).isClosed()).isFalse();
                    assertThat(admitted.invoke()).isEqualTo("first");
                    assertThat(context.getBean(ai.loomspan.api.RestSkillHandler.class).handle(
                            new ai.loomspan.api.RestSkillInvocation("echoRest", Map.of(), reloader.snapshot().generationId())))
                            .isEqualTo("second");
                } finally { admitted.release(); }
                long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
                while ((registry.protectedIds().contains(a.localId()) || registry.ownedCount() != 1)
                        && System.nanoTime() < deadline) Thread.sleep(10);
                assertThat(registry.protectedIds()).doesNotContain(a.localId());
                assertThat(registry.ownedCount()).isEqualTo(1);
            }
        } finally { first.stop(0); second.stop(0); }
    }

    private HttpServer server(String value) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/echo", exchange -> {
            byte[] body = value.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        return server;
    }

    private String routes(int port) {
        return """
                targets:
                  callback: {base-url: 'http://127.0.0.1:%d', auth: {mode: none}, connect-timeout: 1s, read-timeout: 2s, max-response-size: 1KB}
                routes:
                  echoRest: {target: callback, method: GET, path: /echo}
                """.formatted(port);
    }
}
