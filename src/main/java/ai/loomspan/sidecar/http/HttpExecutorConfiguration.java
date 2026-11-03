package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillException;
import ai.loomspan.sidecar.storage.EncryptedCredential;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import java.net.URI;
import java.time.Duration;
import java.util.*;

/** Network-free HTTP-specific validation, shared by invocation and configuration consumers. */
public final class HttpExecutorConfiguration {
    private HttpExecutorConfiguration() {}
    public record Settings(Map<String,Target> targets, Map<String,EncryptedCredential> credentials) {
        public Settings { targets=Map.copyOf(targets); credentials=Map.copyOf(credentials); }
        @Override public String toString() { return "HTTP settings [redacted]"; }
    }
    public record Target(URI baseUrl, Map<String,String> headers, String authMode, Map<String,String> headerRefs,
                         int connectMillis, int readMillis, int maxResponseSize) {
        @Override public String toString() { return "HTTP target [redacted]"; }
    }
    public record Operation(Target target, String method, String path, Map<String,String> pathParams,
                            Map<String,String> queryParams, Body body, Set<Integer> recoverableStatuses) {}
    public record Body(String source, String field) {}

    public static Settings validate(Map<String,Object> source) {
        try { return parse(source); } catch (SkillException failure) { throw failure; }
        catch (RuntimeException failure) { throw invalid("settings"); }
    }
    private static Settings parse(Map<String,Object> source) {
        keys(source,"targets","credentials");
        var credentials=new LinkedHashMap<String,EncryptedCredential>();
        for(var entry: optionalMap(source,"credentials").entrySet()) {
            var value=map(entry.getValue()); keys(value,"version","ciphertext");
            credentials.put(entry.getKey(),new EncryptedCredential(entry.getKey(),string(value.get("version")),string(value.get("ciphertext"))));
        }
        var targets=new LinkedHashMap<String,Target>();
        for(var entry: map(source.get("targets")).entrySet()) {
            string(entry.getKey()); var value=map(entry.getValue());
            keys(value,"base-url","headers","auth","connect-timeout","read-timeout","max-response-size");
            URI base=URI.create(string(value.get("base-url")));
            if (!("http".equals(base.getScheme())||"https".equals(base.getScheme())) || base.getHost()==null
                    || base.getRawUserInfo()!=null || base.getRawQuery()!=null || base.getRawFragment()!=null
                    || base.getPort() < -1 || base.getPort()>65535) throw invalid("base URL");
            safePath(base.getRawPath(),false);
            var headers=stringMap(optionalMap(value,"headers")); validateHeaders(headers,false);
            var auth=value.containsKey("auth")?map(value.get("auth")):Map.<String,Object>of("mode","none");
            keys(auth,"mode","header-refs"); String mode=string(auth.get("mode"));
            if(!Set.of("none","fixed","caller-passthrough").contains(mode)) throw invalid("authentication mode");
            var refs=stringMap(optionalMap(auth,"header-refs")); validateHeaders(refs,true);
            if(mode.equals("fixed") ? refs.isEmpty() : auth.containsKey("header-refs")) throw invalid("authentication headers");
            for(var ref:refs.values()) if(!credentials.containsKey(ref)) throw invalid("credential reference");
            var names=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER); names.addAll(headers.keySet());
            for(var name:refs.keySet()) if(!names.add(name)) throw invalid("header conflict");
            targets.put(entry.getKey(),new Target(base,headers,mode,refs,
                    timeout(value.getOrDefault("connect-timeout","5s")),timeout(value.getOrDefault("read-timeout","60s")),
                    size(value.getOrDefault("max-response-size","1MB"))));
        }
        return new Settings(targets,credentials);
    }
    public static Operation validateOperation(Map<String,Object> source, Settings settings) {
        try {
            keys(source,"target","method","path","pathParams","queryParams","body","recoverableStatuses");
            Target target=settings.targets().get(string(source.get("target"))); if(target==null) throw invalid("target selection");
            String method=string(source.get("method")); if(!Set.of("GET","POST","PUT","PATCH","DELETE").contains(method)) throw invalid("method");
            String path=string(source.get("path")); if(!path.startsWith("/")||path.startsWith("//")||path.contains("?")||path.contains("#")) throw invalid("operation path");
            var pathParams=stringMap(optionalMap(source,"pathParams")); var queryParams=stringMap(optionalMap(source,"queryParams"));
            pathParams.values().forEach(HttpExecutorConfiguration::pointer); queryParams.values().forEach(HttpExecutorConfiguration::pointer);
            var placeholders=new HashSet<String>();
            for(String segment:path.substring(1).split("/",-1)) {
                if(segment.matches("\\{[A-Za-z_][A-Za-z0-9_]*}")) placeholders.add(segment.substring(1,segment.length()-1));
                else { if(segment.contains("{")||segment.contains("}")) throw invalid("placeholder"); authoredSegment(segment); }
            }
            if(!placeholders.equals(pathParams.keySet())) throw invalid("path mappings");
            Body body=null;
            if(source.containsKey("body")) {
                if(method.equals("GET")) throw invalid("GET body");
                var b=map(source.get("body")); keys(b,"source","field"); String selection=string(b.get("source"));
                if(selection.equals("input")) { if(b.containsKey("field")) throw invalid("body selector"); body=new Body(selection,null); }
                else if(selection.equals("field")) { String field=string(b.get("field")); pointer(field); body=new Body(selection,field); }
                else throw invalid("body selector");
                String type=header(target.headers(),"Content-Type"); if(type!=null && !jsonType(MediaType.parseMediaType(type))) throw invalid("JSON request content type");
            }
            var statuses=new HashSet<Integer>();
            if(source.containsKey("recoverableStatuses")) {
                if(!(source.get("recoverableStatuses") instanceof List<?> list)) throw invalid("recoverable statuses");
                for(Object value:list) {
                    if(!(value instanceof Byte||value instanceof Short||value instanceof Integer||value instanceof Long)) throw invalid("recoverable status");
                    long status=((Number)value).longValue();
                    if(status<400||status>499||status==401||status==403||!statuses.add((int)status)) throw invalid("recoverable status");
                }
            }
            return new Operation(target,method,path,pathParams,queryParams,body,Set.copyOf(statuses));
        } catch(SkillException failure) {throw failure;} catch(RuntimeException failure) {throw invalid("operation");}
    }
    /** Attach retained encrypted records to a selected executor subtree before Framework preparation. */
    public static Map<String,Object> withCredentials(Map<String,Object> source, Collection<EncryptedCredential> records) {
        var copy=new LinkedHashMap<>(source); var credentials=new LinkedHashMap<String,Object>();
        for(var record:records) if(credentials.put(record.identifier(),Map.of("version",record.version(),"ciphertext",record.ciphertext()))!=null) throw invalid("duplicate credential");
        if(source.containsKey("credentials")) throw invalid("duplicate credential source");
        copy.put("credentials",Collections.unmodifiableMap(credentials)); return Collections.unmodifiableMap(copy);
    }
    static void pointer(String value) {
        if(!value.startsWith("/")) throw invalid("JSON Pointer");
        for(int i=0;i<value.length();i++) if(value.charAt(i)=='~'&&(i+1>=value.length()||"01".indexOf(value.charAt(++i))<0)) throw invalid("JSON Pointer");
        // Jackson supplies traversal; this check rejects its otherwise permissive invalid escapes.
        tools.jackson.core.JsonPointer.compile(value);
    }
    static Map<String,Object> map(Object value) {
        if(!(value instanceof Map<?,?> values)) throw invalid("object");
        var result=new LinkedHashMap<String,Object>();
        for(var entry:values.entrySet()) { if(!(entry.getKey() instanceof String key)) throw invalid("object key"); result.put(key,entry.getValue()); }
        return result;
    }
    static Map<String,Object> optionalMap(Map<String,Object> source,String key) { return source.containsKey(key)?map(source.get(key)):Map.of(); }
    static Map<String,String> stringMap(Map<String,Object> source) { var result=new LinkedHashMap<String,String>(); source.forEach((k,v)->result.put(k,string(v))); return Collections.unmodifiableMap(result); }
    static String string(Object value) { if(!(value instanceof String text)||text.isBlank()) throw invalid("text setting"); return text; }
    static void keys(Map<String,Object> source,String... allowed) { if(!Set.of(allowed).containsAll(source.keySet())) throw invalid("unknown field"); }
    static int timeout(Object value) { try { Duration d=DurationStyle.detectAndParse(string(value)); long ms=d.toMillis(); if(ms<1||ms>Integer.MAX_VALUE) throw invalid("timeout"); return (int)ms; } catch(RuntimeException failure) {throw invalid("timeout");} }
    static int size(Object value) { try {long n=DataSize.parse(string(value)).toBytes(); if(n<1||n>Integer.MAX_VALUE) throw invalid("response limit"); return (int)n;} catch(RuntimeException failure) {throw invalid("response limit");} }
    static void safePath(String path,boolean selected) { if(path==null)return; for(String segment:path.split("/",-1)) authoredSegment(segment); }
    private static void authoredSegment(String value) {
        for(int i=0;i<value.length();i++) if(value.charAt(i)=='%'&&(i+2>=value.length()||Character.digit(value.charAt(i+1),16)<0||Character.digit(value.charAt(i+2),16)<0)) throw invalid("path escape");
        safeSegment(value);
    }
    static void safeSegment(String value) {
        String decoded=value.toLowerCase(Locale.ROOT);
        // Decode only security markers; all other original punctuation is encoded by the mapper.
        while(true) {
            if(decoded.contains("/")||decoded.contains("\\")||decoded.equals(".")||decoded.equals("..")) throw invalid("unsafe path segment");
            String next=decoded.replace("%25","%").replace("%2e",".").replace("%2f","/").replace("%5c","\\");
            if(next.equals(decoded)) return; decoded=next;
        }
    }
    static void validateHeaders(Map<String,String> headers,boolean auth) {
        var names=new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for(var entry:headers.entrySet()) {
            String name=entry.getKey(); if(!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")||!names.add(name)) throw invalid("header name");
            String lower=name.toLowerCase(Locale.ROOT);
            if(Set.of("host","content-length","transfer-encoding","cookie","proxy-authorization").contains(lower)
                    || (!auth&&lower.equals("authorization")) || (auth&&Set.of("content-type","content-encoding","accept").contains(lower))) throw invalid("header ownership");
            if(!auth) headerValue(entry.getValue());
        }
    }
    static void headerValue(String value) { if(value==null||value.isBlank()||value.chars().anyMatch(c->c==127||c<32&&c!=9)) throw invalid("header value"); }
    static String header(Map<String,String> headers,String name) { return headers.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(null); }
    static boolean jsonType(MediaType type) { return !type.isWildcardType()&&!type.isWildcardSubtype()&&type.getType().equals("application")&&(type.getSubtype().equals("json")||type.getSubtype().endsWith("+json"))&&(type.getCharset()==null||type.getCharset().equals(java.nio.charset.StandardCharsets.UTF_8)); }
    static SkillException invalid(String category) { return new SkillException("Invalid HTTP " + category); }
}
