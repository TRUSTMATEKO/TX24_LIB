package kr.tx24.lib.redis;


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.lettuce.core.codec.RedisCodec;

/**
 * 타입 정보를 자동으로 보존하는 Redis Codec
 * 
 * Jackson의 DefaultTyping을 활용하여 모든 타입 정보를 자동으로 저장/복원
 * 
 * 특징:
 * - 제네릭 타입 완벽 지원 (List<SharedMap>, Map<String, List<Object>> 등)
 * - 빈 컬렉션 자동 처리
 * - 다형성(Polymorphism) 지원
 * - Type Erasure 문제 해결
 * 
 * 직렬화 예시:
 * <pre>
 * List<SharedMap<String, Object>> list = new ArrayList<>();
 * 
 * Redis에 저장:
 * ["java.util.ArrayList", [
 *   ["kr.tx24.lib.map.SharedMap", {"key": "value"}]
 * ]]
 * 
 * 역직렬화 시 정확한 타입으로 복원:
 * List<SharedMap<String, Object>> restored = (List<SharedMap<String, Object>>) Redis.get("key");
 * </pre>
 * 
 * @author TX24
 * @version 1.0
 */
public class TypedRedisCodec implements RedisCodec<String, Object> {

    private static final ObjectMapper MAPPER = createObjectMapper();

    /**
     * ObjectMapper 생성 및 설정
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // JavaTimeModule 등록 (LocalDateTime 등 지원)
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // 알 수 없는 속성 무시 (하위 호환성)
        mapper.configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, 
            false
        );
        
        //DefaultTyping 활성화 (타입 정보 자동 저장)
        PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)  // 모든 Object 기반 타입 허용
            .build();
        
        mapper.activateDefaultTyping(
            validator,
            ObjectMapper.DefaultTyping.NON_FINAL,  // final이 아닌 모든 타입
            JsonTypeInfo.As.WRAPPER_ARRAY         // ["타입명", 데이터] 형식
        );
        
        return mapper;
    }

    /**
     * 키 디코딩 (UTF-8)
     */
    @Override
    public String decodeKey(ByteBuffer bytes) {
        byte[] array = new byte[bytes.remaining()];
        bytes.get(array);
        return new String(array, StandardCharsets.UTF_8);
    }

    /**
     * 값 디코딩 (JSON → Object, 타입 정보 자동 복원)
     */
    @Override
    public Object decodeValue(ByteBuffer bytes) {
        try {
            if (!bytes.hasRemaining()) {
                return null;
            }
            
            byte[] array = new byte[bytes.remaining()];
            bytes.get(array);
            
            // Jackson이 타입 정보를 읽고 자동으로 적절한 타입으로 역직렬화
            return MAPPER.readValue(array, Object.class);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode value", e);
        }
    }

    /**
     * 키 인코딩 (UTF-8)
     */
    @Override
    public ByteBuffer encodeKey(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.wrap(bytes);
    }

    /**
     * 값 인코딩 (Object → JSON, 타입 정보 자동 포함)
     */
    @Override
    public ByteBuffer encodeValue(Object value) {
        try {
            if (value == null) {
                return ByteBuffer.wrap(new byte[0]);
            }
            
            // Jackson이 타입 정보를 자동으로 포함하여 직렬화
            byte[] bytes = MAPPER.writeValueAsBytes(value);
            return ByteBuffer.wrap(bytes);
            
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to encode value: " + value.getClass().getName(), e);
        }
    }

    /**
     * ObjectMapper 반환 (테스트/디버깅용)
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 직렬화 테스트 (디버깅용)
     * 
     * @param value 테스트할 값
     * @return JSON 문자열
     */
    public static String toJson(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 역직렬화 테스트 (디버깅용)
     * 
     * @param json JSON 문자열
     * @return 복원된 객체
     */
    public static Object fromJson(String json) {
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }
}