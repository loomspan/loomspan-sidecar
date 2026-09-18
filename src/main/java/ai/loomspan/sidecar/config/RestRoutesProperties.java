package ai.loomspan.sidecar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loomspan-sidecar")
public class RestRoutesProperties {
    private java.util.Set<String> urlVariables = java.util.Set.of();

    public java.util.Set<String> getUrlVariables() {
        return urlVariables;
    }

    public void setUrlVariables(java.util.Set<String> urlVariables) {
        this.urlVariables = urlVariables == null ? java.util.Set.of() : java.util.Set.copyOf(urlVariables);
    }
}
