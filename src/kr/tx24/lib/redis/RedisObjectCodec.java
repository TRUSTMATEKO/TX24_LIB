package kr.tx24.lib.redis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.codec.RedisCodec;

/**
 * Generic RedisObjectCodec for Lettuce
 * Supports Java serialization for arbitrary objects
 *
 * @param <T> value type
 */
public class RedisObjectCodec<T> implements RedisCodec<String, T> {

    private static final Logger logger = LoggerFactory.getLogger(RedisObjectCodec.class);

    private final Class<T> type;

    public RedisObjectCodec(Class<T> type) {
        this.type = type;
    }

    // ---------------- Key Encoding/Decoding ----------------
    @Override
    public String decodeKey(ByteBuffer bytes) {
        return StandardCharsets.UTF_8.decode(bytes).toString();
    }

    @Override
    public ByteBuffer encodeKey(String key) {
        if (key == null) return ByteBuffer.allocate(0);
        return StandardCharsets.UTF_8.encode(key);
    }

    // ---------------- Value Encoding/Decoding ----------------
    @SuppressWarnings("unchecked")
    @Override
    public T decodeValue(ByteBuffer bytes) {
        if (bytes == null || !bytes.hasRemaining()) return null;

        byte[] array = new byte[bytes.remaining()];
        bytes.get(array);

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(array))) {
            Object obj = ois.readObject();
            if (type.isInstance(obj)) {
                return (T) obj;
            } else {
                logger.warn("Type mismatch: expected {}, actual {}", type.getName(), obj.getClass().getName());
                return null;
            }
        } catch (Exception e) {
            logger.error("Redis decode error for type {}: {}", type.getName(), e.getMessage(), e);
            return null;
        }
    }

    @Override
    public ByteBuffer encodeValue(T value) {
        if (value == null) return ByteBuffer.allocate(0);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {

            oos.writeObject(value);
            oos.flush();
            return ByteBuffer.wrap(baos.toByteArray());

        } catch (IOException e) {
            logger.error("Redis encode error for type {}: {}", type.getName(), e.getMessage(), e);
            return ByteBuffer.allocate(0);
        }
    }
}