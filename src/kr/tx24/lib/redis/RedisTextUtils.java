package kr.tx24.lib.redis;

import java.util.ArrayList;
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
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;

/**
 * Redis String 유틸리티 클래스 (Lettuce 6.2.x, Redis 7.x)
 * 
 * <p><b>주요 특징:</b></p>
 * <ul>
 *   <li>⭐ StringCodec 사용: Key와 Value 모두 String 타입</li>
 *   <li>⭐ 객체 직렬화 불필요: 순수 String 처리</li>
 *   <li>기본 CRUD 연산 (String only)</li>
 *   <li>Hash, List, Set 연산</li>
 *   <li>Transaction 지원 (MULTI/EXEC)</li>
 *   <li>Async 연산 지원 (CompletableFuture)</li>
 *   <li>Pipeline 지원 (대량 작업 최적화)</li>
 *   <li>패턴 매칭 및 대량 삭제</li>
 *   <li>TTL 관리</li>
 *   <li>Increment/Decrement</li>
 * </ul>
 * 
 * <p><b>⭐ close() 불필요:</b></p>
 * Connection은 자동으로 재사용됩니다. 각 메서드 호출 후 close() 할 필요 없습니다.
 * 
 * <p><b>사용 예:</b></p>
 * <pre>
 * // 기본 사용
 * RedisTextUtils.set("key", "value");
 * String value = RedisTextUtils.get("key");
 * 
 * // JSON 처리 (수동)
 * String json = "{\"name\":\"John\", \"age\":30}";
 * RedisTextUtils.set("user:1", json);
 * String userJson = RedisTextUtils.get("user:1");
 * // ObjectMapper로 역직렬화...
 * 
 * // Transaction
 * RedisTextUtils.executeTransaction(tx -> {
 *     tx.set("key1", "value1");
 *     tx.set("key2", "value2");
 * });
 * 
 * // Async
 * RedisTextUtils.setAsync("key", "value")
 *     .thenAccept(result -> System.out.println("Done"));
 * </pre>
 * 
 * @author TX24
 * @version 1.0
 * @see RedisText
 */
public final class RedisTextUtils {

    private static final Logger logger = LoggerFactory.getLogger(RedisTextUtils.class);

    private RedisTextUtils() {
        throw new UnsupportedOperationException("Utility class");
    }


    /**
     * 값 저장 (기본)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisTextUtils.set("user:name", "John");
     * RedisTextUtils.set("user:age", "30");
     * 
     * // JSON 저장
     * String json = objectMapper.writeValueAsString(user);
     * RedisTextUtils.set("user:123", json);
     * </pre>
     * 
     * @param key Redis 키 (null 불가)
     * @param value 값 (String)
     * @throws IllegalArgumentException key가 null인 경우
     */
    public static void set(String key, String value) {
        RedisText.sync().set(key, value);
    }

    /**
     * 값 저장 (TTL 설정)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 세션 저장 (30분)
     * RedisTextUtils.set("session:token", sessionJson, 1800);
     * 
     * // 캐시 저장 (1시간)
     * RedisTextUtils.set("cache:product:123", productJson, 3600);
     * 
     * // 일회용 토큰 (5분)
     * RedisTextUtils.set("otp:user123", "123456", 300);
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값 (String)
     * @param seconds TTL (초 단위, 0보다 커야 함)
     * @throws IllegalArgumentException seconds가 0 이하인 경우
     */
    public static void set(String key, String value, long seconds) {
        RedisText.sync().setex(key, seconds, value);
    }

    /**
     * 키가 없을 때만 성공 처리 (SET NX)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 분산 락 획득
     * boolean acquired = RedisTextUtils.setNx("lock:resource", "owner123");
     * if (acquired) {
     *     try {
     *         // 크리티컬 섹션
     *         processResource();
     *     } finally {
     *         RedisTextUtils.del("lock:resource");
     *     }
     * }
     * 
     * // 중복 방지
     * boolean isNew = RedisTextUtils.setNx("processed:order:123", "true");
     * if (isNew) {
     *     processOrder(123);
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값 (String)
     * @return 키가 없어서 설정에 성공하면 true, 키가 이미 존재하면 false
     */
    public static boolean setNx(String key, String value) {
        String result = RedisText.sync().set(key, value, SetArgs.Builder.nx());
        return "OK".equals(result);
    }

    /**
     * 키가 없을 때만 성공 처리, TTL 포함
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 분산 락 (5초 타임아웃)
     * boolean acquired = RedisTextUtils.setNx("lock:payment", "owner", 5);
     * 
     * // Rate limiting (1분에 1번)
     * boolean canProcess = RedisTextUtils.setNx("rate:user123", "1", 60);
     * if (canProcess) {
     *     processRequest();
     * } else {
     *     throw new RateLimitException();
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값 (String)
     * @param seconds TTL (초 단위)
     * @return 키가 없어서 설정에 성공하면 true, 키가 이미 존재하면 false
     */
    public static boolean setNx(String key, String value, long seconds) {
        String result = RedisText.sync().set(key, value, SetArgs.Builder.nx().ex(seconds));
        return "OK".equals(result);
    }

    /**
     * 값 조회
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String name = RedisTextUtils.get("user:name");
     * 
     * // JSON 조회
     * String userJson = RedisTextUtils.get("user:123");
     * if (userJson != null) {
     *     User user = objectMapper.readValue(userJson, User.class);
     * }
     * </pre>
     * 
     * @param key Redis 키
     * @return 값 (String), 키가 없으면 null
     */
    public static String get(String key) {
        return RedisText.sync().get(key);
    }

    /**
     * 여러 키 조회 (MGET)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<KeyValue<String, String>> results = RedisTextUtils.mget("key1", "key2", "key3");
     * for (KeyValue<String, String> kv : results) {
     *     System.out.println(kv.getKey() + " = " + kv.getValue());
     * }
     * </pre>
     * 
     * @param keys Redis 키 목록
     * @return KeyValue 리스트 (값이 없는 키는 value가 null)
     */
    public static List<KeyValue<String, String>> mget(String... keys) {
        return RedisText.sync().mget(keys);
    }

    /**
     * 여러 키-값 저장 (MSET)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Map<String, String> data = new HashMap<>();
     * data.put("user:name", "John");
     * data.put("user:age", "30");
     * data.put("user:email", "john@example.com");
     * RedisTextUtils.mset(data);
     * </pre>
     * 
     * @param map 저장할 키-값 Map
     */
    public static void mset(Map<String, String> map) {
        RedisText.sync().mset(map);
    }

    /**
     * 키 삭제
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * long deleted = RedisTextUtils.del("key1", "key2", "key3");
     * System.out.println("Deleted: " + deleted + " keys");
     * </pre>
     * 
     * @param keys 삭제할 키 목록
     * @return 삭제된 키 개수
     */
    public static long del(String... keys) {
        return RedisText.sync().del(keys);
    }
    
    
    public static Long del(List<String> keys) {
    	if(keys == null || keys.isEmpty()) {
    		return 0L;
    	}
    	return Redis.sync().del(keys.toArray(new String[0]));
    }

    /**
     * 키 존재 확인
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * if (RedisTextUtils.exists("user:123")) {
     *     // 키가 존재함
     * }
     * </pre>
     * 
     * @param keys 확인할 키 목록
     * @return 존재하는 키 개수
     */
    public static long exists(String... keys) {
        return RedisText.sync().exists(keys);
    }

    /**
     * TTL 조회 (초 단위)
     * 
     * <p><b>반환값:</b></p>
     * <ul>
     *   <li>양수: 남은 TTL (초)</li>
     *   <li>-1: TTL 없음 (영구 저장)</li>
     *   <li>-2: 키가 존재하지 않음</li>
     * </ul>
     * 
     * @param key Redis 키
     * @return TTL (초 단위)
     */
    public static Long ttl(String key) {
        return RedisText.sync().ttl(key);
    }

    /**
     * TTL 설정
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisTextUtils.expire("session:token", 1800);  // 30분
     * </pre>
     * 
     * @param key Redis 키
     * @param seconds TTL (초 단위)
     * @return 성공 여부
     */
    public static boolean expire(String key, long seconds) {
        return RedisText.sync().expire(key, seconds);
    }

    /**
     * TTL 제거 (영구 저장으로 변경)
     * 
     * @param key Redis 키
     * @return 성공 여부
     */
    public static boolean persist(String key) {
        return RedisText.sync().persist(key);
    }

    // ==================== Increment/Decrement ====================

    /**
     * 정수 값 증가 (기본 +1)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * long views = RedisTextUtils.incr("page:views");
     * System.out.println("Total views: " + views);
     * </pre>
     * 
     * @param key Redis 키
     * @return 증가된 값
     */
    public static long incr(String key) {
        return RedisText.sync().incr(key);
    }

    /**
     * 정수 값 증가 (지정된 값만큼)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * long score = RedisTextUtils.incrBy("user:score", 10);
     * </pre>
     * 
     * @param key Redis 키
     * @param amount 증가량
     * @return 증가된 값
     */
    public static long incrBy(String key, long amount) {
        return RedisText.sync().incrby(key, amount);
    }

    /**
     * 실수 값 증가
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * double price = RedisTextUtils.incrByFloat("product:price", 10.5);
     * </pre>
     * 
     * @param key Redis 키
     * @param amount 증가량
     * @return 증가된 값
     */
    public static double incrByFloat(String key, double amount) {
        return RedisText.sync().incrbyfloat(key, amount);
    }

    /**
     * 정수 값 감소 (기본 -1)
     * 
     * @param key Redis 키
     * @return 감소된 값
     */
    public static long decr(String key) {
        return RedisText.sync().decr(key);
    }

    /**
     * 정수 값 감소 (지정된 값만큼)
     * 
     * @param key Redis 키
     * @param amount 감소량
     * @return 감소된 값
     */
    public static long decrBy(String key, long amount) {
        return RedisText.sync().decrby(key, amount);
    }

    // ==================== Hash Operations ====================

    /**
     * Hash 필드 저장
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisTextUtils.hset("user:123", "name", "John");
     * RedisTextUtils.hset("user:123", "age", "30");
     * </pre>
     * 
     * @param key Hash 키
     * @param field 필드명
     * @param value 값
     * @return 새 필드면 true, 업데이트면 false
     */
    public static boolean hset(String key, String field, String value) {
        return RedisText.sync().hset(key, field, value);
    }

    /**
     * Hash 여러 필드 저장
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Map<String, String> user = new HashMap<>();
     * user.put("name", "John");
     * user.put("age", "30");
     * user.put("email", "john@example.com");
     * RedisTextUtils.hmset("user:123", user);
     * </pre>
     * 
     * @param key Hash 키
     * @param map 필드-값 Map
     */
    public static void hmset(String key, Map<String, String> map) {
        RedisText.sync().hmset(key, map);
    }

    /**
     * Hash 필드 조회
     * 
     * @param key Hash 키
     * @param field 필드명
     * @return 값 (없으면 null)
     */
    public static String hget(String key, String field) {
        return RedisText.sync().hget(key, field);
    }

    /**
     * Hash 여러 필드 조회
     * 
     * @param key Hash 키
     * @param fields 필드명 목록
     * @return 값 리스트
     */
    public static List<KeyValue<String, String>> hmget(String key, String... fields) {
        return RedisText.sync().hmget(key, fields);
    }

    /**
     * Hash 전체 조회
     * 
     * @param key Hash 키
     * @return 전체 필드-값 Map
     */
    public static Map<String, String> hgetall(String key) {
        return RedisText.sync().hgetall(key);
    }

    /**
     * Hash 필드 삭제
     * 
     * @param key Hash 키
     * @param fields 삭제할 필드명
     * @return 삭제된 필드 개수
     */
    public static long hdel(String key, String... fields) {
        return RedisText.sync().hdel(key, fields);
    }

    /**
     * Hash 필드 존재 확인
     * 
     * @param key Hash 키
     * @param field 필드명
     * @return 존재하면 true
     */
    public static boolean hexists(String key, String field) {
        return RedisText.sync().hexists(key, field);
    }

    /**
     * Hash 필드 개수
     * 
     * @param key Hash 키
     * @return 필드 개수
     */
    public static long hlen(String key) {
        return RedisText.sync().hlen(key);
    }

    /**
     * Hash 모든 필드명 조회
     * 
     * @param key Hash 키
     * @return 필드명 리스트
     */
    public static List<String> hkeys(String key) {
        return RedisText.sync().hkeys(key);
    }

    /**
     * Hash 모든 값 조회
     * 
     * @param key Hash 키
     * @return 값 리스트
     */
    public static List<String> hvals(String key) {
        return RedisText.sync().hvals(key);
    }

    /**
     * Hash 필드 값 증가
     * 
     * @param key Hash 키
     * @param field 필드명
     * @param amount 증가량
     * @return 증가된 값
     */
    public static long hincrBy(String key, String field, long amount) {
        return RedisText.sync().hincrby(key, field, amount);
    }

    /**
     * Hash 필드 값 증가 (실수)
     * 
     * @param key Hash 키
     * @param field 필드명
     * @param amount 증가량
     * @return 증가된 값
     */
    public static double hincrByFloat(String key, String field, double amount) {
        return RedisText.sync().hincrbyfloat(key, field, amount);
    }

    // ==================== List Operations ====================

    /**
     * List 왼쪽에 추가 (LPUSH)
     * 
     * @param key List 키
     * @param values 추가할 값들
     * @return List 길이
     */
    public static long lpush(String key, String... values) {
        return RedisText.sync().lpush(key, values);
    }

    /**
     * List 오른쪽에 추가 (RPUSH)
     * 
     * @param key List 키
     * @param values 추가할 값들
     * @return List 길이
     */
    public static long rpush(String key, String... values) {
        return RedisText.sync().rpush(key, values);
    }

    /**
     * List 왼쪽에서 제거 (LPOP)
     * 
     * @param key List 키
     * @return 제거된 값 (없으면 null)
     */
    public static String lpop(String key) {
        return RedisText.sync().lpop(key);
    }
    
    public static List<String> lpop(String key, long count) {
        return RedisText.sync().lpop(key, count);
    }
    
    
    public static String blpop(String key, long maxWaitSeconds) {
    	final long startTime = System.currentTimeMillis();
        final long MAX_WAIT_MS = maxWaitSeconds * 1000L;
        
        //서버에 요청할 짧은 BLPOP 대기 시간 (클라이언트 10초보다 짧게 설정한다.)
        final long SERVER_TIMEOUT_SECONDS = 5;
        
        while(System.currentTimeMillis() - startTime < MAX_WAIT_MS) {
            try {
                // 서버에 5초 대기 요청
                KeyValue<String, String> result = RedisText.sync().blpop(SERVER_TIMEOUT_SECONDS, key);
                
                // 데이터 수신 성공 시 즉시 반환
                if (result != null && result.getValue() != null) {
                    return result.getValue(); 
                }
                // null 반환: 5초 서버 타임아웃 발생 -> 루프 재시작하여 경과 시간 체크
                
            } catch (Exception e) {
                // 10초 클라이언트 Timeout 발생에 대한 예외 처리 (RedisCommandTimeoutException)
            }
        }
        
        return "";
    }


    /**
     * List 오른쪽에서 제거 (RPOP)
     * 
     * @param key List 키
     * @return 제거된 값 (없으면 null)
     */
    public static String rpop(String key) {
        return RedisText.sync().rpop(key);
    }
    
    public static List<String> rpop(String key, long count) {
        return RedisText.sync().rpop(key, count);
    }
    
    
    public static String brpop(String key, long maxWaitSeconds) {
    	final long startTime = System.currentTimeMillis();
        final long MAX_WAIT_MS = maxWaitSeconds * 1000L;
        
        //서버에 요청할 짧은 BLPOP 대기 시간 (클라이언트 10초보다 짧게 설정한다.)
        final long SERVER_TIMEOUT_SECONDS = 5;
        
        while(System.currentTimeMillis() - startTime < MAX_WAIT_MS) {
            try {
                // 서버에 5초 대기 요청
                KeyValue<String, String> result = RedisText.sync().brpop(SERVER_TIMEOUT_SECONDS, key);
                
                // 데이터 수신 성공 시 즉시 반환
                if (result != null && result.getValue() != null) {
                    return result.getValue(); 
                }
                // null 반환: 5초 서버 타임아웃 발생 -> 루프 재시작하여 경과 시간 체크
                
            } catch (Exception e) {
                // 10초 클라이언트 Timeout 발생에 대한 예외 처리 (RedisCommandTimeoutException)
            }
        }
        
        return "";
    }
    

    /**
     * List 범위 조회 (LRANGE)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 전체 조회
     * List<String> all = RedisTextUtils.lrange("mylist", 0, -1);
     * 
     * // 처음 10개
     * List<String> top10 = RedisTextUtils.lrange("mylist", 0, 9);
     * </pre>
     * 
     * @param key List 키
     * @param start 시작 인덱스
     * @param stop 끝 인덱스 (-1은 마지막)
     * @return 값 리스트
     */
    public static List<String> lrange(String key, long start, long stop) {
        return RedisText.sync().lrange(key, start, stop);
    }

    /**
     * List 길이 (LLEN)
     * 
     * @param key List 키
     * @return List 길이
     */
    public static long llen(String key) {
        return RedisText.sync().llen(key);
    }

    /**
     * List 인덱스 조회 (LINDEX)
     * 
     * @param key List 키
     * @param index 인덱스
     * @return 값 (없으면 null)
     */
    public static String lindex(String key, long index) {
        return RedisText.sync().lindex(key, index);
    }

    // ==================== Set Operations ====================

    /**
     * Set에 추가 (SADD)
     * 
     * @param key Set 키
     * @param members 추가할 값들
     * @return 추가된 개수
     */
    public static long sadd(String key, String... members) {
        return RedisText.sync().sadd(key, members);
    }

    /**
     * Set에서 제거 (SREM)
     * 
     * @param key Set 키
     * @param members 제거할 값들
     * @return 제거된 개수
     */
    public static long srem(String key, String... members) {
        return RedisText.sync().srem(key, members);
    }

    /**
     * Set 멤버 확인 (SISMEMBER)
     * 
     * @param key Set 키
     * @param member 확인할 값
     * @return 존재하면 true
     */
    public static boolean sismember(String key, String member) {
        return RedisText.sync().sismember(key, member);
    }

    /**
     * Set 전체 조회 (SMEMBERS)
     * 
     * @param key Set 키
     * @return 전체 멤버 Set
     */
    public static Set<String> smembers(String key) {
        return RedisText.sync().smembers(key);
    }

    /**
     * Set 크기 (SCARD)
     * 
     * @param key Set 키
     * @return Set 크기
     */
    public static long scard(String key) {
        return RedisText.sync().scard(key);
    }

    /**
     * Set 랜덤 멤버 조회 (SRANDMEMBER)
     * 
     * @param key Set 키
     * @return 랜덤 멤버 (없으면 null)
     */
    public static String srandmember(String key) {
        return RedisText.sync().srandmember(key);
    }

    /**
     * Set 랜덤 멤버 제거 (SPOP)
     * 
     * @param key Set 키
     * @return 제거된 멤버 (없으면 null)
     */
    public static String spop(String key) {
        return RedisText.sync().spop(key);
    }

    // ==================== Sorted Set Operations ====================

    /**
     * Sorted Set에 추가 (ZADD)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisTextUtils.zadd("leaderboard", 100, "player1");
     * RedisTextUtils.zadd("leaderboard", 200, "player2");
     * </pre>
     * 
     * @param key Sorted Set 키
     * @param score 점수
     * @param member 멤버
     * @return 추가된 개수
     */
    public static long zadd(String key, double score, String member) {
        return RedisText.sync().zadd(key, score, member);
    }

    /**
     * Sorted Set에서 제거 (ZREM)
     * 
     * @param key Sorted Set 키
     * @param members 제거할 멤버들
     * @return 제거된 개수
     */
    public static long zrem(String key, String... members) {
        return RedisText.sync().zrem(key, members);
    }

    /**
     * Sorted Set 범위 조회 (ZRANGE)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 상위 10명
     * List<String> top10 = RedisTextUtils.zrange("leaderboard", 0, 9);
     * </pre>
     * 
     * @param key Sorted Set 키
     * @param start 시작 인덱스
     * @param stop 끝 인덱스
     * @return 멤버 리스트 (점수 순)
     */
    public static List<String> zrange(String key, long start, long stop) {
        return RedisText.sync().zrange(key, start, stop);
    }

    /**
     * Sorted Set 역순 범위 조회 (ZREVRANGE)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 하위 10명
     * List<String> bottom10 = RedisTextUtils.zrevrange("leaderboard", 0, 9);
     * </pre>
     * 
     * @param key Sorted Set 키
     * @param start 시작 인덱스
     * @param stop 끝 인덱스
     * @return 멤버 리스트 (점수 역순)
     */
    public static List<String> zrevrange(String key, long start, long stop) {
        return RedisText.sync().zrevrange(key, start, stop);
    }

    /**
     * Sorted Set 크기 (ZCARD)
     * 
     * @param key Sorted Set 키
     * @return Sorted Set 크기
     */
    public static long zcard(String key) {
        return RedisText.sync().zcard(key);
    }

    /**
     * Sorted Set 점수 조회 (ZSCORE)
     * 
     * @param key Sorted Set 키
     * @param member 멤버
     * @return 점수 (없으면 null)
     */
    public static Double zscore(String key, String member) {
        return RedisText.sync().zscore(key, member);
    }

    /**
     * Sorted Set 순위 조회 (ZRANK)
     * 
     * @param key Sorted Set 키
     * @param member 멤버
     * @return 순위 (0부터 시작, 없으면 null)
     */
    public static Long zrank(String key, String member) {
        return RedisText.sync().zrank(key, member);
    }

    /**
     * Sorted Set 점수 증가 (ZINCRBY)
     * 
     * @param key Sorted Set 키
     * @param amount 증가량
     * @param member 멤버
     * @return 증가된 점수
     */
    public static double zincrby(String key, double amount, String member) {
        return RedisText.sync().zincrby(key, amount, member);
    }

    // ==================== Pattern Matching ====================

    /**
     * 패턴 매칭으로 키 조회 (KEYS)
     * 
     * <p><b>⚠️ 주의:</b></p>
     * 프로덕션 환경에서는 SCAN 사용을 권장합니다. KEYS는 blocking 명령입니다.
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<String> userKeys = RedisTextUtils.keys("user:*");
     * List<String> sessionKeys = RedisTextUtils.keys("session:*");
     * </pre>
     * 
     * @param pattern 패턴 (예: "user:*", "*:name")
     * @return 매칭되는 키 리스트
     */
    public static List<String> keys(String pattern) {
        return RedisText.sync().keys(pattern);
    }

    /**
     * 패턴 매칭으로 키 스캔 (SCAN) - 권장
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * KeyScanCursor<String> cursor = RedisTextUtils.scan("user:*");
     * List<String> keys = cursor.getKeys();
     * 
     * // 다음 페이지 조회
     * if (!cursor.isFinished()) {
     *     cursor = RedisTextUtils.scan("user:*", cursor);
     * }
     * </pre>
     * 
     * @param pattern 패턴
     * @return Scan 커서
     */
    public static KeyScanCursor<String> scan(String pattern) {
        ScanArgs args = ScanArgs.Builder.matches(pattern).limit(100);
        return RedisText.sync().scan(args);
    }

    /**
     * 패턴 매칭으로 키 스캔 (커서 지정)
     * 
     * @param pattern 패턴
     * @param cursor 이전 커서
     * @return Scan 커서
     */
    public static KeyScanCursor<String> scan(String pattern, ScanCursor cursor) {
        ScanArgs args = ScanArgs.Builder.matches(pattern).limit(100);
        return RedisText.sync().scan(cursor, args);
    }

    /**
     * 패턴으로 키 삭제 (SCAN + DEL)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 모든 세션 삭제
     * long deleted = RedisTextUtils.deleteByPattern("session:*");
     * 
     * // 특정 사용자의 모든 캐시 삭제
     * long deleted = RedisTextUtils.deleteByPattern("cache:user:123:*");
     * </pre>
     * 
     * @param pattern 패턴
     * @return 삭제된 키 개수
     */
    public static long deleteByPattern(String pattern) {
        long deletedCount = 0;
        ScanArgs args = ScanArgs.Builder.matches(pattern).limit(100);
        KeyScanCursor<String> cursor = RedisText.sync().scan(args);
        
        while (!cursor.isFinished() || !cursor.getKeys().isEmpty()) {
            List<String> keys = cursor.getKeys();
            
            if (!keys.isEmpty()) {
                deletedCount += del(keys.toArray(new String[0]));
            }
            
            if (cursor.isFinished()) {
                break;
            }
            
            cursor = RedisText.sync().scan(cursor, args);
        }
        
        return deletedCount;
    }

    /**
     * Transaction 실행 (MULTI/EXEC)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * TransactionResult result = RedisTextUtils.executeTransaction(tx -> {
     *     tx.set("key1", "value1");
     *     tx.set("key2", "value2");
     *     tx.incr("counter");
     * });
     * 
     * // 결과 확인
     * if (result.wasDiscarded()) {
     *     System.out.println("Transaction was discarded");
     * } else {
     *     System.out.println("Commands executed: " + result.size());
     * }
     * </pre>
     * 
     * @param action Transaction 로직
     * @return Transaction 결과
     */
    public static TransactionResult executeTransaction(Consumer<RedisCommands<String, String>> action) {
        RedisCommands<String, String> commands = RedisText.sync();
        
        commands.multi();
        
        try {
            action.accept(commands);
            return commands.exec();
        } catch (Exception e) {
            commands.discard();
            throw new RuntimeException("Transaction failed", e);
        }
    }

    // ==================== Async Operations ====================

    /**
     * 비동기 값 저장
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisTextUtils.setAsync("key", "value")
     *     .thenAccept(result -> System.out.println("Done: " + result));
     * </pre>
     * 
     * @param key Redis 키
     * @param value 값
     * @return CompletableFuture
     */
    public static CompletableFuture<String> setAsync(String key, String value) {
        return RedisText.async().set(key, value).toCompletableFuture();
    }

    /**
     * 비동기 값 조회
     * 
     * @param key Redis 키
     * @return CompletableFuture
     */
    public static CompletableFuture<String> getAsync(String key) {
        return RedisText.async().get(key).toCompletableFuture();
    }

    /**
     * 비동기 키 삭제
     * 
     * @param keys 삭제할 키 목록
     * @return CompletableFuture
     */
    public static CompletableFuture<Long> delAsync(String... keys) {
        return RedisText.async().del(keys).toCompletableFuture();
    }
    
    
    /**
     * Pipeline 실행 (대량 작업 최적화)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<String> results = RedisTextUtils.pipeline(async -> {
     *     List<RedisFuture<String>> futures = new ArrayList<>();
     *     
     *     for (int i = 0; i < 1000; i++) {
     *         futures.add(async.set("key" + i, "value" + i));
     *     }
     *     
     *     return futures;
     * });
     * 
     * System.out.println("Executed " + results.size() + " commands");
     * </pre>
     * 
     * @param <T> 결과 타입
     * @param action Pipeline 로직
     * @return 결과 리스트
     */
    public static <T> List<T> pipeline(Function<RedisAsyncCommands<String, String>, List<RedisFuture<T>>> action) {
        // Connection 객체 가져오기
        StatefulRedisConnection<String, String> connection = RedisText.getConnection();
        RedisAsyncCommands<String, String> async = connection.async();
        
        // Connection에서 자동 플러시 비활성화
        connection.setAutoFlushCommands(false);
        
        try {
            List<RedisFuture<T>> futures = action.apply(async);
            
            // Connection에서 일괄 실행
            connection.flushCommands();
            
            // 결과 수집
            List<T> results = new ArrayList<>();
            for (RedisFuture<T> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    logger.warn("Pipeline command failed", e);
                    results.add(null);
                }
            }
            
            return results;
            
        } finally {
            // Connection에서 자동 플러시 재활성화
            connection.setAutoFlushCommands(true);
        }
    }

    // ==================== TTL Management ====================

    /**
     * TTL 조회 (밀리초 단위) (PTTL)
     * 
     * @param key Redis 키
     * @return TTL (밀리초 단위)
     */
    public static Long pttl(String key) {
        return RedisText.sync().pttl(key);
    }

    /**
     * TTL 설정 (밀리초 단위) (PEXPIRE)
     * 
     * @param key Redis 키
     * @param milliseconds TTL (밀리초 단위)
     * @return 성공 여부
     */
    public static boolean pexpire(String key, long milliseconds) {
        return RedisText.sync().pexpire(key, milliseconds);
    }

    /**
     * 특정 시각에 만료 설정 (EXPIREAT)
     * 
     * @param key Redis 키
     * @param timestamp Unix timestamp (초 단위)
     * @return 성공 여부
     */
    public static boolean expireAt(String key, long timestamp) {
        return RedisText.sync().expireat(key, timestamp);
    }

    /**
     * 특정 시각에 만료 설정 (밀리초 단위) (PEXPIREAT)
     * 
     * @param key Redis 키
     * @param timestampMs Unix timestamp (밀리초 단위)
     * @return 성공 여부
     */
    public static boolean pexpireAt(String key, long timestampMs) {
        return RedisText.sync().pexpireat(key, timestampMs);
    }

    // ==================== Delayed Task Queue ====================

    /**
     * 지연 작업 추가 (Sorted Set 활용)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // 10초 후 실행할 작업 추가
     * RedisTextUtils.addDelayedTask("tasks:queue", "task123", 10);
     * </pre>
     * 
     * @param queueKey 큐 키
     * @param taskId 작업 ID
     * @param delaySeconds 지연 시간 (초)
     * @return 성공 여부
     */
    public static boolean addDelayedTask(String queueKey, String taskId, long delaySeconds) {
        long executeTime = System.currentTimeMillis() + (delaySeconds * 1000);
        return RedisText.sync().zadd(queueKey, executeTime, taskId) >= 0;
    }

    /**
     * 실행 가능한 작업 조회 및 제거
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // Worker 스레드에서 실행
     * while (true) {
     *     List<String> tasks = RedisTextUtils.pollDelayedTasks("tasks:queue", 10);
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
        
        // 실행 가능한 작업 조회
        List<String> tasks = RedisText.sync().zrangebyscore(
            queueKey,
            io.lettuce.core.Range.create(0.0, (double) now),
            io.lettuce.core.Limit.create(0, batchSize)
        );
        
        // 조회된 작업 제거
        if (!tasks.isEmpty()) {
            RedisText.sync().zrem(queueKey, tasks.toArray(new String[0]));
        }
        
        return tasks;
    }
}