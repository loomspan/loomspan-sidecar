package ai.loomspan.sidecar.security;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import jakarta.annotation.PreDestroy;
import com.nimbusds.jose.util.JSONObjectUtils;

/** Single process trust owner. Every published snapshot is a complete current-file result. */
public final class CurrentFileJwtTrust implements AutoCloseable {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CurrentFileJwtTrust.class);
    public record Snapshot(String mode, Path path, Map<String, RSAPublicKey> keys,
            Instant lastLoadTime, String state) {
        public boolean ready() { return !keys.isEmpty(); }
    }
    @FunctionalInterface interface Reader { byte[] read(Path path) throws IOException; }
    private final String mode;
    private final Path path;
    private final Reader reader;
    private final LongSupplier ticker;
    private final ScheduledExecutorService scheduler;
    private volatile Snapshot snapshot;
    private byte[] previousBytes;
    private String previousFailure;
    private boolean unknownReloadAttempted;
    private long lastUnknownReload;

    public CurrentFileJwtTrust(String mode, Path path) {
        this(mode, path, Files::readAllBytes, System::nanoTime, true);
    }
    CurrentFileJwtTrust(String mode, Path path, Reader reader, LongSupplier ticker, boolean schedule) {
        this.mode = mode; this.path = path; this.reader = reader; this.ticker = ticker;
        reload();
        scheduler = schedule ? Executors.newSingleThreadScheduledExecutor(
                Thread.ofPlatform().daemon().name("sidecar-jwt-trust").factory()) : null;
        if (scheduler != null) scheduler.scheduleWithFixedDelay(this::reload, 1, 1, TimeUnit.SECONDS);
    }
    public Snapshot snapshot() { return snapshot; }
    public Snapshot forKid(String kid) {
        Snapshot current = snapshot;
        if (kid == null || kid.isBlank()) return current;
        if (current.keys().containsKey(kid)) return current;
        synchronized (this) {
            current = snapshot;
            if (current.keys().containsKey(kid)) return current;
            long now = ticker.getAsLong();
            if (!unknownReloadAttempted || now - lastUnknownReload >= TimeUnit.SECONDS.toNanos(1)) {
                unknownReloadAttempted = true; lastUnknownReload = now;
                reload();
            }
            return snapshot;
        }
    }
    public synchronized void reload() {
        byte[] bytes = null;
        String failure = null;
        try { bytes = reader.read(path); }
        catch (IOException | SecurityException unreadable) { failure = "unavailable"; }
        if (snapshot != null && Arrays.equals(bytes, previousBytes)
                && java.util.Objects.equals(failure, previousFailure)) return;
        Map<String, RSAPublicKey> keys = Map.of();
        if (failure == null) {
            try { keys = validate(bytes); }
            catch (Exception malformed) { failure = "malformed"; }
        }
        // Remember bytes even when malformed, but never retain previous trust.
        previousBytes = bytes; previousFailure = failure != null && bytes == null ? failure : null;
        snapshot = new Snapshot(mode, path, keys, Instant.now(), keys.isEmpty() ? "waiting" : "ready");
        LOG.info("JWT trust mode={} path={} kids={} lastLoadTime={} state={} reason={}", mode, path,
                keys.keySet(), snapshot.lastLoadTime(), snapshot.state(), failure == null ? "loaded" : failure);
    }
    private static Map<String, RSAPublicKey> validate(byte[] bytes) throws Exception {
        var json = JSONObjectUtils.parse(java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                .decode(java.nio.ByteBuffer.wrap(bytes)).toString());
        if (!(json.get("keys") instanceof List<?> candidates)) throw new IllegalArgumentException();
        var keys = new LinkedHashMap<String, RSAPublicKey>();
        for (Object candidate : candidates) {
            if (!(candidate instanceof Map<?, ?> key) || !"RSA".equals(key.get("kty"))) throw new IllegalArgumentException();
            if (!(key.get("kid") instanceof String kid) || kid.isBlank() || keys.containsKey(kid)) throw new IllegalArgumentException();
            for (String member : List.of("d", "p", "q", "dp", "dq", "qi", "oth")) if (key.containsKey(member)) throw new IllegalArgumentException();
            if (key.containsKey("use") && !"sig".equals(key.get("use"))
                    || key.containsKey("alg") && !"RS256".equals(key.get("alg"))) throw new IllegalArgumentException();
            BigInteger n = unsigned(key.get("n"));
            BigInteger e = unsigned(key.get("e"));
            if (n.bitLength() < 2048 || !n.testBit(0) || e.compareTo(BigInteger.valueOf(3)) < 0
                    || !e.testBit(0) || e.compareTo(n) >= 0) throw new IllegalArgumentException();
            var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
            keys.put(kid, publicKey);
        }
        return java.util.Collections.unmodifiableMap(keys);
    }
    private static BigInteger unsigned(Object value) {
        if (!(value instanceof String text) || !text.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException();
        return new BigInteger(1, Base64.getUrlDecoder().decode(text));
    }
    @Override @PreDestroy public void close() { if (scheduler != null) scheduler.shutdownNow(); }
}
