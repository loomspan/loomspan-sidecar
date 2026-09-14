package ai.loomspan.sidecar.rest;

import ai.loomspan.sidecar.config.RestRoutesProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
final class RestRouteLoader {
    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_.-]*)}");
    private static final Pattern HEADER_NAME = Pattern.compile("[!#$%&'*+.^_`|~0-9A-Za-z-]+");
    private static final Set<String> RESERVED_HEADERS = Set.of(
            "host", "content-length", "transfer-encoding", "accept", "content-type");
    private static final Set<String> ROOT_FIELDS = Set.of("targets", "routes");
    private static final Set<String> TARGET_FIELDS = Set.of("base-url", "auth", "ssl-bundle",
            "connect-timeout", "read-timeout", "max-response-size");
    private static final Set<String> AUTH_FIELDS = Set.of("mode", "headers");
    private static final Set<String> ROUTE_FIELDS = Set.of("target", "method", "path");

    private final RestRouteConfiguration configuration;

    RestRouteLoader(RestRoutesProperties properties, ConfigurableEnvironment environment,
                    ResourceLoader resources, ObjectProvider<SslBundles> sslBundlesProvider) {
        String location = properties.getRestRoutesLocation();
        if (!StringUtils.hasText(location)) {
            throw new IllegalStateException("REST routes location must not be blank");
        }
        this.configuration = load(location, environment, resources,
                sslBundlesProvider.getIfAvailable());
    }

    RestRouteConfiguration configuration() {
        return configuration;
    }

    private static RestRouteConfiguration load(String location, ConfigurableEnvironment environment,
                                               ResourceLoader resources, SslBundles sslBundles) {
        Resource resource = resources.getResource(location);
        if (!resource.exists() || !resource.isReadable()) {
            throw failure(location, "$", "route file is missing or unreadable", null);
        }
        JsonNode root;
        try (var input = resource.getInputStream()) {
            var mapper = YAMLMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .build();
            root = mapper.readTree(input);
        } catch (IOException | RuntimeException ex) {
            String detail = ex.getMessage() != null
                    && ex.getMessage().toLowerCase(Locale.ROOT).contains("duplicate")
                    ? "duplicate mapping key" : "malformed YAML";
            throw failure(location, "$", detail, ex);
        }
        if (root == null || !root.isObject()) {
            throw failure(location, "$", "document root must be an object", null);
        }
        requireOnly(location, "$", root, ROOT_FIELDS);
        JsonNode targetsNode = requiredObject(location, root, "targets");
        JsonNode routesNode = requiredObject(location, root, "routes");

        Map<String, RestRouteConfiguration.Target> targets = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> targetFields = targetsNode.properties().iterator();
        while (targetFields.hasNext()) {
            var entry = targetFields.next();
            String rawName = entry.getKey();
            String keyPath = "targets." + rawName;
            String name = resolve(location, keyPath, rawName, environment);
            if (!StringUtils.hasText(name) || targets.containsKey(name)) {
                throw failure(location, keyPath, "target name is blank or duplicated after placeholder resolution", null);
            }
            targets.put(name, parseTarget(location, keyPath, name, entry.getValue(), environment, sslBundles));
        }

        Map<String, RestRouteConfiguration.Route> routes = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> routeFields = routesNode.properties().iterator();
        while (routeFields.hasNext()) {
            var entry = routeFields.next();
            String rawName = entry.getKey();
            String keyPath = "routes." + rawName;
            String name = resolve(location, keyPath, rawName, environment);
            if (!StringUtils.hasText(name) || routes.containsKey(name)) {
                throw failure(location, keyPath, "route name is blank or duplicated after placeholder resolution", null);
            }
            routes.put(name, parseRoute(location, keyPath, name, entry.getValue(), environment));
        }
        for (var route : routes.values()) {
            if (!targets.containsKey(route.target())) {
                throw failure(location, "routes." + route.skillName() + ".target",
                        "references unknown target", null);
            }
        }
        return new RestRouteConfiguration(location, targets, routes);
    }

    private static RestRouteConfiguration.Target parseTarget(String location, String path, String name,
                                                              JsonNode node, ConfigurableEnvironment environment,
                                                              SslBundles sslBundles) {
        requireObject(location, path, node);
        requireOnly(location, path, node, TARGET_FIELDS);
        URI baseUrl = parseBaseUrl(location, path + ".base-url",
                requiredString(location, path, node, "base-url", environment));
        JsonNode authNode = requiredObject(location, node, "auth", path);
        requireOnly(location, path + ".auth", authNode, AUTH_FIELDS);
        String modeText = requiredString(location, path + ".auth", authNode, "mode", environment);
        RestRouteConfiguration.AuthMode mode;
        try {
            mode = RestRouteConfiguration.AuthMode.valueOf(modeText.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw failure(location, path + ".auth.mode", "unsupported authentication mode", null);
        }
        Map<String, String> headers = parseHeaders(location, path + ".auth", authNode.get("headers"), environment);
        if (mode == RestRouteConfiguration.AuthMode.STATIC && headers.isEmpty()) {
            throw failure(location, path + ".auth.headers", "static authentication requires headers", null);
        }
        if (mode != RestRouteConfiguration.AuthMode.STATIC && !headers.isEmpty()) {
            throw failure(location, path + ".auth.headers", "headers are allowed only for static authentication", null);
        }
        String bundle = optionalString(location, path, node, "ssl-bundle", environment);
        if (bundle != null) {
            if (sslBundles == null) {
                throw failure(location, path + ".ssl-bundle", "references an unavailable SSL bundle", null);
            }
            try {
                sslBundles.getBundle(bundle);
            } catch (RuntimeException ex) {
                throw failure(location, path + ".ssl-bundle", "references an unknown SSL bundle", null);
            }
        }
        Duration connect = parseDuration(location, path + ".connect-timeout",
                requiredString(location, path, node, "connect-timeout", environment));
        Duration read = parseDuration(location, path + ".read-timeout",
                requiredString(location, path, node, "read-timeout", environment));
        long max = parseDataSize(location, path + ".max-response-size",
                requiredString(location, path, node, "max-response-size", environment));
        return new RestRouteConfiguration.Target(name, baseUrl,
                new RestRouteConfiguration.Auth(mode, headers), bundle, connect, read, max);
    }

    private static RestRouteConfiguration.Route parseRoute(String location, String path, String name,
                                                            JsonNode node, ConfigurableEnvironment environment) {
        requireObject(location, path, node);
        requireOnly(location, path, node, ROUTE_FIELDS);
        String target = requiredString(location, path, node, "target", environment);
        String methodText = requiredString(location, path, node, "method", environment);
        RestRouteConfiguration.Method method;
        try {
            method = RestRouteConfiguration.Method.valueOf(methodText.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw failure(location, path + ".method", "only GET and POST are supported", null);
        }
        String routePath = requiredString(location, path, node, "path", environment);
        List<RestRouteConfiguration.PathPart> parts = new ArrayList<>();
        List<String> variables = new ArrayList<>();
        validateAndCompilePath(location, path + ".path", routePath, parts, variables);
        return new RestRouteConfiguration.Route(name, target, method, routePath, parts, variables);
    }

    private static Map<String, String> parseHeaders(String location, String path, JsonNode node,
                                                    ConfigurableEnvironment environment) {
        if (node == null || node.isNull()) return Map.of();
        requireObject(location, path + ".headers", node);
        Map<String, String> headers = new LinkedHashMap<>();
        Set<String> normalizedNames = new HashSet<>();
        node.properties().forEach(entry -> {
            String name = resolve(location, path + ".headers.<name>", entry.getKey(), environment);
            if (!HEADER_NAME.matcher(name).matches() || RESERVED_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                throw failure(location, path + ".headers." + entry.getKey(), "invalid or reserved header name", null);
            }
            if (!normalizedNames.add(name.toLowerCase(Locale.ROOT))) {
                throw failure(location, path + ".headers." + entry.getKey(),
                        "duplicate header after placeholder resolution", null);
            }
            if (!entry.getValue().isTextual()) {
                throw failure(location, path + ".headers." + entry.getKey(), "header value must be a string", null);
            }
            String value = resolve(location, path + ".headers." + entry.getKey(), entry.getValue().asText(), environment);
            if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
                throw failure(location, path + ".headers." + entry.getKey(), "header value contains a line break", null);
            }
            if (headers.put(name, value) != null) {
                throw failure(location, path + ".headers." + entry.getKey(), "duplicate header after placeholder resolution", null);
            }
        });
        return Map.copyOf(headers);
    }

    private static URI parseBaseUrl(String location, String path, String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || !StringUtils.hasText(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() > 65535) {
                throw failure(location, path, "must be an absolute HTTP(S) URL without user-info, query or fragment", null);
            }
            String rawPath = uri.getRawPath();
            if (rawPath != null) validateStaticSegments(location, path, rawPath);
            return uri;
        } catch (URISyntaxException ex) {
            throw failure(location, path, "must be a valid absolute HTTP(S) URL", null);
        }
    }

    private static void validateAndCompilePath(String location, String path, String value,
                                               List<RestRouteConfiguration.PathPart> parts,
                                               List<String> variables) {
        if (!value.startsWith("/") || value.startsWith("//") || value.contains("?") || value.contains("#")
                || value.contains("://") || value.indexOf('\\') >= 0 || value.chars().anyMatch(Character::isWhitespace)
                || hasMalformedPercentEscape(value)) {
            throw failure(location, path, "must be an absolute path without authority, scheme, query or fragment", null);
        }
        String[] segments = value.substring(1).split("/", -1);
        Set<String> seen = new HashSet<>();
        for (String segment : segments) {
            Matcher matcher = VARIABLE.matcher(segment);
            if (matcher.matches()) {
                String variable = matcher.group(1);
                if (!seen.add(variable)) throw failure(location, path, "contains a repeated path variable", null);
                variables.add(variable);
                parts.add(new RestRouteConfiguration.PathPart(variable, true));
            } else {
                if (segment.contains("{") || segment.contains("}")) {
                    throw failure(location, path, "variables must occupy a complete path segment", null);
                }
                validateStaticSegment(location, path, segment);
                parts.add(new RestRouteConfiguration.PathPart(segment, false));
            }
        }
        try {
            new URI(VARIABLE.matcher(value).replaceAll("value"));
        } catch (URISyntaxException ex) {
            throw failure(location, path, "must be a valid URI path", null);
        }
    }

    private static boolean hasMalformedPercentEscape(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '%' && (index + 2 >= value.length()
                    || Character.digit(value.charAt(index + 1), 16) < 0
                    || Character.digit(value.charAt(index + 2), 16) < 0)) return true;
        }
        return false;
    }

    private static void validateStaticSegments(String location, String path, String rawPath) {
        for (String segment : rawPath.split("/", -1)) validateStaticSegment(location, path, segment);
    }

    private static void validateStaticSegment(String location, String path, String segment) {
        String normalized = segment.toLowerCase(Locale.ROOT);
        String previous;
        do {
            previous = normalized;
            normalized = normalized.replace("%25", "%").replace("%2e", ".");
        } while (!normalized.equals(previous));
        if (normalized.equals(".") || normalized.equals("..") || normalized.contains("%2f")
                || normalized.contains("%5c") || segment.indexOf('\\') >= 0) {
            throw failure(location, path, "contains a traversal or encoded separator segment", null);
        }
    }

    private static Duration parseDuration(String location, String path, String value) {
        try {
            Duration duration = DurationStyle.detectAndParse(value);
            if (duration.toMillis() <= 0 || duration.toMillis() > Integer.MAX_VALUE) throw new IllegalArgumentException();
            return duration;
        } catch (RuntimeException ex) {
            throw failure(location, path, "must be a positive duration no greater than 2147483647ms", null);
        }
    }

    private static long parseDataSize(String location, String path, String value) {
        try {
            long bytes = DataSize.parse(value).toBytes();
            if (bytes <= 0 || bytes > Integer.MAX_VALUE) throw new IllegalArgumentException();
            return bytes;
        } catch (RuntimeException ex) {
            throw failure(location, path, "must be a positive size no greater than 2147483647 bytes", null);
        }
    }

    private static String requiredString(String location, String path, JsonNode node, String field,
                                         ConfigurableEnvironment environment) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) {
            throw failure(location, path + "." + field, "required string is missing", null);
        }
        return resolve(location, path + "." + field, value.asText(), environment);
    }

    private static String optionalString(String location, String path, JsonNode node, String field,
                                         ConfigurableEnvironment environment) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw failure(location, path + "." + field, "must be a string", null);
        String resolved = resolve(location, path + "." + field, value.asText(), environment);
        if (!StringUtils.hasText(resolved)) throw failure(location, path + "." + field, "must not be blank", null);
        return resolved;
    }

    private static String resolve(String location, String path, String value,
                                  ConfigurableEnvironment environment) {
        try {
            return environment.resolveRequiredPlaceholders(value);
        } catch (IllegalArgumentException ex) {
            Matcher matcher = Pattern.compile("\\$\\{[^}]+}").matcher(value);
            String placeholder = matcher.find() ? matcher.group() : "required placeholder";
            throw failure(location, path, "cannot resolve " + placeholder, null);
        }
    }

    private static JsonNode requiredObject(String location, JsonNode parent, String field) {
        return requiredObject(location, parent, field, "$" );
    }

    private static JsonNode requiredObject(String location, JsonNode parent, String field, String parentPath) {
        JsonNode value = parent.get(field);
        requireObject(location, parentPath.equals("$") ? field : parentPath + "." + field, value);
        return value;
    }

    private static void requireObject(String location, String path, JsonNode node) {
        if (node == null || !node.isObject()) throw failure(location, path, "required object is missing", null);
    }

    private static void requireOnly(String location, String path, JsonNode node, Set<String> allowed) {
        node.propertyNames().forEach(field -> {
            if (!allowed.contains(field)) throw failure(location, path + "." + field, "unknown field", null);
        });
    }

    private static IllegalStateException failure(String location, String path, String reason, Throwable cause) {
        String message = "Invalid REST routes at " + location + " [" + path + "]: " + reason;
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }
}
