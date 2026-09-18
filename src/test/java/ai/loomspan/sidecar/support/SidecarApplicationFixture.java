package ai.loomspan.sidecar.support;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.security.converter.RsaKeyConverters;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Reusable loopback model, route, and independently verifying callback fixture. */
public final class SidecarApplicationFixture implements AutoCloseable {
    /** Seed one complete authored selection before a Spring context starts. */
    public static synchronized Path seedDatabase(Path database, java.util.List<Path> skillFiles, String routesYaml) {
        if (Files.exists(database)) return database;
        var source = ai.loomspan.sidecar.storage.StorageConfiguration.dataSource(database);
        ai.loomspan.sidecar.storage.StorageConfiguration.migrate(source);
        var repository = new ai.loomspan.sidecar.storage.ConfigurationSnapshotRepository(
                new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(source));
        var store = new ai.loomspan.sidecar.storage.ConfigurationSnapshotStore(repository,
                new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)));
        var initial = store.initialize();
        try {
            var documents = new java.util.ArrayList<ai.loomspan.api.SkillDocument>();
            for (Path file : skillFiles) {
                documents.add(new ai.loomspan.api.SkillDocument(file.getFileName().toString(), Files.readString(file)));
            }
            store.submit(new ai.loomspan.sidecar.storage.ManagedConfiguration(documents, routesYaml),
                    null, initial.localId());
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        return database;
    }

    public static Path resourceFile(String resource) {
        try { return Path.of(java.util.Objects.requireNonNull(
                SidecarApplicationFixture.class.getClassLoader().getResource(resource)).toURI()); }
        catch (java.net.URISyntaxException failure) { throw new IllegalStateException(failure); }
    }
    private final ConcurrentLinkedQueue<String> modelResponses = new ConcurrentLinkedQueue<>();
    private volatile CountDownLatch modelBlockEntered = new CountDownLatch(0);
    private volatile CountDownLatch modelBlockRelease = new CountDownLatch(0);
    private final HttpServer modelServer;
    private final CallbackFixture callback;
    private final Path routes;

    public SidecarApplicationFixture() {
        try {
            callback = CallbackFixture.start();
            modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            modelServer.createContext("/v1/chat/completions", exchange -> {
                String response = modelResponses.poll();
                if ("BLOCK".equals(response)) {
                    modelBlockEntered.countDown();
                    try {
                        if (!modelBlockRelease.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("model fixture timeout");
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    }
                    response = modelResponses.poll();
                }
                if (response == null) response = "{}";
                byte[] body = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            });
            modelServer.start();
            routes = Files.createTempFile("sidecar-rest-routes-", ".yaml");
            Files.writeString(routes, """
                    targets:
                      callback:
                        base-url: http://127.0.0.1:%d
                        auth: {mode: caller-passthrough}
                        connect-timeout: 1s
                        read-timeout: 5s
                        max-response-size: 16KB
                    routes:
                      echoRest: {target: callback, method: POST, path: /echo}
                    """.formatted(callback.server.getAddress().getPort()));
            routes.toFile().deleteOnExit();
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    public int modelPort() { return modelServer.getAddress().getPort(); }
    public int callbackPort() { return callback.server.getAddress().getPort(); }
    public Path routes() { return routes; }
    public ConcurrentLinkedQueue<String> modelResponses() { return modelResponses; }
    public void blockNextModelResponse() {
        modelBlockEntered = new CountDownLatch(1);
        modelBlockRelease = new CountDownLatch(1);
        modelResponses.add("BLOCK");
    }
    public boolean awaitModelBlock(long timeout, TimeUnit unit) throws InterruptedException {
        return modelBlockEntered.await(timeout, unit);
    }
    public void releaseModelBlock() { modelBlockRelease.countDown(); }
    public CallbackFixture callback() { return callback; }

    public static String completion(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "").replace("\n", "");
        return "{\"id\":\"fixture\",\"object\":\"chat.completion\",\"created\":1,"
                + "\"model\":\"fixture-provider-model\",\"choices\":[{\"index\":0,"
                + "\"message\":{\"role\":\"assistant\",\"content\":\"" + escaped
                + "\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,"
                + "\"completion_tokens\":1,\"total_tokens\":2}}";
    }

    @Override
    public void close() {
        modelServer.stop(0);
        callback.server.stop(0);
    }

    public static final class CallbackFixture {
        private final HttpServer server;
        public final AtomicReference<String> lastToken = new AtomicReference<>();
        public final AtomicReference<String> lastIssuer = new AtomicReference<>();
        public final AtomicReference<String> lastSubject = new AtomicReference<>();
        public final CopyOnWriteArrayList<String> seenTokens = new CopyOnWriteArrayList<>();
        public final CopyOnWriteArrayList<String> lastRoles = new CopyOnWriteArrayList<>();
        public volatile CountDownLatch blockEntered = new CountDownLatch(0);
        public volatile CountDownLatch blockRelease = new CountDownLatch(0);

        private CallbackFixture(HttpServer server) { this.server = server; }

        private static CallbackFixture start() throws java.io.IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            CallbackFixture fixture = new CallbackFixture(server);
            server.createContext("/echo", fixture::handle);
            server.start();
            return fixture;
        }

        private void handle(com.sun.net.httpserver.HttpExchange exchange) throws java.io.IOException {
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            String token = authorization != null && authorization.startsWith("Bearer ")
                    ? authorization.substring(7) : "";
            lastToken.set(token);
            seenTokens.add(token);
            try {
                SignedJWT jwt = SignedJWT.parse(token);
                var claims = jwt.getJWTClaimsSet();
                if (!jwt.verify(new RSASSAVerifier(publicKey()))
                        || !"https://issuer.test".equals(claims.getIssuer())
                        || !claims.getAudience().contains("sidecar")
                        || claims.getExpirationTime() == null
                        || !claims.getExpirationTime().toInstant().isAfter(Instant.now())
                        || claims.getSubject() == null || claims.getSubject().isBlank()) {
                    respond(exchange, 401, "invalid token");
                    return;
                }
                lastIssuer.set(claims.getIssuer());
                lastSubject.set(claims.getSubject());
                lastRoles.clear();
                lastRoles.addAll(claims.getStringListClaim("roles"));
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String message = new ObjectMapper().readTree(body).path("message").asText();
                if ("block".equals(message)) {
                    blockEntered.countDown();
                    if (!blockRelease.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("fixture timeout");
                }
                if ("fail".equals(message)) {
                    respond(exchange, 503, "fixture-secret");
                    return;
                }
                respond(exchange, 200, "REST: " + message);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                respond(exchange, 500, "fixture interrupted");
            } catch (Exception failure) {
                respond(exchange, 401, "invalid token");
            }
        }

        private static RSAPublicKey publicKey() throws java.io.IOException {
            try (var input = SidecarApplicationFixture.class.getClassLoader().getResourceAsStream("fixtures/jwt-public.pem")) {
                return (RSAPublicKey) RsaKeyConverters.x509().convert(input);
            }
        }

        private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String value)
                throws java.io.IOException {
            byte[] body = value.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        }
    }
}
