package kr.tx24.api.handler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.compression.CompressionException;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import kr.tx24.api.conf.ApiConfigLoader;
import kr.tx24.api.conf.DenyConfig;
import kr.tx24.api.util.ApiConstants;
import kr.tx24.api.util.ApiUtils;
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.lib.netty.NettyUtils;

/**
 * API 요청 필터 핸들러
 * 
 * 처리 우선순위:
 * 1. Health Check
 * 2. 디코딩 검증 (잘못된 요청 조기 차단)
 * 3. IP 차단 (보안 최우선)
 * 4. URI 검증 (악의적 경로 차단)
 * 5. Content-Length 제한 (대용량 요청 차단)
 * 6. Content-Type 검증 (비즈니스 로직 전 검증)
 * 
 */
public class FilterHandler extends ChannelInboundHandlerAdapter {
    
    private static final Logger logger = LoggerFactory.getLogger(FilterHandler.class);
    
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        MDC.put("channelId", ctx.channel().id().asShortText());
        ctx.channel().attr(ApiConstants.KEY_START).set(System.currentTimeMillis());
        super.channelActive(ctx);
    }
    
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!(msg instanceof FullHttpRequest)) {
            ctx.fireChannelRead(msg);
            return;
        }
        
        FullHttpRequest request = (FullHttpRequest) msg;
        
        try {
            String remoteIp = NettyUtils.getClientIp(ctx.channel(), request);
            String path = ApiUtils.getExtractPath(request.uri());
            
            // 로깅 정보 설정
            setupLogging(ctx, request, remoteIp);
            
            // 1: Health Check (가장 빠른 응답)
            if (isHealthCheckRequest(request, path)) {
                handleHealthCheck(ctx, request);
                return;
            }
            
            // 2: 디코딩 검증 (잘못된 요청 조기 차단)
            if (isDecodingFailed(ctx, request)) return;
            
          
            // 3: IP 차단 (보안 최우선)
            if (isDeniedIp(remoteIp)) {
                logger.info("Denied IP: {}", remoteIp);
                sendError(ctx, request, HttpResponseStatus.FORBIDDEN, "Access denied");
                return;
            }
            
            // 4: URI 검증 (악의적 경로 차단)
            if (isInvalidUri(request.uri())) {
                logger.info("Invalid URI: {}", request.uri());
                sendError(ctx, request, HttpResponseStatus.NOT_FOUND, "Resource not found");
                return;
            }
            
            // 5: Content-Length 제한 (대용량 요청 차단)
            long contentLength = getContentLength(request);
            if (contentLength > ApiConstants.HUGE_LIMIT) {
                logger.info("Request too large: {} bytes", contentLength);
                sendError(ctx, request, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "Request body too large");
                return;
            }
            
            // 6: Content-Type 검증
            if (isInvalidContentType(request)) {
                logger.warn("Invalid Content-Type : {}", request.headers().get(HttpHeaderNames.CONTENT_TYPE));
                sendError(ctx, request, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
                return;
            }
            
            ctx.fireChannelRead(request.retain());
            
        } catch (Exception e) {
            logger.warn("Filter error: {}", e.getMessage(), e);
            sendError(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, "Internal server error");
        } finally {
            request.release();
        }
    }
    
    /**
     * 로깅 정보 설정
     */
    private void setupLogging(ChannelHandlerContext ctx, FullHttpRequest request, String remoteIp) {
        long contentLength = getContentLength(request);
        String logInfo = MsgUtils.format("{},{},{},{}", 
            remoteIp, contentLength, request.method().toString().toLowerCase(), request.uri());
        ctx.channel().attr(ApiConstants.KEY_LOG).set(logInfo);
    }
    
    /**
     * Health Check 요청 확인
     */
    private boolean isHealthCheckRequest(FullHttpRequest request, String path) {
        return HealthCheckHandler.HEALTH_CHECK_PATHS.contains(path) && 
               (HttpMethod.HEAD.equals(request.method()) || HttpMethod.GET.equals(request.method()));
    }
    
    /**
     * Health Check 처리
     */
    private void handleHealthCheck(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (HttpMethod.HEAD.equals(request.method())) {
            sendHeadResponse(ctx);
        } else {
            HealthCheckHandler.handleHealthCheck(ctx, request);
        }
    }
    
    /**
     * 디코딩 실패 검증
     */
    private boolean isDecodingFailed(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.decoderResult().isSuccess()) {
            return false;
        }
        
        Throwable cause = request.decoderResult().cause();
        HttpResponseStatus status = HttpResponseStatus.BAD_REQUEST;
        String message = "Malformed request";
        
        if (cause instanceof io.netty.handler.codec.TooLongFrameException) {
            status = HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
            message = "Request entity too large";
        } else if (cause instanceof CompressionException) {
            message = "Invalid content encoding";
        } else if (cause != null && cause.getMessage() != null && cause.getMessage().contains("timeout")) {
            status = HttpResponseStatus.REQUEST_TIMEOUT;
            message = "Request timeout";
        }
        
        logger.warn("Decoding failed: {}, cause: {}", message, cause != null ? cause.getClass().getSimpleName() : "unknown");
        sendError(ctx, request, status, message);
        return true;
    }
    
    /**
     * Content-Length 추출
     */
    private long getContentLength(FullHttpRequest request) {
        String value = request.headers().get(HttpHeaderNames.CONTENT_LENGTH);
        if (value == null || value.isEmpty()) {
            return request.content().readableBytes();
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
    
    /**
     * 유효하지 않은 URI 검증
     */
    private boolean isInvalidUri(String uri) {
        if (uri == null || uri.isEmpty()) return true;
        
        if (uri.indexOf("..") != -1 || uri.indexOf("//") != -1) return true;
        
        // 확장자 기반으로 
        int lastDot = uri.lastIndexOf('.');
        if (lastDot != -1 && lastDot < uri.length() - 1) {
            int queryStart = uri.indexOf('?', lastDot);
            String extension = queryStart != -1 
                ? uri.substring(lastDot, queryStart).toLowerCase()
                : uri.substring(lastDot).toLowerCase();
            
            if (ApiConstants.DENIED_EXTENSIONS.contains(extension)) return true;
        }
        
        // Uri 기반으로 
        List<String> deniedUris = ApiConfigLoader.get().deny.urls;
        if (deniedUris == null || deniedUris.isEmpty()) {
            return false;
        }
        
        // 쿼리스트링 제거 (경로만 추출)
        String path = uri;
        int queryIndex = uri.indexOf('?');
        if (queryIndex != -1) {
            path = uri.substring(0, queryIndex);
        }
        
        String lowerPath = path.toLowerCase();
        
        for (String deniedUri : deniedUris) {
            if (deniedUri == null || deniedUri.isEmpty()) continue;
            
            String lowerDenied = deniedUri.toLowerCase();
            
            if (lowerPath.startsWith(lowerDenied)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * IP 차단 여부 확인
     */
    private boolean isDeniedIp(String remoteIp) {
        if (remoteIp == null || remoteIp.isEmpty()) return false;
        
        DenyConfig deny = ApiConfigLoader.get().deny;
        if (deny == null) return false;
        
        List<String> denyIps = deny.ips;
        if (denyIps == null || denyIps.isEmpty()) return false;
        
        return denyIps.stream().anyMatch(remoteIp::startsWith);
    }
    
    /**
     * 유효하지 않은 Content-Type 검증
     */
    private boolean isInvalidContentType(FullHttpRequest request) {
        String method = request.method().name();
        
        // GET, DELETE, HEAD는 body가 없으므로 검증 불필요
        if ("GET".equals(method) || "DELETE".equals(method) || "HEAD".equals(method)) {
            return false;
        }
        
        String contentType = request.headers().get(HttpHeaderNames.CONTENT_TYPE);
        if (contentType == null || contentType.isEmpty()) return true;
        
        String lowerContentType = contentType.toLowerCase();
        
        // Set을 사용한 효율적인 검색
        for (String allowedType : ApiConstants.ALLOWED_CONTENT_TYPES) {
            if (lowerContentType.contains(allowedType)) return false;
        }
        return true;
    }
    
    /**
     * HEAD 요청 응답
     */
    private void sendHeadResponse(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
            .setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
            .set(HttpHeaderNames.CACHE_CONTROL, "no-cache")
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    /**
     * 에러 응답 전송
     */
    private void sendError(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String message) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8")
            .setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
            .set(HttpHeaderNames.CONNECTION, 
                HttpUtil.isKeepAlive(request) ? HttpHeaderValues.KEEP_ALIVE : HttpHeaderValues.CLOSE);
        
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.warn("Exception in filter: {}", cause.getMessage(), cause);
        ctx.close();
    }
}