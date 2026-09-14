package ai.loomspan.sidecar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loomspan-sidecar")
public class RestRoutesProperties {
    private String restRoutesLocation = "file:/sidecar/rest-routes.yaml";

    public String getRestRoutesLocation() {
        return restRoutesLocation;
    }

    public void setRestRoutesLocation(String restRoutesLocation) {
        this.restRoutesLocation = restRoutesLocation;
    }
}
