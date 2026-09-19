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

    @BeforeEach void clearEditing() { synchronized (editing) { editing.clearAll(); } }

    @Test void editorReviewsFormatOneBundleWithoutMutation() throws Exception {
        String email = "import-review-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        UUID initial = runtime.inspect().publishedId();
        int history = store.history().size();
        byte[] bundle = editor.getBytes("/api/management/configuration/export").body();
        long temporaryBefore = importTemporaryFileCount();
        JsonNode review = ok(editor.multipart("/api/management/configuration/import/review", bundle, java.util.Map.of(), true));
        assertThat(review.path("sourceSnapshotId").asText()).isEqualTo(initial.toString());
        assertThat(review.path("reviewId").asText()).isNotBlank();
        assertThat(review.path("skillDocuments").asInt()).isZero();
        assertThat(review.path("validatedSkills").asInt()).isZero();
        assertThat(review.path("routes").asInt()).isZero();
        assertThat(review.path("validation").path("successful").asBoolean()).isTrue();
        assertThat(review.path("observation").path("publishedId").asText()).isEqualTo(initial.toString());
        assertThat(store.history()).hasSize(history);
        assertThat(runtime.inspect().publishedId()).isEqualTo(initial);
        assertThat(importTemporaryFileCount()).isEqualTo(temporaryBefore);
    }

    @Test void importRequiresEditorAndCsrfAndPublishesFreshLocalIds() throws Exception {
        String editorEmail = "import-editor-" + UUID.randomUUID() + "@example.test";
        String viewerEmail = "import-viewer-" + UUID.randomUUID() + "@example.test";
        seed(editorEmail, "editor"); seed(viewerEmail, "viewer");
        Browser editor = login(editorEmail);
        Browser viewer = login(viewerEmail);
        int accountCount = jdbc.queryForObject("SELECT COUNT(*) FROM management_account", Integer.class);
        UUID source = runtime.inspect().publishedId();
        byte[] bundle = editor.getBytes("/api/management/configuration/export").body();
        String reviewPath = "/api/management/configuration/import/review";
        assertThat(new Browser().multipart(reviewPath, bundle, java.util.Map.of(), false).statusCode()).isEqualTo(403);
        assertThat(viewer.multipart(reviewPath, bundle, java.util.Map.of(), true).statusCode()).isEqualTo(403);
        assertThat(editor.multipart(reviewPath, bundle, java.util.Map.of(), false).statusCode()).isEqualTo(403);
        assertThat(viewer.get("/management/configuration/import").statusCode()).isEqualTo(403);
        UUID prior = source;
        for (int i = 0; i < 2; i++) {
            JsonNode review = ok(editor.multipart(reviewPath, bundle, java.util.Map.of(), true));
            var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                    "expectedPublishedId", review.path("observation").path("publishedId").asText());
            assertThat(editor.multipart("/api/management/configuration/import/confirm", bundle, fields, false)
                    .statusCode()).isEqualTo(403);
            JsonNode accepted = ok(editor.multipart("/api/management/configuration/import/confirm", bundle, fields, true));
            UUID local = UUID.fromString(accepted.path("localId").asText());
            assertThat(local).isNotEqualTo(prior);
            assertThat(accepted.path("sourceId").asText()).isEqualTo(source.toString());
            assertThat(accepted.path("status").asText()).isEqualTo("PUBLISHED");
            assertThat(runtime.inspect().publishedId()).isEqualTo(local);
            assertThat(editor.multipart("/api/management/configuration/import/confirm", bundle, fields, true)
                    .statusCode()).isEqualTo(409);
            prior = local;
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM management_account", Integer.class)).isEqualTo(accountCount);
    }

    @Test void staleImportGrantPreservesForeignDraftAndRefreshesObservation() throws Exception {
        String editorEmail = "import-confirm-" + UUID.randomUUID() + "@example.test";
        String ownerEmail = "import-owner-" + UUID.randomUUID() + "@example.test";
        seed(editorEmail, "editor"); seed(ownerEmail, "editor");
        Browser importer = login(editorEmail);
        Browser owner = login(ownerEmail);
        byte[] bundle = importer.getBytes("/api/management/configuration/export").body();
        JsonNode review = ok(importer.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        int count = store.history().size();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(owner.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        var forged = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                "expectedPublishedId", review.path("observation").path("publishedId").asText(),
                "expectedGrantId", grant.path("grantId").asText());
        assertThat(importer.multipart("/api/management/configuration/import/confirm", bundle, forged, true)
                .statusCode()).isEqualTo(409);
        assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
        var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                "expectedPublishedId", review.path("observation").path("publishedId").asText());
        var conflict = importer.multipart("/api/management/configuration/import/confirm", bundle, fields, true);
        assertThat(conflict.statusCode()).isEqualTo(409);
        JsonNode refreshed = json.readTree(conflict.body());
        assertThat(refreshed.path("observation").path("grantId").asText())
                .isEqualTo(grant.path("grantId").asText());
        assertThat(refreshed.path("observation").path("leaseOwner").asText()).isEqualTo(ownerEmail);
        assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
        assertThat(store.history()).hasSize(count);
        var confirmed = new java.util.HashMap<>(fields);
        confirmed.put("expectedGrantId", grant.path("grantId").asText());
        JsonNode renewed = ok(owner.post("/api/management/editing/lease/activity",
                "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + grant.path("grantId").asText() + "\"}"));
        assertThat(renewed.path("grantId").asText()).isEqualTo(grant.path("grantId").asText());
        assertThat(importer.multipart("/api/management/configuration/import/confirm", bundle, confirmed, true)
                .statusCode()).isEqualTo(200);
        assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
    }

    @Test void replacementGrantFromSameOwnerInvalidatesConfirmation() throws Exception {
        String importerEmail = "import-replaced-" + UUID.randomUUID() + "@example.test";
        String ownerEmail = "import-replaced-owner-" + UUID.randomUUID() + "@example.test";
        seed(importerEmail, "editor"); seed(ownerEmail, "editor");
        Browser importer = login(importerEmail), owner = login(ownerEmail);
        String tab = UUID.randomUUID().toString();
        JsonNode first = ok(owner.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        byte[] bundle = importer.getBytes("/api/management/configuration/export").body();
        JsonNode review = ok(importer.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        var capability = "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + first.path("grantId").asText() + "\"}";
        assertThat(owner.post("/api/management/editing/lease/release", capability).statusCode()).isEqualTo(204);
        JsonNode replacement = ok(owner.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        assertThat(replacement.path("grantId").asText()).isNotEqualTo(first.path("grantId").asText());
        var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                "expectedPublishedId", review.path("observation").path("publishedId").asText(),
                "expectedGrantId", first.path("grantId").asText());
        var conflict = importer.multipart("/api/management/configuration/import/confirm", bundle, fields, true);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).path("observation").path("grantId").asText())
                .isEqualTo(replacement.path("grantId").asText());
        assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
    }

    @Test void changedRuntimeSnapshotInvalidatesReviewedImport() throws Exception {
        String email = "import-runtime-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser browser = login(email);
        byte[] bundle = browser.getBytes("/api/management/configuration/export").body();
        JsonNode review = ok(browser.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        var original = runtime.publishedSnapshot();
        var draft = new ai.loomspan.sidecar.storage.ConfigurationDraft(original);
        draft.replaceContent(new ManagedConfiguration(java.util.List.of(), "targets: {}\nroutes: {}\n# changed runtime\n"));
        var frozen = draft.freeze();
        draft.recordValidation(frozen, runtime.validate(frozen.configuration()));
        var changed = runtime.publish(draft::validatedCandidate);
        int history = store.history().size();
        var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                "expectedPublishedId", original.localId().toString());
        var conflict = browser.multipart("/api/management/configuration/import/confirm", bundle, fields, true);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).path("observation").path("publishedId").asText())
                .isEqualTo(changed.localId().toString());
        assertThat(runtime.inspect().publishedId()).isEqualTo(changed.localId());
        assertThat(store.history()).hasSize(history);
    }

    @Test void laterSessionReviewSupersedesConfirmationWaitingForPublication() throws Exception {
        String email = "import-supersede-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser browser = login(email);
        byte[] bundle = browser.getBytes("/api/management/configuration/export").body();
        JsonNode first = ok(browser.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        var original = runtime.publishedSnapshot();
        var draft = new ai.loomspan.sidecar.storage.ConfigurationDraft(original);
        draft.replaceContent(new ManagedConfiguration(java.util.List.of(), "targets: {}\nroutes: {}\n# superseding publication\n"));
        var frozen = draft.freeze();
        draft.recordValidation(frozen, runtime.validate(frozen.configuration()));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeFrameworkPublish() {
                entered.countDown();
                try { if (!release.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var publication = workers.submit(() -> runtime.publish(draft::validatedCandidate));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var fields = java.util.Map.of("reviewId", first.path("reviewId").asText(),
                    "expectedPublishedId", original.localId().toString());
            var queued = browser.multipartAsync("/api/management/configuration/import/confirm", bundle, fields);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (runtime.queuedPublications() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(runtime.queuedPublications()).isPositive();
            JsonNode second = ok(browser.multipart("/api/management/configuration/import/review", bundle,
                    java.util.Map.of(), true));
            assertThat(second.path("reviewId").asText()).isNotEqualTo(first.path("reviewId").asText());
            release.countDown();
            var published = publication.get(5, TimeUnit.SECONDS);
            var conflict = queued.get(5, TimeUnit.SECONDS);
            assertThat(conflict.statusCode()).isEqualTo(409);
            assertThat(json.readTree(conflict.body()).path("code").asText()).isEqualTo("review_required");
            assertThat(runtime.inspect().publishedId()).isEqualTo(published.localId());
        } finally { release.countDown(); runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
    }

    @Test void changedImportBytesAndMalformedBundleNeverCutOver() throws Exception {
        String email = "import-bytes-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser browser = login(email);
        byte[] bundle = browser.getBytes("/api/management/configuration/export").body();
        UUID original = runtime.inspect().publishedId();
        int history = store.history().size();
        assertThat(browser.multipart("/api/management/configuration/import/review", "not zip".getBytes(StandardCharsets.UTF_8),
                java.util.Map.of(), true).statusCode()).isEqualTo(400);
        assertThat(browser.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of("unexpected", "field"), true).statusCode()).isEqualTo(400);
        JsonNode review = ok(browser.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                "expectedPublishedId", original.toString());
        byte[] changed = bundle.clone(); changed[changed.length - 1] ^= 1;
        var conflict = browser.multipart("/api/management/configuration/import/confirm", changed, fields, true);
        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(json.readTree(conflict.body()).path("code").asText()).isEqualTo("content_changed");
        assertThat(runtime.inspect().publishedId()).isEqualTo(original);
        assertThat(store.history()).hasSize(history);
    }

    @Test void reviewRejectsUnboundDestinationVariableBeforeAnyDraftDisruption() throws Exception {
        String importerEmail = "import-binding-" + UUID.randomUUID() + "@example.test";
        String ownerEmail = "import-binding-owner-" + UUID.randomUUID() + "@example.test";
        seed(importerEmail, "editor"); seed(ownerEmail, "editor");
        Browser importer = login(importerEmail), owner = login(ownerEmail);
        String tab = UUID.randomUUID().toString();
        ok(owner.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        var authored = new ManagedConfiguration(java.util.List.of(),
                "targets:\n  remote:\n    base-url: '${UNDECLARED_IMPORT_TARGET}'\nroutes: {}\n");
        Path path = ConfigurationBundleV1.write(new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                authored, SnapshotStatus.PUBLISHED));
        try {
            byte[] bundle = java.nio.file.Files.readAllBytes(path);
            UUID before = runtime.inspect().publishedId();
            int history = store.history().size();
            JsonNode review = ok(importer.multipart("/api/management/configuration/import/review", bundle,
                    java.util.Map.of(), true));
            assertThat(review.path("validation").path("successful").asBoolean()).isFalse();
            assertThat(review.path("reviewId").isNull()).isTrue();
            assertThat(review.path("validation").path("issues").size()).isPositive();
            assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
            assertThat(runtime.inspect().publishedId()).isEqualTo(before);
            assertThat(store.history()).hasSize(history);
        } finally { java.nio.file.Files.deleteIfExists(path); }
    }

    @Test void validUploadLargerThanDefaultMultipartLimitReachesValidation() throws Exception {
        assertThat(environment.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("100MB");
        assertThat(environment.getProperty("spring.servlet.multipart.max-request-size")).isEqualTo("101MB");
        String email = "import-large-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        byte[] random = new byte[1_500_000];
        new java.util.Random(42).nextBytes(random);
        String rest = "targets: {}\nroutes: {}\n# " + java.util.Base64.getEncoder().encodeToString(random) + "\n";
        Path path = ConfigurationBundleV1.write(new ConfigurationSnapshot(UUID.randomUUID(), null, 1,
                new ManagedConfiguration(java.util.List.of(), rest), SnapshotStatus.PUBLISHED));
        try {
            byte[] bundle = java.nio.file.Files.readAllBytes(path);
            assertThat(bundle.length).isGreaterThan(1_048_576);
            JsonNode review = ok(editor.multipart("/api/management/configuration/import/review", bundle,
                    java.util.Map.of(), true));
            assertThat(review.path("validation").path("successful").asBoolean()).isTrue();
            assertThat(review.path("reviewId").asText()).isNotBlank();
        } finally { java.nio.file.Files.deleteIfExists(path); }
    }

    @Test void importServiceRejectsCompressedInputPastInclusiveBoundAndCleansStaging() throws Exception {
        long before = importTemporaryFileCount();
        var file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        org.mockito.Mockito.when(file.isEmpty()).thenReturn(false);
        org.mockito.Mockito.when(file.getInputStream()).thenReturn(new java.io.InputStream() {
            long remaining = ConfigurationBundleV1.MAX_ZIP_BYTES + 1;
            @Override public int read() { if (remaining-- <= 0) return -1; return 0; }
            @Override public int read(byte[] bytes, int offset, int length) {
                if (remaining <= 0) return -1;
                int count = (int) Math.min(length, remaining);
                java.util.Arrays.fill(bytes, offset, offset + count, (byte) 0);
                remaining -= count;
                return count;
            }
        });
        var session = org.mockito.Mockito.mock(jakarta.servlet.http.HttpSession.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> imports.review(session, file))
                .isInstanceOf(ConfigurationBundleV1.BundleTooLarge.class);
        assertThat(importTemporaryFileCount()).isEqualTo(before);
    }

    @Test void importCommitFailureClearsDraftButKeepsRuntimeAndPointer() throws Exception {
        String importerEmail = "import-fail-" + UUID.randomUUID() + "@example.test";
        String ownerEmail = "import-draft-" + UUID.randomUUID() + "@example.test";
        seed(importerEmail, "editor"); seed(ownerEmail, "editor");
        Browser importer = login(importerEmail), owner = login(ownerEmail);
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(owner.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        byte[] bundle = importer.getBytes("/api/management/configuration/export").body();
        JsonNode review = ok(importer.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        UUID original = runtime.inspect().publishedId();
        int history = store.history().size();
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeCommit() { throw new IllegalStateException("test commit fault"); }
        });
        try {
            var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                    "expectedPublishedId", original.toString(), "expectedGrantId", grant.path("grantId").asText());
            var failed = importer.multipart("/api/management/configuration/import/confirm", bundle, fields, true);
            assertThat(failed.statusCode()).isEqualTo(503);
            JsonNode error = json.readTree(failed.body());
            assertThat(error.path("code").asText()).isEqualTo("commit_failed");
            assertThat(error.path("publishedId").asText()).isEqualTo(original.toString());
            assertThat(error.path("intendedId").asText()).isEqualTo(original.toString());
            assertThat(runtime.inspect().publishedId()).isEqualTo(original);
            assertThat(store.history()).hasSize(history);
            assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        } finally { runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
    }

    @Test void importPreparationFailurePreservesDraftAndLease() throws Exception {
        String importerEmail = "import-prepare-" + UUID.randomUUID() + "@example.test";
        String ownerEmail = "import-prepare-owner-" + UUID.randomUUID() + "@example.test";
        seed(importerEmail, "editor"); seed(ownerEmail, "editor");
        Browser importer = login(importerEmail), owner = login(ownerEmail);
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(owner.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        byte[] bundle = importer.getBytes("/api/management/configuration/export").body();
        JsonNode review = ok(importer.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        int history = store.history().size();
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforePreparation() { throw new IllegalStateException("test preparation fault"); }
        });
        try {
            var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                    "expectedPublishedId", review.path("observation").path("publishedId").asText(),
                    "expectedGrantId", grant.path("grantId").asText());
            var failed = importer.multipart("/api/management/configuration/import/confirm", bundle, fields, true);
            assertThat(failed.statusCode()).isEqualTo(503);
            assertThat(json.readTree(failed.body()).path("code").asText()).isEqualTo("preparation_failed");
            assertThat(store.history()).hasSize(history);
            assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
            assertThat(ok(owner.get("/api/management/editing")).path("held").asBoolean()).isTrue();
        } finally { runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
    }

    @Test void importActivationFailureRevertsPointerAndRetainsFailedHistoryAfterCutover() throws Exception {
        String importerEmail = "import-activation-" + UUID.randomUUID() + "@example.test";
        String ownerEmail = "import-activation-owner-" + UUID.randomUUID() + "@example.test";
        seed(importerEmail, "editor"); seed(ownerEmail, "editor");
        Browser importer = login(importerEmail), owner = login(ownerEmail);
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(owner.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        byte[] bundle = importer.getBytes("/api/management/configuration/export").body();
        JsonNode review = ok(importer.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        UUID original = runtime.inspect().publishedId();
        int history = store.history().size();
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeFrameworkPublish() { throw new IllegalStateException("test activation fault"); }
        });
        try {
            var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                    "expectedPublishedId", original.toString(), "expectedGrantId", grant.path("grantId").asText());
            var failed = importer.multipart("/api/management/configuration/import/confirm", bundle, fields, true);
            assertThat(failed.statusCode()).isEqualTo(503);
            JsonNode error = json.readTree(failed.body());
            assertThat(error.path("code").asText()).isEqualTo("activation_failed");
            assertThat(error.path("publishedId").asText()).isEqualTo(original.toString());
            assertThat(error.path("intendedId").asText()).isEqualTo(original.toString());
            assertThat(error.path("intendedStatus").asText()).isEqualTo("PUBLISHED");
            assertThat(store.history()).hasSize(Math.min(history + 1, 10));
            assertThat(store.history().get(store.history().size() - 1).status().name()).isEqualTo("FAILED");
            assertThat(owner.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        } finally { runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
    }

    @Test void acceptedImportContinuesAfterClientDisconnect() throws Exception {
        String email = "import-disconnect-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser browser = login(email);
        byte[] bundle = browser.getBytes("/api/management/configuration/export").body();
        JsonNode review = ok(browser.multipart("/api/management/configuration/import/review", bundle,
                java.util.Map.of(), true));
        UUID before = runtime.inspect().publishedId();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeFrameworkPublish() {
                entered.countDown();
                try { if (!release.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout"); }
                catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
            }
        });
        try {
            var fields = java.util.Map.of("reviewId", review.path("reviewId").asText(),
                    "expectedPublishedId", before.toString());
            var request = browser.multipartAsync("/api/management/configuration/import/confirm", bundle, fields);
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            request.cancel(true);
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (runtime.publishedSnapshot().localId().equals(before) && System.nanoTime() < deadline)
                Thread.sleep(10);
            assertThat(runtime.publishedSnapshot().localId()).isNotEqualTo(before);
            assertThat(store.current().localId()).isEqualTo(runtime.publishedSnapshot().localId());
        } finally { release.countDown(); runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
    }

    @Test void exportUsesRunningSnapshotThroughIntendedPointerMismatchAndRequiresManagementSession() throws Exception {
        var running = runtime.publishedSnapshot();
        int historyCount = store.history().size();
        String path = "/api/management/configuration/export";
        assertThat(new Browser().get(path).statusCode()).isEqualTo(401);
        assertThat(new Browser().getJwt(path, JwtTestTokens.token("operator", java.util.List.of())).statusCode())
                .isEqualTo(401);
        var intended = store.submit(new ManagedConfiguration(java.util.List.of(),
                "targets: {}\nroutes: {}\n# intended-only\n"), null, running.localId());
        try {
            for (String role : java.util.List.of("viewer", "editor", "admin")) {
                String email = "export-" + UUID.randomUUID() + "@example.test";
                seed(email, role);
                Browser browser = login(email);
                var response = browser.getBytes(path);
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().firstValue("Content-Disposition")).hasValueSatisfying(
                        value -> assertThat(value).contains("attachment", ".zip"));
                assertThat(response.headers().firstValue("Cache-Control")).hasValueSatisfying(
                        value -> assertThat(value).contains("no-store"));
                var bundle = ConfigurationBundleV1.read(new ByteArrayInputStream(response.body()));
                assertThat(bundle.sourceSnapshotId()).isEqualTo(running.localId());
                assertThat(bundle.configuration()).isEqualTo(running.configuration());
                assertThat(bundle.configuration().restRoutesYaml()).doesNotContain("intended-only");
            }
            assertThat(runtime.publishedSnapshot()).isEqualTo(running);
            assertThat(store.history()).hasSize(historyCount + 1);
        } finally { store.revert(intended.localId(), running.localId()); }
    }

    @Test void editorValidatesExactDraftWithoutPublishing() throws Exception {
        String email = "validate-" + UUID.randomUUID() + "@example.test";
        String adminEmail = "foreign-admin-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        seed(adminEmail, "admin");
        Browser editor = login(email);
        Browser admin = login(adminEmail);
        UUID initial = runtime.inspect().publishedId();
        int count = store.history().size();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String first = grant.path("draft").path("candidateId").asText();
        String id = grant.path("grantId").asText();
        String capability = candidate(tab, id, first);
        assertThat(editor.postWithoutCsrf("/api/management/editing/draft/validate", capability).statusCode())
                .isEqualTo(403);
        assertThat(editor.postWithoutCsrf("/api/management/configuration/publish", capability).statusCode())
                .isEqualTo(403);
        var unvalidated = editor.post("/api/management/configuration/publish", capability);
        assertThat(unvalidated.statusCode()).isEqualTo(409);
        assertThat(json.readTree(unvalidated.body()).path("code").asText()).isEqualTo("validation_required");
        JsonNode checked = ok(editor.post("/api/management/editing/draft/validate", capability));
        assertThat(checked.path("validation").path("successful").asBoolean()).isTrue();
        assertThat(checked.path("applied").asBoolean()).isTrue();
        assertThat(admin.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        assertThat(admin.post("/api/management/editing/draft/validate", capability).statusCode()).isEqualTo(409);
        assertThat(admin.post("/api/management/configuration/publish", capability).statusCode()).isEqualTo(409);
        assertThat(runtime.inspect().publishedId()).isEqualTo(initial);
        assertThat(store.history()).hasSize(count);
        JsonNode changed = ok(editor.put("/api/management/editing/draft", "{\"tabId\":\"" + tab
                + "\",\"grantId\":\"" + id + "\",\"expectedCandidateId\":\"" + first
                + "\",\"skillDocuments\":[],\"restRoutesYaml\":\"targets: {}\\nroutes: {}\\n# authored-${EXAMPLE}-literal <script>alert(1)</script>\\n\"}"));
        assertThat(changed.path("candidateId").asText()).isNotEqualTo(first);
        assertThat(changed.path("validation").isNull()).isTrue();
        var stale = editor.post("/api/management/editing/draft/validate", capability);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(json.readTree(stale.body()).path("code").asText()).isEqualTo("candidate_conflict");
        assertThat(editor.post("/api/management/configuration/publish", capability).statusCode()).isEqualTo(409);
        String next = candidate(tab, id, changed.path("candidateId").asText());
        ok(editor.post("/api/management/editing/draft/validate", next));
        JsonNode published = ok(editor.post("/api/management/configuration/publish", next));
        assertThat(published.path("localId").asText()).isNotEqualTo(initial.toString());
        assertThat(editor.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
        JsonNode current = ok(editor.get("/api/management/configuration/current"));
        assertThat(current.path("published").path("localId").asText()).isEqualTo(published.path("localId").asText());
        assertThat(current.path("published").path("configuration").path("restRoutesYaml").asText())
                .contains("authored-${EXAMPLE}-literal <script>alert(1)</script>");
        assertThat(current.path("intendedId").asText()).isEqualTo(published.path("localId").asText());
        assertThat(current.path("intendedStatus").asText()).isEqualTo("PUBLISHED");
        JsonNode history = ok(editor.get("/api/management/configuration/history"));
        assertThat(history.size()).isEqualTo(Math.min(count + 1, 10));
        assertThat(history.get(history.size() - 1).path("configuration").path("restRoutesYaml").asText())
                .contains("authored-${EXAMPLE}-literal");
    }

    @Test void viewerCanInspectButCannotValidateOrPublishAndJwtDoesNotGrantManagementAccess() throws Exception {
        String email = "viewer-" + UUID.randomUUID() + "@example.test";
        seed(email, "viewer");
        Browser viewer = login(email);
        assertThat(viewer.get("/management/configuration/current").statusCode()).isEqualTo(200);
        assertThat(new Browser().get("/management/configuration/current").statusCode()).isEqualTo(302);
        assertThat(viewer.get("/api/management/configuration/current").statusCode()).isEqualTo(200);
        assertThat(viewer.get("/api/management/configuration/history").statusCode()).isEqualTo(200);
        assertThat(viewer.post("/api/management/editing/draft/validate", "{}").statusCode()).isEqualTo(403);
        assertThat(viewer.post("/api/management/configuration/publish", "{}").statusCode()).isEqualTo(403);
        assertThat(new Browser().get("/api/management/configuration/current").statusCode()).isEqualTo(401);
        assertThat(new Browser().getJwt("/api/management/configuration/current",
                JwtTestTokens.token("operator", java.util.List.of())).statusCode()).isEqualTo(401);
        assertThat(viewer.get("/api/management/configuration/current").headers().firstValue("Cache-Control"))
                .hasValue("no-store");
        var missing = viewer.get("/api/management/configuration/history/" + UUID.randomUUID());
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(json.readTree(missing.body()).path("code").asText()).isEqualTo("history_not_found");
    }

    @Test void administratorMayValidateAndPublishOnlyOwnLeasedDraft() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@example.test";
        seed(email, "admin");
        Browser admin = login(email);
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(admin.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = candidate(tab, grant.path("grantId").asText(), grant.path("draft").path("candidateId").asText());
        assertThat(admin.post("/api/management/editing/draft/validate",
                candidate(UUID.randomUUID().toString(), grant.path("grantId").asText(),
                        grant.path("draft").path("candidateId").asText())).statusCode()).isEqualTo(409);
        assertThat(admin.post("/api/management/configuration/publish", cap).statusCode()).isEqualTo(409);
        ok(admin.post("/api/management/editing/draft/validate", cap));
        assertThat(admin.post("/api/management/configuration/publish", cap).statusCode()).isEqualTo(200);
    }

    @Test void queuedPublishRechecksAccountAfterWaitingForPublicationLock() throws Exception {
        String email = "queued-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        UUID initial = runtime.inspect().publishedId();
        int count = store.history().size();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = candidate(tab, grant.path("grantId").asText(), grant.path("draft").path("candidateId").asText());
        ok(editor.post("/api/management/editing/draft/validate", cap));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforePreparation() {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timed out");
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                throw new IllegalStateException("test first attempt fails before commit");
            }
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> editor.post("/api/management/configuration/publish", cap));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> editor.post("/api/management/configuration/publish", cap));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (runtime.queuedPublications() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(runtime.queuedPublications()).isPositive();
            var inspection = workers.submit(() -> editor.get("/api/management/configuration/current"));
            Thread.sleep(50);
            assertThat(inspection.isDone()).isFalse();
            long accountId = jdbc.queryForObject("SELECT id FROM management_account WHERE email=?", Long.class, email);
            identity.alter(accountId, "viewer", true);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(503);
            assertThat(second.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
            assertThat(inspection.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        } finally {
            release.countDown();
            runtime.hooks(new RuntimeConfigurationService.Hooks() {});
        }
        assertThat(runtime.inspect().publishedId()).isEqualTo(initial);
        assertThat(store.history()).hasSize(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "save", "discard", "lease-expire", "logout"})
    void queuedPublishRejectsChangedPrivateAuthority(String change) throws Exception {
        String email = "queued-" + change + "-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        UUID initial = runtime.inspect().publishedId();
        int count = store.history().size();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String grantId = grant.path("grantId").asText();
        String candidateId = grant.path("draft").path("candidateId").asText();
        String cap = candidate(tab, grantId, candidateId);
        ok(editor.post("/api/management/editing/draft/validate", cap));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforePreparation() {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                throw new IllegalStateException("first attempt stops before commit");
            }
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> editor.post("/api/management/configuration/publish", cap));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = workers.submit(() -> editor.post("/api/management/configuration/publish", cap));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (runtime.queuedPublications() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(runtime.queuedPublications()).isPositive();
            switch (change) {
                case "release" -> assertThat(editor.post("/api/management/editing/lease/release",
                        "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + grantId + "\"}").statusCode()).isEqualTo(204);
                case "save" -> assertThat(editor.put("/api/management/editing/draft", "{\"tabId\":\"" + tab
                        + "\",\"grantId\":\"" + grantId + "\",\"expectedCandidateId\":\"" + candidateId
                        + "\",\"skillDocuments\":[],\"restRoutesYaml\":\"targets: {}\\nroutes: {}\\n\"}")
                        .statusCode()).isEqualTo(200);
                case "discard" -> assertThat(editor.delete("/api/management/editing/draft",
                        "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + grantId + "\"}").statusCode()).isEqualTo(204);
                case "lease-expire" -> { synchronized (editing) { editing.lease.expiresAt = 0; } }
                case "logout" -> assertThat(editor.post("/api/management/logout", "{}").statusCode()).isEqualTo(204);
                default -> throw new AssertionError(change);
            }
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(503);
            assertThat(second.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(409);
        } finally {
            release.countDown();
            runtime.hooks(new RuntimeConfigurationService.Hooks() {});
        }
        assertThat(runtime.inspect().publishedId()).isEqualTo(initial);
        assertThat(store.history()).hasSize(count);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void outcomeRecordingFaultReportsActivatedRuntimeTruth() throws Exception {
        String email = "status-" + UUID.randomUUID() + "@example.test";
        String adminEmail = "status-admin-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        seed(adminEmail, "admin");
        Browser editor = login(email);
        Browser admin = login(adminEmail);
        UUID initial = runtime.inspect().publishedId();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = candidate(tab, grant.path("grantId").asText(), grant.path("draft").path("candidateId").asText());
        ok(editor.post("/api/management/editing/draft/validate", cap));
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeStatus() { throw new IllegalStateException("test status fault"); }
        });
        try {
            var response = editor.post("/api/management/configuration/publish", cap);
            assertThat(response.statusCode()).isEqualTo(503);
            JsonNode error = json.readTree(response.body());
            assertThat(error.path("code").asText()).isEqualTo("outcome_recording_failed");
            String running = error.path("publishedId").asText();
            assertThat(running).isNotEqualTo(initial.toString());
            assertThat(error.path("intendedId").asText()).isEqualTo(running);
            assertThat(error.path("intendedStatus").asText()).isEqualTo("PENDING");
            assertThat(error.path("mutationFault").asText()).isNotBlank();
            JsonNode current = ok(editor.get("/api/management/configuration/current"));
            assertThat(current.path("published").path("localId").asText()).isEqualTo(running);
            assertThat(current.path("published").path("status").asText()).isEqualTo("PENDING");
            assertThat(editor.get("/api/management/editing/draft").statusCode()).isEqualTo(404);
            assertThat(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + UUID.randomUUID()
                    + "\"}").statusCode()).isEqualTo(503);
            assertThat(admin.get("/api/management/accounts").statusCode()).isEqualTo(200);
        } finally { runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
    }

    @Test void commitFailureLeavesRuntimeAndHistoryUnchanged() throws Exception {
        String email = "commit-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        UUID initial = runtime.inspect().publishedId();
        int count = store.history().size();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = candidate(tab, grant.path("grantId").asText(), grant.path("draft").path("candidateId").asText());
        ok(editor.post("/api/management/editing/draft/validate", cap));
        jdbc.execute("CREATE TRIGGER fail_switch BEFORE UPDATE ON configuration_store_state "
                + "BEGIN SELECT RAISE(ABORT, 'forced switch failure'); END");
        try {
            var response = editor.post("/api/management/configuration/publish", cap);
            assertThat(response.statusCode()).isEqualTo(503);
            assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("commit_failed");
            assertThat(runtime.inspect().publishedId()).isEqualTo(initial);
            assertThat(store.history()).hasSize(count);
            assertThat(editor.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
        } finally { jdbc.execute("DROP TRIGGER fail_switch"); }
    }

    @Test void admittedPublicationContinuesAfterBrowserDisconnect() throws Exception {
        String email = "disconnect-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        UUID initial = runtime.inspect().publishedId();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = candidate(tab, grant.path("grantId").asText(), grant.path("draft").path("candidateId").asText());
        ok(editor.post("/api/management/editing/draft/validate", cap));
        CountDownLatch accepted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforePreparation() {
                accepted.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        });
        try {
            CompletableFuture<HttpResponse<String>> abandoned = editor.postAsync(
                    "/api/management/configuration/publish", cap);
            assertThat(accepted.await(5, TimeUnit.SECONDS)).isTrue();
            abandoned.cancel(true);
            assertThat(editor.post("/api/management/logout", "{}").statusCode()).isEqualTo(204);
            release.countDown();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (runtime.inspect().publishedId().equals(initial) && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(runtime.inspect().publishedId()).isNotEqualTo(initial);
            Browser reconnected = login(email);
            assertThat(ok(reconnected.get("/api/management/configuration/current"))
                    .path("published").path("localId").asText()).isEqualTo(runtime.inspect().publishedId().toString());
        } finally {
            release.countDown();
            runtime.hooks(new RuntimeConfigurationService.Hooks() {});
        }
    }

    @Test void rejectedActivationRetainsFailedSubmissionAndPrivateDraft() throws Exception {
        String email = "reverted-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        UUID initial = runtime.inspect().publishedId();
        int count = store.history().size();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = candidate(tab, grant.path("grantId").asText(), grant.path("draft").path("candidateId").asText());
        ok(editor.post("/api/management/editing/draft/validate", cap));
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeFrameworkPublish() { throw new IllegalStateException("test publish fault"); }
        });
        try {
            var response = editor.post("/api/management/configuration/publish", cap);
            assertThat(response.statusCode()).isEqualTo(503);
            JsonNode error = json.readTree(response.body());
            assertThat(error.path("code").asText()).isEqualTo("activation_failed");
            assertThat(error.path("publishedId").asText()).isEqualTo(initial.toString());
            assertThat(error.path("intendedId").asText()).isEqualTo(initial.toString());
            assertThat(error.path("mutationFault").isNull()).isTrue();
            JsonNode history = ok(editor.get("/api/management/configuration/history"));
            assertThat(history.size()).isEqualTo(Math.min(count + 1, 10));
            assertThat(history.get(history.size() - 1).path("status").asText()).isEqualTo("FAILED");
            assertThat(editor.get("/api/management/editing/draft").statusCode()).isEqualTo(200);
        } finally { runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void failedRevertReportsRuntimeAAndPendingIntendedB() throws Exception {
        String email = "revert-fault-" + UUID.randomUUID() + "@example.test";
        seed(email, "editor");
        Browser editor = login(email);
        UUID initial = runtime.inspect().publishedId();
        String tab = UUID.randomUUID().toString();
        JsonNode grant = ok(editor.post("/api/management/editing/lease", "{\"tabId\":\"" + tab + "\"}"));
        String cap = candidate(tab, grant.path("grantId").asText(), grant.path("draft").path("candidateId").asText());
        ok(editor.post("/api/management/editing/draft/validate", cap));
        runtime.hooks(new RuntimeConfigurationService.Hooks() {
            @Override public void beforeFrameworkPublish() { throw new IllegalStateException("test publish fault"); }
            @Override public void beforeRevert() { throw new IllegalStateException("test revert fault"); }
        });
        try {
            var response = editor.post("/api/management/configuration/publish", cap);
            assertThat(response.statusCode()).isEqualTo(503);
            JsonNode error = json.readTree(response.body());
            assertThat(error.path("code").asText()).isEqualTo("revert_failed");
            assertThat(error.path("publishedId").asText()).isEqualTo(initial.toString());
            assertThat(error.path("intendedId").asText()).isNotEqualTo(initial.toString());
            assertThat(error.path("intendedStatus").asText()).isEqualTo("PENDING");
            assertThat(error.path("mutationFault").asText()).isNotBlank();
            JsonNode current = ok(editor.get("/api/management/configuration/current"));
            assertThat(current.path("published").path("localId").asText()).isEqualTo(initial.toString());
            assertThat(editor.post("/api/management/editing/draft/validate", cap).statusCode()).isEqualTo(503);
        } finally { runtime.hooks(new RuntimeConfigurationService.Hooks() {}); }
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
    private static String candidate(String tab, String grant, String candidate) {
        return "{\"tabId\":\"" + tab + "\",\"grantId\":\"" + grant
                + "\",\"expectedCandidateId\":\"" + candidate + "\"}";
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
