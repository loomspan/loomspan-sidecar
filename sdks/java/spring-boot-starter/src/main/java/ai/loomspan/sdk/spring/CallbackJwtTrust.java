package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.SigningKeySource;
import com.nimbusds.jose.util.JSONObjectUtils;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Complete current-file callback trust; an unusable configured overlap withdraws all keys. */
public final class CallbackJwtTrust implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(CallbackJwtTrust.class);
    private static final String UNAVAILABLE = "loomspan-sdk.callbacks.overlap-jwks is unavailable. Provide a readable overlap JWKS file.";
    private static final String INVALID = "loomspan-sdk.callbacks.overlap-jwks is invalid. Provide a valid nonempty public RSA JWKS file.";

    private enum State { READY, UNAVAILABLE, INVALID }

    public record Snapshot(Map<String, RSAPublicKey> keys) {
        public Snapshot { keys = Map.copyOf(keys); }
    }
    @FunctionalInterface interface Reader { byte[] read(Path path) throws IOException; }
    private final Map<String, RSAPublicKey> own;
    private final Path path;
    private final Reader reader;
    private final LongSupplier ticker;
    private final ScheduledExecutorService scheduler;
    private volatile Snapshot snapshot;
    private byte[] previousBytes;
    private State state;
    private boolean unknownReloadAttempted;
    private long lastUnknownReload;
    private boolean closed;

    public CallbackJwtTrust(SigningKeySource signer, CallbackProperties properties) {
        this(signer, properties, Files::readAllBytes, System::nanoTime, true);
    }
    CallbackJwtTrust(SigningKeySource signer, CallbackProperties properties, Reader reader,
            LongSupplier ticker, boolean schedule) {
        own = Map.of(signer.kid(), signer.publicKey());
        path = properties.overlapPath(); this.reader = reader; this.ticker = ticker;
        reload();
        scheduler = path != null && schedule ? Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("loomspan-sdk-callback-trust").factory()) : null;
        if (scheduler != null) scheduler.scheduleWithFixedDelay(this::reload, 1, 1, TimeUnit.SECONDS);
    }
    public Snapshot snapshot() { return snapshot; }
    public Snapshot forKid(String kid) {
        Snapshot current = snapshot;
        if (path == null || kid == null || kid.isBlank() || current.keys().containsKey(kid)) return current;
        synchronized (this) {
            if (closed || snapshot.keys().containsKey(kid)) return snapshot;
            long now = ticker.getAsLong();
            if (!unknownReloadAttempted || now - lastUnknownReload >= TimeUnit.SECONDS.toNanos(1)) {
                unknownReloadAttempted = true; lastUnknownReload = now; reload();
            }
            return snapshot;
        }
    }
    public synchronized void reload() {
        if (closed) return;
        if (path == null) { if (snapshot == null) snapshot = new Snapshot(own); return; }
        byte[] bytes;
        try {
            bytes = reader.read(path);
        } catch (IOException | SecurityException ignored) {
            publish(State.UNAVAILABLE, null, Map.of());
            return;
        }
        if (state != null && state != State.UNAVAILABLE && Arrays.equals(bytes, previousBytes)) return;

        var merged = new LinkedHashMap<>(own);
        try {
            for (var entry : validate(bytes).entrySet()) {
                RSAPublicKey duplicate = merged.putIfAbsent(entry.getKey(), entry.getValue());
                if (duplicate != null && !duplicate.equals(entry.getValue())) throw new IllegalArgumentException();
            }
        } catch (Exception ignored) {
            publish(State.INVALID, bytes, Map.of());
            return;
        }
        publish(State.READY, bytes, merged);
    }

    private void publish(State next, byte[] bytes, Map<String, RSAPublicKey> keys) {
        if (state == State.UNAVAILABLE && next == State.UNAVAILABLE) return;
        if (snapshot == null && next != State.READY) {
            throw new IllegalStateException(next == State.UNAVAILABLE ? UNAVAILABLE : INVALID);
        }
        State previous = state;
        previousBytes = bytes == null ? null : bytes.clone();
        state = next;
        snapshot = new Snapshot(keys);
        if (previous != null && previous != next) {
            switch (next) {
                case UNAVAILABLE -> LOG.warn("Callback trust withdrawn. {}", UNAVAILABLE);
                case INVALID -> LOG.warn("Callback trust withdrawn. {}", INVALID);
                case READY -> LOG.info("Callback trust restored.");
            }
        }
    }
    private static Map<String, RSAPublicKey> validate(byte[] bytes) throws Exception {
        var json = JSONObjectUtils.parse(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString());
        if (!(json.get("keys") instanceof List<?> candidates) || candidates.isEmpty()) throw new IllegalArgumentException();
        var keys = new LinkedHashMap<String, RSAPublicKey>();
        for (Object candidate : candidates) {
            if (!(candidate instanceof Map<?, ?> key) || !"RSA".equals(key.get("kty"))) throw new IllegalArgumentException();
            if (!(key.get("kid") instanceof String kid) || kid.isBlank() || keys.containsKey(kid)) throw new IllegalArgumentException();
            for (String member : List.of("d", "p", "q", "dp", "dq", "qi", "oth"))
                if (key.containsKey(member)) throw new IllegalArgumentException();
            if (key.containsKey("use") && !"sig".equals(key.get("use"))
                    || key.containsKey("alg") && !"RS256".equals(key.get("alg"))) throw new IllegalArgumentException();
            BigInteger n = unsigned(key.get("n")), e = unsigned(key.get("e"));
            if (n.bitLength() < 2048 || !n.testBit(0) || e.compareTo(BigInteger.valueOf(3)) < 0
                    || !e.testBit(0) || e.compareTo(n) >= 0) throw new IllegalArgumentException();
            keys.put(kid, (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
        }
        return keys;
    }
    private static BigInteger unsigned(Object value) {
        if (!(value instanceof String text) || !text.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
        return new BigInteger(1, Base64.getUrlDecoder().decode(text));
    }
    @Override public synchronized void close() {
        closed = true;
        if (scheduler != null) scheduler.shutdownNow();
    }
}
