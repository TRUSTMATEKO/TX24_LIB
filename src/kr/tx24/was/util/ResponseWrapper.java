package kr.tx24.was.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.web.util.ContentCachingResponseWrapper;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse; // <--- Jakarta Import 수정

/**
 * @author juseop
 */
public class ResponseWrapper extends ContentCachingResponseWrapper {
    
    private final ObjectMapper objectMapper;
    private final Charset responseCharset;

    public ResponseWrapper(HttpServletResponse response) {
        super(response);
        this.objectMapper = new ObjectMapper();
        
        // 응답 Content-Type에서 Charset을 추출하거나, 기본값으로 UTF-8 사용
        String encoding = response.getCharacterEncoding();
        this.responseCharset = (encoding != null) ? Charset.forName(encoding) : StandardCharsets.UTF_8;
    }

    public Object convertToObject() throws IOException {
        
        byte[] httpBodyBytes = getContentAsByteArray();
        if (httpBodyBytes.length == 0) return null;
        
        // 1. 응답 Content-Type 확인
        String contentType = getContentType();
        
        // 2. JSON 타입인 경우
        if (contentType != null && contentType.contains("application/json")) {
            try {
                // 바이트 배열을 ObjectMapper로 직접 읽어 파싱합니다.
                return objectMapper.readValue(httpBodyBytes, Object.class);
            } catch (Exception e) {
                // JSON 파싱 실패 시 원본 문자열을 반환하여 디버깅에 도움을 줍니다.
                return new String(httpBodyBytes, this.responseCharset);
            }
        } 
        
        // 3. JSON이 아닌 경우 (HTML, XML, Plain Text 등)
        // 불필요한 Form Data 파싱 로직 제거 후, 디코딩된 문자열을 반환합니다.
        // Form Data 응답은 표준적이지 않으므로 제거하는 것이 안전합니다.
        return new String(httpBodyBytes, this.responseCharset);
    }
}