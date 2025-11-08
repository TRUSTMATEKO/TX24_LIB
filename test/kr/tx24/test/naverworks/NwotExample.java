package kr.tx24.test.naverworks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;

import kr.tx24.naverwork.bot.NwBotMessenger;
import kr.tx24.naverwork.bot.NwBotUtils;
import kr.tx24.naverwork.oauth.NwAccessTokenManager;
import kr.tx24.naverwork.oauth.NwConfig;

public class NwotExample {

	 private static final String BOT_ID = "11085652";
	    private static final String CHANNEL_ID = "02155ff2-c722-134b-f3b1-04c8d74f6cb5";
	    
	    public static void main(String[] args) throws Exception {
	        
	        // ========== 1. 초기화 ==========
	        System.out.println("=== NAVER WORKS Bot 예제 시작 ===\n");
	        
	        // OAuth 설정 로드
	        NwConfig config = loadConfig();
	        
	        // Access Token Manager 초기화
	        NwAccessTokenManager.initialize(
	            config.getClientId(),
	            config.getClientSecret(),
	            config.getServiceAccount(),
	            config.getPrivateKey(),
	            config.getScope()
	        );
	        
	        // 토큰 정보 출력
	        NwAccessTokenManager tokenManager = NwAccessTokenManager.getInstance();
	        tokenManager.printTokenInfo();
	        
	        // Bot Utils 초기화
	        NwBotUtils botUtils = new NwBotUtils(BOT_ID, 3, 1000);
	        
	        try {
	            // ========== 2. 기본 메시지 전송 ==========
	            example1_BasicMessage(botUtils);
	            
	            // ========== 3. 비동기 메시지 전송 ==========
	            example2_AsyncMessage(botUtils);
	            
	            // ========== 4. 배치 메시지 전송 ==========
	            example3_BatchMessage(botUtils);
	            
	            // ========== 5. 큐 기반 메시지 전송 ==========
	            example4_QueuedMessage(botUtils);
	            
	            // ========== 6. 템플릿 메시지 전송 ==========
	            example5_TemplateMessage(botUtils);
	            
	            // ========== 7. 예약 메시지 전송 ==========
	            example6_ScheduledMessage(botUtils);
	            
	            // ========== 8. 주기적 메시지 전송 ==========
	            example7_PeriodicMessage(botUtils);
	            
	            // ========== 9. Rate Limiting ==========
	            example8_RateLimiting(botUtils);
	            
	            // ========== 10. 마크다운 포맷팅 ==========
	            example9_MarkdownFormatting(botUtils);
	            
	            // 작업 완료 대기
	            Thread.sleep(5000);
	            
	        } finally {
	            // ========== 11. Graceful Shutdown ==========
	            System.out.println("\n=== Graceful Shutdown 시작 ===");
	            botUtils.shutdown();
	            System.out.println("Bot Utils 종료 완료");
	        }
	        
	        System.out.println("\n=== 예제 완료 ===");
	    }
	    
	    /**
	     * 예제 1: 기본 메시지 전송
	     */
	    private static void example1_BasicMessage(NwBotUtils botUtils) throws Exception {
	        System.out.println("\n--- 예제 1: 기본 메시지 전송 ---");
	        
	        try {
	            NwBotMessenger.MessageResponse response = 
	                botUtils.sendTextMessageWithRetry(CHANNEL_ID, "안녕하세요! NAVER WORKS Bot입니다.");
	            
	            System.out.println("메시지 전송 성공: " + response.getMessageId());
	        } catch (Exception e) {
	            System.err.println("메시지 전송 실패: " + e.getMessage());
	        }
	    }
	    
	    /**
	     * 예제 2: 비동기 메시지 전송
	     */
	    private static void example2_AsyncMessage(NwBotUtils botUtils) {
	        System.out.println("\n--- 예제 2: 비동기 메시지 전송 (AsyncExecutor 활용) ---");
	        
	        // 3개의 메시지를 비동기로 전송
	        CompletableFuture<NwBotMessenger.MessageResponse> future1 = 
	            botUtils.sendTextMessageAsync(CHANNEL_ID, "비동기 메시지 1");
	        
	        CompletableFuture<NwBotMessenger.MessageResponse> future2 = 
	            botUtils.sendTextMessageAsync(CHANNEL_ID, "비동기 메시지 2");
	        
	        CompletableFuture<NwBotMessenger.MessageResponse> future3 = 
	            botUtils.sendTextMessageAsync(CHANNEL_ID, "비동기 메시지 3");
	        
	        // 모든 메시지 전송 완료 대기
	        CompletableFuture.allOf(future1, future2, future3)
	            .thenRun(() -> System.out.println("모든 비동기 메시지 전송 완료"))
	            .exceptionally(ex -> {
	                System.err.println("비동기 메시지 전송 실패: " + ex.getMessage());
	                return null;
	            });
	    }
	    
	    /**
	     * 예제 3: 배치 메시지 전송
	     */
	    private static void example3_BatchMessage(NwBotUtils botUtils) {
	        System.out.println("\n--- 예제 3: 배치 메시지 전송 (AsyncExecutor 활용) ---");
	        
	        List<String> channelIds = Arrays.asList(
	            "channel-001",
	            "channel-002",
	            "channel-003",
	            CHANNEL_ID
	        );
	        
	        Map<String, Boolean> results = 
	            botUtils.sendTextMessageToMultipleChannels(
	                channelIds, 
	                "배치 메시지: 모든 채널에 동일한 내용을 전송합니다."
	            );
	        
	        System.out.println("배치 전송 결과:");
	        results.forEach((channelId, success) -> 
	            System.out.printf("  - %s: %s%n", channelId, success ? "성공" : "실패")
	        );
	    }
	    
	    /**
	     * 예제 4: 큐 기반 메시지 전송
	     */
	    private static void example4_QueuedMessage(NwBotUtils botUtils) throws Exception {
	        System.out.println("\n--- 예제 4: 큐 기반 메시지 전송 (순차 처리) ---");
	        
	        // 5개의 메시지를 큐에 추가
	        for (int i = 1; i <= 5; i++) {
	            final int messageNum = i;
	            botUtils.queueTextMessage(
	                CHANNEL_ID, 
	                "큐 메시지 " + i,
	                response -> System.out.println("메시지 " + messageNum + " 전송 성공: " + response.getMessageId()),
	                error -> System.err.println("메시지 " + messageNum + " 전송 실패: " + error.getMessage())
	            );
	        }
	        
	        System.out.println("현재 큐 크기: " + botUtils.getQueueSize());
	        
	        // 큐 처리 대기
	        Thread.sleep(3000);
	    }
	    
	    /**
	     * 예제 5: 템플릿 메시지 전송
	     */
	    private static void example5_TemplateMessage(NwBotUtils botUtils) throws Exception {
	        System.out.println("\n--- 예제 5: 템플릿 메시지 전송 ---");
	        
	        // Welcome 템플릿
	        Map<String, String> welcomeParams = new HashMap<>();
	        welcomeParams.put("name", "홍길동");
	        welcomeParams.put("company", "TrustMate");
	        
	        botUtils.sendTemplateMessage(CHANNEL_ID, "welcome", welcomeParams);
	        System.out.println("Welcome 메시지 전송 완료");
	        
	        // Notification 템플릿
	        Map<String, String> notificationParams = new HashMap<>();
	        notificationParams.put("title", "시스템 점검 안내");
	        notificationParams.put("message", "오늘 오후 2시부터 4시까지 시스템 점검이 예정되어 있습니다.");
	        
	        botUtils.sendTemplateMessage(CHANNEL_ID, "notification", notificationParams);
	        System.out.println("Notification 메시지 전송 완료");
	        
	        // Error 템플릿
	        Map<String, String> errorParams = new HashMap<>();
	        errorParams.put("code", "ERR_500");
	        errorParams.put("message", "서버 내부 오류가 발생했습니다.");
	        
	        botUtils.sendTemplateMessage(CHANNEL_ID, "error", errorParams);
	        System.out.println("Error 메시지 전송 완료");
	    }
	    
	    /**
	     * 예제 6: 예약 메시지 전송
	     */
	    private static void example6_ScheduledMessage(NwBotUtils botUtils) {
	        System.out.println("\n--- 예제 6: 예약 메시지 전송 (AsyncExecutor.schedule 활용) ---");
	        
	        // 10초 후 메시지 전송
	        ScheduledFuture<?> scheduled = botUtils.scheduleTextMessage(
	            CHANNEL_ID, 
	            "이 메시지는 10초 후에 전송됩니다.", 
	            10
	        );
	        
	        System.out.println("메시지 예약 완료 (10초 후 전송)");
	        
	        // 30초 후 리마인더
	        botUtils.scheduleTextMessage(
	            CHANNEL_ID, 
	            "⏰ 리마인더: 회의 시간입니다!", 
	            30
	        );
	        
	        System.out.println("리마인더 예약 완료 (30초 후 전송)");
	    }
	    
	    /**
	     * 예제 7: 주기적 메시지 전송
	     */
	    private static void example7_PeriodicMessage(NwBotUtils botUtils) {
	        System.out.println("\n--- 예제 7: 주기적 메시지 전송 (AsyncExecutor.scheduleAtFixedRate 활용) ---");
	        
	        // 5초 후 시작, 15초마다 상태 메시지 전송
	        ScheduledFuture<?> periodic = botUtils.schedulePeriodicMessage(
	            CHANNEL_ID, 
	            "📊 시스템 상태: 정상 운영 중", 
	            5,  // 초기 지연 5초
	            15  // 15초마다 반복
	        );
	        
	        System.out.println("주기적 메시지 예약 완료 (5초 후 시작, 15초마다 반복)");
	        System.out.println("※ 주의: 실제 운영 환경에서는 적절한 시점에 periodic.cancel()을 호출해야 합니다.");
	        
	        // 실제 사용 시:
	        // 적절한 시점에 취소
	        // Thread.sleep(60000);
	        // periodic.cancel(false);
	    }
	    
	    /**
	     * 예제 8: Rate Limiting
	     */
	    private static void example8_RateLimiting(NwBotUtils botUtils) {
	        System.out.println("\n--- 예제 8: Rate Limiting (초당 메시지 수 제한) ---");
	        
	        List<String> messages = Arrays.asList(
	            "메시지 1",
	            "메시지 2",
	            "메시지 3",
	            "메시지 4",
	            "메시지 5"
	        );
	        
	        long startTime = System.currentTimeMillis();
	        
	        // 초당 2개씩 전송 (2 messages/sec)
	        botUtils.sendMessagesWithRateLimit(CHANNEL_ID, messages, 2);
	        
	        long duration = System.currentTimeMillis() - startTime;
	        System.out.printf("Rate Limit 전송 완료 (소요 시간: %dms)%n", duration);
	        System.out.println("※ 5개 메시지를 초당 2개씩 전송하므로 약 2.5초 소요");
	    }
	    
	    /**
	     * 예제 9: 마크다운 포맷팅
	     */
	    private static void example9_MarkdownFormatting(NwBotUtils botUtils) throws Exception {
	        System.out.println("\n--- 예제 9: 마크다운 포맷팅 ---");
	        
	        // 볼드
	        String boldText = NwBotUtils.bold("중요한 내용입니다!");
	        botUtils.sendTextMessageWithRetry(CHANNEL_ID, boldText);
	        
	        // 이탤릭
	        String italicText = NwBotUtils.italic("강조된 내용입니다.");
	        botUtils.sendTextMessageWithRetry(CHANNEL_ID, italicText);
	        
	        // 인라인 코드
	        String inlineCode = "변수: " + NwBotUtils.inlineCode("userId = 12345");
	        botUtils.sendTextMessageWithRetry(CHANNEL_ID, inlineCode);
	        
	        // 코드 블록
	        String codeBlock = NwBotUtils.codeBlock(
	            "public static void main(String[] args) {\n" +
	            "    System.out.println(\"Hello, World!\");\n" +
	            "}"
	        );
	        botUtils.sendTextMessageWithRetry(CHANNEL_ID, codeBlock);
	        
	        // 불릿 리스트
	        List<String> bulletItems = Arrays.asList(
	            "첫 번째 항목",
	            "두 번째 항목",
	            "세 번째 항목"
	        );
	        String bulletList = "할 일 목록:\n" + NwBotUtils.bulletList(bulletItems);
	        botUtils.sendTextMessageWithRetry(CHANNEL_ID, bulletList);
	        
	        // 번호 매기기 리스트
	        List<String> numberedItems = Arrays.asList(
	            "프로젝트 기획",
	            "설계 및 개발",
	            "테스트 및 배포"
	        );
	        String numberedList = "프로젝트 단계:\n" + NwBotUtils.numberedList(numberedItems);
	        botUtils.sendTextMessageWithRetry(CHANNEL_ID, numberedList);
	        
	        System.out.println("모든 마크다운 포맷팅 메시지 전송 완료");
	    }
	    
	    /**
	     * 설정 로드 (예제용 - 실제로는 Properties 파일이나 환경변수 사용)
	     */
	    private static NwConfig loadConfig() {
	        try {
	            // 방법 1: Properties 파일에서 로드
	            // return NwConfig.fromPropertiesFile("config/naverworks.properties");
	            
	            // 방법 2: 환경변수에서 로드
	            // return NwConfig.fromEnvironment();
	            
	            // 방법 3: System Properties에서 로드
	            // return NwConfig.fromSystemProperties();
	            
	            // 방법 4: 직접 생성 (테스트용)
	            return new NwConfig(
	                "kHIXP3EhyF89TNwaaQUx",
	                "nzkkkJRGvJ",
	                "cbw9d.serviceaccount@tx24.kr",
	                getPrivateKeyMethodChaining(),
	                "bot"
	            );
	            
	        } catch (Exception e) {
	            throw new RuntimeException("설정 로드 실패", e);
	        }
	    }
	    
	    
	    private static String getPrivateKeyMethodChaining() {
	        return new StringBuilder()
	            .append("-----BEGIN PRIVATE KEY-----\n")
	            .append("MIIEugIBADANBgkqhkiG9w0BAQEFAASCBKQwggSgAgEAAoIBAQC+APTvKZ0uydnN\n")
	            .append("cqsYFj6xS3A0FjdIh1WZszLdhs304Eu+K2Iubuv3/0GshX0v96tv8nk02purTIi6\n")
	            .append("VSMsFpNBYVbQAzRp9lgZnynetAg+UEO7Lk4QJzfMTkeQMPmdCZXjyoOoPaOAprAI\n")
	            .append("21e9gYptWlfFu9aJtzc/TQHFtHEjdil0ZcU8yL0GxequihR4rGwLJccLg6Fqi97k\n")
	            .append("xXvZPzJf7peRnlEhW+9x98LEQQoFXWyNkFT7SYDyU/80x3bKlXm8zF+mi3zRcrBB\n")
	            .append("nz1819rPfMxJRfBgfJDvl+hmsluGLDzgBGZ31EB4ooerDjDq96F2sSh7Df+Ghyzn\n")
	            .append("oR1Zq6QlAgMBAAECgf8DDZ/HgHjgL8SV9u3lKiip14gU8mKxet/kYoyw6CQGBLnD\n")
	            .append("ox6AqxuAuPtWEeJ/O/1kmw09FXXkwDOqw830nGEyZEKtAZ+8ZWKAoIADzKMF05yk\n")
	            .append("H6T1WmcgUzinCYOKHdh2Bnj647wO+kLvGDtt2Bqzc/RIGkHrulnYmAW91XqQQMem\n")
	            .append("p5yEPhM9kawSHuvtCKxUk/xf0ym7XbRPYRBGb1OmDOT5qypTnamWC2SIKXyxyIEl\n")
	            .append("6r71XaspnmQKmBkWe40RROWO5t1CSaMadvdUE/X1VQPoIY3mNx8aAv/9TbVIDfPJ\n")
	            .append("4h0hoJrqAT1mzfD8W7P5Ori4X+hL9TcsC+6vsMECgYEA9RUkJ9zGmPUegN3idii0\n")
	            .append("BZm4TsVFRECehYUlC/OzaGMYdO0jqKOCY8gB8/TDSKC3VEL5nkDuHOAeubv0PWuq\n")
	            .append("u3PDauvJBEEhpJpPBTPuLW5LI3jt0R3LKpQi5SeIHNMGpuB0k81xrswWkTe2HJe/\n")
	            .append("rXOBclzX21HZIfVQX2ThHUUCgYEAxne15mcrpCmDOCDreRKUrOexxXveRGNYUWsr\n")
	            .append("Ml2Yi+0X6GWLcaO2msa2G2KB8ISJ1cBZEb0N9UpcOV0CV61fokbr2zWYFbjcIJkJ\n")
	            .append("X40yPC4jNYY3/1R2mTvHGetCNp656Lh3o9W+tzjohq5jJCseQaWKyOtgaZVB/Ugl\n")
	            .append("VcQpqWECgYA/BB+WzFKYM7aTJfo7rX2UTxEv19NWmFLqO/DpoNDJj2lTb0IS82/s\n")
	            .append("Xhn6cz3fJ8vbs5jhUwqmjA36bdSAEnYE2uAtVtEJ8gFHhJG64b5lGg3h4g8sDMAX\n")
	            .append("g51xVHfQCYaVU/NFqbaIXluTHUMLGQ2k+KUZFbw+3U26SIxQ6uxjDQKBgE/sdXxL\n")
	            .append("n++EKCu6VhlzuhvHUnfM4j14JGDlX4fw2TXATRhxjC4/V5IM49kzWlCZj0hdJYFX\n")
	            .append("OP/G6kzPf9n/H7wiA2lLs+tLfppCBtxL6CcEDXnIi1RvlzMuN4fgjdGhKgzl/Igl\n")
	            .append("05/Fcx6Jq7MtCgo1uCYhY7ohOWneW+qmDIEBAoGATa0fODtMVEEnUHY9hrAIpcGQ\n")
	            .append("uTwjao5PZHQR++137dYJ0T1zQsgHK6qPHPfnd7LkKxUanacTizw1fz6NToprh9Yh\n")
	            .append("2XLttJVnAiK37+D6Jsr/BbwlvWe+uyT/FKYsqXcQpJTsccsPeSNTvbmY/ToQN2DY\n")
	            .append("kXiMRdKZDGCsUZMPp8c=\n")
	            .append("-----END PRIVATE KEY-----")
	            .toString();
	    }
	}

	/**
	 * 통합 테스트 예제
	 */
	class NwBotIntegrationTest {
	    
	    public static void runTest() throws Exception {
	        System.out.println("=== NAVER WORKS Bot 통합 테스트 ===\n");
	        
	        // 1. AsyncExecutor 모니터링 시작
	        kr.tx24.lib.executor.AsyncExecutor.startMonitoring();
	        System.out.println("AsyncExecutor 모니터링 시작");
	        
	        // 2. Bot Utils 초기화
	        NwBotUtils botUtils = new NwBotUtils("11085652");
	        
	        try {
	            // 3. 부하 테스트
	            System.out.println("\n--- 부하 테스트: 100개 메시지 비동기 전송 ---");
	            long startTime = System.currentTimeMillis();
	            
	            List<CompletableFuture<NwBotMessenger.MessageResponse>> futures = new java.util.ArrayList<>();
	            
	            for (int i = 0; i < 100; i++) {
	                CompletableFuture<NwBotMessenger.MessageResponse> future = 
	                    botUtils.sendTextMessageAsync("02155ff2-c722-134b-f3b1-04c8d74f6cb5", "Test Message " + i);
	                futures.add(future);
	            }
	            
	            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
	            
	            long duration = System.currentTimeMillis() - startTime;
	            System.out.printf("부하 테스트 완료: 100개 메시지를 %dms에 전송%n", duration);
	            
	            // 4. AsyncExecutor 상태 확인
	            System.out.println("\n--- AsyncExecutor 상태 ---");
	            System.out.println(kr.tx24.lib.executor.AsyncExecutor.getStatus());
	            
	            // 5. 통계 확인
	            System.out.println("\n--- Bot Utils 통계 ---");
	            System.out.printf("큐 크기: %d%n", botUtils.getQueueSize());
	            System.out.printf("Shutdown 여부: %s%n", botUtils.isShutdown());
	            
	        } finally {
	            // 6. Graceful Shutdown
	            System.out.println("\n--- Graceful Shutdown ---");
	            botUtils.shutdown();
	            System.out.println("Bot Utils 종료 완료");
	        }
	        
	        System.out.println("\n=== 통합 테스트 완료 ===");
	    }
}
