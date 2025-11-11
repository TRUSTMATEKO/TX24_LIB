package kr.tx24.lib.redis;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Redis Connection 관리
 * 
 * <p><b> Redis 설계 방안</b></p>
 * <ul>
 *   <li>Connection Pool 사용하지 않음 (Netty 기반 Non-blocking I/O</li>
 *   <li>하나의 Connection 을 여러 쓰레드에서 재사용</li>
 *   <li>Connection 에 대한 close() 제거, close() 는 성능 저하의 원인이며. Shutdown 시 자동 종료</li>
 *   <li>Lettuce 공식 권장사항: "Reuse connections, don't create/close per operation"</li>
 *   <li>getConnection() 시 close 되어 있다면 reconnect() 시도</li>
 * </ul>
 * 
 * @author TX24
 * @version 1.1
 * @see <a href="https://lettuce.io/core/release/reference/">Lettuce Reference</a>
 */
public final class Redis {

    private static final Logger logger = LoggerFactory.getLogger(Redis.class);
    
    private static volatile RedisClient client;
    private static volatile ClientResources clientResources;
    private static volatile StatefulRedisConnection<String, Object> connection;
    private static volatile boolean reconnecting = false;
    
    // 초기화 상태 추적 플래그 추가
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

    private Redis() {
        throw new UnsupportedOperationException("Utility class");
    }

    private static synchronized void initClient() {
        if (client != null && connection != null && connection.isOpen()) {
            return;
        }
        
        // shutdown 중이면 초기화하지 않음
        if (isShuttingDown.get()) {
            logger.debug("Redis is shutting down, skipping initialization");
            return;
        }
        
        try {
            SystemUtils.init();
            String redisUri = SystemUtils.getRedisSystemUri();
            
            if (SystemUtils.REDIS_INITIAL.equals(redisUri)) {
                throw new IllegalStateException("NOT_SET");
            }
            
            // 기존 리소스 정리 (재연결의 경우)
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception e) {
                    logger.debug("Error closing old connection: {}", e.getMessage());
                }
                connection = null;
            }
            
            if (client != null) {
                try {
                    client.shutdown(100, 500, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    logger.debug("Error shutting down old client: {}", e.getMessage());
                }
                client = null;
            }
            
            if (clientResources == null) {
                // I/O 스레드: CPU 코어 수만큼 (최소 2개), Computation 스레드: CPU 코어 수만큼 (최소 2개)
                int cpuCount = Runtime.getRuntime().availableProcessors();
                clientResources = DefaultClientResources.builder()
                        .ioThreadPoolSize(Math.max(2, cpuCount))
                        .computationThreadPoolSize(Math.max(2, cpuCount))
                        .build();
            }
            
            RedisURI uri = RedisURI.create(redisUri);
            uri.setTimeout(Duration.ofSeconds(10));  // Connection timeout
            
            // Redis Client 생성
            client = RedisClient.create(clientResources, uri);
            
            connection = client.connect(new TypedRedisCodec());
            
            // Connection 유효성 검증
            String pong = connection.sync().ping();
            if (!"PONG".equals(pong)) {
                throw new IllegalStateException("Redis connection validation failed");
            }
            
            // 초기화 성공
            isInitialized.set(true);
            logger.info("Redis        : initialized ");
            
        } catch (IllegalStateException e) {
            if ("NOT_SET".equals(e.getMessage())) {
                logger.warn("Redis 주소가 등록되지 않았습니다: -DREDIS -DREDIS_KEY");
            } else {
                throw e;
            }
        } catch (Exception e) {
            logger.error("Redis client initialization failed: {}", SystemUtils.getRedisSystemUri(), e);
        }
    }

    // ==================== Connection 접근 ====================

    /**
     * Redis Connection 반환
     * Thread-Safe, 자동 재연결
     * @return Thread-Safe한 Redis Connection
     * @throws RuntimeException 재연결 실패 시
     */
    public static StatefulRedisConnection<String, Object> getConnection() {
        // shutdown 중이면 예외 발생
        if (isShuttingDown.get()) {
            throw new IllegalStateException("Redis is shutting down");
        }
        
        if (connection == null || !connection.isOpen()) {
            synchronized (Redis.class) {
                if (connection == null || !connection.isOpen()) {
                    initClient(); 
                }
            }
        }
        
        if (connection != null && connection.isOpen()) {
            return connection;
        }
        
        return reconnect();
    }

    /**
     * ⭐ Connection 재연결 (Thread-Safe)
     * 
     * <p><b>재연결 로직:</b></p>
     * <ol>
     *   <li>중복 재연결 방지 (reconnecting flag)</li>
     *   <li>synchronized로 동시 재연결 방지</li>
     *   <li>Double-check: 재연결 중 다른 스레드가 성공했을 수도 있음</li>
     *   <li>initClient() 호출하여 재연결</li>
     *   <li>재연결 성공/실패 로깅</li>
     * </ol>
     * 
     * @return 재연결된 Connection
     * @throws RuntimeException 재연결 실패 시
     */
    private static synchronized StatefulRedisConnection<String, Object> reconnect() {
        // shutdown 중이면 재연결하지 않음
        if (isShuttingDown.get()) {
            throw new IllegalStateException("Redis is shutting down, cannot reconnect");
        }
        
        // Double-check: 다른 스레드가 이미 재연결 했을 수도 있음
        if (connection != null && connection.isOpen()) {
            return connection;
        }
        
        // 재연결 시도 중 표시
        if (reconnecting) {
            // 다른 스레드가 재연결 중이면 잠시 대기
            int attempts = 0;
            while (reconnecting && attempts < 50) {  // 최대 5초 대기
                try {
                    Thread.sleep(100);
                    attempts++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for reconnection", e);
                }
            }
            
            // 대기 후 다시 확인
            if (connection != null && connection.isOpen()) {
                return connection;
            }
        }
        
        reconnecting = true;
        
        try {
            logger.info("Redis connection is not available. Attempting to reconnect...");
            
            // 재연결 시도
            initClient();
            
            // 재연결 성공 확인
            if (connection == null || !connection.isOpen()) {
                throw new IllegalStateException("Reconnection failed: connection is still not available");
            }
            
            logger.warn("Redis reconnection successful");
            return connection;
            
        } catch (Exception e) {
            logger.warn("Redis reconnection failed", e);
            throw new RuntimeException(
                "Redis connection is not available and reconnection failed. " +
                "Please check Redis server status and network connectivity.", 
                e
            );
        } finally {
            reconnecting = false;
        }
    }

    /**
     * ⭐ Sync Commands (동기 방식 - 권장)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Redis.sync().set("key", value);
     * Object result = Redis.sync().get("key");
     * </pre>
     * 
     * @return Thread-Safe한 Sync Commands
     */
    public static RedisCommands<String, Object> sync() {
        return getConnection().sync();
    }

    /**
     * ⭐ Async Commands (비동기 방식)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * RedisFuture<String> future = Redis.async().set("key", value);
     * future.thenAccept(result -> System.out.println("Done: " + result));
     * </pre>
     * 
     * @return Thread-Safe한 Async Commands
     */
    public static RedisAsyncCommands<String, Object> async() {
        return getConnection().async();
    }

    // ==================== Connection 상태 ====================

    /**
     * Connection 활성 상태 확인
     * 
     * @return Connection이 열려있고 사용 가능하면 true
     */
    public static boolean isConnected() {
        return connection != null && connection.isOpen();
    }

    /**
     * Redis 서버 연결 확인 PING -> PONG
     * 
     * @return "PONG" 또는 null (연결 실패)
     */
    public static String ping() {
        try {
            return sync().ping();
        } catch (Exception e) {
            logger.warn("Ping failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Redis Client 반환
     * 
     * @return Redis Client
     */
    public static RedisClient getClient() {
        if (client == null) {
            getConnection(); // 필요시 초기화
        }
        return client;
    }

    /**
     * ⭐⭐ Redis 리소스 정리 (애플리케이션 종료 시에만!)
     */
    public static synchronized void shutdown() {
        // shutdown 플래그 설정
        if (!isShuttingDown.compareAndSet(false, true)) {
            logger.debug("Redis shutdown already in progress");
            return;
        }
        
        // 초기화되지 않았으면 바로 리턴 (중요!)
        if (!isInitialized.get()) {
            logger.debug("Redis was not initialized, skipping shutdown");
            return;
        }
        
        // 1. Connection 종료
        if (connection != null) {
            try {
                if (connection.isOpen()) {
                    connection.close();
                }
            } catch (Exception e) {
                logger.warn("Error closing connection: {}", e.getMessage());
            } finally {
                connection = null;
            }
        }
        
        // 2. Redis Client 종료
        if (client != null) {
            try {
                client.shutdown(100, 1000, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                logger.warn("Error shutting down Redis client: {}", e.getMessage());
            } finally {
                client = null;
            }
        }
        
        // 3. ClientResources 종료
        if (clientResources != null) {
            try {
                clientResources.shutdown(100, 1000, TimeUnit.MILLISECONDS).get();
            } catch (Exception e) {
                logger.warn("Error shutting down ClientResources: {}", e.getMessage());
            } finally {
                clientResources = null;
            }
        }
        
        isInitialized.set(false);
        logger.info("Redis shutdown");
    }

    // ==================== 유틸리티 ====================

    /**
     * Redis 정보 조회 
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
    
    /**
     * 초기화 상태 확인
     * @return 초기화되었으면 true
     */
    public static boolean isInitialized() {
        return isInitialized.get();
    }
    
    /**
     * Shutdown 진행 중 확인
     * @return shutdown 중이면 true
     */
    public static boolean isShuttingDown() {
        return isShuttingDown.get();
    }
}