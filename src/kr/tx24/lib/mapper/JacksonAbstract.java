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

    // ---------------- Serialization / Deserialization ----------------

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
    
    
    public JsonNode toJsonNode(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception e) {
            // logger는 구현 클래스에서 정의
            return null;
        }
    }
    
    
    
    
    
   
    
}
