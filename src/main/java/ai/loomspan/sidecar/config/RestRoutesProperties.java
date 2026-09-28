package ai.loomspan.sidecar.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("loomspan-sidecar")
public class RestRoutesProperties {
    private RestRoutes restRoutes = new RestRoutes();
    public RestRoutes getRestRoutes() { return restRoutes; }
    public void setRestRoutes(RestRoutes value) { restRoutes = value; }
    public static class RestRoutes {
        private String location = "classpath:/sidecar-empty-rest-routes.yaml";
        public String getLocation() { return location; }
        public void setLocation(String value) { location = value; }
    }
    private java.util.Set<String> urlVariables = java.util.Set.of();

    public java.util.Set<String> getUrlVariables() {
        return urlVariables;
    }

    public void setUrlVariables(java.util.Set<String> urlVariables) {
        this.urlVariables = urlVariables == null ? java.util.Set.of() : java.util.Set.copyOf(urlVariables);
    }
}
