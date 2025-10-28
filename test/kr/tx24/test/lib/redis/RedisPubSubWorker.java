package kr.tx24.test.lib.redis;

import java.util.concurrent.atomic.AtomicInteger;

import kr.tx24.lib.redis.RedisPubSub;

/**
 * Redis Pub/Sub Worker 패턴 - 실무 예제
 * 
 * <p><b>시나리오:</b> 여러 Worker가 하나의 채널을 구독하여 작업을 분산 처리</p>
 * 
 * <p><b>특징:</b></p>
 * <ul>
 *   <li>여러 Worker가 동일 채널 구독 (Load Balancing)</li>
 *   <li>에러 발생 시 자동 재시도</li>
 *   <li>작업 통계 수집</li>
 * </ul>
 */
public class RedisPubSubWorker {

    private final String workerId;
    private final String channel;
    private final WorkerHandler handler;
    private RedisPubSub.Subscriber subscriber;
    
    // 통계
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);

    public RedisPubSubWorker(String workerId, String channel, WorkerHandler handler) {
        this.workerId = workerId;
        this.channel = channel;
        this.handler = handler;
    }

    /**
     * Worker 시작
     */
    public void start() {
        System.out.println("🚀 Worker 시작: " + workerId + " (채널: " + channel + ")");
        
        subscriber = RedisPubSub.subscribe(channel, message -> {
            processMessage(message);
        });
    }

    /**
     * 메시지 처리
     */
    private void processMessage(String message) {
        long startTime = System.currentTimeMillis();
        
        try {
            System.out.println("\n[" + workerId + "] 작업 시작: " + message);
            
            // 실제 작업 실행
            handler.handle(workerId, message);
            
            // 성공
            processedCount.incrementAndGet();
            long duration = System.currentTimeMillis() - startTime;
            System.out.println("[" + workerId + "] ✅ 작업 완료 (" + duration + "ms)");
            
        } catch (Exception e) {
            // 실패
            failedCount.incrementAndGet();
            System.err.println("[" + workerId + "] ❌ 작업 실패: " + e.getMessage());
            
            // 재시도 로직 (필요시)
            // retryMessage(message);
        }
    }

    /**
     * Worker 중지
     */
    public void stop() {
        if (subscriber != null) {
            subscriber.close();
        }
        System.out.println("🛑 Worker 중지: " + workerId);
        printStatistics();
    }

    /**
     * 통계 출력
     */
    public void printStatistics() {
        System.out.println("\n=== " + workerId + " 통계 ===");
        System.out.println("처리 성공: " + processedCount.get());
        System.out.println("처리 실패: " + failedCount.get());
        System.out.println("총 처리: " + (processedCount.get() + failedCount.get()));
    }

    // ========== 작업 핸들러 인터페이스 ==========

    @FunctionalInterface
    public interface WorkerHandler {
        void handle(String workerId, String message) throws Exception;
    }

    // ========== 메인: 실행 예제 ==========

    public static void main(String[] args) throws Exception {
        System.out.println("=== Redis Pub/Sub Worker 패턴 데모 ===\n");
        
        // 작업 핸들러 정의
        WorkerHandler imageProcessor = (workerId, message) -> {
            // 이미지 처리 시뮬레이션
            Thread.sleep(500 + (int)(Math.random() * 500));
            System.out.println("  [" + workerId + "] 🖼️  이미지 처리 완료: " + message);
        };
        
        // Worker 3개 생성 (동일 채널 구독)
        RedisPubSubWorker worker1 = new RedisPubSubWorker("WORKER-1", "jobs:images", imageProcessor);
        RedisPubSubWorker worker2 = new RedisPubSubWorker("WORKER-2", "jobs:images", imageProcessor);
        RedisPubSubWorker worker3 = new RedisPubSubWorker("WORKER-3", "jobs:images", imageProcessor);
        
        // Worker 시작
        worker1.start();
        worker2.start();
        worker3.start();
        
        System.out.println("\n3개의 Worker가 대기 중입니다...\n");
        Thread.sleep(1000);
        
        // 작업 발행 (10개)
        System.out.println("10개의 작업을 발행합니다...\n");
        for (int i = 1; i <= 10; i++) {
            RedisPubSub.publish("jobs:images", "image_" + i + ".jpg");
            Thread.sleep(100);
        }
        
        // 처리 대기
        System.out.println("\n작업 처리를 기다립니다...\n");
        Thread.sleep(8000);
        
        // Worker 중지
        worker1.stop();
        worker2.stop();
        worker3.stop();
        
        System.out.println("\n=== 데모 종료 ===");
    }
}
