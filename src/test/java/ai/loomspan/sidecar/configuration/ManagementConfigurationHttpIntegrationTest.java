package ai.loomspan.sidecar.configuration;

import ai.loomspan.sidecar.configuration.RuntimeConfigurationService;
import ai.loomspan.sidecar.bundle.ConfigurationBundleV1;
import ai.loomspan.sidecar.storage.ManagedConfiguration;
import ai.loomspan.sidecar.storage.ConfigurationSnapshot;
import ai.loomspan.sidecar.storage.SnapshotStatus;
import ai.loomspan.sidecar.storage.ConfigurationSnapshotStore;
import ai.loomspan.sidecar.management.ManagementEditingState;
import ai.loomspan.sidecar.support.JwtTestTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0", "server.servlet.session.cookie.secure=false",
        "loomspan-sidecar.auth.jwt.issuer-uri=https://issuer.test",
        "loomspan-sidecar.auth.jwt.audience=sidecar",
        "loomspan-sidecar.auth.jwt.public-key-location=classpath:fixtures/jwt-public.pem"
})
class ManagementConfigurationHttpIntegrationTest {
    @TempDir static Path storageDirectory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("loomspan-sidecar.storage.database-path", () -> storageDirectory.resolve("sidecar.db").toString());
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ConfigurationSnapshotStore store;
    @Autowired RuntimeConfigurationService runtime;
    @Autowired ManagementEditingState editing;
    @Autowired ai.loomspan.sidecar.management.ManagementConfigurationImportService imports;
    @Autowired org.springframework.core.env.Environment environment;
    @Autowired ai.loomspan.sidecar.management.ManagementIdentityService identity;
    @LocalServerPort int port;
    private final ObjectMapper json = new ObjectMapper();
    private static final String PASSWORD = "Long Password 123!";

    @BeforeEach void clearEditing() { synchronized (editing) { editing.clearLease(); } }

    @Test void rollbackAndImportLoadDraftWithoutPublishing() throws Exception {
        String email = "load-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor"); Browser editor = login(email);
        var source = runtime.publishedSnapshot();
        byte[] bundle;
        Path path = ConfigurationBundleV1.write(source);
        try { bundle = java.nio.file.Files.readAllBytes(path); }
        finally { java.nio.file.Files.deleteIfExists(path); }
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"label\":\"HTTP test\"}"));
        JsonNode draft = grant.path("draft");
        JsonNode reviewed = ok(editor.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        assertThat(reviewed.path("validation").path("successful").asBoolean()).isTrue();
        var fields = new java.util.HashMap<String, String>();
        fields.put("editingSessionId", grant.path("editingSessionId").asText());
        fields.put("generation", grant.path("generation").asText());
        fields.put("draftId", draft.path("draftId").asText());
        fields.put("revision", draft.path("revision").asText());
        fields.put("baseSnapshotId", source.localId().toString());
        JsonNode loaded = ok(editor.multipart("/api/management/configuration/import/load", bundle, fields, true));
        assertThat(loaded.path("revision").asLong()).isGreaterThan(draft.path("revision").asLong());
        assertThat(editor.multipart("/api/management/configuration/import/load", bundle, fields, true).statusCode())
                .isEqualTo(409);
        assertThat(runtime.inspect().publishedId()).isEqualTo(source.localId());
        assertThat(editor.post("/api/management/configuration/publish", candidate(grant, loaded)).statusCode()).isEqualTo(409);
        assertThat(editor.post("/api/management/configuration/import/confirm", "{}").statusCode()).isEqualTo(404);
        JsonNode rollbackReview = ok(editor.post("/api/management/configuration/rollback/" + source.localId() + "/review", "{}"));
        assertThat(rollbackReview.path("sourceId").asText()).isEqualTo(source.localId().toString());
        JsonNode rolled = ok(editor.post("/api/management/configuration/rollback/" + source.localId() + "/load",
                candidate(grant, loaded)));
        assertThat(rolled.path("sourceSnapshotId").asText()).isEqualTo(source.localId().toString());
        assertThat(runtime.inspect().publishedId()).isEqualTo(source.localId());
    }

    @Test void publicationRechecksAccountAfterWaitingForGate() throws Exception {
        String email = "wait-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor"); Browser editor = login(email);
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"label\":\"HTTP test\"}"));
        JsonNode draft = grant.path("draft");
        ok(editor.post("/api/management/editing/draft/validate", candidate(grant, draft)));
        var publicationField = RuntimeConfigurationService.class.getDeclaredField("publication");
        publicationField.setAccessible(true);
        var gate = (java.util.concurrent.locks.ReentrantLock) publicationField.get(runtime);
        var before = runtime.inspect().publishedId();
        gate.lock();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> editor.post("/api/management/configuration/publish", candidate(grant, draft)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (runtime.queuedPublications() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(runtime.queuedPublications()).isPositive();
            identity.alter(identity.account(email).id(), "viewer", true);
            gate.unlock();
            assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
        } finally { if (gate.isHeldByCurrentThread()) gate.unlock(); }
        assertThat(runtime.inspect().publishedId()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM management_draft WHERE account_id=?", Integer.class,
                identity.account(email).id())).isEqualTo(1);
    }

    @Test void viewerCanExportPublishedConfigurationButCannotLoadImport() throws Exception {
        String email = "export-viewer-" + UUID.randomUUID() + "@example.test";
        seed(email, "viewer"); Browser viewer = login(email);
        var exported = viewer.getBytes("/api/management/configuration/export");
        assertThat(exported.statusCode()).isEqualTo(200);
        assertThat(exported.body()).isNotEmpty();
        assertThat(viewer.post("/api/management/configuration/rollback/" + runtime.inspect().publishedId()
                + "/review", "{}").statusCode()).isEqualTo(403);
    }

    @Test void failedPublicationPreservesSavedDraftAndLease() throws Exception {
        String email = "failure-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor"); Browser editor = login(email);
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"label\":\"Failure test\"}"));
        JsonNode draft = grant.path("draft");
        ok(editor.post("/api/management/editing/draft/validate", candidate(grant, draft)));
        var before = runtime.inspect().publishedId();
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeCommit() { throw new IllegalStateException("injected commit fault"); }
        });
        try {
            var failed = editor.post("/api/management/configuration/publish", candidate(grant, draft));
            assertThat(failed.statusCode()).isEqualTo(503);
            assertThat(json.readTree(failed.body()).path("code").asText()).isEqualTo("commit_failed");
        } finally { runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
        assertThat(runtime.inspect().publishedId()).isEqualTo(before);
        assertThat(ok(editor.get("/api/management/editing/draft")).path("draftId").asText())
                .isEqualTo(draft.path("draftId").asText());
        assertThat(ok(editor.get("/api/management/editing")).path("mine").asBoolean()).isTrue();
    }

    @Test void publicationRechecksOwnershipAfterWaitingForGate() throws Exception {
        String email = "handoff-wait-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor"); Browser first = login(email), second = login(email);
        JsonNode grant = ok(first.post("/api/management/editing/lease", "{\"label\":\"First\"}"));
        JsonNode draft = grant.path("draft");
        ok(first.post("/api/management/editing/draft/validate", candidate(grant, draft)));
        var before = runtime.inspect().publishedId();
        var publicationField = RuntimeConfigurationService.class.getDeclaredField("publication");
        publicationField.setAccessible(true);
        var gate = (java.util.concurrent.locks.ReentrantLock) publicationField.get(runtime);
        gate.lock();
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var publishing = workers.submit(() -> first.post("/api/management/configuration/publish", candidate(grant, draft)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (runtime.queuedPublications() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(runtime.queuedPublications()).isPositive();
            var handoff = ok(second.post("/api/management/editing/lease/handoff", "{\"label\":\"Second\"}"));
            assertThat(handoff.path("generation").asText()).isNotEqualTo(grant.path("generation").asText());
            gate.unlock();
            assertThat(publishing.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
        } finally { if (gate.isHeldByCurrentThread()) gate.unlock(); }
        assertThat(runtime.inspect().publishedId()).isEqualTo(before);
        assertThat(ok(second.get("/api/management/editing/draft")).path("draftId").asText())
                .isEqualTo(draft.path("draftId").asText());
    }

    private void seed(String email, String role) {
        var encoder = new Pbkdf2PasswordEncoder("", 16, 310_000,
                Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        jdbc.update("INSERT INTO management_account(email,role,enabled,password_hash,created_at) VALUES (?,?,1,?,?)",
                email, role, "{pbkdf2@SpringSecurity_v5_8}" + encoder.encode(PASSWORD), Clock.systemUTC().millis());
    }
    private static long importTemporaryFileCount() throws Exception {
        try (var paths = java.nio.file.Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.filter(path -> path.getFileName().toString().startsWith("sidecar-configuration-import-"))
                    .count();
        }
    }
    private Browser login(String email) throws Exception {
        Browser browser = new Browser();
        String csrf = browser.get("/management/login").body().split("name='_csrf' value='")[1].split("'")[0];
        String body = "email=" + URLEncoder.encode(email, StandardCharsets.UTF_8) + "&password="
                + URLEncoder.encode(PASSWORD, StandardCharsets.UTF_8) + "&_csrf="
                + URLEncoder.encode(csrf, StandardCharsets.UTF_8);
        assertThat(browser.postForm("/management/login", body).statusCode()).isEqualTo(302);
        browser.csrf = ok(browser.get("/api/management/session")).path("csrfToken").asText();
        return browser;
    }
    private JsonNode ok(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return json.readTree(response.body());
    }
    private static String candidate(JsonNode grant, JsonNode draft) {
        return "{\"editingSessionId\":\"" + grant.path("editingSessionId").asText()
                + "\",\"generation\":\"" + grant.path("generation").asText()
                + "\",\"draftId\":\"" + draft.path("draftId").asText()
                + "\",\"revision\":" + draft.path("revision").asLong()
                + ",\"baseSnapshotId\":\"" + draft.path("baseSnapshotId").asText() + "\"}";
    }
    private final class Browser {
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        private String csrf;
        HttpResponse<String> get(String path) throws Exception { return send("GET", path, null, true); }
        HttpResponse<byte[]> getBytes(String path) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        }
        HttpResponse<String> post(String path, String body) throws Exception { return send("POST", path, body, true); }
        HttpResponse<String> postWithoutCsrf(String path, String body) throws Exception {
            return send("POST", path, body, false);
        }
        CompletableFuture<HttpResponse<String>> postAsync(String path, String body) {
            return client.sendAsync(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                    .header("X-CSRF-TOKEN", csrf).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> put(String path, String body) throws Exception { return send("PUT", path, body, true); }
        HttpResponse<String> delete(String path, String body) throws Exception { return send("DELETE", path, body, true); }
        HttpResponse<String> getJwt(String path, String jwt) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).header("Authorization", "Bearer " + jwt)
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> postForm(String path, String body) throws Exception {
            return client.send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> multipart(String path, byte[] bundle, java.util.Map<String, String> fields,
                boolean token) throws Exception {
            return client.send(multipartRequest(path, bundle, fields, token), HttpResponse.BodyHandlers.ofString());
        }
        CompletableFuture<HttpResponse<String>> multipartAsync(String path, byte[] bundle,
                java.util.Map<String, String> fields) throws Exception {
            return client.sendAsync(multipartRequest(path, bundle, fields, true), HttpResponse.BodyHandlers.ofString());
        }
        private HttpRequest multipartRequest(String path, byte[] bundle, java.util.Map<String, String> fields,
                boolean token) throws Exception {
            String boundary = "sidecar-" + UUID.randomUUID();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"bundle\"; filename=\"bundle.zip\"\r\n"
                    + "Content-Type: application/zip\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            bytes.write(bundle);
            bytes.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            for (var field : fields.entrySet()) {
                bytes.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\""
                        + field.getKey() + "\"\r\n\r\n" + field.getValue() + "\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
            }
            bytes.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            var request = HttpRequest.newBuilder(uri(path)).header("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (token && csrf != null) request.header("X-CSRF-TOKEN", csrf);
            return request.POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray())).build();
        }
        private HttpResponse<String> send(String method, String path, String body, boolean token) throws Exception {
            var builder = HttpRequest.newBuilder(uri(path));
            if (body != null) builder.header("Content-Type", "application/json");
            if (token && csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            return client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
    private URI uri(String path) { return URI.create("http://127.0.0.1:" + port + path); }
}
