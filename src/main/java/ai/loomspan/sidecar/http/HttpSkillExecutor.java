package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillExecutor;
import ai.loomspan.api.extensions.v1.SkillExecutorInvocation;
import ai.loomspan.api.SkillException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.util.LinkedHashMap;

@Component
public final class HttpSkillExecutor {
    private final HttpExecutorClient client;
    private final Clock clock;
    public HttpSkillExecutor(HttpExecutorClient client,Clock clock){this.client=client;this.clock=clock;}
    @SkillExecutor(name="http",namespace="default",config="http")
    public String execute(SkillExecutorInvocation invocation) {
        var settings=HttpExecutorConfiguration.validate(invocation.executorConfig());
        var op=HttpExecutorConfiguration.validateOperation(invocation.skillConfig(),settings);
        var mapped=HttpRequestMapper.map(op,invocation.input());
        var headers=new LinkedHashMap<>(op.target().headers());
        if(op.target().authMode().equals("caller-passthrough")) {
            var auth=SecurityContextHolder.getContext().getAuthentication();
            if(!(auth instanceof JwtAuthenticationToken jwt)||!jwt.isAuthenticated()||jwt.getToken().getExpiresAt()==null
                    ||!clock.instant().isBefore(jwt.getToken().getExpiresAt())) throw new SkillException("HTTP caller credential missing or expired");
            headers.put("Authorization","Bearer "+jwt.getToken().getTokenValue());
        }
        return client.execute(mapped,op.target(),headers,op.recoverableStatuses());
    }
}
