package kr.tx24.lib.redis;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * Redis Pub/Sub Manager
 *
 * 특징
 * - Pub/Sub 전용 StatefulRedisPubSubConnection 사용
 * - Lettuce Auto Reconnect 활용 (재연결 시 자동 Re-subscribe 지원)
 * - HAProxy Idle Timeout 대응용 Heartbeat
 * - 단일 메시지 Publish 및 Subscriber 모듈화
 */
public final class RedisPubSub {

    private static final Logger logger = LoggerFactory.getLogger(RedisPubSub.class);

    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    private static volatile StatefulRedisPubSubConnection<String, String> connection;

    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean shutdown = new AtomicBoolean(false);
    private static final AtomicBoolean heartbeatStarted = new AtomicBoolean(false);

    private static volatile ScheduledFuture<?> heartbeatFuture;
    private static String redisUri;

    private static final JacksonUtils jacksonUtils = new JacksonUtils();

    // 활성 Subscriber 관리 (채널/패턴별)
    private static final Map<String, Subscriber> activeSubscribers = new ConcurrentHashMap<>();

    private RedisPubSub() {
        throw new UnsupportedOperationException();
    }

    /**
     * Redis Pub/Sub 초기화 (최초 1회 실행)
     */
    public static synchronized void init() {
        if (initialized.get() && connection != null && connection.isOpen()) {
            return;
        }

        if (shutdown.get()) {
            throw new IllegalStateException("RedisPubSub already shutdown");
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

            logger.info("RedisPubSub initialized : {}", redisUri);

        } catch (Exception e) {
            logger.error("RedisPubSub initialization failed", e);
            throw e;
        }
    }

    /**
     * RedisClient 생성
     */
    private static void createClient() {
        if (client != null) return;

        if (clientResources == null) {
            int cpu = Runtime.getRuntime().availableProcessors();
            clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(Math.max(2, cpu))
                    .computationThreadPoolSize(Math.max(2, cpu))
                    .build();
        }

        RedisURI uri = RedisURI.create(redisUri);
        uri.setTimeout(Duration.ofSeconds(10));

        client = RedisClient.create(clientResources, uri);

        client.setOptions(ClientOptions.builder()
                .autoReconnect(true) // Netty 자동 재연결 (Auto-Subscribe 연동)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .build()
        );
    }

    /**
     * Connection 생성 (Publish 및 관리용 메인 커넥션)
     */
    private static synchronized void createConnection() {
        if (connection != null && connection.isOpen()) {
            return;
        }

        if (connection != null) {
            try { connection.close(); } catch (Exception ignored) {}
        }

        connection = client.connectPubSub(StringCodec.UTF8);

        String pong = connection.sync().ping();
        if (!"PONG".equals(pong)) {
            throw new IllegalStateException("RedisPubSub initial ping failed");
        }
    }

    /**
     * Connection 반환 (없으면 초기화)
     */
    public static StatefulRedisPubSubConnection<String, String> getConnection() {
        if (shutdown.get()) {
            throw new IllegalStateException("RedisPubSub shutdown");
        }

        if (!initialized.get() || connection == null) {
            init();
        }

        return connection;
    }

    /**
     * Sync command (Publish용)
     */
    public static RedisPubSubCommands<String, String> sync() {
        return getConnection().sync();
    }

    /**
     * Async command (Publish용)
     */
    public static RedisPubSubAsyncCommands<String, String> async() {
        return getConnection().async();
    }

    // ==================== Publish API ====================

    /**
     * 메시지 발행 (String)
     */
    public static Long publish(String channel, String message) {
        try {
            Long receivers = sync().publish(channel, message);
            logger.debug("Published to {}: {} (received by {})", channel, message, receivers);
            return receivers;
        } catch (Exception e) {
            logger.error("Failed to publish message to channel: {}", channel, e);
            return 0L;
        }
    }

    /**
     * 메시지 발행 (JSON)
     */
    public static <T> Long publishJson(String channel, T message) {
        try {
            String json = jacksonUtils.toJson(message);
            return publish(channel, json);
        } catch (Exception e) {
            logger.error("Failed to publish JSON message to channel: {}", channel, e);
            return 0L;
        }
    }

    // ==================== Subscribe API ====================

    /**
     * 채널 구독 시작
     */
    public static Subscriber subscribe(String channel, Consumer<String> messageHandler) {
        Subscriber subscriber = new Subscriber();
        subscriber.subscribe(channel, messageHandler);
        activeSubscribers.put(channel, subscriber);
        return subscriber;
    }

    /**
     * 패턴 구독 시작 (예: "news:*")
     */
    public static Subscriber psubscribe(String pattern, Consumer<String> messageHandler) {
        Subscriber subscriber = new Subscriber();
        subscriber.psubscribe(pattern, messageHandler);
        activeSubscribers.put(pattern, subscriber);
        return subscriber;
    }

    /**
     * JSON 메시지 구독
     */
    public static <T> Subscriber subscribeJson(String channel, Class<T> messageType, Consumer<T> messageHandler) {
        return subscribe(channel, json -> {
            try {
                T obj = jacksonUtils.fromJson(json, messageType);
                messageHandler.accept(obj);
            } catch (Exception e) {
                logger.error("Failed to deserialize JSON message from channel: {}", channel, e);
            }
        });
    }

    /**
     * Subscriber Wrapper (개별 전용 커넥션 관리)
     */
    public static class Subscriber implements AutoCloseable {

        private final StatefulRedisPubSubConnection<String, String> subConnection;
        private final RedisPubSubCommands<String, String> subCommands;
        private volatile boolean closed = false;
        private String subscribedChannel;
        private String subscribedPattern;

        private Subscriber() {
            if (!initialized.get()) {
                init();
            }
            this.subConnection = client.connectPubSub(StringCodec.UTF8);
            this.subCommands = subConnection.sync();
        }

        public void subscribe(String channel, Consumer<String> messageHandler) {
            checkClosed();
            subConnection.addListener(new RedisPubSubAdapter<>() {
                @Override
                public void message(String ch, String message) {
                    if (ch.equals(channel)) {
                        try {
                            messageHandler.accept(message);
                        } catch (Exception e) {
                            logger.error("Error in message handler for channel: {}", channel, e);
                        }
                    }
                }
            });

            subCommands.subscribe(channel);
            this.subscribedChannel = channel;
            logger.info("Subscribed to channel: {}", channel);
        }

        public void psubscribe(String pattern, Consumer<String> messageHandler) {
            checkClosed();
            subConnection.addListener(new RedisPubSubAdapter<>() {
                @Override
                public void message(String pat, String ch, String message) {
                    if (pat.equals(pattern)) {
                        try {
                            messageHandler.accept(message);
                        } catch (Exception e) {
                            logger.error("Error in message handler for pattern: {}", pattern, e);
                        }
                    }
                }
            });

            subCommands.psubscribe(pattern);
            this.subscribedPattern = pattern;
            logger.info("Pattern subscribed: {}", pattern);
        }

        public void unsubscribe() {
            if (closed) return;

            try {
                if (subscribedChannel != null) {
                    subCommands.unsubscribe(subscribedChannel);
                    activeSubscribers.remove(subscribedChannel);
                    logger.info("Unsubscribed from channel: {}", subscribedChannel);
                }
                if (subscribedPattern != null) {
                    subCommands.punsubscribe(subscribedPattern);
                    activeSubscribers.remove(subscribedPattern);
                    logger.info("Pattern unsubscribed: {}", subscribedPattern);
                }
            } catch (Exception e) {
                logger.error("Error during unsubscribe", e);
            }
        }

        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Subscriber already closed");
            }
            if (shutdown.get()) {
                throw new IllegalStateException("RedisPubSub is shutting down");
            }
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;

            try {
                unsubscribe();
                if (subConnection != null && subConnection.isOpen()) {
                    subConnection.close();
                }
            } catch (Exception e) {
                logger.warn("Error closing subscriber connection", e);
            }
        }
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
                RedisPubSub::heartbeat,
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
            StatefulRedisPubSubConnection<String, String> conn = connection;
            if (conn != null && conn.isOpen()) {
                conn.sync().ping();
            } else {
                synchronized (RedisPubSub.class) {
                    createConnection();
                }
            }
        } catch (Exception e) {
            logger.warn("RedisPubSub heartbeat ping failed (Lettuce will auto-reconnect): {}", e.getMessage());
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
     * RedisPubSub 종료
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

        // 모든 활성 구독자 커넥션 정리
        if (!activeSubscribers.isEmpty()) {
            activeSubscribers.values().forEach(subscriber -> {
                try {
                    subscriber.close();
                } catch (Exception ignore) {
                }
            });
            activeSubscribers.clear();
        }

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

        logger.info("RedisPubSub shutdown completed");
    }

    // ==================== 유틸리티 ====================

    /**
     * 활성 구독자 수 확인
     */
    public static int getActiveSubscriberCount() {
        return activeSubscribers.size();
    }

    /**
     * 활성화된 Pub/Sub 채널 목록 확인
     */
    public static List<String> getChannels(String pattern) {
        if (!isConnected()) {
            return List.of();
        }
        try {
            return sync().pubsubChannels(pattern);
        } catch (Exception e) {
            logger.error("Error getting pubsub channels", e);
            return List.of();
        }
    }

    /**
     * Redis 정보 조회
     */
    public static String getInfo() {
        if (!isConnected()) {
            return "RedisPubSub: Not connected";
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
            return "RedisPubSub: Error getting info - " + e.getMessage();
        }
    }
}