package kr.tx24.was.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Enumeration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import kr.tx24.lib.map.SharedMap;

/**
 * @author juseop
 * 요청 본문을 캐싱하여 여러 번 읽을 수 있게 하고, 파라미터도 처리하는 Wrapper.
 */
public class RequestWrapper extends HttpServletRequestWrapper {

    private static final Logger logger = LoggerFactory.getLogger(RequestWrapper.class);
    
    private final ObjectMapper objectMapper;
    private final byte[] httpRequestBodyByteArray;
    private final ByteArrayInputStream bis;
    
    public RequestWrapper(HttpServletRequest request) {
        super(request);
        this.objectMapper = new ObjectMapper();
        
        byte[] bodyBytes = new byte[0]; // 기본값: 빈 배열
        
        try {
            // 요청 스트림을 읽기 전에 이미 읽었는지 확인하는 것은 불가능하므로,
            // StreamUtils.copyToByteArray를 사용해 안전하게 복사합니다.
            bodyBytes = StreamUtils.copyToByteArray(request.getInputStream());
        } catch (IOException e) {
            // 💡 보완: 본문 복사 실패 시 로그를 남기고 빈 배열을 사용합니다. 
            // NullPointerException을 방지합니다.
            logger.warn("Failed to copy request input stream. Using empty body. {}", e.getMessage());
        }
        
        this.httpRequestBodyByteArray = bodyBytes;
        // 실패 시에도 httpRequestBodyByteArray가 빈 배열이므로 bis 초기화는 안전합니다.
        this.bis = new ByteArrayInputStream(this.httpRequestBodyByteArray);
    }
    
    @Override
    public ServletInputStream getInputStream() {
        // 캐싱된 바이트 배열을 기반으로 새로운 InputStream을 반환합니다.
        // 매번 getInputStream()이 호출될 때마다 bis는 처음부터 읽을 수 있습니다.
        this.bis.reset(); 
        
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                // bis.available()은 남은 바이트 수를 반환합니다.
                return bis.available() == 0;
            }
            
            @Override
            public boolean isReady() {
                // 동기(Synchronous) 처리이므로 항상 true
                return true;
            }
            
            @Override
            public void setReadListener(ReadListener readListener) {
                // 비동기 처리(Async)를 지원하지 않으므로 구현하지 않음.
                // 이 클래스는 비동기 I/O를 지원하지 않습니다.
            }
            
            @Override
            public int read() {
                return bis.read();
            }
        };
    }
    
    // getReader() 메서드를 오버라이드하여 캐싱된 본문을 Reader로 반환할 수도 있습니다.

    public Object convertToObject() throws IOException {
        // 본문이 있으면 (POST, PUT, JSON)
        if (httpRequestBodyByteArray.length > 0) {
            return objectMapper.readValue(httpRequestBodyByteArray, Object.class);
        }
        
        // 본문이 없으면 (GET, Form Data) 파라미터를 사용합니다.
        SharedMap<String,Object> map = new SharedMap<String,Object>();
        
        Enumeration<String> e = super.getRequest().getParameterNames();
        String key = "";
        
        while (e.hasMoreElements()) {
            key = e.nextElement();
            String[] values = super.getRequest().getParameterValues(key);
            
            if (values != null && values.length > 1) {
                // 값이 여러 개면 배열로 저장
                map.put(key, values);
            } else if (values != null && values.length == 1) {
                // 값이 하나면 단일 String으로 저장
                // CommonUtils.nToB 대신 String 값 그 자체를 사용 (nToB의 의도가 불분명하여 제거)
                map.put(key, values[0]); 
            }
        }
        return map;
    }
}