package ai.loomspan.sidecar.rest;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record RestRouteConfiguration(String location, Map<String, Target> targets, Map<String, Route> routes) {
    public RestRouteConfiguration {
        targets = Map.copyOf(targets);
        routes = Map.copyOf(routes);
    }

    enum AuthMode { NONE, STATIC, CALLER_PASSTHROUGH }
    enum Method { GET, POST }

    record Auth(AuthMode mode, Map<String, String> headers) {
        Auth { headers = Map.copyOf(headers); }
    }

    record Target(String name, URI baseUrl, Auth auth, String sslBundle,
                  Duration connectTimeout, Duration readTimeout, long maxResponseSize) {}

    record Route(String skillName, String target, Method method, String path,
                 List<PathPart> parts, List<String> variables) {
        Route {
            parts = List.copyOf(parts);
            variables = List.copyOf(variables);
        }
    }

    record PathPart(String value, boolean variable) {}
}
