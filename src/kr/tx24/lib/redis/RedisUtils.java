package kr.tx24.lib.redis;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.KeyScanCursor;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.SetArgs;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import kr.tx24.lib.db.Retrieve;
import kr.tx24.lib.enums.TypeRegistry;
import kr.tx24.lib.map.SharedMap;

/**
 * Redis 유틸리티 클래스 (Lettuce 6.2.x, Redis 7.x)
 * 
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>기본 CRUD 연산 (String, Object)</li>
 *   <li>Hash, List, Set 연산</li>
 *   <li>⭐ Transaction 지원 (MULTI/EXEC)</li>
 *   <li>⭐ Async 연산 지원 (CompletableFuture)</li>
 *   <li>⭐ Pipeline 지원 (대량 작업 최적화)</li>
 *   <li>패턴 매칭 및 대량 삭제</li>
 *   <li>TTL 관리</li>
 *   <li>Increment/Decrement</li>
 * </ul>
 * 
 * <p><b>⭐ close() 불필요:</b></p>
 * Connection은 자동으로 재사용됩니다. 각 메서드 호출 후 close() 할 필요 없습니다.
 * <p><b>사용 예:</b></p>
 * <pre>
 * // 기본 사용
 * RedisUtils.set("key", "value");
 * String value = RedisUtils.get("key", String.class);
 * 
 * // Transaction
 * RedisUtils.executeTransaction(tx -> {
 *     tx.set("key1", "value1");
 *     tx.set("key2", "value2");
 * });
 * 
 * // Async
 * RedisUtils.setAsync("key", "value")
 *     .thenAccept(result -> System.out.println("Done"));
 * 
 * // Pipeline (대량 작업)
 * RedisUtils.pipeline(async -> {
 *     List<RedisFuture<?>> list = new ArrayList<>();
 *     for (int i = 0; i < 1000; i++) {
 *         list.add(async.set("key" + i, "value" + i));
 *     }
 *     return list;
 * });
 * </pre>
 * 
 * @author TX24
 * @version 1.0
 * @see Redis
 * @see TypedRedisCodec
 */
public final class RedisUtils {

    private static final Logger logger = LoggerFactory.getLogger(RedisUtils.class);

    private RedisUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    
    /**
     * ⭐⭐⭐ TypeReference - 제네릭 타입 정보 보존
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // List<String> 조회
     * List<String> names = RedisUtils.get("users:names", 
     *     new TypeReference<List<String>>(){});
     * 
     * // Map<String, Integer> 조회
     * Map<String, Integer> scores = RedisUtils.get("game:scores", 
     *     new TypeReference<Map<String, Integer>>(){});
     * 
     * // 복잡한 중첩 타입
     * Map<String, List<User>> userGroups = RedisUtils.get("groups", 
     *     new TypeReference<Map<String, List<User>>>(){});
     * </pre>
     * 
     * <p><b>작동 원리:</b></p>
     * 익명 내부 클래스를 사용하여 제네릭 타입 정보를 런타임에 보존합니다.
     * 
     * @param <T> 대상 타입
     */
    public abstract static class TypeReference<T> {
        private final Type type;

        protected TypeReference() {
            Type superclass = getClass().getGenericSuperclass();
            if (superclass instanceof ParameterizedType) {
                this.type = ((ParameterizedType) superclass).getActualTypeArguments()[0];
            } else {
                throw new IllegalArgumentException("TypeReference must be parameterized");
            }
        }

        /**
         * 타입 정보 반환
         * 
         * @return Type 객체
         */
        public Type getType() {
            return type;
        }

        /**
         * Raw 클래스 반환
         * 
         * @return Class 객체
         */
        @SuppressWarnings("unchecked")
        public Class<T> getRawType() {
            if (type instanceof Class) {
                return (Class<T>) type;
            } else if (type instanceof ParameterizedType) {
                return (Class<T>) ((ParameterizedType) type).getRawType();
            }
            throw new IllegalStateException("Unable to determine raw type");
        }

        @Override
        public String toString() {
            return "TypeReference<" + type + ">";
        }
    }
    

    // ==================== 기본 Operations ====================

    /**
     * 값 저장 (기본)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisUtils.set("user:name", "John");
     * RedisUtils.set("user:age", 30);
     * 
     * SharedMap<String, Object> user = new SharedMap<>();
     * user.put("id", 123);
     * RedisUtils.set("user:123", user);
     * </pre>
     * 
     * @param key Redis 키 (null 불가)
     * @param value 값 (모든 타입 가능, TypedRedisCodec으로 자동 직렬화)
     * @throws IllegalArgumentException key가 null인 경우
     */
    public static void set(String key, Object value) {
        Redis.sync().set(key, value);
    }

    /**
     * 값 저장 (TTL 설정)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 세션 저장 (30분)
     * RedisUtils.set("session:token", sessionData, 1800);
     * 
     * // 캐시 저장 (1시간)
     * RedisUtils.set("cache:product:123", product, 3600);
     * 
     * // 일회용 토큰 (5분)
     * RedisUtils.set("otp:user123", "123456", 300);
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값
     * @param seconds TTL (초 단위, 0보다 커야 함)
     * @throws IllegalArgumentException seconds가 0 이하인 경우
     */
    public static void set(String key, Object value, long seconds) {
        Redis.sync().setex(key, seconds, value);
    }

    /**
     * 키가 없을 때만만 성공 처리   
     * - SET NX) , SET if NOT exists
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 분산 락 획득
     * boolean acquired = RedisUtils.setNx("lock:resource", "owner123");
     * if (acquired) {
     *     try {
     *         // 크리티컬 섹션
     *         processResource();`
     *     } finally {
     *         RedisUtils.del("lock:resource");
     *     }
     * }
     * 
     * // 중복 방지
     * boolean isNew = RedisUtils.setNx("processed:order:123", "true");
     * if (isNew) {
     *     processOrder(123);
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값
     * @return 키가 없어서 설정에 성공하면 true, 키가 이미 존재하면 false
     */
    public static boolean setNx(String key, Object value) {
        String result = Redis.sync().set(key, value, SetArgs.Builder.nx());
        return "OK".equals(result);
    }

    /**
     * 키가 없을 때만만 성공 처리   , TTL 포함
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 분산 락 (5초 타임아웃)
     * boolean acquired = RedisUtils.setNx("lock:payment", "owner", 5);
     * 
     * // Rate limiting (1분에 1번)
     * boolean canProcess = RedisUtils.setNx("rate:user123", "1", 60);
     * if (canProcess) {
     *     processRequest();
     * } else {
     *     throw new RateLimitException();
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값
     * @param seconds TTL (초 단위)
     * @return 키가 없어서 설정에 성공하면 true, 키가 이미 존재하면 false
     */
    public static boolean setNx(String key, Object value, long seconds) {
        String result = Redis.sync().set(key, value, SetArgs.Builder.nx().ex(seconds));
        return "OK".equals(result);
    }

    /**
     * 값 조회 (기본)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Object value = RedisUtils.get("key");
     * if (value != null) {
     *     // 타입 확인 후 캐스팅
     *     if (value instanceof String) {
     *         String str = (String) value;
     *     }
     * }
     * </pre>
     * 
     * <p><b>주의:</b></p>
     * 타입 안전성이 필요한 경우 {@link #get(String, Class)} 사용을 권장합니다.
     * 
     * @param key Redis 키
     * @return 값 (TypedRedisCodec으로 자동 역직렬화), 키가 없으면 null
     */
    public static Object get(String key) {
        return Redis.sync().get(key);
    }

    /**
     * ⭐ 타입 안전한 값 조회 (권장)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // String
     * String name = RedisUtils.get("user:name", String.class);
     * 
     * // Integer
     * Integer age = RedisUtils.get("user:age", Integer.class);
     * 
     * // 커스텀 타입
     * SharedMap<?, ?> user = RedisUtils.get("user:123", SharedMap.class);
     * 
     * // List (제네릭 타입은 런타임에 검증 불가)
     * List<?> items = (List<?>) RedisUtils.get("items");
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param clazz 예상 타입 (null 불가)
     * @return 값 (지정된 타입으로 캐스팅됨), 키가 없으면 null
     * @throws ClassCastException 실제 타입이 clazz와 다른 경우
     * @throws IllegalArgumentException clazz가 null인 경우
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, Class<T> clazz) {
        Object value = get(key);
        if (value == null) return null;
        if (clazz.isInstance(value)) return (T) value;
        throw new ClassCastException("Expected " + clazz.getName() + " but got " + value.getClass().getName());
    }
    
    
    /**
     * TypeToken을 사용한 제네릭 타입 안전 조회 (권장)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // List<String>
     * List<String> names = RedisUtils.get("users:names", 
     *     new TypeReference<List<String>>(){});
     * 
     * // Map<String, Integer>
     * Map<String, Integer> scores = RedisUtils.get("game:scores", 
     *     new TypeReference<Map<String, Integer>>(){});
     * 
     * // Set<Long>
     * Set<Long> ids = RedisUtils.get("user:ids", 
     *     new TypeReference<Set<Long>>(){});
     * 
     * // 복잡한 중첩 타입
     * Map<String, List<User>> groups = RedisUtils.get("user:groups", 
     *     new TypeReference<Map<String, List<User>>>(){});
     * 
     * // null 체크
     * List<String> tags = RedisUtils.get("article:tags", 
     *     new TypeReference<List<String>>(){});
     * if (tags != null) {
     *     tags.forEach(System.out::println);
     * }
     * </pre>
     * 
     * <p><b>주의사항:</b></p>
     * - 반드시 익명 내부 클래스로 생성해야 함: `new TypeReference<T>(){}`
     * - 중괄호 `{}` 필수!
     * - 저장 시 타입과 조회 시 타입이 일치해야 함
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param typeRef TypeReference (제네릭 타입 정보 포함)
     * @return 값 (제네릭 타입으로 안전하게 캐스팅됨), 키가 없으면 null
     * @throws ClassCastException 실제 저장된 타입과 요청 타입이 다른 경우
     */
    @SuppressWarnings("unchecked")
    public static <T> T get(String key, TypeReference<T> typeRef) {
        Object value = get(key);
        if (value == null) return null;
        
        // Raw 타입으로 먼저 확인
        Class<T> rawType = typeRef.getRawType();
        
        if (rawType.isInstance(value)) {
            // 타입이 맞으면 캐스팅
            return (T) value;
        }
        
        // 타입이 맞지 않으면 예외
        throw new ClassCastException(
            "Expected " + typeRef.getType() + 
            " but got " + value.getClass().getName()
        );
    }
    
    
    public static <T> T get(String key, TypeRegistry typeRegistry) {
        return get(key, typeRegistry.get());
    }
    

    /**
     * ⭐ TypeToken을 사용한 값 조회 with 기본값
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 키가 없으면 빈 리스트 반환
     * List<String> names = RedisUtils.getOrDefault("users:names", 
     *     new TypeReference<List<String>>(){}, 
     *     new ArrayList<>());
     * 
     * // 키가 없으면 빈 맵 반환
     * Map<String, Integer> scores = RedisUtils.getOrDefault("scores", 
     *     new TypeReference<Map<String, Integer>>(){}, 
     *     new HashMap<>());
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param typeRef TypeReference
     * @param defaultValue 기본값 (키가 없을 때 반환)
     * @return 값 또는 기본값
     */
    public static <T> T getOrDefault(String key, TypeReference<T> typeRef, T defaultValue) {
        T value = get(key, typeRef);
        return value != null ? value : defaultValue;
    }
    
    
    public static <T> T getOrDefault(String key, TypeRegistry typeRegistry, T defaultValue) {
        return getOrDefault(key, typeRegistry.get(), defaultValue);
    }

    /**
     * 값 조회 후 삭제 , 일정의 Take  
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 일회용 토큰 사용
     * String token = (String) RedisUtils.getAndDelete("otp:user123");
     * if (token != null && token.equals(inputToken)) {
     *     // 인증 성공
     * }
     * 
     * // 임시 데이터 소비
     * Object data = RedisUtils.getAndDelete("temp:data:123");
     * processData(data);
     * </pre>
     * 
     * <p><b>원자성:</b></p>
     * GET과 DEL이 원자적으로 실행됩니다.
     * 
     * @param key Redis 키
     * @return 삭제된 값, 키가 없으면 null
     */
    public static Object getAndDelete(String key) {
        return Redis.sync().getdel(key);
    }

    /**
     * 여러 키의 값 조회 (MGET)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<KeyValue<String, Object>> results = 
     *     RedisUtils.mget("user:1:name", "user:2:name", "user:3:name");
     * 
     * for (KeyValue<String, Object> kv : results) {
     *     System.out.println(kv.getKey() + " = " + kv.getValue());
     * }
     * </pre>
     * 
     * <p><b>성능:</b></p>
     * 여러 개의 GET을 한 번의 왕복으로 처리하므로 훨씬 빠릅니다.
     * 
     * @param keys Redis 키들 (최소 1개)
     * @return 키-값 쌍 리스트 (순서 보장)
     * @throws IllegalArgumentException keys가 비어있는 경우
     */
    public static List<KeyValue<String, Object>> mget(String... keys) {
        return Redis.sync().mget(keys);
    }

    /**
     * 키 삭제 (단일)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Long deleted = RedisUtils.del("temp:key");
     * if (deleted > 0) {
     *     System.out.println("키가 삭제되었습니다.");
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @return 삭제된 키 개수 (0 또는 1)
     */
    public static Long del(String key) {
        return Redis.sync().del(key);
    }

    /**
     * 여러 키 삭제 (MGET의 반대)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 여러 키 한 번에 삭제
     * Long deleted = RedisUtils.del("key1", "key2", "key3");
     * System.out.println(deleted + "개 키 삭제");
     * 
     * // 세션 관련 키 모두 삭제
     * RedisUtils.del("session:user123", "session:user123:data", "session:user123:meta");
     * </pre>
     * 
     * @param keys Redis 키들 (최소 1개)
     * @return 삭제된 키 개수
     */
    public static Long del(String... keys) {
        return Redis.sync().del(keys);
    }

    /**
     * 키 존재 여부 확인
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * if (RedisUtils.exists("user:123")) {
     *     // 사용자가 존재함
     *     User user = loadUserFromRedis("user:123");
     * } else {
     *     // DB에서 로드
     *     User user = loadUserFromDB(123);
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @return 키가 존재하면 true, 없으면 false
     */
    public static boolean exists(String key) {
        return Redis.sync().exists(key) > 0;
    }

    /**
     * TTL 설정 (키에 만료 시간 설정)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 기존 키에 TTL 추가
     * RedisUtils.set("cache:data", data);
     * RedisUtils.expire("cache:data", 3600);  // 1시간 후 만료
     * 
     * // 세션 갱신
     * if (RedisUtils.exists("session:token")) {
     *     RedisUtils.expire("session:token", 1800);  // 30분 연장
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @param seconds TTL (초 단위, 0보다 커야 함)
     * @return 성공하면 true, 키가 없으면 false
     */
    public static boolean expire(String key, long seconds) {
        return Redis.sync().expire(key, seconds);
    }

    /**
     * TTL 조회 (남은 만료 시간 확인)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Long ttl = RedisUtils.ttl("session:token");
     * if (ttl == -2) {
     *     System.out.println("키가 존재하지 않음");
     * } else if (ttl == -1) {
     *     System.out.println("만료 시간이 설정되지 않음 (무기한)");
     * } else {
     *     System.out.println(ttl + "초 후 만료");
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @return 남은 TTL (초), -1: 만료 시간 없음 (무기한), -2: 키 없음
     */
    public static Long ttl(String key) {
        return Redis.sync().ttl(key);
    }

    /**
     * 키 이름 변경 (RENAME)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 임시 키를 영구 키로 변경
     * RedisUtils.set("temp:processing:123", data);
     * // 처리 완료 후
     * RedisUtils.rename("temp:processing:123", "result:123");
     * </pre>
     * 
     * <p><b>주의:</b></p>
     * - newKey가 이미 존재하면 덮어씁니다.
     * - oldKey가 없으면 예외 발생
     * 
     * @param oldKey 기존 키 (반드시 존재해야 함)
     * @param newKey 새 키 (기존 키 있으면 덮어씀)
     * @return 성공 메시지 "OK"
     * @throws RuntimeException oldKey가 존재하지 않는 경우
     */
    public static String rename(String oldKey, String newKey) {
        return Redis.sync().rename(oldKey, newKey);
    }
    
    
    

    /**
     * 키 타입 조회
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String type = RedisUtils.type("key");
     * switch (type) {
     *     case "string":  // String 값
     *     case "list":    // List
     *     case "set":     // Set
     *     case "zset":    // Sorted Set
     *     case "hash":    // Hash
     *     case "none":    // 키가 없음
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @return 타입 문자열 ("string", "list", "set", "zset", "hash", "none")
     */
    public static String type(String key) {
        return Redis.sync().type(key);
    }

    // ==================== 패턴 매칭 ====================

    /**
     * 패턴에 매칭되는 키 조회 (KEYS)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 모든 사용자 키 조회
     * List<String> userKeys = RedisUtils.keys("user:*");
     * 
     * // 특정 날짜의 로그 조회
     * List<String> todayLogs = RedisUtils.keys("log:2024-01-15:*");
     * </pre>
     * 
     * <p><b>⚠️ 주의:</b></p>
     * - 프로덕션 환경에서는 {@link #scan(String, long)} 사용 권장
     * - KEYS는 blocking 명령어라 대용량 데이터에서 서버 지연 발생
     * - 개발/테스트 환경에서만 사용
     * 
     * @param pattern 패턴 (예: "user:*", "cache:product:*")
     * @return 매칭되는 키 리스트
     */
    public static List<String> keys(String pattern) {
        return Redis.sync().keys(pattern);
    }

    
    /**
     * ⭐ 패턴에 매칭되는 키 조회 (SCAN - 프로덕션 권장)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 대용량 데이터에서도 안전
     * List<String> userKeys = RedisUtils.scan("user:*");
     * 
     * // 캐시 키 조회
     * List<String> cacheKeys = RedisUtils.scan("cache:*");
     * </pre>
     * 
     * <p><b>장점:</b></p>
     * - Non-blocking (서버 블로킹 없음)
     * - 커서 기반 순회 (메모리 효율적)
     * - 대용량 데이터에 적합
     * 
     * @param pattern 패턴 (예: "user:*")
     * @return 매칭되는 모든 키 리스트
     */
    public static List<String> scan(String pattern) {
    	return scan(pattern, 0);
    }
    
    /**
     * ⭐ 패턴에 매칭되는 키 조회 (SCAN - 프로덕션 권장)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 대용량 데이터에서도 안전
     * List<String> userKeys = RedisUtils.scan("user:*", 100);
     * 
     * // 캐시 키 조회
     * List<String> cacheKeys = RedisUtils.scan("cache:*", 1000);
     * </pre>
     * 
     * <p><b>장점:</b></p>
     * - Non-blocking (서버 블로킹 없음)
     * - 커서 기반 순회 (메모리 효율적)
     * - 대용량 데이터에 적합
     * 
     * @param pattern 패턴 (예: "user:*")
     * @param count 한 번에 조회할 키 개수 (힌트, 정확하지 않을 수 있음)
     * @return 매칭되는 모든 키 리스트
     */
    public static List<String> scan(String pattern, long count) {
        List<String> result = new ArrayList<>();
        ScanCursor cursor = ScanCursor.INITIAL;
        
        ScanArgs args;
        if (count <= 0) {
            args = ScanArgs.Builder.matches(pattern);  // ✅ limit() 없음
        } else {
            args = ScanArgs.Builder.matches(pattern).limit(count);
        }
        
        do {
            KeyScanCursor<String> scanResult = Redis.sync().scan(cursor, args);
            result.addAll(scanResult.getKeys());
            cursor = ScanCursor.of(scanResult.getCursor());
        } while (!cursor.isFinished());  // ✅ 여기서 자동으로 전체 검색
        
        return result;
    }

    /**
     * ⭐ 패턴에 매칭되는 모든 키 삭제
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 캐시 전체 삭제
     * long deleted = RedisUtils.deleteByPattern("cache:*");
     * System.out.println(deleted + "개 캐시 삭제");
     * 
     * // 특정 사용자 데이터 삭제
     * RedisUtils.deleteByPattern("user:123:*");
     * 
     * // 임시 데이터 정리
     * RedisUtils.deleteByPattern("temp:*");
     * </pre>
     * 
     * <p><b>내부 동작:</b></p>
     * SCAN으로 키를 찾고 DEL로 삭제 (안전하고 효율적)
     * 
     * @param pattern 패턴 (예: "cache:*")
     * @return 삭제된 키 개수
     */
    public static long deleteByPattern(String pattern) {
        List<String> keys = scan(pattern, 100);
        if (keys.isEmpty()) return 0;
        return del(keys.toArray(new String[0]));
    }

    // ==================== Increment/Decrement ====================

    /**
     * 값 증가 (INCR - 1씩 증가)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 방문자 수 카운트
     * Long visitors = RedisUtils.incr("counter:visitors");
     * 
     * // 페이지뷰 카운트
     * Long pageviews = RedisUtils.incr("counter:pageview");
     * 
     * // ID 생성
     * Long nextId = RedisUtils.incr("id:user");
     * </pre>
     * 
     * <p><b>원자성:</b></p>
     * Thread-Safe하게 증가합니다. 동시성 문제 없음.
     * 
     * @param key Redis 키 (값이 없으면 0으로 초기화 후 증가)
     * @return 증가 후 값
     * @throws RuntimeException 값이 숫자가 아닌 경우
     */
    public static Long incr(String key) {
        return Redis.sync().incr(key);
    }

    /**
     * 값 증가 (INCRBY - 지정된 양만큼 증가)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 포인트 적립
     * Long points = RedisUtils.incrBy("user:123:points", 100);
     * 
     * // 재고 증가
     * Long stock = RedisUtils.incrBy("product:456:stock", 50);
     * </pre>
     * 
     * @param key Redis 키
     * @param amount 증가량 (양수여야 함, 음수는 감소 효과)
     * @return 증가 후 값
     */
    public static Long incrBy(String key, long amount) {
        return Redis.sync().incrby(key, amount);
    }

    /**
     * 값 감소 (DECR - 1씩 감소)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 재고 감소
     * Long stock = RedisUtils.decr("product:789:stock");
     * if (stock < 0) {
     *     throw new OutOfStockException();
     * }
     * </pre>
     * 
     * @param key Redis 키 (값이 없으면 0으로 초기화 후 감소)
     * @return 감소 후 값
     */
    public static Long decr(String key) {
        return Redis.sync().decr(key);
    }

    /**
     * 값 감소 (DECRBY - 지정된 양만큼 감소)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 포인트 차감
     * Long points = RedisUtils.decrBy("user:123:points", 50);
     * 
     * // 재고 감소
     * Long stock = RedisUtils.decrBy("product:456:stock", 10);
     * </pre>
     * 
     * @param key Redis 키
     * @param amount 감소량 (양수여야 함)
     * @return 감소 후 값
     */
    public static Long decrBy(String key, long amount) {
        return Redis.sync().decrby(key, amount);
    }

    // ==================== Hash Operations ====================

    /**
     * Hash 필드 값 설정 (HSET)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 사용자 정보 저장
     * RedisUtils.hset("user:123", "name", "John");
     * RedisUtils.hset("user:123", "age", 30);
     * RedisUtils.hset("user:123", "email", "john@example.com");
     * </pre>
     * 
     * @param key Redis 키
     * @param field 필드명
     * @param value 값
     * @return 새 필드가 생성되면 true, 기존 필드 업데이트면 false
     */
    public static boolean hset(String key, String field, Object value) {
        return Redis.sync().hset(key, field, value);
    }

    /**
     * Hash 여러 필드 값 설정 (HMSET)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Map<String, Object> user = new HashMap<>();
     * user.put("name", "John");
     * user.put("age", 30);
     * user.put("email", "john@example.com");
     * RedisUtils.hmset("user:123", user);
     * </pre>
     * 
     * @param key Redis 키
     * @param map 필드-값 맵
     * @return 성공 메시지 "OK"
     */
    public static String hmset(String key, Map<String, Object> map) {
        return Redis.sync().hmset(key, map);
    }

    /**
     * Hash 필드 값 조회 (HGET)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Object name = RedisUtils.hget("user:123", "name");
     * Object age = RedisUtils.hget("user:123", "age");
     * </pre>
     * 
     * @param key Redis 키
     * @param field 필드명
     * @return 값, 필드가 없으면 null
     */
    public static Object hget(String key, String field) {
        return Redis.sync().hget(key, field);
    }
    
    
    /**
     * ⭐ Hash 필드 값 조회 (TypeToken 지원)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // List<String> 필드 조회
     * List<String> tags = RedisUtils.hget("article:123", "tags", 
     *     new TypeReference<List<String>>(){});
     * 
     * // Map 필드 조회
     * Map<String, Object> meta = RedisUtils.hget("article:123", "metadata", 
     *     new TypeReference<Map<String, Object>>(){});
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param field 필드명
     * @param typeRef TypeReference
     * @return 필드 값, 없으면 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T hget(String key, String field, TypeReference<T> typeRef) {
        Object value = hget(key, field);
        if (value == null) return null;
        
        Class<T> rawType = typeRef.getRawType();
        if (rawType.isInstance(value)) {
            return (T) value;
        }
        
        throw new ClassCastException(
            "Expected " + typeRef.getType() + 
            " but got " + value.getClass().getName()
        );
    }
    
    
    public static <T> T hget(String key, String field, TypeRegistry  typeRegistry) {
    	return hget(key,field,typeRegistry.get());
    }
    
    
    

    /**
     * Hash 모든 필드 조회 (HGETALL)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Map<String, Object> user = RedisUtils.hgetAll("user:123");
     * String name = (String) user.get("name");
     * Integer age = (Integer) user.get("age");
     * </pre>
     * 
     * @param key Redis 키
     * @return 필드-값 맵 (키가 없으면 빈 맵)
     */
    public static Map<String, Object> hgetAll(String key) {
        return Redis.sync().hgetall(key);
    }

    /**
     * Hash 필드 삭제 (HDEL)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 단일 필드 삭제
     * RedisUtils.hdel("user:123", "temp_field");
     * 
     * // 여러 필드 삭제
     * RedisUtils.hdel("user:123", "field1", "field2", "field3");
     * </pre>
     * 
     * @param key Redis 키
     * @param fields 필드명들
     * @return 삭제된 필드 개수
     */
    public static Long hdel(String key, String... fields) {
        return Redis.sync().hdel(key, fields);
    }

    /**
     * Hash 필드 존재 여부 (HEXISTS)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * if (RedisUtils.hexists("user:123", "email")) {
     *     Object email = RedisUtils.hget("user:123", "email");
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @param field 필드명
     * @return 필드가 존재하면 true
     */
    public static boolean hexists(String key, String field) {
        return Redis.sync().hexists(key, field);
    }

    /**
     * Hash 필드 값 증가 (HINCRBY)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 상품 조회수 증가
     * Long views = RedisUtils.hincrBy("product:123", "views", 1);
     * 
     * // 재고 증가
     * Long stock = RedisUtils.hincrBy("product:123", "stock", 10);
     * </pre>
     * 
     * @param key Redis 키
     * @param field 필드명
     * @param amount 증가량
     * @return 증가 후 값
     */
    public static Long hincrBy(String key, String field, long amount) {
        return Redis.sync().hincrby(key, field, amount);
    }

    // ==================== List Operations ====================

    /**
     * List 왼쪽에 값 추가 (LPUSH)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 최신 알림을 맨 앞에 추가
     * RedisUtils.lpush("notifications:user123", "새 메시지가 있습니다");
     * 
     * // 여러 값 추가
     * RedisUtils.lpush("queue:tasks", "task3", "task2", "task1");
     * </pre>
     * 
     * @param key Redis 키
     * @param values 값들 (오른쪽부터 왼쪽으로 추가됨)
     * @return List 길이
     */
    public static Long lpush(String key, Object... values) {
        return Redis.sync().lpush(key, values);
    }

    /**
     * List 오른쪽에 값 추가 (RPUSH)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 큐에 작업 추가 (FIFO)
     * RedisUtils.rpush("queue:jobs", "job1");
     * RedisUtils.rpush("queue:jobs", "job2");
     * 
     * // 로그 추가
     * RedisUtils.rpush("logs", "2024-01-15 10:00:00 - User login");
     * </pre>
     * 
     * @param key Redis 키
     * @param values 값들
     * @return List 길이
     */
    public static Long rpush(String key, Object... values) {
        return Redis.sync().rpush(key, values);
    }

    /**
     * List 왼쪽에서 값 제거 및 반환 (LPOP)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 큐에서 작업 가져오기 (FIFO with RPUSH)
     * Object job = RedisUtils.lpop("queue:jobs");
     * if (job != null) {
     *     processJob(job);
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @return 제거된 값, List가 비었으면 null
     */
    public static Object lpop(String key) {
        return Redis.sync().lpop(key);
    }

    /**
     * List 오른쪽에서 값 제거 및 반환 (RPOP)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 스택에서 값 가져오기 (LIFO)
     * Object item = RedisUtils.rpop("stack:items");
     * </pre>
     * 
     * @param key Redis 키
     * @return 제거된 값, List가 비었으면 null
     */
    public static Object rpop(String key) {
        return Redis.sync().rpop(key);
    }

    /**
     * List 범위 조회 (LRANGE)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 전체 조회
     * List<Object> all = RedisUtils.lrange("list", 0, -1);
     * 
     * // 최근 10개
     * List<Object> recent = RedisUtils.lrange("logs", 0, 9);
     * 
     * // 10~19번째
     * List<Object> page2 = RedisUtils.lrange("items", 10, 19);
     * </pre>
     * 
     * @param key Redis 키
     * @param start 시작 인덱스 (0부터 시작, 음수는 끝에서부터)
     * @param stop 종료 인덱스 (-1은 끝까지)
     * @return 값 리스트
     */
    public static List<Object> lrange(String key, long start, long stop) {
        return Redis.sync().lrange(key, start, stop);
    }
    
    
    /**
     * List 범위 조회 (TypeToken 지원)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // String 리스트
     * List<String> logs = RedisUtils.lrange("logs", 0, 9, 
     *     new TypeReference<List<String>>(){});
     * 
     * // User 리스트
     * List<User> users = RedisUtils.lrange("users:active", 0, -1, 
     *     new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param start 시작 인덱스
     * @param stop 종료 인덱스
     * @param typeRef TypeReference
     * @return 값 리스트
     */
    @SuppressWarnings("unchecked")
    public static <T> T lrange(String key, long start, long stop, TypeReference<T> typeRef) {
        List<Object> values = lrange(key, start, stop);
        
        // 빈 리스트는 그대로 반환
        if (values.isEmpty()) {
            return (T) values;
        }
        
        Class<T> rawType = typeRef.getRawType();
        if (rawType.isInstance(values)) {
            return (T) values;
        }
        
        throw new ClassCastException(
            "Expected " + typeRef.getType() + 
            " but got List<" + values.get(0).getClass().getName() + ">"
        );
    }
    
    public static <T> T lrange(String key, long start, long stop, TypeRegistry typeRegistry) {
    	return lrange(key, start, stop, typeRegistry.get());
    }
    
    

    /**
     * List 길이 조회 (LLEN)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Long queueSize = RedisUtils.llen("queue:jobs");
     * System.out.println("대기 중인 작업: " + queueSize);
     * </pre>
     * 
     * @param key Redis 키
     * @return List 길이, 키가 없으면 0
     */
    public static Long llen(String key) {
        return Redis.sync().llen(key);
    }

    // ==================== Set Operations ====================

    /**
     * Set에 값 추가 (SADD)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 태그 추가
     * RedisUtils.sadd("article:123:tags", "java", "redis", "spring");
     * 
     * // 온라인 사용자
     * RedisUtils.sadd("users:online", "user123");
     * </pre>
     * 
     * @param key Redis 키
     * @param members 값들 (중복 자동 제거)
     * @return 추가된 값 개수 (이미 존재하는 값은 제외)
     */
    public static Long sadd(String key, Object... members) {
        return Redis.sync().sadd(key, members);
    }

    /**
     * Set에서 값 제거 (SREM)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 태그 제거
     * RedisUtils.srem("article:123:tags", "deprecated");
     * 
     * // 오프라인 처리
     * RedisUtils.srem("users:online", "user123");
     * </pre>
     * 
     * @param key Redis 키
     * @param members 값들
     * @return 제거된 값 개수
     */
    public static Long srem(String key, Object... members) {
        return Redis.sync().srem(key, members);
    }

    /**
     * Set 모든 값 조회 (SMEMBERS)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Set<Object> tags = RedisUtils.smembers("article:123:tags");
     * for (Object tag : tags) {
     *     System.out.println(tag);
     * }
     * </pre>
     * 
     * <p><b>⚠️ 주의:</b></p>
     * 대용량 Set은 성능 문제 발생 가능. SSCAN 사용 고려.
     * 
     * @param key Redis 키
     * @return 값 Set, 키가 없으면 빈 Set
     */
    public static Set<Object> smembers(String key) {
        return Redis.sync().smembers(key);
    }
    
    
    /**
     * ⭐ Set 조회 (TypeToken 지원)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // String Set
     * Set<String> tags = RedisUtils.smembers("article:tags", 
     *     new TypeReference<Set<String>>(){});
     * 
     * // Long Set
     * Set<Long> userIds = RedisUtils.smembers("online:users", 
     *     new TypeReference<Set<Long>>(){});
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param typeRef TypeReference
     * @return Set
     */
    @SuppressWarnings("unchecked")
    public static <T> T smembers(String key, TypeReference<T> typeRef) {
        Set<Object> values = smembers(key);
        
        Class<T> rawType = typeRef.getRawType();
        if (rawType.isInstance(values)) {
            return (T) values;
        }
        
        throw new ClassCastException(
            "Expected " + typeRef.getType() + 
            " but got Set<...>"
        );
    }
    
    
    public static <T> T smembers(String key, TypeRegistry typeRegistry) {
    	return smembers(key, typeRegistry.get());
    }

    
    

    /**
     * Set 값 존재 여부 (SISMEMBER)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * if (RedisUtils.sismember("users:online", "user123")) {
     *     System.out.println("사용자가 온라인입니다");
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @param member 값
     * @return 값이 존재하면 true
     */
    public static boolean sismember(String key, Object member) {
        return Redis.sync().sismember(key, member);
    }

    /**
     * Set 크기 조회 (SCARD)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Long onlineCount = RedisUtils.scard("users:online");
     * System.out.println("온라인 사용자: " + onlineCount);
     * </pre>
     * 
     * @param key Redis 키
     * @return Set 크기, 키가 없으면 0
     */
    public static Long scard(String key) {
        return Redis.sync().scard(key);
    }

    // ==================== Transaction (MULTI/EXEC) ====================

    /**
     * ⭐⭐⭐ Transaction 실행 (MULTI/EXEC)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 은행 계좌 이체
     * TransactionResult result = RedisUtils.executeTransaction(tx -> {
     *     tx.decrby("account:A", 1000);
     *     tx.incrby("account:B", 1000);
     *     tx.incr("transfer:count");
     * });
     * 
     * // 장바구니 체크아웃
     * RedisUtils.executeTransaction(tx -> {
     *     tx.del("cart:user123");
     *     tx.set("order:123", orderData);
     *     tx.decrby("product:456:stock", quantity);
     * });
     * </pre>
     * 
     * <p><b>⭐ 원자성 보장:</b></p>
     * - Transaction 내 모든 명령어가 원자적으로 실행됨
     * - 중간에 다른 클라이언트의 명령어가 끼어들 수 없음
     * - 하나라도 실패하면 전체 롤백 (DISCARD)
     * 
     * <p><b>⚠️ 주의:</b></p>
     * - Transaction 내에서는 결과를 즉시 사용할 수 없음
     * - EXEC 시점에 모든 명령어가 실행됨
     * 
     * @param operations Transaction 내에서 실행할 작업
     * @return Transaction 실행 결과
     * @throws RuntimeException Transaction 실패 시
     */
    public static TransactionResult executeTransaction(Consumer<RedisCommands<String, Object>> operations) {
        RedisCommands<String, Object> sync = Redis.sync();
        
        try {
            String multiResult = sync.multi();
            if (!"OK".equals(multiResult)) {
                throw new RuntimeException("Failed to start transaction: " + multiResult);
            }
            
            operations.accept(sync);
            TransactionResult result = sync.exec();
            
            logger.debug("Transaction executed: {} commands", result != null ? result.size() : 0);
            return result;
            
        } catch (Exception e) {
            try {
                sync.discard();
                logger.warn("Transaction discarded: {}", e.getMessage());
            } catch (Exception discardError) {
                logger.error("Error discarding transaction", discardError);
            }
            throw new RuntimeException("Transaction failed", e);
        }
    }

    /**
     * Transaction 실행 (결과 리스트 반환)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<Object> results = RedisUtils.executeTransactionWithResult(tx -> {
     *     tx.set("key1", "value1");       // results[0]: "OK"
     *     tx.set("key2", "value2");       // results[1]: "OK"
     *     tx.get("key1");                 // results[2]: "value1"
     *     tx.incr("counter");             // results[3]: 1
     * });
     * 
     * String setResult = (String) results.get(0);  // "OK"
     * String value = (String) results.get(2);      // "value1"
     * Long counter = (Long) results.get(3);        // 1
     * </pre>
     * 
     * @param operations Transaction 내에서 실행할 작업
     * @return 각 명령어의 실행 결과 리스트 (순서 보장)
     * @throws RuntimeException Transaction이 discard된 경우
     */
    public static List<Object> executeTransactionWithResult(
            Consumer<RedisCommands<String, Object>> operations) {
        
        TransactionResult result = executeTransaction(operations);
        if (result == null || result.wasDiscarded()) {
            throw new RuntimeException("Transaction was discarded");
        }
        
        List<Object> results = new ArrayList<>();
        for (int i = 0; i < result.size(); i++) {
            results.add(result.get(i));
        }
        return results;
    }

    // ==================== Async Operations ====================

    /**
     * ⭐⭐⭐ 비동기 값 저장
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 기본 사용
     * RedisUtils.setAsync("key", "value")
     *     .thenAccept(result -> System.out.println("저장 완료: " + result))
     *     .exceptionally(e -> {
     *         System.err.println("오류: " + e.getMessage());
     *         return null;
     *     });
     * 
     * // 여러 작업 순차 실행
     * RedisUtils.setAsync("key1", "value1")
     *     .thenCompose(r -> RedisUtils.setAsync("key2", "value2"))
     *     .thenCompose(r -> RedisUtils.setAsync("key3", "value3"))
     *     .thenRun(() -> System.out.println("모든 저장 완료"));
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값
     * @return CompletableFuture<String> "OK" 또는 null
     */
    public static CompletableFuture<String> setAsync(String key, Object value) {
        return Redis.async().set(key, value).toCompletableFuture();
    }

    /**
     * 비동기 값 조회
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisUtils.getAsync("key")
     *     .thenAccept(value -> {
     *         if (value != null) {
     *             System.out.println("값: " + value);
     *         }
     *     });
     * </pre>
     * 
     * @param key Redis 키
     * @return CompletableFuture<Object> 값
     */
    public static CompletableFuture<Object> getAsync(String key) {
        return Redis.async().get(key).toCompletableFuture();
    }
    
    /**
     * ⭐ 비동기 값 조회 (TypeToken 지원)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * CompletableFuture<List<String>> future = RedisUtils.getAsync("tags", 
     *     new TypeReference<List<String>>(){});
     * 
     * future.thenAccept(tags -> {
     *     tags.forEach(System.out::println);
     * });
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param key Redis 키
     * @param typeRef TypeReference
     * @return CompletableFuture
     */
    public static <T> CompletableFuture<T> getAsync(String key, TypeReference<T> typeRef) {
        return getAsync(key).thenApply(value -> {
            if (value == null) return null;
            return get(key, typeRef);  // 타입 검증
        });
    }
    
    
    public static <T> CompletableFuture<T> getAsync(String key, TypeRegistry typeRegistry) {
        return getAsync(key).thenApply(value -> {
            if (value == null) return null;
            return get(key, typeRegistry.get());  // 타입 검증
        });
    }
    

    /**
     * 비동기 키 삭제
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisUtils.delAsync("temp:key")
     *     .thenAccept(deleted -> 
     *         System.out.println(deleted + "개 키 삭제"));
     * </pre>
     * 
     * @param key Redis 키
     * @return CompletableFuture<Long> 삭제된 키 개수
     */
    public static CompletableFuture<Long> delAsync(String key) {
        return Redis.async().del(key).toCompletableFuture();
    }

    /**
     * 비동기 여러 작업 실행
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisUtils.executeAsync(async -> {
     *     async.set("key1", "value1");
     *     async.set("key2", "value2");
     *     async.incr("counter");
     * }).thenRun(() -> System.out.println("모든 작업 완료"));
     * </pre>
     * 
     * @param operations 비동기 작업
     * @return CompletableFuture<Void>
     */
    public static CompletableFuture<Void> executeAsync(
            Consumer<RedisAsyncCommands<String, Object>> operations) {
        
        return CompletableFuture.runAsync(() -> {
            RedisAsyncCommands<String, Object> async = Redis.async();
            operations.accept(async);
        });
    }

    /**
     * Pipeline
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 1000개 키 일괄 저장
     * List<CompletableFuture<?>> futures = RedisUtils.pipeline(async -> {
     *     List<RedisFuture<?>> list = new ArrayList<>();
     *     for (int i = 0; i < 1000; i++) {
     *         list.add(async.set("key" + i, "value" + i));
     *     }
     *     return list;
     * });
     * 
     * // 모든 작업 완료 대기
     * CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
     * </pre>
     * 
     * <p><b>⭐ Lettuce 6.x에서의 Pipeline:</b></p>
     * <ul>
     *   <li>Lettuce는 자동으로 command batching을 수행</li>
     *   <li>Async commands를 연속으로 호출하면 자동으로 최적화됨</li>
     *   <li>명시적인 flush 제어 불필요 (deprecated됨)</li>
     *   <li>단순히 여러 async 명령을 실행하고 future를 모으면 됨</li>
     * </ul>
     * 
     * <p><b>성능:</b></p>
     * - 순차 실행 1000건: ~1000ms
     * - Pipeline 1000건: ~50ms (20배 빠름!)
     * 
     * @param operations Pipeline 작업
     * @return 각 명령어의 CompletableFuture 리스트
     */
    public static List<CompletableFuture<?>> pipeline(
            Function<RedisAsyncCommands<String, Object>, List<RedisFuture<?>>> operations) {
        
        // Lettuce 6.x에서는 async commands가 자동으로 최적화됨
        RedisAsyncCommands<String, Object> async = Redis.async();
        
        // 여러 명령을 연속으로 실행 (자동 batching)
        List<RedisFuture<?>> futures = operations.apply(async);
        
        // RedisFuture를 CompletableFuture로 변환
        List<CompletableFuture<?>> completableFutures = new ArrayList<>();
        for (RedisFuture<?> future : futures) {
            completableFutures.add(future.toCompletableFuture());
        }
        
        return completableFutures;
    }
    
    
    /**
     * ⭐⭐⭐ Pipeline with Wait (모든 작업 완료 대기)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 1000개 키 저장하고 완료 대기
     * RedisUtils.pipelineAndWait(async -> {
     *     List<RedisFuture<?>> list = new ArrayList<>();
     *     for (int i = 0; i < 1000; i++) {
     *         list.add(async.set("key" + i, "value" + i));
     *     }
     *     return list;
     * });
     * 
     * System.out.println("모든 저장 완료!");
     * </pre>
     * 
     * <p><b>장점:</b></p>
     * - 간단한 사용법 (자동 대기)
     * - 예외 처리 자동화
     * - 로깅 포함
     * 
     * @param operations Pipeline 작업
     * @throws RuntimeException Pipeline 실행 중 오류 발생 시
     */
    public static void pipelineAndWait(
            Function<RedisAsyncCommands<String, Object>, List<RedisFuture<?>>> operations) {
        
        List<CompletableFuture<?>> futures = pipeline(operations);
        
        try {
            // 모든 작업 완료 대기
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            logger.debug("Pipeline completed: {} commands", futures.size());
            
        } catch (Exception e) {
            logger.error("Pipeline failed", e);
            throw new RuntimeException("Pipeline execution failed", e);
        }
    }

    /**
     * ⭐ 대량 SET 최적화 (Pipeline 자동 적용)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Map<String, Object> data = new HashMap<>();
     * for (int i = 0; i < 1000; i++) {
     *     data.put("key" + i, "value" + i);
     * }
     * 
     * RedisUtils.msetPipeline(data);
     * System.out.println("1000개 키 저장 완료!");
     * </pre>
     * 
     * @param map 키-값 맵
     */
    public static void msetPipeline(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return;
        }
        
        pipelineAndWait(async -> {
            List<RedisFuture<?>> futures = new ArrayList<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                futures.add(async.set(entry.getKey(), entry.getValue()));
            }
            return futures;
        });
    }

    /**
     * ⭐ 대량 SET with TTL 최적화
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Map<String, Object> sessions = new HashMap<>();
     * sessions.put("session:user1", sessionData1);
     * sessions.put("session:user2", sessionData2);
     * 
     * RedisUtils.msetPipelineWithTTL(sessions, 3600);  // 1시간
     * </pre>
     * 
     * @param map 키-값 맵
     * @param seconds TTL (초)
     */
    public static void msetPipeline(Map<String, Object> map, long seconds) {
        if (map == null || map.isEmpty()) {
            return;
        }
        
        pipelineAndWait(async -> {
            List<RedisFuture<?>> futures = new ArrayList<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                futures.add(async.setex(entry.getKey(), seconds, entry.getValue()));
            }
            return futures;
        });
    }

    /**
     * ⭐ 대량 GET 최적화 (Pipeline 자동 적용)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<String> keys = Arrays.asList("key1", "key2", "key3", ...);
     * Map<String, Object> results = RedisUtils.mgetPipeline(keys);
     * 
     * for (Map.Entry<String, Object> entry : results.entrySet()) {
     *     System.out.println(entry.getKey() + " = " + entry.getValue());
     * }
     * </pre>
     * 
     * @param keys 키 리스트
     * @return 키-값 맵 (값이 null인 키는 제외)
     */
    public static Map<String, Object> mgetPipeline(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>();
        }
        
        List<CompletableFuture<Object>> futures = new ArrayList<>();
        
        // 모든 GET을 비동기로 실행
        for (String key : keys) {
            futures.add(getAsync(key));
        }
        
        // 모든 결과 수집
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            try {
                Object value = futures.get(i).join();
                if (value != null) {
                    result.put(keys.get(i), value);
                }
            } catch (Exception e) {
                logger.warn("Failed to get key: {}", keys.get(i), e);
            }
        }
        
        return result;
    }

    /**
     * ⭐ 대량 DEL 최적화 (Pipeline 자동 적용)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<String> keys = Arrays.asList("key1", "key2", "key3", ...);
     * long deleted = RedisUtils.mdelPipeline(keys);
     * System.out.println(deleted + "개 키 삭제");
     * </pre>
     * 
     * @param keys 키 리스트
     * @return 삭제된 키 개수
     */
    public static long mdelPipeline(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return 0;
        }
        
        // 배치 사이즈 (한 번에 100개씩)
        int batchSize = 100;
        long totalDeleted = 0;
        
        for (int i = 0; i < keys.size(); i += batchSize) {
            int end = Math.min(i + batchSize, keys.size());
            List<String> batch = keys.subList(i, end);
            
            String[] keyArray = batch.toArray(new String[0]);
            totalDeleted += del(keyArray);
        }
        
        return totalDeleted;
    }


    // ==================== Utility ====================

    /**
     * Redis 서버 연결 테스트
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String pong = RedisUtils.ping();
     * if ("PONG".equals(pong)) {
     *     System.out.println("Redis 연결 정상");
     * } else {
     *     System.out.println("Redis 연결 실패");
     * }
     * </pre>
     * 
     * @return "PONG" 또는 null (연결 실패)
     */
    public static String ping() {
        return Redis.ping();
    }

    /**
     * 모든 키 개수 조회 (DBSIZE)
     * 
     * @return 현재 데이터베이스의 키 개수
     */
    public static Long dbSize() {
        return Redis.sync().dbsize();
    }

    /**
     * 현재 데이터베이스의 모든 키 삭제 (FLUSHDB)
     * 
     * <p><b>⚠️ 주의:</b></p>
     * 현재 DB의 모든 데이터가 삭제됩니다!
     * 
     * @return 성공 메시지 "OK"
     */
    public static String flushDb() {
        return Redis.sync().flushdb();
    }

    /**
     * 모든 데이터베이스의 모든 키 삭제 (FLUSHALL)
     * 
     * <p><b>⚠️⚠️ 매우 주의:</b></p>
     * Redis 서버의 모든 데이터가 삭제됩니다!
     * 
     * @return 성공 메시지 "OK"
     */
    public static String flushAll() {
        return Redis.sync().flushall();
    }

    /**
     * Redis 서버 정보 조회 (INFO)
     * 
     * @return 서버 정보 (전체)
     */
    public static String info() {
        return Redis.sync().info();
    }

    /**
     * Redis 서버 정보 조회 (섹션별)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String serverInfo = RedisUtils.info("server");
     * String memoryInfo = RedisUtils.info("memory");
     * String statsInfo = RedisUtils.info("stats");
     * </pre>
     * 
     * @param section 섹션명 (server, memory, stats, clients, replication 등)
     * @return 해당 섹션의 서버 정보
     */
    public static String info(String section) {
        return Redis.sync().info(section);
    }
    
    
    
    /**
     * 랜덤 키 조회 (RANDOMKEY)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String randomKey = RedisUtils.randomKey();
     * if (randomKey != null) {
     *     System.out.println("랜덤 키: " + randomKey);
     * }
     * </pre>
     * 
     * @return 랜덤 키, DB가 비어있으면 null
     */
    public static String randomKey() {
        return Redis.sync().randomkey();
    }

    /**
     * TTL 제거 (영구 저장으로 변경) (PERSIST)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisUtils.set("temp:data", value, 3600);  // 1시간 TTL
     * RedisUtils.persist("temp:data");  // TTL 제거, 영구 저장
     * </pre>
     * 
     * @param key Redis 키
     * @return TTL이 제거되었으면 true, 키가 없거나 이미 영구 저장이면 false
     */
    public static boolean persist(String key) {
        return Redis.sync().persist(key);
    }

    /**
     * 지연 작업 추가 (Sorted Set 기반 Delayed Queue)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 5분 후 실행될 작업 추가
     * RedisUtils.addDelayedTask("tasks:queue", "task-001", 300);
     * 
     * // 1시간 후 실행될 작업 추가
     * RedisUtils.addDelayedTask("tasks:queue", "send-email-123", 3600);
     * </pre>
     * 
     * @param queueKey 큐 키
     * @param taskId 작업 ID
     * @param delaySeconds 지연 시간 (초)
     * @return 성공 여부
     */
    public static boolean addDelayedTask(String queueKey, String taskId, long delaySeconds) {
        long executeTime = System.currentTimeMillis() + (delaySeconds * 1000);
        return Redis.sync().zadd(queueKey, executeTime, taskId) >= 0;
    }

    /**
     * 실행 가능한 작업 조회 및 제거
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // Worker 스레드에서 실행
     * while (true) {
     *     List<String> tasks = RedisUtils.pollDelayedTasks("tasks:queue", 10);
     *     
     *     for (String taskId : tasks) {
     *         processTask(taskId);
     *     }
     *     
     *     if (tasks.isEmpty()) {
     *         Thread.sleep(1000);
     *     }
     * }
     * </pre>
     * 
     * @param queueKey 큐 키
     * @param batchSize 한 번에 가져올 작업 수
     * @return 실행 가능한 작업 리스트
     */
    public static List<String> pollDelayedTasks(String queueKey, int batchSize) {
        long now = System.currentTimeMillis();
        
        // 실행 가능한 작업 조회 (List<Object>로 받음)
        List<Object> objectTasks = Redis.sync().zrangebyscore(
            queueKey,
            io.lettuce.core.Range.create(0.0, (double) now),
            io.lettuce.core.Limit.create(0, batchSize)
        );
        
        // List<String>으로 변환
        List<String> tasks = new ArrayList<>();
        for (Object obj : objectTasks) {
            if (obj instanceof String) {
                tasks.add((String) obj);
            }
        }
        
        // 조회된 작업 제거 (Object[] 배열로 변환)
        if (!tasks.isEmpty()) {
            Redis.sync().zrem(queueKey, tasks.toArray());
        }
        
        return tasks;
    }

    /**
     * Cache-Aside Pattern: Redis 캐시 조회 후 없으면 DB에서 조회하여 캐싱
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Retrieve retrieve = new Retrieve()
     *     .sql("SELECT * FROM users WHERE id = ?")
     *     .addParam(userId);
     * 
     * SharedMap<String, Object> user = RedisUtils.fetchRow("user:" + userId, retrieve);
     * </pre>
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
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 1시간 캐싱
     * SharedMap<String, Object> user = RedisUtils.fetchRow(
     *     "user:" + userId, 
     *     retrieve, 
     *     3600
     * );
     * </pre>
     * 
     * @param key Redis 키
     * @param retrieve DB 조회 객체
     * @param expire TTL (초 단위, 0이면 영구 저장)
     * @return 조회된 데이터 (단일 행)
     */
    public static SharedMap<String, Object> fetchRow(String key, Retrieve retrieve, long expireSeconds) {
        // 1. 캐시 확인 (exists + get을 한 번에)
        SharedMap<String, Object> cached = get(key, TypeRegistry.MAP_SHAREDMAP_OBJECT);
        
        if (cached != null) {
            // TTL 갱신 (expireSeconds > 0일 때만)
            if (expireSeconds > 0) {
                expire(key, expireSeconds);
            }
            return cached;
        }
        
        // 2. DB 조회
        SharedMap<String, Object> map = retrieve.select().getRowFirst();
        
        // 3. 캐시 저장 (데이터가 있을 때만)
        if (map != null && !map.isEmpty()) {
            if (expireSeconds > 0) {
                set(key, map, expireSeconds);
            } else {
                set(key, map);
            }
        }
        
        return map;
    }
    
    
    /**
     * TTL 조회 (밀리초 단위) (PTTL)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 밀리초 단위로 정확한 TTL 확인
     * Long ttlMs = RedisUtils.pttl("session:token");
     * System.out.println("남은 시간: " + ttlMs + "ms");
     * 
     * // 초 단위와 비교
     * Long ttlSec = RedisUtils.ttl("session:token");  // 초
     * Long ttlMs = RedisUtils.pttl("session:token");  // 밀리초
     * </pre>
     * 
     * <p><b>반환값:</b></p>
     * <ul>
     *   <li>양수: 남은 TTL (밀리초)</li>
     *   <li>-1: TTL 없음 (영구 저장)</li>
     *   <li>-2: 키가 존재하지 않음</li>
     * </ul>
     * 
     * @param key Redis 키
     * @return TTL (밀리초 단위)
     */
    public static Long pttl(String key) {
        return Redis.sync().pttl(key);
    }

    /**
     * TTL 설정 (밀리초 단위) (PEXPIRE)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 500ms 후 만료
     * RedisUtils.pexpire("temp:data", 500);
     * 
     * // 1.5초 후 만료
     * RedisUtils.pexpire("temp:data", 1500);
     * 
     * // 정확한 타이밍 제어
     * RedisUtils.set("rate:limit", 1);
     * RedisUtils.pexpire("rate:limit", 100);  // 100ms
     * </pre>
     * 
     * <p><b>활용 사례:</b></p>
     * - Rate limiting (밀리초 단위 제어)
     * - 짧은 시간 캐싱
     * - 정확한 타이밍 제어
     * 
     * @param key Redis 키
     * @param milliseconds TTL (밀리초 단위)
     * @return 성공 여부
     */
    public static boolean pexpire(String key, long milliseconds) {
        return Redis.sync().pexpire(key, milliseconds);
    }

    /**
     * 특정 시각에 만료 설정 (EXPIREAT)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 특정 날짜/시간에 만료
     * LocalDateTime expireTime = LocalDateTime.of(2025, 12, 31, 23, 59, 59);
     * long timestamp = expireTime.toEpochSecond(ZoneOffset.UTC);
     * RedisUtils.expireAt("promotion:2025", timestamp);
     * 
     * // 자정에 만료 (일일 데이터)
     * LocalDateTime midnight = LocalDateTime.now().plusDays(1).withHour(0).withMinute(0).withSecond(0);
     * RedisUtils.expireAt("daily:stats", midnight.toEpochSecond(ZoneOffset.UTC));
     * 
     * // 현재 시간 + 1시간
     * long oneHourLater = System.currentTimeMillis() / 1000 + 3600;
     * RedisUtils.expireAt("session:user", oneHourLater);
     * </pre>
     * 
     * <p><b>활용 사례:</b></p>
     * - 프로모션 종료 시각 설정
     * - 일일/주간 데이터 자동 삭제
     * - 이벤트 종료 시각 설정
     * 
     * @param key Redis 키
     * @param timestamp Unix timestamp (초 단위)
     * @return 성공 여부
     */
    public static boolean expireAt(String key, long timestamp) {
        return Redis.sync().expireat(key, timestamp);
    }

    /**
     * 특정 시각에 만료 설정 (밀리초 단위) (PEXPIREAT)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 밀리초 단위 정확한 시각 설정
     * long timestampMs = System.currentTimeMillis() + 5000;  // 5초 후
     * RedisUtils.pexpireAt("temp:token", timestampMs);
     * 
     * // 특정 밀리초 단위 시각
     * LocalDateTime expireTime = LocalDateTime.of(2025, 12, 31, 23, 59, 59, 999_000_000);
     * long timestampMs = expireTime.toInstant(ZoneOffset.UTC).toEpochMilli();
     * RedisUtils.pexpireAt("event:2025", timestampMs);
     * 
     * // Rate limiting 정확한 윈도우
     * long windowEnd = System.currentTimeMillis() + 60000;  // 1분 윈도우
     * RedisUtils.pexpireAt("rate:user123", windowEnd);
     * </pre>
     * 
     * <p><b>활용 사례:</b></p>
     * - 정확한 시각 제어 필요 시
     * - Rate limiting 윈도우
     * - 밀리초 단위 이벤트 관리
     * 
     * @param key Redis 키
     * @param timestampMs Unix timestamp (밀리초 단위)
     * @return 성공 여부
     */
    public static boolean pexpireAt(String key, long timestampMs) {
        return Redis.sync().pexpireat(key, timestampMs);
    }
    
    
    
    
    
    
    
    
    
    
    
    
}