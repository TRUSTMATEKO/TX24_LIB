package kr.tx24.lib.redis;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;

/**
 * - 각 타입별로 하나의 연결만 유지 (Thread-Safe)
 * - Connection을 재사용하여 성능 최적화
 * - Application 종료 시에만 연결 종료
 * - Lettuce 6.2.x 최적화 적용
 */
public final class Redis {

    private static final Logger logger = LoggerFactory.getLogger(Redis.class);
    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;

    private static final Map<String, StatefulRedisConnection<String, ?>> connectionCache = new ConcurrentHashMap<>();
    
    static {
        initClient();
        Runtime.getRuntime().addShutdownHook(new Thread(Redis::shutdown, "Redis-Shutdown-Hook"));
    }

    private Redis() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static synchronized void initClient() {
        if (client != null) return;
        
        try {
            SystemUtils.init();
            String redisUri = SystemUtils.getRedisSystemUri();
            
            if (SystemUtils.REDIS_INITIAL.equals(redisUri)) {
                throw new IllegalStateException("NOT_SET");
            }
            
            // ClientResources 설정 (성능 최적화)
            int cpuCount = Runtime.getRuntime().availableProcessors();
            clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(Math.max(2, cpuCount)) 
                    .computationThreadPoolSize(Math.max(2, cpuCount))
                    .build();
            
            // RedisURI 파싱 및 타임아웃 설정
            RedisURI uri = RedisURI.create(redisUri);
            uri.setTimeout(java.time.Duration.ofSeconds(10));
            
            client = RedisClient.create(clientResources, uri);
            
            logger.info("Redis initialized: {}", redisUri);
            
        } catch (IllegalStateException e) {
            if ("NOT_SET".equals(e.getMessage())) {
                logger.warn("Redis 주소가 등록되지 않았습니다: -DREDIS -DREDIS_KEY");
            }
        } catch (Exception e) {
            logger.error("Redis client initialization failed: {}", SystemUtils.getRedisSystemUri(), e);
        }
    }

    public static RedisClient getClient() {
        return client;
    }
    
    public static int getCachedConnectionCount() {
        return connectionCache.size();
    }
    
    public static boolean isConnectionOpen(Class<?> valueType) {
        var conn = connectionCache.get(valueType.getName());
        return conn != null && conn.isOpen();
    }

    public static <K, V> void close(StatefulRedisConnection<K, V> conn) {
        if (conn != null) {
            try { 
                conn.close(); 
            } catch (Exception e) {
                logger.debug("Error closing connection", e);
            }
        }
    }
    
    public static synchronized void shutdown() {
        
        // 모든 연결 종료
        connectionCache.values().parallelStream().forEach(conn -> {
            try {
                if (conn != null && conn.isOpen()) {
                    conn.close();
                }
            } catch (Exception e) {
                logger.warn("Error closing connection", e);
            }
        });
        
        connectionCache.clear();
        
        // Redis Client 종료
        if (client != null) {
            try {
                client.shutdown(100, 1000, TimeUnit.MILLISECONDS);
                client = null;
            } catch (Exception e) {
                logger.warn("Error shutting down Redis client", e);
            }
        }
        
        // ClientResources 종료
        if (clientResources != null) {
            try {
                clientResources.shutdown(100, 1000, TimeUnit.MILLISECONDS).get();
                clientResources = null;
            } catch (Exception e) {
                logger.warn("Error shutting down ClientResources", e);
            }
        }
        
    }

    @SuppressWarnings("unchecked")
    public static <V> StatefulRedisConnection<String, V> get(Class<V> valueType) {
        if (client == null) {
            throw new IllegalStateException("Redis client not initialized");
        }
        
        String cacheKey = valueType.getName();
        
        return (StatefulRedisConnection<String, V>) connectionCache.computeIfAbsent(
            cacheKey, 
            k -> {
            	RedisObjectCodec<?> codec = new RedisObjectCodec<>(valueType);
            	StatefulRedisConnection <String, ?>  conn = client.connect(codec);
                logger.debug("Created new Redis connection for type: {}", valueType.getName());
                return conn;
            }
        );
    }

    public static <V> StatefulRedisPubSubConnection<String, V> getPubSub(Class<V> valueType) {
        if (client == null) {
            throw new IllegalStateException("Redis client not initialized");
        }
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
        return (StatefulRedisConnection<String, SharedMap<String, String>>) 
            (StatefulRedisConnection<?, ?>) get((Class<SharedMap<String, Object>>)(Class<?>)SharedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<SharedMap<String, Object>>> getSharedMapList() {
        return get((Class<List<SharedMap<String, Object>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<SharedMap<String, String>>> getSharedMapListString() {
        return (StatefulRedisConnection<String, List<SharedMap<String, String>>>) 
            (StatefulRedisConnection<?, ?>) get((Class<List<SharedMap<String, Object>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, LinkedMap<String, Object>> getLinkedMap() {
        return get((Class<LinkedMap<String, Object>>) (Class<?>) LinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, LinkedMap<String, String>> getLinkedMapString() {
        return (StatefulRedisConnection<String, LinkedMap<String, String>>) 
            (StatefulRedisConnection<?, ?>) get((Class<LinkedMap<String, Object>>) (Class<?>) LinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<LinkedMap<String, Object>>> getLinkedMapList() {
        return get((Class<List<LinkedMap<String, Object>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<LinkedMap<String, String>>> getLinkedMapListString() {
        return (StatefulRedisConnection<String, List<LinkedMap<String, String>>>) 
            (StatefulRedisConnection<?, ?>) get((Class<List<LinkedMap<String, Object>>>) (Class<?>) List.class);
    }
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMap() {
        return get((Class<ThreadSafeLinkedMap<String, Object>>) (Class<?>) ThreadSafeLinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, ThreadSafeLinkedMap<String, String>> getThreadSafeLinkedMapString() {
        return (StatefulRedisConnection<String, ThreadSafeLinkedMap<String, String>>) 
            (StatefulRedisConnection<?, ?>) get((Class<ThreadSafeLinkedMap<String, Object>>) (Class<?>) ThreadSafeLinkedMap.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<ThreadSafeLinkedMap<String, Object>>> getThreadSafeLinkedMapList() {
        return get((Class<List<ThreadSafeLinkedMap<String, Object>>>) (Class<?>) List.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<ThreadSafeLinkedMap<String, String>>> getThreadSafeLinkedMapListString() {
        return (StatefulRedisConnection<String, List<ThreadSafeLinkedMap<String, String>>>) 
            (StatefulRedisConnection<?, ?>) get((Class<List<ThreadSafeLinkedMap<String, Object>>>) (Class<?>) List.class);
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