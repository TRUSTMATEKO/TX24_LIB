package kr.tx24.lib.redis;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.GeoArgs;
import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.KeyValue;
import io.lettuce.core.Range;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScoredValue;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.XAddArgs;
import io.lettuce.core.XReadArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import kr.tx24.lib.db.Retrieve;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;

/**
 * Redis 유틸리티 클래스
 * Lettuce 클라이언트를 사용하여 Redis 작업을 수행하는 헬퍼 메서드 제공
 * 
 * <p>주요 기능:</p>
 * <ul>
 *   <li>Key/Value 기본 연산 (get, set, delete, expire 등)</li>
 *   <li>Hash 연산 (hget, hset, hincrby 등)</li>
 *   <li>List 연산 (rpush, lpush, rpop, lpop 등)</li>
 *   <li>Increment/Decrement 연산</li>
 *   <li>패턴 매칭 키 조회 및 삭제</li>
 *   <li>Cache-Aside Pattern 지원</li>
 *   <li>다양한 데이터 타입 지원 (String, SharedMap, LinkedMap, ThreadSafeLinkedMap)</li>
 * </ul>
 * 
 * @author TX24
 * @version 1.0
 */
public final class RedisUtils {

    private static final Logger logger = LoggerFactory.getLogger(RedisUtils.class);

    /** 분산 락 기본 TTL (초) */
    private static final long DEFAULT_LOCK_TTL = 30L;
    
    
    public abstract class TypeReference<T> {
        private final Type type;
        
        protected TypeReference() {
            Type superclass = getClass().getGenericSuperclass();
            if (superclass instanceof ParameterizedType) {
                this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException("TypeReference must be parameterized");
            }
        }
        
        public Type getType() {
            return type;
        }
    }
    
    /**
     * 유틸리티 클래스이므로 인스턴스화 방지
     */
    private RedisUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ------------------- 공통 Helper -------------------

    /**
     * Redis 작업을 수행하는 함수형 인터페이스
     * 
     * @param <K> Redis 키 타입
     * @param <V> Redis 값 타입
     * @param <R> 반환 타입
     */
    @FunctionalInterface
    private interface RedisAction<K, V, R> {
        R execute(StatefulRedisConnection<K, V> conn);
    }

    /**
     * Redis 작업 실행 헬퍼 메서드
     * Lettuce의 Connection은 Thread-Safe하므로 close 불필요
     * 
     * @param <K> Redis 키 타입
     * @param <V> Redis 값 타입
     * @param <R> 반환 타입
     * @param conn Redis 연결 객체
     * @param action 실행할 Redis 작업
     * @return 작업 결과
     */
    private static <K, V, R> R withRedis(StatefulRedisConnection<K, V> conn, RedisAction<K, V, R> action) {
        return action.execute(conn);
    }

    /**
     * 비동기 Redis 작업 실행 (rpush 전용)
     * 
     * @param <K> Redis 키 타입
     * @param <V> Redis 값 타입
     * @param conn Redis 연결 객체
     * @param key Redis 키
     * @param val 저장할 값
     */
    @SuppressWarnings("unchecked")
    private static <K, V> void withRedisAsync(StatefulRedisConnection<K, V> conn, K key, V val) {
        RedisFuture<Long> future = conn.async().rpush(key, val);
        future.thenAccept(v -> logger.debug("rpush async completed for key: {} with length: {}", key, v))
              .exceptionally(throwable -> {
                  logger.error("rpush async failed for key: {}", key, throwable);
                  return null;
              });
    }

    // ------------------- Connection 결정 (타입 기반) -------------------

    /**
     * 값의 타입에 따라 적절한 Redis Connection을 반환 (기존 메서드 - 하위 호환성 유지)
     * 
     * @param <T> 값 타입
     * @param val 값 객체
     * @return 타입에 맞는 Redis 연결 객체
     */
    @SuppressWarnings("unchecked")
    private static <T> StatefulRedisConnection<String, T> determineConnection(T val) {
        if (val == null) {
            return (StatefulRedisConnection<String, T>) Redis.getObject();
        }
        
        // JDK 17 instanceof pattern matching 사용
        if (val instanceof String) {
            return (StatefulRedisConnection<String, T>) Redis.getString();
        } else if (val instanceof SharedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getSharedMap();
        } else if (val instanceof LinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getLinkedMap();
        } else if (val instanceof ThreadSafeLinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getThreadSafeLinkedMap();
        } else if (val instanceof List<?> list) {
            return determineListConnection(list);
        } else if (val instanceof Set<?> set) {
            return determineSetConnection(set);
        } else if (val instanceof Map<?, ?> map) {
            return determineMapConnection(map);
        }
        
        return (StatefulRedisConnection<String, T>) Redis.getObject();
    }

    /**
     * 타입 정보를 명시적으로 받는 Connection 결정 메서드 (100% 커버)
     * 
     * 사용 예:
     * <pre>
     * // 빈 리스트도 올바르게 처리
     * List<SharedMap> emptyList = new ArrayList<>();
     * var conn = determineConnection(emptyList, new TypeReference<List<SharedMap>>() {});
     * 
     * // 복잡한 제네릭 타입
     * var conn = determineConnection(value, new TypeReference<Map<String, List<SharedMap>>>() {});
     * </pre>
     * 
     * @param <T> 값 타입
     * @param val 값 객체
     * @param typeRef 타입 참조
     * @return 타입에 맞는 Redis 연결 객체
     */
    @SuppressWarnings("unchecked")
    private static <T> StatefulRedisConnection<String, T> determineConnection(T val, TypeReference<T> typeRef) {
        Type type = typeRef.getType();
        
        // ParameterizedType이 아니면 기존 로직 사용
        if (!(type instanceof ParameterizedType paramType)) {
            return determineConnection(val);
        }
        
        Type rawType = paramType.getRawType();
        Type[] typeArgs = paramType.getActualTypeArguments();
        
        // List<T> 처리
        if (rawType == List.class && typeArgs.length > 0) {
            Type elementType = typeArgs[0];
            
            if (elementType == String.class) {
                return (StatefulRedisConnection<String, T>) Redis.getStringList();
            } else if (isTypeOf(elementType, SharedMap.class)) {
                return (StatefulRedisConnection<String, T>) Redis.getSharedMapList();
            } else if (isTypeOf(elementType, LinkedMap.class)) {
                return (StatefulRedisConnection<String, T>) Redis.getLinkedMapList();
            } else if (isTypeOf(elementType, ThreadSafeLinkedMap.class)) {
                return (StatefulRedisConnection<String, T>) Redis.getThreadSafeLinkedMapList();
            }
            return (StatefulRedisConnection<String, T>) Redis.getObjectList();
        }
        
        // Set<T> 처리
        if (rawType == Set.class && typeArgs.length > 0) {
            Type elementType = typeArgs[0];
            
            if (elementType == String.class) {
                return (StatefulRedisConnection<String, T>) Redis.getStringSet();
            } else if (isTypeOf(elementType, SharedMap.class)) {
                return (StatefulRedisConnection<String, T>) Redis.getSharedMapSet();
            } else if (isTypeOf(elementType, LinkedMap.class)) {
                return (StatefulRedisConnection<String, T>) Redis.getLinkedMapSet();
            } else if (isTypeOf(elementType, ThreadSafeLinkedMap.class)) {
                return (StatefulRedisConnection<String, T>) Redis.getThreadSafeLinkedMapSet();
            }
            return (StatefulRedisConnection<String, T>) Redis.getObjectSet();
        }
        
        // Map<K, V> 처리
        if (rawType == Map.class && typeArgs.length == 2) {
            Type keyType = typeArgs[0];
            Type valueType = typeArgs[1];
            
            if (keyType == String.class && valueType == String.class) {
                return (StatefulRedisConnection<String, T>) Redis.getStringMap();
            } else if (keyType == String.class && valueType == Object.class) {
                return (StatefulRedisConnection<String, T>) Redis.getStringObjectMap();
            } else if (keyType == String.class && isTypeOf(valueType, SharedMap.class)) {
                return (StatefulRedisConnection<String, T>) Redis.getStringSharedMapMap();
            }
            return (StatefulRedisConnection<String, T>) Redis.getObjectMap();
        }
        
        // 기타 복잡한 제네릭 타입
        return (StatefulRedisConnection<String, T>) Redis.getObject();
    }

    /**
     * 간편 사용을 위한 Class 기반 오버로드
     * 
     * 사용 예:
     * <pre>
     * var conn = determineConnectionByClass(String.class);
     * var conn = determineConnectionByClass(SharedMap.class);
     * </pre>
     * 
     * @param <T> 값 타입
     * @param clazz 클래스 타입
     * @return 타입에 맞는 Redis 연결 객체
     */
    @SuppressWarnings("unchecked")
    private static <T> StatefulRedisConnection<String, T> determineConnectionByClass(Class<?> clazz) {
        if (clazz == null) {
            return (StatefulRedisConnection<String, T>) Redis.getObject();
        }
        
        if (String.class.isAssignableFrom(clazz)) {
            return (StatefulRedisConnection<String, T>) Redis.getString();
        } else if (SharedMap.class.isAssignableFrom(clazz)) {
            return (StatefulRedisConnection<String, T>) Redis.getSharedMap();
        } else if (LinkedMap.class.isAssignableFrom(clazz)) {
            return (StatefulRedisConnection<String, T>) Redis.getLinkedMap();
        } else if (ThreadSafeLinkedMap.class.isAssignableFrom(clazz)) {
            return (StatefulRedisConnection<String, T>) Redis.getThreadSafeLinkedMap();
        } else if (List.class.isAssignableFrom(clazz)) {
            // List는 제네릭 정보 없이는 Object로 처리
            return (StatefulRedisConnection<String, T>) Redis.getObjectList();
        } else if (Set.class.isAssignableFrom(clazz)) {
            return (StatefulRedisConnection<String, T>) Redis.getObjectSet();
        } else if (Map.class.isAssignableFrom(clazz)) {
            return (StatefulRedisConnection<String, T>) Redis.getObjectMap();
        }
        
        return (StatefulRedisConnection<String, T>) Redis.getObject();
    }

    // ================= Private Helper Methods =================

    /**
     * List의 요소 타입에 따라 적절한 Connection 반환
     */
    @SuppressWarnings("unchecked")
    private static <T> StatefulRedisConnection<String, T> determineListConnection(List<?> list) {
        if (list.isEmpty()) {
            // 빈 리스트는 Object로 처리 (타입 정보 없음)
            return (StatefulRedisConnection<String, T>) Redis.getObjectList();
        }
        
        Object firstElement = list.get(0);
        
        // 모든 요소가 동일한 타입인지 확인
        Class<?> elementType = firstElement.getClass();
        boolean homogeneous = list.stream()
            .allMatch(e -> e != null && elementType.isInstance(e));
        
        if (!homogeneous) {
            return (StatefulRedisConnection<String, T>) Redis.getObjectList();
        }
        
        // 요소 타입별 Connection 반환
        if (firstElement instanceof String) {
            return (StatefulRedisConnection<String, T>) Redis.getStringList();
        } else if (firstElement instanceof SharedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getSharedMapList();
        } else if (firstElement instanceof LinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getLinkedMapList();
        } else if (firstElement instanceof ThreadSafeLinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getThreadSafeLinkedMapList();
        }
        
        return (StatefulRedisConnection<String, T>) Redis.getObjectList();
    }

    /**
     * Set의 요소 타입에 따라 적절한 Connection 반환
     */
    @SuppressWarnings("unchecked")
    private static <T> StatefulRedisConnection<String, T> determineSetConnection(Set<?> set) {
        if (set.isEmpty()) {
            return (StatefulRedisConnection<String, T>) Redis.getObjectSet();
        }
        
        Object firstElement = set.iterator().next();
        
        // 모든 요소가 동일한 타입인지 확인
        Class<?> elementType = firstElement.getClass();
        boolean homogeneous = set.stream()
            .allMatch(e -> e != null && elementType.isInstance(e));
        
        if (!homogeneous) {
            return (StatefulRedisConnection<String, T>) Redis.getObjectSet();
        }
        
        // 요소 타입별 Connection 반환
        if (firstElement instanceof String) {
            return (StatefulRedisConnection<String, T>) Redis.getStringSet();
        } else if (firstElement instanceof SharedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getSharedMapSet();
        } else if (firstElement instanceof LinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getLinkedMapSet();
        } else if (firstElement instanceof ThreadSafeLinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getThreadSafeLinkedMapSet();
        }
        
        return (StatefulRedisConnection<String, T>) Redis.getObjectSet();
    }

    /**
     * Map의 키/값 타입에 따라 적절한 Connection 반환
     */
    @SuppressWarnings("unchecked")
    private static <T> StatefulRedisConnection<String, T> determineMapConnection(Map<?, ?> map) {
        if (map.isEmpty()) {
            return (StatefulRedisConnection<String, T>) Redis.getObjectMap();
        }
        
        Map.Entry<?, ?> firstEntry = map.entrySet().iterator().next();
        Object firstKey = firstEntry.getKey();
        Object firstValue = firstEntry.getValue();
        
        // 모든 키/값이 동일한 타입인지 확인
        Class<?> keyType = firstKey.getClass();
        Class<?> valueType = firstValue.getClass();
        
        boolean homogeneousKeys = map.keySet().stream()
            .allMatch(k -> k != null && keyType.isInstance(k));
        boolean homogeneousValues = map.values().stream()
            .allMatch(v -> v != null && valueType.isInstance(v));
        
        if (!homogeneousKeys || !homogeneousValues) {
            return (StatefulRedisConnection<String, T>) Redis.getObjectMap();
        }
        
        // 키-값 타입별 Connection 반환
        if (firstKey instanceof String && firstValue instanceof String) {
            return (StatefulRedisConnection<String, T>) Redis.getStringMap();
        } else if (firstKey instanceof String && firstValue instanceof SharedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getStringSharedMapMap();
        } else if (firstKey instanceof String && firstValue instanceof LinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getStringLinkedMapMap();
        } else if (firstKey instanceof String && firstValue instanceof ThreadSafeLinkedMap) {
            return (StatefulRedisConnection<String, T>) Redis.getStringThreadSafeLinkedMapMap();
        } else if (firstKey instanceof String) {
            return (StatefulRedisConnection<String, T>) Redis.getStringObjectMap();
        }
        
        return (StatefulRedisConnection<String, T>) Redis.getObjectMap();
    }

    /**
     * Type이 특정 클래스와 매칭되는지 확인
     */
    private static boolean isTypeOf(Type type, Class<?> clazz) {
        if (type instanceof Class<?>) {
            return clazz.isAssignableFrom((Class<?>) type);
        } else if (type instanceof ParameterizedType paramType) {
            Type rawType = paramType.getRawType();
            if (rawType instanceof Class<?>) {
                return clazz.isAssignableFrom((Class<?>) rawType);
            }
        }
        return false;
    }

    // ------------------- 기본 Key/Value Operations -------------------

    /**
     * Redis 서버 연결 테스트
     * 
     * @return "PONG" 문자열
     */
    public static String ping() {
        return withRedis(Redis.getString(), conn -> conn.sync().ping());
    }

    /**
     * 키의 남은 TTL을 초 단위로 조회
     * 
     * @param key Redis 키
     * @return 남은 TTL (초), 키가 없으면 -2, TTL이 없으면 -1
     */
    public static long ttl(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().ttl(key));
    }

    /**
     * 키의 남은 TTL을 밀리초 단위로 조회
     * 
     * @param key Redis 키
     * @return 남은 TTL (밀리초), 키가 없으면 -2, TTL이 없으면 -1
     */
    public static long pttl(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().pttl(key));
    }

    /**
     * 하나 이상의 키를 삭제
     * 
     * @param keys 삭제할 키들
     * @return 삭제된 키의 개수
     */
    public static long del(String... keys) {
        return withRedis(Redis.getString(), conn -> conn.sync().del(keys));
    }
    
    /**
     * 패턴에 매칭되는 모든 키를 동기 방식으로 삭제
     * 
     * @param pattern 키 패턴 (예: "user:*", "session:*")
     * @return 삭제된 키의 개수
     */
    public static long deletePattern(String pattern) {
        if (CommonUtils.isNullOrSpace(pattern)) {
            return 0;
        }

        // 패턴 매칭되는 키 조회
        List<String> keys = scan(pattern);

        if (CommonUtils.isEmpty(keys)) {
            return 0;
        }

        // 일괄 삭제
        long deletedCount = del(keys.toArray(new String[0]));
        
        logger.debug("deletePattern: deleted {} ,pattern: {}", deletedCount, pattern);
        return deletedCount;
    }
    
    /**
     * 패턴에 매칭되는 모든 키를 비동기 방식으로 삭제
     * 
     * @param pattern 키 패턴 (예: "user:*", "session:*")
     */
    public static void deletePatternAsync(String pattern) {
        if (CommonUtils.isNullOrSpace(pattern)) {
            return;
        }

        // 패턴 매칭되는 키 조회
        List<String> keys = scan(pattern);

        if (CommonUtils.isEmpty(keys)) {
            return;
        }

        // 비동기로 삭제
        withRedis(Redis.getString(), conn -> {
            conn.async().del(keys.toArray(new String[0]))
                .thenAccept(count -> 
                    logger.debug("deletePatternAsync: deleted {} ,pattern: {}", count, pattern)
                )
                .exceptionally(throwable -> {
                    logger.debug("deletePatternAsync: failed for pattern: {}", pattern, throwable);
                    return null;
                });
            return null;
        });
    }

    /**
     * 하나 이상의 키가 존재하는지 확인
     * 
     * @param keys 확인할 키들
     * @return 존재하는 키의 개수
     */
    public static long exist(String... keys) {
        return withRedis(Redis.getString(), conn -> conn.sync().exists(keys));
    }

    /**
     * Hash에서 특정 필드가 존재하는지 확인
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @return 존재 여부
     */
    public static boolean exist(String key, String field) {
        return withRedis(Redis.getString(), conn -> conn.sync().hexists(key, field));
    }

    /**
     * Hash에서 특정 필드(숫자)가 존재하는지 확인
     * 
     * @param key Redis 키
     * @param field Hash 필드명 (숫자)
     * @return 존재 여부
     */
    public static boolean exists(String key, long field) {
        return exist(key, String.valueOf(field));
    }

    /**
     * 키에 TTL 설정 (초 단위)
     * 
     * @param key Redis 키
     * @param seconds TTL (초)
     * @return 설정 성공 여부
     */
    public static boolean expire(String key, long seconds) {
        return withRedis(Redis.getString(), conn -> conn.sync().expire(key, seconds));
    }

    /**
     * 키에 만료 시각 설정 (Unix timestamp)
     * 
     * @param key Redis 키
     * @param timestamp Unix timestamp (초)
     * @return 설정 성공 여부
     */
    public static boolean expireAt(String key, long timestamp) {
        return withRedis(Redis.getString(), conn -> conn.sync().expireat(key, timestamp));
    }

    /**
     * 키에 만료 시각 설정 (Date 객체)
     * 
     * @param key Redis 키
     * @param date 만료 일시
     * @return 설정 성공 여부
     */
    public static boolean expireAt(String key, Date date) {
        return withRedis(Redis.getString(), conn -> conn.sync().expireat(key, date));
    }

    // ------------------- Increment/Decrement Operations -------------------

    /**
     * 키의 값을 1 증가 (없으면 0으로 초기화 후 증가)
     * 
     * @param key Redis 키
     * @return 증가 후 값
     */
    public static long incr(String key) {
        return withRedis(Redis.getString(), conn -> {
            String value = conn.sync().get(key);
            if (CommonUtils.isNullOrSpace(value)) {
                conn.sync().set(key, "0");
            }
            return conn.sync().incr(key);
        });
    }

    /**
     * 키의 값을 지정한 수만큼 증가 (없으면 0으로 초기화 후 증가)
     * 
     * @param key Redis 키
     * @param val 증가할 값
     * @return 증가 후 값
     */
    public static long incrBy(String key, long val) {
        return withRedis(Redis.getString(), conn -> {
            String value = conn.sync().get(key);
            if (CommonUtils.isNullOrSpace(value)) {
                conn.sync().set(key, "0");
            }
            return conn.sync().incrby(key, val);
        });
    }

    /**
     * 키의 값을 실수만큼 증가 (없으면 0으로 초기화 후 증가)
     * 
     * @param key Redis 키
     * @param val 증가할 값 (실수)
     * @return 증가 후 값
     */
    public static double incrBy(String key, double val) {
        return withRedis(Redis.getString(), conn -> {
            String value = conn.sync().get(key);
            if (CommonUtils.isNullOrSpace(value)) {
                conn.sync().set(key, "0");
            }
            return conn.sync().incrbyfloat(key, val);
        });
    }

    /**
     * 키의 값을 1 감소
     * 
     * @param key Redis 키
     * @return 감소 후 값
     */
    public static long decr(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().decr(key));
    }

    /**
     * 키의 값을 지정한 수만큼 감소
     * 
     * @param key Redis 키
     * @param val 감소할 값
     * @return 감소 후 값
     */
    public static long decrBy(String key, long val) {
        return withRedis(Redis.getString(), conn -> conn.sync().decrby(key, val));
    }

    /**
     * 키의 값을 실수만큼 감소
     * 
     * @param key Redis 키
     * @param val 감소할 값 (실수)
     * @return 감소 후 값
     */
    public static double decrBy(String key, double val) {
        return withRedis(Redis.getString(), conn -> {
            double current = CommonUtils.parseDouble(conn.sync().get(key));
            double newValue = current - val;
            conn.sync().set(key, String.valueOf(newValue));
            return newValue;
        });
    }

    // ------------------- Hash Operations -------------------

    /**
     * Hash 필드의 값을 1 증가
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @return 증가 후 값
     */
    public static long incrHash(String key, String field) {
        return incrByHash(key, field, 1L);
    }

    /**
     * Hash 필드의 값을 지정한 수만큼 증가 (없으면 0으로 초기화 후 증가)
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @param val 증가할 값
     * @return 증가 후 값
     */
    public static long incrByHash(String key, String field, long val) {
        return withRedis(Redis.getString(), conn -> {
            String value = conn.sync().hget(key, field);
            if (CommonUtils.isNullOrSpace(value)) {
                conn.sync().hset(key, field, "0");
            }
            return conn.sync().hincrby(key, field, val);
        });
    }

    /**
     * Hash 필드의 값을 실수만큼 증가 (없으면 0으로 초기화 후 증가)
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @param val 증가할 값 (실수)
     * @return 증가 후 값
     */
    public static double incrByHash(String key, String field, double val) {
        return withRedis(Redis.getString(), conn -> {
            String value = conn.sync().hget(key, field);
            if (CommonUtils.isNullOrSpace(value)) {
                conn.sync().hset(key, field, "0");
            }
            return conn.sync().hincrbyfloat(key, field, val);
        });
    }

    /**
     * Hash 필드의 값을 1 감소
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @return 감소 후 값
     */
    public static long decrHash(String key, String field) {
        return decrByHash(key, field, 1L);
    }

    /**
     * Hash 필드의 값을 지정한 수만큼 감소
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @param val 감소할 값
     * @return 감소 후 값
     */
    public static long decrByHash(String key, String field, long val) {
        return withRedis(Redis.getString(), conn -> {
            long currentValue = CommonUtils.parseLong(conn.sync().hget(key, field));
            long newValue = currentValue - val;
            conn.sync().hset(key, field, String.valueOf(newValue));
            return newValue;
        });
    }

    /**
     * Hash 필드의 값을 실수만큼 감소
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @param val 감소할 값 (실수)
     * @return 감소 후 값
     */
    public static double decrByHash(String key, String field, double val) {
        return withRedis(Redis.getString(), conn -> {
            double currentValue = CommonUtils.parseDouble(conn.sync().hget(key, field));
            double newValue = currentValue - val;
            conn.sync().hset(key, field, String.valueOf(newValue));
            return newValue;
        });
    }

    /**
     * Hash에 필드-값 설정
     * 
     * @param key Redis 키
     * @param field Hash 필드명
     * @param val 저장할 값
     * @return 새로운 필드 추가 여부 (true: 새 필드, false: 기존 필드 업데이트)
     */
    public static boolean setHash(String key, String field, Object val) {
        StatefulRedisConnection<String, Object> conn = determineConnection(val);
        return withRedis(conn, c -> c.sync().hset(key, field, val));
    }

    /**
     * Hash에 필드-값 설정 (필드명이 숫자인 경우)
     * 
     * @param key Redis 키
     * @param field Hash 필드명 (숫자)
     * @param val 저장할 값
     * @return 새로운 필드 추가 여부
     */
    public static boolean setHash(String key, long field, Object val) {
        return setHash(key, String.valueOf(field), val);
    }

    /**
     * Hash에서 필드 값 조회
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param field Hash 필드명
     * @param clazz 반환 클래스 타입
     * @return 필드 값
     */
    public static <T> T getHash(String key, String field, Class<T> clazz) {
        StatefulRedisConnection<String, T> conn = determineConnectionByClass(clazz);
        return withRedis(conn, c -> clazz.cast(c.sync().hget(key, field)));
    }

    /**
     * Hash에서 필드 값 조회 (필드명이 숫자인 경우)
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param field Hash 필드명 (숫자)
     * @param clazz 반환 클래스 타입
     * @return 필드 값
     */
    public static <T> T getHash(String key, long field, Class<T> clazz) {
        return getHash(key, String.valueOf(field), clazz);
    }

    // ------------------- List Operations -------------------

    /**
     * List의 오른쪽(끝)에 값 추가 (동기)
     * 
     * @param key Redis 키
     * @param val 추가할 값
     * @return 추가 후 List의 길이
     */
    public static long rpush(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = determineConnection(val);
        return withRedis(conn, c -> c.sync().rpush(key, val));
    }

    /**
     * List의 오른쪽(끝)에 값 추가 (비동기)
     * 
     * @param key Redis 키
     * @param val 추가할 값
     */
    public static void rpushAsync(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = determineConnection(val);
        withRedisAsync(conn, key, val);
    }

    /**
     * List의 왼쪽(앞)에 값 추가 (동기)
     * 
     * @param key Redis 키
     * @param val 추가할 값
     * @return 추가 후 List의 길이
     */
    public static long lpush(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = determineConnection(val);
        return withRedis(conn, c -> c.sync().lpush(key, val));
    }

    /**
     * List의 왼쪽(앞)에 값 추가 (비동기)
     * 
     * @param key Redis 키
     * @param val 추가할 값
     */
    public static void lpushAsync(String key, Object val) {
        StatefulRedisConnection<String, Object> conn = determineConnection(val);
        withRedisAsync(conn, key, val);
    }

    /**
     * List의 오른쪽(끝)에서 값을 꺼내서 반환
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param clazz 반환 클래스 타입
     * @return 꺼낸 값
     */
    public static <T> T rpop(String key, Class<T> clazz) {
        StatefulRedisConnection<String, T> conn = determineConnectionByClass(clazz);
        return withRedis(conn, c -> clazz.cast(c.sync().rpop(key)));
    }
    
    
    /**
     * List의 오른쪽(끝)에서 값을 지정한 갯수 만큼 꺼내서 반환
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param clazz 반환 클래스 타입
     * @return 꺼낸 값
     */
    public static <T> T rpops(String key, Number size, Class<T> clazz) {
        StatefulRedisConnection<String, T> conn = determineConnectionByClass(clazz);
        return withRedis(conn, c -> clazz.cast(c.sync().rpop(key,CommonUtils.parseLong(size))));
    }

    /**
     * List의 왼쪽(앞)에서 값을 꺼내서 반환
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param clazz 반환 클래스 타입
     * @return 꺼낸 값
     */
    public static <T> T lpop(String key, Class<T> clazz) {
        StatefulRedisConnection<String, T> conn = determineConnectionByClass(clazz);
        return withRedis(conn, c -> clazz.cast(c.sync().lpop(key)));
    }
    
    /**
     * List의 왼쪽(앞)에서 값을 꺼내서 반환
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param clazz 반환 클래스 타입
     * @return 꺼낸 값
     */
    public static <T> T lpops(String key, Number size, Class<T> clazz) {
        StatefulRedisConnection<String, T> conn = determineConnectionByClass(clazz);
        return withRedis(conn, c -> clazz.cast(c.sync().lpop(key,CommonUtils.parseLong(size))));
    }

    /**
     * List의 길이 조회
     * 
     * @param key Redis 키
     * @return List의 길이
     */
    public static long llen(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().llen(key));
    }

    // ------------------- Generic get/set (with custom connection) -------------------

    /**
     * 커스텀 Connection을 사용한 값 조회
     * 
     * @param <V> 값 타입
     * @param key Redis 키
     * @param conn Redis 연결 객체
     * @return 조회된 값
     */
    public static <V> V get(String key, StatefulRedisConnection<String, V> conn) {
        return withRedis(conn, c -> c.sync().get(key));
    }

    /**
     * 커스텀 Connection을 사용한 값 저장
     * 
     * @param <V> 값 타입
     * @param key Redis 키
     * @param val 저장할 값
     * @param expire TTL (초), 0이면 영구 저장
     * @param conn Redis 연결 객체
     * @return "OK" (성공 시)
     */
    public static <V> String set(String key, V val, long expire, StatefulRedisConnection<String, V> conn) {
        return withRedis(conn, c -> {
            if (expire > 0) {
                return c.sync().setex(key, expire, val);
            } else {
                return c.sync().set(key, val);
            }
        });
    }

    // ------------------- Scan / Keys Operations -------------------

    /**
     * 패턴에 매칭되는 모든 키 조회 (KEYS 명령 사용)
     * 주의: 프로덕션에서는 성능 이슈로 scan() 메서드 권장
     * 
     * @param pattern 키 패턴 (예: "user:*")
     * @return 매칭되는 키 목록
     */
    public static List<String> keys(String pattern) {
        return withRedis(Redis.getString(), conn -> conn.sync().keys(pattern));
    }

    /**
     * Hash의 모든 필드명 조회
     * 
     * @param key Redis 키
     * @return 필드명 목록
     */
    public static List<String> keysHash(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().hkeys(key));
    }

    /**
     * 패턴에 매칭되는 키를 SCAN으로 조회 (기본 limit: 999,999)
     * 
     * @param key 키 패턴
     * @return 매칭되는 키 목록 (정렬됨)
     */
    public static List<String> scan(String key) {
        return scan(key, new ScanArgs().match(key).limit(999_999));
    }

    /**
     * 패턴에 매칭되는 키를 SCAN으로 조회 (커스텀 ScanArgs)
     * 
     * @param key 키 패턴
     * @param args SCAN 파라미터
     * @return 매칭되는 키 목록 (정렬됨)
     */
    public static List<String> scan(String key, ScanArgs args) {
        return withRedis(Redis.getString(), conn -> {
            KeyScanCursor<String> cursor = conn.sync().scan(args);
            List<String> list = cursor == null ? new ArrayList<>() : new ArrayList<>(cursor.getKeys());
            list.sort(String::compareTo);
            return list;
        });
    }

    // ---------------- String Operations ----------------
    
    /**
     * String 값 조회
     * 
     * @param key Redis 키
     * @return 조회된 값
     */
    public static String get(String key) {
        return get(key, 0);
    }

    /**
     * String 값 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 값
     */
    public static String get(String key, long expire) {
        return getValue(key, expire, Redis.getString());
    }

    /**
     * String 값 저장
     * 
     * @param key Redis 키
     * @param val 저장할 값
     * @return "OK" (성공 시)
     */
    public static String set(String key, String val) {
        return set(key, val, 0);
    }

    /**
     * String 값 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param val 저장할 값
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String set(String key, String val, long expire) {
        return setValue(key, val, expire, Redis.getString());
    }

    // ---------------- SharedMap Operations ----------------
    
    /**
     * SharedMap<String, Object> 값 조회
     * 
     * @param key Redis 키
     * @return 조회된 SharedMap
     */
    public static SharedMap<String, Object> getSharedMap(String key) {
        return getSharedMap(key, 0);
    }

    /**
     * SharedMap<String, Object> 값 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 SharedMap
     */
    public static SharedMap<String, Object> getSharedMap(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMap());
    }

    /**
     * SharedMap<String, Object> 값 저장
     * 
     * @param key Redis 키
     * @param val 저장할 SharedMap
     * @return "OK" (성공 시)
     */
    public static String set(String key, SharedMap<String, Object> val) {
        return set(key, val, 0);
    }

    /**
     * SharedMap<String, Object> 값 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param val 저장할 SharedMap
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String set(String key, SharedMap<String, Object> val, long expire) {
        return setValue(key, val, expire, Redis.getSharedMap());
    }

    // ---------------- SharedMap<String,String> Operations ----------------
    
    /**
     * SharedMap<String, String> 값 조회
     * 
     * @param key Redis 키
     * @return 조회된 SharedMap
     */
    public static SharedMap<String, String> getSharedMapString(String key) {
        return getSharedMapString(key, 0);
    }

    /**
     * SharedMap<String, String> 값 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 SharedMap
     */
    public static SharedMap<String, String> getSharedMapString(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapString());
    }

    /**
     * SharedMap<String, String> 값 저장
     * 
     * @param key Redis 키
     * @param val 저장할 SharedMap
     * @return "OK" (성공 시)
     */
    public static String setString(String key, SharedMap<String, String> val) {
        return setString(key, val, 0);
    }

    /**
     * SharedMap<String, String> 값 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param val 저장할 SharedMap
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setString(String key, SharedMap<String, String> val, long expire) {
        return setValue(key, val, expire, Redis.getSharedMapString());
    }

    // ---------------- SharedMap<String,Object> List Operations ----------------
    
    /**
     * SharedMap<String, Object> 리스트 조회
     * 
     * @param key Redis 키
     * @return 조회된 SharedMap 리스트
     */
    public static List<SharedMap<String, Object>> getSharedMapList(String key) {
        return getSharedMapList(key, 0);
    }

    /**
     * SharedMap<String, Object> 리스트 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 SharedMap 리스트
     */
    public static List<SharedMap<String, Object>> getSharedMapList(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapList());
    }

    /**
     * SharedMap<String, Object> 리스트 저장
     * 
     * @param key Redis 키
     * @param vals 저장할 SharedMap 리스트
     * @return "OK" (성공 시)
     */
    public static String setSharedMapList(String key, List<SharedMap<String, Object>> vals) {
        return setSharedMapList(key, vals, 0);
    }

    /**
     * SharedMap<String, Object> 리스트 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param vals 저장할 SharedMap 리스트
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setSharedMapList(String key, List<SharedMap<String, Object>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getSharedMapList());
    }

    // ---------------- SharedMap<String,String> List Operations ----------------
    
    /**
     * SharedMap<String, String> 리스트 조회
     * 
     * @param key Redis 키
     * @return 조회된 SharedMap 리스트
     */
    public static List<SharedMap<String, String>> getSharedMapListString(String key) {
        return getSharedMapListString(key, 0);
    }

    /**
     * SharedMap<String, String> 리스트 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 SharedMap 리스트
     */
    public static List<SharedMap<String, String>> getSharedMapListString(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapListString());
    }

    /**
     * SharedMap<String, String> 리스트 저장
     * 
     * @param key Redis 키
     * @param vals 저장할 SharedMap 리스트
     * @return "OK" (성공 시)
     */
    public static String setShared(String key, List<SharedMap<String, String>> vals) {
        return setShared(key, vals, 0);
    }

    /**
     * SharedMap<String, String> 리스트 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param vals 저장할 SharedMap 리스트
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setShared(String key, List<SharedMap<String, String>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getSharedMapListString());
    }

    // ---------------- LinkedMap Operations ----------------
    
    /**
     * LinkedMap<String, Object> 값 조회
     * 
     * @param key Redis 키
     * @return 조회된 LinkedMap
     */
    public static LinkedMap<String, Object> getLinkedMap(String key) {
        return getLinkedMap(key, 0);
    }

    /**
     * LinkedMap<String, Object> 값 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 LinkedMap
     */
    public static LinkedMap<String, Object> getLinkedMap(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMap());
    }

    /**
     * LinkedMap<String, Object> 값 저장
     * 
     * @param key Redis 키
     * @param val 저장할 LinkedMap
     * @return "OK" (성공 시)
     */
    public static String setLinkedMap(String key, LinkedMap<String, Object> val) {
        return setLinkedMap(key, val, 0);
    }

    /**
     * LinkedMap<String, Object> 값 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param val 저장할 LinkedMap
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setLinkedMap(String key, LinkedMap<String, Object> val, long expire) {
        return setValue(key, val, expire, Redis.getLinkedMap());
    }

    // ---------------- LinkedMap<String,String> Operations ----------------
    
    /**
     * LinkedMap<String, String> 값 조회
     * 
     * @param key Redis 키
     * @return 조회된 LinkedMap
     */
    public static LinkedMap<String, String> getLinkedMapString(String key) {
        return getLinkedMapString(key, 0);
    }

    /**
     * LinkedMap<String, String> 값 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 LinkedMap
     */
    public static LinkedMap<String, String> getLinkedMapString(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMapString());
    }

    /**
     * LinkedMap<String, String> 값 저장
     * 
     * @param key Redis 키
     * @param val 저장할 LinkedMap
     * @return "OK" (성공 시)
     */
    public static String set(String key, LinkedMap<String, String> val) {
        return set(key, val, 0);
    }

    /**
     * LinkedMap<String, String> 값 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param val 저장할 LinkedMap
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String set(String key, LinkedMap<String, String> val, long expire) {
        return setValue(key, val, expire, Redis.getLinkedMapString());
    }

    // ---------------- LinkedMap<String,Object> List Operations ----------------
    
    /**
     * LinkedMap<String, Object> 리스트 조회
     * 
     * @param key Redis 키
     * @return 조회된 LinkedMap 리스트
     */
    public static List<LinkedMap<String, Object>> getLinkedMapList(String key) {
        return getLinkedMapList(key, 0);
    }

    /**
     * LinkedMap<String, Object> 리스트 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 LinkedMap 리스트
     */
    public static List<LinkedMap<String, Object>> getLinkedMapList(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMapList());
    }

    /**
     * LinkedMap<String, Object> 리스트 저장
     * 
     * @param key Redis 키
     * @param vals 저장할 LinkedMap 리스트
     * @return "OK" (성공 시)
     */
    public static String setLinkedMapList(String key, List<LinkedMap<String, Object>> vals) {
        return setLinkedMapList(key, vals, 0);
    }

    /**
     * LinkedMap<String, Object> 리스트 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param vals 저장할 LinkedMap 리스트
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setLinkedMapList(String key, List<LinkedMap<String, Object>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getLinkedMapList());
    }

    // ---------------- LinkedMap<String,String> List Operations ----------------
    
    /**
     * LinkedMap<String, String> 리스트 조회
     * 
     * @param key Redis 키
     * @return 조회된 LinkedMap 리스트
     */
    public static List<LinkedMap<String, String>> getLinkedMapListString(String key) {
        return getLinkedMapListString(key, 0);
    }

    /**
     * LinkedMap<String, String> 리스트 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 LinkedMap 리스트
     */
    public static List<LinkedMap<String, String>> getLinkedMapListString(String key, long expire) {
        return getValue(key, expire, Redis.getLinkedMapListString());
    }

    /**
     * LinkedMap<String, String> 리스트 저장
     * 
     * @param key Redis 키
     * @param vals 저장할 LinkedMap 리스트
     * @return "OK" (성공 시)
     */
    public static String setLinkedMapListString(String key, List<LinkedMap<String, String>> vals) {
        return setLinkedMapListString(key, vals, 0);
    }

    /**
     * LinkedMap<String, String> 리스트 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param vals 저장할 LinkedMap 리스트
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setLinkedMapListString(String key, List<LinkedMap<String, String>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getLinkedMapListString());
    }

    // ---------------- ThreadSafeLinkedMap Operations ----------------
    
    /**
     * ThreadSafeLinkedMap<String, Object> 값 조회
     * 
     * @param key Redis 키
     * @return 조회된 ThreadSafeLinkedMap
     */
    public static ThreadSafeLinkedMap<String, Object> getThreadSafeLinkedMap(String key) {
        return getThreadSafeLinkedMap(key, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, Object> 값 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 ThreadSafeLinkedMap
     */
    public static ThreadSafeLinkedMap<String, Object> getThreadSafeLinkedMap(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMap());
    }

    /**
     * ThreadSafeLinkedMap<String, Object> 값 저장
     * 
     * @param key Redis 키
     * @param val 저장할 ThreadSafeLinkedMap
     * @return "OK" (성공 시)
     */
    public static String setThreadSafeLinkedMap(String key, ThreadSafeLinkedMap<String, Object> val) {
        return setThreadSafeLinkedMap(key, val, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, Object> 값 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param val 저장할 ThreadSafeLinkedMap
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setThreadSafeLinkedMap(String key, ThreadSafeLinkedMap<String, Object> val, long expire) {
        return setValue(key, val, expire, Redis.getThreadSafeLinkedMap());
    }

    // ---------------- ThreadSafeLinkedMap<String,String> Operations ----------------
    
    /**
     * ThreadSafeLinkedMap<String, String> 값 조회
     * 
     * @param key Redis 키
     * @return 조회된 ThreadSafeLinkedMap
     */
    public static ThreadSafeLinkedMap<String, String> getThreadSafeLinkedMapString(String key) {
        return getThreadSafeLinkedMapString(key, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, String> 값 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 ThreadSafeLinkedMap
     */
    public static ThreadSafeLinkedMap<String, String> getThreadSafeLinkedMapString(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMapString());
    }

    /**
     * ThreadSafeLinkedMap<String, String> 값 저장
     * 
     * @param key Redis 키
     * @param val 저장할 ThreadSafeLinkedMap
     * @return "OK" (성공 시)
     */
    public static String set(String key, ThreadSafeLinkedMap<String, String> val) {
        return set(key, val, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, String> 값 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param val 저장할 ThreadSafeLinkedMap
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String set(String key, ThreadSafeLinkedMap<String, String> val, long expire) {
        return setValue(key, val, expire, Redis.getThreadSafeLinkedMapString());
    }

    // ---------------- ThreadSafeLinkedMap<String,Object> List Operations ----------------
    
    /**
     * ThreadSafeLinkedMap<String, Object> 리스트 조회
     * 
     * @param key Redis 키
     * @return 조회된 ThreadSafeLinkedMap 리스트
     */
    public static List<ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMapList(String key) {
        return getThreadSafeLinkedMapList(key, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, Object> 리스트 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 ThreadSafeLinkedMap 리스트
     */
    public static List<ThreadSafeLinkedMap<String, Object>> getThreadSafeLinkedMapList(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMapList());
    }

    /**
     * ThreadSafeLinkedMap<String, Object> 리스트 저장
     * 
     * @param key Redis 키
     * @param vals 저장할 ThreadSafeLinkedMap 리스트
     * @return "OK" (성공 시)
     */
    public static String setThreadSafeLinkedList(String key, List<ThreadSafeLinkedMap<String, Object>> vals) {
        return setThreadSafeLinkedList(key, vals, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, Object> 리스트 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param vals 저장할 ThreadSafeLinkedMap 리스트
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setThreadSafeLinkedList(String key, List<ThreadSafeLinkedMap<String, Object>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getThreadSafeLinkedMapList());
    }

    // ---------------- ThreadSafeLinkedMap<String,String> List Operations ----------------
    
    /**
     * ThreadSafeLinkedMap<String, String> 리스트 조회
     * 
     * @param key Redis 키
     * @return 조회된 ThreadSafeLinkedMap 리스트
     */
    public static List<ThreadSafeLinkedMap<String, String>> getThreadSafeLinkedMapListString(String key) {
        return getThreadSafeLinkedMapListString(key, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, String> 리스트 조회 및 TTL 갱신
     * 
     * @param key Redis 키
     * @param expire TTL (초), 0이면 갱신하지 않음
     * @return 조회된 ThreadSafeLinkedMap 리스트
     */
    public static List<ThreadSafeLinkedMap<String, String>> getThreadSafeLinkedMapListString(String key, long expire) {
        return getValue(key, expire, Redis.getThreadSafeLinkedMapListString());
    }

    /**
     * ThreadSafeLinkedMap<String, String> 리스트 저장
     * 
     * @param key Redis 키
     * @param vals 저장할 ThreadSafeLinkedMap 리스트
     * @return "OK" (성공 시)
     */
    public static String setThreadSafeLinkedListString(String key, List<ThreadSafeLinkedMap<String, String>> vals) {
        return setThreadSafeLinkedListString(key, vals, 0);
    }

    /**
     * ThreadSafeLinkedMap<String, String> 리스트 저장 및 TTL 설정
     * 
     * @param key Redis 키
     * @param vals 저장할 ThreadSafeLinkedMap 리스트
     * @param expire TTL (초), 0이면 영구 저장
     * @return "OK" (성공 시)
     */
    public static String setThreadSafeLinkedListString(String key, List<ThreadSafeLinkedMap<String, String>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getThreadSafeLinkedMapListString());
    }

    // ------------------- Private Helper Methods -------------------

    /**
     * Redis에서 값을 조회하고, expire가 지정되면 TTL을 갱신합니다.
     * 
     * @param <T> 값 타입
     * @param key Redis 키
     * @param expire TTL (초 단위, 0이면 갱신하지 않음)
     * @param conn Redis 연결 (Singleton, Thread-Safe)
     * @return 조회된 값 (없으면 null)
     */
    private static <T> T getValue(String key, long expire, StatefulRedisConnection<String, T> conn) {
        return withRedis(conn, c -> {
            T value = c.sync().get(key);
            
            // 값이 존재하고 expire가 지정된 경우 TTL 갱신
            if (value != null && expire > 0) {
                c.sync().expire(key, expire);
            }
            
            return value;
        });
    }

    /**
     * Redis에 값을 저장하고, expire가 지정되면 TTL을 설정합니다.
     * 
     * @param <T> 값 타입
     * @param key Redis 키
     * @param val 저장할 값
     * @param expire TTL (초 단위, 0이면 영구 저장)
     * @param conn Redis 연결 (Singleton, Thread-Safe)
     * @return "OK" (성공 시)
     */
    private static <T> String setValue(String key, T val, long expire, StatefulRedisConnection<String, T> conn) {
        return withRedis(conn, c -> {
            if (expire > 0) {
                return c.sync().setex(key, expire, val);
            } else {
                return c.sync().set(key, val);
            }
        });
    }

    // ------------------- Cache Pattern Helper -------------------

    /**
     * Cache-Aside Pattern: Redis 캐시 조회 후 없으면 DB에서 조회하여 캐싱
     * 
     * <p>동작 순서:</p>
     * <ol>
     *   <li>Redis 캐시에서 데이터 확인</li>
     *   <li>캐시 미스 시 DB에서 조회</li>
     *   <li>조회된 데이터를 Redis에 저장</li>
     * </ol>
     * 
     * @param key Redis 키
     * @param retrieve DB 조회 객체
     * @return 조회된 데이터 (단일 행)
     */
    public static SharedMap<String, Object> fetchRow(String key, Retrieve retrieve) {
        return fetchRow(key, retrieve, 0);
    }

    /**
     * Cache-Aside Pattern with TTL
     * 
     * @param key Redis 키
     * @param retrieve DB 조회 객체
     * @param expire TTL (초 단위, 0이면 영구 저장)
     * @return 조회된 데이터 (단일 행)
     */
    public static SharedMap<String, Object> fetchRow(String key, Retrieve retrieve, long expire) {
        // 1. 캐시 확인
        if (exist(key) > 0) {
            return getSharedMap(key, expire); // TTL 갱신
        }
        
        // 2. DB 조회
        SharedMap<String, Object> map = retrieve.select().getRowFirst();
        
        // 3. 캐시 저장
        if (map != null && !map.isEmpty()) {
            set(key, map, expire);
        }
        
        return map;
    }

    /**
     * Cache-Aside Pattern for List
     * 
     * @param key Redis 키
     * @param retrieve DB 조회 객체
     * @return 조회된 데이터 (다중 행)
     */
    public static List<SharedMap<String, Object>> fetchRows(String key, Retrieve retrieve) {
        return fetchRows(key, retrieve, 0);
    }

    /**
     * Cache-Aside Pattern for List with TTL
     * 
     * @param key Redis 키
     * @param retrieve DB 조회 객체
     * @param expire TTL (초 단위, 0이면 영구 저장)
     * @return 조회된 데이터 (다중 행)
     */
    public static List<SharedMap<String, Object>> fetchRows(String key, Retrieve retrieve, long expire) {
        // 1. 캐시 확인
        if (exist(key) > 0) {
            return getSharedMapList(key, expire); // TTL 갱신
        }
        
        // 2. DB 조회
        List<SharedMap<String, Object>> list = retrieve.select().getRows();
        
        // 3. 캐시 저장
        if (list != null && !list.isEmpty()) {
            setSharedMapList(key, list, expire);
        }
        
        return list;
    }
    
    
    
    
    
 // ================= SET (집합) 연산 =================

    /**
     * Set에 하나 이상의 멤버 추가
     * 
     * @param key Redis 키
     * @param members 추가할 멤버들
     * @return 추가된 멤버 개수
     */
    public static long sadd(String key, String... members) {
        return withRedis(Redis.getString(), conn -> conn.sync().sadd(key, members));
    }

    /**
     * Set에서 하나 이상의 멤버 제거
     * 
     * @param key Redis 키
     * @param members 제거할 멤버들
     * @return 제거된 멤버 개수
     */
    public static long srem(String key, String... members) {
        return withRedis(Redis.getString(), conn -> conn.sync().srem(key, members));
    }

    /**
     * Set의 모든 멤버 조회
     * 
     * @param key Redis 키
     * @return 멤버 Set
     */
    public static Set<String> smembers(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().smembers(key));
    }

    /**
     * Set에 멤버가 존재하는지 확인
     * 
     * @param key Redis 키
     * @param member 확인할 멤버
     * @return 존재 여부
     */
    public static boolean sismember(String key, String member) {
        return withRedis(Redis.getString(), conn -> conn.sync().sismember(key, member));
    }

    /**
     * Set의 크기(멤버 개수) 조회
     * 
     * @param key Redis 키
     * @return Set 크기
     */
    public static long scard(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().scard(key));
    }

    /**
     * 여러 Set의 합집합
     * 
     * @param keys Set 키들
     * @return 합집합 Set
     */
    public static Set<String> sunion(String... keys) {
        return withRedis(Redis.getString(), conn -> conn.sync().sunion(keys));
    }

    /**
     * 여러 Set의 교집합
     * 
     * @param keys Set 키들
     * @return 교집합 Set
     */
    public static Set<String> sinter(String... keys) {
        return withRedis(Redis.getString(), conn -> conn.sync().sinter(keys));
    }

    /**
     * Set 간의 차집합
     * 
     * @param keys Set 키들 (첫 번째 Set에서 나머지 Set들을 뺌)
     * @return 차집합 Set
     */
    public static Set<String> sdiff(String... keys) {
        return withRedis(Redis.getString(), conn -> conn.sync().sdiff(keys));
    }

    // ================= SORTED SET (정렬된 집합) 연산 =================

    /**
     * Sorted Set에 score와 함께 멤버 추가
     * 
     * @param key Redis 키
     * @param score 점수
     * @param member 멤버
     * @return 추가된 멤버 개수
     */
    public static long zadd(String key, double score, String member) {
        return withRedis(Redis.getString(), conn -> conn.sync().zadd(key, score, member));
    }

    /**
     * Sorted Set에서 멤버 제거
     * 
     * @param key Redis 키
     * @param members 제거할 멤버들
     * @return 제거된 멤버 개수
     */
    public static long zrem(String key, String... members) {
        return withRedis(Redis.getString(), conn -> conn.sync().zrem(key, members));
    }

    /**
     * 인덱스 범위로 멤버 조회 (오름차순)
     * 
     * @param key Redis 키
     * @param start 시작 인덱스
     * @param stop 종료 인덱스
     * @return 멤버 리스트
     */
    public static List<String> zrange(String key, long start, long stop) {
        return withRedis(Redis.getString(), conn -> conn.sync().zrange(key, start, stop));
    }

    /**
     * 인덱스 범위로 멤버 조회 (내림차순)
     * 
     * @param key Redis 키
     * @param start 시작 인덱스
     * @param stop 종료 인덱스
     * @return 멤버 리스트
     */
    public static List<String> zrevrange(String key, long start, long stop) {
        return withRedis(Redis.getString(), conn -> conn.sync().zrevrange(key, start, stop));
    }

    /**
     * score 범위로 멤버 조회
     * 
     * @param key Redis 키
     * @param min 최소 score
     * @param max 최대 score
     * @return 멤버 리스트
     */
    public static List<String> zrangebyscore(String key, double min, double max) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().zrangebyscore(key, Range.create(min, max)));
    }

    /**
     * 멤버의 순위 조회 (오름차순, 0부터 시작)
     * 
     * @param key Redis 키
     * @param member 멤버
     * @return 순위 (없으면 null)
     */
    public static Long zrank(String key, String member) {
        return withRedis(Redis.getString(), conn -> conn.sync().zrank(key, member));
    }

    /**
     * 멤버의 score 조회
     * 
     * @param key Redis 키
     * @param member 멤버
     * @return score (없으면 null)
     */
    public static Double zscore(String key, String member) {
        return withRedis(Redis.getString(), conn -> conn.sync().zscore(key, member));
    }

    /**
     * Sorted Set의 크기(멤버 개수) 조회
     * 
     * @param key Redis 키
     * @return Sorted Set 크기
     */
    public static long zcard(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().zcard(key));
    }

    /**
     * 멤버의 score 증가
     * 
     * @param key Redis 키
     * @param increment 증가할 값
     * @param member 멤버
     * @return 증가 후 score
     */
    public static double zincrby(String key, double increment, String member) {
        return withRedis(Redis.getString(), conn -> conn.sync().zincrby(key, increment, member));
    }

    // ================= LIST 추가 연산 =================

    /**
     * List의 범위 조회
     * 
     * @param key Redis 키
     * @param start 시작 인덱스
     * @param stop 종료 인덱스
     * @return 범위 내 값 리스트
     */
    public static List<String> lrange(String key, long start, long stop) {
        return withRedis(Redis.getString(), conn -> conn.sync().lrange(key, start, stop));
    }

    /**
     * 특정 인덱스의 값 조회
     * 
     * @param key Redis 키
     * @param index 인덱스
     * @return 해당 인덱스의 값
     */
    public static String lindex(String key, long index) {
        return withRedis(Redis.getString(), conn -> conn.sync().lindex(key, index));
    }

    /**
     * 특정 인덱스의 값 수정
     * 
     * @param key Redis 키
     * @param index 인덱스
     * @param value 새 값
     * @return "OK" (성공 시)
     */
    public static String lset(String key, long index, String value) {
        return withRedis(Redis.getString(), conn -> conn.sync().lset(key, index, value));
    }

    /**
     * List에서 값 제거
     * 
     * @param key Redis 키
     * @param count 제거할 개수 (0: 모두, 양수: 앞에서부터, 음수: 뒤에서부터)
     * @param value 제거할 값
     * @return 제거된 개수
     */
    public static long lrem(String key, long count, String value) {
        return withRedis(Redis.getString(), conn -> conn.sync().lrem(key, count, value));
    }

    /**
     * List를 특정 범위로 자르기
     * 
     * @param key Redis 키
     * @param start 시작 인덱스
     * @param stop 종료 인덱스
     * @return "OK" (성공 시)
     */
    public static String ltrim(String key, long start, long stop) {
        return withRedis(Redis.getString(), conn -> conn.sync().ltrim(key, start, stop));
    }

    // ================= HASH 추가 연산 =================

    /**
     * Hash의 모든 필드와 값 조회
     * 
     * @param key Redis 키
     * @return 필드-값 Map
     */
    public static Map<String, String> hgetall(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().hgetall(key));
    }

    /**
     * Hash의 모든 값만 조회
     * 
     * @param key Redis 키
     * @return 값 리스트
     */
    public static List<String> hvals(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().hvals(key));
    }

    /**
     * Hash의 필드 개수 조회
     * 
     * @param key Redis 키
     * @return 필드 개수
     */
    public static long hlen(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().hlen(key));
    }

    /**
     * Hash 필드 삭제
     * 
     * @param key Redis 키
     * @param fields 삭제할 필드들
     * @return 삭제된 필드 개수
     */
    public static long hdel(String key, String... fields) {
        return withRedis(Redis.getString(), conn -> conn.sync().hdel(key, fields));
    }

    /**
     * 여러 필드 한번에 조회
     * 
     * @param key Redis 키
     * @param fields 조회할 필드들
     * @return 필드 값 리스트
     */
    public static List<KeyValue<String, String>> hmget(String key, String... fields) {
        return withRedis(Redis.getString(), conn -> conn.sync().hmget(key, fields));
    }

    /**
     * 여러 필드 한번에 설정
     * 
     * @param key Redis 키
     * @param map 필드-값 Map
     * @return "OK" (성공 시)
     */
    public static String hmset(String key, Map<String, String> map) {
        return withRedis(Redis.getString(), conn -> conn.sync().hmset(key, map));
    }

    // ================= BATCH 작업 =================

    /**
     * 여러 키 한번에 조회
     * 
     * @param keys 조회할 키들
     * @return 키-값 리스트
     */
    public static List<KeyValue<String, String>> mget(String... keys) {
        return withRedis(Redis.getString(), conn -> conn.sync().mget(keys));
    }

    /**
     * 여러 키 한번에 설정
     * 
     * @param map 키-값 Map
     * @return "OK" (성공 시)
     */
    public static String mset(Map<String, String> map) {
        return withRedis(Redis.getString(), conn -> conn.sync().mset(map));
    }

    /**
     * 모든 키가 없을 때만 여러 키 설정
     * 
     * @param map 키-값 Map
     * @return 설정 성공 여부
     */
    public static boolean msetnx(Map<String, String> map) {
        return withRedis(Redis.getString(), conn -> conn.sync().msetnx(map));
    }

    /**
     * 대량 삭제 (청크 단위)
     * 
     * @param keys 삭제할 키들
     * @param chunkSize 청크 크기
     * @return 삭제된 총 키 개수
     */
    public static long batchDelete(List<String> keys, int chunkSize) {
        if (CommonUtils.isEmpty(keys)) {
            return 0;
        }

        long totalDeleted = 0;
        List<String> chunk = new ArrayList<>();

        for (String key : keys) {
            chunk.add(key);
            if (chunk.size() >= chunkSize) {
                totalDeleted += RedisUtils.del(chunk.toArray(new String[0]));
                chunk.clear();
            }
        }

        // 남은 키 처리
        if (!chunk.isEmpty()) {
            totalDeleted += RedisUtils.del(chunk.toArray(new String[0]));
        }

        logger.debug("batchDelete: deleted {} keys", totalDeleted);
        return totalDeleted;
    }

    // ================= BIT 연산 =================

    /**
     * 비트 설정
     * 
     * @param key Redis 키
     * @param offset 오프셋
     * @param value 비트 값 (0 또는 1)
     * @return 이전 비트 값
     */
    public static long setbit(String key, long offset, int value) {
        return withRedis(Redis.getString(), conn -> conn.sync().setbit(key, offset, value));
    }

    /**
     * 비트 조회
     * 
     * @param key Redis 키
     * @param offset 오프셋
     * @return 비트 값 (0 또는 1)
     */
    public static long getbit(String key, long offset) {
        return withRedis(Redis.getString(), conn -> conn.sync().getbit(key, offset));
    }

    /**
     * 1인 비트 개수
     * 
     * @param key Redis 키
     * @return 비트 카운트
     */
    public static long bitcount(String key) {
        return withRedis(Redis.getString(), conn -> conn.sync().bitcount(key));
    }

    // ================= HYPERLOGLOG (카디널리티 추정) =================

    /**
     * HyperLogLog에 요소 추가
     * 
     * @param key Redis 키
     * @param values 추가할 값들
     * @return 추가 여부 (HyperLogLog 변경되면 1)
     */
    public static long pfadd(String key, String... values) {
        return withRedis(Redis.getString(), conn -> conn.sync().pfadd(key, values));
    }

    /**
     * HyperLogLog 카디널리티 추정
     * 
     * @param keys HyperLogLog 키들
     * @return 추정된 유니크 요소 개수
     */
    public static long pfcount(String... keys) {
        return withRedis(Redis.getString(), conn -> conn.sync().pfcount(keys));
    }

    /**
     * 여러 HyperLogLog 병합
     * 
     * @param destkey 목적지 키
     * @param sourcekeys 소스 키들
     * @return "OK" (성공 시)
     */
    public static String pfmerge(String destkey, String... sourcekeys) {
        return withRedis(Redis.getString(), conn -> conn.sync().pfmerge(destkey, sourcekeys));
    }

    // ================= 분산 락 패턴 =================

    /**
     * 분산 락 획득 시도
     * 
     * @param lockKey 락 키
     * @param requestId 요청 ID (락 소유자 식별)
     * @param expireTime 락 만료 시간 (초)
     * @return 락 획득 성공 여부
     */
    public static boolean tryLock(String lockKey, String requestId, long expireTime) {
        return withRedis(Redis.getString(), conn -> {
            String result = conn.sync().set(lockKey, requestId, 
                io.lettuce.core.SetArgs.Builder.nx().ex(expireTime));
            return "OK".equals(result);
        });
    }

    /**
     * 분산 락 획득 시도 (기본 TTL 사용)
     * 
     * @param lockKey 락 키
     * @param requestId 요청 ID
     * @return 락 획득 성공 여부
     */
    public static boolean tryLock(String lockKey, String requestId) {
        return tryLock(lockKey, requestId, DEFAULT_LOCK_TTL);
    }

    /**
     * 분산 락 해제
     * 
     * @param lockKey 락 키
     * @param requestId 요청 ID (락 소유자만 해제 가능)
     * @return 락 해제 성공 여부
     */
    public static boolean unlock(String lockKey, String requestId) {
        return withRedis(Redis.getString(), conn -> {
            // Lua 스크립트로 원자적 해제 (소유자 확인 후 삭제)
            String script = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('del', KEYS[1]) " +
                "else " +
                "    return 0 " +
                "end";
            
            Long result = conn.sync().eval(script, 
                io.lettuce.core.ScriptOutputType.INTEGER, 
                new String[]{lockKey}, requestId);
            
            return result != null && result == 1L;
        });
    }

    /**
     * 락 갱신 (TTL 연장)
     * 
     * @param lockKey 락 키
     * @param requestId 요청 ID
     * @param expireTime 새로운 만료 시간 (초)
     * @return 갱신 성공 여부
     */
    public static boolean renewLock(String lockKey, String requestId, long expireTime) {
        return withRedis(Redis.getString(), conn -> {
            String script = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    return redis.call('expire', KEYS[1], ARGV[2]) " +
                "else " +
                "    return 0 " +
                "end";
            
            Long result = conn.sync().eval(script, 
                io.lettuce.core.ScriptOutputType.INTEGER, 
                new String[]{lockKey}, requestId, String.valueOf(expireTime));
            
            return result != null && result == 1L;
        });
    }

    

    // ================= LEADERBOARD (순위표) =================

    /**
     * 리더보드에 점수 추가/업데이트
     * 
     * @param leaderboardKey 리더보드 키
     * @param userId 사용자 ID
     * @param score 점수
     * @return 추가/업데이트 성공 여부
     */
    public static boolean addScore(String leaderboardKey, String userId, double score) {
        long result = zadd(leaderboardKey, score, userId);
        return result >= 0;
    }

    /**
     * 상위 N명 조회 (내림차순)
     * 
     * @param leaderboardKey 리더보드 키
     * @param topN 조회할 인원 수
     * @return 상위 N명의 사용자 ID 리스트
     */
    public static List<String> getTopN(String leaderboardKey, int topN) {
        return zrevrange(leaderboardKey, 0, topN - 1);
    }

    /**
     * 상위 N명 조회 (점수 포함)
     * 
     * @param leaderboardKey 리더보드 키
     * @param topN 조회할 인원 수
     * @return 상위 N명의 사용자 ID와 점수
     */
    public static List<ScoredValue<String>> getTopNWithScores(String leaderboardKey, int topN) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().zrevrangeWithScores(leaderboardKey, 0, topN - 1));
    }

    /**
     * 사용자 순위 조회 (1부터 시작)
     * 
     * @param leaderboardKey 리더보드 키
     * @param userId 사용자 ID
     * @return 순위 (없으면 null)
     */
    public static Long getUserRank(String leaderboardKey, String userId) {
        return withRedis(Redis.getString(), conn -> {
            Long rank = conn.sync().zrevrank(leaderboardKey, userId);
            return rank != null ? rank + 1 : null;
        });
    }

    /**
     * 점수 범위로 사용자 조회
     * 
     * @param leaderboardKey 리더보드 키
     * @param minScore 최소 점수
     * @param maxScore 최대 점수
     * @return 범위 내 사용자 리스트
     */
    public static List<String> getScoreRange(String leaderboardKey, double minScore, double maxScore) {
        return zrangebyscore(leaderboardKey, minScore, maxScore);
    }

    // ================= 고급 캐시 패턴 =================

    /**
     * 캐시에 없으면 계산 후 저장 (Cache-Aside Pattern)
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param supplier 값 계산 함수
     * @param expire TTL (초)
     * @param conn Redis 연결
     * @return 조회/계산된 값
     */
    public static <T> T getOrCompute(String key, Supplier<T> supplier, long expire, 
                                      StatefulRedisConnection<String, T> conn) {
        return withRedis(conn, c -> {
            // 1. 캐시 확인
            T value = c.sync().get(key);
            
            if (value != null) {
                // 캐시 히트 - TTL 갱신
                if (expire > 0) {
                    c.sync().expire(key, expire);
                }
                return value;
            }
            
            // 2. 캐시 미스 - 값 계산
            T computed = supplier.get();
            
            // 3. 캐시 저장
            if (computed != null) {
                if (expire > 0) {
                    c.sync().setex(key, expire, computed);
                } else {
                    c.sync().set(key, computed);
                }
            }
            
            return computed;
        });
    }

    /**
     * 캐시에 없을 때만 계산 후 저장
     * 
     * @param key Redis 키
     * @param supplier 값 계산 함수
     * @param expire TTL (초)
     * @return 조회/계산된 값
     */
    public static String computeIfAbsent(String key, Supplier<String> supplier, long expire) {
        return getOrCompute(key, supplier, expire, Redis.getString());
    }

    /**
     * 캐시 조회 실패 시 폴백 값 반환
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param fallback 폴백 값
     * @param conn Redis 연결
     * @return 캐시 값 또는 폴백 값
     */
    public static <T> T getWithFallback(String key, T fallback, StatefulRedisConnection<String, T> conn) {
        return withRedis(conn, c -> {
            T value = c.sync().get(key);
            return value != null ? value : fallback;
        });
    }

    /**
     * 캐시 강제 갱신
     * 
     * @param <T> 값 타입
     * @param key Redis 키
     * @param supplier 새 값 계산 함수
     * @param expire TTL (초)
     * @param conn Redis 연결
     * @return 갱신된 값
     */
    public static <T> T refreshCache(String key, Supplier<T> supplier, long expire, 
                                      StatefulRedisConnection<String, T> conn) {
        return withRedis(conn, c -> {
            T value = supplier.get();
            
            if (value != null) {
                if (expire > 0) {
                    c.sync().setex(key, expire, value);
                } else {
                    c.sync().set(key, value);
                }
            }
            
            return value;
        });
    }

    /**
     * 캐시 예열 (Cache Warming)
     * 
     * @param keys 예열할 키-값 Map
     * @param expire TTL (초)
     */
    public static void cacheWarming(Map<String, String> keys, long expire) {
        if (CommonUtils.isEmpty(keys)) {
            return;
        }

        withRedis(Redis.getString(), conn -> {
            keys.forEach((key, value) -> {
                if (expire > 0) {
                    conn.sync().setex(key, expire, value);
                } else {
                    conn.sync().set(key, value);
                }
            });
            logger.info("Cache warming completed for {} keys", keys.size());
            return null;
        });
    }

    

    // ================= GEO (지리정보) 연산 =================

    /**
     * 위치 정보 추가
     * 
     * @param key Redis 키
     * @param longitude 경도
     * @param latitude 위도
     * @param member 멤버명
     * @return 추가된 멤버 개수
     */
    public static long geoadd(String key, double longitude, double latitude, String member) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().geoadd(key, longitude, latitude, member));
    }

    /**
     * 두 지점 간 거리 계산
     * 
     * @param key Redis 키
     * @param from 시작 지점
     * @param to 도착 지점
     * @param unit 거리 단위 (m, km, mi, ft)
     * @return 거리
     */
    public static Double geodist(String key, String from, String to, GeoArgs.Unit unit) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().geodist(key, from, to, unit));
    }

    /**
     * 반경 내 위치 검색
     * 
     * @param key Redis 키
     * @param longitude 중심점 경도
     * @param latitude 중심점 위도
     * @param distance 반경
     * @param unit 거리 단위
     * @return 반경 내 위치 리스트
     */
    public static Set<String> georadius(String key, double longitude, double latitude, 
                                        double distance, GeoArgs.Unit unit) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().georadius(key, longitude, latitude, distance, unit));
    }

    /**
     * 위치 좌표 조회
     * 
     * @param key Redis 키
     * @param members 조회할 멤버들
     * @return 좌표 리스트
     */
    public static List<io.lettuce.core.GeoCoordinates> geopos(String key, String... members) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().geopos(key, members));
    }

    // ================= STREAM 연산 =================

    /**
     * 스트림에 메시지 추가
     * 
     * @param key 스트림 키
     * @param body 메시지 내용
     * @return 메시지 ID
     */
    public static String xadd(String key, Map<String, String> body) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().xadd(key, body));
    }

    /**
     * 스트림에 메시지 추가 (최대 길이 제한)
     * 
     * @param key 스트림 키
     * @param maxlen 최대 길이
     * @param body 메시지 내용
     * @return 메시지 ID
     */
    public static String xaddMaxlen(String key, long maxlen, Map<String, String> body) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().xadd(key, XAddArgs.Builder.maxlen(maxlen), body));
    }

    /**
     * 스트림 메시지 읽기
     * 
     * @param key 스트림 키
     * @param messageId 시작 메시지 ID ("0": 처음부터, "$": 새 메시지만)
     * @return 메시지 리스트
     */
    public static List<StreamMessage<String, String>> xread(String key, String messageId) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().xread(XReadArgs.StreamOffset.from(key, messageId)));
    }

    /**
     * 스트림 범위 조회
     * 
     * @param key 스트림 키
     * @param start 시작 ID
     * @param end 종료 ID
     * @return 메시지 리스트
     */
    public static List<StreamMessage<String, String>> xrange(String key, String start, String end) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().xrange(key, Range.create(start, end)));
    }

    /**
     * 스트림 길이 조회
     * 
     * @param key 스트림 키
     * @return 스트림 길이
     */
    public static long xlen(String key) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().xlen(key));
    }

    // ================= UTILITY 헬퍼 =================

    /**
     * 키 이름 변경
     * 
     * @param oldKey 기존 키
     * @param newKey 새 키
     * @return "OK" (성공 시)
     */
    public static String rename(String oldKey, String newKey) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().rename(oldKey, newKey));
    }

    /**
     * 키가 없을 때만 이름 변경
     * 
     * @param oldKey 기존 키
     * @param newKey 새 키
     * @return 변경 성공 여부
     */
    public static boolean renamenx(String oldKey, String newKey) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().renamenx(oldKey, newKey));
    }

    /**
     * 키의 데이터 타입 조회
     * 
     * @param key Redis 키
     * @return 데이터 타입 (string, list, set, zset, hash, stream)
     */
    public static String type(String key) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().type(key));
    }

    /**
     * 랜덤 키 조회
     * 
     * @return 랜덤 키
     */
    public static String randomKey() {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().randomkey());
    }

    /**
     * TTL 제거 (영구 저장으로 변경)
     * 
     * @param key Redis 키
     * @return 성공 여부
     */
    public static boolean persist(String key) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().persist(key));
    }
    
    
    /**
     * 메시지 발행
     * 
     * @param channel 채널명
     * @param message 메시지
     * @return 메시지를 받은 구독자 수
     */
    public static long publish(String channel, String message) {
        return withRedis(Redis.getString(), conn -> 
            conn.sync().publish(channel, message));
    }


    // ================= 트랜잭션 =================

    /**
     * 트랜잭션 실행
     * 
     * 예제:
     * TransactionResult result = executeTransaction(redis -> {
     *     redis.set("user:1:name", "홍길동");
     *     redis.set("user:1:age", "30");
     *     redis.set("user:1:city", "서울");
     * });
     *
     * @param commands Redis 연결을 받아서 실행할 명령어들을 정의하는 Consumer 함수
     * @return 트랜잭션 결과
     */
    public static TransactionResult executeTransaction(java.util.function.Consumer<RedisCommands<String, String>> commands) {
        return withRedis(Redis.getString(), conn -> {
            conn.sync().multi();
            try {
                commands.accept(conn.sync());
                return conn.sync().exec();
            } catch (Exception e) {
                conn.sync().discard();
                logger.error("Transaction failed and rolled back", e);
                throw e;
            }
        });
    }
    
    /**
     * 트랜잭션 실행 (타입 자동 감지)
     * 
     * 예제:
     * SharedMap data = new SharedMap();
     * data.put("name", "홍길동");
     * 
     * TransactionResult result = executeTransaction(data, redis -> {
     *     redis.set("user:profile", data);
     *     redis.expire("user:profile", 3600);
     *     redis.hset("user:123:stats", "last_login", String.valueOf(System.currentTimeMillis()));
     * });
     * 
     * // List<SharedMap> 예제
     * List<SharedMap> userList = Arrays.asList(userData);
     * TransactionResult result2 = executeTransaction(userList, redis -> {
     *     redis.rpush("users:batch", userList.toArray(new SharedMap[0]));
     *     redis.expire("users:pending", 1800);
     * });
     * TransactionResult result1 = executeTransaction("", stringRedis -> {
     * stringRedis.set("counter", "1");
     * stringRedis.incr("counter");
     * });
     *
     * @param <T> 값 타입
     * @param typeHint 타입 힌트 (연결 결정용)
     * @param commands Redis 연결을 받아서 실행할 명령어들
     * @return 트랜잭션 결과
     */
    
    public static <T> TransactionResult executeTransaction(T typeHint, Consumer<RedisCommands<String, T>> commands) {
        StatefulRedisConnection<String, T> connection = determineConnection(typeHint);
        return withRedis(connection, conn -> {
            conn.sync().multi();
            try {
                commands.accept(conn.sync());
                return conn.sync().exec();
            } catch (Exception e) {
                conn.sync().discard();
                logger.error("Generic transaction failed and rolled back for type: {}", 
                            typeHint != null ? typeHint.getClass().getSimpleName() : "null", e);
                throw e;
            }
        });
    }

    /**
     * Watch를 사용한 낙관적 락 트랜잭션 (제네릭 타입 지원)
     * 
     * Watch Key가 트랜잭션 실행 중 다른 클라이언트에 의해 변경되면 트랜잭션이 실패합니다.
     * 주로 읽기-수정-쓰기 패턴에서 동시성 제어를 위해 사용됩니다.
     * 
     * 예제 1 - 계좌 이체 (동시성 제어):
     * TransactionResult result = executeTransactionWithWatch("balance:123", "", redis -> {
     *     String fromBalance = redis.get("balance:123");
     *     String toBalance = redis.get("balance:456");
     *     if (fromBalance != null && toBalance != null) {
     *         int from = Integer.parseInt(fromBalance);
     *         int to = Integer.parseInt(toBalance);
     *         if (from >= 1000) {  // 잔액 충분 확인
     *             redis.set("balance:123", String.valueOf(from - 1000));
     *             redis.set("balance:456", String.valueOf(to + 1000));
     *             redis.rpush("transactions:123", "transfer_out:1000:" + System.currentTimeMillis());
     *         }
     *     }
     * });
     * boolean success = (result != null); // null이면 다른 클라이언트가 잔액 변경으로 실패
     * 
     * 예제 2 - 상품 재고 관리:
     * TransactionResult result = executeTransactionWithWatch("stock:product456", "", redis -> {
     *     String stockStr = redis.get("stock:product456");
     *     if (stockStr != null) {
     *         int currentStock = Integer.parseInt(stockStr);
     *         if (currentStock >= 5) {  // 재고 충분 확인
     *             redis.set("stock:product456", String.valueOf(currentStock - 5));
     *             redis.incrby("sold:product456", 5);
     *             if (currentStock - 5 <= 10) {
     *                 redis.rpush("low_stock_alerts", "product456");
     *             }
     *         }
     *     }
     * });
     * 
     * 예제 3 - SharedMap 사용자 프로필 업데이트:
     * SharedMap profileUpdate = new SharedMap();
     * TransactionResult result = executeTransactionWithWatch("profile:user123", profileUpdate, redis -> {
     *     SharedMap currentProfile = redis.get("profile:user123");
     *     if (currentProfile != null) {
     *         SharedMap updatedProfile = new SharedMap(currentProfile);
     *         updatedProfile.put("status", "premium");
     *         updatedProfile.put("last_modified", System.currentTimeMillis());
     *         updatedProfile.put("version", ((Integer) updatedProfile.getOrDefault("version", 0)) + 1);
     *         redis.set("profile:user123", updatedProfile);
     *         
     *         SharedMap changeLog = new SharedMap();
     *         changeLog.put("field", "status");
     *         changeLog.put("new_value", "premium");
     *         changeLog.put("timestamp", System.currentTimeMillis());
     *         redis.rpush("profile_changes:user123", changeLog);
     *     }
     * });
     * 
     * 예제 4 - List<SharedMap> 배치 업데이트:
     * List<SharedMap> batchUpdates = new ArrayList<>();
     * TransactionResult result = executeTransactionWithWatch("batch_lock:users", batchUpdates, redis -> {
     *     String lockStatus = redis.get("batch_lock:users");
     *     if (lockStatus == null) {
     *         redis.setex("batch_lock:users", 300, "processing"); // 5분 TTL
     *         
     *         for (String userId : Arrays.asList("user1", "user2", "user3")) {
     *             SharedMap userUpdate = new SharedMap();
     *             userUpdate.put("user_id", userId);
     *             userUpdate.put("status", "active");
     *             userUpdate.put("updated_at", System.currentTimeMillis());
     *             batchUpdates.add(userUpdate);
     *             redis.hset("user:" + userId, "status", "active");
     *         }
     *         redis.rpush("batch_results", batchUpdates.toArray(new SharedMap[0]));
     *     }
     * });
     * 
     * 예제 5 - 조회수 중복 방지:
     * TransactionResult result = executeTransactionWithWatch("views:post789", "", redis -> {
     *     String userViewKey = "user_viewed:user123:post789";
     *     if (redis.get(userViewKey) == null) {  // 이미 조회했는지 확인
     *         String viewsStr = redis.get("views:post789");
     *         int currentViews = viewsStr != null ? Integer.parseInt(viewsStr) : 0;
     *         redis.set("views:post789", String.valueOf(currentViews + 1));
     *         redis.setex(userViewKey, 86400, "1");  // 24시간 TTL
     *         redis.zadd("popular_posts", currentViews + 1, "post789");
     *     }
     * });
     * 
     * 실패 처리 패턴:
     * int maxRetries = 3;
     * for (int i = 0; i < maxRetries; i++) {
     *     TransactionResult result = executeTransactionWithWatch("critical_key", "", redis -> {
     *         redis.incr("critical_counter");
     *     });
     *     if (result != null) break;  // 성공
     *     Thread.sleep(10 + (i * 10));  // 백오프 후 재시도
     * }
     *
     * @param <T> 값 타입
     * @param watchKey 감시할 키 (이 키가 변경되면 트랜잭션이 실패함)
     * @param typeHint 타입 힌트 (적절한 Redis 연결 결정용)
     * @param commands Redis 연결을 받아서 실행할 명령어들을 정의하는 Consumer 함수
     * @return 트랜잭션 결과 (null이면 watch된 키가 변경되어 트랜잭션 실패)
     */
    public static <T> TransactionResult executeTransactionWithWatch(String watchKey, T typeHint, Consumer<RedisCommands<String, T>> commands) {
        StatefulRedisConnection<String, T> connection = determineConnection(typeHint);
        return withRedis(connection, conn -> {
            conn.sync().watch(watchKey);
            conn.sync().multi();
            try {
                commands.accept(conn.sync());
                return conn.sync().exec();
            } catch (Exception e) {
                conn.sync().discard();
                conn.sync().unwatch();
                logger.error("Generic transaction with watch failed and rolled back for type: {}", 
                            typeHint != null ? typeHint.getClass().getSimpleName() : "null", e);
                throw e;
            }
        });
    }



    // ================= TTL 기반 작업 큐 =================

    /**
     * 지연 작업 큐에 작업 추가
     * 
     * @param queueKey 큐 키
     * @param taskId 작업 ID
     * @param delaySeconds 지연 시간 (초)
     * @return 추가 성공 여부
     */
    public static boolean addDelayedTask(String queueKey, String taskId, long delaySeconds) {
        long executeTime = System.currentTimeMillis() + (delaySeconds * 1000);
        return zadd(queueKey, executeTime, taskId) >= 0;
    }

    /**
     * 실행 가능한 작업 조회 및 제거
     * 
     * @param queueKey 큐 키
     * @param batchSize 한 번에 가져올 작업 수
     * @return 실행 가능한 작업 리스트
     */
    public static List<String> pollDelayedTasks(String queueKey, int batchSize) {
        return withRedis(Redis.getString(), conn -> {
            long now = System.currentTimeMillis();
            
            // 실행 가능한 작업 조회
            List<String> tasks = conn.sync().zrangebyscore(
                queueKey, 
                Range.create(0.0, (double) now), 
                io.lettuce.core.Limit.create(0, batchSize)
            );
            
            // 조회된 작업 제거
            if (!tasks.isEmpty()) {
                conn.sync().zrem(queueKey, tasks.toArray(new String[0]));
            }
            
            return tasks;
        });
    }

    // ================= 유틸리티 메서드 =================

    /**
     * 여러 키의 값을 Map으로 조회
     * 
     * @param keys 조회할 키들
     * @return 키-값 Map
     */
    public static Map<String, String> mgetAsMap(String... keys) {
        List<KeyValue<String, String>> values = mget(keys);
        Map<String, String> result = new java.util.HashMap<>();
        
        for (KeyValue<String, String> kv : values) {
            if (kv.hasValue()) {
                result.put(kv.getKey(), kv.getValue());
            }
        }
        
        return result;
    }
    
    
    /**
     * 여러 키의 값을 지정한 타입의 Map으로 조회 (제네릭 버전)
     * 
     * 예제 1 - SharedMap 조회:
     * SharedMap typeHint = new SharedMap();
     * Map<String, SharedMap> userProfiles = mgetAsMap(typeHint, 
     *     "profile:user1", "profile:user2", "profile:user3");
     * 
     * SharedMap user1Profile = userProfiles.get("profile:user1");
     * if (user1Profile != null) {
     *     String name = (String) user1Profile.get("name");
     *     Integer age = (Integer) user1Profile.get("age");
     * }
     * 
     * 예제 2 - LinkedMap 조회:
     * LinkedMap typeHint = new LinkedMap();
     * Map<String, LinkedMap> orderedData = mgetAsMap(typeHint,
     *     "ordered:data1", "ordered:data2");
     * 
     * 예제 3 - Object 조회:
     * Object typeHint = new Object();
     * Map<String, Object> mixedData = mgetAsMap(typeHint,
     *     "cache:obj1", "cache:obj2", "cache:obj3");
     * 
     * @param <T> 값 타입
     * @param typeHint 타입 힌트 (적절한 Redis 연결 결정용)
     * @param keys 조회할 키들
     * @return 키-값 Map
     */
    
    public static <T> Map<String, T> mgetAsMap(T typeHint, String... keys) {
        StatefulRedisConnection<String, T> connection = determineConnection(typeHint);
        
        return withRedis(connection, conn -> {
            List<KeyValue<String, T>> values = conn.sync().mget(keys);
            Map<String, T> result = new java.util.HashMap<>();

            for (KeyValue<String, T> kv : values) {
                if (kv.hasValue()) {
                    result.put(kv.getKey(), kv.getValue());
                }
            }

            return result;
        });
    }
    
    
    /**
     * 여러 키의 값을 TypeReference로 지정한 타입의 Map으로 조회 (타입 안전)
     * 
     * 예제 1 - List<SharedMap> 조회:
     * List<SharedMap> emptyList = new ArrayList<>();
     * Map<String, List<SharedMap>> userLists = mgetAsMap(emptyList, 
     *     new TypeReference<List<SharedMap>>() {}, 
     *     "users:active", "users:pending", "users:blocked");
     * 
     * List<SharedMap> activeUsers = userLists.get("users:active");
     * if (activeUsers != null && !activeUsers.isEmpty()) {
     *     for (SharedMap user : activeUsers) {
     *         String userId = (String) user.get("id");
     *         String status = (String) user.get("status");
     *     }
     * }
     * 
     * 예제 2 - Set<String> 조회:
     * Set<String> emptySet = new HashSet<>();
     * Map<String, Set<String>> tagSets = mgetAsMap(emptySet, 
     *     new TypeReference<Set<String>>() {},
     *     "tags:tech", "tags:business", "tags:entertainment");
     * 
     * 예제 3 - Map<String, Object> 조회:
     * Map<String, Object> emptyMap = new HashMap<>();
     * Map<String, Map<String, Object>> configMaps = mgetAsMap(emptyMap,
     *     new TypeReference<Map<String, Object>>() {},
     *     "config:app", "config:db", "config:cache");
     * 
     * @param <T> 값 타입
     * @param typeHint 타입 힌트
     * @param typeRef 타입 참조
     * @param keys 조회할 키들
     * @return 키-값 Map
     */
    public static <T> Map<String, T> mgetAsMap(T typeHint, TypeReference<T> typeRef, String... keys) {
        StatefulRedisConnection<String, T> connection = determineConnection(typeHint, typeRef);
        
        return withRedis(connection, conn -> {
            List<KeyValue<String, T>> values = conn.sync().mget(keys);
            Map<String, T> result = new java.util.HashMap<>();

            for (KeyValue<String, T> kv : values) {
                if (kv.hasValue()) {
                    result.put(kv.getKey(), kv.getValue());
                }
            }

            return result;
        });
    }
    
    
    /**
     * 패턴 매칭으로 키를 찾아서 값들을 Map으로 조회 (제네릭 버전)
     * 
     * 예제:
     * SharedMap typeHint = new SharedMap();
     * Map<String, SharedMap> userProfiles = mgetByPatternAsMap(typeHint, "profile:user*");
     * 
     * // profile:user1, profile:user2, profile:user3 등의 모든 키 조회
     * for (Map.Entry<String, SharedMap> entry : userProfiles.entrySet()) {
     *     String key = entry.getKey();
     *     SharedMap profile = entry.getValue();
     *     System.out.println("Key: " + key + ", Name: " + profile.get("name"));
     * }
     * 
     * @param <T> 값 타입
     * @param typeHint 타입 힌트
     * @param pattern 키 패턴 (*, ? 지원)
     * @return 패턴에 매칭되는 키-값 Map
     */
    public static <T> Map<String, T> mgetByPatternAsMap(T typeHint, String pattern) {
        List<String> matchingKeys = keys(pattern);
        if (matchingKeys.isEmpty()) {
            return new java.util.HashMap<>();
        }
        
        return mgetAsMap(typeHint, matchingKeys.toArray(new String[0]));
    }
    
    
    

    /**
     * 키 존재 여부를 빠르게 확인 (EXISTS 사용)
     * 
     * @param key Redis 키
     * @return 존재 여부
     */
    public static boolean keyExists(String key) {
        return RedisUtils.exist(key) > 0;
    }

    /**
     * 키 만료까지 남은 시간 확인 후 조건부 작업
     * 
     * @param key Redis 키
     * @param minTtl 최소 TTL (초)
     * @param action TTL이 충분할 때 실행할 작업
     * @return 작업 실행 여부
     */
    public static boolean executeIfTtlSufficient(String key, long minTtl, Runnable action) {
        long ttl = RedisUtils.ttl(key);
        
        if (ttl >= minTtl) {
            action.run();
            return true;
        }
        
        return false;
    }
    
    
    
    
    
    /**
	 * 특정 섹션의 서버 정보 조회
	 *
	 * @param section 섹션명 (server, clients, memory, stats 등)
	 * @return 서버 정보
	 */
	public static String info(String section) {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info(section));
	}

	/**
	 * 서버 일반 정보 조회
	 * Redis 서버 버전, 운영체제, 아키텍처, 업타임 등의 기본 정보
	 * 
	 * 예제:
	 * String serverInfo = infoServer();
	 * // redis_version:7.0.0
	 * // redis_git_sha1:00000000
	 * // os:Linux 5.4.0-74-generic x86_64
	 * // uptime_in_seconds:3600
	 * 
	 * @return 서버 정보 문자열
	 */
	public static String infoServer() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("server"));
	}

	/**
	 * 클라이언트 연결 정보 조회
	 * 연결된 클라이언트 수, 차단된 클라이언트 수, 최대 클라이언트 수 등
	 * 
	 * 예제:
	 * String clientInfo = infoClients();
	 * // connected_clients:5
	 * // client_recent_max_input_buffer:8
	 * // client_recent_max_output_buffer:0
	 * // blocked_clients:0
	 * 
	 * @return 클라이언트 정보 문자열
	 */
	public static String infoClients() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("clients"));
	}

	/**
	 * 메모리 사용량 정보 조회
	 * 사용 중인 메모리, 최대 메모리, 메모리 조각화 비율 등
	 * 
	 * 예제:
	 * String memoryInfo = infoMemory();
	 * // used_memory:1024000
	 * // used_memory_human:1000.00K
	 * // used_memory_rss:2048000
	 * // maxmemory:0
	 * 
	 * @return 메모리 정보 문자열
	 */
	public static String infoMemory() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("memory"));
	}

	/**
	 * 지속성(Persistence) 정보 조회
	 * RDB 저장, AOF 로그, 마지막 저장 시간 등
	 * 
	 * 예제:
	 * String persistenceInfo = infoPersistence();
	 * // loading:0
	 * // rdb_changes_since_last_save:0
	 * // rdb_bgsave_in_progress:0
	 * // rdb_last_save_time:1640995200
	 * 
	 * @return 지속성 정보 문자열
	 */
	public static String infoPersistence() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("persistence"));
	}

	/**
	 * 통계 정보 조회
	 * 총 명령어 처리 수, 초당 처리량, 히트/미스 비율 등
	 * 
	 * 예제:
	 * String statsInfo = infoStats();
	 * // total_connections_received:1000
	 * // total_commands_processed:5000
	 * // instantaneous_ops_per_sec:50
	 * // keyspace_hits:800
	 * // keyspace_misses:200
	 * 
	 * @return 통계 정보 문자열
	 */
	public static String infoStats() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("stats"));
	}

	/**
	 * 복제(Replication) 정보 조회
	 * 마스터/슬레이브 역할, 연결된 슬레이브 수, 복제 지연 등
	 * 
	 * 예제:
	 * String replicationInfo = infoReplication();
	 * // role:master
	 * // connected_slaves:2
	 * // slave0:ip=192.168.1.100,port=6379,state=online,offset=1024
	 * 
	 * @return 복제 정보 문자열
	 */
	public static String infoReplication() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("replication"));
	}

	/**
	 * CPU 사용량 정보 조회
	 * 사용자/시스템 CPU 시간, 자식 프로세스 CPU 시간 등
	 * 
	 * 예제:
	 * String cpuInfo = infoCpu();
	 * // used_cpu_sys:1.50
	 * // used_cpu_user:2.30
	 * // used_cpu_sys_children:0.10
	 * // used_cpu_user_children:0.20
	 * 
	 * @return CPU 정보 문자열
	 */
	public static String infoCpu() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("cpu"));
	}

	/**
	 * 명령어 통계 정보 조회
	 * 각 명령어별 실행 횟수, 총 소요 시간, 평균 시간 등
	 * 
	 * 예제:
	 * String commandStatsInfo = infoCommandStats();
	 * // cmdstat_get:calls=1000,usec=5000,usec_per_call=5.00
	 * // cmdstat_set:calls=500,usec=3000,usec_per_call=6.00
	 * // cmdstat_incr:calls=200,usec=800,usec_per_call=4.00
	 * 
	 * @return 명령어 통계 정보 문자열
	 */
	public static String infoCommandStats() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("commandstats"));
	}

	/**
	 * 클러스터 정보 조회
	 * 클러스터 활성화 상태 등
	 * 
	 * 예제:
	 * String clusterInfo = infoCluster();
	 * // cluster_enabled:0
	 * 
	 * @return 클러스터 정보 문자열
	 */
	public static String infoCluster() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("cluster"));
	}

	/**
	 * 키스페이스 정보 조회
	 * 각 데이터베이스별 키 개수, 만료 키 개수, 평균 TTL 등
	 * 
	 * 예제:
	 * String keyspaceInfo = infoKeyspace();
	 * // db0:keys=1000,expires=100,avg_ttl=3600000
	 * // db1:keys=500,expires=50,avg_ttl=7200000
	 * 
	 * @return 키스페이스 정보 문자열
	 */
	public static String infoKeyspace() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info("keyspace"));
	}

	/**
	 * 모든 Redis 서버 정보 조회
	 * 
	 * 예제:
	 * String allInfo = infoAll();
	 * // 모든 섹션의 정보가 포함된 긴 문자열
	 * 
	 * @return 전체 서버 정보 문자열
	 */
	public static String infoAll() {
	    return withRedis(Redis.getString(), conn ->
	        conn.sync().info());
	}

	/**
	 * 서버 정보를 Map으로 파싱하여 조회
	 * 
	 * 예제:
	 * Map<String, String> serverInfo = infoAsMap("server");
	 * String version = serverInfo.get("redis_version");
	 * String os = serverInfo.get("os");
	 * long uptime = Long.parseLong(serverInfo.get("uptime_in_seconds"));
	 * 
	 * @param section 섹션명
	 * @return 키-값 쌍으로 파싱된 정보
	 */
	public static Map<String, String> infoAsMap(String section) {
	    String info = info(section);
	    Map<String, String> result = new java.util.HashMap<>();
	    
	    if (info != null && !info.trim().isEmpty()) {
	        String[] lines = info.split("\r?\n");
	        for (String line : lines) {
	            line = line.trim();
	            if (!line.isEmpty() && !line.startsWith("#") && line.contains(":")) {
	                String[] parts = line.split(":", 2);
	                if (parts.length == 2) {
	                    result.put(parts[0].trim(), parts[1].trim());
	                }
	            }
	        }
	    }
	    
	    return result;
	}

	

	/**
	 * 서버 상태 요약 정보 조회 (편의 메서드)
	 * 
	 * 예제:
	 * Map<String, Object> summary = getServerSummary();
	 * System.out.println("Redis Version: " + summary.get("version"));
	 * System.out.println("Used Memory: " + summary.get("used_memory_human"));
	 * System.out.println("Connected Clients: " + summary.get("connected_clients"));
	 * 
	 * @return 주요 서버 정보 요약
	 */
	public static Map<String, Object> getServerSummary() {
	    Map<String, Object> summary = new java.util.HashMap<>();
	    
	    // 서버 기본 정보
	    Map<String, String> serverInfo = infoAsMap("server");
	    summary.put("version", serverInfo.get("redis_version"));
	    summary.put("uptime_seconds", serverInfo.get("uptime_in_seconds"));
	    summary.put("os", serverInfo.get("os"));
	    
	    // 메모리 정보
	    Map<String, String> memoryInfo = infoAsMap("memory");
	    summary.put("used_memory", memoryInfo.get("used_memory"));
	    summary.put("used_memory_human", memoryInfo.get("used_memory_human"));
	    summary.put("maxmemory", memoryInfo.get("maxmemory"));
	    
	    // 클라이언트 정보
	    Map<String, String> clientInfo = infoAsMap("clients");
	    summary.put("connected_clients", clientInfo.get("connected_clients"));
	    summary.put("blocked_clients", clientInfo.get("blocked_clients"));
	    
	    // 통계 정보
	    Map<String, String> statsInfo = infoAsMap("stats");
	    summary.put("total_commands_processed", statsInfo.get("total_commands_processed"));
	    summary.put("instantaneous_ops_per_sec", statsInfo.get("instantaneous_ops_per_sec"));
	    summary.put("keyspace_hits", statsInfo.get("keyspace_hits"));
	    summary.put("keyspace_misses", statsInfo.get("keyspace_misses"));
	    
	    return summary;
	}
    
}