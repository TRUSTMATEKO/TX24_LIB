package kr.tx24.naverwork.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class NwBotUtils {

	private final NwBotMessenger messenger;
    private final ExecutorService executorService;
    private final BlockingQueue<MessageTask> messageQueue;
    private final int maxRetries;
    private final long retryDelayMillis;
    
    /**
     * 생성자
     * 
     * @param botId Bot ID
     */
    public NwBotUtils(String botId) {
        this(botId, 3, 1000);
    }
    
    /**
     * 생성자 (재시도 설정 포함)
     * 
     * @param botId Bot ID
     * @param maxRetries 최대 재시도 횟수
     * @param retryDelayMillis 재시도 간격 (밀리초)
     */
    public NwBotUtils(String botId, int maxRetries, long retryDelayMillis) {
        this.messenger = new NwBotMessenger(botId);
        this.executorService = Executors.newFixedThreadPool(5);
        this.messageQueue = new LinkedBlockingQueue<>();
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
        
        // 메시지 큐 처리 스레드 시작
        startMessageQueueProcessor();
    }
    
    /**
     * 재시도 로직이 포함된 텍스트 메시지 전송
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public NwBotMessenger.MessageResponse sendTextMessageWithRetry(
            String channelId, String text) throws IOException {
        return executeWithRetry(() -> messenger.sendTextMessage(channelId, text));
    }
    
    /**
     * 비동기 텍스트 메시지 전송
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @return CompletableFuture
     */
    public CompletableFuture<NwBotMessenger.MessageResponse> sendTextMessageAsync(
            String channelId, String text) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendTextMessageWithRetry(channelId, text);
            } catch (IOException e) {
                throw new RuntimeException("비동기 메시지 전송 실패", e);
            }
        }, executorService);
    }
    
    /**
     * 메시지 큐에 추가 (순차 처리)
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @param onSuccess 성공 콜백
     * @param onFailure 실패 콜백
     */
    public void queueTextMessage(String channelId, String text,
                                 Consumer<NwBotMessenger.MessageResponse> onSuccess,
                                 Consumer<Exception> onFailure) {
        MessageTask task = new MessageTask(
            () -> messenger.sendTextMessage(channelId, text),
            onSuccess,
            onFailure
        );
        
        messageQueue.offer(task);
    }
    
    /**
     * 배치 메시지 전송 (여러 채널에 동일 메시지)
     * 
     * @param channelIds 채널 ID 목록
     * @param text 메시지 텍스트
     * @return 전송 결과 맵 (채널 ID -> 성공 여부)
     */
    public Map<String, Boolean> sendTextMessageToMultipleChannels(
            List<String> channelIds, String text) {
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String channelId : channelIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    sendTextMessageWithRetry(channelId, text);
                    results.put(channelId, true);
                } catch (Exception e) {
                    results.put(channelId, false);
                    System.err.println("채널 " + channelId + " 전송 실패: " + e.getMessage());
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // 모든 전송 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return results;
    }
    
    /**
     * 메시지 템플릿 적용
     * 
     * @param channelId 채널 ID
     * @param templateName 템플릿 이름
     * @param params 파라미터 맵
     * @return 메시지 전송 응답
     * @throws IOException 전송 실패 시
     */
    public NwBotMessenger.MessageResponse sendTemplateMessage(
            String channelId, String templateName, Map<String, String> params) throws IOException {
        String text = applyTemplate(templateName, params);
        return sendTextMessageWithRetry(channelId, text);
    }
    
    /**
     * 예약 메시지 전송
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @param delaySeconds 지연 시간 (초)
     * @return ScheduledFuture
     */
    public ScheduledFuture<?> scheduleTextMessage(String channelId, String text, long delaySeconds) {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        
        return scheduler.schedule(() -> {
            try {
                sendTextMessageWithRetry(channelId, text);
                System.out.println("예약 메시지 전송 완료: " + text);
            } catch (IOException e) {
                System.err.println("예약 메시지 전송 실패: " + e.getMessage());
            } finally {
                scheduler.shutdown();
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }
    
    /**
     * Rate Limiting을 적용한 메시지 전송
     * (초당 최대 메시지 수 제한)
     * 
     * @param channelId 채널 ID
     * @param messages 메시지 목록
     * @param messagesPerSecond 초당 메시지 수
     */
    public void sendMessagesWithRateLimit(String channelId, List<String> messages, 
                                         int messagesPerSecond) {
        long delayMillis = 1000L / messagesPerSecond;
        
        for (String message : messages) {
            try {
                sendTextMessageWithRetry(channelId, message);
                Thread.sleep(delayMillis);
            } catch (Exception e) {
                System.err.println("메시지 전송 실패: " + e.getMessage());
            }
        }
    }
    
    /**
     * 재시도 로직 실행
     * 
     * @param action 실행할 작업
     * @return 메시지 전송 응답
     * @throws IOException 모든 재시도 실패 시
     */
    private NwBotMessenger.MessageResponse executeWithRetry(
            MessageAction action) throws IOException {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < maxRetries) {
            try {
                return action.execute();
            } catch (IOException e) {
                lastException = e;
                attempt++;
                
                if (attempt < maxRetries) {
                    System.err.printf("메시지 전송 실패 (재시도 %d/%d): %s%n", 
                        attempt, maxRetries, e.getMessage());
                    
                    try {
                        Thread.sleep(retryDelayMillis * attempt); // 지수 백오프
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("재시도 중 인터럽트 발생", ie);
                    }
                }
            }
        }
        
        throw new IOException("메시지 전송 실패 (" + maxRetries + "회 재시도)", lastException);
    }
    
    /**
     * 메시지 큐 처리 스레드 시작
     */
    private void startMessageQueueProcessor() {
        Thread processor = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    MessageTask task = messageQueue.take();
                    
                    try {
                    	NwBotMessenger.MessageResponse response = 
                            executeWithRetry(task.action);
                        
                        if (task.onSuccess != null) {
                            task.onSuccess.accept(response);
                        }
                    } catch (IOException e) {
                        if (task.onFailure != null) {
                            task.onFailure.accept(e);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        
        processor.setDaemon(true);
        processor.setName("MessageQueueProcessor");
        processor.start();
    }
    
    /**
     * 템플릿 적용
     * 
     * @param templateName 템플릿 이름
     * @param params 파라미터
     * @return 적용된 텍스트
     */
    private String applyTemplate(String templateName, Map<String, String> params) {
        // 간단한 템플릿 예제
        Map<String, String> templates = new HashMap<>();
        templates.put("welcome", "안녕하세요 {name}님! {company}에 오신 것을 환영합니다.");
        templates.put("notification", "[알림] {title}\n{message}");
        templates.put("error", "⚠️ 오류 발생\n코드: {code}\n메시지: {message}");
        
        String template = templates.getOrDefault(templateName, "{message}");
        
        for (Map.Entry<String, String> entry : params.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return template;
    }
    
    /**
     * 종료
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // ==================== 내부 클래스 ====================
    
    /**
     * 메시지 액션 인터페이스
     */
    @FunctionalInterface
    private interface MessageAction {
    	NwBotMessenger.MessageResponse execute() throws IOException;
    }
    
    /**
     * 메시지 작업
     */
    private static class MessageTask {
        final MessageAction action;
        final Consumer<NwBotMessenger.MessageResponse> onSuccess;
        final Consumer<Exception> onFailure;
        
        MessageTask(MessageAction action,
                   Consumer<NwBotMessenger.MessageResponse> onSuccess,
                   Consumer<Exception> onFailure) {
            this.action = action;
            this.onSuccess = onSuccess;
            this.onFailure = onFailure;
        }
    }
    
    // ==================== 정적 유틸리티 메서드 ====================
    
    /**
     * 마크다운 형식 텍스트 생성
     * 
     * @param text 원본 텍스트
     * @return 마크다운 포맷팅된 텍스트
     */
    public static String bold(String text) {
        return "**" + text + "**";
    }
    
    /**
     * 이탤릭 텍스트 생성
     * 
     * @param text 원본 텍스트
     * @return 이탤릭 포맷팅된 텍스트
     */
    public static String italic(String text) {
        return "*" + text + "*";
    }
    
    /**
     * 코드 블록 생성
     * 
     * @param code 코드 텍스트
     * @return 코드 블록 포맷팅된 텍스트
     */
    public static String codeBlock(String code) {
        return "```\n" + code + "\n```";
    }
    
    /**
     * 목록 형식 텍스트 생성
     * 
     * @param items 목록 아이템
     * @return 목록 포맷팅된 텍스트
     */
    public static String bulletList(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("• ").append(item).append("\n");
        }
        return sb.toString();
    }
}
