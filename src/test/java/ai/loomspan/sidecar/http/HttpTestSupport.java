package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillExecutorInvocation;
import ai.loomspan.sidecar.configuration.ProviderCredentialCipher;
import com.sun.net.httpserver.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;

final class HttpTestSupport implements AutoCloseable {
    final HttpServer server;
    final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    final HttpExecutorClient client=new HttpExecutorClient();
    final ProviderCredentialCipher cipher=new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    final HttpSkillExecutor executor;
    HttpTestSupport(HttpHandler handler) throws Exception {this(handler,Clock.systemUTC());}
    HttpTestSupport(HttpHandler handler,Clock clock) throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",handler);server.setExecutor(workers);server.start();
        executor=new HttpSkillExecutor(client,cipher,clock);
    }
    String base(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    Map<String,Object> settings(Object... overrides){var target=map("base-url",base());target.putAll(map(overrides));return map("targets",Map.of("app",target));}
    static Map<String,Object> operation(Object... overrides){var op=map("target","app","method","GET","path","/");op.putAll(map(overrides));return op;}
    String call(Map<String,Object> op,Map<String,Object> input,Map<String,Object> settings){return executor.execute(new SkillExecutorInvocation(input,op,settings,"default-call","generation"));}
    static Map<String,Object> map(Object... pairs){var map=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)map.put((String)pairs[i],pairs[i+1]);return map;}
    static void respond(HttpExchange e,int status,String type,byte[] bytes) throws java.io.IOException {
        if(type!=null)e.getResponseHeaders().set("Content-Type",type);
        e.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);try(var out=e.getResponseBody()){if(bytes.length>0)out.write(bytes);}e.close();
    }
    static void respond(HttpExchange e,int status,String type,String body) throws java.io.IOException {respond(e,status,type,body.getBytes(StandardCharsets.UTF_8));}
    @Override public void close(){client.close();server.stop(0);workers.close();}
}
