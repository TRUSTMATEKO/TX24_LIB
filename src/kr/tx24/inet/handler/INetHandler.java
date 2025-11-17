package kr.tx24.inet.handler;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import kr.tx24.inet.route.RouteInvoker;
import kr.tx24.inet.route.Router;
import kr.tx24.inet.util.INetRespUtils;
import kr.tx24.inet.util.INetUtils;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.IDUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * INet 프로토콜 핸들러
 */
public class INetHandler extends SimpleChannelInboundHandler<INet> {
    
    private static final Logger logger = LoggerFactory.getLogger(INetHandler.class);
    
    private static final JacksonUtils jsonUtils = new JacksonUtils();
    
    private long startTime = 0L;
    
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        startTime = System.nanoTime();
        if(SystemUtils.deepview()) {
        	logger.info("channel active: {}", ctx.channel().id().asShortText());
        }
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, INet inet) throws Exception {
    	String extTrxId = System.currentTimeMillis()+ctx.channel().id().asShortText();// 트랜잭션 ID 생성
        inet.head().put(INetUtils.EXT_TRX_ID, extTrxId);
        MDC.put("id", extTrxId);													// MDC에 트랜잭션 ID 설정
        
        
        try {
            String target = inet.head().getString("target");
            logger.info("target: {}", target);

            RouteInvoker invoker = Router.getRoute(target);							// 라우트 조회 (RouteInvoker 반환)
            
            if (invoker == null) {
                logger.warn("Route not found: {}", target);
                sendNotFoundError(ctx, target);
                return;
            }

            processRequest(ctx, invoker, inet, extTrxId);							// 요청 처리
            
        } catch (Exception e) {
            logger.error("Unexpected error processing request", e);
            sendInternalError(ctx, e);
        } finally {
            MDC.remove("id");
        }
    }

    private void processRequest(ChannelHandlerContext ctx, RouteInvoker invoker,INet inet, String extTrxId) {
    	
		INet resInet = new INet()													// 기본 응답 객체 생성
				.head("id", ctx.channel().id().asShortText())
				.head("result", true)
				.head("message", "successful");
		
		try {
			logRequest(invoker, inet);
		
			Object returnObj = null;
			
			try {
				returnObj = invoker.invoke(ctx, inet);								// RouteInvoker로 메서드 호출
			} catch (Exception e) {
				handleInvokeException(ctx, e, extTrxId);
				returnObj = resInet;
			}
		
			// Void 타입이 아닌 경우에만 응답 처리
			if (!invoker.getMethod().getReturnType().equals(Void.TYPE)) {			// Void 타입이 아닌 경우에만 응답 전송
				sendResponse(ctx, returnObj, invoker, extTrxId);
			}
		
		} catch (Exception e) {
			logger.error("Error processing request", e);
			sendInternalError(ctx, e);
		}
	}

    /**
     * invoke 중 발생한 예외를 처리합니다.
     */
    private void handleInvokeException(ChannelHandlerContext ctx,Exception e, String extTrxId) {
        Throwable cause = e.getCause();
        if (cause != null) {
            logger.error("Business logic exception: {}", cause.getMessage(), cause);
            
            String errorDetail = cause.getMessage();
            if (cause.getStackTrace().length > 0) {
                errorDetail += " at " + cause.getStackTrace()[0].toString();
            }
            
            INetRespUtils.error(ctx, "서버 내부 오류가 발생하였습니다.")
            .data("response", errorDetail)
            .data(INetUtils.EXT_TRX_ID, extTrxId)
            .data("exceptionType", cause.getClass().getSimpleName())
            .delayBeforeClose(100L)
            .send();
        } else {
            logger.error("Unexpected exception during invoke", e);
            throw new RuntimeException(e);
        }
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
            @SuppressWarnings("unchecked")
            Map<String, Object> castedMap = (Map<String, Object>) map;
            responseUtils.data(castedMap);
            
        } else {
            // 일반 객체는 JSON으로 변환
            responseUtils.dataFromJson("response", returnObj);
        }
    }

    /**
     * 응답을 전송합니다.
     */
    private void sendResponse(ChannelHandlerContext ctx, Object returnObj,RouteInvoker invoker, String extTrxId) {

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
			   double elapsedMs = (System.nanoTime() - startTime) / 1e6d;
			   logger.info("response sent, elapsed: {}ms", elapsedMs);
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
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        StringBuilder sb = new StringBuilder();									// 스택 트레이스 로깅
        StackTraceElement[] trace = cause.getStackTrace();

        if (trace != null && trace.length > 0) {
            int depth = Math.min(trace.length, 40);
            for (int i = 0; i < depth; i++) {
                sb.append(trace[i].toString()).append(System.lineSeparator());
            }
        }

        logger.error("Exception caught in channel: {}\n{}", cause.getMessage(), sb.toString());
        sendError(ctx, "내부 시스템 오류: " + cause.getMessage());
    }

    /**
     * 에러 응답 전송
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        INet errorInet = new INet()
                .head("result", false)
                .head("message", message);

        ctx.writeAndFlush(errorInet).addListener((ChannelFutureListener) future -> {
            double elapsedMs = (System.nanoTime() - startTime) / 1e6d;
            logger.info("Error response sent - elapsed: %.3fms", elapsedMs);
            future.channel().close();
        });
    }
    
    
    private void logRequest(RouteInvoker invoker, INet inet) {
        if (invoker.isLoggable() || SystemUtils.deepview()) {
            StringBuilder sb =new StringBuilder()
            .append("\nrequest \n")
            .append("head : ").append(jsonUtils.toJson(inet.head()))
            .append("\n")
            .append("data : ").append(jsonUtils.toJson(inet.data()));
            logger.info(sb.toString());
            
            
        }
    }
    
    
    private void sendNotFoundError(ChannelHandlerContext ctx, String target) {
        INetRespUtils.error(ctx, "Target not found: " + target)
            .data("target", target)
            .data("errorType", "ROUTE_NOT_FOUND")
            .delayBeforeClose(100L)	//100ms
            .send()
            .addListener(future -> {
                double elapsedMs = (System.nanoTime() - startTime) / 1e6d;
                logger.info("Error response sent (Route Not Found) - elapsed: {:.3f}ms", 
                           elapsedMs);
            });
    }
    
    private void sendInternalError(ChannelHandlerContext ctx, Exception e) {
        String errorMessage = e.getMessage() != null ? 
                             e.getMessage() : "Unknown error occurred";
        
        INetRespUtils.error(ctx, "내부 시스템 오류: " + errorMessage)
            .data("errorType", e.getClass().getSimpleName())
            .data("errorMessage", errorMessage)
            .delayBeforeClose(100L)
            .send()
            .addListener(future -> {
                double elapsedMs = (System.nanoTime() - startTime) / 1e6d;
                logger.info("Error response sent (Internal Error) - elapsed: {:.3f}ms", 
                           elapsedMs);
            });
    }
    
    

}