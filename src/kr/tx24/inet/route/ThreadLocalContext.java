package kr.tx24.inet.route;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.lib.inter.INet;

final class ThreadLocalContext {
    
    private static final ThreadLocal<ChannelHandlerContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<INet> INET = new ThreadLocal<>();
    
    public static void set(ChannelHandlerContext ctx, INet inet) {
        CONTEXT.set(ctx);
        INET.set(inet);
    }
    
    public static ChannelHandlerContext getContext() {
        return CONTEXT.get();
    }
     
    public static INet getINet() {
        return INET.get();
    }
    
    public static void clear() {
        CONTEXT.remove();
        INET.remove();
    }
    
    private ThreadLocalContext() {}
}
