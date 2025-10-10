package kr.tx24.inter.mapper;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.lib.inter.INet;

final class ThreadLocalContext {
    
    private static final ThreadLocal<ChannelHandlerContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<INet> INET = new ThreadLocal<>();
    
    static void set(ChannelHandlerContext ctx, INet inet) {
        CONTEXT.set(ctx);
        INET.set(inet);
    }
    
    static ChannelHandlerContext getContext() {
        return CONTEXT.get();
    }
    
    static INet getINet() {
        return INET.get();
    }
    
    static void clear() {
        CONTEXT.remove();
        INET.remove();
    }
    
    private ThreadLocalContext() {}
}
