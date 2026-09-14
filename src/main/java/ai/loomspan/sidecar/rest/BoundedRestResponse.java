package ai.loomspan.sidecar.rest;

import ai.loomspan.api.SkillException;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class BoundedRestResponse {
    private BoundedRestResponse() {}

    static String read(String target, long cap,
                       RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response)
            throws IOException {
        int status = response.getStatusCode().value();
        String mediaText = response.getHeaders().getFirst("Content-Type");
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new SkillException(message(target, "HTTP status " + status, mediaText));
        }
        try (var body = response.getBody()) {
            int first = body.read();
            if (first == -1) return "";
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) Math.min(cap, 8192));
            bytes.write(first);
            byte[] buffer = new byte[8192];
            long count = 1;
            while (true) {
                int read = body.read(buffer, 0, (int) Math.min(buffer.length, cap - count + 1));
                if (read == -1) break;
                count += read;
                if (count > cap) throw new SkillException(message(target, "response exceeds configured byte limit", mediaText));
                bytes.write(buffer, 0, read);
            }
            MediaType media;
            try {
                media = mediaText == null ? null : MediaType.parseMediaType(mediaText);
            } catch (RuntimeException invalid) {
                throw new SkillException(message(target, "invalid response Content-Type", mediaText));
            }
            if (media == null || !(media.getType().equalsIgnoreCase("text")
                    || media.getType().equalsIgnoreCase("application")
                    && media.getSubtype().equalsIgnoreCase("json")
                    || media.getSubtype().toLowerCase(java.util.Locale.ROOT).endsWith("+json"))) {
                throw new SkillException(message(target, "unsupported response Content-Type", mediaText));
            }
            java.nio.charset.Charset charset;
            try {
                charset = media.getCharset() == null ? StandardCharsets.UTF_8 : media.getCharset();
            } catch (RuntimeException invalid) {
                throw new SkillException(message(target, "invalid response charset", mediaText));
            }
            try {
                return charset.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes.toByteArray())).toString();
            } catch (CharacterCodingException invalid) {
                throw new SkillException(message(target, "response body is invalid for its charset", mediaText));
            }
        }
    }

    static String message(String target, String reason, String mediaType) {
        String safeTarget = abbreviate(target, 80);
        String safeMedia = mediaType == null ? "" : "; content-type=" + abbreviate(mediaType, 120);
        return "REST target '" + safeTarget + "' failed: " + reason + safeMedia;
    }

    private static String abbreviate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum) + "...";
    }
}
