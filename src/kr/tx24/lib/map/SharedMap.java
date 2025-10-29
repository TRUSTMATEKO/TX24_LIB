package kr.tx24.lib.map;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * Thread-safe Map 확장. 
 * 기존 SharedMap 기능 유지 + LinkedMap 개선 사항 적용
 */
public class SharedMap<K, V> extends ConcurrentHashMap<K, V> {

    private static final long serialVersionUID = -5846208410575929029L;

    public SharedMap() {}

    public SharedMap(Map<? extends K, ? extends V> map) {
        if (map != null) {
            super.putAll(map);
        }
    }
    



    public SharedMap(String json) {
        super.putAll(new JacksonUtils().fromJson(json, new TypeReference<ConcurrentHashMap<K, V>>() {}));
    }

    /* ================== Getter Methods ================== */

    public String getString(String key, String... replace) {
        V val = super.get(key);
        String str = CommonUtils.toString(val);
        if (replace != null && replace.length > 0 && (val == null || str.trim().isEmpty())) {
            return replace[0];
        }
        return str;
    }

    public boolean getBoolean(String key) {
        V val = super.get(key);
        String str = CommonUtils.toString(val).toLowerCase();
        return "true".equals(str) || "1".equals(str);
    }

    public int getInt(String key, int... replace) {
        V val = super.get(key);
        if (val == null && replace.length > 0) return replace[0];
        return CommonUtils.parseInt(val);
    }

    public long getLong(String key, long... replace) {
        V val = super.get(key);
        if (val == null && replace.length > 0) return replace[0];
        return CommonUtils.parseLong(val);
    }

    public double getDouble(String key, double... replace) {
        V val = super.get(key);
        if (val == null && replace.length > 0) return replace[0];
        return CommonUtils.parseDouble(val);
    }

    
    /**
     * TypeRegistry로 Map 타입 반환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * LinkedMap<String, Object> map = new LinkedMap<>();
     * 
     * // SharedMap 가져오기
     * SharedMap<String, Object> sharedMap = map.getMap("data", TypeRegistry.MAP_SHAREDMAP_OBJECT);
     * 
     * // LinkedMap 가져오기
     * LinkedMap<String, Object> linkedMap = map.getMap("data", TypeRegistry.MAP_LINKEDMAP_OBJECT);
     * 
     * // ConcurrentHashMap 가져오기
     * ConcurrentHashMap<String, Object> concurrent = map.getMap("data", TypeRegistry.MAP_CONCURRENTHASHMAP_OBJECT);
     * 
     * // TreeMap 가져오기
     * TreeMap<String, Object> treeMap = map.getMap("data", TypeRegistry.MAP_TREEMAP_OBJECT);
     * </pre>
     * 
     * @param <T> Map 타입
     * @param key 키
     * @param typeRegistry TypeRegistry
     * @return 변환된 Map 또는 null
     */
    @SuppressWarnings("unchecked")
    public <T extends Map<?, ?>> T getMap(String key, TypeRegistry typeRegistry) {
        V val = super.get(key);
        if (val == null) {
            return null;
        }
        
        // 이미 해당 타입인 경우
        try {
            // TypeRegistry에서 기대하는 타입 확인
            switch (typeRegistry) {
                case MAP_SHAREDMAP_OBJECT:
                case MAP_SHAREDMAP_STRING:
                    if (val instanceof SharedMap) {
                        return (T) val;
                    }
                    break;
                    
                case MAP_LINKEDMAP_OBJECT:
                case MAP_LINKEDMAP_STRING:
                    if (val instanceof LinkedMap) {
                        return (T) val;
                    }
                    break;
                    
                case MAP_LINKEDHASHMAP_OBJECT:
                case MAP_LINKEDHASHMAP_STRING:
                    if (val instanceof LinkedHashMap) {
                        return (T) val;
                    }
                    break;
                    
                case MAP_CONCURRENTHASHMAP_OBJECT:
                case MAP_CONCURRENTHASHMAP_STRING:
                    if (val instanceof ConcurrentHashMap) {
                        return (T) val;
                    }
                    break;
                    
                case MAP_THREADSAFE_LINKEDMAP_OBJECT:
                case MAP_THREADSAFE_LINKEDMAP_STRING:
                    if (val instanceof ThreadSafeLinkedMap) {
                        return (T) val;
                    }
                    break;
                    
                case MAP_TREEMAP_OBJECT:
                case MAP_TREEMAP_STRING:
                    if (val instanceof TreeMap) {
                        return (T) val;
                    }
                    break;
                    
                case MAP_OBJECT:
                case MAP_STRING:
                    if (val instanceof Map) {
                        return (T) val;
                    }
                    break;
                    
                default:
                    break;
            }
            
            // 타입이 다른 경우 Jackson으로 변환 시도
            if (val instanceof Map) {
                return new JacksonUtils().getMapper().convertValue(val, typeRegistry.get());
            }
            
        } catch (Exception e) {
            // 변환 실패시 null 반환
        }
        
        return null;
    }

    public BigDecimal getBigDecimal(String key) {
        V val = super.get(key);
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof String) {
            try { return new BigDecimal((String) val); } 
            catch (Exception e) { return BigDecimal.ZERO; }
        }
        return BigDecimal.ZERO;
    }

    public Timestamp getTimestamp(String key) {
        V val = super.get(key);
        if (val instanceof String) return DateUtils.toTimestampNotDefine((String) val);
        if (val instanceof Timestamp) return (Timestamp) val;
        return null;
    }

    /* ================== Utility Methods ================== */

    public boolean like(String key, String value) {
        String val = getString(key);
        return val.contains(value);
    }

    public boolean isTrue(String key) {
        return "true".equalsIgnoreCase(getString(key));
    }

    public boolean isNull(String key) {
        return super.get(key) == null;
    }

    public boolean isNullOrSpace(String key) {
        return CommonUtils.isNullOrSpace(getString(key));
    }

    public boolean startsWith(String key, String value) {
        return getString(key).startsWith(value);
    }

    public boolean isEquals(String key, Object value) {
        return CommonUtils.equals(super.get(key), value);
    }

    public boolean equalsIgnoreCase(String key, Object value) {
        return CommonUtils.equalsIgnoreCase(super.get(key), value);
    }

    /* ================== Output Methods ================== */

    public String toString(String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (Entry<K, V> entry : this.entrySet()) {
            sb.append(CommonUtils.toString(entry.getKey()))
              .append("=")
              .append(CommonUtils.toString(entry.getValue()))
              .append(delimiter);
        }
        return sb.toString();
    }

    public String toJson() {
        return new JacksonUtils().toJson(this);
    }
}
