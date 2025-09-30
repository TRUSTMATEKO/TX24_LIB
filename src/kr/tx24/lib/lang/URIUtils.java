package kr.tx24.lib.lang;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.map.SharedMap;

public class URIUtils {

    private static final Logger logger = LoggerFactory.getLogger(URIUtils.class);
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    public static String getScheme(String url) {
        URI uri = getUri(url);
        return uri != null ? uri.getScheme() : "";
    }

    public static String getHost(String url) {
        URI uri = getUri(url);
        return uri != null ? uri.getHost() : "";
    }

    public static String getRawQuery(String url) {
        URI uri = getUri(url);
        return uri != null ? uri.getRawQuery() : "";
    }

    public static String getQuery(String url) {
        URI uri = getUri(url);
        return uri != null ? uri.getQuery() : "";
    }

    public static List<String> getRawPaths(String url) {
        String s = getRawPath(url);
        if (s == null || s.isEmpty()) return Collections.emptyList();
        return Arrays.stream(s.split("/"))
                     .filter(seg -> !seg.isEmpty())
                     .collect(Collectors.toList());
    }

    public static String getRawPath(String url) {
        URI uri = getUri(url);
        return uri != null ? uri.getRawPath() : "";
    }

    public static int getPort(String url) {
        URI uri = getUri(url);
        return uri != null ? (uri.getPort() != -1 ? uri.getPort() : 80) : 80;
    }

    public static URI getUri(String url) {
        try {
            return new URI(url);
        } catch (Exception e) {
            logger.warn("Invalid URI: {}", url);
            return null;
        }
    }

    public static Map<String, String> parseQuery(String url, String... charsetNames) {
        URI uri = getUri(url);
        if (uri == null || uri.getQuery() == null) return Collections.emptyMap();
        return parseQueryString(uri.getQuery(), charsetNames);
    }

    public static SharedMap<String, String> parseQueryString(String query, String... charsetNames) {
        SharedMap<String, String> map = new SharedMap<>();
        if (query == null || query.isEmpty()) return map;

        Charset charset = charsetNames.length > 0 ? Charset.forName(charsetNames[0]) : DEFAULT_CHARSET;

        Arrays.stream(query.split("&"))
              .forEach(token -> {
                  int idx = token.indexOf('=');
                  if (idx > 0) {
                      map.put(decode(token.substring(0, idx), charset), decode(token.substring(idx + 1), charset));
                  } else {
                      map.put(decode(token, charset), null);
                  }
              });

        return map;
    }

    public static <M extends Map<String, String>> String toQueryString(M map, String... charsetNames) {
        if (map == null || map.isEmpty()) return "";
        Charset charset = charsetNames.length > 0 ? Charset.forName(charsetNames[0]) : DEFAULT_CHARSET;

        return map.entrySet()
                  .stream()
                  .map(e -> encode(e.getKey(), charset) + "=" + encode(e.getValue(), charset))
                  .collect(Collectors.joining("&"));
    }

    public static String decode(String value, String... charsetNames) {
        Charset charset = charsetNames.length > 0 ? Charset.forName(charsetNames[0]) : DEFAULT_CHARSET;
        return decode(value, charset);
    }

    public static String decode(String value, Charset charset) {
        if (value == null) return null;
        try {
            return URLDecoder.decode(value, charset.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    public static String encode(String value, String... charsetNames) {
        Charset charset = charsetNames.length > 0 ? Charset.forName(charsetNames[0]) : DEFAULT_CHARSET;
        return encode(value, charset);
    }

    public static String encode(String value, Charset charset) {
        if (value == null) return null;
        try {
            return URLEncoder.encode(value, charset.name());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
