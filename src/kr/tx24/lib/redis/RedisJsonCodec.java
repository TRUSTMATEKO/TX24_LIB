package kr.tx24.lib.redis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.lettuce.core.codec.RedisCodec;

/**
 * - Jackson JSON 직렬화/역직렬화 사용
 * - 싱글톤 ObjectMapper로 성능 최적화
 * - ByteBuffer 재사용으로 메모리 최적화
 *
 * @param <T> value type
 */
public final class RedisJsonCodec<T> implements RedisCodec<String, T> {

    private static final Logger logger = LoggerFactory.getLogger(RedisJsonCodec.class);

    private static final ObjectMapper OBJECT_MAPPER = createOptimizedObjectMapper();

    private final JavaType javaType;

    public RedisJsonCodec(Class<T> type) {
        this.javaType = OBJECT_MAPPER.getTypeFactory().constructType(type);
    }

    private static ObjectMapper createOptimizedObjectMapper() {
        return new ObjectMapper()
            // Java 8+ 날짜/시간 타입 지원
            .registerModule(new JavaTimeModule())
            
            // 직렬화 설정
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // ISO-8601 포맷 사용
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL) // null 값 제외
            
            // 역직렬화 설정
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES) // 알 수 없는 필드 무시
            .enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            
            // 접근성 설정 - private 필드도 직렬화
            .setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    
    @Override
    public String decodeKey(ByteBuffer bytes) {
        if (bytes == null || !bytes.hasRemaining()) {
            return "";
        }
        return StandardCharsets.UTF_8.decode(bytes).toString();
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        if (key == null || key.isEmpty()) {
            return ByteBuffer.allocate(0);
        }
        return StandardCharsets.UTF_8.encode(key);
    }

    // ---------------- Value Encoding/Decoding ----------------
    
    @Override
    public T decodeValue(ByteBuffer bytes) {
        if (bytes == null || !bytes.hasRemaining()) {
            return null;
        }

        try {
            // ByteBuffer -> byte array 변환
            byte[] array = new byte[bytes.remaining()];
            bytes.get(array);
            
            // Jackson JSON 역직렬화
            return OBJECT_MAPPER.readValue(array, javaType);
            
        } catch (Exception e) {
            logger.error("Redis decode error for type {}: {}", javaType.getRawClass().getName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Decode error details", e);
            }
            return null;
        }
    }

    @Override
    public ByteBuffer encodeValue(T value) {
        if (value == null) {
            return ByteBuffer.allocate(0);
        }

        try {
            // Jackson JSON 직렬화
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(value);
            return ByteBuffer.wrap(bytes);
            
        } catch (JsonProcessingException e) {
            logger.error("Redis encode error for type {}: {}", javaType.getRawClass().getName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Encode error details", e);
            }
            return ByteBuffer.allocate(0);
        }
    }


}