package kr.tx24.lib.redis;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Redis Connection Manager
 *
 * <pre>
 * RedisClient
 *      |
 * StatefulRedisConnection
 *      |
 * Heartbeat Monitor
 *
 * 특징
 * - Connection Pool 사용하지 않음
 * - Lettuce Connection 공유
 * - Auto reconnect 지원
 * - HAProxy idle timeout 대응
 * - 장애 발생 시 자동 복구
 * </pre>
 */
public final class RedisText {

    private static final Logger logger = LoggerFactory.getLogger(RedisText.class);

    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    private static volatile StatefulRedisConnection<String, String> connection;

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);
    private static final AtomicBoolean reconnecting = new AtomicBoolean(false);
    private static final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);

    private static volatile ScheduledFuture<?> heartbeatFuture;
    private static String redisUri;

    private RedisText() {
        throw new UnsupportedOperationException();
    }

    /**
     * Redis 초기화
     */
    private static synchronized void init() {

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
                throw new IllegalStateException(
                        "Redis URI not configured");
            }

            createClient();
            createConnection();
            initialized.set(true);
            startHeartbeat();

            logger.info( "Redis initialized : {}",redisUri);

        } catch (Exception e) {
            logger.error("Redis initialization failed", e);

        }
    }

    /**
     * RedisClient 생성
     */
    private static void createClient() {

        if (clientResources == null) {

            clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(2)
                    .computationThreadPoolSize(2)
                    .build();
            /* 리소스 소비가 과함.
            int cpu = Runtime.getRuntime().availableProcessors();

            clientResources = DefaultClientResources.builder()
                            .ioThreadPoolSize( Math.max(2, cpu))
                            .computationThreadPoolSize( Math.max(2, cpu))
                            .build();*/
        }

        RedisURI uri = RedisURI.create(redisUri);
        uri.setTimeout(Duration.ofSeconds(3));

        client = RedisClient.create( clientResources,uri);

        client.setOptions(ClientOptions.builder()
                        .autoReconnect(true)
                        .disconnectedBehavior(
                                ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .build()
        );
    }

    /**
     * Connection 생성
     */
    private static synchronized void createConnection() {

        try {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception ignore) {
                }
            }

            connection = client.connect(StringCodec.UTF8);

            String pong = connection.sync().ping();

            if (!"PONG".equals(pong)) {
                throw new IllegalStateException("Redis ping failed");
            }

        } catch (Exception e) {
            logger.error("Redis connection create failed",e);
            throw e;
        }
    }

    /**
     * Connection 반환
     */
    public static StatefulRedisConnection<String, String> getConnection() {
        if (shutdown.get()) {
            throw new IllegalStateException(
                    "Redis shutdown");
        }

        if (connection == null || !connection.isOpen()) {
            reconnect();
        }

        return connection;
    }

    public static RedisClient getClient(){
        return client;
    }

    public static String getUriString(){
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
    public static RedisAsyncCommands<String, String>async() {
        return getConnection().async();
    }

    // ==================== Connection 상태 ====================

    /**
     * Heartbeat 시작
     */
    private static void startHeartbeat() {

        if (!heartbeatStarted.compareAndSet(false, true)) {
            return;
        }

        heartbeatFuture =
                AsyncExecutor.scheduleAtFixedRate(
                        RedisText::heartbeat,
                        30,
                        30,
                        TimeUnit.SECONDS);

        //logger.info("Redis heartbeat started");
    }

    /**
     * Redis heartbeat
     */
    private static void heartbeat() {

        if (shutdown.get()) {
            return;
        }

        try {
            if(connection == null|| !connection.isOpen()) {
                reconnect();
                return;
            }

            String pong = connection.sync().ping();

            if (!"PONG".equals(pong)) {
                logger.info("Redis heartbeat failed");
                reconnect();
            }

        } catch (Exception e) {
            logger.info("Redis heartbeat error",e);
            reconnect();
        }
    }

    /**
     * Redis 재연결
     */
    private static void reconnect() {

        if (shutdown.get()) {
            return;
        }

        if (!reconnecting.compareAndSet(false, true)) {
            return;
        }

        try {
            logger.info( "Redis reconnect start");
            createConnection();
            logger.info("Redis reconnect success");
        } catch (Exception e) {
            logger.error("Redis reconnect failed",e);
        } finally {
            reconnecting.set(false);
        }
    }

    /**
     * 연결 상태 확인
     */
    public static boolean isConnected() {
        return connection != null&& connection.isOpen();
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
            try {
                connection.close();
            } catch (Exception ignore) {
            }
            connection = null;
        }

        if (client != null) {
            try {
                client.shutdown(
                        100,
                        1000,
                        TimeUnit.MILLISECONDS);

            } catch (Exception ignore) {
            }
            client = null;
        }

        if (clientResources != null) {
            try {
                clientResources.shutdown().get();
            } catch (Exception ignore) {
            }
            clientResources = null;
        }

        initialized.set(false);

        logger.info("Redis shutdown");
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