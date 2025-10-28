package kr.tx24.test.lib.redis;


import java.util.Scanner;

import kr.tx24.lib.redis.RedisPubSub;

/**
 * RedisPubSub Subscribe 실전 예제 (순수 Java)
 * 
 * <p><b>시나리오:</b> 분산 시스템에서 메시지를 받아 특정 작업 클래스를 실행</p>
 * 
 * <p><b>실행 방법:</b></p>
 * <pre>
 * 1. 이 클래스를 실행 (Subscriber 역할)
 * 2. 다른 터미널에서 RedisPubSubSimplePublisher 실행 (Publisher 역할)
 * 3. 메시지가 전달되어 Task가 자동 실행됨
 * </pre>
 */
public class RedisPubSubSimpleExample {

    public static void main(String[] args) {
        System.out.println("=== Redis Pub/Sub 간단한 예제 ===\n");
        
        // 예제 선택
        System.out.println("실행할 예제를 선택하세요:");
        System.out.println("1. 단순 메시지 수신");
        System.out.println("2. 작업 클래스 자동 실행");
        System.out.println("3. 배치 작업 처리");
        System.out.println("4. 실시간 알림 시스템");
        System.out.println("5. 모두 실행");
        System.out.print("\n선택 (1-5): ");
        
        Scanner scanner = new Scanner(System.in);
        int choice = scanner.nextInt();
        
        try {
            switch (choice) {
                case 1 -> example1_SimpleMessageReceiver();
                case 2 -> example2_TaskClassExecution();
                case 3 -> example3_BatchProcessing();
                case 4 -> example4_RealtimeNotification();
                case 5 -> {
                    example1_SimpleMessageReceiver();
                    Thread.sleep(2000);
                    example2_TaskClassExecution();
                    Thread.sleep(2000);
                    example3_BatchProcessing();
                    Thread.sleep(2000);
                    example4_RealtimeNotification();
                }
                default -> System.out.println("잘못된 선택입니다.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }

    /**
     * 예제 1: 단순 메시지 수신
     * - 가장 기본적인 Subscribe 사용법
     */
    private static void example1_SimpleMessageReceiver() throws InterruptedException {
        System.out.println("\n--- 예제 1: 단순 메시지 수신 ---");
        System.out.println("채널 'chat:room1'을 구독합니다...\n");
        
        // Subscribe 시작
        RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("chat:room1", message -> {
            System.out.println("💬 수신: " + message);
        });
        
        // 테스트 메시지 발행
        System.out.println("테스트 메시지를 발행합니다...");
        Thread.sleep(500);
        RedisPubSub.publish("chat:room1", "안녕하세요!");
        RedisPubSub.publish("chat:room1", "Redis Pub/Sub 테스트 중입니다.");
        
        // 5초간 대기 (외부에서 메시지를 보낼 수 있음)
        System.out.println("\n5초간 메시지를 기다립니다...");
        System.out.println("다른 터미널에서 메시지를 보내보세요:");
        System.out.println("  RedisPubSub.publish(\"chat:room1\", \"Your Message\");\n");
        Thread.sleep(5000);
        
        // 종료
        subscriber.close();
        System.out.println("✅ 구독 종료\n");
    }

    /**
     * 예제 2: 작업 클래스 자동 실행
     * - 메시지를 받으면 특정 클래스의 메서드를 자동 실행
     */
    private static void example2_TaskClassExecution() throws InterruptedException {
        System.out.println("\n--- 예제 2: 작업 클래스 자동 실행 ---");
        System.out.println("채널 'task:execute'를 구독합니다...\n");
        
        // 실행할 작업 클래스들
        DataProcessor dataProcessor = new DataProcessor();
        FileHandler fileHandler = new FileHandler();
        
        // Subscribe - 메시지에 따라 다른 클래스 실행
        RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("task:execute", message -> {
            System.out.println("📨 작업 요청 수신: " + message);
            
            // 메시지 파싱하여 해당 클래스의 메서드 실행
            if (message.startsWith("PROCESS:")) {
                String data = message.substring(8);
                dataProcessor.process(data);
                
            } else if (message.startsWith("SAVE:")) {
                String filename = message.substring(5);
                fileHandler.save(filename);
                
            } else if (message.startsWith("DELETE:")) {
                String filename = message.substring(7);
                fileHandler.delete(filename);
                
            } else {
                System.out.println("⚠️  알 수 없는 작업: " + message);
            }
        });
        
        // 테스트 메시지 발행
        Thread.sleep(500);
        System.out.println("\n테스트 작업들을 발행합니다...\n");
        RedisPubSub.publish("task:execute", "PROCESS:user_data_2025");
        Thread.sleep(300);
        RedisPubSub.publish("task:execute", "SAVE:report.pdf");
        Thread.sleep(300);
        RedisPubSub.publish("task:execute", "DELETE:temp.log");
        
        Thread.sleep(2000);
        
        // 종료
        subscriber.close();
        System.out.println("\n✅ 작업 실행 완료\n");
    }

    /**
     * 예제 3: 배치 작업 처리
     * - 대량의 작업을 비동기로 처리
     */
    private static void example3_BatchProcessing() throws InterruptedException {
        System.out.println("\n--- 예제 3: 배치 작업 처리 ---");
        System.out.println("채널 'batch:jobs'를 구독합니다...\n");
        
        BatchJobProcessor processor = new BatchJobProcessor();
        
        // Subscribe
        RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("batch:jobs", message -> {
            processor.execute(message);
        });
        
        // 배치 작업 발행
        System.out.println("배치 작업을 발행합니다...\n");
        Thread.sleep(500);
        
        for (int i = 1; i <= 5; i++) {
            RedisPubSub.publish("batch:jobs", "JOB_" + i);
            Thread.sleep(200);
        }
        
        Thread.sleep(2000);
        
        // 종료
        subscriber.close();
        System.out.println("\n✅ 배치 작업 완료\n");
    }

    /**
     * 예제 4: 실시간 알림 시스템
     * - 여러 채널을 동시에 구독하여 알림 처리
     */
    private static void example4_RealtimeNotification() throws InterruptedException {
        System.out.println("\n--- 예제 4: 실시간 알림 시스템 ---");
        System.out.println("패턴 'notify:*'를 구독합니다...\n");
        
        NotificationHandler notificationHandler = new NotificationHandler();
        
        // 패턴 구독 - notify:로 시작하는 모든 채널
        RedisPubSub.Subscriber subscriber = RedisPubSub.psubscribe("notify:*", message -> {
            notificationHandler.handle(message);
        });
        
        // 다양한 알림 발행
        System.out.println("다양한 알림을 발행합니다...\n");
        Thread.sleep(500);
        
        RedisPubSub.publish("notify:email", "새 메일이 도착했습니다");
        Thread.sleep(300);
        RedisPubSub.publish("notify:sms", "인증번호: 123456");
        Thread.sleep(300);
        RedisPubSub.publish("notify:push", "새 댓글이 달렸습니다");
        Thread.sleep(300);
        RedisPubSub.publish("notify:slack", "배포가 완료되었습니다");
        
        Thread.sleep(2000);
        
        // 종료
        subscriber.close();
        System.out.println("\n✅ 알림 처리 완료\n");
    }

    // ========== 실행될 작업 클래스들 ==========

    /**
     * 데이터 처리 클래스
     */
    static class DataProcessor {
        public void process(String data) {
            System.out.println("  🔄 DataProcessor 실행");
            System.out.println("     처리 데이터: " + data);
            // 실제 데이터 처리 로직
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("  ✅ 데이터 처리 완료\n");
        }
    }

    /**
     * 파일 처리 클래스
     */
    static class FileHandler {
        public void save(String filename) {
            System.out.println("  💾 FileHandler.save() 실행");
            System.out.println("     파일명: " + filename);
            // 실제 파일 저장 로직
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("  ✅ 파일 저장 완료\n");
        }
        
        public void delete(String filename) {
            System.out.println("  🗑️  FileHandler.delete() 실행");
            System.out.println("     파일명: " + filename);
            // 실제 파일 삭제 로직
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("  ✅ 파일 삭제 완료\n");
        }
    }

    /**
     * 배치 작업 처리 클래스
     */
    static class BatchJobProcessor {
        private int jobCount = 0;
        
        public void execute(String jobId) {
            jobCount++;
            System.out.println("  ⚙️  배치 작업 실행 [" + jobCount + "/5]");
            System.out.println("     작업 ID: " + jobId);
            // 실제 배치 작업 로직
            try {
                Thread.sleep(400);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("  ✅ 작업 완료\n");
        }
    }

    /**
     * 알림 처리 클래스
     */
    static class NotificationHandler {
        public void handle(String message) {
            System.out.println("  🔔 NotificationHandler 실행");
            System.out.println("     알림 내용: " + message);
            
            // 알림 종류에 따라 다른 처리
            if (message.contains("메일")) {
                sendEmail(message);
            } else if (message.contains("인증")) {
                sendSMS(message);
            } else if (message.contains("댓글")) {
                sendPushNotification(message);
            } else {
                sendSlackMessage(message);
            }
        }
        
        private void sendEmail(String message) {
            System.out.println("     → 이메일 발송\n");
        }
        
        private void sendSMS(String message) {
            System.out.println("     → SMS 발송\n");
        }
        
        private void sendPushNotification(String message) {
            System.out.println("     → 푸시 알림 발송\n");
        }
        
        private void sendSlackMessage(String message) {
            System.out.println("     → Slack 메시지 발송\n");
        }
    }
}
