package kr.tx24.lib.logback;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Redis Appender for Logback (Simplified)
 * 
 * 특징:
 * - 동기 방식 Redis 초기화
 * - 단일 연결 유지
 * - 연결 실패시 2초 간격 3회 재시도
 * - 재시도 실패시 큐 데이터 폐기
 * - 외부에서 shutdown 제어 가능
 * 
 * <p><b>외부 제어 예시:</b></p>
 * <pre>
 * // Graceful shutdown (큐 처리 후 종료)
 * RedisAppender.shutdown();
 * 
 * // 상태 확인
 * boolean running = RedisAppender.isRunning();
 * int queueSize = RedisAppender.getQueueSize();
 * </pre>
 */
public class RedisAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {
    
    // Redis 리소스 (단일 연결)
    private static RedisClient client;
    private static StatefulRedisConnection<String, String> connection;
    private static RedisAsyncCommands<String, String> asyncCommands;
    
    // 설정
    private static final int QUEUE_CAPACITY = 10000;
    private static final int POLL_TIMEOUT_MS = 1000;
    private static final int RETRY_DELAY_MS = 2000;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int SHUTDOWN_TIMEOUT_MS = 1000;
    
    // 상태 관리
    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    
    // 스레드 참조
    private static Thread workerThread;
    private static RedisAppender instance;
    
    // 인스턴스 변수
    private Layout<ILoggingEvent> layout;
    
    // ========================================================================
    // Lifecycle Methods
    // ========================================================================
    
    @Override
    public void start() {
        if (instance == null) {
            instance = this;
        }
        
        // Redis 동기 초기화 (한 번만)
        if (!initialized.get()) {
            synchronized (RedisAppender.class) {
                if (!initialized.get()) {
                    if (initializeRedis()) {
                        initialized.set(true);
                        System.err.println("RedisAppender: initialized");
                    } else {
                        System.err.println("RedisAppender: initialization failed");
                        super.stop();
                        return;
                    }
                }
            }
        }
        
        // Worker 스레드 시작 (한 번만)
        if (running.compareAndSet(false, true)) {
            startWorkerThread();
            //System.err.println("RedisAppender: Worker thread started");
        }
        
        super.start();
    }
    
    @Override
    public void stop() {
        performShutdown();
        super.stop();
    }
    
    // ========================================================================
    // Redis 초기화 및 연결 관리
    // ========================================================================
    
    /**
     * Redis 초기화 (동기 방식)
     */
    private boolean initializeRedis() {
        String redisUri = "";
        try {
            SystemUtils.init();
            
            redisUri = SystemUtils.getRedisLogUri();
            if (redisUri == null || redisUri.trim().isEmpty()) {
                System.err.println("RedisAppender: Redis URI is empty");
                return false;
            }
            
            // Redis URI 설정
            RedisURI uri = RedisURI.create(redisUri);
            uri.setTimeout(Duration.ofSeconds(10));
            
            // Redis Client 생성
            client = RedisClient.create(uri);
            
            // 단일 Connection 생성
            connection = client.connect();
            connection.setAutoFlushCommands(true);
            
            // Async Commands 획득
            asyncCommands = connection.async();
            
            // 연결 테스트
            asyncCommands.ping().get(5, TimeUnit.SECONDS);
            
            return true;
            
        } catch (Exception e) {
            System.err.println("RedisAppender: " + redisUri);
            System.err.println("RedisAppender: Redis initialization failed - " + e.getMessage());
            cleanupRedis();
            return false;
        }
    }
    
    /**
     * 연결 유효성 확인
     */
    private boolean isConnectionValid() {
        try {
            if (connection != null && connection.isOpen() && asyncCommands != null) {
                // Ping 테스트 (비동기)
                asyncCommands.ping().get(1, TimeUnit.SECONDS);
                return true;
            }
        } catch (Exception e) {
            // 연결 실패
        }
        return false;
    }
    
    /**
     * Redis 재연결 시도
     * shutdown 중에는 재연결하지 않음
     */
    private boolean reconnect() {
        // shutdown 중이면 재연결 시도하지 않음
        if (shuttingDown.get()) {
            return false;
        }
        
        // 기존 연결 정리
        cleanupRedis();
        
        // 재연결 시도 (3회)
        for (int i = 0; i < MAX_RETRY_COUNT; i++) {
            if (i > 0) {
                System.err.println("RedisAppender: Reconnection attempt " + (i + 1) + "/" + MAX_RETRY_COUNT);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            
            if (initializeRedis()) {
                System.err.println("RedisAppender: Reconnected");
                return true;
            }
        }
        
        System.err.println("RedisAppender: Failed to reconnect after " + MAX_RETRY_COUNT + " attempts");
        return false;
    }
    
    /**
     * Redis 리소스 정리
     */
    private void cleanupRedis() {
        try {
            if (asyncCommands != null) {
                asyncCommands = null;
            }
            
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // 무시
                }
                connection = null;
            }
            
            if (client != null) {
                try {
                    client.shutdown();
                } catch (Exception e) {
                    // 무시
                }
                client = null;
            }
        } catch (Exception e) {
            // 무시
        }
    }
    
    // ========================================================================
    // Worker Thread
    // ========================================================================
    
    private void startWorkerThread() {
        workerThread = new Thread(() -> {
            processQueue();
        }, "RedisAppender-Worker");
        
        workerThread.setDaemon(false);
        workerThread.start();
    }
    
    /**
     * Queue 처리 메인 루프
     */
    private void processQueue() {
        AtomicInteger consecutiveFailures = new AtomicInteger(0);
        
        while (running.get() || !QUEUE.isEmpty()) {
            try {
                // Queue에서 로그 가져오기
                String log = QUEUE.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                
                if (log != null) {
                    // 연결 확인
                    if (!isConnectionValid()) {
                        // 재연결 시도
                        if (!reconnect()) {
                            // 재연결 실패 - Queue 비우기
                            int discarded = QUEUE.size() + 1; // 현재 log 포함
                            QUEUE.clear();
                            System.out.println("RedisAppender: Connection failed after " + MAX_RETRY_COUNT + 
                                " retries. Discarded " + discarded + " log messages");
                            consecutiveFailures.set(0);
                            continue;
                        }
                    }
                    
                    // Redis로 전송
                    try {
                        asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log);
                        consecutiveFailures.set(0); // 성공시 리셋
                        
                    } catch (Exception e) {
                        // 전송 실패
                        int failures = consecutiveFailures.incrementAndGet();
                        
                        if (failures >= MAX_RETRY_COUNT) {
                            // 연속 실패 - Queue 비우기
                            int discarded = QUEUE.size() + 1;
                            QUEUE.clear();
                            System.out.println("RedisAppender: Failed to send logs after " + MAX_RETRY_COUNT + 
                                " consecutive failures. Discarded " + discarded + " log messages");
                            consecutiveFailures.set(0);
                        } else {
                            // 로그를 다시 큐에 넣기
                            QUEUE.offer(log);
                            Thread.sleep(RETRY_DELAY_MS);
                        }
                    }
                }
                
            } catch (InterruptedException e) {
                // Shutdown 신호
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("RedisAppender: Unexpected error in worker thread - " + e.getMessage());
            }
        }
    }
    
    // ========================================================================
    // Shutdown 처리
    // ========================================================================
    
    /**
     * 내부 shutdown 처리
     */
    private void performShutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        
        shuttingDown.set(true);
        
        // Worker Thread 종료
        if (workerThread != null && workerThread.isAlive()) {
            try {
                workerThread.interrupt();
                workerThread.join(SHUTDOWN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // 남은 로그 전송 시도
        flushQueue();
        
        // Redis 리소스 정리
        cleanupRedis();
        
        initialized.set(false);
        shuttingDown.set(false);
        
        System.err.println("RedisAppender shutdown");
    }
    
    /**
     * Queue에 남은 로그 모두 전송
     * shutdown 중에는 재연결 시도하지 않음
     */
    private void flushQueue() {
        if (!initialized.get()) {
            return;
        }
        
        int processed = 0;
        int failed = 0;
        
        while (!QUEUE.isEmpty()) {
            try {
                String log = QUEUE.poll(100, TimeUnit.MILLISECONDS);
                if (log != null) {
                    try {
                        // shutdown 중이므로 연결 체크 없이 바로 전송 시도
                        // 실패하면 그냥 포기 (재연결 시도 안함)
                        if (asyncCommands != null) {
                            asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log)
                                .toCompletableFuture()
                                .get(1, TimeUnit.SECONDS);
                            processed++;
                        } else {
                            failed++;
                        }
                    } catch (Exception e) {
                        failed++;
                    }
                }
                
            } catch (Exception e) {
                failed = QUEUE.size();
                QUEUE.clear();
                break;
            }
        }
        
        if (processed > 0) {
            //System.err.println("RedisAppender: Flushed " + processed + " log messages");
        }
        if (failed > 0) {
            //System.err.println("RedisAppender: Failed to flush " + failed + " log messages");
        }
    }
    
    // ========================================================================
    // Appender Methods
    // ========================================================================
    
    @Override
    protected void append(ILoggingEvent event) {
        // Shutdown 중이면 무시
        if (layout == null || !running.get() || shuttingDown.get()) {
            return;
        }
        
        try {
            String log = layout.doLayout(event);
            
            // Queue가 가득 찬 경우 오래된 로그 제거
            if (!QUEUE.offer(log)) {
                QUEUE.poll();
                QUEUE.offer(log);
            }
            
        } catch (Exception e) {
            // 무시
        }
    }
    
    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }
    
    // ========================================================================
    // Public API
    // ========================================================================
    
    /**
     * Graceful shutdown - Queue에 남은 로그를 모두 전송한 후 종료
     * 
     * <p>사용 예:</p>
     * <pre>
     * // 애플리케이션 종료 시
     * RedisAppender.shutdown();
     * </pre>
     */
    public static void shutdown() {
        if (instance != null) {
            //System.err.println("RedisAppender: External shutdown called");
            instance.performShutdown();
        } else {
            //System.err.println("RedisAppender: Shutdown called but instance is null");
        }
    }
    
    /**
     * RedisAppender 실행 여부 확인
     * 
     * @return true if running
     */
    public static boolean isRunning() {
        return running.get();
    }
    
    /**
     * 현재 Queue에 대기 중인 로그 수
     * 
     * @return queue size
     */
    public static int getQueueSize() {
        return QUEUE.size();
    }
    
    /**
     * RedisAppender 상태 정보
     * 
     * @return status string
     */
    public static String getStatus() {
        return String.format("RedisAppender{running=%s, initialized=%s, queueSize=%d, shuttingDown=%s}",
            running.get(), initialized.get(), QUEUE.size(), shuttingDown.get());
    }
}