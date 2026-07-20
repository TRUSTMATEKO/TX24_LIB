package kr.tx24.lib.logback;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Redis Appender for Logback (Optimized)
 *
 * 특징:
 * - Lettuce Auto-Reconnect 활용 (수동 Ping/재연결 로직 제거)
 * - Batching 전송 (drainTo) 지원으로 대량 로그 처리 성능 극대화
 * - Non-blocking 비동기 큐 기반
 * - Graceful Shutdown 시 큐 데이터 Flush 보장
 */
public class RedisAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final int QUEUE_CAPACITY = 10000;
    private static final int BATCH_SIZE = 200; // 한 번에 Redis로 묶어 보낼 최대 로그 수
    private static final int POLL_TIMEOUT_MS = 500;
    private static final int SHUTDOWN_TIMEOUT_MS = 3000;

    private static volatile ClientResources clientResources;
    private static volatile RedisClient client;
    private static volatile StatefulRedisConnection<String, String> connection;
    private static volatile RedisAsyncCommands<String, String> asyncCommands;

    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final AtomicBoolean initialized = new AtomicBoolean(false);

    private static Thread workerThread;
    private static RedisAppender instance;

    private Layout<ILoggingEvent> layout;

    // ========================================================================
    // Lifecycle Methods
    // ========================================================================

    @Override
    public void start() {
        if (instance == null) {
            instance = this;
        }

        if (!initialized.get()) {
            synchronized (RedisAppender.class) {
                if (!initialized.get()) {
                    if (initializeRedis()) {
                        initialized.set(true);
                    } else {
                        System.err.println("RedisAppender: Initialization failed. Appender will not start.");
                        super.stop();
                        return;
                    }
                }
            }
        }

        if (running.compareAndSet(false, true)) {
            startWorkerThread();
        }

        super.start();
    }

    @Override
    public void stop() {
        performShutdown();
        super.stop();
    }

    // ========================================================================
    // Redis 초기화
    // ========================================================================

    private boolean initializeRedis() {
        String redisUri = "";
        try {
            SystemUtils.init();
            redisUri = SystemUtils.getRedisLogUri();

            if (redisUri == null || redisUri.trim().isEmpty() || SystemUtils.REDIS_INITIAL.equals(redisUri)) {
                System.err.println("RedisAppender: Redis Log URI is not properly configured");
                return false;
            }

            int cpu = Runtime.getRuntime().availableProcessors();
            clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(Math.max(1, cpu / 2))
                    .computationThreadPoolSize(Math.max(1, cpu / 2))
                    .build();

            RedisURI uri = RedisURI.create(redisUri);
            uri.setTimeout(Duration.ofSeconds(5));

            client = RedisClient.create(clientResources, uri);

            // Lettuce 자동 재연결 옵션
            client.setOptions(ClientOptions.builder()
                    .autoReconnect(true)
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .build());

            connection = client.connect();
            asyncCommands = connection.async();

            // 연결 테스트
            connection.sync().ping();
            System.err.println("RedisAppender: Successfully connected to RedisLog (" + redisUri + ")");
            return true;

        } catch (Exception e) {
            System.err.println("RedisAppender: Initialization failed for URI [" + redisUri + "] - " + e.getMessage());
            cleanupRedis();
            return false;
        }
    }

    private void cleanupRedis() {
        try {
            if (connection != null) {
                try { connection.close(); } catch (Exception ignored) {}
                connection = null;
            }
            if (client != null) {
                try { client.shutdown(100, 500, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
                client = null;
            }
            if (clientResources != null) {
                try { clientResources.shutdown().get(); } catch (Exception ignored) {}
                clientResources = null;
            }
        } catch (Exception e) {
            // ignore
        }
    }

    // ========================================================================
    // Worker Thread & Queue Processing
    // ========================================================================

    private void startWorkerThread() {
        workerThread = new Thread(this::processQueue, "RedisAppender-Worker");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * Queue 배치(Batch) 처리 메인 루프
     */
    private void processQueue() {
        List<String> batch = new ArrayList<>(BATCH_SIZE);

        // running 상태이거나, 종료 요청 후에도 Queue에 데이터가 남아있으면 계속 처리
        while (running.get() || !QUEUE.isEmpty()) {
            try {
                // 1. 단건 먼저 가져오기 (타임아웃 적용)
                String firstLog = QUEUE.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                if (firstLog != null) {
                    batch.add(firstLog);

                    // 2. 큐에 쌓인 나머지 로그들을 최대 BATCH_SIZE만큼 한 번에 꺼냄
                    QUEUE.drainTo(batch, BATCH_SIZE - 1);

                    // 3. Redis 배치 전송
                    sendBatch(batch);

                    batch.clear();
                }
            } catch (InterruptedException e) {
                // Shutdown 시 대기 해제
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("RedisAppender: Error processing queue - " + e.getMessage());
                batch.clear();
            }
        }

        // 종료 직전 남아있는 큐 최종 처리 (Flush)
        if (!QUEUE.isEmpty()) {
            batch.clear();
            QUEUE.drainTo(batch);
            if (!batch.isEmpty()) {
                sendBatch(batch);
            }
        }
    }

    /**
     * Redis로 배치(Multiple Arguments) 전송
     */
    private void sendBatch(List<String> batch) {
        if (batch == null || batch.isEmpty() || asyncCommands == null) {
            return;
        }

        try {
            String[] logs = batch.toArray(new String[0]);

            // RPUSH key val1 val2 val3 ... (단 1회의 커맨드로 배치 처리)
            asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, logs)
                    .toCompletableFuture()
                    .get(2, TimeUnit.SECONDS);

        } catch (Exception e) {
            // Redis 연결 차단 등의 사유로 실패 시
            // 애플리케이션 로그 어펜더 특성상 무한 재시도로 인한 OOM을 방지하기 위해 드랍 처리하거나 필요시 제어
            System.err.println("RedisAppender: Failed to send batch (" + batch.size() + " logs) - " + e.getMessage());
        }
    }

    // ========================================================================
    // Shutdown 처리
    // ========================================================================

    private void performShutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        // Worker Thread가 remaining queue를 처리하고 스스로 종료할 때까지 대기
        if (workerThread != null && workerThread.isAlive()) {
            try {
                workerThread.join(SHUTDOWN_TIMEOUT_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Redis 연결 종료
        cleanupRedis();
        initialized.set(false);

        System.err.println("RedisAppender: Shutdown completed.");
    }

    // ========================================================================
    // Logback Appender Core
    // ========================================================================

    @Override
    protected void append(ILoggingEvent event) {
        if (layout == null || !running.get()) {
            return;
        }

        try {
            String log = layout.doLayout(event);

            // Queue 가득 찼을 때 가장 오래된 로그를 버리고(Drop) 신규 로그 삽입 (Ring-Buffer 형태)
            if (!QUEUE.offer(log)) {
                QUEUE.poll();
                QUEUE.offer(log);
            }
        } catch (Exception e) {
            // Logback Appender 내부 예외는 무시 (애플리케이션 영향 최소화)
        }
    }

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    // ========================================================================
    // Public API
    // ========================================================================

    public static void shutdown() {
        if (instance != null) {
            instance.performShutdown();
        }
    }

    public static boolean isRunning() {
        return running.get();
    }

    public static int getQueueSize() {
        return QUEUE.size();
    }

    public static String getStatus() {
        return String.format("RedisAppender{running=%s, initialized=%s, queueSize=%d}",
                running.get(), initialized.get(), QUEUE.size());
    }
}