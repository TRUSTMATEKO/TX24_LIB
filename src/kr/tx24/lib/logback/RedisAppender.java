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
 * Redis Appender for Logback
 * 
 * 비동기 Redis 연결로 초기 로그 손실 방지
 * - Redis 연결을 백그라운드에서 비동기 처리
 * - 연결 완료 전에도 로그를 Queue에 수집
 * - 연결 완료 후 쌓인 로그부터 전송 시작
 * - Connection 유효성 자동 확인 및 재연결
 * - 3회 재시도 실패 시 Queue 초기화
 */
public class RedisAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    // Redis Resources (Single Connection)
    private static volatile RedisClient client;
    private static volatile StatefulRedisConnection<String, String> connection;
    private static volatile RedisAsyncCommands<String, String> asyncCommands;
    
    // Configuration
    private static final int QUEUE_CAPACITY = 10000;
    private static final int SHUTDOWN_TIMEOUT_MS = 3000;
    private static final int POLL_TIMEOUT_MS = 1000;
    private static final int MAX_RETRY_COUNT = 3;
    private static final int RECONNECT_DELAY_MS = 2000;
    private static final int INIT_CHECK_INTERVAL_MS = 100;
    
    // Lifecycle Markers
    private static final String PROCESS_STARTED = "#process started,";
    private static final String PROCESS_STOPPED = "#process shutdown,";
    private static final long PROCESS_ID = System.currentTimeMillis();
    
    // State Management
    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean initializing = new AtomicBoolean(false);
    private static final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private static final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private static final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    
    // Thread References
    private static volatile Thread shutdownHook;
    private static volatile Thread workerThread;
    private static volatile Thread initThread;
    private static volatile RedisAppender instance;
    
    // Instance Variable
    private Layout<ILoggingEvent> layout;

    // ========================================================================
    // Lifecycle Methods
    // ========================================================================
    
    @Override
    public void start() {
        if (instance == null) {
            instance = this;
        }
        
        // Worker Thread 먼저 시작 (로그 수집 즉시 시작)
        if (running.compareAndSet(false, true)) {
            startWorkerThread();
        }
        
        // Redis 초기화는 비동기로 진행 (최초 1회)
        if (!initialized.get() && initializing.compareAndSet(false, true)) {
            startAsyncInitialization();
            registerShutdownHook();
        }
        
        super.start();
    }
    
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        
        shuttingDown.set(true);
        
        // 초기화 스레드 중단
        stopInitThread();
        
        // Worker Thread 종료
        stopWorkerThread();
        
        // 남은 로그 전송
        flushQueue();
        
        // Redis 리소스 정리
        cleanupRedis();
        
        // ShutdownHook 제거
        removeShutdownHook();
        
        initialized.set(false);
        initializing.set(false);
        super.stop();
    }

    // ========================================================================
    // Async Initialization
    // ========================================================================
    
    /**
     * Redis 초기화를 비동기로 시작
     */
    private void startAsyncInitialization() {
        initThread = new Thread(() -> {
            try {
                SystemUtils.init();
                
                // 초기화 시도 (실패 시 재시도)
                while (!initialized.get() && running.get() && !shuttingDown.get()) {
                    if (initializeRedis()) {
                        break;
                    } else {
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
            } catch (Exception e) {
                System.err.println("RedisAppender: async initialization failed - " + e.getMessage());
            } finally {
                initializing.set(false);
            }
        }, "RedisAppender-Init");
        
        initThread.setDaemon(true);
        initThread.start();
    }
    
    /**
     * 초기화 스레드 중지
     */
    private void stopInitThread() {
        if (initThread != null && initThread.isAlive()) {
            try {
                initThread.interrupt();
                initThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * 초기화 스레드 중지 (ShutdownHook용 - 안전)
     */
    private void stopInitThreadSafe() {
        try {
            if (initThread != null && initThread.isAlive()) {
                try {
                    initThread.interrupt();
                    initThread.join(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (NoClassDefFoundError e) {
                    // 무시
                }
            }
        } catch (NoClassDefFoundError e) {
            // 무시
        } catch (Throwable t) {
            // 무시
        }
    }

    // ========================================================================
    // Redis Initialization
    // ========================================================================
    
    private boolean initializeRedis() {
        try {
            String redisUri = SystemUtils.getRedisLogUri();
            
            if (redisUri == null || redisUri.trim().isEmpty()) {
                System.err.println("RedisAppender: Redis URI is empty");
                return false;
            }
            
            // RedisURI 설정
            RedisURI uri = RedisURI.create(redisUri);
            uri.setTimeout(Duration.ofSeconds(10));
            
            // Redis Client 생성
            client = RedisClient.create(uri);
            
            // 단일 Connection 생성
            connection = client.connect();
            connection.setAutoFlushCommands(true);
            
            // Async Commands
            asyncCommands = connection.async();
            
            // 연속 실패 카운터 초기화
            consecutiveFailures.set(0);
            
            // 초기화 완료
            initialized.set(true);
            
            System.err.println("RedisAppender: initialized successfully");
            
            return true;
            
        } catch (Exception e) {
            System.err.println("RedisAppender: initialization failed - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Connection 유효성 확인
     */
    private boolean isConnectionValid() {
        return connection != null && connection.isOpen() && asyncCommands != null;
    }
    
    /**
     * Redis 재연결 시도
     */
    private boolean reconnect() {
        try {
            // 기존 Connection 정리
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    // 무시
                }
                connection = null;
                asyncCommands = null;
            }
            
            // 재연결 대기
            Thread.sleep(RECONNECT_DELAY_MS);
            
            // 새로운 Connection 생성
            connection = client.connect();
            connection.setAutoFlushCommands(true);
            asyncCommands = connection.async();
            
            // 성공 시 실패 카운터 초기화
            consecutiveFailures.set(0);
            
            System.err.println("RedisAppender: reconnected successfully");
            
            return true;
            
        } catch (Exception e) {
            System.err.println("RedisAppender: reconnect failed - " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Queue 초기화 및 누락 로그 warning 생성
     */
    private void clearQueueAndWarn() {
        int droppedCount = QUEUE.size();
        QUEUE.clear();
        
        // Warning 로그 생성
        String warningLog = String.format("[WARN] %d개의 로그가 누락되었습니다. (Redis 연결 실패)", droppedCount);
        
        // 재연결 후 warning 로그 전송 시도
        if (reconnect()) {
            try {
                asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, warningLog);
            } catch (Exception e) {
                // 무시
            }
        }
        
        // 콘솔에도 출력
        System.err.println("RedisAppender: " + warningLog);
    }
    
    // ========================================================================
    // ShutdownHook Management
    // ========================================================================
    
    private void registerShutdownHook() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            
            shutdownHook = new Thread(() -> {
                // 전체를 try-catch로 감싸서 어떤 에러도 밖으로 나가지 않도록
                try {
                    if (!shuttingDown.compareAndSet(false, true)) {
                        return;
                    }
                    
                    // running 플래그 설정
                    try {
                        running.set(false);
                    } catch (Throwable t) {
                        // 무시
                    }
                    
                    // 종료 로그를 큐에 추가
                    try {
                        String shutdownLog = PROCESS_STOPPED + PROCESS_ID + "," + System.currentTimeMillis();
                        QUEUE.offer(shutdownLog);
                    } catch (Throwable t) {
                        // 무시
                    }
                    
                    // 초기화 스레드 중지
                    try {
                        stopInitThreadSafe();
                    } catch (Throwable t) {
                        // 무시
                    }
                    
                    // Worker Thread 종료 대기
                    try {
                        waitForWorkerSafe();
                    } catch (Throwable t) {
                        // 무시
                    }
                    
                    // 남은 로그 전송
                    try {
                        flushQueueSafely();
                    } catch (Throwable t) {
                        // 무시
                    }
                    
                    // Redis 리소스 정리
                    try {
                        cleanupRedisSafely();
                    } catch (Throwable t) {
                        // 무시
                    }
                    
                } catch (NoClassDefFoundError e) {
                    // ClassLoader 언로드 - 조용히 종료
                } catch (Throwable t) {
                    // 모든 에러 무시
                }
            }, "ShutdownHook-RedisAppender");
            
            try {
                Runtime.getRuntime().addShutdownHook(shutdownHook);
                
                // 시작 로그를 큐에 추가
                String startLog = PROCESS_STARTED + PROCESS_ID;
                QUEUE.offer(startLog);
                
            } catch (IllegalStateException e) {
                shutdownHookRegistered.set(false);
            }
        }
    }
    
    private void removeShutdownHook() {
        if (shutdownHookRegistered.get() && shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                shutdownHookRegistered.set(false);
            } catch (IllegalStateException e) {
                // JVM이 이미 종료 중
            } catch (Exception e) {
                // 무시
            }
        }
    }
    
    // ========================================================================
    // Resource Cleanup
    // ========================================================================
    
    private void cleanupRedis() {
        cleanupRedisSafely();
    }
    
    private void cleanupRedisSafely() {
        // ShutdownHook에서 호출 시 ClassLoader가 클래스를 언로드했을 수 있으므로
        // 모든 Redis 관련 작업을 스킵하고 참조만 null로 설정
        try {
            // Connection 참조 제거
            connection = null;
            asyncCommands = null;
            
            // Client 참조 제거
            client = null;
            
            // 초기화 플래그 리셋
            initialized.set(false);
            
        } catch (Throwable t) {
            // 어떤 에러든 무시
        }
    }
    
    // ========================================================================
    // Worker Thread Management
    // ========================================================================
    
    private void startWorkerThread() {
        workerThread = new Thread(this::processQueue, "RedisAppender-Worker");
        workerThread.setDaemon(true);
        workerThread.setPriority(Thread.NORM_PRIORITY - 1);
        workerThread.start();
    }
    
    private void stopWorkerThread() {
        if (workerThread != null && workerThread.isAlive()) {
            try {
                workerThread.join(SHUTDOWN_TIMEOUT_MS);
                if (workerThread.isAlive()) {
                    workerThread.interrupt();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    

    /**
     * Worker Thread 종료 대기 (ShutdownHook용 - 안전)
     */
    private void waitForWorkerSafe() {
        try {
            if (workerThread != null && workerThread.isAlive()) {
                try {
                    workerThread.join(SHUTDOWN_TIMEOUT_MS);
                    if (workerThread.isAlive()) {
                        workerThread.interrupt();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (NoClassDefFoundError e) {
                    // 무시
                }
            }
        } catch (NoClassDefFoundError e) {
            // 무시
        } catch (Throwable t) {
            // 무시
        }
    }
    
    /**
     * Queue 처리 - Redis 초기화를 기다린 후 로그 전송
     */
    private void processQueue() {
        while (running.get() || !QUEUE.isEmpty()) {
            try {
                // Redis 초기화 대기
                if (!initialized.get()) {
                    Thread.sleep(INIT_CHECK_INTERVAL_MS);
                    continue;
                }
                
                String log = QUEUE.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                
                if (log != null) {
                    // Connection 유효성 확인
                    if (!isConnectionValid()) {
                        if (!reconnect()) {
                            QUEUE.offer(log);
                            
                            int failures = consecutiveFailures.incrementAndGet();
                            
                            if (failures >= MAX_RETRY_COUNT) {
                                clearQueueAndWarn();
                            }
                            
                            Thread.sleep(RECONNECT_DELAY_MS);
                            continue;
                        }
                    }
                    
                    // Redis로 전송
                    try {
                        asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log);
                        consecutiveFailures.set(0);
                    } catch (Exception e) {
                        QUEUE.offer(log);
                        
                        int failures = consecutiveFailures.incrementAndGet();
                        
                        if (failures >= MAX_RETRY_COUNT) {
                            clearQueueAndWarn();
                        }
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 무시
            }
        }
    }
    
    // ========================================================================
    // Appender Method
    // ========================================================================
    
    @Override
    protected void append(ILoggingEvent event) {
        // 종료 중이 아니면 항상 Queue에 추가 (initialized 체크 안 함)
        if (layout == null || !running.get() || shuttingDown.get()) {
            return;
        }
        
        try {
            String log = layout.doLayout(event);
            
            // 큐가 가득 찬 경우 오래된 로그 제거
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
    // Flush Operations
    // ========================================================================
    
    private void flushQueue() {
        if (!waitForInitialization()) {
            return;
        }
        
        while (!QUEUE.isEmpty()) {
            try {
                if (!isConnectionValid()) {
                    if (!reconnect()) {
                        int remaining = QUEUE.size();
                        if (remaining > 0) {
                            System.err.println("RedisAppender: flush failed - " + remaining + " logs remaining");
                        }
                        return;
                    }
                }
                
                String log = QUEUE.poll(100, TimeUnit.MILLISECONDS);
                if (log != null) {
                    asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log)
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                // 무시
            }
        }
    }
    
    private void flushQueueSafely() {
        // ShutdownHook에서 호출 시 ClassLoader가 클래스를 언로드했을 수 있음
        try {
            // 초기화 완료 대기 (짧게)
            if (!waitForInitializationQuick()) {
                return;
            }
            
            // Queue 처리
            while (!QUEUE.isEmpty()) {
                try {
                    // Connection 체크도 NoClassDefFoundError 발생 가능
                    if (!isConnectionValidSafe()) {
                        break;
                    }
                    
                    String log = QUEUE.poll(100, TimeUnit.MILLISECONDS);
                    if (log != null) {
                        asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log)
                            .toCompletableFuture()
                            .get(1, TimeUnit.SECONDS);
                    }
                } catch (NoClassDefFoundError e) {
                    // ClassLoader가 클래스 언로드 - 즉시 중단
                    break;
                } catch (Exception e) {
                    // 기타 예외는 무시하고 계속
                }
            }
        } catch (NoClassDefFoundError e) {
            // 최상위 레벨에서도 NoClassDefFoundError 처리
        } catch (Throwable t) {
            // 모든 에러 무시
        }
    }
    
    /**
     * Connection 유효성 확인 (ShutdownHook용 - 안전)
     */
    private boolean isConnectionValidSafe() {
        try {
            return connection != null && connection.isOpen() && asyncCommands != null;
        } catch (NoClassDefFoundError e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * 초기화 완료 대기 (ShutdownHook용 - 짧게)
     */
    private boolean waitForInitializationQuick() {
        int maxWait = 3000;  // 3초만 대기
        int elapsed = 0;
        
        try {
            while (!initialized.get() && elapsed < maxWait) {
                try {
                    Thread.sleep(INIT_CHECK_INTERVAL_MS);
                    elapsed += INIT_CHECK_INTERVAL_MS;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            
            return initialized.get();
        } catch (NoClassDefFoundError e) {
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
    
    /**
     * 초기화 완료 대기 (최대 10초)
     */
    private boolean waitForInitialization() {
        int maxWait = 10000;
        int elapsed = 0;
        
        while (!initialized.get() && elapsed < maxWait) {
            try {
                Thread.sleep(INIT_CHECK_INTERVAL_MS);
                elapsed += INIT_CHECK_INTERVAL_MS;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        
        return initialized.get();
    }
    
    // ========================================================================
    // Public API
    // ========================================================================
    
    public static void flush() {
        if (instance != null && instance.isStarted()) {
            instance.flushQueue();
        }
    }
    
    public static void shutdown() {
        if (instance != null && instance.isStarted()) {
            instance.stop();
        }
    }
    
    public static boolean isRunning() {
        return running.get();
    }

    public static boolean isInitialized() {
        return initialized.get();
    }
    
    public static int getQueueSize() {
        return QUEUE.size();
    }
}