package ai.loomspan.sidecar.rest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Resources and durable identity owned by each published framework generation. */
@Component
public final class GenerationRestResources implements SmartLifecycle {
    public static final class Resources implements AutoCloseable {
        private final RestRouteConfiguration routes;
        private final RestTargetClients clients;
        private final AtomicBoolean closed = new AtomicBoolean();
        private volatile UUID snapshotId;

        private Resources(RestRouteConfiguration routes, RestTargetClients clients) {
            this.routes = routes;
            this.clients = clients;
        }

        public RestRouteConfiguration routes() { return routes; }
        RestTargetClients clients() { return clients; }
        public UUID snapshotId() { return snapshotId; }
        private synchronized void bind(UUID id) {
            if (id == null) return;
            if (snapshotId != null && !snapshotId.equals(id))
                throw new IllegalStateException("REST generation has a different durable snapshot identity");
            snapshotId = id;
        }
        @Override public void close() { if (closed.compareAndSet(false, true)) clients.close(); }
        public boolean isClosed() { return closed.get(); }
    }

    private final RestRouteLoader loader;
    private final SslBundles sslBundles;
    private final Map<String, Resources> generations = new ConcurrentHashMap<>();
    private final Set<Resources> owned = ConcurrentHashMap.newKeySet();
    private final ExecutorService cleanup = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("sidecar-rest-cleanup").factory());
    private final AtomicBoolean running = new AtomicBoolean(true);

    public GenerationRestResources(RestRouteLoader loader, ObjectProvider<SslBundles> sslBundlesProvider) {
        this.loader = loader;
        this.sslBundles = sslBundlesProvider.getIfAvailable();
    }

    public Resources prepare(String yaml) {
        RestRouteConfiguration routes = loader.parse(yaml, "rest-routes.yaml");
        Resources resources = new Resources(routes, new RestTargetClients(routes, sslBundles));
        owned.add(resources);
        return resources;
    }

    public void stage(String generationId, Resources resources, UUID snapshotId) {
        resources.bind(snapshotId);
        if (generations.putIfAbsent(generationId, resources) != null)
            throw new IllegalStateException("REST generation is already staged");
    }

    public void bind(String generationId, UUID snapshotId) {
        require(generationId).bind(snapshotId);
    }

    public Resources require(String generationId) {
        Resources resources = generations.get(generationId);
        if (resources == null) throw new IllegalStateException("REST generation mapping is missing");
        return resources;
    }

    public UUID snapshotId(String generationId) { return require(generationId).snapshotId(); }

    public Set<UUID> protectedIds() {
        java.util.HashSet<UUID> result = new java.util.HashSet<>();
        generations.values().forEach(resources -> {
            if (resources.snapshotId() != null) result.add(resources.snapshotId());
        });
        return Set.copyOf(result);
    }

    int ownedCount() { return owned.size(); }

    public void discard(String generationId) {
        Resources resources = generations.remove(generationId);
        if (resources != null) closeOwned(resources);
    }

    public void retire(String generationId) {
        Resources resources = generations.remove(generationId);
        if (resources == null) return;
        try { cleanup.execute(() -> closeOwned(resources)); }
        catch (java.util.concurrent.RejectedExecutionException ignored) {
            // Shutdown already closes every owned generation; never close a client on a framework callback thread.
        }
    }

    public void release(Resources resources) { closeOwned(resources); }

    private void closeOwned(Resources resources) {
        resources.close();
        owned.remove(resources);
    }

    @Override public int getPhase() { return 0; }
    @Override public boolean isAutoStartup() { return true; }
    @Override public boolean isRunning() { return running.get(); }
    @Override public void start() { running.set(true); }
    @Override public void stop() {
        if (!running.compareAndSet(true, false)) return;
        generations.clear();
        owned.forEach(this::closeOwned);
        cleanup.shutdownNow();
    }
}
