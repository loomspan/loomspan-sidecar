package ai.loomspan.sdk.spring;

import ai.loomspan.sdk.SidecarClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;

/** Creates an application-owned client using the same validation as the Java builder. */
@AutoConfiguration(after = SslAutoConfiguration.class)
@EnableConfigurationProperties(SidecarClientProperties.class)
public class SidecarClientAutoConfiguration {
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(SidecarClient.class)
    SidecarClient sidecarClient(SidecarClientProperties properties, ObjectProvider<SslBundles> bundles) {
        var builder = SidecarClient.builder()
                .baseUrl(properties.getBaseUrl())
                .connectTimeout(properties.getConnectTimeout())
                .responseTimeout(properties.getResponseTimeout())
                .maxResponseSize(properties.getMaxResponseSize());
        if (properties.getSslBundle() != null) {
            builder.sslContext(bundles.getObject().getBundle(properties.getSslBundle()).createSslContext());
        }
        return builder.build();
    }
}
