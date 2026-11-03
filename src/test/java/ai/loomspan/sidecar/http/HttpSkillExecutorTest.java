package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillExecutorInvocation;
import ai.loomspan.sidecar.configuration.ProviderCredentialCipher;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static ai.loomspan.sidecar.http.HttpTestSupport.*;

class HttpSkillExecutorTest {
    @org.junit.jupiter.api.AfterEach void clearSecurity(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    @Test void allMethodsAndBodySelectionsReachWire() throws Exception {
        var received=new java.util.concurrent.LinkedBlockingQueue<String>();
        try(var fixture=new HttpTestSupport(e->{assertThat(e.getRequestHeaders().getFirst("Authorization")).isNull();received.add(e.getRequestMethod()+":"+new String(e.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));respond(e,200,null,"");})) {
            for(String method:java.util.List.of("GET","POST","PUT","PATCH","DELETE")) {
                assertThat(fixture.call(operation("method",method),Map.of(),fixture.settings())).isEmpty();assertThat(received.take()).isEqualTo(method+":");
                if(!method.equals("GET")) {
                    fixture.call(operation("method",method,"body",map("source","field","field","/nested/v")),map("nested",map("v",null)),fixture.settings());
                    assertThat(received.take()).isEqualTo(method+":null");
                }
            }
        }
    }
    @Test void fixedCredentialsAreCapturedAndFailuresDoNotDispatch() throws Exception {
        var received=new java.util.concurrent.LinkedBlockingQueue<String>();
        try(var fixture=new HttpTestSupport(e->{received.add(e.getRequestHeaders().getFirst("Authorization"));respond(e,200,null,"ok");})) {
            var base=fixture.settings("auth",map("mode","fixed","header-refs",Map.of("Authorization","service")));
            for(String version:java.util.List.of("v1","v2")) {
                var record=new ai.loomspan.sidecar.storage.EncryptedCredential("service",version,fixture.cipher.encrypt("service",version,"Bearer "+version));
                var settings=HttpExecutorConfiguration.withCredentials(base,java.util.List.of(record));
                assertThat(fixture.call(operation(),Map.of(),settings)).isEqualTo("ok");assertThat(received.take()).isEqualTo("Bearer "+version);
                assertThat(settings.toString()).doesNotContain("Bearer "+version);
                assertThatThrownBy(()->HttpExecutorConfiguration.withCredentials(base,java.util.List.of(record,record))).isInstanceOf(ai.loomspan.api.SkillException.class);
                var bad=new ai.loomspan.sidecar.storage.EncryptedCredential("service",version,fixture.cipher.encrypt("service",version,"secret\r\nmarker"));
                assertThatThrownBy(()->fixture.call(operation(),Map.of(),HttpExecutorConfiguration.withCredentials(base,java.util.List.of(bad))))
                        .isExactlyInstanceOf(ai.loomspan.api.SkillException.class).hasMessage("HTTP service credential unavailable").hasNoCause();
                var wrongCipher=new HttpSkillExecutor(fixture.client,new ProviderCredentialCipher(null),Clock.systemUTC());
                assertThatThrownBy(()->wrongCipher.execute(new SkillExecutorInvocation(Map.of(),operation(),settings,"call","g"))).isInstanceOf(ai.loomspan.api.SkillException.class).hasNoCause();
            }
            assertThat(received).isEmpty();
            assertThatThrownBy(()->fixture.call(operation(),Map.of(),base)).isInstanceOf(ai.loomspan.api.SkillException.class);
        }
    }
    @Test void passthroughExpiryAndConcurrentCallersKeepOriginalTokensAndNoCookies() throws Exception {
        var now=java.time.Instant.parse("2026-11-02T12:00:00Z");var received=new java.util.concurrent.ConcurrentLinkedQueue<String>();
        try(var fixture=new HttpTestSupport(e->{received.add(e.getRequestHeaders().getFirst("Authorization")+"|"+e.getRequestHeaders().getFirst("X-App")+"|"+e.getRequestHeaders().getFirst("Cookie"));e.getResponseHeaders().set("Set-Cookie","session=secret");respond(e,200,null,"ok");},Clock.fixed(now,java.time.ZoneOffset.UTC))) {
            var settings=fixture.settings("auth",Map.of("mode","caller-passthrough"));
            assertThatThrownBy(()->fixture.call(operation(),Map.of(),settings)).isInstanceOf(ai.loomspan.api.SkillException.class);
            for(var expiration:java.util.List.of(now.minusSeconds(1),now)) {
                authenticate("expired",expiration);assertThatThrownBy(()->fixture.call(operation(),Map.of(),settings)).isInstanceOf(ai.loomspan.api.SkillException.class);
            }
            assertThat(received).isEmpty();
            try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var tasks=new java.util.ArrayList<java.util.concurrent.Future<?>>();
                for(String caller:java.util.List.of("original.A-_","original.B-_"))tasks.add(pool.submit(()->{try {authenticate(caller,now.plusSeconds(60));for(int i=0;i<2;i++)fixture.call(operation(),Map.of("Authorization","ignored"),fixture.settings("auth",Map.of("mode","caller-passthrough"),"headers",Map.of("X-App",caller)));}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}}));
                for(var task:tasks)task.get(3,java.util.concurrent.TimeUnit.SECONDS);
            }
            assertThat(received).containsExactlyInAnyOrder("Bearer original.A-_|original.A-_|null","Bearer original.A-_|original.A-_|null","Bearer original.B-_|original.B-_|null","Bearer original.B-_|original.B-_|null");
        }
    }
    static void authenticate(String token,java.time.Instant expiry){var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue(token).header("alg","none").subject("caller").expiresAt(expiry).build();org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,java.util.List.of()));}
    @Test void responseFidelityAndStatusClassification() throws Exception {
        for(int status:java.util.List.of(200,201,202,299,400,401,403,409,422,302,500)) {
            String body="  exact 雪\n";
            try(var fixture=new HttpTestSupport(e->respond(e,status,"application/problem+json",body))) {
                if(status<300)assertThat(fixture.call(operation(),Map.of(),fixture.settings())).isEqualTo(body);
                else {
                    Class<?> expected=status==409?ai.loomspan.api.RecoverableSkillException.class:ai.loomspan.api.SkillException.class;
                    assertThatThrownBy(()->fixture.call(operation("recoverableStatuses",java.util.List.of(409)),Map.of(),fixture.settings())).isExactlyInstanceOf(expected).hasMessage("HTTP "+status+"\n"+body).hasNoCause();
                }
            }
        }
    }
    @Test void mediaMatrixForSuccessAndError() throws Exception {
        for(int status:java.util.List.of(200,409))for(String type:java.util.Arrays.asList(null,"text/plain","application/json","application/vnd.app+json","text/plain;charset=ISO-8859-1")) {
            byte[] bytes=" x\n".getBytes();
            try(var fixture=new HttpTestSupport(e->respond(e,status,type,bytes))) {
                if(status==200)assertThat(fixture.call(operation(),Map.of(),fixture.settings())).isEqualTo(" x\n");
                else assertThatThrownBy(()->fixture.call(operation("recoverableStatuses",java.util.List.of(409)),Map.of(),fixture.settings())).isExactlyInstanceOf(ai.loomspan.api.RecoverableSkillException.class).hasMessage("HTTP 409\n x\n");
            }
        }
        for(int status:java.util.List.of(200,409))for(String type:java.util.List.of("application/json;charset=ISO-8859-1","application/octet-stream","image/a+json","text/plain;charset=unknown","invalid"))try(var fixture=new HttpTestSupport(e->respond(e,status,type,"bad"))) {
            assertThatThrownBy(()->fixture.call(operation("recoverableStatuses",java.util.List.of(409)),Map.of(),fixture.settings())).isExactlyInstanceOf(ai.loomspan.api.SkillException.class).hasMessage("HTTP response cannot be decoded");
        }
        try(var fixture=new HttpTestSupport(e->respond(e,409,"text/plain",new byte[]{(byte)0xc3,0x28}))) {assertThatThrownBy(()->fixture.call(operation("recoverableStatuses",java.util.List.of(409)),Map.of(),fixture.settings())).isExactlyInstanceOf(ai.loomspan.api.SkillException.class);}
        try(var fixture=new HttpTestSupport(e->respond(e,200,"text/plain;charset=ISO-8859-1",new byte[]{(byte)0xe9}))) {assertThat(fixture.call(operation(),Map.of(),fixture.settings())).isEqualTo("é");}
        try(var fixture=new HttpTestSupport(e->respond(e,204,"application/octet-stream",""))) {assertThat(fixture.call(operation(),Map.of(),fixture.settings())).isEmpty();}
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints={200,409,500})
    void abandonsOversizeBodiesBeforeUnboundedRemainder(int status) throws Exception {
        var release=new java.util.concurrent.CountDownLatch(1);
        try(var fixture=new HttpTestSupport(e->{e.sendResponseHeaders(status,0);try(var out=e.getResponseBody()){out.write("123456".getBytes());out.flush();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}out.write("remainder".getBytes());}});var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var result=pool.submit(()->catchThrowable(()->fixture.call(operation("recoverableStatuses",java.util.List.of(409)),Map.of(),fixture.settings("max-response-size","5B"))));
            try {assertThat(result.get(2,java.util.concurrent.TimeUnit.SECONDS)).isExactlyInstanceOf(ai.loomspan.api.SkillException.class).hasMessageContaining("size limit");}finally{release.countDown();}
        }finally{release.countDown();}
    }
    @Test void concurrentReadTimeoutsAndReuseDoNotMutateSharedSettings() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(2);var release=new java.util.concurrent.CountDownLatch(1);
        try(var fixture=new HttpTestSupport(e->{entered.countDown();try{release.await(5,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}respond(e,200,null,"ok");});var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var shortCall=pool.submit(()->catchThrowable(()->fixture.call(operation(),Map.of(),fixture.settings("read-timeout","300ms"))));
            var longCall=pool.submit(()->fixture.call(operation(),Map.of(),fixture.settings("read-timeout","3s")));
            assertThat(entered.await(2,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {assertThat(shortCall.get(2,java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(ai.loomspan.api.SkillException.class);assertThat(longCall.isDone()).isFalse();}finally{release.countDown();}
            assertThat(longCall.get(2,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("ok");
            assertThat(fixture.call(operation(),Map.of(),fixture.settings("read-timeout","1s"))).isEqualTo("ok");
        }finally{release.countDown();}
    }
    @Test void redirectsAndDroppedConnectionsAreOneAttempt() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        try(var fixture=new HttpTestSupport(e->{count.incrementAndGet();e.getResponseHeaders().set("Location","/redirected");respond(e,302,null,"");})) {
            assertThatThrownBy(()->fixture.call(operation(),Map.of(),fixture.settings())).hasMessage("HTTP 302");assertThat(count).hasValue(1);
        }
        count.set(0);try(var fixture=new HttpTestSupport(e->{count.incrementAndGet();e.close();})) {
            assertThatThrownBy(()->fixture.call(operation(),Map.of(),fixture.settings())).hasMessage("HTTP transport failed").hasNoCause();assertThat(count).hasValue(1);
        }
    }
    @Test void requestLocalConnectTimeoutsReachSocketWithoutRetries() throws Exception {
        try(var fixture=new HttpTestSupport(e->respond(e,200,null,"unexpected"))) {
            for(int timeout:java.util.List.of(175,500))try(var sockets=org.mockito.Mockito.mockConstruction(java.net.Socket.class,(socket,context)->org.mockito.Mockito.doThrow(new java.net.SocketTimeoutException("secret fixture")).when(socket).connect(org.mockito.ArgumentMatchers.any(java.net.SocketAddress.class),org.mockito.ArgumentMatchers.anyInt()))) {
                assertThatThrownBy(()->fixture.call(operation(),Map.of(),fixture.settings("connect-timeout",timeout+"ms"))).hasMessage("HTTP transport failed").hasNoCause();
                assertThat(sockets.constructed()).hasSize(1);org.mockito.Mockito.verify(sockets.constructed().getFirst()).connect(org.mockito.ArgumentMatchers.any(java.net.SocketAddress.class),org.mockito.ArgumentMatchers.eq(timeout));assertThat(Thread.currentThread().isInterrupted()).isFalse();
            }
        }
    }
    @Test void exactAndCompressedLimitsApplyToSuccessAndError() throws Exception {
        for(int status:java.util.List.of(200,409)) {
            try(var fixture=new HttpTestSupport(e->respond(e,status,"text/plain","12345"))) {
                if(status==200)assertThat(fixture.call(operation(),Map.of(),fixture.settings("max-response-size","5B"))).isEqualTo("12345");
                else assertThatThrownBy(()->fixture.call(operation("recoverableStatuses",java.util.List.of(409)),Map.of(),fixture.settings("max-response-size","5B"))).isExactlyInstanceOf(ai.loomspan.api.RecoverableSkillException.class).hasMessage("HTTP 409\n12345");
            }
            var compressed=new java.io.ByteArrayOutputStream();try(var gzip=new java.util.zip.GZIPOutputStream(compressed)){gzip.write("x".repeat(10000).getBytes());}
            try(var fixture=new HttpTestSupport(e->{e.getResponseHeaders().set("Content-Encoding","gzip");respond(e,status,"text/plain",compressed.toByteArray());})) {
                assertThatThrownBy(()->fixture.call(operation("recoverableStatuses",java.util.List.of(409)),Map.of(),fixture.settings("max-response-size","256B"))).isExactlyInstanceOf(ai.loomspan.api.SkillException.class).hasMessageContaining("size limit");
            }
        }
    }
    @Test void configurationAndInputFailuresNeverDispatchAndInterruptIsPreserved() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        try(var fixture=new HttpTestSupport(e->{count.incrementAndGet();respond(e,200,null,"unexpected");})) {
            assertThatThrownBy(()->fixture.call(operation("path","/{id}","pathParams",Map.of("id","/id")),Map.of("id","a/b"),fixture.settings())).isInstanceOf(ai.loomspan.api.SkillException.class);
            assertThatThrownBy(()->fixture.call(operation("method","GET","body",Map.of("source","input")),Map.of(),fixture.settings())).isInstanceOf(ai.loomspan.api.SkillException.class);
            assertThat(count).hasValue(0);
            try(var sockets=org.mockito.Mockito.mockConstruction(java.net.Socket.class,(socket,context)->org.mockito.Mockito.doThrow(new java.io.InterruptedIOException("private marker")).when(socket).connect(org.mockito.ArgumentMatchers.any(java.net.SocketAddress.class),org.mockito.ArgumentMatchers.anyInt()))) {
                try {assertThatThrownBy(()->fixture.call(operation(),Map.of(),fixture.settings())).hasMessage("HTTP transport failed").hasNoCause();assertThat(Thread.currentThread().isInterrupted()).isTrue();}finally{Thread.interrupted();}
            }
        }
    }
    @Test void mapsExplicitPointersAndPreservesWholeInputBody() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            assertThat(exchange.getRequestURI().toString()).isEqualTo("/api/v1/cars/42?color=red&color=blue");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8))
                    .contains("\"id\":42", "\"colors\":[\"red\",\"blue\"]", "\"zero\":0", "\"flag\":false");
            byte[] body = "  result\n".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try (var client = new HttpExecutorClient()) {
            var executor = new HttpSkillExecutor(client, new ProviderCredentialCipher(null), Clock.systemUTC());
            var input = Map.<String,Object>of("car", Map.of("id",42), "options", Map.of("colors", java.util.List.of("red","blue")), "zero",0,"flag",false);
            var operation = Map.<String,Object>of("target","app","method","POST","path","/cars/{id}","pathParams",Map.of("id","/car/id"),"queryParams",Map.of("color","/options/colors"),"body",Map.of("source","input"));
            var settings = Map.<String,Object>of("targets", Map.of("app",Map.of("base-url","http://127.0.0.1:"+server.getAddress().getPort()+"/api/v1")));
            assertThat(executor.execute(new SkillExecutorInvocation(input,operation,settings,"default-call","generation"))).isEqualTo("  result\n");
        } finally { server.stop(0); }
    }
}

