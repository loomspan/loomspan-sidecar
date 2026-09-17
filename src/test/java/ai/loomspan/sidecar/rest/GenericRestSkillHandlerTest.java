package ai.loomspan.sidecar.rest;

import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillException;
import ai.loomspan.sidecar.config.RestRoutesProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenericRestSkillHandlerTest {
    @TempDir Path temporaryDirectory;
    private final List<GenericRestSkillHandler> handlers = new CopyOnWriteArrayList<>();
    private final List<HttpServer> servers = new CopyOnWriteArrayList<>();

    @AfterEach
    void closeResources() {
        SecurityContextHolder.clearContext();
        handlers.forEach(GenericRestSkillHandler::stop);
        servers.forEach(server -> server.stop(0));
    }

    @Test
    void rejectsNullPathAndNonScalarQueryValuesBeforeSending() throws Exception {
        var requests = new AtomicInteger();
        var server = server(exchange -> { requests.incrementAndGet(); respond(exchange, 200, "text/plain", "ok"); });
        var handler = handler(routes(server, "lookup: {target: callback, method: GET, path: '/things/{id}'}",
                "none", "", "1KB", "2s"));
        for (var input : List.of(Map.<String, Object>of(), linked("id", null))) {
            assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("lookup", input, "test-generation")))
                    .isInstanceOf(SkillException.class).hasMessageContaining("path input 'id'", "non-null scalar");
        }
        for (Object query : java.util.Arrays.asList(null, List.of("one", "two"), List.of(), Map.of("key", "value"))) {
            assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("lookup", linked("id", 1, "q", query), "test-generation")))
                    .isInstanceOf(SkillException.class).hasMessageContaining("query input 'q'", "non-null scalar");
        }
        assertThat(requests).hasValue(0);
        // Omitted query values are absent, rather than required or encoded as null.
        assertThat(handler.handle(new RestSkillInvocation("lookup", Map.of("id", 1), "test-generation"))).isEqualTo("ok");
        assertThat(requests).hasValue(1);
    }

    @Test
    void rejectsNestedResourcesFilesAndStreamsBeforeSending() throws Exception {
        var requests = new AtomicInteger();
        var server = server(exchange -> { requests.incrementAndGet(); respond(exchange, 200, "text/plain", "ok"); });
        var handler = handler(routes(server, "post: {target: callback, method: POST, path: /things}",
                "none", "", "1KB", "2s"));
        try (var stream = new java.io.ByteArrayInputStream(new byte[] {1})) {
            for (Object value : List.of(new org.springframework.core.io.ByteArrayResource(new byte[] {1}),
                    temporaryDirectory.resolve("file").toFile(), temporaryDirectory.resolve("file"), stream)) {
                assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("post",
                        Map.of("nested", List.of(Map.of("value", value))), "test-generation")))
                        .isInstanceOf(SkillException.class).hasMessageContaining("non-JSON", "input.nested[0].value");
            }
        }
        assertThat(requests).hasValue(0);
    }

    @Test
    void preservesBasePrefixesAndSendsEmptyObjectWhenPathConsumesAllPostInput() throws Exception {
        var paths = new CopyOnWriteArrayList<String>();
        var bodies = new CopyOnWriteArrayList<String>();
        var server = server(exchange -> {
            paths.add(exchange.getRequestURI().toString());
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            assertThat(exchange.getRequestHeaders().getFirst("Content-Type")).contains("application/json");
            respond(exchange, 200, "text/plain", "ok");
        });
        for (String prefix : List.of("/api", "/api/")) {
            var file = routes(server, "post: {target: callback, method: POST, path: '/things/{id}'}",
                    "none", "", "1KB", "2s");
            Files.writeString(file, Files.readString(file).replace("/api/", prefix));
            assertThat(handler(file).handle(new RestSkillInvocation("post", Map.of("id", "a/b"), "test-generation")))
                    .isEqualTo("ok");
        }
        assertThat(paths).containsExactly("/api/things/a%2Fb", "/api/things/a%2Fb");
        assertThat(bodies).containsExactly("{}", "{}");
    }

    @Test
    void bindsGetPathSegmentsAndRemainingScalarQueryValues() throws Exception {
        var captured = new java.util.concurrent.atomic.AtomicReference<HttpExchange>();
        HttpServer server = server(exchange -> {
            captured.set(exchange);
            respond(exchange, 200, "text/plain", "ok");
        });
        var handler = handler(routes(server, """
                lookup: {target: callback, method: GET, path: '/expenses/{category}'}
                """, "none", "", "1KB", "2s"));
        String result = handler.handle(new RestSkillInvocation("lookup", linked(
                "category", "café/a?b#c%+&", "enabled", true, "count", 2,
                "filter", "café/a?b#c%+&="), "test-generation"));
        assertThat(result).isEqualTo("ok");
        assertThat(captured.get().getRequestURI().getRawPath()).isEqualTo("/api/expenses/caf%C3%A9%2Fa%3Fb%23c%25+&");
        assertThat(captured.get().getRequestURI().getRawQuery())
                .isEqualTo("count=2&enabled=true&filter=caf%C3%A9/a?b%23c%25%2B%26%3D");
        assertThat(captured.get().getRequestHeaders().getFirst("Accept")).isEqualTo("application/json, text/*");
    }

    @Test
    void preservesPlusAndSpaceInDecodedQueryNamesAndValues() throws Exception {
        var query = new java.util.concurrent.atomic.AtomicReference<String>();
        var server = server(exchange -> {
            query.set(java.net.URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8));
            respond(exchange, 200, "text/plain", "ok");
        });
        var handler = handler(routes(server, "lookup: {target: callback, method: GET, path: /lookup}",
                "none", "", "1KB", "2s"));
        handler.handle(new RestSkillInvocation("lookup", Map.of("a+b c", "+1 555+0100"), "test-generation"));
        assertThat(query).hasValue("a+b c=+1 555+0100");
    }

    @Test
    void doesNotReplayTargetCookiesBetweenJwtCallers() throws Exception {
        var requests = new CopyOnWriteArrayList<Map<String, String>>();
        var server = server(exchange -> {
            requests.add(Map.of("authorization", exchange.getRequestHeaders().getFirst("Authorization"),
                    "cookie", java.util.Objects.toString(exchange.getRequestHeaders().getFirst("Cookie"), "")));
            exchange.getResponseHeaders().set("Set-Cookie", "session=first-caller; Path=/");
            respond(exchange, 200, "text/plain", "ok");
        });
        var handler = handler(routes(server, "lookup: {target: callback, method: GET, path: /lookup}",
                "caller-passthrough", "", "1KB", "2s"));
        for (String caller : List.of("first-caller", "second-caller")) {
            var jwt = Jwt.withTokenValue(caller).header("alg", "none").subject(caller).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
            handler.handle(new RestSkillInvocation("lookup", Map.of(), "test-generation"));
        }
        assertThat(requests).containsExactly(
                Map.of("authorization", "Bearer first-caller", "cookie", ""),
                Map.of("authorization", "Bearer second-caller", "cookie", ""));
    }

    @Test
    void appliesEachConnectTimeoutAtTheSocketBoundaryWithoutRetrying() throws Exception {
        var server = server(exchange -> respond(exchange, 200, "text/plain", "unexpected"));
        for (int timeout : List.of(175, 500)) {
            Path file = routes(server, "lookup: {target: callback, method: GET, path: /lookup}",
                    "none", "", "1KB", "5s");
            Files.writeString(file, Files.readString(file).replace("connect-timeout: 500ms",
                    "connect-timeout: " + timeout + "ms"));
            var handler = handler(file);
            // Make the OS connect boundary fail deterministically, without a remote blackhole
            // or platform-dependent loopback backlog saturation.
            try (var sockets = org.mockito.Mockito.mockConstruction(java.net.Socket.class, (socket, context) -> {
                org.mockito.Mockito.doThrow(new java.net.SocketTimeoutException("fixture connect timeout"))
                        .when(socket).connect(org.mockito.ArgumentMatchers.any(java.net.SocketAddress.class),
                                org.mockito.ArgumentMatchers.anyInt());
            })) {
                var failure = org.assertj.core.api.Assertions.catchThrowable(() -> handler.handle(new RestSkillInvocation("lookup", Map.of(), "test-generation")));
                assertThat(failure).isInstanceOf(SkillException.class).hasMessageContaining("transport error");
                assertThat(Thread.currentThread().isInterrupted()).isFalse();
                assertThat(sockets.constructed()).hasSize(1);
                org.mockito.Mockito.verify(sockets.constructed().getFirst()).connect(
                        org.mockito.ArgumentMatchers.any(java.net.SocketAddress.class), org.mockito.ArgumentMatchers.eq(timeout));
            }
        }
    }

    @Test
    void acceptsExactChunkedLimitAndRejectsMissingContentType() throws Exception {
        var server = server(exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("missing")) {
                respond(exchange, 200, null, "body");
            } else {
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(200, 0);
                try (var output = exchange.getResponseBody()) {
                    output.write("12".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    output.write("345".getBytes(StandardCharsets.UTF_8));
                }
            }
        });
        var handler = handler(routes(server, """
                exact: {target: callback, method: GET, path: /exact}
                missing: {target: callback, method: GET, path: /missing}
                """, "none", "", "5B", "5s"));
        assertThat(handler.handle(new RestSkillInvocation("exact", Map.of(), "test-generation"))).isEqualTo("12345");
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("missing", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("unsupported response Content-Type");
    }

    @Test
    void abortsOversizeAndErrorStreamsWithoutWaitingForTheirRemainder() throws Exception {
        for (int status : List.of(200, 503)) {
            var entered = new java.util.concurrent.CountDownLatch(1);
            var release = new java.util.concurrent.CountDownLatch(1);
            var server = server(exchange -> {
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
                exchange.sendResponseHeaders(status, 0);
                try (var output = exchange.getResponseBody()) {
                    output.write("SECRET".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    entered.countDown();
                    release.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });
            var handler = handler(routes(server, "lookup: {target: callback, method: GET, path: /lookup}",
                    "none", "", "5B", "5s"));
            var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                var outcome = worker.submit(() -> org.assertj.core.api.Assertions.catchThrowable(
                        () -> handler.handle(new RestSkillInvocation("lookup", Map.of(), "test-generation"))));
                assertThat(entered.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                assertThat(outcome.get(2, java.util.concurrent.TimeUnit.SECONDS))
                        .isInstanceOf(SkillException.class)
                        .hasMessageContaining(status == 200 ? "byte limit" : "HTTP status 503")
                        .hasMessageNotContaining("SECRET");
            } finally {
                release.countDown();
                worker.shutdownNow();
            }
        }
    }

    @Test
    void serializesPostAndAppliesAuthenticationIndependentlyPerTarget() throws Exception {
        var bodies = new CopyOnWriteArrayList<String>();
        var auth = new CopyOnWriteArrayList<String>();
        HttpServer server = server(exchange -> {
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "application/json", "{\"ok\":true}");
        });
        var staticHandler = handler(routes(server, """
                write: {target: callback, method: POST, path: '/customers/{id}'}
                """, "static", "headers: {Authorization: 'Service secret'}", "1KB", "2s"));
        assertThat(staticHandler.handle(new RestSkillInvocation("write", linked(
                "id", 7, "nullable", null, "nested", Map.of("items", List.of(true, 3))), "test-generation")))
                .isEqualTo("{\"ok\":true}");
        assertThat(bodies.getFirst()).isEqualTo("{\"nullable\":null,\"nested\":{\"items\":[true,3]}}");
        assertThat(auth.getFirst()).isEqualTo("Service secret");

        var passthrough = handler(routes(server, """
                write: {target: callback, method: POST, path: /passthrough}
                """, "caller-passthrough", "", "1KB", "2s"));
        assertThatThrownBy(() -> passthrough.handle(new RestSkillInvocation("write", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("requires JWT");
        Jwt jwt = Jwt.withTokenValue("original-token").header("alg", "none")
                .claim("sub", "caller").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt));
        SecurityContextHolder.setContext(context);
        passthrough.handle(new RestSkillInvocation("write", Map.of(), "test-generation"));
        assertThat(auth.getLast()).isEqualTo("Bearer original-token");
    }

    @Test
    void rejectsUnsafeAndNonJsonInputsBeforeOutboundCall() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(exchange -> { requests.incrementAndGet(); respond(exchange, 200, "text/plain", "ok"); });
        var handler = handler(routes(server, """
                lookup: {target: callback, method: GET, path: '/things/{id}'}
                post: {target: callback, method: POST, path: /things}
                """, "none", "", "1KB", "2s"));
        for (Object value : List.of(".", "..", "../child", "%2e%2e", "%252e%252e",
                "%2f", "%255c", List.of("x"), Map.of("x", 1))) {
            assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("lookup", Map.of("id", value), "test-generation")))
                    .isInstanceOf(SkillException.class);
        }
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("lookup", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class);
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("post", Map.of("bad", new Object()), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("non-JSON");
        assertThat(requests).hasValue(0);
    }

    @Test
    void boundsBodiesMediaDiagnosticsRedirectsRetriesAndReadTimeout() throws Exception {
        AtomicInteger source = new AtomicInteger();
        AtomicInteger destination = new AtomicInteger();
        HttpServer destinationServer = server(exchange -> { destination.incrementAndGet(); respond(exchange, 200, "text/plain", "followed"); });
        HttpServer sourceServer = server(exchange -> {
            int call = source.incrementAndGet();
            switch (exchange.getRequestURI().getPath()) {
                case "/api/exact" -> respond(exchange, 200, "text/plain", "12345");
                case "/api/over" -> respond(exchange, 200, "text/plain", "123456");
                case "/api/empty" -> respond(exchange, 204, null, "");
                case "/api/problem" -> respond(exchange, 200, "application/problem+json", "{}");
                case "/api/latin" -> respond(exchange, 200, "text/plain; charset=ISO-8859-1",
                        "café".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                case "/api/malformed" -> respond(exchange, 200, "text/plain; charset=UTF-8",
                        new byte[] {(byte) 0xc3, (byte) 0x28});
                case "/api/empty-unsupported" -> respond(exchange, 200, "application/octet-stream", new byte[0]);
                case "/api/binary" -> respond(exchange, 200, "application/octet-stream", "BIN");
                case "/api/nonapplication-json" -> respond(exchange, 200, "image/json", "{}");
                case "/api/error" -> respond(exchange, 503, "text/plain", "SECRET-BODY");
                case "/api/redirect" -> {
                    exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + destinationServer.getAddress().getPort() + "/next");
                    respond(exchange, 302, null, "");
                }
                case "/api/slow" -> {
                    try { Thread.sleep(300); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                    respond(exchange, 200, "text/plain", "late");
                }
                default -> throw new AssertionError("Unexpected request " + call);
            }
        });
        String routes = """
                exact: {target: callback, method: GET, path: /exact}
                over: {target: callback, method: GET, path: /over}
                empty: {target: callback, method: GET, path: /empty}
                problem: {target: callback, method: GET, path: /problem}
                latin: {target: callback, method: GET, path: /latin}
                malformed: {target: callback, method: GET, path: /malformed}
                emptyUnsupported: {target: callback, method: GET, path: /empty-unsupported}
                binary: {target: callback, method: GET, path: /binary}
                nonApplicationJson: {target: callback, method: GET, path: /nonapplication-json}
                error: {target: callback, method: GET, path: /error}
                redirect: {target: callback, method: GET, path: /redirect}
                slow: {target: callback, method: GET, path: /slow}
                """;
        var handler = handler(routes(sourceServer, routes, "none", "", "5B", "100ms"));
        assertThat(handler.handle(new RestSkillInvocation("exact", Map.of(), "test-generation"))).isEqualTo("12345");
        assertThat(handler.handle(new RestSkillInvocation("empty", Map.of(), "test-generation"))).isEmpty();
        assertThat(handler.handle(new RestSkillInvocation("problem", Map.of(), "test-generation"))).isEqualTo("{}");
        assertThat(handler.handle(new RestSkillInvocation("latin", Map.of(), "test-generation"))).isEqualTo("café");
        assertThat(handler.handle(new RestSkillInvocation("emptyUnsupported", Map.of(), "test-generation"))).isEmpty();
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("malformed", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("invalid for its charset");
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("over", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("byte limit");
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("binary", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("unsupported").hasMessageNotContaining("SECRET");
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("nonApplicationJson", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("unsupported");
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("error", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("503").hasMessageNotContaining("SECRET");
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("redirect", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("302");
        assertThat(destination).hasValue(0);
        assertThatThrownBy(() -> handler.handle(new RestSkillInvocation("slow", Map.of(), "test-generation")))
                .isInstanceOf(SkillException.class).hasMessageContaining("transport error");
        assertThat(source).hasValue(12);
    }

    private GenericRestSkillHandler handler(Path routes) {
        RestRoutesProperties properties = new RestRoutesProperties();
        properties.setRestRoutesLocation(routes.toUri().toString());
        var beanFactory = new DefaultListableBeanFactory();
        var loader = new RestRouteLoader(properties, new MockEnvironment(), new DefaultResourceLoader(),
                beanFactory.getBeanProvider(SslBundles.class));
        var clients = new RestTargetClients(loader, beanFactory.getBeanProvider(SslBundles.class));
        var handler = new GenericRestSkillHandler(loader, clients);
        handlers.add(handler);
        return handler;
    }

    private Path routes(HttpServer server, String routes, String mode, String authExtra,
                        String maxSize, String readTimeout) throws Exception {
        String auth = authExtra.isBlank() ? "mode: " + mode : "mode: " + mode + ", " + authExtra;
        Path file = temporaryDirectory.resolve("routes-" + java.util.UUID.randomUUID() + ".yaml");
        Files.writeString(file, """
                targets:
                  callback:
                    base-url: http://127.0.0.1:%d/api/
                    auth: {%s}
                    connect-timeout: 500ms
                    read-timeout: %s
                    max-response-size: %s
                routes:
                %s
                """.formatted(server.getAddress().getPort(), auth, readTimeout, maxSize,
                routes.indent(2)));
        return file;
    }

    private HttpServer server(com.sun.net.httpserver.HttpHandler handler) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler);
        server.start();
        servers.add(server);
        return server;
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws java.io.IOException {
        respond(exchange, status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] bytes) throws java.io.IOException {
        if (contentType != null) exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        try (var output = exchange.getResponseBody()) { if (status != 204) output.write(bytes); }
    }

    private static Map<String, Object> linked(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) values.put((String) pairs[index], pairs[index + 1]);
        return values;
    }
}
