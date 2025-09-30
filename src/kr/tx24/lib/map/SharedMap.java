package kr.tx24.lib.map;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.Map;
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

    @SuppressWarnings("unchecked")
    public SharedMap<String, Object> getSharedMap(String key) {
        V val = super.get(key);
        if (val instanceof SharedMap) return (SharedMap<String, Object>) val;
        return null;
    }

    @SuppressWarnings("unchecked")
    public LinkedHashMap<String, Object> getLinkedHashMap(String key) {
        V val = super.get(key);
        if (val instanceof LinkedHashMap) return (LinkedHashMap<String, Object>) val;
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
