package ai.loomspan.sidecar.configuration;

import org.springframework.boot.context.properties.ConfigurationPropertiesBindHandlerAdvisor;
import org.springframework.boot.context.properties.bind.AbstractBindHandler;
import org.springframework.boot.context.properties.bind.BindContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import java.util.List;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class ConfigurationModeConfiguration {
    @Bean
    static ConfigurationMode configurationMode(ConfigurableEnvironment environment) {
        String selected = Binder.get(environment).bind("loomspan-sidecar.configuration.mode",
                String.class).orElse("database");
        ConfigurationMode mode = switch (selected) {
            case "file" -> ConfigurationMode.FILE;
            case "database" -> ConfigurationMode.DATABASE;
            default -> throw new IllegalArgumentException("loomspan-sidecar.configuration.mode must be file or database");
        };
        if (mode == ConfigurationMode.DATABASE)
            environment.getPropertySources().addFirst(new MapPropertySource("sidecar-database-bootstrap",
                    Map.of("loomspan.skills.locations", List.of("classpath:/sidecar-empty-skills/*.yaml"))));
        return mode;
    }

    @Bean
    static ConfigurationPropertiesBindHandlerAdvisor databaseExecutionBinding(ConfigurationMode mode) {
        return parent -> mode == ConfigurationMode.FILE ? parent : new AbstractBindHandler(parent) {
            @Override
            public <T> Bindable<T> onStart(ConfigurationPropertyName name, Bindable<T> target,
                    BindContext context) {
                if (excluded(name)) return null;
                return super.onStart(name, target, context);
            }
            @Override
            public void onFinish(ConfigurationPropertyName name, Bindable<?> target, BindContext context,
                    Object result) throws Exception {
                try { super.onFinish(name, target, context, result); }
                catch (org.springframework.boot.context.properties.bind.UnboundConfigurationPropertiesException failure) {
                    // Strict binding still rejects unknown process settings. Only deliberately skipped
                    // execution settings are allowed to remain unbound in database mode.
                    if (!failure.getUnboundProperties().stream().allMatch(property -> excluded(property.getName())))
                        throw failure;
                }
            }
        };
    }

    private static boolean excluded(ConfigurationPropertyName name) {
        String property = name.toString();
        for (String root : List.of("loomspan.connections", "loomspan.models", "loomspan.session",
                "loomspan.execution-trace.persistence"))
            if (property.equals(root) || property.startsWith(root + ".") || property.startsWith(root + "[")) return true;
        return false;
    }
}
