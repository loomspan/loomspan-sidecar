package ai.loomspan.sidecar.rest;

import ai.loomspan.api.SkillCatalog;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestRouteValidationIntegrationTest {
    @TempDir Path directory;
    private static final String VALID = """
            targets:
              callback: {base-url: http://localhost/api, auth: {mode: none}, connect-timeout: 1s, read-timeout: 1s, max-response-size: 1KB}
            routes:
              leaf: {target: callback, method: GET, path: /echo}
            """;

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "max-response-size: 1KB|max-response-size: 0B|max-response-size",
            "max-response-size: 1KB|max-response-size: 3GB|max-response-size",
            "connect-timeout: 1s|connect-timeout: 0s|connect-timeout",
            "read-timeout: 1s|read-timeout: -1s|read-timeout",
            "read-timeout: 1s|read-timeout: nonsense|read-timeout",
            "mode: none|mode: unsupported|authentication mode",
            "mode: none|mode: static|static authentication requires headers",
            "max-response-size: 1KB|max-response-size: 1KB, ssl-bundle: missing|SSL bundle",
            "path: /echo|path: //evil.example/echo|without authority",
            "path: /echo|path: relative|absolute path",
            "path: /echo|path: /../escape|traversal",
            "path: /echo|path: /%252fescape|encoded separator"
    })
    void rejectsInvalidSettingsInApplication(String original, String replacement, String expected) throws Exception {
        assertInvalid(VALID.replace(original, replacement), expected);
    }

    @Test
    void rejectsMissingUnreadableMalformedAndDuplicateRouteFiles() throws Exception {
        Path missing = directory.resolve("missing.yaml");
        assertStartupFailure(missing, "missing or unreadable");
        // A directory is not a readable route document on either Windows or Linux.
        assertStartupFailure(directory, "Invalid REST routes");
        assertInvalid("targets: [broken", "malformed YAML");
        assertInvalid(VALID + "  leaf: {target: callback, method: GET, path: /again}\n", "duplicate mapping key");
    }

    @Test
    void rejectsAnExplicitlyUnreadableResourceInApplication() {
        var application = builder().initializers(context -> context.addProtocolResolver((location, loader) -> {
            if (!location.equals("unreadable:routes")) return null;
            return new org.springframework.core.io.AbstractResource() {
                @Override public String getDescription() { return "unreadable route fixture"; }
                @Override public boolean exists() { return true; }
                @Override public boolean isReadable() { return false; }
                @Override public java.io.InputStream getInputStream() { throw new AssertionError("Unreadable resource was opened"); }
            };
        }));
        String[] arguments = arguments(directory);
        for (int index = 0; index < arguments.length; index++) {
            if (arguments[index].startsWith("--loomspan-sidecar.rest-routes-location=")) {
                arguments[index] = "--loomspan-sidecar.rest-routes-location=unreadable:routes";
            }
        }
        assertThatThrownBy(() -> {
            try (var ignored = application.run(arguments)) { }
        }).hasStackTraceContaining("missing or unreadable");
    }

    @Test
    void rejectsCatalogMismatchesInApplication() throws Exception {
        assertInvalid(VALID, "unknown skill");
        Files.writeString(directory.resolve("leaf.yml"), """
                name: leaf
                description: REST startup fixture.
                rest: true
                input_schema: {type: object, properties: {}}
                """);
        assertInvalid("targets: {}\nroutes: {}\n", "has no route");
        Files.writeString(directory.resolve("leaf.yml"), """
                name: leaf
                description: YAML startup fixture.
                model: fixture
                planning_mode: false
                """);
        assertInvalid(VALID, "non-REST skill");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void readinessRefusesTrafficUntilRegistrationAndRouteValidationComplete(boolean registration) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var starting = new AtomicReference<ConfigurableApplicationContext>();
        Path routes = directory.resolve("routes.yaml");
        Files.writeString(routes, VALID);
        byte[] skill = """
                name: leaf
                description: Gated registration fixture.
                rest: true
                input_schema: {type: object, properties: {}}
                """.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var source = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        source.createContext("/leaf.yml", exchange -> {
            if (exchange.getRequestMethod().equals("HEAD")) {
                exchange.getResponseHeaders().set("Content-Length", Integer.toString(skill.length));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }
            if (registration) {
                entered.countDown();
                try { release.await(10, TimeUnit.SECONDS); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
            }
            exchange.sendResponseHeaders(200, skill.length);
            try (var output = exchange.getResponseBody()) { output.write(skill); }
        });
        source.start();
        String[] arguments = arguments(routes);
        arguments[0] = "--loomspan.skills.locations=http://127.0.0.1:" + source.getAddress().getPort() + "/leaf.yml";
        var builder = builder().initializers(context -> {
            starting.set(context);
            context.addBeanFactoryPostProcessor(factory -> factory.addBeanPostProcessor(new BeanPostProcessor() {
                @Override public Object postProcessBeforeInitialization(Object bean, String name) {
                    if (!registration && bean instanceof RestRouteCatalogValidator) {
                        entered.countDown();
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("Startup gate timed out");
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(failure);
                        }
                    }
                    return bean;
                }
            }));
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var startup = executor.submit(() -> builder.run(arguments));
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(starting.get().getBean(ApplicationAvailability.class).getReadinessState())
                        .isEqualTo(ReadinessState.REFUSING_TRAFFIC);
                assertThat(startup.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            try (var context = startup.get(10, TimeUnit.SECONDS)) {
                assertThat(context.getBean(SkillCatalog.class).skill("leaf")).isPresent();
                assertThat(context.getBean(ApplicationAvailability.class).getReadinessState())
                        .isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
            }
        } finally {
            release.countDown();
            source.stop(0);
            var context = starting.get();
            if (context != null && context.isActive()) context.close();
        }
    }

    private void assertInvalid(String yaml, String expected) throws Exception {
        Path file = directory.resolve("routes.yaml");
        Files.writeString(file, yaml);
        assertStartupFailure(file, expected);
    }

    private void assertStartupFailure(Path file, String expected) {
        var readiness = new java.util.concurrent.CopyOnWriteArrayList<ReadinessState>();
        var application = builder().listeners(event -> {
            if (event instanceof org.springframework.boot.availability.AvailabilityChangeEvent<?> change
                    && change.getState() instanceof ReadinessState state) readiness.add(state);
        });
        assertThatThrownBy(() -> {
            try (var ignored = application.run(arguments(file))) { }
        }).hasStackTraceContaining(expected);
        assertThat(readiness).doesNotContain(ReadinessState.ACCEPTING_TRAFFIC);
    }

    private SpringApplicationBuilder builder() {
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class).web(WebApplicationType.NONE);
    }

    private String[] arguments(Path routes) {
        return new String[] {
                "--loomspan.skills.locations=" + directory.toUri() + "*.yml",
                "--loomspan-sidecar.storage.database-path=" + directory.resolve("sidecar.db"),
                "--loomspan.observability.enabled=false",
                "--loomspan.connections.fixture.driver=openai",
                "--loomspan.connections.fixture.base-url=http://127.0.0.1:9/v1",
                "--loomspan.connections.fixture.api-key=unused",
                "--loomspan.models.fixture.connection=fixture",
                "--loomspan.models.fixture.provider-model=unused",
                "--loomspan-sidecar.rest-routes-location=" + routes.toUri(),
                "--loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
                "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"};
    }
}
