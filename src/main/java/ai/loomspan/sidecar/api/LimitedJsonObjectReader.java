package ai.loomspan.sidecar.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import ai.loomspan.sidecar.config.SidecarExecutionProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Component
final class LimitedJsonObjectReader {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final ObjectMapper mapper;
    private final long limit;

    LimitedJsonObjectReader(ObjectMapper mapper, SidecarExecutionProperties properties) {
        this.mapper = mapper;
        this.limit = properties.getMaxInputSize().toBytes();
    }

    Map<String, Object> read(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > limit) throw new PayloadTooLargeException();
        try (var input = request.getInputStream(); var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            long total = 0;
            for (int count; (count = input.read(buffer)) >= 0;) {
                total += count;
                if (total > limit) throw new PayloadTooLargeException();
                output.write(buffer, 0, count);
            }
            byte[] body = output.toByteArray();
            if (body.length == 0) throw new ApiInputException("A JSON object body is required");
            try {
                try (var parser = mapper.createParser(body)) {
                    var node = mapper.readTree(parser);
                    if (node == null || !node.isObject()) {
                        throw new ApiInputException("The request body must be a JSON object");
                    }
                    if (parser.nextToken() != null) {
                        throw new ApiInputException("The request body is not valid JSON");
                    }
                    return mapper.convertValue(node, MAP_TYPE);
                }
            } catch (ApiInputException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new ApiInputException("The request body is not valid JSON");
            }
        }
    }

    long measure(Map<String, Object> input) {
        return mapper.writeValueAsBytes(input).length;
    }
}
