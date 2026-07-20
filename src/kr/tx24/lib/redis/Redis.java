package kr.tx24.lib.redis;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Redis Connection Manager
 *
 * 특징
 * - StatefulRedisConnection 싱글톤 재사용
 * - Lettuce 자동 재연결(Netty Auto-Reconnect) 활용
 * - HAProxy Idle Timeout 방지용 주기적 Heartbeat
 */
public final class Redis {

    private static final Logger logger = LoggerFactory.getLogger(Redis.class);

    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    private static volatile StatefulRedisConnection<String, Object> connection;

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);
    private static final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);

    private static volatile ScheduledFuture<?> heartbeatFuture;
    private static String redisUri;

    private Redis() {
        throw new UnsupportedOperationException();
    }

    /**
     * Redis 초기화 (최초 1회 실행)
     */
    public static synchronized void init() {
        if (initialized.get() && connection != null && connection.isOpen()) {
            return;
        }

        if (shutdown.get()) {
            throw new IllegalStateException("Redis already shutdown");
        }

        try {
            SystemUtils.init();
            redisUri = SystemUtils.getRedisSystemUri();

            if (SystemUtils.REDIS_INITIAL.equals(redisUri)) {
                throw new IllegalStateException("Redis URI not configured");
            }

            createClient();
            createConnection();
            initialized.set(true);
            startHeartbeat();

            logger.info("Redis initialized : {}", redisUri);

        } catch (Exception e) {
            logger.error("Redis initialization failed", e);
            throw e;
        }
    }

    private static void createClient() {
        if (client != null) return;

        if (clientResources == null) {
            clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(2)
                    .computationThreadPoolSize(2)
                    .build();
        }

        RedisURI uri = RedisURI.create(redisUri);
        uri.setTimeout(Duration.ofSeconds(3));

        client = RedisClient.create(clientResources, uri);

        client.setOptions(ClientOptions.builder()
                .autoReconnect(true) // Netty 레벨 자동 재연결 활성화
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build()
        );
    }

    private static synchronized void createConnection() {
        if (connection != null && connection.isOpen()) {
            return;
        }

        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
        }

        // 연결 시도 (실패 시 예외 던짐)
        connection = client.connect(new TypedRedisCodec());

        // PING 테스트
        String pong = connection.sync().ping();
        if (!"PONG".equals(pong)) {
            throw new IllegalStateException("Redis initial ping failed");
        }
    }

    /**
     * 커넥션 반환 (없으면 초기화)
     */
    public static StatefulRedisConnection<String, Object> getConnection() {
        if (shutdown.get()) {
            throw new IllegalStateException("Redis shutdown");
        }

        if (!initialized.get() || connection == null) {
            init();
        }

        return connection;
    }

    public static RedisCommands<String, Object> sync() {
        return getConnection().sync();
    }

    public static RedisAsyncCommands<String, Object> async() {
        return getConnection().async();
    }

    /**
     * HAProxy Idle Timeout 대응용 Heartbeat
     */
    private static void startHeartbeat() {
        if (!heartbeatStarted.compareAndSet(false, true)) {
            return;
        }

        heartbeatFuture = AsyncExecutor.scheduleAtFixedRate(
                Redis::heartbeat,
                30,
                30,
                TimeUnit.SECONDS);
    }

    private static void heartbeat() {
        if (shutdown.get() || !initialized.get()) {
            return;
        }

        try {
            StatefulRedisConnection<String, Object> conn = connection;
            if (conn != null && conn.isOpen()) {
                // 단순 세션 유지용 PING (재연결은 Lettuce가 알아서 처리함)
                conn.sync().ping();
            } else {
                // 커넥션 자체가 null이거나 완전히 닫힌 경우에만 재생성 시도
                synchronized (Redis.class) {
                    createConnection();
                }
            }
        } catch (Exception e) {
            logger.warn("Redis heartbeat ping failed (Lettuce will auto-reconnect): {}", e.getMessage());
        }
    }

    public static boolean isConnected() {
        return connection != null && connection.isOpen();
    }

    public static boolean ping() {
        try {
            return "PONG".equals(sync().ping());
        } catch (Exception e) {
            return false;
        }
    }

    public static synchronized void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }

        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
            heartbeatFuture = null;
        }

        heartbeatStarted.set(false);

        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
            connection = null;
        }

        if (client != null) {
            try { client.shutdown(100, 1000, TimeUnit.MILLISECONDS); } catch (Exception ignored) {}
            client = null;
        }

        if (clientResources != null) {
            try { clientResources.shutdown().get(); } catch (Exception ignored) {}
            clientResources = null;
        }

        initialized.set(false);
        logger.info("Redis shutdown completed");
    }

    public static String getInfo() {
        if (!isConnected()) {
            return "Redis: Not connected";
        }

        try {
            String info = sync().info("server");
            String[] lines = info.split("\r?\n");
            StringBuilder sb = new StringBuilder();

            for (String line : lines) {
                if (line.startsWith("redis_version:") ||
                        line.startsWith("redis_mode:") ||
                        line.startsWith("os:") ||
                        line.startsWith("uptime_in_seconds:")) {
                    sb.append(line).append("\n");
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "Redis: Error getting info - " + e.getMessage();
        }
    }

    public static RedisClient getClient() { return client; }
    public static String getUriString() { return redisUri; }
}