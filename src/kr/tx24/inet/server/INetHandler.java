package kr.tx24.inet.server;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import kr.tx24.inet.mapper.RouteInvoker;
import kr.tx24.inet.mapper.Router;
import kr.tx24.inet.util.INetUtils;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * INet 프로토콜 핸들러
 * 
 * 중요: @Sharable 어노테이션이 없으므로 각 클라이언트 연결마다 새 인스턴스가 생성됩니다.
 * 따라서 startTime과 같은 인스턴스 변수는 스레드 안전합니다.
 */
public class INetHandler extends SimpleChannelInboundHandler<INet> {
    
    private static final Logger logger = LoggerFactory.getLogger(INetHandler.class);
    
    private static final ThreadLocal<JacksonUtils> jsonUtils = 	ThreadLocal.withInitial(JacksonUtils::new);
    
    private long startTime = 0L;
    
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        startTime = System.nanoTime();
        logger.debug("Channel active: {}", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, INet inet) throws Exception {
    	String extTrxId = INetUtils.getTrxId();										// 트랜잭션 ID 생성
        inet.data(INetUtils.INET_TRX_ID, extTrxId);
        
        if (!inet.head().containsKey(INetUtils.INET_TRX_ID)) {
            inet.head().put(INetUtils.INET_TRX_ID, extTrxId);
        }

        MDC.put("id", extTrxId);													// MDC에 트랜잭션 ID 설정
        
        
        try {
            String target = inet.head().getString("target");
            logger.info("Processing request - target: {}", target);

            RouteInvoker invoker = Router.getRoute(target);							// 라우트 조회 (RouteInvoker 반환)
            
            if (invoker == null) {
                logger.warn("Route not found: {}", target);
                sendError(ctx, "Target not found: " + target);
                return;
            }

            processRequest(ctx, invoker, inet, extTrxId);							// 요청 처리
            
        } catch (Exception e) {
            logger.error("Unexpected error processing request", e);
            sendError(ctx, "내부 시스템 오류: " + e.getMessage());
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
			if (invoker.isLoggable() || SystemUtils.deepview()) {					// 요청 로깅
				JacksonUtils json = jsonUtils.get();
				logger.info("Request head:\n{}", json.toJson(inet.head()));
				logger.info("Request data:\n{}", json.toJson(inet.data()));
			}
		
			Object returnObj = null;
			
			try {
				returnObj = invoker.invoke(ctx, inet);								// RouteInvoker로 메서드 호출
			} catch (Exception e) {
				handleInvokeException(e, resInet);
				returnObj = resInet;
			}
		
			if (!invoker.getMethod().getReturnType().equals(Void.TYPE)) {			// Void 타입이 아닌 경우에만 응답 전송
				prepareResponse(resInet, returnObj);
				sendResponse(ctx, resInet, invoker, extTrxId);
			}
		
		} catch (Exception e) {
			logger.error("Error processing request", e);
			sendError(ctx, "내부 시스템 오류: " + e.getMessage());
		}
	}

    /**
     * invoke 중 발생한 예외를 처리합니다.
     */
    private void handleInvokeException(Exception e, INet resInet) {
        Throwable cause = e.getCause();
        if (cause != null) {
            logger.error("Business logic exception: {}", cause.getMessage(), cause);
            
            String errorDetail = cause.getMessage();
            if (cause.getStackTrace().length > 0) {
                errorDetail += " at " + cause.getStackTrace()[0].toString();
            }
            
            resInet.head("result", false)
                   .head("message", "서버 내부 오류가 발생하였습니다.")
                   .data("response", errorDetail);
        } else {
            logger.error("Unexpected exception during invoke", e);
            throw new RuntimeException(e);
        }
    }


    /**
     * 반환 객체를 기반으로 응답을 준비합니다.
     */
    private void prepareResponse(INet resInet, Object returnObj) {
        if (returnObj == null) {
            return;
        }

        if (returnObj instanceof String) {
            resInet.data("response", returnObj);
        } else if (returnObj instanceof INet inet) {							
            resInet.head().putAll(inet.head());
            resInet.data().putAll(inet.data());
        } else if (returnObj instanceof Map<?, ?> map) {						
            @SuppressWarnings("unchecked")
            Map<String, Object> castedMap = (Map<String, Object>) map;
            resInet.data(castedMap);
        } else {
            resInet.data("response", jsonUtils.get().pretty().toJson(returnObj));
        }
    }

    /**
     * 응답을 전송합니다.
     */
    private void sendResponse(ChannelHandlerContext ctx, INet resInet,RouteInvoker invoker, String extTrxId) {

		if (invoker.isLoggable() || SystemUtils.deepview()) {						// 응답 로깅
			JacksonUtils json = jsonUtils.get().pretty();
			logger.info("Response head:\n{}", json.toJson(resInet.head()));
			logger.info("Response data:\n{}", json.toJson(resInet.data()));
		}
		
		logger.info("Response - result: {}, message: {}",resInet.head().getString("result"),resInet.head().getString("message"));
		
		resInet.data("extTrxId", extTrxId);
		
		ctx.writeAndFlush(resInet).addListener((ChannelFutureListener) future -> {	// 응답 전송 및 연결 종료
			if (future.isSuccess()) {
				double elapsedMs = (System.nanoTime() - startTime) / 1e6d;
				logger.info("Response sent successfully - elapsed: %.3fms", elapsedMs);
			} else {
				logger.error("Failed to send response", future.cause());
			}
		
			try {																	// 클라이언트에서 먼저 끊는 것 방지
				Thread.sleep(100);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		
			future.channel().close();
		});
    }
    
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("Channel inactive: {}", ctx.channel().id().asShortText());
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
    
    

}