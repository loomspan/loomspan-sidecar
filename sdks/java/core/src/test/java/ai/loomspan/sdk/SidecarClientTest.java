package ai.loomspan.sdk;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.json.JsonMapper;

class SidecarClientTest {
    @Test void discoversSubmitsAndAwaitsOpaqueResult() throws Exception {
        UUID id = UUID.randomUUID();
        var reads = new AtomicInteger();
        var requests = new CopyOnWriteArrayList<Request>();
        var json = JsonMapper.builder().build();
        String skill = """
                {"namespace":"order-support","localName":"lookup-3pl","description":"Echo","kind":"YAML",
                 "inputSchema":"{}","outputSchema":null}
                """;
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/prefix/v1", exchange -> {
            String path = exchange.getRequestURI().getPath();
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
            if (path.contains("/executions/")) reads.incrementAndGet();
            String status = reads.get() == 1 ? "QUEUED" : reads.get() == 2 ? "RUNNING" : "COMPLETED";
            String body = path.endsWith("/executions") ? "{\"id\":\"" + id + "\"}"
                    : path.contains("/executions/") ? """
                            {"id":"%s","namespace":"order-support","localName":"lookup-3pl","status":"%s",
                             "createdAt":"2026-10-18T00:00:00Z","completedAt":"2026-10-18T00:00:01Z",
                             "result":"not JSON 🌻"}
                            """.formatted(id, status)
                    : path.endsWith("/lookup-3pl") ? skill : "[" + skill + "]";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Location", "http://invalid.example/ignored");
            exchange.sendResponseHeaders(exchange.getRequestMethod().equals("POST") ? 202 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var client = SidecarClient.builder().baseUrl(java.net.URI.create("http://localhost:" + server.getAddress().getPort() + "/prefix/")).build()) {
            var credential = CredentialSnapshot.of("test-token");
            var descriptor = client.listSkills("order-support", credential).getFirst();
            assertEquals("order-support", descriptor.namespace());
            assertEquals("lookup-3pl", descriptor.localName());
            assertEquals("Echo", descriptor.description());
            assertEquals("YAML", descriptor.kind());
            assertEquals("{}", descriptor.inputSchema());
            var detail = client.getSkill(descriptor.namespace(), descriptor.localName(), credential);
            assertEquals(descriptor, detail);
            assertNull(detail.outputSchema());
            var input = json.createObjectNode();
            input.set("nested", json.createObjectNode().put("arbitrary", true));
            assertEquals(id, client.submit(descriptor.namespace(), descriptor.localName(), input, credential).id());
            assertEquals(ExecutionStatus.QUEUED, client.getExecution(id, credential).status());
            var terminal = client.await(id, credential, new WaitOptions(Duration.ofSeconds(1), Duration.ofMillis(10)));
            assertEquals(ExecutionStatus.COMPLETED, terminal.status());
            assertEquals(descriptor.namespace(), terminal.namespace());
            assertEquals(descriptor.localName(), terminal.localName());
            assertEquals("not JSON 🌻", terminal.result());
            assertEquals(List.of("GET /prefix/v1/namespaces/order-support/skills",
                    "GET /prefix/v1/namespaces/order-support/skills/lookup-3pl",
                    "POST /prefix/v1/namespaces/order-support/skills/lookup-3pl/executions",
                    "GET /prefix/v1/executions/" + id, "GET /prefix/v1/executions/" + id,
                    "GET /prefix/v1/executions/" + id),
                    requests.stream().map(request -> request.method() + " " + request.path()).toList());
            assertTrue(requests.stream().allMatch(request -> "Bearer test-token".equals(request.authorization())));
            assertEquals(input, json.readTree(requests.get(2).body()));
        } finally { server.stop(0); }
    }

    private record Request(String method, String path, String authorization, String body) {}
}
