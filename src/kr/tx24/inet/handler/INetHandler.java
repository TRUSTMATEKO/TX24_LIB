package kr.tx24.inet.handler;

import java.util.HashMap;
import java.util.Map;

import kr.tx24.lib.netty.NettyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleStateEvent;
import kr.tx24.inet.route.RouteInvoker;
import kr.tx24.inet.route.Router;
import kr.tx24.inet.util.INetRespUtils;
import kr.tx24.inet.util.INetUtils;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * INet 프로토콜 핸들러
 */
public class INetHandler extends SimpleChannelInboundHandler<INet> {
    
    private static final Logger logger = LoggerFactory.getLogger(INetHandler.class);
    
    private static final JacksonUtils jsonUtils = new JacksonUtils();
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if(SystemUtils.deepview()) {
        	logger.info("channel active: {}", ctx.channel().id().asShortText());
        }
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, INet inet) throws Exception {
        long requestStartNanos = System.nanoTime();
    	String extTrxId = System.currentTimeMillis()+ctx.channel().id().asShortText();// 트랜잭션 ID 생성
        inet.head().put(INetUtils.EXT_TRX_ID, extTrxId);
        MDC.put("id", extTrxId);													// MDC에 트랜잭션 ID 설정
        
        
        try {
            String target = inet.head().getString("target");
            logger.info("target: {}", target);

            RouteInvoker invoker = Router.getRoute(target);							// 라우트 조회 (RouteInvoker 반환)
            
            if (invoker == null) {
                logger.warn("Route not found: {}", target);
                sendNotFoundError(ctx, target, requestStartNanos);
                return;
            }

            processRequest(ctx, invoker, inet, extTrxId, requestStartNanos);		// 요청 처리
            
        } catch (Exception e) {
            logger.error("Unexpected error processing request", e);
            sendInternalError(ctx, extTrxId, requestStartNanos);
        } finally {
            MDC.remove("id");
        }
    }

    private void processRequest(ChannelHandlerContext ctx, RouteInvoker invoker, INet inet,
                                String extTrxId, long requestStartNanos) {
		try {
			logRequest(invoker, inet);
		
			Object returnObj;
			try {
				returnObj = invoker.invoke(ctx, inet);								// RouteInvoker로 메서드 호출
			} catch (Exception e) {
				handleInvokeException(ctx, e, extTrxId, requestStartNanos);
				return;
			}
		
			// Void 타입이 아닌 경우에만 응답 처리
			if (!invoker.getMethod().getReturnType().equals(Void.TYPE)) {			// Void 타입이 아닌 경우에만 응답 전송
				sendResponse(ctx, returnObj, invoker, extTrxId, requestStartNanos);
			}
		
		} catch (Exception e) {
			logger.error("Error processing request", e);
			sendInternalError(ctx, extTrxId, requestStartNanos);
		}
	}

    /**
     * invoke 중 발생한 예외를 처리합니다.
     */
    private void handleInvokeException(ChannelHandlerContext ctx, Exception e, String extTrxId,
                                       long requestStartNanos) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        logger.error("Business logic exception", cause);

        sendInternalError(ctx, extTrxId, requestStartNanos);
    }


    /**
     * 반환 객체를 기반으로 응답을 준비합니다.
     */
    private void processReturnObject(INetRespUtils responseUtils, Object returnObj) {
        if (returnObj instanceof String) {
            responseUtils.data("response", returnObj);
            
        } else if (returnObj instanceof INet inet) {
            responseUtils.head(inet.head());
            responseUtils.data(inet.data());
            
        } else if (returnObj instanceof Map<?, ?> map) {
            Map<String, Object> castedMap = new HashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    castedMap.put(
                            CommonUtils.toString(entry.getKey()),
                            entry.getValue()
                    );
                }
            }

            responseUtils.data(castedMap);
            
        } else {
            // 일반 객체는 JSON으로 변환
            responseUtils.dataFromJson("response", returnObj);
        }
    }

    /**
     * 응답을 전송합니다.
     */
    private void sendResponse(ChannelHandlerContext ctx, Object returnObj, RouteInvoker invoker,
                              String extTrxId, long requestStartNanos) {

		INetRespUtils responseUtils = INetRespUtils.success(ctx);
		
		// 기본 정보 설정
		responseUtils
			//.head("result", true)
			//.head("message", "successful")
			.data(INetUtils.EXT_TRX_ID, extTrxId);
		
		// 반환 객체에 따른 처리
		if (returnObj != null) {
			processReturnObject(responseUtils, returnObj);
		}
		
		// 로깅 설정
		if (invoker.isLoggable() || SystemUtils.deepview()) {
			responseUtils.enableLogging();
		}
		
		// 응답 전송
		responseUtils
			.delayBeforeClose(100L)
			.send()
			.addListener(future -> {
			if (future.isSuccess()) {
			   double elapsedMs = elapsedMillis(requestStartNanos);
			   logger.info("response sent, elapsed: {} ms", elapsedMs);
			} else {
			   logger.error("Failed to send response", future.cause());
			}
		});
	}

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	if(SystemUtils.deepview()) {
    		logger.debug("channel inactive: {}", ctx.channel().id().asShortText());
    	}
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof IdleStateEvent idleEvent) {
            logger.warn(
                "Closing idle INet channel after 300 seconds: channel={}, state={}",
                ctx.channel().id().asShortText(),
                idleEvent.state()
            );
            ctx.close();
            return;
        }

        super.userEventTriggered(ctx, event);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        long requestStartNanos = System.nanoTime();
        logger.error("Exception caught in channel", cause);
        sendError(ctx, "서버 내부 오류가 발생하였습니다.", requestStartNanos);
    }

    /**
     * 에러 응답 전송
     */
    private void sendError(ChannelHandlerContext ctx, String message, long requestStartNanos) {
        INet errorInet = new INet()
                .head("result", false)
                .head("message", message)
                .data("errorCode", "INTERNAL_SERVER_ERROR");

        ctx.writeAndFlush(errorInet).addListener((ChannelFutureListener) future -> {
            double elapsedMs = elapsedMillis(requestStartNanos);
            logger.info("Error response sent - elapsed: {} ms", elapsedMs);
            future.channel().close();
        });
    }
    
    
    private void logRequest(RouteInvoker invoker, INet inet) {
        if (invoker.isLoggable() || SystemUtils.deepview()) {
            StringBuilder sb =new StringBuilder()
            .append("\nrequest \n")
            .append("head : ").append(jsonUtils.toJson(inet.head()))
            .append("\n")
            .append("data : ").append(CommonUtils.cut(jsonUtils.toJson(inet.data()),1024));
            logger.info(sb.toString());
            
            
        }
    }
    
    
    private void sendNotFoundError(ChannelHandlerContext ctx, String target,
                                   long requestStartNanos) {
        INetRespUtils.error(ctx, "Target not found: " + target)
            .data("target", target)
            .data("errorType", "ROUTE_NOT_FOUND")
            .delayBeforeClose(100L)	//100ms
            .send()
            .addListener(future -> {
                double elapsedMs = elapsedMillis(requestStartNanos);
                logger.info("Error response sent (Route Not Found) - elapsed: {} ms",
                           elapsedMs);
            });
    }
    
    private void sendInternalError(ChannelHandlerContext ctx, String extTrxId,
                                   long requestStartNanos) {
        INetRespUtils.error(ctx, "서버 내부 오류가 발생하였습니다.")
            .data("errorCode", "INTERNAL_SERVER_ERROR")
            .data(INetUtils.EXT_TRX_ID, extTrxId)
            .delayBeforeClose(100L)
            .send()
            .addListener(future -> {
                double elapsedMs = elapsedMillis(requestStartNanos);
                logger.info("Error response sent (Internal Error) - elapsed: {} ms",
                           elapsedMs);
            });
    }

    private double elapsedMillis(long requestStartNanos) {
        return (System.nanoTime() - requestStartNanos) / 1e6d;
    }
    
    

}
