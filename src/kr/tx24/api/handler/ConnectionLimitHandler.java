package kr.tx24.api.handler;

import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import kr.tx24.lib.netty.NettyUtils;

public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(ConnectionLimitHandler.class);
    
    // IP별 활성 연결 수
    private static final ConcurrentHashMap<String, AtomicInteger> connections = new ConcurrentHashMap<>();
    
    private final int maxConnectionsPerIp;
    
    public ConnectionLimitHandler(int maxConnectionsPerIp) {
        this.maxConnectionsPerIp = maxConnectionsPerIp;
    }
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String clientIp = NettyUtils.getRemoteAddress(ctx);
        
        AtomicInteger count = connections.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        int currentConnections = count.incrementAndGet();
        
        if (currentConnections > maxConnectionsPerIp) {
            logger.warn("Connection limit exceeded for IP: {} (current: {})", clientIp, currentConnections);
            count.decrementAndGet();
            ctx.close();
            return;
        }
        
        logger.debug("Connection accepted from IP: {} (total: {})", clientIp, currentConnections);
        super.channelActive(ctx);
    }
    
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	String clientIp = NettyUtils.getRemoteAddress(ctx);
        
        AtomicInteger count = connections.get(clientIp);
        if (count != null) {
            int remaining = count.decrementAndGet();
            if (remaining <= 0) {
                connections.remove(clientIp);
            }
            logger.debug("Connection closed from IP: {} (remaining: {})", clientIp, remaining);
        }
        
        super.channelInactive(ctx);
    }
    
    private String getClientIp(ChannelHandlerContext ctx) {
        InetSocketAddress address = (InetSocketAddress) ctx.channel().remoteAddress();
        return address.getAddress().getHostAddress();
    }


}
