package ai.loomspan.sidecar.support;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.security.converter.RsaKeyConverters;

/** Loopback application that independently authenticates and authorizes record reads. */
public final class RemoteAuthoringApplicationFixture implements AutoCloseable {
    private final HttpServer server;
    public final AtomicInteger allowed = new AtomicInteger();
    public final AtomicInteger denied = new AtomicInteger();
    private final Map<String, String> owners = Map.of("alice-record", "alice", "bob-record", "bob");

    public RemoteAuthoringApplicationFixture() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/records/", this::read);
            server.start();
        } catch (IOException failure) { throw new IllegalStateException(failure); }
    }

    public String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private void read(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equals(exchange.getRequestMethod())) { respond(exchange, 405, "method denied"); return; }
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            if (header == null || !header.startsWith("Bearer ")) { respond(exchange, 401, "unauthorized"); return; }
            SignedJWT jwt = SignedJWT.parse(header.substring(7));
            var claims = jwt.getJWTClaimsSet();
            try (var key = getClass().getClassLoader().getResourceAsStream("fixtures/jwt-public.pem")) {
                RSAPublicKey publicKey = (RSAPublicKey) RsaKeyConverters.x509().convert(key);
                if (!jwt.verify(new RSASSAVerifier(publicKey)) ||
                        !"https://issuer.test".equals(claims.getIssuer()) ||
                        !claims.getAudience().contains("sidecar") ||
                        claims.getExpirationTime() == null ||
                        !claims.getExpirationTime().toInstant().isAfter(Instant.now()) ||
                        claims.getSubject() == null || claims.getSubject().isBlank() ||
                        !claims.getStringListClaim("roles").contains("RECORD_READER")) {
                    respond(exchange, 401, "unauthorized"); return;
                }
            }
            String recordId = exchange.getRequestURI().getPath().substring("/records/".length());
            if (!claims.getSubject().equals(owners.get(recordId))) {
                denied.incrementAndGet();
                respond(exchange, 403, "record denied"); return;
            }
            allowed.incrementAndGet();
            respond(exchange, 200, "{\"recordId\":\"" + recordId + "\"}");
        } catch (Exception failure) {
            respond(exchange, 401, "unauthorized");
        }
    }

    private static void respond(HttpExchange exchange, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", status == 200 ? "application/json" : "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var body = exchange.getResponseBody()) { body.write(bytes); }
    }

    @Override public void close() { server.stop(0); }
}
