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

/** Complete current-file callback trust; an unusable configured overlap withdraws all keys. */
public final class CallbackJwtTrust implements AutoCloseable {
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
    private boolean previousUnavailable;
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
        byte[] bytes = null;
        boolean unavailable = false;
        try { bytes = reader.read(path); }
        catch (IOException | SecurityException ignored) { unavailable = true; }
        if (snapshot != null && Arrays.equals(bytes, previousBytes) && unavailable == previousUnavailable) return;
        Map<String, RSAPublicKey> merged = Map.of();
        if (!unavailable) {
            try {
                var candidate = validate(bytes);
                var result = new LinkedHashMap<>(own);
                for (var entry : candidate.entrySet()) {
                    RSAPublicKey duplicate = result.putIfAbsent(entry.getKey(), entry.getValue());
                    if (duplicate != null && !duplicate.equals(entry.getValue())) throw new IllegalArgumentException();
                }
                merged = result;
            } catch (Exception ignored) { /* Publish empty trust, never parser text or stale keys. */ }
        }
        previousBytes = bytes == null ? null : bytes.clone(); previousUnavailable = unavailable;
        snapshot = new Snapshot(merged);
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
