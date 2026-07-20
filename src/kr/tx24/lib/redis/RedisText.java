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
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Redis Connection Manager (String Key - String Value)
 *
 * 특징
 * - StatefulRedisConnection<String, String> 싱글톤 재사용
 * - Lettuce 자동 재연결(Netty Auto-Reconnect) 활용
 * - HAProxy Idle Timeout 대응용 주기적 Heartbeat
 */
public final class RedisText {

    private static final Logger logger = LoggerFactory.getLogger(RedisText.class);

    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    private static volatile StatefulRedisConnection<String, String> connection;

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);
    private static final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);

    private static volatile ScheduledFuture<?> heartbeatFuture;
    private static String redisUri;

    private RedisText() {
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

            logger.info("RedisText initialized : {}", redisUri);

        } catch (Exception e) {
            logger.error("RedisText initialization failed", e);
            throw e;
        }
    }

    /**
     * RedisClient 생성
     */
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

    /**
     * Connection 생성
     */
    private static synchronized void createConnection() {
        if (connection != null && connection.isOpen()) {
            return;
        }

        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
        }

        // StringCodec.UTF8 사용
        connection = client.connect(StringCodec.UTF8);

        // PING 테스트
        String pong = connection.sync().ping();
        if (!"PONG".equals(pong)) {
            throw new IllegalStateException("RedisText initial ping failed");
        }
    }

    /**
     * Connection 반환 (없으면 초기화)
     */
    public static StatefulRedisConnection<String, String> getConnection() {
        if (shutdown.get()) {
            throw new IllegalStateException("Redis shutdown");
        }

        if (!initialized.get() || connection == null) {
            init();
        }

        return connection;
    }

    public static RedisClient getClient() {
        return client;
    }

    public static String getUriString() {
        return redisUri;
    }

    /**
     * Sync command
     */
    public static RedisCommands<String, String> sync() {
        return getConnection().sync();
    }

    /**
     * Async command
     */
    public static RedisAsyncCommands<String, String> async() {
        return getConnection().async();
    }

    // ==================== Connection 상태 ====================

    /**
     * HAProxy Idle Timeout 대응용 Heartbeat 시작
     */
    private static void startHeartbeat() {
        if (!heartbeatStarted.compareAndSet(false, true)) {
            return;
        }

        heartbeatFuture = AsyncExecutor.scheduleAtFixedRate(
                RedisText::heartbeat,
                30,
                30,
                TimeUnit.SECONDS);
    }

    /**
     * Redis heartbeat
     */
    private static void heartbeat() {
        if (shutdown.get() || !initialized.get()) {
            return;
        }

        try {
            StatefulRedisConnection<String, String> conn = connection;
            if (conn != null && conn.isOpen()) {
                // 단순 세션 유지용 PING (재연결은 Lettuce가 알아서 처리함)
                conn.sync().ping();
            } else {
                // 커넥션 자체가 null이거나 완전히 닫힌 경우에만 재생성 시도
                synchronized (RedisText.class) {
                    createConnection();
                }
            }
        } catch (Exception e) {
            logger.warn("RedisText heartbeat ping failed (Lettuce will auto-reconnect): {}", e.getMessage());
        }
    }

    /**
     * 연결 상태 확인
     */
    public static boolean isConnected() {
        return connection != null && connection.isOpen();
    }

    /**
     * Ping
     */
    public static boolean ping() {
        try {
            return "PONG".equals(sync().ping());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Redis 종료
     */
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

        logger.info("RedisText shutdown completed");
    }

    // ==================== 유틸리티 ====================

    /**
     * Redis 정보 조회
     *
     * @return Redis 서버 정보 요약
     */
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
}