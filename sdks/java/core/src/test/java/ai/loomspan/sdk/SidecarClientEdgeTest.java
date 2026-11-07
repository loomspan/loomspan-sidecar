package ai.loomspan.sdk;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

class SidecarClientEdgeTest {
    static final JsonMapper JSON = JsonMapper.builder().build();
    static final CredentialSnapshot CREDENTIAL = CredentialSnapshot.of("jwt-secret-sentinel");
    static final UUID ID = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
    static final String SKILL = "{\"name\":\"echo\",\"description\":\"Echo\",\"kind\":\"YAML\",\"inputSchema\":\"{}\",\"outputSchema\":null}";
    static ObjectNode envelope(String status) {
        var n = JSON.createObjectNode().put("id", ID.toString()).put("skillName", "echo")
                .put("status", status).put("createdAt", "2026-10-18T00:00:00Z");
        if (status.equals("COMPLETED") || status.equals("FAILED")) n.put("completedAt", "2026-10-18T00:00:01Z");
        if (status.equals("FAILED")) n.set("failure", JSON.createObjectNode().put("kind", "SKILL_FAILURE").put("message", "primary failure"));
        return n;
    }
    interface Handler { void handle(HttpExchange exchange) throws Exception; }
    static final class Fixture implements AutoCloseable {
        final AtomicInteger attempts = new AtomicInteger();
        final AtomicReference<String> path = new AtomicReference<>();
        final AtomicReference<String> auth = new AtomicReference<>();
        final ExecutorService executor = Executors.newCachedThreadPool(r -> { var t = new Thread(r); t.setDaemon(true); return t; });
        final HttpServer server;
        Fixture(Handler handler) throws Exception {
            server = HttpServer.create(new InetSocketAddress("localhost",0),0);
            server.setExecutor(executor);
            server.createContext("/", ex -> {
                attempts.incrementAndGet(); path.set(ex.getRequestURI().getRawPath()); auth.set(ex.getRequestHeaders().getFirst("Authorization"));
                ex.getRequestBody().readAllBytes();
                try { handler.handle(ex); } catch (Exception ignored) { } finally { ex.close(); }
            });
            server.start();
        }
        URI uri() { return URI.create("http://localhost:"+server.getAddress().getPort()); }
        SidecarClient client() { return SidecarClient.builder().baseUrl(uri()).build(); }
        public void close() { server.stop(0); executor.shutdownNow(); }
    }
    static void reply(HttpExchange ex, int status, String body) throws IOException {
        reply(ex, status, body, false);
    }
    static void reply(HttpExchange ex, int status, String body, boolean chunked) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        long length = bytes.length == 0 ? -1 : bytes.length;
        ex.sendResponseHeaders(status, chunked ? 0 : length);
        if (bytes.length != 0) ex.getResponseBody().write(bytes);
    }

    @Test void preservesOpaqueResultsAndPrimaryFailure() throws Exception {
        for (String value : Arrays.asList("", "  ", "plain text", "line\n🌻\r\n𐐷", "null", "{\"x\":true}", "{broken", null)) {
            var n=envelope("COMPLETED"); n.put("result", value);
            n.put("configurationSnapshotId",ID.toString());
            n.set("events", JSON.readTree("[{\"timestamp\":\"2026-10-18T00:00:00Z\",\"level\":\"INFO\",\"type\":\"nested\",\"details\":{\"unicode\":[\"🌻\",1,null]},\"frameId\":\"frame\",\"route\":\"root/child\"}]"));
            try (var fixture=new Fixture(ex -> reply(ex,200,n.toString())); var client=fixture.client()) {
                var result=client.getExecution(ID,CREDENTIAL);
                assertEquals(value,result.result()); assertEquals(ExecutionStatus.COMPLETED,result.status());
                assertEquals(ID,result.configurationSnapshotId()); assertEquals("frame",result.events().getFirst().frameId());
                assertEquals("🌻",result.events().getFirst().details().get("unicode").get(0).asString());
                ((ObjectNode)result.events().getFirst().details()).removeAll();
                assertTrue(result.events().getFirst().details().has("unicode"));
            }
        }
        var failed=envelope("FAILED"); failed.put("result","success-looking");
        ((ObjectNode)failed.get("failure")).set("issues",JSON.readTree("[{\"path\":\"$.x\",\"code\":\"invalid\",\"message\":\"original\"}]"));
        try (var f=new Fixture(ex -> reply(ex,200,failed.toString())); var c=f.client()) {
            var result=c.await(ID,CREDENTIAL,WaitOptions.defaults());
            assertEquals(ExecutionStatus.FAILED,result.status()); assertEquals("primary failure",result.failure().message());
            assertEquals("$.x",result.failure().issues().getFirst().path()); assertEquals(1,f.attempts.get());
        }
    }

    @Test void preservesAbsentAndBlankPrimaryFailureMessages() throws Exception {
        for (String message : Arrays.asList(null, "", "  ")) {
            var failed = envelope("FAILED");
            ((ObjectNode) failed.get("failure")).put("message", message);
            try (var f = new Fixture(ex -> reply(ex, 200, failed.toString())); var c = f.client()) {
                var result = c.getExecution(ID, CREDENTIAL);
                assertEquals(ExecutionStatus.FAILED, result.status());
                assertEquals("SKILL_FAILURE", result.failure().kind());
                assertEquals(message, result.failure().message());
                assertEquals(message, c.await(ID, CREDENTIAL, WaitOptions.defaults()).failure().message());
                assertEquals(2, f.attempts.get());
            }
        }
        var invalid = envelope("FAILED");
        ((ObjectNode) invalid.get("failure")).put("message", 42);
        try (var f = new Fixture(ex -> reply(ex, 200, invalid.toString())); var c = f.client()) {
            assertThrows(ProtocolException.class, () -> c.getExecution(ID, CREDENTIAL));
        }
    }

    @Test void rejectsLocalFailuresWithoutSendingAndEncodesOneSegment() throws Exception {
        try (var f=new Fixture(ex -> reply(ex,200,SKILL));var c=f.client()) {
            for(String name:Arrays.asList(null,""," ",".","..","/","x/y","\\","%2f","%252f","a\nb")) {
                assertThrows(LocalFailureException.class,()->c.getSkill(name,CREDENTIAL));
                assertThrows(LocalFailureException.class,()->c.submit(name,JSON.createObjectNode(),CREDENTIAL));
            }
            assertThrows(LocalFailureException.class,()->c.submit("echo",null,CREDENTIAL));
            assertThrows(LocalFailureException.class,()->c.getSkill("echo",null));
            assertThrows(LocalFailureException.class,()->c.getExecution(null,CREDENTIAL));
            assertThrows(LocalFailureException.class,()->c.await(ID,CREDENTIAL,null));
            assertEquals(0,f.attempts.get());
        }
        try(var f=new Fixture(ex->reply(ex,200,SKILL));var c=SidecarClient.builder().baseUrl(URI.create(f.uri()+"/prefix/")).build()) {
            c.getSkill("é ?#",CREDENTIAL);
            assertEquals("/prefix/v1/skills/%C3%A9%20%3F%23",f.path.get());
            assertEquals("Bearer jwt-secret-sentinel",f.auth.get());
        }
    }

    @Test void validatesConfigurationDefaultsAndClose() throws Exception {
        for (String url:List.of("/relative","file:///tmp/x","http:/missing","http://u:p@localhost","http://localhost?q=x","http://localhost#x"))
            assertThrows(LocalFailureException.class,()->SidecarClient.builder().baseUrl(URI.create(url)).build());
        assertThrows(LocalFailureException.class,()->SidecarClient.builder().build());
        for(Duration d:Arrays.asList(null,Duration.ZERO,Duration.ofMillis(-1),Duration.ofSeconds(Long.MAX_VALUE))) {
            assertThrows(LocalFailureException.class,()->SidecarClient.builder().baseUrl(URI.create("http://localhost")).connectTimeout(d).build());
            assertThrows(LocalFailureException.class,()->SidecarClient.builder().baseUrl(URI.create("http://localhost")).responseTimeout(d).build());
            assertThrows(LocalFailureException.class,()->new WaitOptions(d,Duration.ofMillis(1)));
            assertThrows(LocalFailureException.class,()->new WaitOptions(Duration.ofSeconds(1),d));
        }
        for(long limit:List.of(0L,-1L,Long.MAX_VALUE)) assertThrows(LocalFailureException.class,()->SidecarClient.builder().baseUrl(URI.create("http://localhost")).maxResponseSize(limit).build());
        assertEquals(Duration.ofSeconds(2),SidecarClient.DEFAULT_CONNECT_TIMEOUT);
        assertEquals(Duration.ofSeconds(10),SidecarClient.DEFAULT_RESPONSE_TIMEOUT);
        assertEquals(8*1024*1024,SidecarClient.DEFAULT_MAX_RESPONSE_SIZE);
        assertEquals(new WaitOptions(Duration.ofSeconds(60),Duration.ofMillis(250)),WaitOptions.defaults());
        try(var f=new Fixture(ex->reply(ex,200,SKILL))) {
            var c=f.client();c.close();c.close();
            assertThrows(LocalFailureException.class,()->c.listSkills(CREDENTIAL));
            assertThrows(LocalFailureException.class,()->c.getSkill("echo",CREDENTIAL));
            assertThrows(LocalFailureException.class,()->c.submit("echo",JSON.createObjectNode(),CREDENTIAL));
            assertThrows(LocalFailureException.class,()->c.getExecution(ID,CREDENTIAL));
            assertThrows(LocalFailureException.class,()->c.await(ID,CREDENTIAL,WaitOptions.defaults()));
            assertEquals(0,f.attempts.get());
        }
    }

    @Test void distinguishesHttpProtocolAndUnknownAcceptanceWithoutReplay() throws Exception {
        for(int status:List.of(301,302,307,308,400,401,403,404,429,503)) {
            try(var destination=new Fixture(ex->reply(ex,200,SKILL));var f=new Fixture(ex->{
                ex.getResponseHeaders().set("Location",destination.uri().toString());
                ex.getResponseHeaders().set("WWW-Authenticate","Basic realm=\"test\"");
                reply(ex,status,"{\"title\":\"problem\",\"status\":"+status+",\"issues\":[{\"message\":\"detail\"}]}");
            });var c=f.client()) {
                var e=assertThrows(HttpFailureException.class,()->c.submit("echo",JSON.createObjectNode(),CREDENTIAL));
                assertEquals(status,e.status());assertEquals("detail",e.problem().get("issues").get(0).get("message").asString());
                var read=assertThrows(HttpFailureException.class,()->c.getExecution(ID,CREDENTIAL));
                assertEquals(ID,read.executionId()); assertEquals(2,f.attempts.get());assertEquals(0,destination.attempts.get());
            }
        }
        for(String body:List.of("","{","[]","null","{}","{\"id\":123}","{\"id\":\"bad\"}","{\"id\":\""+ID+"\"", "{\"id\":\""+ID+"\",\"id\":\""+ID+"\"}")) {
            try(var f=new Fixture(ex->reply(ex,202,body));var c=f.client()) {
                var e=assertThrows(AcceptanceUnknownException.class,()->c.submit("echo",JSON.createObjectNode(),CREDENTIAL));
                assertNull(e.executionId());assertEquals(1,f.attempts.get());
            }
        }
        try(var f=new Fixture(ex->reply(ex,200,"{\"id\":\""+ID+"\"}"));var c=f.client()) {
            assertThrows(AcceptanceUnknownException.class,()->c.submit("echo",JSON.createObjectNode(),CREDENTIAL));
        }
        try(var f=new Fixture(ex->{ex.sendResponseHeaders(400,100);ex.getResponseBody().write('{');});var c=f.client()) {
            var e=assertThrows(HttpFailureException.class,()->c.submit("echo",JSON.createObjectNode(),CREDENTIAL));
            assertEquals(400,e.status());assertNull(e.problem());
        }
        // Consumed POST then closed socket; GET loss is also never retried.
        try(var f=new Fixture(ex->{});var c=f.client()) {
            assertThrows(AcceptanceUnknownException.class,()->c.submit("echo",JSON.createObjectNode().put("input","business-sentinel"),CREDENTIAL));
            var e=assertThrows(ProtocolException.class,()->c.getExecution(ID,CREDENTIAL));assertEquals(ID,e.executionId());
            assertEquals(2,f.attempts.get());
        }
    }

    @Test void validatesExecutionEnvelopeAndKeepsRequestedId() throws Exception {
        List<ObjectNode> invalid=new ArrayList<>();
        for(String field:List.of("id","skillName","status","createdAt")) {var n=envelope("RUNNING");n.remove(field);invalid.add(n);}
        for(String field:List.of("id","skillName","status","createdAt","result","configurationSnapshotId","completedAt","events","failure")) {var n=envelope("RUNNING");n.put(field,42);invalid.add(n);}
        for(String[] change:List.of(new String[]{"id",UUID.randomUUID().toString()},new String[]{"id","bad"},new String[]{"createdAt","yesterday"},new String[]{"status","UNKNOWN"})) {var n=envelope("RUNNING");n.put(change[0],change[1]);invalid.add(n);}
        var terminal=envelope("COMPLETED");terminal.remove("completedAt");invalid.add(terminal);
        var failed=envelope("FAILED");failed.remove("failure");invalid.add(failed);
        for(var n:invalid) try(var f=new Fixture(ex->reply(ex,200,n.toString()));var c=f.client()) {
            var e=assertThrows(ProtocolException.class,()->c.getExecution(ID,CREDENTIAL));assertEquals(ID,e.executionId());
        }
        var calls=new AtomicInteger();
        var wrong=envelope("RUNNING").put("id",UUID.randomUUID().toString());
        try(var f=new Fixture(ex->reply(ex,200,(calls.incrementAndGet()==1?envelope("RUNNING"):wrong).toString()));var c=f.client()) {
            var e=assertThrows(ProtocolException.class,()->c.await(ID,CREDENTIAL,new WaitOptions(Duration.ofSeconds(1),Duration.ofMillis(1))));
            assertEquals(ID,e.executionId());assertEquals(ExecutionStatus.RUNNING,e.lastSnapshot().status());assertEquals(2,calls.get());
        }
        var early=envelope("QUEUED").put("unknownFutureField",123).putNull("configurationSnapshotId");
        try(var f=new Fixture(ex->reply(ex,200,early.toString()));var c=f.client()) {
            var result=c.getExecution(ID,CREDENTIAL);assertNull(result.configurationSnapshotId());assertNull(result.events());
        }
    }

    @Test void awaitHonorsOneTotalDeadlineAndStopsOnFirstReadFailure() throws Exception {
        for(int mode=0;mode<4;mode++) {
            int behavior=mode;var reached=new CountDownLatch(1);
            try(var f=new Fixture(ex->{
                reached.countDown();
                if(behavior==0) {reply(ex,200,envelope("QUEUED").toString());return;}
                if(behavior==1) {Thread.sleep(5000);return;}
                ex.sendResponseHeaders(200,0);
                if(behavior==2) {ex.getResponseBody().write('{');ex.getResponseBody().flush();Thread.sleep(5000);}
                else while(true) {ex.getResponseBody().write(' ');ex.getResponseBody().flush();Thread.sleep(20);}
            });var c=SidecarClient.builder().baseUrl(f.uri()).responseTimeout(Duration.ofSeconds(5)).build()) {
                long start=System.nanoTime();
                var e=assertThrows(WaitTerminatedException.class,()->c.await(ID,CREDENTIAL,new WaitOptions(Duration.ofMillis(250),Duration.ofSeconds(2))));
                assertTrue(reached.await(1,TimeUnit.SECONDS));assertTrue(System.nanoTime()-start<TimeUnit.SECONDS.toNanos(1));
                assertEquals(ID,e.executionId());assertEquals(1,f.attempts.get());
                if(mode==0) assertEquals(ExecutionStatus.QUEUED,e.lastSnapshot().status());
            }
        }
        for(boolean http:List.of(true,false)) {
            var count=new AtomicInteger();
            try(var f=new Fixture(ex->{if(count.incrementAndGet()==1)reply(ex,200,envelope("RUNNING").toString());else reply(ex,http?503:200,"{broken");});var c=f.client()) {
                Class<? extends SidecarException> expected=http?HttpFailureException.class:ProtocolException.class;
                var e=assertThrows(expected,()->c.await(ID,CREDENTIAL,new WaitOptions(Duration.ofSeconds(2),Duration.ofMillis(1))));
                assertEquals(ID,e.executionId());assertEquals(ExecutionStatus.RUNNING,e.lastSnapshot().status());assertEquals(2,count.get());
            }
        }
    }

    @Test void interruptionRestoresFlagDuringSleepAndNetworkRead() throws Exception {
        for(boolean network:List.of(false,true)) {
            var reached=new CountDownLatch(1);var valid=new AtomicInteger();
            try(var f=new Fixture(ex->{
                if(valid.incrementAndGet()==1) reply(ex,200,envelope("RUNNING").toString());
                else {ex.sendResponseHeaders(200,0);ex.getResponseBody().write('{');ex.getResponseBody().flush();reached.countDown();Thread.sleep(5000);}
            });var c=f.client()) {
                AtomicReference<Throwable> error=new AtomicReference<>();AtomicBoolean flag=new AtomicBoolean();
                var thread=new Thread(()->{try{c.await(ID,CREDENTIAL,new WaitOptions(Duration.ofSeconds(10),network?Duration.ofMillis(1):Duration.ofSeconds(5)));}catch(Throwable e){error.set(e);flag.set(Thread.currentThread().isInterrupted());}});
                thread.start();
                if(network)assertTrue(reached.await(2,TimeUnit.SECONDS));else {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(thread.getState()!=Thread.State.TIMED_WAITING&&System.nanoTime()<until)Thread.sleep(1);}
                thread.interrupt();thread.join(1000);assertFalse(thread.isAlive());assertTrue(flag.get());
                var e=assertInstanceOf(WaitTerminatedException.class,error.get());assertEquals(ID,e.executionId());assertEquals(ExecutionStatus.RUNNING,e.lastSnapshot().status());
                assertEquals(network?2:1,f.attempts.get());
            }
        }
    }

    @Test void boundsResponsesReleasesResourcesAndClosesIdempotently() throws Exception {
        int size=SKILL.getBytes(StandardCharsets.UTF_8).length;
        var mode=new AtomicInteger();
        try(var f=new Fixture(ex->{
            switch(mode.get()) {
                case 0 -> reply(ex,200,SKILL);
                case 1 -> reply(ex,200,SKILL+" ");
                case 2 -> {ex.sendResponseHeaders(200,0);ex.getResponseBody().write((SKILL+" ").getBytes(StandardCharsets.UTF_8));}
                case 3 -> reply(ex,200,"{broken");
                case 4 -> {ex.sendResponseHeaders(202,size+1);ex.getResponseBody().write(' ');}
                case 5 -> {ex.sendResponseHeaders(200,size);ex.getResponseBody().write('{');}
            }
        });var c=SidecarClient.builder().baseUrl(f.uri()).maxResponseSize(size).build()) {
            assertEquals("echo",c.getSkill("echo",CREDENTIAL).name());
            for(int i=0;i<15;i++) {
                mode.set(1+i%5);
                if(mode.get()==4)assertNull(assertThrows(AcceptanceUnknownException.class,
                        ()->c.submit("echo",JSON.createObjectNode(),CREDENTIAL)).executionId());
                else assertThrows(ProtocolException.class,()->c.getSkill("echo",CREDENTIAL));
                mode.set(0);assertEquals("echo",c.getSkill("echo",CREDENTIAL).name());
            }
        }
    }

    @Test void oversizedSubmissionResponsesLeaveAcceptanceUnknown() throws Exception {
        int limit = 128;
        String response = JSON.createObjectNode().put("id", ID.toString()) + " ".repeat(limit);
        for (boolean chunked : List.of(false, true)) {
            try (var fixture = new Fixture(ex -> {
                if (ex.getRequestMethod().equals("POST")) {
                    ex.getResponseHeaders().set("Location", "/v1/executions/" + ID);
                    reply(ex, 202, response, chunked);
                } else {
                    reply(ex, 200, SKILL);
                }
            }); var client = SidecarClient.builder().baseUrl(fixture.uri()).maxResponseSize(limit).build()) {
                var failure = assertThrows(AcceptanceUnknownException.class,
                        () -> client.submit("echo", JSON.createObjectNode(), CREDENTIAL));
                assertNull(failure.executionId());
                assertNull(failure.lastSnapshot());
                assertEquals(1, fixture.attempts.get());
                assertEquals("echo", client.getSkill("echo", CREDENTIAL).name());
                assertEquals(2, fixture.attempts.get());
            }
        }
    }

    @Test void interruptionBeforeSubmissionDispatchFailsLocally() throws Exception {
        try (var fixture = new Fixture(ex -> reply(ex, 200, SKILL)); var client = fixture.client()) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(LocalFailureException.class, () -> client.submit("echo", JSON.createObjectNode(), CREDENTIAL));
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(0, fixture.attempts.get());
            } finally {
                Thread.interrupted();
            }
            assertEquals("echo", client.getSkill("echo", CREDENTIAL).name());
            assertEquals(1, fixture.attempts.get());
        }
    }

    @Test void interruptionAfterSubmissionDispatchLeavesAcceptanceUnknownAndReleasesConnections() throws Exception {
        var received = new Semaphore(0);
        var release = new CountDownLatch(1);
        try (var fixture = new Fixture(ex -> {
            if (ex.getRequestMethod().equals("POST")) {
                received.release();
                release.await(30, TimeUnit.SECONDS);
            } else {
                reply(ex, 200, SKILL);
            }
        }); var client = SidecarClient.builder().baseUrl(fixture.uri()).responseTimeout(Duration.ofSeconds(30)).build()) {
            try {
                // Exceed the connection pool's default capacity of five connections per route.
                for (int attempt = 1; attempt <= 6; attempt++) {
                    var error = new AtomicReference<SidecarException>();
                    var interrupted = new AtomicBoolean();
                    var caller = new Thread(() -> {
                        try {
                            client.submit("echo", JSON.createObjectNode(), CREDENTIAL);
                        } catch (SidecarException failure) {
                            error.set(failure);
                            interrupted.set(Thread.currentThread().isInterrupted());
                        }
                    });
                    caller.setDaemon(true);
                    caller.start();
                    try {
                        assertTrue(received.tryAcquire(2, TimeUnit.SECONDS), "POST reached the server");
                        caller.interrupt();
                        caller.join(2_000);
                        assertFalse(caller.isAlive(), "caller exits while the server still holds the response");
                        var failure = assertInstanceOf(AcceptanceUnknownException.class, error.get());
                        assertTrue(interrupted.get());
                        assertNull(failure.executionId());
                        assertNull(failure.lastSnapshot());
                        assertEquals(attempt, fixture.attempts.get());
                    } finally {
                        if (caller.isAlive()) {
                            release.countDown();
                            caller.interrupt();
                            caller.join(2_000);
                        }
                    }
                }
                assertEquals("echo", client.getSkill("echo", CREDENTIAL).name());
                assertEquals(7, fixture.attempts.get());
            } finally {
                release.countDown();
            }
        }
    }

    @Test void httpErrorsKeepStatusWithoutUnsafeProblemDetails() throws Exception {
        int limit = 128;
        String oversized = "{\"detail\":\"diagnostic-sentinel jwt-secret-sentinel\"}" + " ".repeat(limit);
        for (boolean chunked : List.of(false, true)) {
            assertHttpFailureWithoutProblem(429, oversized, chunked, limit);
        }
        assertHttpFailureWithoutProblem(503, "{broken", false, limit);
        assertHttpFailureWithoutProblem(403, "[]", false, limit);
    }

    private static void assertHttpFailureWithoutProblem(int status, String body, boolean chunked, int limit) throws Exception {
        try (var fixture = new Fixture(ex -> {
            if (ex.getRequestMethod().equals("POST")) reply(ex, status, body, chunked);
            else reply(ex, 200, SKILL);
        }); var client = SidecarClient.builder().baseUrl(fixture.uri()).maxResponseSize(limit).build()) {
            var input = JSON.createObjectNode().put("input", "business-sentinel");
            var failure = assertThrows(HttpFailureException.class, () -> client.submit("echo", input, CREDENTIAL));
            assertEquals(status, failure.status());
            assertNull(failure.problem());
            assertNull(failure.executionId());
            assertNull(failure.lastSnapshot());
            assertNull(failure.getCause());
            var diagnostic = new StringWriter();
            failure.printStackTrace(new PrintWriter(diagnostic));
            for (String sentinel : List.of("diagnostic-sentinel", "jwt-secret-sentinel", "business-sentinel"))
                assertFalse(diagnostic.toString().contains(sentinel));
            assertEquals(1, fixture.attempts.get());
            assertEquals("echo", client.getSkill("echo", CREDENTIAL).name());
            assertEquals(2, fixture.attempts.get());
        }
    }

    @Test void statusOverflowPreservesWaitContext() throws Exception {
        int limit = 256;
        String running = envelope("RUNNING").toString();
        var options = new WaitOptions(Duration.ofSeconds(2), Duration.ofMillis(1));
        for (int status : List.of(200, 503)) {
            var polls = new AtomicInteger();
            try (var fixture = new Fixture(ex -> {
                if (polls.incrementAndGet() == 1) reply(ex, 200, running);
                else reply(ex, status, running + " ".repeat(limit));
            }); var client = SidecarClient.builder().baseUrl(fixture.uri()).maxResponseSize(limit).build()) {
                Class<? extends SidecarException> expected = status == 200 ? ProtocolException.class : HttpFailureException.class;
                var failure = assertThrows(expected, () -> client.await(ID, CREDENTIAL, options));
                assertEquals(ID, failure.executionId());
                assertEquals(ID, failure.lastSnapshot().id());
                assertEquals(ExecutionStatus.RUNNING, failure.lastSnapshot().status());
                if (failure instanceof HttpFailureException httpFailure) {
                    assertEquals(503, httpFailure.status());
                    assertNull(httpFailure.problem());
                }
                assertEquals(2, fixture.attempts.get());
            }
        }
    }

    @Test void credentialAndErrorsDoNotExposeTokens() throws Exception {
        for(String token:Arrays.asList(null,""," ","x\ny","x\ry","x y","x\ty","xé","🌻"))assertThrows(LocalFailureException.class,()->CredentialSnapshot.of(token));
        assertEquals("CredentialSnapshot[redacted]",CREDENTIAL.toString());
        assertFalse(java.io.Serializable.class.isAssignableFrom(CredentialSnapshot.class));
        for(var method:CredentialSnapshot.class.getMethods()) assertFalse(method.getName().startsWith("get")&&!method.getName().equals("getClass"));
        try {assertFalse(JSON.writeValueAsString(CREDENTIAL).contains("jwt-secret-sentinel"));}catch(RuntimeException safeRefusal) {assertFalse(safeRefusal.toString().contains("jwt-secret-sentinel"));}
        var err=new ByteArrayOutputStream();PrintStream previous=System.err;System.setErr(new PrintStream(err));
        try(var f=new Fixture(ex->reply(ex,400,"{\"detail\":\"jwt-secret-sentinel\",\"jwt-secret-sentinel\":[\"echo jwt-secret-sentinel\"]}"));var c=f.client()) {
            var e=assertThrows(HttpFailureException.class,()->c.submit("echo",JSON.createObjectNode().put("input","business-sentinel"),CREDENTIAL));
            var stack=new StringWriter();e.printStackTrace(new PrintWriter(stack));
            assertFalse(stack.toString().contains("jwt-secret-sentinel"));assertFalse(stack.toString().contains("business-sentinel"));
            assertFalse(e.problem().toString().contains("jwt-secret-sentinel"));assertNull(e.getCause());
        } finally {System.setErr(previous);}
        assertFalse(err.toString().contains("jwt-secret-sentinel"));assertFalse(err.toString().contains("business-sentinel"));
    }
}
