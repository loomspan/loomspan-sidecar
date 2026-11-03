package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillException;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Application-owned pool; Framework's higher lifecycle phase finishes admitted work first. */
@Component
public final class HttpExecutorClient implements SmartLifecycle,AutoCloseable {
    private final AtomicBoolean closed=new AtomicBoolean();
    private final CloseableHttpClient client=HttpClients.custom()
            .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create().useSystemProperties().build())
            .useSystemProperties().disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
    @SuppressWarnings("deprecation") // Apache 5.6 consumes these captured request-local connect settings.
    String execute(HttpRequestMapper.Request mapped,HttpExecutorConfiguration.Target target,Map<String,String> headers,Set<Integer> recoverable) {
        var request=new HttpUriRequestBase(mapped.method(),mapped.uri());
        request.setConfig(RequestConfig.custom().setConnectTimeout(Timeout.ofMilliseconds(target.connectMillis()))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(target.connectMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(target.readMillis())).build());
        headers.forEach(request::setHeader);
        if(mapped.body()!=null) {
            request.setEntity(new ByteArrayEntity(mapped.body(),ContentType.APPLICATION_JSON));
            if(HttpExecutorConfiguration.header(headers,"Content-Type")==null)request.setHeader("Content-Type","application/json");
        }
        try(var response=client.execute(request)) {
            try {
                var entity=response.getEntity();
                return HttpExecutorResponse.read(response.getCode(),response.getFirstHeader("Content-Type")==null?null:response.getFirstHeader("Content-Type").getValue(),
                        entity==null?java.io.InputStream.nullInputStream():entity.getContent(),target.maxResponseSize(),recoverable);
            } finally {
                // Immediate close abandons unread entities, avoiding Apache's graceful drain on failure.
                response.close(CloseMode.IMMEDIATE);
            }
        } catch(SkillException failure) {throw failure;}
        catch(Exception failure) {
            for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof InterruptedException||cause instanceof java.io.InterruptedIOException&&!(cause instanceof java.net.SocketTimeoutException)) {Thread.currentThread().interrupt();break;}
            throw new SkillException("HTTP transport failed");
        }
    }
    public boolean isClosed(){return closed.get();}
    @Override public void start() {}
    @Override public boolean isRunning(){return !closed.get();}
    @Override public int getPhase(){return 0;}
    @Override public void stop(){close();}
    @Override @PreDestroy public void close(){if(closed.compareAndSet(false,true))client.close(CloseMode.IMMEDIATE);}
}
