package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.SkillExecutorInvocation;
import ai.loomspan.api.SkillException;
import ai.loomspan.sidecar.configuration.ProviderCredentialCipher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.LinkedHashMap;

@Component
public final class HttpSkillExecutor {
    private final HttpExecutorClient client;
    private final ProviderCredentialCipher cipher;
    private final Clock clock;
    @Autowired public HttpSkillExecutor(HttpExecutorClient client,ProviderCredentialCipher cipher,Clock clock){this.client=client;this.cipher=cipher;this.clock=clock;}
    @SkillExecutor(name="http",namespace="default",config="http")
    public String execute(SkillExecutorInvocation invocation) {
        var settings=HttpExecutorConfiguration.validate(invocation.executorConfig());
        var op=HttpExecutorConfiguration.validateOperation(invocation.skillConfig(),settings);
        var mapped=HttpRequestMapper.map(op,invocation.input());
        var headers=new LinkedHashMap<>(op.target().headers());
        if(op.target().authMode().equals("fixed")) {
            try {
                op.target().headerRefs().forEach((name,ref)-> {
                    var encrypted=settings.credentials().get(ref);
                    String value=cipher.decrypt(encrypted.identifier(),encrypted.version(),encrypted.ciphertext());
                    HttpExecutorConfiguration.headerValue(value); headers.put(name,value);
                });
            } catch(RuntimeException failure) {throw new SkillException("HTTP service credential unavailable");}
        }
        if(op.target().authMode().equals("caller-passthrough")) {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            if(!(auth instanceof JwtAuthenticationToken jwt)||!jwt.isAuthenticated()||jwt.getToken().getExpiresAt()==null
                    ||!clock.instant().isBefore(jwt.getToken().getExpiresAt())) throw new SkillException("HTTP caller credential missing or expired");
            headers.put("Authorization","Bearer "+jwt.getToken().getTokenValue());
        }
        return client.execute(mapped,op.target(),headers,op.recoverableStatuses());
    }
}
