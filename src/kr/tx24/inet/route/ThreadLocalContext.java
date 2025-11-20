package kr.tx24.inet.route;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.lib.inter.INetB;

final class ThreadLocalContext {
    
    private static final ThreadLocal<ChannelHandlerContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<INetB> INET = new ThreadLocal<>();
    
    public static void set(ChannelHandlerContext ctx, INetB inet) {
        CONTEXT.set(ctx);
        INET.set(inet);
    }
    
    public static ChannelHandlerContext getContext() {
        return CONTEXT.get();
    }
    
    public static INetB getINet() {
        return INET.get();
    }
    
    public static void clear() {
        CONTEXT.remove();
        INET.remove();
    }
    
    private ThreadLocalContext() {}
}
