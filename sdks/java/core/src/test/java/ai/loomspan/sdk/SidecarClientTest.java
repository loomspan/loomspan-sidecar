package ai.loomspan.sdk;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import tools.jackson.databind.json.JsonMapper;

class SidecarClientTest {
    @Test void discoversSubmitsAndAwaitsOpaqueResult() throws Exception {
        UUID id = UUID.randomUUID();
        var reads = new AtomicInteger();
        var submissions = new AtomicInteger();
        var discovery = new AtomicInteger();
        String skill = "{\"name\":\"echo\",\"description\":\"Echo\",\"kind\":\"YAML\",\"inputSchema\":\"{}\",\"outputSchema\":null}";
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/prefix/v1", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.contains("/executions/")) reads.incrementAndGet();
            else if (exchange.getRequestMethod().equals("POST")) submissions.incrementAndGet();
            else discovery.incrementAndGet();
            String status = reads.get() == 1 ? "QUEUED" : reads.get() == 2 ? "RUNNING" : "COMPLETED";
            String body = path.endsWith("/executions") ? "{\"id\":\"" + id + "\"}"
                    : path.contains("/executions/") ? "{\"id\":\"" + id + "\",\"skillName\":\"echo\",\"status\":\"" + status + "\",\"createdAt\":\"2026-10-18T00:00:00Z\",\"completedAt\":\"2026-10-18T00:00:01Z\",\"result\":\"not JSON 🌻\"}"
                    : path.endsWith("/echo") ? skill : "[" + skill + "]";
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "http://invalid.example/ignored");
            exchange.sendResponseHeaders(exchange.getRequestMethod().equals("POST") ? 202 : 200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try (var client = SidecarClient.builder().baseUrl(java.net.URI.create("http://localhost:" + server.getAddress().getPort() + "/prefix/")).build()) {
            var credential = CredentialSnapshot.of("test-token");
            var descriptor = client.listSkills(credential).getFirst();
            assertEquals("echo", descriptor.name());
            assertEquals("Echo", descriptor.description());
            assertEquals("YAML", descriptor.kind());
            assertEquals("{}", descriptor.inputSchema());
            assertNull(client.getSkill("echo", credential).outputSchema());
            var input = JsonMapper.builder().build().createObjectNode();
            input.set("nested", JsonMapper.builder().build().createObjectNode().put("arbitrary", true));
            assertEquals(id, client.submit("echo", input, credential).id());
            assertEquals(ExecutionStatus.QUEUED, client.getExecution(id, credential).status());
            var terminal = client.await(id, credential, new WaitOptions(Duration.ofSeconds(1), Duration.ofMillis(10)));
            assertEquals(ExecutionStatus.COMPLETED, terminal.status());
            assertEquals("not JSON 🌻", terminal.result());
            assertEquals(3, reads.get());
            assertEquals(1, submissions.get());
            assertEquals(2, discovery.get());
        } finally { server.stop(0); }
    }
}
