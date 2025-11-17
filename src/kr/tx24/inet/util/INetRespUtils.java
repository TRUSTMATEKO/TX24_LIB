package kr.tx24.inet.util;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

public class INetRespUtils {

	private static final Logger logger = LoggerFactory.getLogger(INetRespUtils.class);
	private static final JacksonUtils jsonUtils = new JacksonUtils();
    
    private final ChannelHandlerContext ctx;
    private final INet resInet;
    private boolean autoClose = true;
    private long delayBeforeClose = 100L; // ms
    private boolean enableLogging = false;
    
    private INetRespUtils(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.resInet = new INet()
            .head("id", ctx.channel().id().asShortText())
            .head("result", true)
            .head("message", "successful");
    }
    
    public static INetRespUtils success(ChannelHandlerContext ctx) {
        return new INetRespUtils(ctx);
    }
    
    
    public static INetRespUtils error(ChannelHandlerContext ctx, String message) {
    	INetRespUtils utils = new INetRespUtils(ctx);
        utils.resInet
            .head("result", false)
            .head("message", message);
        return utils;
    }
    

    public static INetRespUtils custom(ChannelHandlerContext ctx, INet inet) {
    	INetRespUtils utils = new INetRespUtils(ctx);
        utils.resInet.head().putAll(inet.head());
        utils.resInet.data().putAll(inet.data());
        return utils;
    }
    
    public INetRespUtils head(String key, Object value) {
        resInet.head(key, value);
        return this;
    }
    
    
    public INetRespUtils head(Map<String, Object> headMap) {
    	resInet.head().putAll(headMap);
        return this;
    }
    
    
    public INetRespUtils data(String key, Object value) {
        resInet.data(key, value);
        return this;
    }
    
    
    public INetRespUtils data(Map<String, Object> headMap) {
    	resInet.data().putAll(headMap);
        return this;
    }
    
    
    public INetRespUtils result(boolean success) {
    	resInet.head("result", success);
        return this;
    }
    
    public INetRespUtils message(String message) {
    	resInet.head("message", message);
        return this;
    }
    
    public INetRespUtils dataFromJson(String key, Object object) {
        if (object != null) {
        	resInet.data(key, jsonUtils.toJson(object));
        }
        return this;
    }
    
    
    public INetRespUtils autoClose(boolean autoClose) {
        this.autoClose = autoClose;
        return this;
    }
    
    /**
     * 연결 종료 전 대기 시간 설정 (기본: 100ms)
     */
    public INetRespUtils delayBeforeClose(long delayMs) {
        this.delayBeforeClose = delayMs;
        return this;
    }
    
    /**
     * 로깅 활성화
     */
    public INetRespUtils enableLogging() {
        this.enableLogging = true;
        return this;
    }
    
    
    /**
     * 동기 방식으로 응답 전송
     * 
     * @return ChannelFuture
     */
    public ChannelFuture send() {
        logIfEnabled();
        
        ChannelFuture future = ctx.writeAndFlush(resInet);
        
        if (autoClose) {
            future.addListener((ChannelFutureListener) f -> {
                if (f.isSuccess()) {
                	if(SystemUtils.deepview()) {
                		logger.debug("Response sent successfully to {}",ctx.channel().id().asShortText());
                	}
                } else {
                    logger.error("Failed to send response", f.cause());
                }
                
                if (delayBeforeClose > 0) {
                    try {
                        Thread.sleep(delayBeforeClose);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                
                f.channel().close();
            });
        }
        
        return future;
    }
    
    /**
     * 비동기 방식으로 응답 전송 (CompletableFuture 반환)
     */
    public CompletableFuture<Void> sendAsync() {
        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        
        logIfEnabled();
        
        ChannelFuture future = ctx.writeAndFlush(resInet);
        
        future.addListener((ChannelFutureListener) f -> {
            if (f.isSuccess()) {
                completableFuture.complete(null);
            } else {
                completableFuture.completeExceptionally(f.cause());
            }
            
            if (autoClose && delayBeforeClose > 0) {
                // 스케줄러 사용
                f.channel().eventLoop().schedule(() -> {
                    f.channel().close();
                }, delayBeforeClose, TimeUnit.MILLISECONDS);
            } else if (autoClose) {
                f.channel().close();
            }
        });
        
        return completableFuture;
    }
    
    
    
    
    public INet build() {
        return resInet;
    }
    
    public boolean isChannelActive() {
        return ctx.channel().isActive();
    }
    
    public boolean isChannelWritable() {
        return ctx.channel().isWritable();
    }
    
    private void logIfEnabled() {
        if (enableLogging || SystemUtils.deepview()) {
            StringBuilder sb =new StringBuilder()
            .append("\nresponse\n")
            .append("head : ").append(jsonUtils.toJson(resInet.head()))
            .append("\n")
            .append("data : ").append(jsonUtils.toJson(resInet.data()));
            logger.info(sb.toString());
        }
    }
    
    
    public static ChannelFuture sendSuccess(ChannelHandlerContext ctx,String message, Map<String, Object> data) {
    	return success(ctx)
    			.message(message)
    			.data(data)
    			.send();
	}
    
    
    public static ChannelFuture sendError(ChannelHandlerContext ctx,String errorMessage) {
    	return error(ctx, errorMessage).send();
    }
    
    public static ChannelFuture sendError(ChannelHandlerContext ctx,String errorMessage, Throwable cause) {
		return error(ctx, errorMessage)
			.data("error", cause.getClass().getSimpleName())
			.data("detail", cause.getMessage())
			.send();
	}
    
    
    
	
}
