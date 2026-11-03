package ai.loomspan.sidecar.http;

import ai.loomspan.api.*;
import ai.loomspan.sidecar.configuration.ProviderCredentialCipher;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import java.nio.file.Path;
import java.time.Clock;
import java.util.*;

final class HttpFrameworkFixture {
    @Configuration(proxyBeanMethods=false) @EnableAutoConfiguration
    @Import({HttpSkillExecutor.class,HttpExecutorClient.class})
    static class App {
        @Bean Clock clock(){return Clock.systemUTC();}
        @Bean ProviderCredentialCipher cipher(){return new ProviderCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));}
    }
    static ConfigurableApplicationContext start(Path empty,String... extra) {
        Path startup=empty.resolve("startup.yml");
        try {java.nio.file.Files.writeString(startup,"loomspan: {executor-config: {default: {http: {targets: {}}}}}\n");}catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        var args=new ArrayList<>(List.of("--spring.config.location="+startup.toUri(),
                "--spring.main.banner-mode=off","--logging.level.root=ERROR","--loomspan.skills.locations="+empty.toUri()+"*.yaml",
                "--loomspan.observability.enabled=false","--loomspan.shutdown.timeout=3s",
                "--spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"));
        for(String value:extra) {String key=value.substring(0,value.indexOf('='));args.removeIf(existing->existing.startsWith(key+"="));args.add(value);}
        return new SpringApplicationBuilder(App.class).web(WebApplicationType.NONE).run(args.toArray(String[]::new));
    }
    static void publish(ConfigurableApplicationContext context,List<SkillDocument> documents,Map<String,Object> http,String extraYaml) {
        String selected=new tools.jackson.dataformat.yaml.YAMLMapper().writeValueAsString(Map.of("loomspan",Map.of("executor-config",Map.of("default",Map.of("http",http)))));
        // Additional provider config is passed as a second loomspan child through tree assembly, never duplicate YAML keys.
        var tree=new tools.jackson.dataformat.yaml.YAMLMapper().readTree(selected);
        if(!extraYaml.isEmpty()) {
            var extra=new tools.jackson.dataformat.yaml.YAMLMapper().readTree(extraYaml);
            var loomspan=(tools.jackson.databind.node.ObjectNode)tree.get("loomspan");
            extra.properties().forEach(e->loomspan.set(e.getKey(),e.getValue()));
            selected=new tools.jackson.dataformat.yaml.YAMLMapper().writeValueAsString(tree);
        }
        var reloader=context.getBean(SkillReloader.class);reloader.publish(reloader.prepare("default",documents,new ExecutionConfiguration(selected),extraYaml.isEmpty()?Map.of():Map.of("fixture-key","fixture")));
    }
    static SkillDocument leaf(String path,String more){return new SkillDocument("call.yaml","""
            name: call
            description: HTTP fixture operation
            executor: http
            config:
              target: app
              method: GET
              path: %s
            %s
            """.formatted(path,more));}
}
