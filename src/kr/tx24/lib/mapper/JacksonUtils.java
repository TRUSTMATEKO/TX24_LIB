package kr.tx24.lib.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;

public class JacksonUtils extends JacksonAbstract<ObjectMapper> {

    public JacksonUtils() {
        super(createDefaultMapper());
    }

    private JacksonUtils(ObjectMapper mapper) {
        super(mapper);
    }

    @Override
    public JacksonUtils pretty() {
        return this;
    }

    @Override
    public JacksonUtils compact() {
        return new JacksonUtils(mapper.copy().disable(SerializationFeature.INDENT_OUTPUT));
    }

    @Override
    protected JacksonUtils createNewInstance(ObjectMapper mapper) {
        return new JacksonUtils(mapper);
    }
    
    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable(SerializationFeature.INDENT_OUTPUT); // 기본 pretty format
        return mapper;
    }
    
    
    public boolean isValid(String json) {
        if (json == null || json.isEmpty()) return false;
        try {
            mapper.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
        
    }
    
    
    public boolean isValid(String json, Class<?> valueType) {
        if (json == null || json.isEmpty()) return false;
        try {
            mapper.readValue(json, valueType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    public String toJson(Object value) {
        return serialize(value);
    }
    


    
    public <V> V fromJson(String json, Class<V> type) {
    	return deserialize(json, type);
    }
    
    public <V> V fromJson(String json,  TypeReference<V> typeRef) {
    	return deserialize(json, typeRef);
    }
    
    
    public <V> V fromJson(byte[] json, Class<V> type) {
    	return deserialize(json, type);
    }
    
    public <V> V fromJson(byte[] json,  TypeReference<V> typeRef) {
    	return deserialize(json, typeRef);
    }
    
    
 // ---------------- Map 변환 ----------------
    public <V> Map<String, V> fromJsonMap(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<Map<String, V>>() {});
    }

    public Map<String, String> fromJsonMap(String json) {
        return deserialize(json, new TypeReference<Map<String, String>>() {});
    }

    public Map<String, Object> fromJsonMapObject(String json) {
        return deserialize(json, new TypeReference<Map<String, Object>>() {});
    }

    public <V> List<Map<String, V>> fromJsonMapList(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<List<Map<String, V>>>() {});
    }

    public List<Map<String, String>> fromJsonMapList(String json) {
        return deserialize(json, new TypeReference<List<Map<String, String>>>() {});
    }

    public List<Map<String, Object>> fromJsonMapListObject(String json) {
        return deserialize(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    // ---------------- SharedMap 변환 ----------------
    public <V> SharedMap<String, V> fromJsonSharedMap(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<SharedMap<String, V>>() {});
    }

    public SharedMap<String, String> fromJsonSharedMap(String json) {
        return deserialize(json, new TypeReference<SharedMap<String, String>>() {});
    }

    public SharedMap<String, Object> fromJsonSharedMapObject(String json) {
        return deserialize(json, new TypeReference<SharedMap<String, Object>>() {});
    }

    public <V> List<SharedMap<String, V>> fromJsonSharedMapList(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<List<SharedMap<String, V>>>() {});
    }

    public List<SharedMap<String, String>> fromJsonSharedMapList(String json) {
        return deserialize(json, new TypeReference<List<SharedMap<String, String>>>() {});
    }

    public List<SharedMap<String, Object>> fromJsonSharedMapListObject(String json) {
        return deserialize(json, new TypeReference<List<SharedMap<String, Object>>>() {});
    }

    // ---------------- LinkedMap 변환 ----------------
    public <V> LinkedMap<String, V> fromJsonLinkedMap(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<LinkedMap<String, V>>() {});
    }

    public LinkedMap<String, String> fromJsonLinkedMap(String json) {
        return deserialize(json, new TypeReference<LinkedMap<String, String>>() {});
    }

    public LinkedMap<String, Object> fromJsonLinkedMapObject(String json) {
        return deserialize(json, new TypeReference<LinkedMap<String, Object>>() {});
    }

    public <V> List<LinkedMap<String, V>> fromJsonLinkedMapList(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<List<LinkedMap<String, V>>>() {});
    }

    public List<LinkedMap<String, String>> fromJsonLinkedMapList(String json) {
        return deserialize(json, new TypeReference<List<LinkedMap<String, String>>>() {});
    }

    public List<LinkedMap<String, Object>> fromJsonLinkedMapListObject(String json) {
        return deserialize(json, new TypeReference<List<LinkedMap<String, Object>>>() {});
    }

    // ---------------- LinkedHashMap 변환 ----------------
    public <V> LinkedHashMap<String, V> fromJsonLinkedHashMap(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<LinkedHashMap<String, V>>() {});
    }

    public LinkedHashMap<String, String> fromJsonLinkedHashMap(String json) {
        return deserialize(json, new TypeReference<LinkedHashMap<String, String>>() {});
    }

    public LinkedHashMap<String, Object> fromJsonLinkedHashMapObject(String json) {
        return deserialize(json, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    public <V> List<LinkedHashMap<String, V>> fromJsonLinkedHashMapList(String json, Class<V> valueClass) {
        return deserialize(json, new TypeReference<List<LinkedHashMap<String, V>>>() {});
    }

    public List<LinkedHashMap<String, String>> fromJsonLinkedHashMapList(String json) {
        return deserialize(json, new TypeReference<List<LinkedHashMap<String, String>>>() {});
    }

    public List<LinkedHashMap<String, Object>> fromJsonLinkedHashMapListObject(String json) {
        return deserialize(json, new TypeReference<List<LinkedHashMap<String, Object>>>() {});
    }
    
   

}
