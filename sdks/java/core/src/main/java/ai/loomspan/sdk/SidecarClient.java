package ai.loomspan.sdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Blocking, single-attempt transport. Closing or ending a wait does not cancel server work. */
public final class SidecarClient implements AutoCloseable {
    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(10);
    static final long DEFAULT_MAX_RESPONSE_SIZE = 8L * 1024 * 1024;
    private final String base;
    private final long responseNanos;
    private final long connectNanos;
    private final long limit;
    private final CloseableHttpClient http;
    private final ScheduledThreadPoolExecutor timer;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final JsonMapper json = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

    private SidecarClient(Builder builder) {
        base = base(builder.baseUrl);
        connectNanos = nanos(builder.connectTimeout);
        responseNanos = nanos(builder.responseTimeout);
        if (builder.maxResponseSize <= 0 || builder.maxResponseSize > Integer.MAX_VALUE - 8)
            throw new LocalFailureException("Invalid response size limit");
        limit = builder.maxResponseSize;
        var tls = ClientTlsStrategyBuilder.create();
        if (builder.sslContext != null) tls.setSslContext(builder.sslContext);
        var pool = PoolingHttpClientConnectionManagerBuilder.create()
                .setTlsSocketStrategy(tls.buildClassic())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(connectNanos, TimeUnit.NANOSECONDS))
                        .setSocketTimeout(Timeout.of(responseNanos, TimeUnit.NANOSECONDS)).build()).build();
        http = HttpClients.custom().setConnectionManager(pool)
                .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement()
                .disableAuthCaching().disableContentCompression().build();
        timer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "loomspan-sdk-deadlines"); thread.setDaemon(true); return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
    }

    public static Builder builder() { return new Builder(); }
    public static final class Builder {
        private URI baseUrl;
        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration responseTimeout = DEFAULT_RESPONSE_TIMEOUT;
        private long maxResponseSize = DEFAULT_MAX_RESPONSE_SIZE;
        private SSLContext sslContext;
        private Builder() {}
        public Builder baseUrl(URI value) { baseUrl = value; return this; }
        public Builder connectTimeout(Duration value) { connectTimeout = value; return this; }
        public Builder responseTimeout(Duration value) { responseTimeout = value; return this; }
        public Builder maxResponseSize(long value) { maxResponseSize = value; return this; }
        public Builder sslContext(SSLContext value) {
            if (value == null) throw new LocalFailureException("Missing SSL context");
            sslContext = value; return this;
        }
        public SidecarClient build() { return new SidecarClient(this); }
    }

    public List<SkillDescriptor> listSkills(CredentialSnapshot credential) {
        JsonNode node = request("GET", "/v1/skills", null, credential, null, null, 0, false);
        try {
            if (!node.isArray()) throw new IllegalArgumentException();
            List<SkillDescriptor> skills = new ArrayList<>();
            for (var entry : node) skills.add(skill(entry));
            return List.copyOf(skills);
        } catch (RuntimeException invalid) { throw new ProtocolException(null, null); }
    }

    public SkillDescriptor getSkill(String name, CredentialSnapshot credential) {
        JsonNode node = request("GET", "/v1/skills/" + segment(name), null, credential, null, null, 0, false);
        try { return skill(node); } catch (RuntimeException invalid) { throw new ProtocolException(null, null); }
    }

    public AcceptedExecution submit(String name, ObjectNode input, CredentialSnapshot credential) {
        String path = "/v1/skills/" + segment(name) + "/executions";
        if (input == null) throw new LocalFailureException("Missing object input");
        byte[] body;
        try { body = json.writeValueAsBytes(input); }
        catch (RuntimeException invalid) { throw new LocalFailureException("Invalid object input"); }
        JsonNode node = request("POST", path, body, credential, null, null, 0, true);
        try { return new AcceptedExecution(uuid(text(node, "id", true))); }
        catch (RuntimeException invalid) { throw new AcceptanceUnknownException(null, null); }
    }

    public ExecutionSnapshot getExecution(UUID id, CredentialSnapshot credential) {
        return read(id, credential, null, 0);
    }

    private ExecutionSnapshot read(UUID id, CredentialSnapshot credential, ExecutionSnapshot last, long deadline) {
        if (id == null) throw new LocalFailureException("Missing execution ID");
        JsonNode node = request("GET", "/v1/executions/" + id, null, credential, id, last, deadline, false);
        try { return snapshot(node, id); }
        catch (RuntimeException invalid) { throw new ProtocolException(id, last); }
    }

    public ExecutionSnapshot await(UUID id, CredentialSnapshot credential, WaitOptions options) {
        validate(credential);
        if (id == null || options == null) throw new LocalFailureException("Missing wait arguments");
        long deadline = System.nanoTime() + nanos(options.timeout());
        long interval = nanos(options.pollInterval());
        ExecutionSnapshot last = null;
        while (true) {
            if (Thread.currentThread().isInterrupted() || deadline - System.nanoTime() <= 0)
                throw new WaitTerminatedException(id, last);
            last = read(id, credential, last, deadline);
            if (last.status().terminal()) return last;
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new WaitTerminatedException(id, last);
            try { TimeUnit.NANOSECONDS.sleep(Math.min(interval, remaining)); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new WaitTerminatedException(id, last);
            }
        }
    }

    private JsonNode request(String method, String path, byte[] body, CredentialSnapshot credential,
            UUID id, ExecutionSnapshot last, long waitDeadline, boolean submission) {
        validate(credential);
        Thread caller = Thread.currentThread();
        checkDispatchInterrupt(caller, submission, id, last);
        long now = System.nanoTime();
        long budget = waitDeadline == 0 ? responseNanos : Math.min(responseNanos, waitDeadline - now);
        if (budget <= 0) throw new WaitTerminatedException(id, last);
        long deadline = now + budget;
        HttpUriRequestBase request = new HttpUriRequestBase(method, URI.create(base + path));
        request.setHeader("Authorization", credential.authorization());
        request.setHeader("Accept", "application/json");
        request.setConfig(RequestConfig.custom().setAuthenticationEnabled(false)
                .setConnectionRequestTimeout(Timeout.of(Math.min(connectNanos, budget), TimeUnit.NANOSECONDS))
                .setResponseTimeout(Timeout.of(budget, TimeUnit.NANOSECONDS)).build());
        if (body != null) request.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_JSON));
        java.util.concurrent.ScheduledFuture<?> abort;
        try {
            abort = timer.scheduleAtFixedRate(() -> {
                if (caller.isInterrupted() || deadline - System.nanoTime() <= 0) request.cancel();
            }, 0, Math.min(budget, TimeUnit.MILLISECONDS.toNanos(5)), TimeUnit.NANOSECONDS);
        } catch (RejectedExecutionException closedDuringCall) {
            throw new LocalFailureException("Client is closed");
        }
        CloseableHttpResponse response = null;
        int observedStatus = 0;
        try {
            checkDispatchInterrupt(caller, submission, id, last);
            response = http.execute(request);
            observedStatus = response.getCode();
            var entity = response.getEntity();
            byte[] bytes = new byte[0];
            if (entity != null) {
                if (entity.getContentLength() > limit) throw responseFailure(observedStatus, submission, id, last);
                InputStream stream = entity.getContent();
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer, 0, (int) Math.min(buffer.length, limit + 1 - out.size()))) != -1) {
                    if (deadline - System.nanoTime() <= 0 || caller.isInterrupted()) throw new IOException();
                    out.write(buffer, 0, count);
                    if (out.size() > limit) throw responseFailure(observedStatus, submission, id, last);
                }
                bytes = out.toByteArray();
            }
            if (deadline - System.nanoTime() <= 0 || caller.isInterrupted()) throw new IOException();
            if (isHttpFailure(observedStatus)) {
                JsonNode problem = null;
                try {
                    JsonNode parsed = json.readTree(bytes);
                    if (parsed != null && parsed.isObject()) problem = redact(parsed, credential);
                } catch (RuntimeException ignored) { /* A non-problem error body is never exposed. */ }
                throw new HttpFailureException(observedStatus, problem, id, last);
            }
            if (observedStatus != (submission ? 202 : 200)) throw responseFailure(observedStatus, submission, id, last);
            try {
                JsonNode node = json.readTree(bytes);
                if (node == null || node.isMissingNode() || node.isNull()) throw new IllegalArgumentException();
                return node;
            } catch (RuntimeException invalid) {
                throw responseFailure(observedStatus, submission, id, last);
            }
        } catch (IOException | IllegalStateException failure) {
            if (!submission && (caller.isInterrupted() || (waitDeadline != 0 && waitDeadline - System.nanoTime() <= 0)))
                throw new WaitTerminatedException(id, last);
            throw responseFailure(observedStatus, submission, id, last);
        } finally {
            abort.cancel(false);
            // Cancel before closing: an incomplete body must never be drained for reuse.
            request.cancel();
            if (response != null) response.close(CloseMode.IMMEDIATE);
        }
    }

    private static void checkDispatchInterrupt(Thread caller, boolean submission, UUID id, ExecutionSnapshot last) {
        if (caller.isInterrupted()) {
            if (submission) throw new LocalFailureException("Submission interrupted before dispatch");
            throw new WaitTerminatedException(id, last);
        }
    }

    private static boolean isHttpFailure(int observedStatus) {
        return observedStatus != 0 && (observedStatus < 200 || observedStatus >= 300);
    }

    private static SidecarException responseFailure(int observedStatus, boolean submission, UUID id, ExecutionSnapshot last) {
        if (isHttpFailure(observedStatus)) return new HttpFailureException(observedStatus, null, id, last);
        if (submission) return new AcceptanceUnknownException(null, null);
        return new ProtocolException(id, last);
    }

    private void validate(CredentialSnapshot credential) {
        if (closed.get()) throw new LocalFailureException("Client is closed");
        if (credential == null) throw new LocalFailureException("Missing execution credential");
    }
    private JsonNode redact(JsonNode node, CredentialSnapshot credential) {
        if (node.isString()) return json.getNodeFactory().stringNode(credential.redact(node.asString()));
        if (node.isObject()) {
            ObjectNode copy = json.createObjectNode();
            for (var entry : node.properties())
                copy.set(credential.redact(entry.getKey()), redact(entry.getValue(), credential));
            return copy;
        }
        if (node.isArray()) {
            var copy = json.createArrayNode();
            for (var value : node) copy.add(redact(value, credential));
            return copy;
        }
        return node.deepCopy();
    }
    @Override public void close() {
        if (closed.compareAndSet(false, true)) {
            try { http.close(CloseMode.IMMEDIATE); }
            finally { timer.shutdownNow(); }
        }
    }
    static long nanos(Duration value) {
        try {
            if (value == null || value.isNegative() || value.isZero()) throw new ArithmeticException();
            long nanos = value.toNanos();
            // Keep monotonic subtraction and deadline addition safe across nanoTime wraparound.
            if (nanos > Long.MAX_VALUE / 2) throw new ArithmeticException();
            return nanos;
        } catch (ArithmeticException invalid) { throw new LocalFailureException("Invalid finite positive duration"); }
    }
    private static String base(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null
                || !(value.getScheme().equalsIgnoreCase("http") || value.getScheme().equalsIgnoreCase("https"))
                || value.getRawUserInfo() != null || value.getRawQuery() != null || value.getRawFragment() != null)
            throw new LocalFailureException("Invalid Sidecar base URL");
        String result = value.toASCIIString();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }
    private static String segment(String name) {
        if (name == null || name.isBlank() || name.equals(".") || name.equals("..")
                || name.chars().anyMatch(c -> Character.isISOControl(c) || c == '/' || c == '\\' || c == '%'))
            throw new LocalFailureException("Invalid skill name");
        StringBuilder result = new StringBuilder();
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') result.append((char)c);
            else result.append('%').append("0123456789ABCDEF".charAt(c >>> 4)).append("0123456789ABCDEF".charAt(c & 15));
        }
        return result.toString();
    }
    private static SkillDescriptor skill(JsonNode n) {
        object(n);
        return new SkillDescriptor(text(n,"name",true), text(n,"description",false), text(n,"kind",true), text(n,"inputSchema",true), text(n,"outputSchema",false));
    }
    private static ExecutionSnapshot snapshot(JsonNode n, UUID expected) {
        object(n);
        UUID id = uuid(text(n,"id",true));
        if (!id.equals(expected)) throw new IllegalArgumentException();
        String name = text(n,"skillName",true);
        ExecutionStatus status = ExecutionStatus.valueOf(text(n,"status",true));
        Instant created = Instant.parse(text(n,"createdAt",true));
        String completedText = text(n,"completedAt",false);
        Instant completed = completedText == null ? null : Instant.parse(completedText);
        if (status.terminal() && completed == null) throw new IllegalArgumentException();
        ExecutionFailure failure = null;
        JsonNode f = n.get("failure");
        if (f != null && !f.isNull()) {
            object(f);
            List<ExecutionFailure.Issue> issues = new ArrayList<>();
            JsonNode issueNodes = f.get("issues");
            if (issueNodes != null && !issueNodes.isNull()) {
                if (!issueNodes.isArray()) throw new IllegalArgumentException();
                for (var issue : issueNodes) {
                    object(issue);
                    issues.add(new ExecutionFailure.Issue(text(issue,"path",false),text(issue,"code",false),text(issue,"message",false)));
                }
            }
            failure = new ExecutionFailure(text(f,"kind",true),text(f,"message",false),issues);
        }
        if (status == ExecutionStatus.FAILED && failure == null) throw new IllegalArgumentException();
        List<ExecutionEvent> events = null;
        JsonNode eventNodes = n.get("events");
        if (eventNodes != null && !eventNodes.isNull()) {
            if (!eventNodes.isArray()) throw new IllegalArgumentException();
            events = new ArrayList<>();
            for (var event : eventNodes) {
                object(event);
                JsonNode details = event.get("details"); object(details);
                events.add(new ExecutionEvent(Instant.parse(text(event,"timestamp",true)),text(event,"level",true),text(event,"type",true),details,text(event,"frameId",false),text(event,"route",false)));
            }
        }
        String config = text(n,"configurationSnapshotId",false);
        return new ExecutionSnapshot(id,name,status,created,completed,text(n,"result",false),failure,events,config == null ? null : uuid(config));
    }
    private static void object(JsonNode n) { if (n == null || !n.isObject()) throw new IllegalArgumentException(); }
    private static String text(JsonNode n, String field, boolean required) {
        object(n); JsonNode v = n.get(field);
        if (v == null || v.isNull()) { if (required) throw new IllegalArgumentException(); return null; }
        if (!v.isString() || (required && v.asString().isBlank())) throw new IllegalArgumentException();
        return v.asString();
    }
    private static UUID uuid(String text) {
        UUID value = UUID.fromString(text);
        if (!value.toString().equalsIgnoreCase(text)) throw new IllegalArgumentException();
        return value;
    }
}
