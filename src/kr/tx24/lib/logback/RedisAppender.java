package kr.tx24.lib.logback;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Advanced Redis Appender
 * - Async Queue + Connection reuse
 * - Multi-thread safe
 * - ShutdownHook 친화적 개선
 */
public class RedisAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static RedisClient client = null;
    private static StatefulRedisConnection<String, String> connection = null;
    private static RedisAsyncCommands<String, String> asyncCommands = null;

    private static final BlockingQueue<String> QUEUE = new LinkedBlockingQueue<>(10000);

    private static volatile boolean running = false;
    private static Thread workerThread;

    private static RedisAppender instance;

    private Layout<ILoggingEvent> layout;

    @Override
    public void start() {
        if (instance == null) instance = this;

        if (client == null) {
            try {
                SystemUtils.init();
                client = RedisClient.create(SystemUtils.getRedisLogUri());
                connection = client.connect();
                asyncCommands = connection.async();
                System.out.println("RedisAppender: initialized");
            } catch (Exception e) {
                addError("RedisAppender init failed", e);
                return;
            }
        }

        running = true;
        workerThread = new Thread(RedisAppender::processQueue, "RedisAppender-Worker");
        workerThread.setDaemon(true);
        workerThread.start();

        super.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (layout != null && running) {
            try {
                String log = layout.doLayout(event);
                QUEUE.offer(log);
            } catch (Exception e) {
                addError("RedisAppender append fail", e);
            }
        }
    }

    private static void processQueue() {
        while (running || !QUEUE.isEmpty()) {
            try {
                String log = QUEUE.poll(1, TimeUnit.SECONDS);
                if (log != null && asyncCommands != null) {
                    asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void stop() {
        running = false;
        if (workerThread != null) {
            try {
                workerThread.join(2000); // 최대 2초 기다리고 종료
            } catch (InterruptedException ignored) {
            }
        }

        flush(); // 종료 전에 남은 로그 처리

        if (connection != null) connection.close();
        if (client != null) client.shutdown();

        super.stop();
    }

    public void setLayout(Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }

    /** 즉시 flush: 큐에 남은 로그를 Redis로 전송 */
    public static void flush() {
        while (!QUEUE.isEmpty()) {
            try {
                String log = QUEUE.poll(100, TimeUnit.MILLISECONDS);
                if (log != null && asyncCommands != null) {
                    asyncCommands.rpush(SystemUtils.REDIS_STORAGE_LOG, log);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** 안전한 종료: ShutdownHook에서 호출 가능 */
    public static void shutdown() {
        try {
            flush(); // 남은 로그 강제 전송
            if (instance != null) instance.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
