package kr.tx24.api.handler;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import kr.tx24.api.util.ApiUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;

public class HealthCheckHandler {
    
private static final Logger logger = LoggerFactory.getLogger(HealthCheckHandler.class);
    
    /**
     * Health Check 경로 정의
     * 
     * - 기본: /health, /health-check, /healthcheck
     * - Kubernetes: /healthz, /readyz, /livez
     * - 간단: /ping, /status
     * - 세부: /health/live, /health/ready
     * - 내부: /_health, /_status
     */
    public static final Set<String> HEALTH_CHECK_PATHS = Set.of(
        "/health",
        "/health-check",
        "/healthcheck",
        "/healthz",
        "/readyz",
        "/livez",
        "/ping",
        "/status",
        "/health/live",
        "/health/ready",
        "/_health",
        "/_status"
    );
    
    /**
     * Health Check 요청 처리
     * 
     * @param ctx Netty 채널 컨텍스트
     * @param request HTTP 요청
     */
    public static void handleHealthCheck(ChannelHandlerContext ctx, FullHttpRequest request) {
        String path = ApiUtils.getExtractPath(request.uri());
        
        
        // 경로별 응답 생성
        FullHttpResponse response = createHealthCheckResponse(path);
        
        // 응답 전송 및 연결 종료
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        
        // 디버그 로그
        if (SystemUtils.deepview()) {
            logger.debug("Health check: {} -> {}", path, response.status().code());
        }
    }
    
    
    
    
    
    /**
     * 경로별 Health Check 응답 생성
     */
    private static FullHttpResponse createHealthCheckResponse(String path) {
        LinkedMap<String, Object> responseMap;
        String contentType;
        boolean isJson;
        
        switch (path) {
            // ========== 기본 health check (상세 JSON) ==========
            case "/health":
            case "/health-check":
            case "/healthcheck":
                responseMap = createDetailedHealthMap();
                contentType = "application/json";
                isJson = true;
                break;
            
            // ========== Kubernetes 표준 (Plain text) ==========
            case "/healthz":
                responseMap = createPlainTextMap("ok\n");
                contentType = "text/plain";
                isJson = false;
                break;
            
            case "/readyz":
                responseMap = createPlainTextMap("ready\n");
                contentType = "text/plain";
                isJson = false;
                break;
            
            case "/livez":
                responseMap = createPlainTextMap("alive\n");
                contentType = "text/plain";
                isJson = false;
                break;
            
            // ========== 간단한 체크 ==========
            case "/ping":
                responseMap = createPlainTextMap("pong\n");
                contentType = "text/plain";
                isJson = false;
                break;
            
            case "/status":
                responseMap = createStatusMap();
                contentType = "application/json";
                isJson = true;
                break;
            
            // ========== 세부 health check ==========
            case "/health/live":
                responseMap = createLivenessMap();
                contentType = "application/json";
                isJson = true;
                break;
            
            case "/health/ready":
                responseMap = createReadinessMap();
                contentType = "application/json";
                isJson = true;
                break;
            
            // ========== 내부용 (상세 시스템 정보) ==========
            case "/_health":
                responseMap = createInternalHealthMap();
                contentType = "application/json";
                isJson = true;
                break;
            
            case "/_status":
                responseMap = createInternalStatusMap();
                contentType = "application/json";
                isJson = true;
                break;
            
            // ========== 기본 응답 ==========
            default:
                responseMap = createPlainTextMap("OK\n");
                contentType = "text/plain";
                isJson = false;
                break;
        }
        
        return createHttpResponse(responseMap, contentType, isJson);
    }
    

    /**
     * Plain text 응답용 Map
     */
    private static LinkedMap<String, Object> createPlainTextMap(String text) {
        LinkedMap<String, Object> map = new LinkedMap<>();
        map.put("_text", text);
        return map;
    }
    
    /**
     * 상세 Health 정보
     * 
     * 사용: /health, /health-check, /healthcheck
     */
    private static LinkedMap<String, Object> createDetailedHealthMap() {
        LinkedMap<String, Object> map = new LinkedMap<>();
        map.put("status", "UP");
        map.put("timestamp", Instant.now().toString());
        map.put("service", "api-server");
        map.put("uptime", getUptime());
        
        LinkedMap<String, Object> checks = new LinkedMap<>();
        checks.put("liveness", "UP");
        checks.put("readiness", "UP");
        map.put("checks", checks);
        
        return map;
    }
    
    /**
     * 간단한 Status
     * 
     * 사용: /status
     */
    private static LinkedMap<String, Object> createStatusMap() {
        LinkedMap<String, Object> map = new LinkedMap<>();
        map.put("status", "running");
        map.put("timestamp", Instant.now().toString());
        map.put("uptime", getUptime());
        return map;
    }
    
    /**
     * Liveness 상태
     * 
     * 사용: /health/live
     * 의미: 애플리케이션이 살아있는지 확인 (문제 시 재시작 필요)
     */
    private static LinkedMap<String, Object> createLivenessMap() {
        LinkedMap<String, Object> map = new LinkedMap<>();
        map.put("status", "UP");
        map.put("type", "liveness");
        map.put("timestamp", Instant.now().toString());
        map.put("message", "Application is alive");
        return map;
    }
    
    /**
     * Readiness 상태
     * 
     * 사용: /health/ready
     * 의미: 트래픽을 받을 준비가 되었는지 확인 (의존 서비스 연결 상태 등)
     */
    private static LinkedMap<String, Object> createReadinessMap() {
        boolean isReady = checkReadiness();
        
        LinkedMap<String, Object> map = new LinkedMap<>();
        map.put("status", isReady ? "UP" : "DOWN");
        map.put("type", "readiness");
        map.put("timestamp", Instant.now().toString());
        map.put("ready", isReady);
        map.put("message", isReady ? "Ready to serve traffic" : "Not ready");
        return map;
    }
    
    /**
     * 내부용 상세 Health 정보
     * 
     * 사용: /_health
     * 시스템 리소스 정보 포함 (메모리, CPU 등)
     */
    private static LinkedMap<String, Object> createInternalHealthMap() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        LinkedMap<String, Object> map = new LinkedMap<>();
        map.put("status", "UP");
        map.put("timestamp", Instant.now().toString());
        map.put("service", "api-server");
        map.put("uptime", getUptime());
        
        // 시스템 정보
        LinkedMap<String, Object> system = new LinkedMap<>();
        system.put("processors", runtime.availableProcessors());
        
        LinkedMap<String, Object> memory = new LinkedMap<>();
        memory.put("total", totalMemory);
        memory.put("used", usedMemory);
        memory.put("free", freeMemory);
        memory.put("usage", String.format("%.2f", (double) usedMemory / totalMemory * 100));
        
        system.put("memory", memory);
        map.put("system", system);
        
        // Health Checks
        LinkedMap<String, Object> checks = new LinkedMap<>();
        checks.put("liveness", "UP");
        checks.put("readiness", "UP");
        map.put("checks", checks);
        
        return map;
    }
    
    /**
     * 내부용 Status
     * 
     * 사용: /_status
     * 시스템 상태 정보 포함
     */
    private static LinkedMap<String, Object> createInternalStatusMap() {
        LinkedMap<String, Object> map = new LinkedMap<>();
        map.put("status", "running");
        map.put("timestamp", Instant.now().toString());
        map.put("uptime", getUptime());
        map.put("threads", Thread.activeCount());
        map.put("java_version", System.getProperty("java.version"));
        return map;
    }
    
    // ========== HTTP 응답 생성 ==========
    
    /**
     * HTTP 응답 생성
     */
    private static FullHttpResponse createHttpResponse(
            LinkedMap<String, Object> map, 
            String contentType, 
            boolean isJson) {
        
        String body;
        
        if (isJson) {
            body = map.toJson();
        } else {
            // Plain text는 _text 키의 값 사용
            body = map.containsKey("_text") ? (String) map.get("_text") : "OK\n";
        }
        
        ByteBuf content = Unpooled.copiedBuffer(body, StandardCharsets.UTF_8);
        
        FullHttpResponse response = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.OK,
            content
        );
        
        response.headers()
            .set(HttpHeaderNames.CONTENT_TYPE, contentType + "; charset=UTF-8")
            .setInt(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes())
            .set(HttpHeaderNames.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
            .set(HttpHeaderNames.PRAGMA, "no-cache")
            .set(HttpHeaderNames.EXPIRES, "0")
            .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        
        return response;
    }
    
    /**
     * Readiness 체크 로직
     */
    private static boolean checkReadiness() {
    	 //* - DB 연결 확인
         //* - Redis 연결 확인
        return true;
    }
    
    /**
     * 서버 가동 시간 (초)
     */
    private static long getUptime() {
        return java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
    }
}