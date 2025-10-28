package kr.tx24.test.lib.redis;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.TransactionResult;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.redis.RedisUtils;
import kr.tx24.lib.redis.TypeRegistry;

/**
 * Redis 사용 예제 - Transaction + Async 포함
 */
public class RedisExamples {

    public static void main(String[] args) throws Exception {
        
    	System.setProperty("CONF", "./conf");
    	System.setProperty("REDIS", "192.168.10.200:6379/0");

        System.out.println("=== Redis 완전 사용 가이드 ===\n");
        
        // 1. 기본 사용
        basicUsage();
        
        // 2. Transaction
        transactionExample();
        
        // 3. Async
        asyncExample();
        
        // 4. Pipeline
        pipelineExample();
        
        System.out.println("\n=== 완료 ===");
    }

    // ==================== 1. 기본 사용 ====================
    
    static void basicUsage() {
        System.out.println("📝 1. 기본 사용\n");
        
        // String
        RedisUtils.set("user:name", "John");
        String name = RedisUtils.get("user:name", String.class);
        System.out.println("  String: " + name);
        
        // SharedMap
        SharedMap<String, Object> user = new SharedMap<>();
        user.put("id", 123);
        user.put("name", "Alice");
        user.put("age", 30);
        RedisUtils.set("user:123", user);
        
        SharedMap<?, ?> retrieved = RedisUtils.get("user:123", SharedMap.class);
        System.out.println("  SharedMap: " + retrieved);
        
        // List
        List<String> items = Arrays.asList("apple", "banana", "cherry");
        RedisUtils.set("items", items);
        
        @SuppressWarnings("unchecked")
        List<String> retrievedItems = (List<String>) RedisUtils.get("items");
        System.out.println("  List: " + retrievedItems);
        
        // TTL
        RedisUtils.set("temp:key", "expires", 5);
        Long ttl = RedisUtils.ttl("temp:key");
        System.out.println("  TTL: " + ttl + " seconds");
        
        // 정리
        RedisUtils.del("user:name", "user:123", "items", "temp:key");
       
        
        List<SharedMap<String,Object>> ll = new ArrayList<SharedMap<String,Object>>();
        ll.add(user);
        ll.add(user);
        ll.add(user);
        ll.add(user);
        ll.add(user);
        RedisUtils.set("user:temp", ll);
        
        List<SharedMap<String,Object>> rr = RedisUtils.get("user:temp",TypeRegistry.LIST_SHAREDMAP_OBJECT);
        
        
        System.out.println(rr.toString());
    }

    // ==================== 2. Transaction ====================
    
    static void transactionExample() {
        System.out.println("📝 2. Transaction (MULTI/EXEC)\n");
        
        // ✅ 기본 Transaction
        System.out.println("  [예제 1] 기본 Transaction");
        TransactionResult result = RedisUtils.executeTransaction(tx -> {
            tx.set("account:A", 1000);
            tx.set("account:B", 2000);
            tx.incr("total_accounts");
        });
        
        System.out.println("  Transaction 성공: " + !result.wasDiscarded());
        System.out.println("  명령어 개수: " + result.size());
        System.out.println();
        
        // ✅ 결과 반환하는 Transaction
        System.out.println("  [예제 2] 결과 반환 Transaction");
        List<Object> results = RedisUtils.executeTransactionWithResult(tx -> {
            tx.set("key1", "value1");
            tx.set("key2", "value2");
            tx.get("key1");
            tx.incr("counter");
        });
        
        System.out.println("  결과[0] (set): " + results.get(0));  // "OK"
        System.out.println("  결과[1] (set): " + results.get(1));  // "OK"
        System.out.println("  결과[2] (get): " + results.get(2));  // "value1"
        System.out.println("  결과[3] (incr): " + results.get(3)); // 1
        System.out.println();
        
        // ✅ 복잡한 Transaction (은행 계좌 이체)
        System.out.println("  [예제 3] 은행 계좌 이체");
        RedisUtils.set("account:Alice", 10000);
        RedisUtils.set("account:Bob", 5000);
        
        RedisUtils.executeTransaction(tx -> {
            // Alice → Bob 3000원 이체
            tx.decrby("account:Alice", 3000);
            tx.incrby("account:Bob", 3000);
            tx.incr("transfer:count");
            tx.lpush("transfer:log", "Alice->Bob:3000");
        });
        
        Integer aliceBalance = (Integer) RedisUtils.get("account:Alice");
        Integer bobBalance = (Integer) RedisUtils.get("account:Bob");
        System.out.println("  Alice 잔액: " + aliceBalance); // 7000
        System.out.println("  Bob 잔액: " + bobBalance);     // 8000
        
        // 정리
        RedisUtils.del("account:A", "account:B", "total_accounts",
                      "key1", "key2", "counter",
                      "account:Alice", "account:Bob", 
                      "transfer:count", "transfer:log");
        
        System.out.println();
    }

    // ==================== 3. Async ====================
    
    static void asyncExample() throws Exception {
        System.out.println("📝 3. Async (비동기)\n");
        
        // ✅ 기본 Async
        System.out.println("  [예제 1] 기본 Async");
        CompletableFuture<String> future1 = RedisUtils.setAsync("async:key1", "value1");
        CompletableFuture<Object> future2 = RedisUtils.getAsync("async:key1");
        
        future1.thenAccept(result -> 
            System.out.println("  Set 완료: " + result));
        
        future2.thenAccept(value -> 
            System.out.println("  Get 결과: " + value));
        
        // 모든 작업 완료 대기
        CompletableFuture.allOf(future1, future2).join();
        System.out.println();
        
        // ✅ 여러 Async 작업
        System.out.println("  [예제 2] 여러 Async 작업");
        
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            int index = i;
            CompletableFuture<String> f = RedisUtils.setAsync(
                "async:batch:" + index, 
                "value" + index
            );
            futures.add(f);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> System.out.println("  10개 키 저장 완료!"))
            .join();
        
        System.out.println();
        
        // ✅ Async + 콜백
        System.out.println("  [예제 3] Async + 콜백");
        RedisUtils.setAsync("async:callback", "test")
            .thenCompose(result -> {
                System.out.println("  저장 완료: " + result);
                return RedisUtils.getAsync("async:callback");
            })
            .thenAccept(value -> {
                System.out.println("  조회 결과: " + value);
            })
            .exceptionally(e -> {
                System.err.println("  오류: " + e.getMessage());
                return null;
            })
            .join();
        
        // 정리
        RedisUtils.deleteByPattern("async:*");
        
        System.out.println();
    }

    // ==================== 4. Pipeline ====================
    
    static void pipelineExample() throws Exception {
        System.out.println("📝 4. Pipeline (일괄 실행)\n");
        
        // ✅ 기본 Pipeline
        System.out.println("  [예제 1] 1000개 키 일괄 저장");
        
        long startTime = System.currentTimeMillis();
        
        List<CompletableFuture<?>> futures = RedisUtils.pipeline(async -> {
            List<RedisFuture<?>> list = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                list.add(async.set("pipeline:key" + i, "value" + i));
            }
            return list;
        });
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("  1000개 키 저장: " + elapsed + "ms");
        System.out.println();
        
        // ✅ Pipeline + 다양한 명령어
        System.out.println("  [예제 2] 다양한 명령어 Pipeline");
        
        List<CompletableFuture<?>> mixedFutures = RedisUtils.pipeline(async -> {
            List<RedisFuture<?>> list = new ArrayList<>();
            list.add(async.set("pl:string", "value"));
            list.add(async.lpush("pl:list", "item1", "item2", "item3"));
            list.add(async.sadd("pl:set", "a", "b", "c"));
            list.add(async.hset("pl:hash", "field1", "value1"));
            list.add(async.incr("pl:counter"));
            return list;
        });
        
        CompletableFuture.allOf(mixedFutures.toArray(new CompletableFuture[0]))
            .thenRun(() -> System.out.println("  모든 명령어 실행 완료!"))
            .join();
        
        // 결과 확인
        String str = RedisUtils.get("pl:string", String.class);
        List<Object> list = RedisUtils.lrange("pl:list", 0, -1);
        Set<Object> set = RedisUtils.smembers("pl:set");
        
        System.out.println("  String: " + str);
        System.out.println("  List: " + list);
        System.out.println("  Set: " + set);
        
        // 정리
        RedisUtils.deleteByPattern("pipeline:*");
        RedisUtils.del("pl:string", "pl:list", "pl:set", "pl:hash", "pl:counter");
        
        System.out.println();
    }

    // ==================== 보너스: 실전 시나리오 ====================
    
    @SuppressWarnings("unused")
    static void realWorldScenarios() throws Exception {
        System.out.println("📝 실전 시나리오\n");
        
        // 시나리오 1: 세션 관리
        session();
        
        // 시나리오 2: 캐시 관리
        cache();
        
        // 시나리오 3: 카운터
        counter();
        
        // 시나리오 4: Rate Limiting
        rateLimiting();
    }

    static void session() {
        System.out.println("  [시나리오 1] 세션 관리");
        
        String sessionId = UUID.randomUUID().toString();
        SharedMap<String, Object> session = new SharedMap<>();
        session.put("userId", "user123");
        session.put("loginTime", System.currentTimeMillis());
        session.put("ip", "192.168.1.1");
        
        // 30분 TTL
        RedisUtils.set("session:" + sessionId, session, 1800);
        
        // 세션 조회
        SharedMap<?, ?> retrieved = RedisUtils.get("session:" + sessionId, SharedMap.class);
        System.out.println("  세션 조회: " + retrieved);
        
        RedisUtils.del("session:" + sessionId);
        System.out.println();
    }

    static void cache() {
        System.out.println("  [시나리오 2] 캐시 관리");
        
        String cacheKey = "cache:product:123";
        
        // 캐시 확인
        Object cached = RedisUtils.get(cacheKey);
        if (cached != null) {
            System.out.println("  캐시 히트: " + cached);
        } else {
            System.out.println("  캐시 미스, DB 조회 후 저장");
            
            // DB 조회 (시뮬레이션)
            SharedMap<String, Object> product = new SharedMap<>();
            product.put("id", 123);
            product.put("name", "상품명");
            product.put("price", 10000);
            
            // 1시간 캐시
            RedisUtils.set(cacheKey, product, 3600);
        }
        
        RedisUtils.del(cacheKey);
        System.out.println();
    }

    static void counter() {
        System.out.println("  [시나리오 3] 실시간 카운터");
        
        // 페이지 뷰 카운터
        for (int i = 0; i < 10; i++) {
            RedisUtils.incr("counter:pageview");
        }
        
        Long pageviews = (Long) RedisUtils.get("counter:pageview");
        System.out.println("  총 페이지뷰: " + pageviews);
        
        // 일별 카운터
        String today = "2024-01-15";
        RedisUtils.incr("counter:daily:" + today);
        
        RedisUtils.del("counter:pageview", "counter:daily:" + today);
        System.out.println();
    }

    static void rateLimiting() {
        System.out.println("  [시나리오 4] Rate Limiting");
        
        String userId = "user123";
        String key = "ratelimit:" + userId;
        
        // 1분에 10회 제한
        Long count = RedisUtils.incr(key);
        if (count == 1) {
            RedisUtils.expire(key, 60);  // 1분 TTL
        }
        
        if (count <= 10) {
            System.out.println("  요청 허용 (" + count + "/10)");
        } else {
            System.out.println("  요청 거부 (Rate limit 초과)");
        }
        
        RedisUtils.del(key);
        System.out.println();
    }
}