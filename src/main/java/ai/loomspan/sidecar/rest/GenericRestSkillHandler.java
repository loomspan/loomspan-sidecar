package ai.loomspan.sidecar.rest;

import ai.loomspan.api.RestSkillHandler;
import ai.loomspan.api.RestSkillInvocation;
import ai.loomspan.api.SkillException;
import org.springframework.context.SmartLifecycle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
final class GenericRestSkillHandler implements RestSkillHandler, SmartLifecycle {
    private final RestRouteConfiguration configuration;
    private final RestTargetClients clients;
    private final AtomicBoolean running = new AtomicBoolean(true);

    GenericRestSkillHandler(RestRouteLoader loader, RestTargetClients clients) {
        this.configuration = loader.configuration();
        this.clients = clients;
    }

    @Override
    public String handle(RestSkillInvocation invocation) {
        RestRouteConfiguration.Route route = configuration.routes().get(invocation.skillName());
        if (route == null) throw new SkillException("No REST route is configured for skill '" + safe(invocation.skillName()) + "'");
        RestRouteConfiguration.Target target = configuration.targets().get(route.target());
        Map<String, Object> remaining = new LinkedHashMap<>(invocation.input());
        validateJsonMap(remaining, "input");
        URI uri = bindUri(target, route, remaining);
        try {
            RestClient.RequestBodySpec request = clients.get(target.name()).restClient()
                    .method(route.method() == RestRouteConfiguration.Method.GET
                            ? org.springframework.http.HttpMethod.GET : org.springframework.http.HttpMethod.POST)
                    .uri(uri)
                    .header(HttpHeaders.ACCEPT, "application/json, text/*");
            target.auth().headers().forEach(request::header);
            if (target.auth().mode() == RestRouteConfiguration.AuthMode.CALLER_PASSTHROUGH) {
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (!(authentication instanceof JwtAuthenticationToken jwt)) {
                    throw new SkillException(BoundedRestResponse.message(target.name(),
                            "caller-passthrough requires JWT authentication", null));
                }
                request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.getToken().getTokenValue());
            }
            if (route.method() == RestRouteConfiguration.Method.POST) {
                request.contentType(MediaType.APPLICATION_JSON).body(remaining);
            }
            String result = request.exchange((ignored, response) ->
                    BoundedRestResponse.read(target.name(), target.maxResponseSize(), response));
            if (result == null) throw new SkillException(BoundedRestResponse.message(target.name(), "empty client result", null));
            return result;
        } catch (SkillException failure) {
            throw failure;
        } catch (RestClientException failure) {
            preserveInterrupt(failure);
            throw new SkillException(BoundedRestResponse.message(target.name(), "transport error", null), failure);
        } catch (RuntimeException failure) {
            preserveInterrupt(failure);
            throw new SkillException(BoundedRestResponse.message(target.name(), "transport error", null), failure);
        }
    }

    @Override public void start() { running.set(true); }
    @Override public boolean isRunning() { return running.get(); }
    @Override public int getPhase() { return 0; }
    @Override public boolean isAutoStartup() { return true; }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) clients.close();
    }

    private static URI bindUri(RestRouteConfiguration.Target target, RestRouteConfiguration.Route route,
                               Map<String, Object> remaining) {
        StringBuilder path = new StringBuilder();
        String basePath = target.baseUrl().getRawPath();
        if (basePath != null && !basePath.isEmpty() && !basePath.equals("/")) {
            path.append(basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath);
        }
        for (var part : route.parts()) {
            path.append('/');
            if (!part.variable()) {
                path.append(part.value());
                continue;
            }
            Object value = remaining.remove(part.value());
            if (!isScalar(value)) throw new SkillException("REST path input '" + safe(part.value()) + "' must be a non-null scalar");
            String text = String.valueOf(value);
            if (isUnsafePathValue(text)) {
                throw new SkillException("REST path input '" + safe(part.value()) + "' is not a safe path segment");
            }
            path.append(UriUtils.encodePathSegment(text, StandardCharsets.UTF_8));
        }
        StringBuilder value = new StringBuilder(target.baseUrl().getScheme()).append("://")
                .append(target.baseUrl().getRawAuthority()).append(path);
        if (route.method() == RestRouteConfiguration.Method.GET && !remaining.isEmpty()) {
            List<Map.Entry<String, Object>> entries = new ArrayList<>(remaining.entrySet());
            entries.sort(Comparator.comparing(Map.Entry::getKey));
            value.append('?');
            for (int index = 0; index < entries.size(); index++) {
                var entry = entries.get(index);
                if (!isScalar(entry.getValue())) {
                    throw new SkillException("REST query input '" + safe(entry.getKey()) + "' must be a non-null scalar");
                }
                if (index > 0) value.append('&');
                value.append(UriUtils.encodeQueryParam(entry.getKey(), StandardCharsets.UTF_8))
                        .append('=')
                        .append(UriUtils.encodeQueryParam(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }
        }
        return URI.create(value.toString());
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Boolean || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long
                || value instanceof java.math.BigInteger || value instanceof java.math.BigDecimal
                || value instanceof Float number && Float.isFinite(number)
                || value instanceof Double number && Double.isFinite(number);
    }

    private static boolean isUnsafePathValue(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        while (true) {
            for (String segment : normalized.split("[/\\\\]", -1)) {
                if (segment.equals(".") || segment.equals("..")) return true;
            }
            if (normalized.contains("%2f") || normalized.contains("%5c")) return true;
            String decodedMarkers = normalized.replace("%25", "%").replace("%2e", ".");
            if (decodedMarkers.equals(normalized)) return false;
            normalized = decodedMarkers;
        }
    }

    private static void validateJsonMap(Map<?, ?> values, String path) {
        for (var entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new SkillException("REST input contains a non-string object key at " + path);
            validateJsonValue(entry.getValue(), path + "." + safe(key));
        }
    }

    private static void validateJsonValue(Object value, String path) {
        if (value == null || isScalar(value)) return;
        if (value instanceof Map<?, ?> map) {
            validateJsonMap(map, path);
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) validateJsonValue(list.get(index), path + "[" + index + "]");
            return;
        }
        throw new SkillException("REST input contains a non-JSON value at " + path);
    }

    private static void preserveInterrupt(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof InterruptedException || cause instanceof java.io.InterruptedIOException) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static String safe(String value) {
        return value.length() <= 80 ? value : value.substring(0, 80) + "...";
    }
}
