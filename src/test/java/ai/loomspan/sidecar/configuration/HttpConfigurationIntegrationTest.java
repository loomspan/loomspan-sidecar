package ai.loomspan.sidecar.configuration;

import ai.loomspan.api.*;
import ai.loomspan.sidecar.LoomspanSidecarApplication;
import ai.loomspan.sidecar.storage.*;
import ai.loomspan.sidecar.management.ManagementEditingService;
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

    @Test void managedValuesFollowPublicationRollbackAndRestartWhileAdmittedWorkKeepsItsGeneration() throws Exception {
        var seen = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            seen.add(path + ":" + exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] bytes = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        Path database = directory.resolve("captured.db");
        int port = server.getAddress().getPort();
        String fixed = execution(port, "auth: {mode: fixed}, headers: { Authorization: { $var: service.token } }");
        String noHeader = execution(port, "auth: {mode: none}");
        UUID a;
        UUID rolledBack;
        String email = "rollback@fixture.test";
        try {
            try (var context = start(database)) {
                context.getBean(org.springframework.jdbc.core.JdbcTemplate.class).update(
                        "INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,'editor',1,'fixture',?)",
                        email, System.currentTimeMillis());
                var editor = editor(context, email);
                var grant = editor.acquire();
                var draft = editor.save(grant, "{target: app, method: GET, path: { $var: records.a }}", fixed);
                draft = editor.value(grant, draft, "records.a", "/records/a");
                draft = editor.value(grant, draft, "service.token", "v1");
                a = editor.publish(grant, draft).localId();
                invoke(context, "/records/a");
                grant = editor.acquire();
                draft = editor.save(grant, "{target: app, method: GET, path: { $var: records.b }}", fixed);
                draft = editor.value(grant, draft, "service.token", "v2");
                assertThat(editor.validate(grant, draft).validation().issues()).anySatisfy(issue -> {
                    assertThat(issue.location()).isEqualTo("config.path");
                    assertThat(issue.message()).contains("missing-value", "records.b");
                });
                invoke(context, "/records/a");
            }
            try (var context = start(database)) {
                var editor = editor(context, email);
                var grant = editor.acquire();
                invoke(context, "/records/a");
                var draft = editor.value(grant, grant.draft(), "records.b", "/records/b");
                var admitted = context.getBean(SkillInvocationHandoff.class).handoff("call", Map.of());
                try {
                    editor.publish(grant, draft);
                    assertThat(context.getBean(ConfigurationGenerationRegistry.class).snapshotId(admitted.generationId()))
                            .isEqualTo(a);
                    invoke(context, "/records/b");
                    grant = editor.acquire();
                    rolledBack = editor.publish(grant, editor.rollback(grant, a)).localId();
                    invoke(context, "/records/a");
                    assertThat(admitted.invoke()).isEqualTo("/records/a");
                } finally { admitted.release(); }
            }
            try (var context = start(database)) {
                assertThat(context.getBean(RuntimeConfigurationService.class).publishedSnapshot().localId())
                        .isEqualTo(rolledBack);
                invoke(context, "/records/a");
                var editor = editor(context, email);
                var grant = editor.acquire();
                var withoutReference = editor.save(grant, "{target: app, method: GET, path: /records/unreferenced}", noHeader);
                assertThat(editor.publish(grant, withoutReference).configuration().credentialIdentifiers())
                        .contains("service.token");
                invoke(context, "/records/unreferenced");
                grant = editor.acquire();
                editor.publish(grant, editor.rollback(grant, a));
                invoke(context, "/records/a");

                grant = editor.acquire();
                var removal = editor.save(grant, "{target: app, method: GET, path: /records/removed}", noHeader);
                removal = editor.editing().removeCredential(editor.session(), editor.user(), grant.editingSessionId(),
                        grant.generation(), removal.draftId(), removal.revision(), removal.baseSnapshotId(), "service.token");
                assertThat(editor.publish(grant, removal).configuration().credentialIdentifiers())
                        .doesNotContain("service.token");
                grant = editor.acquire();
                assertThat(editor.rollbackService().review(a).validation().successful()).isFalse();
                var rollback = editor.rollback(grant, a);
                var failed = editor.validate(grant, rollback).validation();
                assertThat(failed.successful()).isFalse();
                assertThat(failed.issues()).anySatisfy(issue -> {
                    assertThat(issue.message()).contains("missing-value", "service.token");
                    assertThat(issue.location()).endsWith("headers.Authorization");
                });
                assertThat(editor.editing().read(editor.session(), editor.user()).draftId()).isEqualTo(rollback.draftId());
                invoke(context, "/records/removed");
                editor.publish(grant, editor.value(grant, rollback, "service.token", "v3"));
                invoke(context, "/records/a");
            }
            assertThat(seen).containsExactly("/records/a:v1", "/records/a:v1", "/records/a:v1",
                    "/records/b:v2", "/records/a:v2", "/records/a:v1", "/records/a:v2",
                    "/records/unreferenced:null", "/records/a:v2", "/records/removed:null", "/records/a:v3");
        } finally { server.stop(0); }
    }

    private static void invoke(ConfigurableApplicationContext context, String path) {
        assertThat(context.getBean(SkillTemplate.class).invoke("call", Map.of())).isEqualTo(path);
    }

    private record Editor(ai.loomspan.sidecar.management.ManagementEditingService editing,
            ai.loomspan.sidecar.management.ManagementConfigurationRollbackService rollbackService,
            org.springframework.mock.web.MockHttpSession session,
            ai.loomspan.sidecar.management.ManagementUserDetailsService.Principal user) {
        ManagementEditingService.Grant acquire() {
            return editing.acquire(session, user, "HTTP lifecycle", false, false);
        }
        ManagementEditingService.Draft save(ManagementEditingService.Grant grant, String operation, String yaml) {
            var draft = grant.draft();
            return editing.save(session, user, grant.editingSessionId(), grant.generation(), draft.draftId(),
                    draft.revision(), draft.baseSnapshotId(), new ManagedConfiguration(List.of(skill(operation)), yaml));
        }
        ManagementEditingService.Draft value(ManagementEditingService.Grant grant, ManagementEditingService.Draft draft,
                String name, String value) {
            return editing.replaceCredential(session, user, grant.editingSessionId(), grant.generation(), draft.draftId(),
                    draft.revision(), draft.baseSnapshotId(), name, value);
        }
        ManagementEditingService.Draft validate(ManagementEditingService.Grant grant, ManagementEditingService.Draft draft) {
            return editing.validate(session, user, grant.editingSessionId(), grant.generation(), draft.draftId(),
                    draft.revision(), draft.baseSnapshotId());
        }
        ConfigurationSnapshot publish(ManagementEditingService.Grant grant, ManagementEditingService.Draft draft) {
            var validation = validate(grant, draft).validation();
            assertThat(validation.successful()).as(validation.toString()).isTrue();
            return editing.publish(session, user, grant.editingSessionId(), grant.generation(), draft.draftId(),
                    draft.revision(), draft.baseSnapshotId());
        }
        ManagementEditingService.Draft rollback(ManagementEditingService.Grant grant, UUID source) {
            var draft = grant.draft();
            return rollbackService.load(session, user, source, grant.editingSessionId(), grant.generation(),
                    draft.draftId(), draft.revision(), draft.baseSnapshotId());
        }
    }

    private Editor editor(ConfigurableApplicationContext context, String email) {
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("management.activity", System.currentTimeMillis());
        var user = (ai.loomspan.sidecar.management.ManagementUserDetailsService.Principal)
                context.getBean(ai.loomspan.sidecar.management.ManagementUserDetailsService.class).loadUserByUsername(email);
        return new Editor(context.getBean(ManagementEditingService.class),
                context.getBean(ai.loomspan.sidecar.management.ManagementConfigurationRollbackService.class), session, user);
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

    @Test void fileStartupResolvesHeadersAndOperationPathPerInstallation() throws Exception {
        record Request(String path, List<String> accept, List<String> region, List<String> authorization) {}
        var seen = new java.util.concurrent.ConcurrentLinkedQueue<Request>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            var headers = exchange.getRequestHeaders();
            String path = exchange.getRequestURI().getPath();
            seen.add(new Request(path, List.copyOf(headers.getOrDefault("Accept", List.of())),
                    List.copyOf(headers.getOrDefault("X-Region", List.of())),
                    List.copyOf(headers.getOrDefault("Authorization", List.of()))));
            byte[] bytes = path.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            Path skill = directory.resolve("records.yaml");
            Files.writeString(skill, skill("{target: app, method: GET, path: {$var: records.lookup-path}, pathParams: {recordId: /recordId}}").yaml());
            Path settings = directory.resolve("records.yml");
            Files.writeString(settings, execution(server.getAddress().getPort(),
                    "auth: {mode: fixed}, headers: {Accept: application/json, X-Region: {$var: app.region}, "
                            + "Authorization: {$var: service.authorization}}"));

            record Installation(String region, String version, String authorization) {}
            for (var installation : List.of(new Installation("west", "v2", "Basic fixture-west"),
                    new Installation("east", "v3", "Token fixture-east"))) {
                String path = "/" + installation.version() + "/records/";
                try (var context = start(directory.resolve(installation.region() + ".db"),
                        "--loomspan-sidecar.configuration.mode=file", "--spring.config.additional-location=" + settings.toUri(),
                        "--loomspan.skills.locations=" + skill.toUri(), "--app.region=" + installation.region(),
                        "--records.lookup-path=" + path + "{recordId}", "--service.authorization=" + installation.authorization())) {
                    assertThat(context.getBean(SkillTemplate.class).invoke("call", Map.of("recordId", "42")))
                            .isEqualTo(path + "42");
                }
            }
            assertThat(seen).containsExactly(
                    new Request("/v2/records/42", List.of("application/json"), List.of("west"), List.of("Basic fixture-west")),
                    new Request("/v3/records/42", List.of("application/json"), List.of("east"), List.of("Token fixture-east")));
        } finally { server.stop(0); }
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

    @Test void missingManagedValuesAndOmittedTargetsDoNotInheritSpringOrEarlierPublication() {
        try (var context = start(directory.resolve("omission.db"), "--service.key=host-value")) {
            var operation = skill("{target: app, method: GET, path: /}");
            String settings = execution(1, "auth: {mode: fixed}, headers: {Authorization: { $var: service.key }}");
            publish(context, operation, settings,
                    SidecarApplicationFixture.encrypt(Map.of("service.key", "first-publication-value")));
            var runtime = context.getBean(RuntimeConfigurationService.class);
            var missing = runtime.validate(new ManagedConfiguration(List.of(operation), settings));
            assertThat(missing.successful()).isFalse();
            assertThat(missing.issues()).anySatisfy(issue -> {
                assertThat(issue.message()).contains("missing-value", "service.key").doesNotContain("host-value");
                assertThat(issue.location()).endsWith("headers.Authorization");
            });
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
