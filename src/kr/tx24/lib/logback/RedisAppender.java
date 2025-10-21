package kr.tx24.lib.logback;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import kr.tx24.lib.lang.SystemUtils;

/**
 * - AtomicBoolean을 사용한 Thread-Safe 상태 관리
 * - 향상된 Shutdown Hook 처리
 * - Connection Pool 대신 Thread-Safe한 단일 연결 사용
 * - 비동기 명령어 사용으로 성능 최적화
 * - 예외 처리 강화
 */
public class RedisAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

	private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    private static volatile StatefulRedisConnection<String, String> connection;
    private static volatile RedisAsyncCommands<String, String> asyncCommands;
    
 // 설정
    private static final int QUEUE_CAPACITY = 10000;
    private static final int SHUTDOWN_TIMEOUT_MS = 3000;
    private static final int POLL_TIMEOUT_MS = 1000;
    private static final int FLUSH_POLL_TIMEOUT_MS = 100;
    
    // 로그 큐 및 상태 관리
    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    
    // Worker Thread
    private static volatile Thread workerThread;
    private static volatile RedisAppender instance;
    
    // Layout
    private Layout<ILoggingEvent> layout;
    
    

    @Override
    public void start() {
        if (instance == null) {
            instance = this;
        }
        
        // 이미 초기화되어 있으면 시작만
        if (initialized.compareAndSet(false, true)) {
            initializeRedisClient();
        }
        
        // Worker Thread 시작
        if (running.compareAndSet(false, true)) {
            startWorkerThread();
        }
        
        super.start();
    }
    
    
    private void initializeRedisClient() {
        try {
            SystemUtils.init();
            String redisLogUri = SystemUtils.getRedisLogUri();
            
            // ClientResources 설정 (성능 최적화)
            int cpuCount = Runtime.getRuntime().availableProcessors();
            clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(Math.max(2, cpuCount / 2))
                    .computationThreadPoolSize(Math.max(2, cpuCount / 2))
                    .build();
            
            // RedisURI 설정
            RedisURI uri = RedisURI.create(redisLogUri);
            uri.setTimeout(Duration.ofSeconds(10));
            
            // Redis Client 생성
            client = RedisClient.create(clientResources, uri);
            connection = client.connect();
            // Async 명령어 자동 flush 설정
            connection.setAutoFlushCommands(true);
            
            asyncCommands = connection.async();
            
            
            System.out.println("RedisAppender: initialized");
            

            
        } catch (Exception e) {
            addError("RedisAppender initialization failed", e);
            initialized.set(false);
        }
    }
    
    /**
     * Worker Thread 시작
     */
    private void startWorkerThread() {
        workerThread = new Thread(this::processQueue, "RedisAppender-Worker");
        workerThread.setDaemon(true);
        workerThread.setPriority(Thread.NORM_PRIORITY - 1);
        workerThread.start();
    }


    
    
    @Override
    protected void append(ILoggingEvent event) {
        if (layout == null || !running.get() || !initialized.get()) {
            return;
        }
        
        try {
            String log = layout.doLayout(event);
            
            // 큐가 가득 찬 경우 오래된 로그 제거
            if (!QUEUE.offer(log)) {
                QUEUE.poll(); // 가장 오래된 로그 제거
                QUEUE.offer(log); // 새 로그 추가
            }
            
        } catch (Exception e) {
            addError("RedisAppender: append failed", e);
        }
    }
    
    
    /**
     * 큐의 로그를 Redis로 전송하는 Worker 프로세스
     */
    private void processQueue() {
        
        while (running.get() || !QUEUE.isEmpty()) {
            try {
                String log = QUEUE.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                
                if (log != null && asyncCommands != null) {
                    // 비동기로 Redis에 전송
                    asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log)
                        .exceptionally(throwable -> {
                            addError("RedisAppender: failed to send log to Redis", throwable);
                            return null;
                        });
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                addError("RedisAppender: error processing queue", e);
            }
        }
        
        addInfo("RedisAppender: queue processing stopped");
    }
    
    
    
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return; // 이미 중지됨
        }
        
        // Worker Thread 종료 대기
        if (workerThread != null && workerThread.isAlive()) {
            try {
                workerThread.join(SHUTDOWN_TIMEOUT_MS);
                if (workerThread.isAlive()) {
                    workerThread.interrupt();
                    //addWarn("RedisAppender: worker thread interrupted after timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                //addWarn("RedisAppender: interrupted while stopping worker thread");
            }
        }
        
        // 남은 로그 플러시
        flush();
        
        // Redis 리소스 정리
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception e) {
                addError("RedisAppender: error closing connection", e);
            }
        }
        
        if (client != null) {
            try {
                client.shutdown(100, 1000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                addError("RedisAppender: error shutting down client", e);
            }
        }
        
        if (clientResources != null) {
            try {
                clientResources.shutdown(100, 1000, TimeUnit.MILLISECONDS).get();
            } catch (Exception e) {
                addError("RedisAppender: error shutting down client resources", e);
            }
        }
        
        initialized.set(false);
        
        super.stop();
    }
    


    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    /**
     * 즉시 flush: 큐에 남은 로그를 Redis로 전송
     */
    public static void flush() {
        if (!initialized.get() || asyncCommands == null) {
            return;
        }
        
        int flushedCount = 0;
        
        while (!QUEUE.isEmpty()) {
            try {
                String log = QUEUE.poll(FLUSH_POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                
                if (log != null) {
                    // 동기 방식으로 확실하게 전송
                    asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log)
                        .toCompletableFuture()
                        .get(1, TimeUnit.SECONDS);
                    flushedCount++;
                }
                
            } catch (Exception e) {
                System.err.println("RedisAppender: flush error - " + e.getMessage());
            }
        }
        
        if (flushedCount > 0) {
            System.out.println("RedisAppender: flushed " + flushedCount + " logs");
        }
    }

    /**
     * 안전한 종료: ShutdownHook에서 호출 가능
     */
    public static void shutdown() {
        try {
            if (instance != null) {
                // 실행 중지
                running.set(false);
                
                // 남은 로그 강제 전송
                flush();
                
                // 인스턴스 정리
                if (instance.isStarted()) {
                    instance.stop();
                }
            }
        } catch (Exception e) {
            System.err.println("RedisAppender: shutdown error - " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Appender 실행 상태 확인
     */
    public static boolean isRunning() {
        return running.get();
    }

    /**
     * 초기화 상태 확인
     */
    public static boolean isInitialized() {
        return initialized.get();
    }
    

}
