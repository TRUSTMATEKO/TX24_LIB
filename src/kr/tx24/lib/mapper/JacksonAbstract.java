package kr.tx24.lib.mapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.tx24.lib.map.TypeRegistry;

/**
 * Abstract Jackson Utility with Factory pattern
 * JSON, XML 공용
 */
public abstract class JacksonAbstract<T extends ObjectMapper> {
    protected final T mapper;

    protected JacksonAbstract(T mapper) {
        this.mapper = mapper;
    }

    public abstract JacksonAbstract<T> pretty();
    public abstract JacksonAbstract<T> compact();
    protected abstract JacksonAbstract<T> createNewInstance(T mapper);

    
    public ObjectMapper getMapper() {
        return mapper.copy();
    }
    
    // ---------------- Factory 메서드 추가 ----------------

    @SuppressWarnings("unchecked")
    public JacksonAbstract<T> nullable(boolean nullable) {
        ObjectMapper tmp = mapper.copy();
        JsonInclude.Include inclusion = nullable
                ? JsonInclude.Include.USE_DEFAULTS
                : JsonInclude.Include.NON_NULL;
        tmp.setDefaultPropertyInclusion(JsonInclude.Value.construct(inclusion, inclusion));
        return createNewInstance((T) tmp);
    }

    @SuppressWarnings("unchecked")
    public JacksonAbstract<T> dateformat(DateFormat df) {
        ObjectMapper tmp = mapper.copy();
        tmp.setDateFormat(df);
        return createNewInstance((T) tmp);
    }


    public String serialize(Object value) {
        if (value == null) return "";
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
    
    
    public byte[] serializeBytes(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }

    public <V> V deserialize(String str, Class<V> type) {
        try {
            return mapper.readValue(str, type);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    public <V> V deserialize(String str, TypeReference<V> typeRef) {
        try {
            return mapper.readValue(str, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
    
    public <V> V deserialize(String str, TypeRegistry typeRegistry) {
        try {
            return mapper.readValue(str, typeRegistry.get());
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
    
    
    public <V> V deserialize(byte[] str, Class<V> type) {
        try {
            return mapper.readValue(str, type);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    public <V> V deserialize(byte[] str, TypeReference<V> typeRef) {
        try {
            return mapper.readValue(str, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
    
    
    public <V> V deserialize(byte[] str, TypeRegistry typeRegistry) {
        try {
            return mapper.readValue(str, typeRegistry.get());
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
    
    
    public <V> V deserialize(Path path, Class<V> type) {
        try {
        	return mapper.readValue(Files.readAllBytes(path),type);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    public <V> V deserialize(Path path, TypeReference<V> typeRef) {
        try {
        	return mapper.readValue(Files.readAllBytes(path),typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
    
    public <V> V deserialize(Path path, TypeRegistry typeRegistry) {
        try {
            return mapper.readValue(Files.readAllBytes(path), typeRegistry.get());
        } catch (Exception e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }
    
    
    

    public <V> V copyObject(V source, Class<V> type) {
        if (source == null) return null;
        try {
            byte[] bytes = mapper.writeValueAsBytes(source);
            return mapper.readValue(bytes, type);
        } catch (Exception e) {
            throw new RuntimeException("Object copy failed", e);
        }
    }

    public <V> V copyObject(V source, TypeReference<V> typeRef) {
        if (source == null) return null;
        try {
            byte[] bytes = mapper.writeValueAsBytes(source);
            return mapper.readValue(bytes, typeRef);
        } catch (Exception e) {
            throw new RuntimeException("Object copy failed", e);
        }
    }
    
    
    
    public <V> V mapToObject(Map<?, ?> map, Class<V> type) {
        try {
            ObjectMapper tmpMapper = mapper.copy();
            tmpMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
            tmpMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            tmpMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
            return tmpMapper.convertValue(map, type);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Map을 TypeRegistry 타입으로 변환
     * 
     * @param <V> 반환 타입
     * @param map 변환할 Map
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체 또는 null
     */
    public <V> V convertValue(Map<?, ?> map, TypeRegistry typeRegistry) {
        try {
        	return mapper.convertValue(map, typeRegistry.get());
        } catch (Exception e) {
            return null;
        }
    }
    
    
    
    
    
    
    /**
     * 문자열 콘텐츠의 유효성 검사
     * 
     * <p>파싱 가능한 구조인지 확인합니다. (JSON, XML, YAML 등)</p>
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // JSON
     * boolean valid = json.isValid("{\"name\":\"John\"}");  // true
     * 
     * // XML
     * boolean valid = xml.isValid("{@code <root><name>John</name></root>}");  // true
     * 
     * // YAML
     * boolean valid = yaml.isValid("name: John");  // true
     * </pre>
     * 
     * @param content 검사할 문자열 (JSON, XML, YAML 등)
     * @return 유효하면 true, 아니면 false
     */
    protected boolean isValid(String content) {
        if (content == null || content.isEmpty()) return false;
        try {
            mapper.readTree(content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 문자열 콘텐츠를 특정 타입으로 변환 가능한지 검사
     * 
     * <p>주어진 문자열이 지정된 클래스 타입으로 역직렬화 가능한지 검증합니다.</p>
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // JSON
     * String json = "{\"name\":\"John\",\"age\":30}";
     * boolean valid = json.isValid(json, User.class);  // true
     * 
     * // XML
     * String xml = "{@code <User><name>John</name><age>30</age></User>}";
     * boolean valid = xml.isValid(xml, User.class);  // true
     * 
     * // YAML
     * String yaml = "name: John\nage: 30";
     * boolean valid = yaml.isValid(yaml, User.class);  // true
     * </pre>
     * 
     * @param content 검사할 문자열
     * @param valueType 변환 대상 클래스
     * @return 변환 가능하면 true, 아니면 false
     */
    protected boolean isValid(String content, Class<?> valueType) {
        if (content == null || content.isEmpty()) return false;
        try {
            mapper.readValue(content, valueType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 문자열 콘텐츠를 TypeReference 타입으로 변환 가능한지 검사
     * 
     * <p>제네릭 타입(List, Map 등)으로 역직렬화 가능한지 검증합니다.</p>
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // JSON - List 타입 검증
     * String json = "[\"Apple\",\"Banana\",\"Cherry\"]";
     * boolean valid = json.isValid(json, new TypeReference<List<String>>(){});  // true
     * 
     * // XML - List 타입 검증
     * String xml = "{@code <list><item>Apple</item><item>Banana</item></list>}";
     * boolean valid = xml.isValid(xml, new TypeReference<List<String>>(){});  // true
     * 
     * // YAML - Map 타입 검증
     * String yaml = "name: John\nage: 30";
     * boolean valid = yaml.isValid(yaml, new TypeReference<Map<String, Object>>(){});  // true
     * </pre>
     * 
     * @param content 검사할 문자열
     * @param typeRef TypeReference (제네릭 타입 지원)
     * @return 변환 가능하면 true, 아니면 false
     */
    protected boolean isValid(String content, TypeReference<?> typeRef) {
        if (content == null || content.isEmpty()) return false;
        try {
            mapper.readValue(content, typeRef);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    
    
    
    
   
    
}
