package kr.tx24.test.lib.redis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import kr.tx24.lib.redis.RedisPubSub;

/**
 * Redis Pub/Sub 이벤트 리스너 패턴
 * 
 * <p><b>사용 시나리오:</b></p>
 * <ul>
 *   <li>주문 생성 → 재고 감소, 이메일 발송, 포인트 적립 등 여러 작업 트리거</li>
 *   <li>사용자 가입 → 환영 메일, 쿠폰 발급, 통계 업데이트</li>
 *   <li>파일 업로드 → 썸네일 생성, 바이러스 검사, 백업</li>
 * </ul>
 * 
 * <p><b>장점:</b></p>
 * <ul>
 *   <li>느슨한 결합 (Loose Coupling)</li>
 *   <li>확장 용이 (새 Listener 추가가 쉬움)</li>
 *   <li>비동기 처리</li>
 * </ul>
 */
public class RedisPubSubEventBus {

    private final Map<String, List<EventListener>> listeners = new ConcurrentHashMap<>();
    private final Map<String, RedisPubSub.Subscriber> subscribers = new ConcurrentHashMap<>();

    /**
     * 이벤트 리스너 등록
     * 
     * @param eventType 이벤트 타입 (예: "order.created", "user.registered")
     * @param listener 리스너
     */
    public void addEventListener(String eventType, EventListener listener) {
        listeners.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
        
        // 해당 이벤트 타입을 아직 구독하지 않았다면 구독 시작
        if (!subscribers.containsKey(eventType)) {
            subscribeToEvent(eventType);
        }
        
        System.out.println("✅ Listener 등록: " + eventType + " -> " + listener.getClass().getSimpleName());
    }

    /**
     * 이벤트 구독 시작
     */
    private void subscribeToEvent(String eventType) {
        String channel = "event:" + eventType;
        
        RedisPubSub.Subscriber subscriber = RedisPubSub.subscribeJson(
            channel, 
            Event.class, 
            event -> {
                handleEvent(event);
            }
        );
        
        subscribers.put(eventType, subscriber);
        System.out.println("📡 이벤트 구독 시작: " + channel);
    }

    /**
     * 이벤트 처리
     */
    private void handleEvent(Event event) {
        String eventType = event.getType();
        List<EventListener> eventListeners = listeners.get(eventType);
        
        if (eventListeners == null || eventListeners.isEmpty()) {
            System.out.println("⚠️  이벤트에 대한 Listener가 없음: " + eventType);
            return;
        }
        
        System.out.println("\n🔔 이벤트 수신: " + eventType + " (데이터: " + event.getData() + ")");
        System.out.println("   실행할 Listener 수: " + eventListeners.size());
        
        // 모든 Listener 실행
        for (EventListener listener : eventListeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                System.err.println("❌ Listener 실행 실패: " + listener.getClass().getSimpleName());
                e.printStackTrace();
            }
        }
    }

    /**
     * 이벤트 발행 (다른 시스템에서 호출)
     */
    public static void publishEvent(String eventType, Map<String, Object> data) {
        Event event = new Event(eventType, data);
        String channel = "event:" + eventType;
        RedisPubSub.publishJson(channel, event);
        System.out.println("📤 이벤트 발행: " + eventType);
    }

    /**
     * 모든 구독 종료
     */
    public void shutdown() {
        subscribers.values().forEach(RedisPubSub.Subscriber::close);
        subscribers.clear();
        listeners.clear();
        System.out.println("🛑 EventBus 종료");
    }

    // ========== 이벤트 클래스 ==========

    public static class Event {
        private String type;
        private Map<String, Object> data;
        private long timestamp;
        private String eventId;

        public Event() {
            this.timestamp = System.currentTimeMillis();
            this.eventId = java.util.UUID.randomUUID().toString();
        }

        public Event(String type, Map<String, Object> data) {
            this();
            this.type = type;
            this.data = data;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public Map<String, Object> getData() { return data; }
        public void setData(Map<String, Object> data) { this.data = data; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public String getEventId() { return eventId; }
        public void setEventId(String eventId) { this.eventId = eventId; }
    }

    // ========== 리스너 인터페이스 ==========

    @FunctionalInterface
    public interface EventListener {
        void onEvent(Event event) throws Exception;
    }

    // ========== 메인: 실행 예제 ==========

    public static void main(String[] args) throws Exception {
        System.out.println("=== Redis Pub/Sub 이벤트 버스 데모 ===\n");
        
        RedisPubSubEventBus eventBus = new RedisPubSubEventBus();
        
        // ========== 시나리오 1: 주문 생성 이벤트 ==========
        
        System.out.println("--- 시나리오 1: 주문 생성 ---\n");
        
        // 주문 생성 시 실행될 Listener들 등록
        eventBus.addEventListener("order.created", new InventoryListener());
        eventBus.addEventListener("order.created", new EmailListener());
        eventBus.addEventListener("order.created", new PointListener());
        eventBus.addEventListener("order.created", new AnalyticsListener());
        
        Thread.sleep(1000);
        
        // 주문 생성 이벤트 발행
        System.out.println("\n📦 주문을 생성합니다...\n");
        publishEvent("order.created", Map.of(
            "orderId", "ORD-12345",
            "customerId", "CUST-1001",
            "amount", 50000,
            "items", List.of("상품A", "상품B")
        ));
        
        Thread.sleep(3000);
        
        // ========== 시나리오 2: 사용자 가입 이벤트 ==========
        
        System.out.println("\n\n--- 시나리오 2: 사용자 가입 ---\n");
        
        // 사용자 가입 시 실행될 Listener들 등록
        eventBus.addEventListener("user.registered", new WelcomeEmailListener());
        eventBus.addEventListener("user.registered", new CouponListener());
        eventBus.addEventListener("user.registered", new UserStatsListener());
        
        Thread.sleep(1000);
        
        // 사용자 가입 이벤트 발행
        System.out.println("\n👤 사용자가 가입합니다...\n");
        publishEvent("user.registered", Map.of(
            "userId", "USER-9999",
            "email", "newuser@example.com",
            "name", "홍길동",
            "referralCode", "FRIEND123"
        ));
        
        Thread.sleep(3000);
        
        // 종료
        eventBus.shutdown();
        
        System.out.println("\n=== 데모 종료 ===");
    }

    // ========== Listener 구현 예제들 ==========

    /**
     * 재고 감소 Listener
     */
    static class InventoryListener implements EventListener {
        @Override
        public void onEvent(Event event) throws Exception {
            System.out.println("  📦 InventoryListener 실행");
            Thread.sleep(200);
            
            @SuppressWarnings("unchecked")
            List<String> items = (List<String>) event.getData().get("items");
            System.out.println("     재고 감소: " + items);
            System.out.println("  ✅ 재고 업데이트 완료\n");
        }
    }

    /**
     * 이메일 발송 Listener
     */
    static class EmailListener implements EventListener {
        @Override
        public void onEvent(Event event) throws Exception {
            System.out.println("  📧 EmailListener 실행");
            Thread.sleep(300);
            
            String customerId = (String) event.getData().get("customerId");
            String orderId = (String) event.getData().get("orderId");
            System.out.println("     주문 확인 메일 발송: " + customerId);
            System.out.println("     주문번호: " + orderId);
            System.out.println("  ✅ 이메일 발송 완료\n");
        }
    }

    /**
     * 포인트 적립 Listener
     */
    static class PointListener implements EventListener {
        @Override
        public void onEvent(Event event) throws Exception {
            System.out.println("  💰 PointListener 실행");
            Thread.sleep(150);
            
            Integer amount = (Integer) event.getData().get("amount");
            int points = amount / 100; // 1% 적립
            System.out.println("     포인트 적립: " + points + "P");
            System.out.println("  ✅ 포인트 적립 완료\n");
        }
    }

    /**
     * 통계 업데이트 Listener
     */
    static class AnalyticsListener implements EventListener {
        @Override
        public void onEvent(Event event) throws Exception {
            System.out.println("  📊 AnalyticsListener 실행");
            Thread.sleep(100);
            
            System.out.println("     매출 통계 업데이트");
            System.out.println("  ✅ 통계 업데이트 완료\n");
        }
    }

    /**
     * 환영 이메일 Listener
     */
    static class WelcomeEmailListener implements EventListener {
        @Override
        public void onEvent(Event event) throws Exception {
            System.out.println("  👋 WelcomeEmailListener 실행");
            Thread.sleep(250);
            
            String name = (String) event.getData().get("name");
            String email = (String) event.getData().get("email");
            System.out.println("     환영 메일 발송: " + email);
            System.out.println("     수신자: " + name);
            System.out.println("  ✅ 환영 메일 발송 완료\n");
        }
    }

    /**
     * 쿠폰 발급 Listener
     */
    static class CouponListener implements EventListener {
        @Override
        public void onEvent(Event event) throws Exception {
            System.out.println("  🎟️  CouponListener 실행");
            Thread.sleep(200);
            
            String userId = (String) event.getData().get("userId");
            System.out.println("     신규 가입 쿠폰 발급: " + userId);
            System.out.println("     쿠폰: 10,000원 할인");
            System.out.println("  ✅ 쿠폰 발급 완료\n");
        }
    }

    /**
     * 사용자 통계 Listener
     */
    static class UserStatsListener implements EventListener {
        @Override
        public void onEvent(Event event) throws Exception {
            System.out.println("  📈 UserStatsListener 실행");
            Thread.sleep(100);
            
            String referralCode = (String) event.getData().get("referralCode");
            System.out.println("     신규 가입자 통계 업데이트");
            if (referralCode != null) {
                System.out.println("     추천인 포인트 적립: " + referralCode);
            }
            System.out.println("  ✅ 통계 업데이트 완료\n");
        }
    }
}
