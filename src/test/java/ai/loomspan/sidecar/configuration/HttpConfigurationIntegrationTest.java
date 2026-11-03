package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.*;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.storage.*;
import ai.loomspan.sidecar.support.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static org.assertj.core.api.Assertions.*;

/** Composed Sidecar management and startup coverage, using only public Framework APIs. */
class HttpConfigurationIntegrationTest {
    @TempDir Path directory;

    @Test void invalidHttpConfigurationPublishesRestoresAndFailsOnlyOnInvocation() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> { requests.incrementAndGet(); exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.start();
        Path database = directory.resolve("invalid.db");
        try {
            try (var context = start(database)) {
                var runtime = context.getBean(RuntimeConfigurationService.class);
                for (String operation : List.of(
                        "{target: missing, method: GET, path: /}",
                        "{target: app, method: GET, path: /, body: input}",
                        "{target: app, method: POST, path: /, queryParams: {q: bad-pointer}}")) {
                    var invalid = publish(context, skill(operation), execution(server.getAddress().getPort(), "auth: {mode: none}"), List.of());
                    assertThat(context.getBean(ai.loomspan.sidecar.management.ManagementConfigurationRollbackService.class)
                            .review(invalid.localId()).validation().successful()).isTrue();
                    Path archive = ai.loomspan.sidecar.bundle.ConfigurationBundleV4.write(invalid);
                    try {
                        var upload = new org.springframework.mock.web.MockMultipartFile("bundle", "candidate.zip",
                                "application/zip", Files.readAllBytes(archive));
                        assertThat(context.getBean(ai.loomspan.sidecar.management.ManagementConfigurationImportService.class)
                                .review(upload).validation().successful()).isTrue();
                    } finally { Files.deleteIfExists(archive); }
                    assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("call", Map.of()))
                            .hasMessageContaining("HTTP");
                    assertThat(runtime.current().published()).isNotNull();
                    assertThat(requests).hasValue(0);
                }
                publish(context, skill("{target: app, method: GET, path: /}"),
                        execution(server.getAddress().getPort(), "read-timeout: invalid"), List.of());
            }
            try (var context = start(database)) {
                assertThat(context.getBean(RuntimeConfigurationService.class).current().published()).isNotNull();
                assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("call", Map.of())).hasMessageContaining("HTTP");
                assertThat(requests).hasValue(0);
            }
        } finally { server.stop(0); }
    }

    @Test void retainedAdmissionUsesOldTargetAndCredentialWhileNewAndRestartUseReplacement() throws Exception {
        var seen = new ArrayList<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            synchronized (seen) { seen.add(exchange.getRequestURI().getPath() + ":" + exchange.getRequestHeaders().getFirst("Authorization")); }
            byte[] bytes = exchange.getRequestURI().getPath().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        Path database = directory.resolve("captured.db");
        String settings = execution(server.getAddress().getPort(), "auth: {mode: fixed, header-refs: {Authorization: service.key}}");
        UUID replacement;
        try {
            try (var context = start(database)) {
                var first = publish(context, skill("{target: app, method: GET, path: /old}"), settings,
                        SidecarApplicationFixture.encrypt(Map.of("service.key", "old-fixture")));
                var admitted = context.getBean(SkillInvocationHandoff.class).handoff("call", Map.of());
                try {
                    replacement = publish(context, skill("{target: app, method: GET, path: /new}"), settings,
                            SidecarApplicationFixture.encrypt(Map.of("service.key", "new-fixture"))).localId();
                    var registry = context.getBean(ConfigurationGenerationRegistry.class);
                    assertThat(registry.snapshotId(admitted.generationId())).isEqualTo(first.localId());
                    assertThat(admitted.invoke()).isEqualTo("/old");
                    assertThat(context.getBean(SkillTemplate.class).invoke("call", Map.of())).isEqualTo("/new");
                } finally { admitted.release(); }
                var runtime = context.getBean(RuntimeConfigurationService.class);
                runtime.hooks(new RuntimeConfigurationService.Hooks() {
                    @Override public void beforeFrameworkPublish() { throw new IllegalStateException("injected"); }
                });
                assertThatThrownBy(() -> publish(context, skill("{target: app, method: GET, path: /failed}"), settings,
                        runtime.retainedCredentials(runtime.publishedSnapshot()))).hasMessageContaining("publication failed");
                assertThat(context.getBean(SkillTemplate.class).invoke("call", Map.of())).isEqualTo("/new");
            }
            try (var context = start(database, "--loomspan.executor-config.default.http.targets.poison.base-url=invalid")) {
                assertThat(context.getBean(RuntimeConfigurationService.class).publishedSnapshot().localId()).isEqualTo(replacement);
                assertThat(context.getBean(SkillTemplate.class).invoke("call", Map.of())).isEqualTo("/new");
            }
            assertThat(seen).containsExactly("/old:old-fixture", "/new:new-fixture", "/new:new-fixture", "/new:new-fixture");
        } finally { server.stop(0); }
    }

    @Test void fileStartupAcceptsInvalidHttpSettingsWithoutDomainChecks() throws Exception {
        Path skill = directory.resolve("call.yaml");
        Files.writeString(skill, skill("{target: missing, method: GET, path: /}").yaml());
        Path settings = directory.resolve("settings.yml");
        Files.writeString(settings, execution(1, "auth: {mode: none}"));
        try (var context = start(directory.resolve("file.db"), "--loomspan-sidecar.configuration.mode=file",
                "--spring.config.additional-location=" + settings.toUri(), "--loomspan.skills.locations=" + skill.toUri())) {
            assertThat(context.getBean(RuntimeConfigurationService.class).current().readOnly()).isTrue();
            assertThat(context.getBean(SkillReloader.class).snapshot("default").skill("default-call").orElseThrow().kind()).isEqualTo(SkillKind.JAVA);
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("call", Map.of())).hasMessageContaining("HTTP");
        }
    }

    @Test void fileInvocationUsesBootImportAndActiveProfileSelection() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = exchange.getRequestURI().getPath().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            Path skill = directory.resolve("profile-call.yaml");
            Files.writeString(skill, skill("{target: app, method: GET, path: /actual}").yaml());
            String initial = execution(server.getAddress().getPort(), "auth: {mode: none}");
            Path imported = directory.resolve("imported.yml");
            Files.writeString(imported, initial + "---\nspring:\n  config:\n    activate:\n      on-profile: chosen\n"
                    + initial.replace("'http://127.0.0.1:" + server.getAddress().getPort() + "'",
                            "'http://127.0.0.1:" + server.getAddress().getPort() + "/selected'"));
            Path settings = directory.resolve("profile-settings.yml");
            Files.writeString(settings, "spring:\n  config:\n    import: '" + imported.toUri() + "'\n");
            try (var context = start(directory.resolve("profile.db"), "--loomspan-sidecar.configuration.mode=file",
                    "--spring.profiles.active=chosen", "--spring.config.additional-location=" + settings.toUri(),
                    "--loomspan.skills.locations=" + skill.toUri())) {
                assertThat(context.getBean(SkillTemplate.class).invoke("call", Map.of())).isEqualTo("/selected/actual");
            }
        } finally { server.stop(0); }
    }

    @Test void missingHttpCredentialAndOmittedTargetsDoNotInheritEarlierPublication() {
        try (var context = start(directory.resolve("omission.db"))) {
            publish(context, skill("{target: app, method: GET, path: /}"), execution(1,
                    "auth: {mode: fixed, header-refs: {Authorization: service.key}}"),
                    SidecarApplicationFixture.encrypt(Map.of("service.key", "omitted-fixture")));
            publish(context, skill("{target: app, method: GET, path: /}"), execution(1,
                    "auth: {mode: fixed, header-refs: {Authorization: service.key}}"), List.of());
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("call", Map.of())).hasMessageContaining("credential reference");
            var runtime = context.getBean(RuntimeConfigurationService.class);
            var previous = runtime.publishedSnapshot().localId();
            assertThat(runtime.validate(new ManagedConfiguration(List.of(skill("{target: app, method: GET, path: /}")),
                    "loomspan: {}")).successful()).isFalse();
            assertThat(runtime.publishedSnapshot().localId()).isEqualTo(previous);
            publish(context, skill("{target: app, method: GET, path: /}"),
                    "loomspan: {executor-config: {default: {http: {targets: {}}}}}", List.of());
            assertThatThrownBy(() -> context.getBean(SkillTemplate.class).invoke("call", Map.of())).hasMessageContaining("HTTP");
        }
    }

    static SkillDocument skill(String operation) { return new SkillDocument("call.yaml", "name: call\ndescription: HTTP fixture\nexecutor: http\nconfig: " + operation + "\n"); }
    static String execution(int port, String settings) { return "loomspan: {executor-config: {default: {http: {targets: {app: {base-url: 'http://127.0.0.1:" + port + "', " + settings + "}}}}}}\n"; }
    static ConfigurationSnapshot publish(ConfigurableApplicationContext context, SkillDocument skill, String yaml, List<EncryptedCredential> credentials) {
        var runtime = context.getBean(RuntimeConfigurationService.class);
        var draft = new ConfigurationDraft(runtime.publishedSnapshot());
        draft.replaceContent(new ManagedConfiguration(List.of(skill), yaml));
        assertThat(TestDrafts.validate(runtime, draft, credentials).successful()).isTrue();
        return context.getBean(RuntimePublicationFixture.class).publish(draft, credentials);
    }
    private ConfigurableApplicationContext start(Path database, String... extra) {
        var args = new ArrayList<>(List.of("--loomspan-sidecar.storage.database-path=" + database,
                "--spring.main.banner-mode=off", "--logging.level.root=ERROR", "--loomspan.observability.enabled=false",
                "--loomspan-sidecar.trust.mode=file", "--loomspan-sidecar.auth.jwt.audience=sidecar",
                "--loomspan-sidecar.trust.file.jwks=src/test/resources/fixtures/jwks.json"));
        args.addAll(List.of(extra));
        return new SpringApplicationBuilder(LoomspanSidecarApplication.class, SidecarApplicationFixture.Credentials.class)
                .web(WebApplicationType.NONE).run(args.toArray(String[]::new));
    }
}
