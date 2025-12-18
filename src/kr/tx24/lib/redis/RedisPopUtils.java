package kr.tx24.lib.redis;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.TypeRegistry;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * Redis 블로킹 POP 유틸리티 클래스 (BLPOP, BRPOP)
 * * <p><b>주요 특징:</b></p>
 * <ul>
 * <li>1. Connection은 함수 호출 시 연결하고, 실패 시 오류(null) 리턴</li>
 * <li>2. Connection은 함수 종료 시 반드시 종료 (finally 블록 사용)</li>
 * <li>4. timeoutSecond 값에 따라 Connection의 명령어 Timeout을 동적으로 지정</li>
 * </ul>
 */
public final class RedisPopUtils {

    private static final Logger logger = LoggerFactory.getLogger(RedisPopUtils.class);
    private static final JacksonUtils jsonUtils = new JacksonUtils().compact();

    /**
     * 3. BLPOP 명령을 실행합니다.
     * @param key POP 작업을 수행할 Redis 키
     * @param timeoutSecond Redis 서버 대기 시간 및 Connection 명령어 Timeout (초)
     * @return 추출된 값 (String 형태의 Object) 또는 Connection 오류/타임아웃 시 null
     */
    public static Object blpop(String key, long timeoutSecond) throws Exception {
        return executeBlockingPop(key, timeoutSecond, false); // false for BLPOP
    }

    /**
     * 3. BRPOP 명령을 실행합니다.
     * @param key POP 작업을 수행할 Redis 키
     * @param timeoutSecond Redis 서버 대기 시간 및 Connection 명령어 Timeout (초)
     * @return 추출된 값 (String 형태의 Object) 또는 Connection 오류/타임아웃 시 null
     */
    public static Object brpop(String key, long timeoutSecond) throws Exception {
        return executeBlockingPop(key, timeoutSecond, true); // true for BRPOP
    }
    
    
    private static Object unmarshall(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        // 1. JSON 시작 여부 확인 (배열 '[' 또는 객체 '{')
        if (!data.startsWith("[") && !data.startsWith("{")) {
            return data; // 단순 String 형식으로 판단
        }
        
        // 2. JacksonUtils를 사용하여 JSON 파싱 및 유효성 검사
        JsonNode rootNode = jsonUtils.toJsonNode(data);
        
        if (rootNode == null) {
            return data; // JSON 파싱 실패 시, 단순 String으로 간주
        }

        try {
            // A. JSON 배열 형식 ("[\"className\", {data}]") 처리
            if (rootNode.isArray() && rootNode.size() == 2 
                    && rootNode.get(0).isTextual() 
                    && rootNode.get(1).isObject()) {
                
                String className = rootNode.get(0).asText();
                String dataJson = rootNode.get(1).toString(); // 객체 노드를 다시 JSON String으로 추출

                // 클래스 이름으로 Class 객체를 로드하고 변환 시도
                Class<?> targetClass = Class.forName(className);
                
                return jsonUtils.fromJson(dataJson, targetClass); 
                
            } 
            
            // B. JSON 객체 형식 ("{...}") 처리
            else if (rootNode.isObject()) {
                return jsonUtils.fromJson(data, TypeRegistry.MAP_SHAREDMAP_OBJECT); 
            }
            
        } catch (ClassNotFoundException e) {
            logger.info("Class not found during POJO unmarshalling: {}", e.getMessage());
        } catch (Exception e) {
            // JSON 파싱은 toJsonNode에서, 변환 오류는 여기서 처리
            logger.info("Unexpected error during unmarshalling. Returning as String: {}", e.getMessage());
        }
        
        return data; 
    }


    private static Object executeBlockingPop(String key, long timeoutSecond, boolean isRightPop) throws Exception{
        
       
        StatefulRedisConnection<String, String> connection = null;

        if (key == null || key.isEmpty() || timeoutSecond <= 0) {
        	throw new Exception(MsgUtils.format("Invalid arguments: key={}, timeoutSecond={}",key, timeoutSecond));
        }

        try {
        	
        	RedisClient client = Redis.getClient(); 
            String redisUri = Redis.getUriString();
        	
            if (SystemUtils.REDIS_INITIAL.equals(redisUri)) {
                throw new IllegalStateException("NOT_SET redisUrl");
            }
            if (redisUri == null || redisUri.isEmpty()) {
            	throw new Exception(MsgUtils.format("RedisUri is empty"));
            }
            
            
            RedisURI baseUri = RedisURI.create(redisUri);
            RedisURI blockingUri = RedisURI.builder(baseUri)
                                           .withTimeout(Duration.ofSeconds(timeoutSecond)) 
                                           .build();
            
            connection = client.connect(blockingUri); 
            RedisCommands<String, String> sync = connection.sync();
            
            KeyValue<String, String> result;
            if (isRightPop) {
                result = sync.brpop(timeoutSecond, key); 
            } else {
                result = sync.blpop(timeoutSecond, key); 
            }

            if (result == null || result.getValue() == null) {
                return null;
            }
            return unmarshall(result.getValue()); 
            
        } catch (RedisConnectionException e) {
        	throw new Exception(MsgUtils.format("Redis Connection failed: {}", key, e));
        } catch (Exception e) {
        	throw new Exception(MsgUtils.format("Redis Blocking POP failed for key: {} (Timeout: {}s)", key, timeoutSecond, e)); 
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (Exception closeEx) {
                    logger.warn("Failed to close Redis connection: {}", closeEx.getMessage());
                }
            }
        }
    }
}