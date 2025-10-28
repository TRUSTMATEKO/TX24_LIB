package kr.tx24.lib.redis;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.sync.RedisPubSubCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * Redis Pub/Sub 전용 Connection 관리자 (JDK 17+, Lettuce 6.2.x)
 * 
 * <p><b>설계 철학:</b></p>
 * <ul>
 *   <li>Subscribe는 장기 연결이 필요하므로 명시적 관리</li>
 *   <li>Publish는 일회성 작업이므로 필요시 Connection 생성/해제</li>
 * </ul>
 * 
 * <p><b>사용 예시:</b></p>
 * <pre>
 * // 1. 메시지 발행 (간단한 방식)
 * RedisPubSub.publish("channel:news", "Breaking News!");
 * 
 * // 2. 메시지 구독 (Listener 등록)
 * RedisPubSub.Subscriber subscriber = RedisPubSub.subscribe("channel:news", message -> {
 *     System.out.println("수신: " + message);
 * });
 * 
 * // 3. 구독 해제
 * subscriber.unsubscribe();
 * subscriber.close();
 * 
 * // 4. 재사용 가능한 Publisher
 * try (RedisPubSub.Publisher publisher = RedisPubSub.createPublisher()) {
 *     publisher.publish("channel1", "Message 1");
 *     publisher.publish("channel2", "Message 2");
 * } // auto-close
 * </pre>
 */
public final class RedisPubSub {

    private static final Logger logger = LoggerFactory.getLogger(RedisPubSub.class);
    
    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    
    // 활성 Subscriber 관리 (채널별)
    private static final Map<String, Subscriber> activeSubscribers = new ConcurrentHashMap<>();
    
    static {
        initClient();
        Runtime.getRuntime().addShutdownHook(new Thread(RedisPubSub::shutdown, "ShutdownHook-RedisPubSub"));
    }

    private RedisPubSub() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== 초기화 및 종료 ==========
    
    private static synchronized void initClient() {
        if (client != null) return;
        
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
            
            logger.info("RedisPubSub client initialized");
            
        } catch (Exception e) {
            logger.error("Failed to initialize RedisPubSub client", e);
            throw new RuntimeException("RedisPubSub initialization failed", e);
        }
    }
    
    /**
     * 모든 활성 연결 종료 및 리소스 해제
     */
    public static synchronized void shutdown() {
        logger.info("Shutting down RedisPubSub...");
        
        // 모든 Subscriber 종료
        activeSubscribers.values().forEach(subscriber -> {
            try {
                subscriber.close();
            } catch (Exception e) {
                logger.warn("Failed to close subscriber", e);
            }
        });
        activeSubscribers.clear();
        
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
        
        logger.info("RedisPubSub shutdown completed");
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
        try (Publisher publisher = createPublisher()) {
            return publisher.publish(channel, message);
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
        try (Publisher publisher = createPublisher()) {
            return publisher.publishJson(channel, message);
        }
    }

    // ========== Publisher API (재사용 가능) ==========
    
    /**
     * 재사용 가능한 Publisher 생성
     * try-with-resources 사용 권장
     */
    public static Publisher createPublisher() {
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
        }
        
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            
            try {
                connection.close();
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
                    logger.info("Subscribed to channel: {} (total subscriptions: {})", channel, count);
                }

                @Override
                public void psubscribed(String pattern, long count) {
                    logger.info("Subscribed to pattern: {} (total subscriptions: {})", pattern, count);
                }

                @Override
                public void unsubscribed(String channel, long count) {
                    logger.info("Unsubscribed from channel: {} (remaining: {})", channel, count);
                }

                @Override
                public void punsubscribed(String pattern, long count) {
                    logger.info("Unsubscribed from pattern: {} (remaining: {})", pattern, count);
                }
            });
            
            commands.subscribe(channel);
            this.subscribedChannel = channel;
            logger.info("Started subscription to channel: {}", channel);
        }
        
        /**
         * 패턴 구독
         */
        public void psubscribe(String pattern, Consumer<String> messageHandler) {
            checkClosed();
            
            connection.addListener(new RedisPubSubListener<String, String>() {
                @Override
                public void message(String channel, String message) {
                    // 일반 subscribe에서 사용
                }

                @Override
                public void message(String pattern, String channel, String message) {
                    try {
                        messageHandler.accept(message);
                    } catch (Exception e) {
                        logger.error("Error in pattern message handler for {}", pattern, e);
                    }
                }

                @Override
                public void subscribed(String channel, long count) {}

                @Override
                public void psubscribed(String pattern, long count) {
                    logger.info("Subscribed to pattern: {} (total subscriptions: {})", pattern, count);
                }

                @Override
                public void unsubscribed(String channel, long count) {}

                @Override
                public void punsubscribed(String pattern, long count) {
                    logger.info("Unsubscribed from pattern: {} (remaining: {})", pattern, count);
                }
            });
            
            commands.psubscribe(pattern);
            this.subscribedPattern = pattern;
            logger.info("Started subscription to pattern: {}", pattern);
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
                    logger.info("Unsubscribed from pattern: {}", subscribedPattern);
                }
            } catch (Exception e) {
                logger.warn("Error during unsubscribe", e);
            }
        }
        
        private void checkClosed() {
            if (closed) {
                throw new IllegalStateException("Subscriber already closed");
            }
        }
        
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            
            unsubscribe();
            
            try {
                connection.close();
                logger.debug("Subscriber connection closed");
            } catch (Exception e) {
                logger.warn("Error closing subscriber connection", e);
            }
        }
    }

    // ========== 유틸리티 메서드 ==========
    
    /**
     * 특정 채널의 활성 구독자 수 조회
     */
    public static Long countSubscribers(String channel) {
        try (var conn = client.connect(StringCodec.UTF8)) {
            RedisCommands<String, String> commands = conn.sync();
            
            // PUBSUB NUMSUB channel 명령어 사용
            var result = commands.pubsubNumsub(channel);
            return result.isEmpty() ? 0L : result.get(channel);
            
        } catch (Exception e) {
            logger.error("Failed to count subscribers for channel: {}", channel, e);
            return 0L;
        }
    }
    
    /**
     * 활성 채널 목록 조회
     */
    public static java.util.List<String> getActiveChannels() {
        try (var conn = client.connect(StringCodec.UTF8)) {
            RedisCommands<String, String> commands = conn.sync();
            return commands.pubsubChannels();
        } catch (Exception e) {
            logger.error("Failed to get active channels", e);
            return java.util.List.of();
        }
    }
    
    /**
     * 현재 관리 중인 Subscriber 수
     */
    public static int getActiveSubscriberCount() {
        return activeSubscribers.size();
    }
}