package ai.loomspan.sidecar.http;

import ai.loomspan.api.SkillException;
import ai.loomspan.api.RecoverableSkillException;
import org.springframework.http.MediaType;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.Set;

final class HttpExecutorResponse {
    static String read(int status,String contentType,InputStream input,int limit,Set<Integer> recoverable) throws IOException {
        var bytes=new ByteArrayOutputStream(Math.min(limit,8192));
        byte[] buffer=new byte[8192]; int count;
        while((count=input.read(buffer,0,(int)Math.min(buffer.length,(long)limit+1-bytes.size())))!=-1) {
            bytes.write(buffer,0,count);
            if(bytes.size()>limit)throw new SkillException("HTTP response exceeds size limit");
        }
        String body=decode(bytes.toByteArray(),contentType);
        if(status>=200&&status<=299)return body;
        String message="HTTP "+status+(body.isEmpty()?"":"\n"+body);
        if(recoverable.contains(status))throw new RecoverableSkillException(message);
        throw new SkillException(message);
    }
    static String decode(byte[] bytes,String contentType) {
        if(bytes.length==0)return "";
        try {
            Charset charset=StandardCharsets.UTF_8;
            if(contentType!=null) {
                MediaType type=MediaType.parseMediaType(contentType);
                if(type.isWildcardType()||type.isWildcardSubtype())throw new IllegalArgumentException();
                if(type.getType().equals("application")&&(type.getSubtype().equals("json")||type.getSubtype().endsWith("+json"))) {
                    if(!HttpExecutorConfiguration.jsonType(type))throw new IllegalArgumentException();
                } else if(type.getType().equals("text")) {if(type.getCharset()!=null)charset=type.getCharset();}
                else throw new IllegalArgumentException();
            }
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch(RuntimeException|CharacterCodingException failure) {throw new SkillException("HTTP response cannot be decoded");}
    }
}
