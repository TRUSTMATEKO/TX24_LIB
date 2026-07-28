package kr.tx24.inet.handler;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

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
 * {@link INetHandler}의 전체 요청 처리 기능과 bounded business executor 기반
 * backpressure를 함께 제공하는 독립 INet handler.
 */
public class INetHandlerV2 extends SimpleChannelInboundHandler<INet> {

    private static final Logger logger = LoggerFactory.getLogger(INetHandlerV2.class);
    private static final JacksonUtils jsonUtils = new JacksonUtils();
    private static final AtomicLong rejectedTaskCount = new AtomicLong();

    private final ThreadPoolExecutor businessExecutor;
    private final long slowRequestThresholdMillis;

    public INetHandlerV2(
            ThreadPoolExecutor businessExecutor,
            long slowRequestThresholdMillis) {

        if (businessExecutor == null) {
            throw new IllegalArgumentException("businessExecutor must not be null");
        }
        if (slowRequestThresholdMillis <= 0) {
            throw new IllegalArgumentException(
                    "slowRequestThresholdMillis must be greater than zero"
            );
        }

        this.businessExecutor = businessExecutor;
        this.slowRequestThresholdMillis = slowRequestThresholdMillis;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (SystemUtils.deepview()) {
            logger.info(
                    "channel active: {}",
                    ctx.channel().id().asShortText()
            );
        }
        super.channelActive(ctx);
    }

    /**
     * Netty I/O thread에서는 executor 제출만 수행한다.
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, INet inet) {
        long receivedNanos = System.nanoTime();
        String target = inet.head().getString("target");
        String extTrxId = createExtTrxId(ctx);
        inet.head().put(INetUtils.EXT_TRX_ID, extTrxId);

        try {
            businessExecutor.execute(() ->
                    processOnBusinessThread(
                            ctx,
                            inet,
                            target,
                            extTrxId,
                            receivedNanos
                    )
            );
        } catch (RejectedExecutionException e) {
            sendServerBusy(
                    ctx,
                    target,
                    extTrxId,
                    receivedNanos
            );
        }
    }

    private void processOnBusinessThread(
            ChannelHandlerContext ctx,
            INet inet,
            String target,
            String extTrxId,
            long receivedNanos) {

        long executionStartNanos = System.nanoTime();
        long queueWaitMillis = elapsedMillis(
                receivedNanos,
                executionStartNanos
        );

        MDC.put("id", extTrxId);
        try {
            logger.info("target: {}", target);

            RouteInvoker invoker = Router.getRoute(target);
            if (invoker == null) {
                logger.warn("Route not found: {}", target);
                sendNotFoundError(ctx, target, receivedNanos);
                return;
            }

            processRequest(
                    ctx,
                    invoker,
                    inet,
                    extTrxId,
                    receivedNanos
            );
        } catch (Exception e) {
            logger.error("Unexpected error processing request", e);
            sendInternalError(ctx, extTrxId, receivedNanos);
        } finally {
            long processingMillis = elapsedMillis(
                    executionStartNanos,
                    System.nanoTime()
            );
            warnIfSlow(
                    target,
                    queueWaitMillis,
                    processingMillis
            );
            MDC.remove("id");
        }
    }

    private void processRequest(
            ChannelHandlerContext ctx,
            RouteInvoker invoker,
            INet inet,
            String extTrxId,
            long requestStartNanos) {

        try {
            logRequest(invoker, inet);

            Object returnObj;
            try {
                returnObj = invoker.invoke(ctx, inet);
            } catch (Exception e) {
                handleInvokeException(
                        ctx,
                        e,
                        extTrxId,
                        requestStartNanos
                );
                return;
            }

            if (!invoker.getMethod().getReturnType().equals(Void.TYPE)) {
                sendResponse(
                        ctx,
                        returnObj,
                        invoker,
                        extTrxId,
                        requestStartNanos
                );
            }
        } catch (Exception e) {
            logger.error("Error processing request", e);
            sendInternalError(ctx, extTrxId, requestStartNanos);
        }
    }

    private void handleInvokeException(
            ChannelHandlerContext ctx,
            Exception exception,
            String extTrxId,
            long requestStartNanos) {

        Throwable cause = exception.getCause() != null
                ? exception.getCause()
                : exception;
        logger.error("Business logic exception", cause);
        sendInternalError(ctx, extTrxId, requestStartNanos);
    }

    private void processReturnObject(
            INetRespUtils responseUtils,
            Object returnObj) {

        if (returnObj instanceof String stringValue) {
            responseUtils.data("response", stringValue);
        } else if (returnObj instanceof INet responseInet) {
            responseUtils.head(responseInet.head());
            responseUtils.data(responseInet.data());
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
            responseUtils.dataFromJson("response", returnObj);
        }
    }

    private void sendResponse(
            ChannelHandlerContext ctx,
            Object returnObj,
            RouteInvoker invoker,
            String extTrxId,
            long requestStartNanos) {

        INetRespUtils responseUtils = INetRespUtils.success(ctx)
                .data(INetUtils.EXT_TRX_ID, extTrxId);

        if (returnObj != null) {
            processReturnObject(responseUtils, returnObj);
        }

        if (invoker.isLoggable() || SystemUtils.deepview()) {
            responseUtils.enableLogging();
        }

        responseUtils
                .delayBeforeClose(100L)
                .send()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info(
                                "response sent, elapsed: {} ms",
                                elapsedMillis(
                                        requestStartNanos,
                                        System.nanoTime()
                                )
                        );
                    } else {
                        logger.error(
                                "Failed to send response",
                                future.cause()
                        );
                    }
                });
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (SystemUtils.deepview()) {
            logger.debug(
                    "channel inactive: {}",
                    ctx.channel().id().asShortText()
            );
        }
        super.channelInactive(ctx);
    }

    @Override
    public void userEventTriggered(
            ChannelHandlerContext ctx,
            Object event) throws Exception {

        if (event instanceof IdleStateEvent idleEvent) {
            logger.warn(
                    "Closing idle INet channel after 300 seconds: "
                            + "channel={}, state={}",
                    ctx.channel().id().asShortText(),
                    idleEvent.state()
            );
            ctx.close();
            return;
        }

        super.userEventTriggered(ctx, event);
    }

    @Override
    public void exceptionCaught(
            ChannelHandlerContext ctx,
            Throwable cause) {

        long requestStartNanos = System.nanoTime();
        logger.error("Exception caught in channel", cause);
        sendError(
                ctx,
                "서버 내부 오류가 발생했습니다.",
                requestStartNanos
        );
    }

    private void sendError(
            ChannelHandlerContext ctx,
            String message,
            long requestStartNanos) {

        INet errorInet = new INet()
                .head("result", false)
                .head("message", message)
                .data("errorCode", "INTERNAL_SERVER_ERROR");

        ctx.writeAndFlush(errorInet)
                .addListener((ChannelFutureListener) future -> {
                    logger.info(
                            "Error response sent - elapsed: {} ms",
                            elapsedMillis(
                                    requestStartNanos,
                                    System.nanoTime()
                            )
                    );
                    future.channel().close();
                });
    }

    private void logRequest(RouteInvoker invoker, INet inet) {
        if (invoker.isLoggable() || SystemUtils.deepview()) {
            StringBuilder message = new StringBuilder()
                    .append("\nrequest \n")
                    .append("head : ")
                    .append(jsonUtils.toJson(inet.head()))
                    .append("\n")
                    .append("data : ")
                    .append(
                            CommonUtils.cut(
                                    jsonUtils.toJson(inet.data()),
                                    1024
                            )
                    );
            logger.info(message.toString());
        }
    }

    private void sendNotFoundError(
            ChannelHandlerContext ctx,
            String target,
            long requestStartNanos) {

        INetRespUtils.error(ctx, "Target not found: " + target)
                .data("target", target)
                .data("errorType", "ROUTE_NOT_FOUND")
                .delayBeforeClose(100L)
                .send()
                .addListener(future -> logger.info(
                        "Error response sent (Route Not Found) - "
                                + "elapsed: {} ms",
                        elapsedMillis(
                                requestStartNanos,
                                System.nanoTime()
                        )
                ));
    }

    private void sendInternalError(
            ChannelHandlerContext ctx,
            String extTrxId,
            long requestStartNanos) {

        INetRespUtils.error(ctx, "서버 내부 오류가 발생했습니다.")
                .data("errorCode", "INTERNAL_SERVER_ERROR")
                .data(INetUtils.EXT_TRX_ID, extTrxId)
                .delayBeforeClose(100L)
                .send()
                .addListener(future -> logger.info(
                        "Error response sent (Internal Error) - "
                                + "elapsed: {} ms",
                        elapsedMillis(
                                requestStartNanos,
                                System.nanoTime()
                        )
                ));
    }

    private void sendServerBusy(
            ChannelHandlerContext ctx,
            String target,
            String extTrxId,
            long receivedNanos) {

        long rejected = rejectedTaskCount.incrementAndGet();
        int pending = businessExecutor.getQueue().size();
        int capacity = pending
                + businessExecutor.getQueue().remainingCapacity();

        logger.warn(
                "INet business executor saturated: target={}, active={}, "
                        + "pending={}/{}, rejected={}",
                target,
                businessExecutor.getActiveCount(),
                pending,
                capacity,
                rejected
        );

        INetRespUtils.error(ctx, "Server is busy. Please retry later.")
                .data("errorCode", "SERVER_BUSY")
                .data(INetUtils.EXT_TRX_ID, extTrxId)
                .data("retryable", true)
                .delayBeforeClose(0L)
                .send()
                .addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info(
                                "SERVER_BUSY response sent: "
                                        + "target={}, elapsedMs={}",
                                target,
                                elapsedMillis(
                                        receivedNanos,
                                        System.nanoTime()
                                )
                        );
                    } else {
                        logger.error(
                                "Failed to send SERVER_BUSY response: "
                                        + "target={}",
                                target,
                                future.cause()
                        );
                    }
                });
    }

    private void warnIfSlow(
            String target,
            long queueWaitMillis,
            long processingMillis) {

        long totalMillis = queueWaitMillis + processingMillis;
        if (totalMillis >= slowRequestThresholdMillis) {
            logger.info(
                    "Slow INet request: target={}, queueWaitMs={}, "
                            + "processingMs={}, totalMs={}, active={}, pending={}",
                    target,
                    queueWaitMillis,
                    processingMillis,
                    totalMillis,
                    businessExecutor.getActiveCount(),
                    businessExecutor.getQueue().size()
            );
        }
    }

    public static long getRejectedTaskCount() {
        return rejectedTaskCount.get();
    }

    private static String createExtTrxId(ChannelHandlerContext ctx) {
        return System.currentTimeMillis()
                + ctx.channel().id().asShortText();
    }

    private static long elapsedMillis(
            long startNanos,
            long endNanos) {

        return TimeUnit.NANOSECONDS.toMillis(
                endNanos - startNanos
        );
    }
}
