package kr.tx24.lib.redis;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;

/**
 * Redis 연결 관리 클래스
 * - 각 타입별로 하나의 연결만 유지 (Thread-Safe)
 * - Connection을 재사용하여 성능 최적화
 * - Application 종료 시에만 연결 종료
 * - Lettuce 6.2.x 최적화 적용
 * - JSON/FST Codec 선택 지원 
 * @author TX24
 * @version 2.0
 */
public final class Redis {

    private static final Logger logger = LoggerFactory.getLogger(Redis.class);
    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;

    private static final Map<String, StatefulRedisConnection<String, ?>> connectionCache = new ConcurrentHashMap<>();
    
    static {
        initClient();
        Runtime.getRuntime().addShutdownHook(new Thread(Redis::shutdown, "ShutdownHook-Redis"));
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
        return isConnectionOpen(valueType, CodecType.JSON);
    }
    
    public static boolean isConnectionOpen(Class<?> valueType, CodecType codecType) {
        String cacheKey = buildCacheKey(valueType, codecType);
        var conn = connectionCache.get(cacheKey);
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
        
    	/*
    	for (StatefulRedisConnection <String, ?>  conn : connectionCache.values()) { 
            try {
                if (conn != null && conn.isOpen()) {
                    conn.close();
                }
            } catch (Exception e) {
                logger.warn("Error closing connection", e);
            }
        }*/
    	
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
    
    
    /**
     * 타입별 Connection 가져오기 (기본 - JSON Codec)
     * 
     * @param <V> 값 타입
     * @param valueType 값 클래스
     * @return Redis Connection
     */
    public static <V> StatefulRedisConnection<String, V> get(Class<V> valueType) {
        return get(valueType, CodecType.JSON);
    }
    

    /**
     * ⭐ 타입별 Connection 가져오기 (Codec 선택)
     * 
     * 사용 예:
     * Redis.get(User.class, CodecType.JSON)
     * Redis.get(Order.class, CodecType.FST)
     * 
     * @param <V> 값 타입
     * @param valueType 값 클래스
     * @param codecType Codec 타입 (JSON 또는 FST)
     * @return Redis Connection
     */
    @SuppressWarnings("unchecked")
    public static <V> StatefulRedisConnection<String, V> get(Class<V> valueType, CodecType codecType) {
        if (client == null) {
            throw new IllegalStateException("Redis client not initialized");
        }
        
        String cacheKey = buildCacheKey(valueType, codecType);
        
        return (StatefulRedisConnection<String, V>) connectionCache.computeIfAbsent(
            cacheKey, 
            k -> {
                RedisCodec<String, V> codec = createCodec(valueType, codecType);
                StatefulRedisConnection<String, V> conn = client.connect(codec);
                logger.debug("Created new Redis connection: {} [{}]", valueType.getName(), codecType);
                return conn;
            }
        );
    }

    /**
     * PubSub Connection (기본 - JSON Codec)
     */
    public static <V> StatefulRedisPubSubConnection<String, V> getPubSub(Class<V> valueType) {
        return getPubSub(valueType, CodecType.JSON);
    }

    /**
     * ⭐ PubSub Connection (Codec 선택)
     */
    public static <V> StatefulRedisPubSubConnection<String, V> getPubSub(Class<V> valueType, CodecType codecType) {
        if (client == null) {
            throw new IllegalStateException("Redis client not initialized");
        }
        RedisCodec<String, V> codec = createCodec(valueType, codecType);
        return client.connectPubSub(codec);
    }
    
    
    /**
     * Codec 생성
     */
    private static <V> RedisCodec<String, V> createCodec(Class<V> valueType, CodecType codecType) {
        switch (codecType) {
            case FST:
                return new RedisFstCodec<>(valueType);
            case JSON:
            default:
                return new RedisJsonCodec<>(valueType);
        }
    }
    
    
    
    
    private static String buildCacheKey(Class<?> valueType, CodecType codecType) {
        return valueType.getName() + ":" + codecType.name();
    }

    // ================= 기본 Convenience Methods (기존) =================

    public static StatefulRedisConnection<String, String> get() {
        return get(String.class);
    }
    
    public static StatefulRedisConnection<String, String> getString() {
        return get(String.class);
    }

    public static StatefulRedisConnection<String, Object> getObject() {
        return get(Object.class);
    }
    
    /**
     * String Connection (Codec 선택)
     */
    public static StatefulRedisConnection<String, String> getString(CodecType codecType) {
        return get(String.class, codecType);
    }

    /**
     * Object Connection (Codec 선택)
     */
    public static StatefulRedisConnection<String, Object> getObject(CodecType codecType) {
        return get(Object.class, codecType);
    }

    
    

    // ================= SharedMap 관련 (기존) =================

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, SharedMap<String, Object>> getSharedMap() {
        return get((Class<SharedMap<String, Object>>)(Class<?>)SharedMap.class);
    }
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, SharedMap<String, Object>> getSharedMap(CodecType codecType) {
        return get((Class<SharedMap<String, Object>>)(Class<?>)SharedMap.class, codecType);
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

    // ================= LinkedMap 관련 (기존) =================

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, LinkedMap<String, Object>> getLinkedMap() {
        return get((Class<LinkedMap<String, Object>>) (Class<?>) LinkedMap.class);
    }
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, LinkedMap<String, Object>> getLinkedMap(CodecType codecType) {
        return get((Class<LinkedMap<String, Object>>) (Class<?>) LinkedMap.class, codecType);
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
    
    // ================= ThreadSafeLinkedMap 관련 (기존) =================

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMap() {
        return get((Class<ThreadSafeLinkedMap<String, Object>>) (Class<?>) ThreadSafeLinkedMap.class);
    }
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMap(CodecType codecType) {
        return get((Class<ThreadSafeLinkedMap<String, Object>>) (Class<?>) ThreadSafeLinkedMap.class, codecType);
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

    // ================= List 관련 (신규 추가) =================

    /**
     * List<String> 전용 Connection
     * 
     * @return List<String> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<String>> getStringList() {
        return get((Class<List<String>>) (Class<?>) List.class);
    }
    
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<String>> getStringList(CodecType codecType) {
        return get((Class<List<String>>) (Class<?>) List.class, codecType);
    }

    /**
     * List<Object> 전용 Connection
     * 
     * @return List<Object> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<Object>> getObjectList() {
        return get((Class<List<Object>>) (Class<?>) List.class);
    }
    
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, List<Object>> getObjectList(CodecType codecType) {
        return get((Class<List<Object>>) (Class<?>) List.class, codecType);
    }

    // ================= Set 관련 (신규 추가) =================

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Set<String>> getStringSet() {
        return get((Class<Set<String>>) (Class<?>) Set.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Set<Object>> getObjectSet() {
        return get((Class<Set<Object>>) (Class<?>) Set.class);
    }
    
    
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Set<Object>> getObjectSet(CodecType codecType) {
        return get((Class<Set<Object>>) (Class<?>) Set.class, codecType);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Set<SharedMap<String, Object>>> getSharedMapSet() {
        return get((Class<Set<SharedMap<String, Object>>>) (Class<?>) Set.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Set<LinkedMap<String, Object>>> getLinkedMapSet() {
        return get((Class<Set<LinkedMap<String, Object>>>) (Class<?>) Set.class);
    }

    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Set<ThreadSafeLinkedMap<String, Object>>> getThreadSafeLinkedMapSet() {
        return get((Class<Set<ThreadSafeLinkedMap<String, Object>>>) (Class<?>) Set.class);
    }


    // ================= Map 관련 (신규 추가) =================

    /**
     * Map<String, String> 전용 Connection
     * 
     * @return Map<String, String> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Map<String, String>> getStringMap() {
        return get((Class<Map<String, String>>) (Class<?>) Map.class);
    }

    /**
     * Map<String, Object> 전용 Connection
     * 
     * @return Map<String, Object> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Map<String, Object>> getStringObjectMap() {
        return get((Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    /**
     * Map<String, SharedMap<String, Object>> 전용 Connection
     * 
     * @return Map<String, SharedMap> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Map<String, SharedMap<String, Object>>> getStringSharedMapMap() {
        return get((Class<Map<String, SharedMap<String, Object>>>) (Class<?>) Map.class);
    }

    /**
     * Map<String, LinkedMap<String, Object>> 전용 Connection
     * 
     * @return Map<String, LinkedMap> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Map<String, LinkedMap<String, Object>>> getStringLinkedMapMap() {
        return get((Class<Map<String, LinkedMap<String, Object>>>) (Class<?>) Map.class);
    }

    /**
     * Map<String, ThreadSafeLinkedMap<String, Object>> 전용 Connection
     * 
     * @return Map<String, ThreadSafeLinkedMap> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Map<String, ThreadSafeLinkedMap<String, Object>>> getStringThreadSafeLinkedMapMap() {
        return get((Class<Map<String, ThreadSafeLinkedMap<String, Object>>>) (Class<?>) Map.class);
    }

    /**
     * Map<Object, Object> 전용 Connection
     * 
     * @return Map<Object, Object> Connection
     */
    @SuppressWarnings("unchecked")
    public static StatefulRedisConnection<String, Map<Object, Object>> getObjectMap() {
        return get((Class<Map<Object, Object>>) (Class<?>) Map.class);
    }

    // ================= Pub/Sub Convenience (기존) =================

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