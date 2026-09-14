package ai.loomspan.sidecar.rest;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.EofSensorInputStream;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
final class RestTargetClients implements AutoCloseable {
    record TargetClient(RestClient restClient, CloseableHttpClient httpClient) {}

    private final Map<String, TargetClient> clients;
    private final AtomicBoolean closed = new AtomicBoolean();

    RestTargetClients(RestRouteLoader loader, ObjectProvider<SslBundles> sslBundlesProvider) {
        SslBundles sslBundles = sslBundlesProvider.getIfAvailable();
        Map<String, TargetClient> built = new LinkedHashMap<>();
        try {
            for (var target : loader.configuration().targets().values()) {
                built.put(target.name(), build(target, sslBundles));
            }
        } catch (RuntimeException failure) {
            built.values().forEach(RestTargetClients::closeQuietly);
            throw failure;
        }
        clients = Map.copyOf(built);
    }

    TargetClient get(String target) {
        TargetClient client = clients.get(target);
        if (client == null) throw new IllegalStateException("Unknown REST target " + target);
        return client;
    }

    boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) clients.values().forEach(RestTargetClients::closeQuietly);
    }

    private static TargetClient build(RestRouteConfiguration.Target target, SslBundles sslBundles) {
        Timeout connect = Timeout.ofMilliseconds(target.connectTimeout().toMillis());
        Timeout read = Timeout.ofMilliseconds(target.readTimeout().toMillis());
        var connectionBuilder = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultSocketConfig(SocketConfig.custom().setSoTimeout(read).build());
        if (target.sslBundle() != null) {
            connectionBuilder.setSSLSocketFactory(SSLConnectionSocketFactoryBuilder.create()
                    .setSslContext(sslBundles.getBundle(target.sslBundle()).createSslContext())
                    .build());
        }
        var requestConfig = RequestConfig.custom()
                .setConnectTimeout(connect)
                .setConnectionRequestTimeout(connect)
                .setResponseTimeout(read)
                .build();
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(connectionBuilder.build())
                .setDefaultRequestConfig(requestConfig)
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .addResponseInterceptorLast((response, entity, context) -> {
                    if (response instanceof ClassicHttpResponse classic && classic.getEntity() != null) {
                        classic.setEntity(new HttpEntityWrapper(classic.getEntity()) {
                            @Override public InputStream getContent() throws IOException {
                                return new AbortableBoundedInputStream(super.getContent(), target.maxResponseSize() + 1);
                            }
                        });
                    }
                })
                .build();
        var requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return new TargetClient(RestClient.builder().requestFactory(requestFactory).build(), httpClient);
    }

    private static void closeQuietly(TargetClient client) {
        try {
            client.httpClient().close();
        } catch (IOException ignored) {
            // Closing an already-failed client must not add a second shutdown failure.
        }
    }

    private static final class AbortableBoundedInputStream extends FilterInputStream {
        private final long maximum;
        private long count;

        private AbortableBoundedInputStream(InputStream input, long maximum) {
            super(input);
            this.maximum = maximum;
        }

        @Override public int read() throws IOException {
            requireRemaining();
            int value = super.read();
            if (value >= 0) count++;
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            requireRemaining();
            int read = super.read(buffer, offset, (int) Math.min(length, maximum - count));
            if (read > 0) count += read;
            return read;
        }

        @Override public void close() throws IOException {
            if (in instanceof EofSensorInputStream sensor) sensor.abort();
            else in.close();
        }

        private void requireRemaining() throws IOException {
            if (count >= maximum) throw new IOException("REST response exceeded bounded transport stream");
        }
    }
}
