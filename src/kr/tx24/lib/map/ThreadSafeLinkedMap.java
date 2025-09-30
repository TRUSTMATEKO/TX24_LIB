package kr.tx24.lib.map;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * Thread-safe, insertion-order preserving Map.
 * 순서를 유지하면서 멀티스레드 환경에서 안전하게 사용 가능.
 */
public class ThreadSafeLinkedMap<K, V>{

    private final Map<K, V> map;

    /** 기본 생성자 */
    public ThreadSafeLinkedMap() {
        this.map = Collections.synchronizedMap(new LinkedHashMap<>());
    }

    /** 기존 Map으로 초기화 */
    public ThreadSafeLinkedMap(Map<? extends K, ? extends V> map) {
        this.map = Collections.synchronizedMap(new LinkedHashMap<>());
        if (map != null) {
            this.map.putAll(map);
        }
    }
    
    /** LinkedMap 복사 생성자 */
    public ThreadSafeLinkedMap(ThreadSafeLinkedMap<K, V> other) {
        this.map = Collections.synchronizedMap(new LinkedHashMap<>());
        if (other != null) {
            synchronized (other.map) {   // thread-safe 복사
                this.map.putAll(other.map);
            }
        }
    }
    
    /** LinkedMap 복사 생성자 */
    public ThreadSafeLinkedMap(SharedMap<K, V> other) {
        this.map = Collections.synchronizedMap(new LinkedHashMap<>());
        if (other != null) {
            synchronized (other) {   // thread-safe 복사
                this.map.putAll(other);
            }
        }
    }

    /** JSON 문자열로 초기화 */
    public ThreadSafeLinkedMap(String json) {
        this.map = Collections.synchronizedMap(new LinkedHashMap<>());
        if (!CommonUtils.isNullOrSpace(json)) {
            Map<K, V> loaded = new JacksonUtils().fromJson(json, new TypeReference<LinkedHashMap<K, V>>() {});
            if (loaded != null) {
                this.map.putAll(loaded);
            }
        }
    }

    /** Map 조작 메서드 */
    public void put(K key, V value) {
        map.put(key, value);
    }

    public V get(K key) {
        return map.get(key);
    }

    public V remove(K key) {
        return map.remove(key);
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public void clear() {
        map.clear();
    }
    
    
    /** Map 기반 추가 메서드 */
    public Set<K> keySet() {
        synchronized (map) {
            return new LinkedHashSet<>(map.keySet());
        }
    }
    
    public Map<K, V> getMap() {
        return map; // 내부 Map 필드를 반환
    }

    public Collection<V> values() {
        synchronized (map) {
            return new ArrayList<>(map.values());
        }
    }

    public Set<Entry<K, V>> entrySet() {
        synchronized (map) {
            return new LinkedHashSet<>(map.entrySet());
        }
    }

    public void putAll(Map<? extends K, ? extends V> m) {
        synchronized (map) {
            map.putAll(m);
        }
    }

    /** Thread-safe 순회용 메서드 */
    public void forEachEntry(EntryConsumer<K, V> consumer) {
        synchronized (map) {
            for (Entry<K, V> entry : map.entrySet()) {
                consumer.accept(entry.getKey(), entry.getValue());
            }
        }
    }

    @FunctionalInterface
    public interface EntryConsumer<K, V> {
        void accept(K key, V value);
    }

    /** Convenience Methods */
    public String getString(K key, String... defaultValue) {
        V val = map.get(key);
        if (val == null) return defaultValue.length > 0 ? defaultValue[0] : "";
        String str = CommonUtils.toString(val);
        if (str.trim().isEmpty() && defaultValue.length > 0) return defaultValue[0];
        return str;
    }

    public int getInt(K key, int... defaultValue) {
        V val = map.get(key);
        if (val == null) return defaultValue.length > 0 ? defaultValue[0] : 0;
        return CommonUtils.parseInt(val);
    }

    public long getLong(K key, long... defaultValue) {
        V val = map.get(key);
        if (val == null) return defaultValue.length > 0 ? defaultValue[0] : 0L;
        return CommonUtils.parseLong(val);
    }

    public double getDouble(K key, double... defaultValue) {
        V val = map.get(key);
        if (val == null) return defaultValue.length > 0 ? defaultValue[0] : 0.0;
        return CommonUtils.parseDouble(val);
    }

    public boolean getBoolean(K key) {
        V val = map.get(key);
        if (val == null) return false;
        String str = CommonUtils.toString(val).toLowerCase();
        return str.equals("true") || str.equals("1");
    }

    public BigDecimal getBigDecimal(K key) {
        V val = map.get(key);
        if (val instanceof BigDecimal) return (BigDecimal) val;
        if (val instanceof String) {
            try {
                return new BigDecimal((String) val);
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
        return BigDecimal.ZERO;
    }

    public Timestamp getTimestamp(K key) {
        V val = map.get(key);
        if (val instanceof Timestamp) return (Timestamp) val;
        if (val instanceof String) return DateUtils.toTimestampNotDefine((String) val);
        return null;
    }

    /** 상태 체크 */
    public boolean isNull(K key) {
        return map.get(key) == null;
    }

    public boolean isNullOrSpace(K key) {
        return CommonUtils.isNullOrSpace(getString(key));
    }

    public boolean isEquals(K key, Object value) {
        if (!map.containsKey(key)) return false;
        Object val = map.get(key);
        return CommonUtils.equals(val, value);
    }

    public boolean equalsIgnoreCase(K key, Object value) {
        if (!map.containsKey(key)) return false;
        Object val = map.get(key);
        return CommonUtils.equalsIgnoreCase(val, value);
    }

    /** JSON 변환 */
    public String toJson() {
        return new JacksonUtils().toJson(map);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        synchronized (map) {
            for (Entry<K, V> entry : map.entrySet()) {
                sb.append(CommonUtils.toString(entry.getKey()))
                  .append("=")
                  .append(CommonUtils.toString(entry.getValue()))
                  .append(", ");
            }
        }
        return sb.length() > 0 ? sb.substring(0, sb.length() - 2) : "";
    }

}
