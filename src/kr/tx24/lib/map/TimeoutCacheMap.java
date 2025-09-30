package kr.tx24.lib.map;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TimeoutCache 기본 구현 예제
 */
public class TimeoutCacheMap<K, V extends Map<?, ?>> extends TimeoutCache<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(TimeoutCacheMap.class);

    /** 기본 만료 시간: 10분 */
    public TimeoutCacheMap() {
        super(10);
    }

    /** 만료 시간 지정 */
    public TimeoutCacheMap(int expireInMinutes) {
        super(expireInMinutes);
    }

    @Override
    public void processAfterExpire(K key, V value) {
        logger.debug("Cache expired: key={}", key);
    }
    
    
    public static void main(String[] args) throws InterruptedException {

        // 1. TimeoutCacheMap 생성 (만료 시간 1분)
        TimeoutCacheMap<String, SharedMap<String, Object>> cache = new TimeoutCacheMap<>(1);

        // 2. SharedMap 생성 및 데이터 저장
        SharedMap<String, Object> user1 = new SharedMap<>();
        user1.put("name", "Alice");
        user1.put("age", 30);

        SharedMap<String, Object> user2 = new SharedMap<>();
        user2.put("name", "Bob");
        user2.put("age", 25);

        // 3. 캐시에 put
        cache.put("user1", user1);
        cache.put("user2", user2);

        // 4. 캐시 조회
        System.out.println("User1 name: " + cache.get("user1").getString("name")); // Alice
        System.out.println("User2 age: " + cache.get("user2").getInt("age"));      // 25

        // 5. 캐시 키 확인
        System.out.println("Keys in cache: " + cache.keySet());

        // 6. 캐시 삭제
        cache.delete("user1");
        System.out.println("After delete user1, contains user1? " + (cache.get("user1") != null));

        // 7. 캐시 만료 테스트
        System.out.println("Waiting 65 seconds for cache to expire...");
        Thread.sleep(65_000); // 65초 대기

        System.out.println("After expiry, user2: " + cache.get("user2")); // null
    }
}
