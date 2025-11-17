package kr.tx24.test.lib.redis;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import kr.tx24.lib.redis.RedisPubSub;

/**
 * RedisPubSub 사용 예시
 */
public class RedisPubSubExample {

    public static void main(String[] args) throws Exception {
        
        System.out.println("=== RedisPubSub 사용 예시 ===\n");
        
        // 예시 1: 간단한 메시지 발행 (일회성)
        example1_SimplePublish();
        
        // 예시 2: 메시지 구독 및 수신
        example2_Subscribe();
        
        // 예시 3: 재사용 가능한 Publisher
        example3_ReusablePublisher();
        
        // 예시 4: JSON 메시지 Pub/Sub
        example4_JsonPubSub();
        
        // 예시 5: 패턴 구독 (와일드카드)
        example5_PatternSubscribe();
        
 
        
        // 정리
        RedisPubSub.shutdown();
        System.out.println("\n=== 모든 예시 완료 ===");
    }

    /**
     * 예시 1: 간단한 메시지 발행 (일회성)
     * - Connection이 자동으로 생성/해제됨
     */
    private static void example1_SimplePublish() {
        System.out.println("--- 예시 1: 간단한 메시지 발행 ---");
        
        Long subscriberCount = RedisPubSub.publish("channel:news", "Breaking News!");
        System.out.println("메시지 발행 완료. 수신자 수: " + subscriberCount);
        
        System.out.println();
    }

    /**
     * 예시 2: 메시지 구독 및 수신
     * - 장기 연결이 유지됨
     * - 사용 후 반드시 close 필요
     */
    private static void example2_Subscribe() throws InterruptedException {
        System.out.println("--- 예시 2: 메시지 구독 ---");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        // 구독 시작
        RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("channel:alerts", message -> {
            System.out.println("수신한 메시지: " + message);
            latch.countDown();
        });
        
        // 잠시 대기 후 메시지 발행
        Thread.sleep(500);
        RedisPubSub.publish("channel:alerts", "System Alert: High CPU usage!");
        
        // 메시지 수신 대기
        latch.await(2, TimeUnit.SECONDS);
        
        // 구독 해제 및 종료
        subscriber.unsubscribe();
        subscriber.close();
        
        System.out.println();
    }

    /**
     * 예시 3: 재사용 가능한 Publisher
     * - 여러 메시지를 한 Connection으로 발행
     * - try-with-resources로 자동 정리
     */
    private static void example3_ReusablePublisher() {
        System.out.println("--- 예시 3: 재사용 가능한 Publisher ---");
        
        try (RedisPubSub.Publisher publisher = RedisPubSub.createPublisher()) {
            publisher.publish("channel:logs", "Log message 1");
            publisher.publish("channel:logs", "Log message 2");
            publisher.publish("channel:logs", "Log message 3");
            System.out.println("3개의 메시지 발행 완료 (하나의 Connection 사용)");
        } // Connection 자동 close
        
        System.out.println();
    }

    /**
     * 예시 4: JSON 메시지 Pub/Sub
     * - 객체를 JSON으로 직렬화하여 발행
     * - 수신 시 자동 역직렬화
     */
    private static void example4_JsonPubSub() throws InterruptedException {
        System.out.println("--- 예시 4: JSON 메시지 Pub/Sub ---");
        
        CountDownLatch latch = new CountDownLatch(1);
        
        // JSON 구독
        RedisPubSub.Subscriber subscriber = RedisPubSub.subscribeJson(
            "channel:users", 
            UserEvent.class, 
            event -> {
                System.out.printf("사용자 이벤트 수신: %s (ID: %d)%n", 
                    event.action, event.userId);
                latch.countDown();
            }
        );
        
        // JSON 발행
        Thread.sleep(500);
        UserEvent event = new UserEvent(1001, "LOGIN", System.currentTimeMillis());
        RedisPubSub.publishJson("channel:users", event);
        
        latch.await(2, TimeUnit.SECONDS);
        
        subscriber.close();
        System.out.println();
    }

    /**
     * 예시 5: 패턴 구독 (와일드카드 사용)
     * - "order:*" 패턴으로 여러 채널 구독
     */
    private static void example5_PatternSubscribe() throws InterruptedException {
        System.out.println("--- 예시 5: 패턴 구독 ---");
        
        CountDownLatch latch = new CountDownLatch(3);
        
        // 패턴 구독: order:* 로 시작하는 모든 채널
        RedisPubSub.Subscriber subscriber = RedisPubSub.psubscribe("order:*", message -> {
            System.out.println("패턴 매칭 메시지 수신: " + message);
            latch.countDown();
        });
        
        Thread.sleep(500);
        
        // 여러 채널에 메시지 발행
        RedisPubSub.publish("order:created", "Order #1234 created");
        RedisPubSub.publish("order:shipped", "Order #1234 shipped");
        RedisPubSub.publish("order:delivered", "Order #1234 delivered");
        
        latch.await(2, TimeUnit.SECONDS);
        
        subscriber.close();
        System.out.println();
    }

    

    // ========== 테스트용 DTO ==========
    
    static class UserEvent {
        public int userId;
        public String action;
        public long timestamp;
        
        public UserEvent() {}
        
        public UserEvent(int userId, String action, long timestamp) {
            this.userId = userId;
            this.action = action;
            this.timestamp = timestamp;
        }
    }
}
