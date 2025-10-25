package kr.tx24.lib.redis;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.nustaq.serialization.FSTConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.codec.RedisCodec;

public class RedisFstCodec<T> implements RedisCodec<String, T> {

private static final Logger logger = LoggerFactory.getLogger(RedisFstCodec.class);
    

    private static final FSTConfiguration FST_CONF = FSTConfiguration.createDefaultConfiguration();
    
    static {
        // 성능 최적화: 자주 사용하는 클래스 등록
        FST_CONF.registerClass(
        	kr.tx24.lib.map.SharedMap.class,
        	kr.tx24.lib.map.LinkedMap.class,
        	kr.tx24.lib.map.ThreadSafeLinkedMap.class,
            java.util.ArrayList.class,
            java.util.HashMap.class,
            java.util.LinkedHashMap.class,
            java.util.HashSet.class,
            java.time.LocalDateTime.class,
            java.time.LocalDate.class
        );
    }
    
    private final Class<T> type;
    
    public RedisFstCodec(Class<T> type) {
        this.type = type;
    }
    
    
    // ---------------- Key Encoding/Decoding ----------------
    
    @Override
    public String decodeKey(ByteBuffer bytes) {
        if (bytes == null || !bytes.hasRemaining()) {
            return "";
        }
        return StandardCharsets.UTF_8.decode(bytes).toString();
    }
    
    @Override
    public ByteBuffer encodeKey(String key) {
        if (key == null || key.isEmpty()) {
            return ByteBuffer.allocate(0);
        }
        return StandardCharsets.UTF_8.encode(key);
    }
    
    
    // ---------------- Value Encoding/Decoding ----------------
    
    @Override
    public T decodeValue(ByteBuffer bytes) {
        if (bytes == null || !bytes.hasRemaining()) {
            return null;
        }
        
        try {
            // ByteBuffer -> byte array
            byte[] array = new byte[bytes.remaining()];
            bytes.get(array);
            
            // FST 역직렬화
            Object obj = FST_CONF.asObject(array);
            
            // 타입 체크
            if (obj != null && !type.isInstance(obj)) {
                logger.error("FST decode type mismatch: expected {}, got {}", 
                    type.getName(), obj.getClass().getName());
                return null;
            }
            
            return type.cast(obj);
            
        } catch (Exception e) {
            logger.error("FST decode error for type {}: {}", type.getName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Decode error details", e);
            }
            return null;
        }
    }
    
    @Override
    public ByteBuffer encodeValue(T value) {
        if (value == null) {
            return ByteBuffer.allocate(0);
        }
        
        try {
            // FST 직렬화
            byte[] bytes = FST_CONF.asByteArray(value);
            return ByteBuffer.wrap(bytes);
            
        } catch (Exception e) {
            logger.error("FST encode error for type {}: {}", type.getName(), e.getMessage());
            if (logger.isDebugEnabled()) {
                logger.debug("Encode error details", e);
            }
            return ByteBuffer.allocate(0);
        }
    }
}
