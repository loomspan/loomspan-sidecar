package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

/** Explicit SDK signing and issuance; does not install an application security chain. */
@AutoConfiguration
@EnableConfigurationProperties({SigningProperties.class, IssuanceProperties.class})
public class ExecutionTokenAutoConfiguration {
    @Bean SigningKeySource signingKeySource(SigningProperties properties, ConfigurableEnvironment environment) {
        for (String name : new String[]{"format", "key-alias", "keystore-password-reference",
                "key-password-reference", "certificate-location", "keystore-password", "key-password"}) {
            String property = "loomspan-sdk.signing.provided." + name;
            var canonical = ConfigurationPropertyName.of(property);
            for (var source : ConfigurationPropertySources.get(environment)) {
                if (source.getConfigurationProperty(canonical) != null)
                    throw new IllegalArgumentException(property + " is unsupported; use unencrypted PKCS#8 PRIVATE KEY with provided.key-location and provided.jwks-export");
            }
        }
        return SigningKeys.initialize(properties.settings());
    }
    @Bean ExecutionTokenIssuer executionTokenIssuer(SigningKeySource source, IssuanceProperties properties) {
        return new ExecutionTokenIssuer(source, properties.settings());
    }
    @Bean SpringExecutionTokenIssuer springExecutionTokenIssuer(ExecutionTokenIssuer issuer) {
        return new SpringExecutionTokenIssuer(issuer);
    }
}
