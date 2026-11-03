package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static ai.loomspan.sidecar.http.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class HttpExecutorConfigurationTest {
    static Map<String,Object> settings(Object... pairs){var target=map("base-url","http://localhost/base");target.putAll(map(pairs));return map("targets",Map.of("app",target));}
    @Test void defaultsAndAllMethods() {
        var settings=HttpExecutorConfiguration.validate(settings());var target=settings.targets().get("app");
        assertThat(target.connectMillis()).isEqualTo(5000);assertThat(target.readMillis()).isEqualTo(60000);assertThat(target.maxResponseSize()).isEqualTo(1048576);
        for(String method:List.of("GET","POST","PUT","PATCH","DELETE")) assertThat(HttpExecutorConfiguration.validateOperation(operation("method",method),settings).method()).isEqualTo(method);
        assertThatThrownBy(()->HttpExecutorConfiguration.validateOperation(operation("body",Map.of("source","input")),settings)).isInstanceOf(SkillException.class);
    }
    @ParameterizedTest @ValueSource(strings={"0ms","-1s","0.1ms","2147483648ms","NaN","Infinity","999999999999999999999d"})
    void rejectsInvalidTimeouts(String value){assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("read-timeout",value))).isInstanceOf(SkillException.class);}
    @ParameterizedTest @ValueSource(strings={"0B","-1B","2147483648B","NaN","1e99MB"})
    void rejectsInvalidSizes(String value){assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("max-response-size",value))).isInstanceOf(SkillException.class);}
    @ParameterizedTest @ValueSource(strings={"file:///tmp","http://user:secret@host","http://host?q=x","http://host#frag","http://host/a/../b","http://host/%252e%252e","http://host/%2f"})
    void rejectsBadBaseWithoutNetwork(String value){assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("base-url",value))).isInstanceOf(SkillException.class).hasMessageStartingWith("Invalid HTTP").hasNoCause();}
    @ParameterizedTest @ValueSource(strings={"x","","/bad~","/bad~2","/~01~9"})
    void rejectsInvalidPointers(String value){assertThatThrownBy(()->HttpExecutorConfiguration.validateOperation(operation("queryParams",Map.of("a",value)),HttpExecutorConfiguration.validate(settings()))).isInstanceOf(SkillException.class);}
    @Test void strictOperationShapesAndStatuses() {
        var settings=HttpExecutorConfiguration.validate(settings());
        for(var op:List.of(operation("method","HEAD"),operation("path","https://other/a"),operation("path","//other/a"),operation("path","/pre{id}"),operation("path","/{id}"),operation("pathParams",Map.of("unused","/x")),operation("unknown",1),operation("body",null),operation("queryParams",null),operation("recoverableStatuses",null),operation("target","missing"),operation("method","POST","body",map("source","input","field","/x")))) {
            assertThatThrownBy(()->HttpExecutorConfiguration.validateOperation(op,settings)).isInstanceOf(SkillException.class);
        }
        for(Object value:List.of(401,403,399,500,"409",409.5,409L+Integer.MAX_VALUE))assertThatThrownBy(()->HttpExecutorConfiguration.validateOperation(operation("recoverableStatuses",List.of(value)),settings)).isInstanceOf(SkillException.class);
        assertThatThrownBy(()->HttpExecutorConfiguration.validateOperation(operation("recoverableStatuses",List.of(409,409)),settings)).isInstanceOf(SkillException.class);
        assertThat(HttpExecutorConfiguration.validateOperation(operation("recoverableStatuses",List.of(400,409,499)),settings).recoverableStatuses()).containsExactlyInAnyOrder(400,409,499);
    }
    @Test void validatesHeadersAndAuthOwnership() {
        for(String name:List.of("Host","Content-Length","Transfer-Encoding","Authorization","Cookie","Proxy-Authorization","bad name"))assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("headers",Map.of(name,"x")))).isInstanceOf(SkillException.class);
        for(String value:List.of("x\r\nInjected: y","x\u0000","x\u007f"))assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("headers",Map.of("X-App",value)))).isInstanceOf(SkillException.class);
        assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("headers",map("X-App","a","x-app","b")))).isInstanceOf(SkillException.class);
        assertThat(HttpExecutorConfiguration.validate(settings("headers",Map.of("Accept","text/plain"))).targets()).hasSize(1);
        for(String mode:List.of("none","caller-passthrough","bogus"))assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("auth",map("mode",mode,"header-refs",Map.of("X-Key","id"))))).isInstanceOf(SkillException.class);
        assertThatThrownBy(()->HttpExecutorConfiguration.validate(settings("auth",Map.of("mode","fixed")))).isInstanceOf(SkillException.class);
        assertThatThrownBy(()->HttpExecutorConfiguration.validateOperation(operation("method","POST","body",Map.of("source","input")),HttpExecutorConfiguration.validate(settings("headers",Map.of("Content-Type","text/plain"))))).isInstanceOf(SkillException.class);
    }
}
