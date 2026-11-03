package ai.loomspan.sidecar.http;

import ai.loomspan.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.*;
import static ai.loomspan.sidecar.http.HttpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class HttpExecutorIntegrationTest {
    @TempDir Path empty;
    @Test void yamlExecutorUsesCapturedSettingsCredentialsAndAuthentication() throws Exception {
        var requests=new ArrayList<String>();
        try(var fixture=new HttpTestSupport(e->{requests.add(e.getRequestURI()+"|"+e.getRequestHeaders().getFirst("Authorization"));respond(e,200,null,"exact\n");});var context=HttpFrameworkFixture.start(empty)) {
            var cipher=context.getBean(ai.loomspan.sidecar.configuration.ProviderCredentialCipher.class);
            var base=fixture.settings("auth",map("mode","fixed","header-refs",Map.of("Authorization","service")));
            var a=HttpExecutorConfiguration.withCredentials(base,List.of(new ai.loomspan.sidecar.storage.EncryptedCredential("service","v1",cipher.encrypt("service","v1","Bearer first"))));
            HttpFrameworkFixture.publish(context,List.of(HttpFrameworkFixture.leaf("/first","input_schema: {type: object, properties: {id: {type: integer}}, required: [id]}")),a,"");
            var template=context.getBean(SkillTemplate.class);
            assertThatThrownBy(()->template.invoke("call",Map.of())).isInstanceOf(SkillInputValidationException.class);
            var retained=context.getBean(SkillInvocationHandoff.class).handoff("call",Map.of("id",1));
            var b=HttpExecutorConfiguration.withCredentials(base,List.of(new ai.loomspan.sidecar.storage.EncryptedCredential("service","v2",cipher.encrypt("service","v2","Bearer second"))));
            HttpFrameworkFixture.publish(context,List.of(HttpFrameworkFixture.leaf("/second","")),b,"");
            var views=new ArrayList<SkillExecutionView>();
            assertThat(retained.invoke(views::add)).isEqualTo("exact\n");assertThat(template.invoke("call",Map.of())).isEqualTo("exact\n");
            assertThat(requests).containsExactly("/first|Bearer first","/second|Bearer second");
            assertThat(views.toString()).doesNotContain("Bearer first");
            assertThat(context.getBean(SkillReloader.class).snapshot("default").skill("default-call").orElseThrow().kind()).isEqualTo(SkillKind.JAVA);
            assertThat(context.getBean(SkillReloader.class).snapshot("default").skill("default-http")).isEmpty();
            HttpFrameworkFixture.publish(context,List.of(HttpFrameworkFixture.leaf("/caller","rbac_roles: [READER]")),fixture.settings("auth",Map.of("mode","caller-passthrough")),"");
            assertThatThrownBy(()->template.invoke("call",Map.of())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            var jwt=org.springframework.security.oauth2.jwt.Jwt.withTokenValue("captured.original").header("alg","none").subject("reader").expiresAt(java.time.Instant.now().plusSeconds(300)).build();
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken(jwt,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_READER"))));
            var admission=context.getBean(SkillInvocationHandoff.class).handoff("call",Map.of());
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            assertThat(admission.invoke()).isEqualTo("exact\n");assertThat(requests.getLast()).isEqualTo("/caller|Bearer captured.original");
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void standardToolReceivesOnlyDesignatedRecoverableFeedback() throws Exception {
        var status=new AtomicInteger(409);var modelCalls=new AtomicInteger();var feedback=new AtomicReference<String>();var providerFailure=new AtomicReference<Throwable>();
        String exact="  application correction 雪\n";
        var applicationTokens=new java.util.concurrent.ConcurrentLinkedQueue<String>();
        try(var application=new HttpTestSupport(e->{applicationTokens.add(String.valueOf(e.getRequestHeaders().getFirst("Authorization")));respond(e,status.get(),"text/plain",exact);});var provider=new HttpTestSupport(e->{
            try {
                var json=new tools.jackson.databind.json.JsonMapper();var request=json.readTree(e.getRequestBody().readAllBytes());modelCalls.incrementAndGet();
                Object message=null;
                for(var m:request.path("messages"))if(m.path("role").asText().equals("tool")){feedback.set(m.path("content").asText());message=Map.of("role","assistant","content","DONE");}
                if(message==null)message=Map.of("role","assistant","content","","tool_calls",List.of(Map.of("id","call-1","type","function","function",Map.of("name","default-call","arguments","{}"))));
                respond(e,200,"application/json",json.writeValueAsBytes(Map.of("id","fixture","object","chat.completion","created",1,"model","fixture","choices",List.of(Map.of("index",0,"message",message,"finish_reason",feedback.get()==null?"tool_calls":"stop")),"usage",Map.of("prompt_tokens",1,"completion_tokens",1,"total_tokens",2))));
            }catch(Throwable failure){providerFailure.set(failure);respond(e,500,null,"fixture error");}
        });var context=HttpFrameworkFixture.start(empty)) {
            var op="""
                    name: call
                    description: HTTP application operation
                    executor: http
                    config: {target: app, method: GET, path: /, recoverableStatuses: [409]}
                    """;
            var parent=new SkillDocument("parent.yaml","name: parent\ndescription: Use application tool\nmodel: local\nallowed_skills: [{name: default-call}]\n");
            String models="""
                    connections:
                      local: {driver: openai, base-url: '%s/v1', api-key-ref: fixture-key, provider-retry: {enabled: false}}
                    models:
                      local: {connection: local, provider-model: fixture}
                    """.formatted(provider.base());
            HttpFrameworkFixture.publish(context,List.of(new SkillDocument("call.yaml",op),parent),application.settings("auth",Map.of("mode","caller-passthrough")),models);
            var template=context.getBean(SkillTemplate.class);var views=new ArrayList<SkillExecutionView>();
            HttpSkillExecutorTest.authenticate("original.tool.token",java.time.Instant.now().plusSeconds(300));
            assertThatThrownBy(()->template.invoke("call",Map.of())).isInstanceOf(RecoverableSkillException.class).hasMessage("HTTP 409\n"+exact);
            HttpSkillExecutorTest.authenticate("original.tool.token",java.time.Instant.now().plusSeconds(300));
            assertThat(template.invoke("parent",Map.of(),views::add)).isEqualTo("DONE");
            assertThat(providerFailure.get()).isNull();assertThat(modelCalls).hasValue(2);
            assertThat(applicationTokens).containsOnly("Bearer original.tool.token");
            var decoded=new tools.jackson.databind.json.JsonMapper().readTree(feedback.get());
            assertThat(decoded.path("message").asText()).isEqualTo("HTTP 409\n"+exact);
            assertThat(decoded.path("correctionPermitted").asBoolean()).isTrue();
            assertThat(views).hasSize(1);assertThat(views.getFirst().events()).anySatisfy(event->{assertThat(event.type()).isEqualTo("ERROR");assertThat(event.details()).containsEntry("classification","RECOVERABLE");});
            for(int terminal:List.of(400,401,403,302,500)) {
                status.set(terminal);modelCalls.set(0);feedback.set(null);
                assertThatThrownBy(()->template.invoke("parent",Map.of())).isInstanceOf(SkillException.class);
                assertThat(modelCalls).hasValue(1);assertThat(feedback.get()).isNull();
            }
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
}
