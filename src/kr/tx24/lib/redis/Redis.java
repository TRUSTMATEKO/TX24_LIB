package kr.tx24.lib.redis;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;

/**
 * Redis helper
 * - Unified connection management
 * - Generic-based connection methods
 * - Pub/Sub support
 */
public class Redis {

    private static final Logger logger = LoggerFactory.getLogger(Redis.class);
    private static RedisClient client = null;

    static {
        initClient();
    }

    private static synchronized void initClient() {
        if (client != null) return;
        try {
            SystemUtils.init();
            if(SystemUtils.getRedisSystemUri().equals(SystemUtils.REDIS_INITIAL)) {
            	throw new Exception("NOT_SET");
            }else {
	            client = RedisClient.create(SystemUtils.getRedisSystemUri());
	            logger.info("Redis initialized: {}", SystemUtils.getRedisSystemUri());
            }
        } catch (Exception e) {
        	if(e.getMessage().startsWith("NOT_SET")) {
        		logger.warn("Redis 에 주소가 등록되지 않았습니다 .-DREDIS -DREDIS_KEY ");	
        	}else {
        		logger.warn("Redis client initialization failed: {}", SystemUtils.getRedisSystemUri(), e);
        	}
        }
    }

    public static RedisClient getClient() {
        return client;
    }

    public static <K, V> void close(StatefulRedisConnection<K, V> conn) {
        if (conn != null) {
            try { conn.close(); } catch (Exception ignored) {}
        }
    }

    public static void shutdown() {
        if (client != null) {
            client.shutdown(0, 10, TimeUnit.MILLISECONDS);
        }
    }

    // ---------------- Generic Connection ----------------

    public static <V> StatefulRedisConnection<String, V> get(Class<V> valueType) {
        return client.connect(new RedisObjectCodec<>(valueType));
    }

    public static <V> StatefulRedisPubSubConnection<String, V> getPubSub(Class<V> valueType) {
        return client.connectPubSub(new RedisObjectCodec<>(valueType));
    }

    // ---------------- Convenience Methods ----------------

    public static StatefulRedisConnection<String, String> get() {
        return get(String.class);
    }
    
    public static StatefulRedisConnection<String, String> getString() {
        return get(String.class);
    }

    public static StatefulRedisConnection<String, Object> getObject() {
        return get(Object.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, SharedMap<String, Object>> getSharedMap() {
        return get((Class<SharedMap<String, Object>>)(Class<?>)SharedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, SharedMap<String, String>> getSharedMapString() {
        return get((Class<SharedMap<String, String>>)(Class<?>)SharedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<SharedMap<String, Object>>> getSharedMapList() {
        return get((Class<List<SharedMap<String, Object>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<SharedMap<String, String>>> getSharedMapListString() {
        return get((Class<List<SharedMap<String, String>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, LinkedMap<String, Object>> getLinkedMap() {
        return get((Class<LinkedMap<String, Object>>) (Class<?>) LinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, LinkedMap<String, String>> getLinkedMapString() {
        return get((Class<LinkedMap<String, String>>) (Class<?>) LinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<LinkedMap<String, Object>>> getLinkedMapList() {
        return get((Class<List<LinkedMap<String, Object>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<LinkedMap<String, String>>> getLinkedMapListString() {
        return get((Class<List<LinkedMap<String, String>>>) (Class<?>) List.class);
    }
    
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMap() {
        return get((Class<ThreadSafeLinkedMap<String, Object>>) (Class<?>) ThreadSafeLinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, ThreadSafeLinkedMap<String, String>> getThreadSafeLinkedMapString() {
        return get((Class<ThreadSafeLinkedMap<String, String>>) (Class<?>) ThreadSafeLinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<ThreadSafeLinkedMap<String, Object>>> getThreadSafeLinkedMapList() {
        return get((Class<List<ThreadSafeLinkedMap<String, Object>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<ThreadSafeLinkedMap<String, String>>> getThreadSafeLinkedMapListString() {
        return get((Class<List<ThreadSafeLinkedMap<String, String>>>) (Class<?>) List.class);
    }


    // ---------------- Pub/Sub Convenience ----------------

    public static StatefulRedisPubSubConnection<String, Object> getPubSubObject() {
        return getPubSub(Object.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisPubSubConnection<String, SharedMap<String, Object>> getPubSubSharedMap() {
        return getPubSub((Class<SharedMap<String, Object>>) (Class<?>) SharedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisPubSubConnection<String, SharedMap<String, String>> getPubSubSharedMapString() {
        return getPubSub((Class<SharedMap<String, String>>) (Class<?>) SharedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisPubSubConnection<String, LinkedMap<String, Object>> getPubSubLinkedMap() {
        return getPubSub((Class<LinkedMap<String, Object>>) (Class<?>) LinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisPubSubConnection<String, LinkedMap<String, String>> getPubSubLinkedMapString() {
        return getPubSub((Class<LinkedMap<String, String>>) (Class<?>) LinkedMap.class);
    }
    
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisPubSubConnection<String, ThreadSafeLinkedMap<String, Object>> getPubSubThreadSafeLinkedMap() {
        return getPubSub((Class<ThreadSafeLinkedMap<String, Object>>) (Class<?>) ThreadSafeLinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisPubSubConnection<String, ThreadSafeLinkedMap<String, String>> getPubSubThreadSafeLinkedMapString() {
        return getPubSub((Class<ThreadSafeLinkedMap<String, String>>) (Class<?>) ThreadSafeLinkedMap.class);
    }
}
