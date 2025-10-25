package kr.tx24.lib.netty;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;
import kr.tx24.api.util.ApiConstants;

/**
 * Netty Utility 클래스
 * <p>
 * Netty Channel 및 HTTP Request 처리에 유용한 다양한 유틸리티 메서드를 제공합니다.
 * - 원격 IP 조회
 * - HTTP 헤더/쿼리 파라미터 변환
 * - Channel Attribute 처리
 * - 이벤트 루프 지연(sleep) 처리
 */
public class NettyUtils {

    private NettyUtils() {
    }

    /**
     * 채널로부터 원격 IP 주소를 가져옵니다.
     *
     * @param channel Netty Channel
     * @return IPv4 또는 IPv6 형식의 원격 IP 문자열, 없으면 빈 문자열
     */
    public static String getRemoteAddress(Channel channel) {
        if (channel == null) return "";
        SocketAddress remoteSocketAddr = channel.remoteAddress();
        if (remoteSocketAddr instanceof InetSocketAddress inetSocketAddress) {
            InetAddress address = inetSocketAddress.getAddress();
            if (address instanceof Inet6Address || address instanceof Inet4Address) {
                return address.getHostAddress();
            }
        }
        return "";
    }

    /**
     * FullHttpRequest로부터 특정 헤더 값을 가져옵니다.
     *
     * @param request    FullHttpRequest 객체
     * @param headerName 조회할 헤더 이름
     * @return 헤더 값, 없으면 null
     */
    public static String getHeaderString(FullHttpRequest request, String headerName) {
        if (request == null || headerName == null) return null;
        return request.headers().get(headerName);
    }

    /**
     * FullHttpRequest의 쿼리 파라미터를 Map<String, List<String>> 형태로 반환합니다.
     *
     * @param request FullHttpRequest 객체
     * @return 쿼리 파라미터 Map, 없으면 빈 Map
     */
    public static Map<String, List<String>> getQueryParameters(FullHttpRequest request) {
        if (request == null) return Map.of();
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        return decoder.parameters();
    }

    /**
     * FullHttpRequest의 쿼리 파라미터를 Map<String, String> 형태로 반환합니다.
     * 각 파라미터는 첫 번째 값을 사용합니다.
     *
     * @param request FullHttpRequest 객체
     * @return 단일 값 쿼리 파라미터 Map
     */
    public static Map<String, String> getQueryParameterSingle(FullHttpRequest request) {
        return getQueryParameters(request).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().isEmpty() ? "" : e.getValue().get(0)));
    }

    /**
     * 채널에 Attribute를 저장합니다.
     *
     * @param channel Netty Channel
     * @param key     Attribute 키
     * @param value   저장할 값
     * @param <T>     값 타입
     */
    public static <T> void setAttribute(Channel channel, String key, T value) {
        if (channel == null || key == null) return;
        channel.attr(AttributeKey.valueOf(key)).set(value);
    }

    /**
     * 채널로부터 Attribute를 가져옵니다.
     *
     * @param channel Netty Channel
     * @param key     Attribute 키
     * @param <T>     반환 타입
     * @return Attribute 값, 없으면 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAttribute(Channel channel, String key) {
        if (channel == null || key == null) return null;
        return (T) channel.attr(AttributeKey.valueOf(key)).get();
    }

    /**
     * ChannelHandlerContext로부터 원격 IP 주소를 가져옵니다.
     *
     * @param ctx ChannelHandlerContext
     * @return 원격 IP 문자열
     */
    public static String getRemoteAddress(ChannelHandlerContext ctx) {
        if (ctx == null) return "";
        return getRemoteAddress(ctx.channel());
    }

    /**
     * FullHttpRequest의 HTTP 헤더를 Map<String, String>으로 변환합니다.
     *
     * @param request FullHttpRequest 객체
     * @return HTTP 헤더 Map
     */
    public static Map<String, String> headersToMap(FullHttpRequest request) {
        if (request == null) return Map.of();
        HttpHeaders headers = request.headers();
        return headers.entries().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * ChannelHandlerContext와 FullHttpRequest를 통해 클라이언트 실제 IP를 가져옵니다.
     *
     * @param ctx     ChannelHandlerContext
     * @param request FullHttpRequest
     * @return 클라이언트 IP 문자열
     */
    public static String getClientIp(ChannelHandlerContext ctx, FullHttpRequest request) {
        return getClientIp(ctx.channel(), request);
    }

    /**
     * Channel과 FullHttpRequest를 통해 클라이언트 실제 IP를 가져옵니다.
     * <p>
     * 우선순위:
     * 1. X-Forwarded-For 헤더 (쉼표로 구분된 첫 번째 IP)
     * 2. X-Real-IP 헤더
     * 3. 채널 원격 주소
     *
     * @param channel Netty Channel
     * @param request FullHttpRequest
     * @return 클라이언트 IP 문자열
     */
    public static String getClientIp(Channel channel, FullHttpRequest request) {

        // X-Forwarded-For 헤더 체크
        String forwardedFor = request.headers().get(ApiConstants.X_FORWARDED_FOR);
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }

        // X-Real-IP 헤더 체크
        String xRealIp = request.headers().get(ApiConstants.X_REAL_IP);
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp.trim();
        }

        // 채널 원격 주소 반환
        return getRemoteAddress(channel);
    }
    
    
    /**
     * 이벤트 루프를 블록하지 않고 지정 시간 후 Runnable 실행
     * NettyUtils.sleep(ctx, 1000, () -> {
     *      System.out.println("1초 후 작업 실행");
     * });
     * @param ctx    ChannelHandlerContext
     * @param millis 지연 시간 (밀리초)
     * @param task   지연 후 실행할 작업
     */
    public static void sleep(ChannelHandlerContext ctx, long millis, Runnable task) {
        ctx.executor().schedule(task, millis, TimeUnit.MILLISECONDS);
    }
    
    


    
}

