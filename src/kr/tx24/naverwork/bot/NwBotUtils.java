package kr.tx24.naverwork.bot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;

/**
 * NAVER WORKS Bot 유틸리티 클래스
 * 
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>비동기 메시지 전송 (AsyncExecutor 활용)</li>
 *   <li>메시지 큐 처리 (순차 전송)</li>
 *   <li>재시도 로직 (지수 백오프)</li>
 *   <li>배치 전송 지원</li>
 *   <li>Rate Limiting</li>
 *   <li>템플릿 메시지 지원</li>
 *   <li>예약 메시지 전송 (AsyncExecutor scheduler 활용)</li>
 * </ul>
 * 
 * <p><b>변경사항:</b></p>
 * <ul>
 *   <li>Executors.newFixedThreadPool → AsyncExecutor.getExecutor() 사용</li>
 *   <li>Executors.newScheduledThreadPool → AsyncExecutor.schedule* 메서드 사용</li>
 *   <li>Thread-safe 큐 처리 개선</li>
 *   <li>Graceful shutdown 지원</li>
 * </ul>
 * 
 * @author TX24
 */
public class NwBotUtils {

    private static final Logger logger = LoggerFactory.getLogger(NwBotUtils.class);
    
    private final NwBotMessenger messenger;
    private final BlockingQueue<MessageTask> messageQueue;
    private final int maxRetries;
    private final long retryDelayMillis;
    private final AtomicBoolean isShutdown;
    private final Thread queueProcessorThread;
    
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
        this.messageQueue = new LinkedBlockingQueue<>();
        this.maxRetries = maxRetries;
        this.retryDelayMillis = retryDelayMillis;
        this.isShutdown = new AtomicBoolean(false);
        
        // 메시지 큐 처리 스레드 시작
        this.queueProcessorThread = startMessageQueueProcessor();
        
        logger.info("NwBotUtils 초기화 완료 - botId: {}, maxRetries: {}, retryDelay: {}ms", 
            botId, maxRetries, retryDelayMillis);
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
     * <p><b>변경사항:</b> AsyncExecutor 사용</p>
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @return CompletableFuture
     */
    public CompletableFuture<NwBotMessenger.MessageResponse> sendTextMessageAsync(
            String channelId, String text) {
        checkShutdown();
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sendTextMessageWithRetry(channelId, text);
            } catch (IOException e) {
                logger.error("비동기 메시지 전송 실패 - channelId: {}, text: {}", channelId, text, e);
                throw new RuntimeException("비동기 메시지 전송 실패", e);
            }
        }, AsyncExecutor.getExecutor());
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
        checkShutdown();
        
        MessageTask task = new MessageTask(
            () -> messenger.sendTextMessage(channelId, text),
            onSuccess,
            onFailure
        );
        
        boolean queued = messageQueue.offer(task);
        if (!queued) {
            logger.warn("메시지 큐가 가득 참 - channelId: {}, text: {}", channelId, text);
            if (onFailure != null) {
                onFailure.accept(new IOException("메시지 큐가 가득 참"));
            }
        }
    }
    
    /**
     * 배치 메시지 전송 (여러 채널에 동일 메시지)
     * 
     * <p><b>변경사항:</b> AsyncExecutor 사용</p>
     * 
     * @param channelIds 채널 ID 목록
     * @param text 메시지 텍스트
     * @return 전송 결과 맵 (채널 ID -> 성공 여부)
     */
    public Map<String, Boolean> sendTextMessageToMultipleChannels(
            List<String> channelIds, String text) {
        checkShutdown();
        
        Map<String, Boolean> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (String channelId : channelIds) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    sendTextMessageWithRetry(channelId, text);
                    results.put(channelId, true);
                    logger.debug("배치 메시지 전송 성공 - channelId: {}", channelId);
                } catch (Exception e) {
                    results.put(channelId, false);
                    logger.error("배치 메시지 전송 실패 - channelId: {}, text: {}", channelId, text, e);
                }
            }, AsyncExecutor.getExecutor());
            
            futures.add(future);
        }
        
        // 모든 전송 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        logger.info("배치 메시지 전송 완료 - 총: {}, 성공: {}, 실패: {}", 
            channelIds.size(), 
            results.values().stream().filter(v -> v).count(),
            results.values().stream().filter(v -> !v).count());
        
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
        checkShutdown();
        String text = applyTemplate(templateName, params);
        return sendTextMessageWithRetry(channelId, text);
    }
    
    /**
     * 예약 메시지 전송
     * 
     * <p><b>변경사항:</b> AsyncExecutor.schedule 사용</p>
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @param delaySeconds 지연 시간 (초)
     * @return ScheduledFuture
     */
    public ScheduledFuture<?> scheduleTextMessage(String channelId, String text, long delaySeconds) {
        checkShutdown();
        
        logger.info("메시지 예약 - channelId: {}, delay: {}초", channelId, delaySeconds);
        
        return AsyncExecutor.schedule(() -> {
            try {
                sendTextMessageWithRetry(channelId, text);
                logger.info("예약 메시지 전송 완료 - channelId: {}, text: {}", channelId, text);
            } catch (IOException e) {
                logger.error("예약 메시지 전송 실패 - channelId: {}, text: {}", channelId, text, e);
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }
    
    /**
     * 주기적 메시지 전송 (고정 간격)
     * 
     * <p><b>추가:</b> AsyncExecutor.scheduleAtFixedRate 활용</p>
     * 
     * @param channelId 채널 ID
     * @param text 메시지 텍스트
     * @param initialDelaySeconds 초기 지연 시간 (초)
     * @param periodSeconds 주기 (초)
     * @return ScheduledFuture
     */
    public ScheduledFuture<?> schedulePeriodicMessage(String channelId, String text, 
                                                      long initialDelaySeconds, long periodSeconds) {
        checkShutdown();
        
        logger.info("주기적 메시지 예약 - channelId: {}, initialDelay: {}초, period: {}초", 
            channelId, initialDelaySeconds, periodSeconds);
        
        return AsyncExecutor.scheduleAtFixedRate(() -> {
            try {
                sendTextMessageWithRetry(channelId, text);
                logger.debug("주기적 메시지 전송 완료 - channelId: {}", channelId);
            } catch (IOException e) {
                logger.error("주기적 메시지 전송 실패 - channelId: {}", channelId, e);
            }
        }, initialDelaySeconds, periodSeconds, TimeUnit.SECONDS);
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
        checkShutdown();
        
        if (messagesPerSecond <= 0) {
            throw new IllegalArgumentException("messagesPerSecond는 양수여야 합니다");
        }
        
        long delayMillis = 1000L / messagesPerSecond;
        
        logger.info("Rate Limit 메시지 전송 시작 - channelId: {}, 총: {}개, rate: {}/초", 
            channelId, messages.size(), messagesPerSecond);
        
        for (int i = 0; i < messages.size(); i++) {
            final String message = messages.get(i);
            final int messageIndex = i + 1;
            
            try {
                sendTextMessageWithRetry(channelId, message);
                logger.debug("Rate Limit 메시지 전송 ({}/{}) - channelId: {}", 
                    messageIndex, messages.size(), channelId);
                
                // 마지막 메시지가 아니면 대기
                if (i < messages.size() - 1) {
                    Thread.sleep(delayMillis);
                }
            } catch (Exception e) {
                logger.error("Rate Limit 메시지 전송 실패 ({}/{}) - channelId: {}", 
                    messageIndex, messages.size(), channelId, e);
            }
        }
        
        logger.info("Rate Limit 메시지 전송 완료 - channelId: {}", channelId);
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
                NwBotMessenger.MessageResponse response = action.execute();
                
                if (attempt > 0) {
                    logger.info("메시지 전송 성공 (재시도 {}/{})", attempt, maxRetries);
                }
                
                return response;
                
            } catch (IOException e) {
                lastException = e;
                attempt++;
                
                if (attempt < maxRetries) {
                    long backoffDelay = retryDelayMillis * attempt; // 지수 백오프
                    logger.warn("메시지 전송 실패 (재시도 {}/{}), {}ms 후 재시도: {}", 
                        attempt, maxRetries, backoffDelay, e.getMessage());
                    
                    try {
                        Thread.sleep(backoffDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("재시도 중 인터럽트 발생", ie);
                    }
                } else {
                    logger.error("메시지 전송 최종 실패 ({}회 재시도)", maxRetries, e);
                }
            }
        }
        
        throw new IOException("메시지 전송 실패 (" + maxRetries + "회 재시도)", lastException);
    }
    
    /**
     * 메시지 큐 처리 스레드 시작
     * 
     * <p><b>변경사항:</b> Daemon 스레드로 생성하여 JVM 종료 시 자동 정리</p>
     * 
     * @return 큐 처리 스레드
     */
    private Thread startMessageQueueProcessor() {
        Thread processor = new Thread(() -> {
            logger.info("메시지 큐 처리 스레드 시작");
            
            while (!Thread.currentThread().isInterrupted() && !isShutdown.get()) {
                try {
                    MessageTask task = messageQueue.take();
                    
                    try {
                        NwBotMessenger.MessageResponse response = 
                            executeWithRetry(task.action);
                        
                        if (task.onSuccess != null) {
                            task.onSuccess.accept(response);
                        }
                        
                        logger.debug("큐 메시지 전송 성공");
                        
                    } catch (IOException e) {
                        logger.error("큐 메시지 전송 실패", e);
                        if (task.onFailure != null) {
                            task.onFailure.accept(e);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.info("메시지 큐 처리 스레드 인터럽트");
                    break;
                }
            }
            
            logger.info("메시지 큐 처리 스레드 종료");
        }, "NwBotUtils-QueueProcessor");
        
        processor.setDaemon(true);
        processor.start();
        
        return processor;
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
        templates.put("info", "ℹ️ 정보\n{message}");
        templates.put("success", "✅ 성공\n{message}");
        
        String template = templates.getOrDefault(templateName, "{message}");
        
        for (Map.Entry<String, String> entry : params.entrySet()) {
            template = template.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        
        return template;
    }
    
    /**
     * Shutdown 체크
     * 
     * @throws IllegalStateException shutdown된 경우
     */
    private void checkShutdown() {
        if (isShutdown.get()) {
            throw new IllegalStateException("NwBotUtils가 이미 종료되었습니다");
        }
    }
    
    /**
     * 남은 큐 작업 수 반환
     * 
     * @return 큐에 남은 작업 수
     */
    public int getQueueSize() {
        return messageQueue.size();
    }
    
    /**
     * Shutdown 여부 반환
     * 
     * @return shutdown 여부
     */
    public boolean isShutdown() {
        return isShutdown.get();
    }
    
    /**
     * Graceful shutdown
     * 
     * <p><b>종료 절차:</b></p>
     * <ol>
     *   <li>새 작업 거부</li>
     *   <li>큐에 남은 작업 완료 대기 (최대 30초)</li>
     *   <li>큐 처리 스레드 종료</li>
     * </ol>
     * 
     * <p><b>참고:</b> AsyncExecutor는 별도로 관리되므로 여기서 종료하지 않음</p>
     */
    public void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            logger.debug("NwBotUtils가 이미 종료되었습니다");
            return;
        }
        
        logger.info("NwBotUtils 종료 시작 - 남은 큐 작업: {}", messageQueue.size());
        
        try {
            // 큐 처리 완료 대기 (최대 30초)
            long startTime = System.currentTimeMillis();
            long timeoutMillis = 30_000;
            
            while (!messageQueue.isEmpty()) {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed > timeoutMillis) {
                    logger.warn("큐 처리 타임아웃 - 남은 작업: {}", messageQueue.size());
                    break;
                }
                Thread.sleep(100);
            }
            
            // 큐 처리 스레드 종료
            if (queueProcessorThread != null && queueProcessorThread.isAlive()) {
                queueProcessorThread.interrupt();
                queueProcessorThread.join(5000);
                
                if (queueProcessorThread.isAlive()) {
                    logger.warn("큐 처리 스레드가 5초 내에 종료되지 않았습니다");
                }
            }
            
            logger.info("NwBotUtils 종료 완료");
            
        } catch (InterruptedException e) {
            logger.error("NwBotUtils 종료 중 인터럽트 발생", e);
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
     * 인라인 코드 생성
     * 
     * @param code 코드 텍스트
     * @return 인라인 코드 포맷팅된 텍스트
     */
    public static String inlineCode(String code) {
        return "`" + code + "`";
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
        return sb.toString().trim();
    }
    
    /**
     * 번호 매기기 목록 생성
     * 
     * @param items 목록 아이템
     * @return 번호 매기기 목록 포맷팅된 텍스트
     */
    public static String numberedList(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append((i + 1)).append(". ").append(items.get(i)).append("\n");
        }
        return sb.toString().trim();
    }
}