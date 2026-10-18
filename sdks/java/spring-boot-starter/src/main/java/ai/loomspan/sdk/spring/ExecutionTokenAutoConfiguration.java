package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Explicit SDK signing and issuance; does not install an application security chain. */
@AutoConfiguration
@EnableConfigurationProperties({SigningProperties.class, IssuanceProperties.class})
public class ExecutionTokenAutoConfiguration {
    @Bean SigningKeySource signingKeySource(SigningProperties properties, Environment environment) {
        for (String name : new String[]{"keystore-password", "key-password"}) {
            if (environment.containsProperty("loomspan-sdk.signing.provided." + name))
                throw new IllegalArgumentException("loomspan-sdk.signing.provided." + name + " is forbidden; use a password-reference");
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
