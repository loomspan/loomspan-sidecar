package ai.loomspan.sidecar.http;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import ai.loomspan.sidecar.http.HttpExecutorConfiguration.Operation;

/** Reusable, network-free actual-input validation and HTTP request construction. */
public final class HttpRequestMapper {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    public record Request(URI uri,String method,byte[] body) {
        @Override public String toString() {return "HTTP request [redacted]";}
    }
    public static Request map(Operation op,Map<String,Object> input) {
        validateJson(input);
        JsonNode root=JSON.valueToTree(input);
        var path=new StringBuilder();
        String base=op.target().baseUrl().getRawPath();
        if(base!=null&&!base.equals("/")) path.append(base.endsWith("/")?base.substring(0,base.length()-1):base);
        for(String part:op.path().substring(1).split("/",-1)) {
            path.append('/');
            if(part.startsWith("{")) {
                var node=root.at(op.pathParams().get(part.substring(1,part.length()-1)));
                if(!scalar(node)||node.asText().isEmpty()) throw HttpExecutorConfiguration.invalid("path input");
                String text=node.asText(); HttpExecutorConfiguration.safeSegment(text); path.append(encode(text));
            } else path.append(encode(part));
        }
        var query=new ArrayList<String>();
        op.queryParams().forEach((name,pointer)-> {
            JsonNode node=root.at(pointer);
            if(node.isMissingNode()||node.isNull())return;
            if(node.isArray()) for(JsonNode element:node) addQuery(query,name,element);
            else addQuery(query,name,node);
        });
        byte[] body=null;
        if(op.body()!=null) {
            JsonNode node=op.body().source().equals("input")?root:root.at(op.body().field());
            if(node.isMissingNode()) throw HttpExecutorConfiguration.invalid("missing body input");
            body=JSON.writeValueAsBytes(node);
        }
        URI baseUri=op.target().baseUrl();
        return new Request(URI.create(baseUri.getScheme()+"://"+baseUri.getRawAuthority()+path+(query.isEmpty()?"":"?"+String.join("&",query))),op.method(),body);
    }
    private static void addQuery(List<String> query,String name,JsonNode node) {
        if(!scalar(node)) throw HttpExecutorConfiguration.invalid("query input");
        query.add(encode(name)+"="+encode(node.asText()));
    }
    private static boolean scalar(JsonNode node) {return node.isTextual()||node.isNumber()||node.isBoolean();}
    static String encode(String text) {
        var result=new StringBuilder();
        for(byte octet:text.getBytes(StandardCharsets.UTF_8)) {
            int c=octet&255;
            if(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='-'||c=='_'||c=='.'||c=='~') result.append((char)c);
            else result.append('%').append("0123456789ABCDEF".charAt(c>>>4)).append("0123456789ABCDEF".charAt(c&15));
        }
        return result.toString();
    }
    private static void validateJson(Object value) {
        if(value==null||value instanceof String||value instanceof Boolean||value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long||value instanceof java.math.BigInteger||value instanceof java.math.BigDecimal)return;
        if(value instanceof Float f&&Float.isFinite(f)||value instanceof Double d&&Double.isFinite(d))return;
        if(value instanceof Map<?,?> map) {for(var entry:map.entrySet()) {if(!(entry.getKey() instanceof String))throw HttpExecutorConfiguration.invalid("input object key"); validateJson(entry.getValue());}return;}
        if(value instanceof List<?> list) {list.forEach(HttpRequestMapper::validateJson);return;}
        throw HttpExecutorConfiguration.invalid("JSON input");
    }
}
