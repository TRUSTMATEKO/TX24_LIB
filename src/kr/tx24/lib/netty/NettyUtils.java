package kr.tx24.lib.netty;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.util.AttributeKey;

/**
 * Netty Utility 클래스
 */
public class NettyUtils {

    private NettyUtils() {
        // 유틸 클래스이므로 인스턴스화 방지
    }

    /**
     * 채널로부터 원격 IP 주소를 가져온다.
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
     * FullHttpRequest로부터 특정 헤더 값을 가져온다.
     */
    public static String getHeaderString(FullHttpRequest request, String headerName) {
        if (request == null || headerName == null) return null;
        return request.headers().get(headerName);
    }

    /**
     * FullHttpRequest로부터 쿼리 파라미터를 Map<String, List<String>> 형태로 반환
     */
    public static Map<String, List<String>> getQueryParameters(FullHttpRequest request) {
        if (request == null) return Map.of();
        QueryStringDecoder decoder = new QueryStringDecoder(request.uri());
        return decoder.parameters();
    }

    /**
     * FullHttpRequest로부터 쿼리 파라미터를 단일 값 Map<String, String> 형태로 반환 (첫 번째 값 사용)
     */
    public static Map<String, String> getQueryParameterSingle(FullHttpRequest request) {
        return getQueryParameters(request).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().isEmpty() ? "" : e.getValue().get(0)));
    }

    /**
     * 채널에 Attribute를 저장한다.
     */
    public static <T> void setAttribute(Channel channel, String key, T value) {
        if (channel == null || key == null) return;
        channel.attr(AttributeKey.valueOf(key)).set(value);
    }

    /**
     * 채널로부터 Attribute를 가져온다.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAttribute(Channel channel, String key) {
        if (channel == null || key == null) return null;
        return (T) channel.attr(AttributeKey.valueOf(key)).get();
    }

    /**
     * ChannelHandlerContext로부터 원격 IP를 가져온다.
     */
    public static String getRemoteAddress(ChannelHandlerContext ctx) {
        if (ctx == null) return "";
        return getRemoteAddress(ctx.channel());
    }

    /**
     * HTTP 헤더 전체를 Map<String, String>으로 변환
     */
    public static Map<String, String> headersToMap(FullHttpRequest request) {
        if (request == null) return Map.of();
        HttpHeaders headers = request.headers();
        return headers.entries().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * X-Forwarded-For 또는 Remote Address를 통해 클라이언트 실제 IP를 가져온다.
     */
    public static String getClientIp(FullHttpRequest request, Channel channel) {
        if (request != null) {
            String ip = getHeaderString(request, "X-Forwarded-For");
            if (ip != null && !ip.isEmpty()) {
                return ip.split(",")[0].trim(); // 여러 IP 중 첫 번째
            }
        }
        return getRemoteAddress(channel);
    }
}
