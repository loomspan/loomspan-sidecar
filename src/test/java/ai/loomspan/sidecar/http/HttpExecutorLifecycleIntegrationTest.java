package ai.loomspan.sidecar.http;

import ai.loomspan.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static ai.loomspan.sidecar.http.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class HttpExecutorLifecycleIntegrationTest {
    @TempDir Path empty;
    @ParameterizedTest @ValueSource(booleans={false,true})
    void frameworkDrainPrecedesClientCloseIncludingAsyncEvents(boolean async) throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var fixture=new HttpTestSupport(e->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}respond(e,200,null,"drained");});var context=HttpFrameworkFixture.start(empty);var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            if(async) ((org.springframework.context.event.SimpleApplicationEventMulticaster)context.getBean("applicationEventMulticaster")).setTaskExecutor(pool);
            HttpFrameworkFixture.publish(context,List.of(HttpFrameworkFixture.leaf("/","")),fixture.settings(),"");
            var client=context.getBean(HttpExecutorClient.class);
            try(var child=new org.springframework.context.support.GenericApplicationContext(context)){child.refresh();}
            assertThat(client.isClosed()).isFalse();
            var admitted=context.getBean(SkillInvocationHandoff.class).handoff("call",Map.of());var call=pool.submit(()->{return admitted.invoke();});
            assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();var closing=pool.submit(context::close);
            try {Thread.sleep(100);assertThat(closing.isDone()).isFalse();assertThat(client.isClosed()).isFalse();}finally{release.countDown();}
            assertThat(call.get(3,TimeUnit.SECONDS)).isEqualTo("drained");closing.get(3,TimeUnit.SECONDS);assertThat(client.isClosed()).isTrue();client.close();
        }finally{release.countDown();}
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cutoffClosesClientWithoutSecondReadTimeoutDrain(boolean async) throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        try(var fixture=new HttpTestSupport(e->{entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException x){Thread.currentThread().interrupt();}e.close();});var context=HttpFrameworkFixture.start(empty,"--loomspan.shutdown.timeout=200ms");var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            if(async) ((org.springframework.context.event.SimpleApplicationEventMulticaster)context.getBean("applicationEventMulticaster")).setTaskExecutor(pool);
            HttpFrameworkFixture.publish(context,List.of(HttpFrameworkFixture.leaf("/","")),fixture.settings("read-timeout","30s"),"");
            var client=context.getBean(HttpExecutorClient.class);var admitted=context.getBean(SkillInvocationHandoff.class).handoff("call",Map.of());
            var call=pool.submit(()->catchThrowable(admitted::invoke));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
            var close=pool.submit(context::close);close.get(2,TimeUnit.SECONDS);assertThat(client.isClosed()).isTrue();
            assertThat(call.get(2,TimeUnit.SECONDS)).isInstanceOf(SkillException.class);release.countDown();
        }finally{release.countDown();}
    }
}
