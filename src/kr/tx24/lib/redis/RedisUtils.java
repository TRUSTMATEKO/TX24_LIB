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
     * 값의 타입에 따라 적절한 Redis Connection을 반환
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
            // 리스트의 첫 번째 요소 타입으로 Connection 결정
            if (list.isEmpty()) {
                return (StatefulRedisConnection<String, T>) Redis.getObject();
            }
            Object firstElement = list.get(0);
            if (firstElement instanceof SharedMap) {
                return (StatefulRedisConnection<String, T>) Redis.getSharedMapList();
            } else if (firstElement instanceof LinkedMap) {
                return (StatefulRedisConnection<String, T>) Redis.getLinkedMapList();
            } else if (firstElement instanceof ThreadSafeLinkedMap) {
                return (StatefulRedisConnection<String, T>) Redis.getThreadSafeLinkedMapList();
            }
        }
        
        return (StatefulRedisConnection<String, T>) Redis.getObject();
    }

    /**
     * 클래스 타입에 따라 적절한 Redis Connection을 반환
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
        }
        
        return (StatefulRedisConnection<String, T>) Redis.getObject();
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
     * 문자열 값을 조회합니다.
     *
     * @param key Redis 키
     * @param expire 조회 후 만료 시간(초). 0이면 변경하지 않음
     * @return 저장된 문자열 값
     */
    public static String get(String key, long expire) {
        return getValue(key, expire, Redis.getString());
    }

    /**
     * 문자열 값을 설정합니다.
     *
     * @param key Redis 키
     * @param val 저장할 문자열 값
     * @return 결과 상태 문자열
     */
    public static String set(String key, String val) {
        return set(key, val, 0);
    }

    /**
     * 문자열 값을 설정하고, 만료 시간을 지정합니다.
     *
     * @param key Redis 키
     * @param val 저장할 문자열 값
     * @param expire 만료 시간(초). 0이면 영구 저장
     * @return 결과 상태 문자열
     */
    public static String set(String key, String val, long expire) {
        return setValue(key, val, expire, Redis.getString());
    }

    // ---------------- SharedMap Operations ----------------

    /**
     * SharedMap<String, Object> 형태의 값을 조회합니다.
     *
     * @param key Redis 키
     * @return SharedMap 값
     */
    public static SharedMap<String, Object> getSharedMap(String key) {
        return getSharedMap(key, 0);
    }

    /**
     * SharedMap<String, Object> 형태의 값을 조회하고, 만료 시간을 지정합니다.
     *
     * @param key Redis 키
     * @param expire 만료 시간(초)
     * @return SharedMap 값
     */
    public static SharedMap<String, Object> getSharedMap(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMap());
    }

    /**
     * SharedMap<String, Object> 값을 설정합니다.
     *
     * @param key Redis 키
     * @param val 저장할 SharedMap 값
     * @return 결과 상태 문자열
     */
    public static String set(String key, SharedMap<String, Object> val) {
        return set(key, val, 0);
    }

    /**
     * SharedMap<String, Object> 값을 설정하고 만료 시간을 지정합니다.
     *
     * @param key Redis 키
     * @param val 저장할 SharedMap 값
     * @param expire 만료 시간(초)
     * @return 결과 상태 문자열
     */
    public static String set(String key, SharedMap<String, Object> val, long expire) {
        return setValue(key, val, expire, Redis.getSharedMap());
    }

    // ---------------- SharedMap<String,String> Operations ----------------

    /**
     * SharedMap<String, String> 값을 조회합니다.
     *
     * @param key Redis 키
     * @return SharedMap 값
     */
    public static SharedMap<String, String> getSharedMapString(String key) {
        return getSharedMapString(key, 0);
    }

    /**
     * SharedMap<String, String> 값을 조회하고 만료 시간을 지정합니다.
     *
     * @param key Redis 키
     * @param expire 만료 시간(초)
     * @return SharedMap 값
     */
    public static SharedMap<String, String> getSharedMapString(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapString());
    }

    /**
     * SharedMap<String, String> 값을 설정합니다.
     *
     * @param key Redis 키
     * @param val 저장할 SharedMap 값
     * @return 결과 상태 문자열
     */
    public static String setString(String key, SharedMap<String, String> val) {
        return setString(key, val, 0);
    }

    /**
     * SharedMap<String, String> 값을 설정하고 만료 시간을 지정합니다.
     *
     * @param key Redis 키
     * @param val 저장할 SharedMap 값
     * @param expire 만료 시간(초)
     * @return 결과 상태 문자열
     */
    public static String setString(String key, SharedMap<String, String> val, long expire) {
        return setValue(key, val, expire, Redis.getSharedMapString());
    }

    // ---------------- SharedMap<String,Object> List Operations ----------------

    /**
     * SharedMap<String, Object> 리스트를 조회합니다.
     *
     * @param key Redis 키
     * @return SharedMap 리스트
     */
    public static List<SharedMap<String, Object>> getSharedMapList(String key) {
        return getSharedMapList(key, 0);
    }

    /**
     * SharedMap<String, Object> 리스트를 조회하고 만료 시간을 지정합니다.
     *
     * @param key Redis 키
     * @param expire 만료 시간(초)
     * @return SharedMap 리스트
     */
    public static List<SharedMap<String, Object>> getSharedMapList(String key, long expire) {
        return getValue(key, expire, Redis.getSharedMapList());
    }

    /**
     * SharedMap<String, Object> 리스트를 저장합니다.
     *
     * @param key Redis 키
     * @param vals 저장할 리스트
     * @return 결과 상태 문자열
     */
    public static String setSharedMapList(String key, List<SharedMap<String, Object>> vals) {
        return setSharedMapList(key, vals, 0);
    }

    /**
     * SharedMap<String, Object> 리스트를 저장하고 만료 시간을 지정합니다.
     *
     * @param key Redis 키
     * @param vals 저장할 리스트
     * @param expire 만료 시간(초)
     * @return 결과 상태 문자열
     */
    public static String setSharedMapList(String key, List<SharedMap<String, Object>> vals, long expire) {
        return setValue(key, vals, expire, Redis.getSharedMapList());
    }
    
    
    

    // ---------------- SharedMap<String,String> List Operations ----------------
    
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

    // ---------------- LinkedMap Operations ----------------
    
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

    // ---------------- LinkedMap<String,String> Operations ----------------
    
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

    // ---------------- LinkedMap<String,Object> List Operations ----------------
    
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

    // ---------------- LinkedMap<String,String> List Operations ----------------
    
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

    // ---------------- ThreadSafeLinkedMap Operations ----------------
    
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

    // ---------------- ThreadSafeLinkedMap<String,String> Operations ----------------
    
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

    // ---------------- ThreadSafeLinkedMap<String,Object> List Operations ----------------
    
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

    // ---------------- ThreadSafeLinkedMap<String,String> List Operations ----------------
    
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

    // ------------------- Private Helper Methods -------------------

    /**
     * Redis에서 값을 조회하고, expire가 지정되면 TTL을 갱신합니다.
     * 
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
     * @param key Redis 키
     * @param retrieve DB 조회 객체
     * @return 조회된 데이터
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
     * @return 조회된 데이터
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
     */
    public static List<SharedMap<String, Object>> fetchRows(String key, Retrieve retrieve) {
        return fetchRows(key, retrieve, 0);
    }

    /**
     * Cache-Aside Pattern for List with TTL
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
    
    
    
	
	
	
}
