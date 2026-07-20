package kr.tx24.lib.redis;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RedisHeartbeat {
    private static final Logger logger = LoggerFactory.getLogger(RedisHeartbeat.class);

    public static boolean check(StatefulConnection<?, ?> connection){
        try {
            if(connection == null){
                return false;
            }

            if (!connection.isOpen()) {
                logger.info("redis connection closed");
                return false;
            }

            if (connection instanceof StatefulRedisConnection) {
                StatefulRedisConnection<?, ?> redisConnection = (StatefulRedisConnection<?, ?>) connection;
                Object result = redisConnection.sync().ping();

                if (!"PONG".equals(result)) {
                    return false;
                }

            }

            return true;
        }catch(Exception e){
            logger.info("Redis heartbeat exception : {}", e.getMessage());
            return false;
        }
    }


    public static boolean checkPubSub(StatefulRedisPubSubConnection<?, ?> connection) {
        try {
            if(connection == null || !connection.isOpen()) {
                return false;
            }
            return true;
        } catch(Exception e) {
            logger.info( "Redis pubsub heartbeat failed", e);
            return false;
        }

    }

}
