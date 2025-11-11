package kr.tx24.lib.redis;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * Redis Pub/Sub 전용 Connection 관리자 (수정 버전 v2)
 * - static 블록 자동 초기화 제거
 * - Lazy initialization (실제 사용시에만 초기화)
 * - shutdown시 불필요한 초기화 방지
 */
public final class RedisPubSub {

    private static final Logger logger = LoggerFactory.getLogger(RedisPubSub.class);
    
    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    
    // 상태 관리 플래그
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    
    // 활성 Subscriber 관리 (채널별)
    private static final Map<String, Subscriber> activeSubscribers = new ConcurrentHashMap<>();
    
    // static 블록 제거 - 자동 초기화 하지 않음
    // static {
    //     initClient();
    // }

    private RedisPubSub() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== 초기화 및 종료 ==========
    
    /**
     * Client 초기화 (Lazy initialization)
     * 실제 사용시에만 호출됨
     */
    private static synchronized void initClient() {
        // 이미 초기화되었으면 스킵
        if (isInitialized.get()) {
            return;
        }
        
        // shutdown 중이면 초기화하지 않음
        if (isShuttingDown.get()) {
            //logger.debug("RedisPubSub is shutting down, skipping initialization");
            return;
        }
        
        try {
            SystemUtils.init();
            String redisUri = SystemUtils.getRedisSystemUri();
            
            if (SystemUtils.REDIS_INITIAL.equals(redisUri)) {
                throw new IllegalStateException("Redis URI not configured");
            }
            
            // ClientResources 최적화
            clientResources = DefaultClientResources.builder()
                    .ioThreadPoolSize(2)  // Pub/Sub는 I/O 집약적
                    .computationThreadPoolSize(2)
                    .build();
            
            RedisURI uri = RedisURI.create(redisUri);
            uri.setTimeout(Duration.ofSeconds(10));
            
            client = RedisClient.create(clientResources, uri);
            isInitialized.set(true);
            
            logger.info("RedisPubSub  : initialized");
        } catch (Exception e) {
            logger.error("Failed to initialize RedisPubSub client", e);
            throw new RuntimeException("RedisPubSub initialization failed", e);
        }
    }
    
    /**
     * client가 사용 가능한지 확인하고 필요시 초기화
     * 실제 pub/sub 작업 전에만 호출
     */
    private static void ensureClientAvailable() {
        if (isShuttingDown.get()) {
            throw new IllegalStateException("RedisPubSub is shutting down");
        }
        
        if (!isInitialized.get()) {
            synchronized (RedisPubSub.class) {
                if (!isInitialized.get() && !isShuttingDown.get()) {
                    initClient();
                }
            }
        }
        
        if (client == null) {
            throw new IllegalStateException("RedisPubSub client is not available");
        }
    }
    
    /**
     * 모든 활성 연결 종료 및 리소스 해제
     */
    public static synchronized void shutdown() {
        // shutdown 플래그 설정
        if (!isShuttingDown.compareAndSet(false, true)) {
            logger.debug("RedisPubSub shutdown already in progress");
            return;
        }
        
        // 초기화되지 않았으면 바로 리턴
        if (!isInitialized.get()) {
            //logger.debug("RedisPubSub was not initialized, skipping shutdown");
            return;
        }
        
        // 모든 Subscriber 종료
        if (!activeSubscribers.isEmpty()) {
            activeSubscribers.values().forEach(subscriber -> {
                try {
                    subscriber.close();
                } catch (Exception e) {
                    logger.warn("Failed to close subscriber", e);
                }
            });
            activeSubscribers.clear();
        }
        
        // Client 종료
        if (client != null) {
            try {
                client.shutdown(100, 1000, TimeUnit.MILLISECONDS);
                client = null;
            } catch (Exception e) {
                logger.error("Error during client shutdown", e);
            }
        }
        
        // ClientResources 종료
        if (clientResources != null) {
            try {
                clientResources.shutdown(100, 1000, TimeUnit.MILLISECONDS).get();
                clientResources = null;
            } catch (Exception e) {
                logger.error("Error during client resources shutdown", e);
            }
        }
        
        isInitialized.set(false);
        logger.info("RedisPubSub shutdown");
    }

    // ========== 간편 API (일회성 Publish) ==========
    
    /**
     * 메시지 발행 (일회성 - Connection 자동 생성/해제)
     * 
     * @param channel 채널명
     * @param message 메시지 (String)
     * @return 메시지를 받은 구독자 수
     */
    public static Long publish(String channel, String message) {
        if (isShuttingDown.get()) {
            logger.warn("Cannot publish, RedisPubSub is shutting down");
            return 0L;
        }
        
        try (Publisher publisher = createPublisher()) {
            return publisher.publish(channel, message);
        } catch (Exception e) {
            logger.error("Failed to publish message", e);
            return 0L;
        }
    }
    
    /**
     * 메시지 발행 (일회성 - JSON 객체)
     * 
     * @param channel 채널명
     * @param message 메시지 객체 (JSON으로 직렬화됨)
     * @return 메시지를 받은 구독자 수
     */
    public static <T> Long publishJson(String channel, T message) {
        if (isShuttingDown.get()) {
            logger.warn("Cannot publish, RedisPubSub is shutting down");
            return 0L;
        }
        
        try (Publisher publisher = createPublisher()) {
            return publisher.publishJson(channel, message);
        } catch (Exception e) {
            logger.error("Failed to publish JSON message", e);
            return 0L;
        }
    }

    // ========== Publisher API (재사용 가능) ==========
    
    /**
     * 재사용 가능한 Publisher 생성
     * try-with-resources 사용 권장
     */
    public static Publisher createPublisher() {
        ensureClientAvailable();  // 여기서 필요시 초기화
        return new Publisher();
    }
    
    /**
     * 메시지 발행용 Publisher
     * - 여러 번 publish 가능
     * - AutoCloseable로 자동 정리
     */
    public static class Publisher implements AutoCloseable {
        
        private final StatefulRedisPubSubConnection<String, String> connection;
        private final RedisPubSubCommands<String, String> commands;
        private volatile boolean closed = false;
        
        private Publisher() {
            this.connection = client.connectPubSub(StringCodec.UTF8);
            this.commands = connection.sync();
            logger.debug("Publisher connection created");
        }
        
        /**
         * 메시지 발행 (String)
         */
        public Long publish(String channel, String message) {
            checkClosed();
            try {
                Long subscribers = commands.publish(channel, message);
                logger.debug("Published to {}: {} (received by {} subscribers)", 
                           channel, message, subscribers);
                return subscribers;
            } catch (Exception e) {
                logger.error("Failed to publish to channel: {}", channel, e);
                throw new RuntimeException("Publish failed", e);
            }
        }
        
        /**
         * 메시지 발행 (JSON)
         */
        public <T> Long publishJson(String channel, T message) {
            checkClosed();
            try {
                String json = new JacksonUtils().toJson(message);
                return publish(channel, json);
            } catch (Exception e) {
                logger.error("Failed to serialize and publish JSON to channel: {}", channel, e);
                throw new RuntimeException("JSON publish failed", e);
            }
        }
        
        /**
         * 패턴 발행 (여러 채널에 한 번에 전송)
         */
        public void publishToPattern(String channelPattern, String message, String... channels) {
            checkClosed();
            for (String channel : channels) {
                if (channel.matches(channelPattern.replace("*", ".*"))) {
                    publish(channel, message);
                }
            }
        }
        
        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Publisher already closed");
            }
            if (isShuttingDown.get()) {
                throw new IllegalStateException("RedisPubSub is shutting down");
            }
        }
        
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            
            try {
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
                logger.debug("Publisher connection closed");
            } catch (Exception e) {
                logger.warn("Error closing publisher connection", e);
            }
        }
    }

    // ========== Subscriber API (장기 연결) ==========
    
    /**
     * 채널 구독 시작
     * 
     * @param channel 채널명
     * @param messageHandler 메시지 수신 시 호출될 핸들러
     * @return Subscriber 인스턴스 (unsubscribe/close 호출 필요)
     */
    public static Subscriber subscribe(String channel, Consumer<String> messageHandler) {
        if (isShuttingDown.get()) {
            throw new IllegalStateException("Cannot subscribe, RedisPubSub is shutting down");
        }
        
        ensureClientAvailable();  // 여기서 필요시 초기화
        Subscriber subscriber = new Subscriber();
        subscriber.subscribe(channel, messageHandler);
        activeSubscribers.put(channel, subscriber);
        return subscriber;
    }
    
    /**
     * 패턴 구독 시작 (예: "news:*")
     * 
     * @param pattern 채널 패턴
     * @param messageHandler 메시지 수신 시 호출될 핸들러
     * @return Subscriber 인스턴스
     */
    public static Subscriber psubscribe(String pattern, Consumer<String> messageHandler) {
        if (isShuttingDown.get()) {
            throw new IllegalStateException("Cannot subscribe, RedisPubSub is shutting down");
        }
        
        ensureClientAvailable();  // 여기서 필요시 초기화
        Subscriber subscriber = new Subscriber();
        subscriber.psubscribe(pattern, messageHandler);
        activeSubscribers.put(pattern, subscriber);
        return subscriber;
    }
    
    /**
     * JSON 메시지 구독
     * 
     * @param channel 채널명
     * @param messageType 메시지 타입 클래스
     * @param messageHandler 역직렬화된 객체를 받는 핸들러
     */
    public static <T> Subscriber subscribeJson(String channel, Class<T> messageType, 
                                                Consumer<T> messageHandler) {
        return subscribe(channel, json -> {
            try {
                T obj = new JacksonUtils().fromJson(json, messageType);
                messageHandler.accept(obj);
            } catch (Exception e) {
                logger.error("Failed to deserialize JSON message from {}", channel, e);
            }
        });
    }
    
    /**
     * 메시지 구독용 Subscriber
     * - 장기 연결 유지
     * - 명시적으로 unsubscribe/close 호출 필요
     */
    public static class Subscriber implements AutoCloseable {
        
        private final StatefulRedisPubSubConnection<String, String> connection;
        private final RedisPubSubCommands<String, String> commands;
        private volatile boolean closed = false;
        private String subscribedChannel;
        private String subscribedPattern;
        
        private Subscriber() {
            this.connection = client.connectPubSub(StringCodec.UTF8);
            this.commands = connection.sync();
            logger.debug("Subscriber connection created");
        }
        
        /**
         * 채널 구독
         */
        public void subscribe(String channel, Consumer<String> messageHandler) {
            checkClosed();
            
            connection.addListener(new RedisPubSubListener<String, String>() {
                @Override
                public void message(String channel, String message) {
                    try {
                        messageHandler.accept(message);
                    } catch (Exception e) {
                        logger.error("Error in message handler for channel {}", channel, e);
                    }
                }

                @Override
                public void message(String pattern, String channel, String message) {
                    // Pattern subscribe에서 사용
                }

                @Override
                public void subscribed(String channel, long count) {
                    logger.debug("Subscribed to channel: {} (total: {})", channel, count);
                }

                @Override
                public void psubscribed(String pattern, long count) {
                    // Pattern subscribe에서 사용
                }

                @Override
                public void unsubscribed(String channel, long count) {
                    logger.debug("Unsubscribed from channel: {} (remaining: {})", channel, count);
                }

                @Override
                public void punsubscribed(String pattern, long count) {
                    // Pattern unsubscribe에서 사용
                }
            });
            
            commands.subscribe(channel);
            this.subscribedChannel = channel;
            logger.info("Subscribed to channel: {}", channel);
        }
        
        /**
         * 패턴 구독
         */
        public void psubscribe(String pattern, Consumer<String> messageHandler) {
            checkClosed();
            
            connection.addListener(new RedisPubSubListener<String, String>() {
                @Override
                public void message(String channel, String message) {
                    // Regular subscribe에서 사용
                }

                @Override
                public void message(String pattern, String channel, String message) {
                    try {
                        messageHandler.accept(message);
                    } catch (Exception e) {
                        logger.error("Error in message handler for pattern {} channel {}", 
                                   pattern, channel, e);
                    }
                }

                @Override
                public void subscribed(String channel, long count) {
                    // Regular subscribe에서 사용
                }

                @Override
                public void psubscribed(String pattern, long count) {
                    logger.debug("Pattern subscribed: {} (total: {})", pattern, count);
                }

                @Override
                public void unsubscribed(String channel, long count) {
                    // Regular unsubscribe에서 사용
                }

                @Override
                public void punsubscribed(String pattern, long count) {
                    logger.debug("Pattern unsubscribed: {} (remaining: {})", pattern, count);
                }
            });
            
            commands.psubscribe(pattern);
            this.subscribedPattern = pattern;
            logger.info("Pattern subscribed: {}", pattern);
        }
        
        /**
         * 구독 해제
         */
        public void unsubscribe() {
            if (closed) return;
            
            try {
                if (subscribedChannel != null) {
                    commands.unsubscribe(subscribedChannel);
                    activeSubscribers.remove(subscribedChannel);
                    logger.info("Unsubscribed from channel: {}", subscribedChannel);
                }
                
                if (subscribedPattern != null) {
                    commands.punsubscribe(subscribedPattern);
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
            if (isShuttingDown.get()) {
                throw new IllegalStateException("RedisPubSub is shutting down");
            }
        }
        
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            
            try {
                unsubscribe();
                
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
                logger.debug("Subscriber connection closed");
            } catch (Exception e) {
                logger.warn("Error closing subscriber connection", e);
            }
        }
    }
    
    /**
     * 상태 확인 메서드들
     */
    public static boolean isInitialized() {
        return isInitialized.get() && client != null;
    }
    
    public static boolean isShuttingDown() {
        return isShuttingDown.get();
    }
    
    /**
     * 활성 구독자 수 확인
     */
    public static int getActiveSubscriberCount() {
        return activeSubscribers.size();
    }
}