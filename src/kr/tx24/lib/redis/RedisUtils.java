package kr.tx24.lib.redis;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;



public class RedisUtils {

    private static final Logger logger = LoggerFactory.getLogger(RedisUtils.class);

    // ------------------- 공통 Helper -------------------

    @FunctionalInterface
    private interface RedisAction<K, V, R> {
        R execute(StatefulRedisConnection<K, V> conn);
    }

    private static <K, V, R> R withRedis(StatefulRedisConnection<K, V> conn, RedisAction<K, V, R> action) {
        return action.execute(conn);
    }

    @SuppressWarnings("unchecked")
    private static <K, V> void withRedisAsync(StatefulRedisConnection<K, V> conn, K key, V val) {
		RedisFuture<Long> future = conn.async().rpush(key, val);
        future.thenAccept(v -> logger.debug("rpush key : {}", v));
    }

    // ------------------- Connection 결정 -------------------

    private static StatefulRedisConnection<String, ?> determineConnection(Object val) {
        if (val == null) return Redis.getObject();
        if (val instanceof String) return Redis.get();
        if (val instanceof SharedMap) return Redis.getSharedMap();
        if (val instanceof LinkedMap) return Redis.getLinkedMap();
        if (val instanceof ThreadSafeLinkedMap) return Redis.getThreadSafeLinkedMap();
        if (val instanceof List) {
            List<?> list = (List<?>) val;
            return list.isEmpty() ? Redis.getObject() : determineConnection(list.get(0));
        }
        return Redis.getObject();
    }

    private static StatefulRedisConnection<String, ?> determineConnection(Class<?> clazz) {
        if (clazz == null) return Redis.getObject();
        if (String.class.isAssignableFrom(clazz)) return Redis.get();
        if (SharedMap.class.isAssignableFrom(clazz)) return Redis.getSharedMap();
        if (LinkedMap.class.isAssignableFrom(clazz)) return Redis.getLinkedMap();
        if (ThreadSafeLinkedMap.class.isAssignableFrom(clazz)) return Redis.getThreadSafeLinkedMap();
        return Redis.getObject();
    }

    // ------------------- 기본 Key/Value -------------------

    public static String ping() {
        return withRedis(Redis.get(), conn -> conn.sync().ping());
    }

    public static long ttl(String key) {
        return withRedis(Redis.get(), conn -> conn.sync().ttl(key));
    }

    public static long pttl(String key) {
        return withRedis(Redis.get(), conn -> conn.sync().pttl(key));
    }

    public static long del(String... keys) {
        return withRedis(Redis.get(), conn -> conn.sync().del(keys));
    }

    public static long exist(String... keys) {
        return withRedis(Redis.get(), conn -> conn.sync().exists(keys));
    }

    public static boolean exist(String key, String field) {
        return withRedis(Redis.get(), conn -> conn.sync().hexists(key, field));
    }

    public static boolean exists(String key, long field) {
        return exist(key, CommonUtils.toString(field));
    }

    public static boolean expire(String key, long seconds) {
        return withRedis(Redis.get(), conn -> conn.sync().expire(key, seconds));
    }

    public static boolean expireAt(String key, long timestamp) {
        return withRedis(Redis.get(), conn -> conn.sync().expireat(key, timestamp));
    }

    public static boolean expireAt(String key, Date date) {
        return withRedis(Redis.get(), conn -> conn.sync().expireat(key, date));
    }

    public static long incr(String key) {
        return withRedis(Redis.get(), conn -> {
            if (CommonUtils.isNullOrSpace(conn.sync().get(key))) conn.sync().set(key, "0");
            return conn.sync().incr(key);
        });
    }

    public static long incrBy(String key, long val) {
        return withRedis(Redis.get(), conn -> {
            if (CommonUtils.isNullOrSpace(conn.sync().get(key))) conn.sync().set(key, "0");
            return conn.sync().incrby(key, val);
        });
    }

    public static double incrBy(String key, double val) {
        return withRedis(Redis.get(), conn -> {
            if (CommonUtils.isNullOrSpace(conn.sync().get(key))) conn.sync().set(key, "0");
            return conn.sync().incrbyfloat(key, val);
        });
    }

    public static long decr(String key) {
        return withRedis(Redis.get(), conn -> conn.sync().decr(key));
    }

    public static long decrBy(String key, long val) {
        return withRedis(Redis.get(), conn -> conn.sync().decrby(key, val));
    }

    public static double decrBy(String key, double val) {
        return withRedis(Redis.get(), conn -> {
            double current = CommonUtils.parseDouble(conn.sync().get(key));
            conn.sync().set(key, CommonUtils.toString(current - val));
            return current - val;
        });
    }

    // ------------------- Hash -------------------

    public static long incrHash(String key, String field) {
        return withRedis(Redis.get(), conn -> {
            if (CommonUtils.isNullOrSpace(conn.sync().hget(key, field))) conn.sync().hset(key, field, "0");
            return conn.sync().hincrby(key, field, 1);
        });
    }

    public static long incrByHash(String key, String field, long val) {
        return withRedis(Redis.get(), conn -> {
            if (CommonUtils.isNullOrSpace(conn.sync().hget(key, field))) conn.sync().hset(key, field, "0");
            return conn.sync().hincrby(key, field, val);
        });
    }

    public static double incrByHash(String key, String field, double val) {
        return withRedis(Redis.get(), conn -> {
            if (CommonUtils.isNullOrSpace(conn.sync().hget(key, field))) conn.sync().hset(key, field, "0");
            return conn.sync().hincrbyfloat(key, field, val);
        });
    }

    public static long decrHash(String key, String field) {
        return withRedis(Redis.get(), conn -> {
            long value = CommonUtils.parseLong(conn.sync().hget(key, field)) - 1;
            conn.sync().hset(key, field, CommonUtils.toString(value));
            return value;
        });
    }

    public static long decrBy(String key, String field, long val) {
        return withRedis(Redis.get(), conn -> {
            long value = CommonUtils.parseLong(conn.sync().hget(key, field)) - val;
            conn.sync().hset(key, field, CommonUtils.toString(value));
            return value;
        });
    }

    public static double decrBy(String key, String field, double val) {
        return withRedis(Redis.get(), conn -> {
            double value = CommonUtils.parseDouble(conn.sync().hget(key, field)) - val;
            conn.sync().hset(key, field, CommonUtils.toString(value));
            return value;
        });
    }

    @SuppressWarnings("unchecked")
	public static boolean setHash(String key, String field, Object val) {
        StatefulRedisConnection<String, Object> conn = (StatefulRedisConnection<String, Object>) determineConnection(val);
        return withRedis(conn, c -> c.sync().hset(key, field, val));
    }

    public static boolean setHash(String key, long field, Object val) {
        return setHash(key, CommonUtils.toString(field), val);
    }

    // ------------------- List -------------------

    @SuppressWarnings("unchecked")
    public static long rpush(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = (StatefulRedisConnection<String, Object>) determineConnection(val);
        return withRedis(conn, c -> c.sync().rpush(key, val));
    }

    @SuppressWarnings("unchecked")
    public static void rpushAsync(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = (StatefulRedisConnection<String, Object>) determineConnection(val);
        withRedisAsync(conn, key, val);
    }

    @SuppressWarnings("unchecked")
    public static long lpush(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = (StatefulRedisConnection<String, Object>) determineConnection(val);
        return withRedis(conn, c -> c.sync().lpush(key, val));
    }

    @SuppressWarnings("unchecked")
    public static void lpushAsync(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = (StatefulRedisConnection<String, Object>) determineConnection(val);
        withRedisAsync(conn, key, val);
    }

    @SuppressWarnings("unchecked")
    public static <T> T rpop(String key, Class<T> clazz) {
        StatefulRedisConnection<String, Object> conn = (StatefulRedisConnection<String, Object>) determineConnection(clazz);
        return clazz.cast(withRedis(conn, c -> c.sync().rpop(key)));
    }

    @SuppressWarnings("unchecked")
    public static <T> T lpop(String key, Class<T> clazz) {
        StatefulRedisConnection<String, Object> conn = (StatefulRedisConnection<String, Object>) determineConnection(clazz);
        return clazz.cast(withRedis(conn, c -> c.sync().lpop(key)));
    }

    // ------------------- Generic get/set -------------------

    public static <V> V get(String key, StatefulRedisConnection<String, V> conn) {
        return withRedis(conn, c -> c.sync().get(key));
    }

    public static <V> String set(String key, V val, long expire, StatefulRedisConnection<String, V> conn) {
        return withRedis(conn, c -> {
            if (expire != 0) return c.sync().setex(key, expire, val);
            else return c.sync().set(key, val);
        });
    }

    // ------------------- Scan / Keys -------------------

    public static List<String> keys(String pattern) {
        return withRedis(Redis.get(), conn -> conn.sync().keys(pattern));
    }

    public static List<String> keysHash(String key) {
        return withRedis(Redis.get(), conn -> conn.sync().hkeys(key));
    }

    public static List<String> scan(String key) {
        return scan(key, new ScanArgs().match(key).limit(999_999));
    }

    public static List<String> scan(String key, ScanArgs args) {
        return withRedis(Redis.get(), conn -> {
            KeyScanCursor<String> cursor = conn.sync().scan(args);
            List<String> list = cursor == null ? new ArrayList<>() : cursor.getKeys();
            list.sort(String::compareTo);
            return list;
        });
    }
    


    // ---------------- String ----------------
    public static String get(String key) {
        return get(key, 0);
    }

    public static String get(String key, long expire) {
        return getValue(key, expire, Redis.get());
    }

    public static String set(String key, String val) {
        return set(key, val, 0);
    }

    public static String set(String key, String val, long expire) {
        return setValue(key, val, expire, Redis.get());
    }

    // ---------------- SharedMap ----------------
    public static SharedMap<String, Object> getSharedMap(String key) {
        return getSharedMap(key, 0);
    }

    public static SharedMap<String, Object> getSharedMap(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMap());
    }

    public static String set(String key, SharedMap<String, Object> val) {
        return set(key, val, 0);
    }

    public static String set(String key, SharedMap<String, Object> val, long expire) {
        return setValue(key, val, expire, Redis.getSharedMap());
    }

    // ---------------- SharedMap<String,String> ----------------
    public static SharedMap<String, String> getSharedMapString(String key) {
        return getSharedMapString(key, 0);
    }

    public static SharedMap<String, String> getSharedMapString(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapString());
    }

    public static String setString(String key, SharedMap<String, String> val) {
        return setString(key, val, 0);
    }

    public static String setString(String key, SharedMap<String, String> val, long expire) {
        return setValue(key, val, expire, Redis.getSharedMapString());
    }

    // ---------------- SharedMap<String,Object> List ----------------
    public static List<SharedMap<String, Object>> getSharedMapList(String key) {
        return getSharedMapList(key, 0);
    }

    public static List<SharedMap<String, Object>> getSharedMapList(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapList());
    }

    public static String setSharedMapList(String key, List<SharedMap<String, Object>> vals) {
        return setSharedMapList(key, vals, 0);
    }

    public static String setSharedMapList(String key, List<SharedMap<String, Object>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getSharedMapList());
    }

    // ---------------- SharedMap<String,String> List ----------------
    public static List<SharedMap<String, String>> getSharedMapListString(String key) {
        return getSharedMapListString(key, 0);
    }

    public static List<SharedMap<String, String>> getSharedMapListString(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapListString());
    }

    public static String setShared(String key, List<SharedMap<String, String>> vals) {
        return setShared(key, vals, 0);
    }

    public static String setShared(String key, List<SharedMap<String, String>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getSharedMapListString());
    }

    // ---------------- LinkedMap ----------------
    public static LinkedMap<String, Object> getLinkedMap(String key) {
        return getLinkedMap(key, 0);
    }

    public static LinkedMap<String, Object> getLinkedMap(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMap());
    }

    public static String setLinkedMap(String key, LinkedMap<String, Object> val) {
        return setLinkedMap(key, val, 0);
    }

    public static String setLinkedMap(String key, LinkedMap<String, Object> val, long expire) {
        return setValue(key, val, expire, Redis.getLinkedMap());
    }

    // ---------------- LinkedMap<String,String> ----------------
    public static LinkedMap<String, String> getLinkedMapString(String key) {
        return getLinkedMapString(key, 0);
    }

    public static LinkedMap<String, String> getLinkedMapString(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMapString());
    }

    public static String set(String key, LinkedMap<String, String> val) {
        return set(key, val, 0);
    }

    public static String set(String key, LinkedMap<String, String> val, long expire) {
        return setValue(key, val, expire, Redis.getLinkedMapString());
    }

    // ---------------- LinkedMap<String,Object> List ----------------
    public static List<LinkedMap<String, Object>> getLinkedMapList(String key) {
        return getLinkedMapList(key, 0);
    }

    public static List<LinkedMap<String, Object>> getLinkedMapList(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMapList());
    }

    public static String setLinkedMapList(String key, List<LinkedMap<String, Object>> vals) {
        return setLinkedMapList(key, vals, 0);
    }

    public static String setLinkedMapList(String key, List<LinkedMap<String, Object>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getLinkedMapList());
    }

    // ---------------- LinkedMap<String,String> List ----------------
    public static List<LinkedMap<String, String>> getLinkedMapListString(String key) {
        return getLinkedMapListString(key, 0);
    }

    public static List<LinkedMap<String, String>> getLinkedMapListString(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMapListString());
    }

    public static String setLinkedMapListString(String key, List<LinkedMap<String, String>> vals) {
        return setLinkedMapListString(key, vals, 0);
    }

    public static String setLinkedMapListString(String key, List<LinkedMap<String, String>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getLinkedMapListString());
    }

    // ---------------- ThreadSafeLinkedMap ----------------
    public static ThreadSafeLinkedMap<String, Object> getThreadSafeLinkedMap(String key) {
        return getThreadSafeLinkedMap(key, 0);
    }

    public static ThreadSafeLinkedMap<String, Object> getThreadSafeLinkedMap(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMap());
    }

    public static String setThreadSafeLinkedMap(String key, ThreadSafeLinkedMap<String, Object> val) {
        return setThreadSafeLinkedMap(key, val, 0);
    }

    public static String setThreadSafeLinkedMap(String key, ThreadSafeLinkedMap<String, Object> val, long expire) {
        return setValue(key, val, expire, Redis.getThreadSafeLinkedMap());
    }

    // ---------------- ThreadSafeLinkedMap<String,String> ----------------
    public static ThreadSafeLinkedMap<String, String> getThreadSafeLinkedMapString(String key) {
        return getThreadSafeLinkedMapString(key, 0);
    }

    public static ThreadSafeLinkedMap<String, String> getThreadSafeLinkedMapString(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMapString());
    }

    public static String set(String key, ThreadSafeLinkedMap<String, String> val) {
        return set(key, val, 0);
    }

    public static String set(String key, ThreadSafeLinkedMap<String, String> val, long expire) {
        return setValue(key, val, expire, Redis.getThreadSafeLinkedMapString());
    }

    // ---------------- ThreadSafeLinkedMap<String,Object> List ----------------
    public static List<ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMapList(String key) {
        return getThreadSafeLinkedMapList(key, 0);
    }

    public static List<ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMapList(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMapList());
    }

    public static String setThreadSafeLinkedList(String key, List<ThreadSafeLinkedMap<String, Object>> vals) {
        return setThreadSafeLinkedList(key, vals, 0);
    }

    public static String setThreadSafeLinkedList(String key, List<ThreadSafeLinkedMap<String, Object>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getThreadSafeLinkedMapList());
    }

    // ---------------- ThreadSafeLinkedMap<String,String> List ----------------
    public static List<ThreadSafeLinkedMap<String, String>> getThreadSafeLinkedMapListString(String key) {
        return getThreadSafeLinkedMapListString(key, 0);
    }

    public static List<ThreadSafeLinkedMap<String, String>> getThreadSafeLinkedMapListString(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMapListString());
    }

    public static String setThreadSafeLinkedListString(String key, List<ThreadSafeLinkedMap<String, String>> vals) {
        return setThreadSafeLinkedListString(key, vals, 0);
    }

    public static String setThreadSafeLinkedListString(String key, List<ThreadSafeLinkedMap<String, String>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getThreadSafeLinkedMapListString());
    }

    // ---------------- 내부 유틸 (getValue/setValue) ----------------
    private static <T> T getValue(String key, long expire, StatefulRedisConnection<String, T> conn) {
        // 실제 Redis 조회 로직 구현
        // 예: conn.sync().get(key) 또는 JSON deserialize
        return null;
    }

    private static <T> String setValue(String key, T val, long expire, StatefulRedisConnection<String, T> conn) {
        // 실제 Redis 저장 로직 구현
        // 예: conn.sync().set(key, val) + expire 처리
        return "OK";
    }

    
	
	
	
}
