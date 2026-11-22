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
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
        if (mode == ConfigurationMode.DATABASE) {
            excludeDeploymentConnections(environment);
            // Load only our fixed empty bootstrap through Boot's installed YAML loader.
            // Never read or reconstruct the operator's selected Config Data here.
            var loaders = org.springframework.core.io.support.SpringFactoriesLoader.loadFactories(
                    org.springframework.boot.env.PropertySourceLoader.class, ConfigurationModeConfiguration.class.getClassLoader());
            org.springframework.core.annotation.AnnotationAwareOrderComparator.sort(loaders);
            var loader = loaders.stream().filter(candidate -> java.util.Arrays.asList(candidate.getFileExtensions()).contains("yml"))
                    .findFirst().orElseThrow();
            try {
                for (var source : loader.load("sidecar-empty-executor-bootstrap",
                        new org.springframework.core.io.ClassPathResource("sidecar-database-bootstrap.yml")))
                    environment.getPropertySources().addFirst(source);
            } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load empty database bootstrap", failure); }
            environment.getPropertySources().addFirst(new MapPropertySource("sidecar-database-bootstrap",
                    Map.of("loomspan.skills.locations", List.of("classpath:/sidecar-empty-skills/*.yaml"))));
        }
        return mode;
    }

    private static void excludeDeploymentConnections(ConfigurableEnvironment environment) {
        var sources = environment.getPropertySources();
        var original = new ArrayList<PropertySource<?>>();
        sources.forEach(original::add);
        for (PropertySource<?> source : original) {
            // The Boot binding facade delegates to these underlying sources.
            if (source.getName().equals("configurationProperties")) continue;
            if (source instanceof SystemEnvironmentPropertySource system) {
                var values = new java.util.LinkedHashMap<>(system.getSource());
                values.keySet().removeIf(ConfigurationModeConfiguration::deploymentConnection);
                sources.replace(source.getName(), new SystemEnvironmentPropertySource(source.getName(), values));
            } else if (source instanceof EnumerablePropertySource<?> enumerable) {
                sources.replace(source.getName(), new EnumerablePropertySource<EnumerablePropertySource<?>>(
                        source.getName(), enumerable) {
                    @Override public String[] getPropertyNames() {
                        return Arrays.stream(enumerable.getPropertyNames())
                                .filter(name -> !deploymentConnection(name)).toArray(String[]::new);
                    }
                    @Override public Object getProperty(String name) {
                        return deploymentConnection(name) ? null : enumerable.getProperty(name);
                    }
                });
            } else {
                sources.replace(source.getName(), new PropertySource<PropertySource<?>>(source.getName(), source) {
                    @Override public Object getProperty(String name) {
                        return deploymentConnection(name) ? null : source.getProperty(name);
                    }
                });
            }
        }
    }

    private static boolean deploymentConnection(String name) {
        String property = name.toLowerCase(Locale.ROOT);
        return property.equals("loomspan.connections") || property.startsWith("loomspan.connections.")
                || property.startsWith("loomspan.connections[") || property.equals("loomspan_connections")
                || property.startsWith("loomspan_connections_");
    }

    @Bean
    static ConfigurationPropertiesBindHandlerAdvisor databaseExecutionBinding(ConfigurationMode mode) {
        return parent -> mode == ConfigurationMode.FILE ? parent : new AbstractBindHandler(parent) {
            @Override
            public <T> Bindable<T> onStart(ConfigurationPropertyName name, Bindable<T> target,
                    BindContext context) {
                if (excluded(name) && !name.toString().equals("loomspan.executor-config")) return null;
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
                "loomspan.executor-config", "loomspan.execution-trace.persistence"))
            if (property.equals(root) || property.startsWith(root + ".") || property.startsWith(root + "[")) return true;
        return false;
    }
}
