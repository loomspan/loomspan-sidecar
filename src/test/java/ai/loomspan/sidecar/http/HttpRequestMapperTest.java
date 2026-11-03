package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static ai.loomspan.sidecar.http.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class HttpRequestMapperTest {
    static HttpRequestMapper.Request mapped(Map<String,Object> op,Map<String,Object> input){var settings=HttpExecutorConfiguration.validate(HttpExecutorConfigurationTest.settings());return HttpRequestMapper.map(HttpExecutorConfiguration.validateOperation(op,settings),input);}
    @Test void escapedPointersArraysScalarsAndOmissions() {
        var input=map("a/b",map("~key",List.of("雪 +?%#&=",0,false)),"empty","","nil",null,"array",List.of());
        var result=mapped(operation("path","/{id}","pathParams",Map.of("id","/a~1b/~0key/0"),"queryParams",map("v","/a~1b/~0key","e","/empty","null","/nil","missing","/missing","out","/a~1b/~0key/9","array","/array")),input);
        assertThat(result.uri().toString()).isEqualTo("http://localhost/base/%E9%9B%AA%20%2B%3F%25%23%26%3D?v=%E9%9B%AA%20%2B%3F%25%23%26%3D&v=0&v=false&e=");
        assertThat(mapped(operation("queryParams",Map.of("star","/*")),Map.of("*","literal")).uri().getRawQuery()).isEqualTo("star=literal");
    }
    @ParameterizedTest @ValueSource(strings={"/","\\",".","..","%2F","%5c","%2e%2e","%25252f","%252e%252e"})
    void rejectsPathTraversalValues(String value){assertThatThrownBy(()->mapped(operation("path","/{id}","pathParams",Map.of("id","/id")),Map.of("id",value))).isInstanceOf(SkillException.class);}
    @Test void rejectsAllInvalidPathAndQueryShapes() {
        for(Object value:Arrays.asList(null,"",Map.of(),List.of(),Double.NaN))assertThatThrownBy(()->mapped(operation("path","/{id}","pathParams",Map.of("id","/id")),map("id",value))).isInstanceOf(SkillException.class);
        for(Object value:List.of(Map.of(),List.of(List.of(1)),Arrays.asList(1,null)))assertThatThrownBy(()->mapped(operation("queryParams",Map.of("q","/v")),map("v",value))).isInstanceOf(SkillException.class);
        for(Object value:List.of(0,false,"hello"))assertThat(mapped(operation("path","/{id}","pathParams",Map.of("id","/id")),map("id",value)).uri().getPath()).endsWith("/"+value);
    }
    @Test void preservesBodySelectionTypesAndAbsence() {
        assertThat(mapped(operation("method","POST"),Map.of()).body()).isNull();
        for(Object value:Arrays.asList(null,"text",12,false,List.of(1),Map.of("x",2))) {
            var result=mapped(operation("method","PATCH","body",map("source","field","field","/outer/v")),map("outer",map("v",value)));
            assertThat(new tools.jackson.databind.json.JsonMapper().readTree(result.body())).isEqualTo(new tools.jackson.databind.json.JsonMapper().valueToTree(value));
        }
        assertThatThrownBy(()->mapped(operation("method","PUT","body",map("source","field","field","/missing")),Map.of())).isInstanceOf(SkillException.class);
    }
}
