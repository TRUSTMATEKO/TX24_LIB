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

import kr.tx24.lib.map.MapFactory;
import kr.tx24.lib.map.TypeRegistry;

/**
 * URI 및 URL 쿼리 문자열 처리 유틸리티
 * 
 * <p>URL과 URI를 모두 지원하며, 쿼리 파라미터 파싱 및 인코딩/디코딩 기능을 제공합니다.</p>
 * 
 * <h3>URL vs URI</h3>
 * <ul>
 *   <li>URL: https://example.com:8080/path?key=value (완전한 주소)</li>
 *   <li>URI: /path?key=value 또는 key=value (상대 경로 또는 쿼리만)</li>
 * </ul>
 * 
 * <h3>MapFactory 통합</h3>
 * <ul>
 *   <li>쿼리 파라미터 개수를 기반으로 최적의 initialCapacity 설정</li>
 *   <li>TypeRegistry를 통한 다양한 Map 타입 지원</li>
 *   <li>불필요한 리사이징을 방지하여 성능 최적화</li>
 * </ul>
 */
public class URIUtils {

    private static final Logger logger = LoggerFactory.getLogger(URIUtils.class);
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    /**
     * URL/URI에서 스킴(프로토콜)을 추출합니다.
     * URI인 경우 빈 문자열 반환
     * 
     * @param url URL 또는 URI 문자열
     * @return 스킴 (http, https 등), URI인 경우 ""
     * 
     * @example
     * <pre>
     * getScheme("https://example.com")  // "https"
     * getScheme("/path?key=value")      // ""
     * getScheme("key=value")            // ""
     * </pre>
     */
    public static String getScheme(String url) {
        URI uri = getUri(url);
        if (uri == null) return "";
        String scheme = uri.getScheme();
        return scheme != null ? scheme : "";
    }

    /**
     * URL/URI에서 호스트를 추출합니다.
     * URI인 경우 빈 문자열 반환
     * 
     * @param url URL 또는 URI 문자열
     * @return 호스트명, URI인 경우 ""
     * 
     * @example
     * <pre>
     * getHost("https://example.com:8080/path")  // "example.com"
     * getHost("/path?key=value")                // ""
     * getHost("key=value")                      // ""
     * </pre>
     */
    public static String getHost(String url) {
        URI uri = getUri(url);
        if (uri == null) return "";
        String host = uri.getHost();
        return host != null ? host : "";
    }

    /**
     * URL/URI에서 인코딩된 쿼리 문자열을 추출합니다.
     * 
     * @param url URL 또는 URI 문자열
     * @return 인코딩된 쿼리 문자열
     * 
     * @example
     * <pre>
     * getRawQuery("https://example.com?name=%ED%99%8D")  // "name=%ED%99%8D"
     * getRawQuery("/path?key=value")                     // "key=value"
     * getRawQuery("key=value")                           // "" (쿼리만 있는 경우)
     * </pre>
     */
    public static String getRawQuery(String url) {
        URI uri = getUri(url);
        if (uri == null) return "";
        String query = uri.getRawQuery();
        return query != null ? query : "";
    }

    /**
     * URL/URI에서 디코딩된 쿼리 문자열을 추출합니다.
     * 
     * @param url URL 또는 URI 문자열
     * @return 디코딩된 쿼리 문자열
     */
    public static String getQuery(String url) {
        URI uri = getUri(url);
        if (uri == null) return "";
        String query = uri.getQuery();
        return query != null ? query : "";
    }

    /**
     * URL/URI에서 경로를 세그먼트 리스트로 추출합니다.
     * 
     * @param url URL 또는 URI 문자열
     * @return 경로 세그먼트 리스트
     * 
     * @example
     * <pre>
     * getRawPaths("https://example.com/api/users/123")  // ["api", "users", "123"]
     * getRawPaths("/api/users")                         // ["api", "users"]
     * getRawPaths("key=value")                          // []
     * </pre>
     */
    public static List<String> getRawPaths(String url) {
        String s = getRawPath(url);
        if (s == null || s.isEmpty()) return Collections.emptyList();
        return Arrays.stream(s.split("/"))
                     .filter(seg -> !seg.isEmpty())
                     .collect(Collectors.toList());
    }

    /**
     * URL/URI에서 인코딩된 경로를 추출합니다.
     * 
     * @param url URL 또는 URI 문자열
     * @return 인코딩된 경로
     */
    public static String getRawPath(String url) {
        URI uri = getUri(url);
        if (uri == null) return "";
        String path = uri.getRawPath();
        return path != null ? path : "";
    }

    /**
     * URL/URI에서 포트를 추출합니다.
     * URI인 경우 기본 포트(80) 반환
     * 
     * @param url URL 또는 URI 문자열
     * @return 포트 번호 (미지정 시 80)
     * 
     * @example
     * <pre>
     * getPort("https://example.com:8080")  // 8080
     * getPort("https://example.com")       // 80
     * getPort("/path")                     // 80
     * </pre>
     */
    public static int getPort(String url) {
        URI uri = getUri(url);
        return uri != null ? (uri.getPort() != -1 ? uri.getPort() : 80) : 80;
    }

    /**
     * 문자열을 URI 객체로 변환합니다.
     * URL과 URI 모두 처리 가능
     * 
     * @param url URL 또는 URI 문자열
     * @return URI 객체 (변환 실패 시 null)
     */
    public static URI getUri(String url) {
        if (url == null || url.isEmpty()) return null;
        
        try {
            return new URI(url);
        } catch (Exception e) {
            logger.debug("URI parsing failed, treating as query string: {}", url);
            return null;
        }
    }

    /**
     * URL, URI 또는 쿼리 문자열을 Map으로 파싱합니다.
     * TypeRegistry의 MAP_ 타입만 지원합니다.
     * 
     * <p>쿼리 파라미터 개수를 예측하여 적절한 initialCapacity로 Map을 생성하므로
     * 불필요한 리사이징을 방지하고 성능을 향상시킵니다.</p>
     *
     * @param <M> Map 타입
     * @param input URL, URI 또는 쿼리 문자열
     * @param type TypeRegistry (MAP_로 시작하는 타입만 유효)
     * @param charsetNames 문자 인코딩 (미지정 시 UTF-8)
     * @return 파싱된 Map<String, Object>
     *
     * @example
     * <pre>
     * // URL 파싱
     * Map<String, Object> map = parse("http://example.com?name=홍길동&age=30", TypeRegistry.MAP_OBJECT);
     * // {name=홍길동, age=30}
     *
     * // SharedMap으로 파싱
     * SharedMap<String, Object> sharedMap = parse("key=value", TypeRegistry.MAP_SHAREDMAP_OBJECT);
     *
     * // LinkedMap으로 파싱 (순서 보장)
     * LinkedMap<String, Object> linkedMap = parse("a=1&b=2", TypeRegistry.MAP_LINKEDMAP_OBJECT);
     * </pre>
     */
    public static <M extends Map<String, Object>> M parse(String input, TypeRegistry type, String... charsetNames) {
        // TypeRegistry 검증
        if (!isValidMapType(type)) {
            logger.warn("Parse to {} is not supported. Only MAP_* types are valid.", type.name());
            type = TypeRegistry.MAP_OBJECT; // fallback
        }
        
        if (input == null || input.isEmpty()) {
            return MapFactory.createObjectMap(type);
        }
        
        // 쿼리 문자열 추출
        String queryString = extractQueryString(input);
        
        if (queryString == null || queryString.isEmpty()) {
            return MapFactory.createObjectMap(type);
        }
        
        return parseToMap(queryString, type, charsetNames);
    }

    /**
     * URL, URI 또는 쿼리 문자열을 기본 HashMap으로 파싱합니다.
     * 
     * @param input URL, URI 또는 쿼리 문자열
     * @param charsetNames 문자 인코딩 (미지정 시 UTF-8)
     * @return 파싱된 Map<String, Object>
     * 
     * @example
     * <pre>
     * Map<String, Object> map = parse("http://example.com?name=홍길동&age=30");
     * // {name=홍길동, age=30}
     * </pre>
     */
    public static Map<String, Object> parse(String input, String... charsetNames) {
        return parse(input, TypeRegistry.MAP_OBJECT, charsetNames);
    }

    /**
     * 쿼리 문자열을 파싱하여 지정된 타입의 Map으로 변환합니다.
     * 
     * <p>MapFactory.create를 사용하여 쿼리 파라미터 개수에 최적화된 초기 용량으로
     * Map을 생성합니다. 이를 통해 불필요한 리사이징을 방지하고 성능을 향상시킵니다.</p>
     * 
     * @param <M> Map 타입
     * @param query 쿼리 문자열 (key=value&key2=value2 형식)
     * @param type TypeRegistry
     * @param charsetNames 문자 인코딩 (미지정 시 UTF-8)
     * @return 파싱된 Map<String, Object>
     * 
     * @example
     * <pre>
     * Map<String, Object> map = parseToMap("name=홍길동&age=30", TypeRegistry.MAP_OBJECT);
     * // {name=홍길동, age=30}
     * 
     * // LinkedHashMap으로 파싱 (순서 보장)
     * Map<String, Object> orderedMap = parseToMap("a=1&b=2&c=3", TypeRegistry.MAP_LINKEDHASHMAP_OBJECT);
     * </pre>
     */
    public static <M extends Map<String, Object>> M parseToMap(String query, TypeRegistry type, String... charsetNames) {
        if (query == null || query.isEmpty()) {
            return MapFactory.createObjectMap(type);
        }

        // 쿼리 파라미터 개수를 추정하여 적절한 초기 용량 계산
        int estimatedParamCount = estimateParameterCount(query);
        int initialCapacity = calculateOptimalCapacity(estimatedParamCount);
        
        // MapFactory를 사용하여 최적화된 용량으로 Map 생성
        M map = MapFactory.createObjectMap(type, initialCapacity);

        Charset charset = charsetNames.length > 0 
            ? Charset.forName(charsetNames[0]) 
            : DEFAULT_CHARSET;

        // 쿼리 문자열 파싱
        Arrays.stream(query.split("&"))
              .forEach(token -> {
                  int idx = token.indexOf('=');
                  if (idx > 0) {
                      String key = decode(token.substring(0, idx), charset);
                      String value = decode(token.substring(idx + 1), charset);
                      map.put(key, value);
                  } else if (idx == 0) {
                      // =value 형태 (키 없음)
                      map.put("", decode(token.substring(1), charset));
                  } else {
                      // key만 있는 경우 (값 없음)
                      map.put(decode(token, charset), null);
                  }
              });

        return map;
    }

    /**
     * 쿼리 문자열을 기본 HashMap으로 파싱합니다.
     * 
     * @param query 쿼리 문자열 (key=value&key2=value2 형식)
     * @param charsetNames 문자 인코딩 (미지정 시 UTF-8)
     * @return 파싱된 Map<String, Object>
     */
    public static Map<String, Object> parseToMap(String query, String... charsetNames) {
        return parseToMap(query, TypeRegistry.MAP_OBJECT, charsetNames);
    }

    /**
     * Map을 쿼리 문자열로 변환합니다.
     * 
     * @param <M> Map 타입
     * @param map 변환할 Map
     * @param charsetNames 문자 인코딩 (미지정 시 UTF-8)
     * @return 쿼리 문자열
     * 
     * @example
     * <pre>
     * Map<String, Object> map = Map.of("name", "홍길동", "age", 30);
     * String query = toQueryString(map);
     * // "name=%ED%99%8D%EA%B8%B8%EB%8F%99&age=30"
     * </pre>
     */
    public static <M extends Map<String, ?>> String toQueryString(M map, String... charsetNames) {
        if (map == null || map.isEmpty()) return "";
        
        Charset charset = charsetNames.length > 0 
            ? Charset.forName(charsetNames[0]) 
            : DEFAULT_CHARSET;

        return map.entrySet()
                  .stream()
                  .map(e -> {
                      String key = encode(String.valueOf(e.getKey()), charset);
                      String value = e.getValue() != null 
                          ? encode(String.valueOf(e.getValue()), charset) 
                          : "";
                      return key + "=" + value;
                  })
                  .collect(Collectors.joining("&"));
    }

    /**
     * Map을 쿼리 문자열로 변환합니다. (레거시 메서드명 호환성 유지)
     * 
     * @deprecated {@link #toQueryString(Map, String...)} 사용을 권장합니다.
     */
    @Deprecated
    public static <M extends Map<String, ?>> String parse(M map, String... charsetNames) {
        return toQueryString(map, charsetNames);
    }

    /**
     * URL 디코딩을 수행합니다.
     * 
     * @param value 인코딩된 문자열
     * @param charsetNames 문자 인코딩 (미지정 시 UTF-8)
     * @return 디코딩된 문자열
     * 
     * @example
     * <pre>
     * decode("%ED%99%8D%EA%B8%B8%EB%8F%99")  // "홍길동"
     * decode(null)                            // null
     * </pre>
     */
    public static String decode(String value, String... charsetNames) {
        Charset charset = charsetNames.length > 0 
            ? Charset.forName(charsetNames[0]) 
            : DEFAULT_CHARSET;
        return decode(value, charset);
    }

    /**
     * URL 디코딩을 수행합니다.
     * 
     * @param value 인코딩된 문자열
     * @param charset 문자 인코딩
     * @return 디코딩된 문자열
     */
    public static String decode(String value, Charset charset) {
        if (value == null) return null;
        try {
            return URLDecoder.decode(value, charset.name());
        } catch (UnsupportedEncodingException e) {
            logger.warn("Decode failed for value: {}", value, e);
            return value;
        }
    }

    /**
     * URL 인코딩을 수행합니다.
     * 
     * @param value 원본 문자열
     * @param charsetNames 문자 인코딩 (미지정 시 UTF-8)
     * @return 인코딩된 문자열
     * 
     * @example
     * <pre>
     * encode("홍길동")  // "%ED%99%8D%EA%B8%B8%EB%8F%99"
     * encode(null)     // null
     * </pre>
     */
    public static String encode(String value, String... charsetNames) {
        Charset charset = charsetNames.length > 0 
            ? Charset.forName(charsetNames[0]) 
            : DEFAULT_CHARSET;
        return encode(value, charset);
    }

    /**
     * URL 인코딩을 수행합니다.
     * 
     * @param value 원본 문자열
     * @param charset 문자 인코딩
     * @return 인코딩된 문자열
     */
    public static String encode(String value, Charset charset) {
        if (value == null) return null;
        try {
            return URLEncoder.encode(value, charset.name());
        } catch (UnsupportedEncodingException e) {
            logger.warn("Encode failed for value: {}", value, e);
            return value;
        }
    }

    // ==================== Private Helper 메서드 ====================

    /**
     * TypeRegistry가 MAP_ 타입인지 검증합니다.
     */
    private static boolean isValidMapType(TypeRegistry type) {
        return type != null && type.name().startsWith("MAP_");
    }

    /**
     * 입력 문자열에서 쿼리 문자열을 추출합니다.
     * - URL 형식: scheme이 있는 경우 (http://, https:// 등)
     * - URI 형식: /로 시작하고 ?가 있는 경우
     * - 쿼리 문자열: =가 있거나 &가 있는 경우
     */
    private static String extractQueryString(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        
        // 1. URL 형식 (scheme://host/path?query)
        if (input.contains("://")) {
            URI uri = getUri(input);
            return uri != null ? uri.getQuery() : null;
        }
        
        // 2. URI 형식 (/path?query)
        if (input.startsWith("/")) {
            int queryIndex = input.indexOf('?');
            if (queryIndex > 0 && queryIndex < input.length() - 1) {
                return input.substring(queryIndex + 1);
            }
            return null;
        }
        
        // 3. 쿼리 문자열 형식 (key=value&key2=value2)
        if (input.contains("=") || input.contains("&")) {
            return input;
        }
        
        // 4. URI로 파싱 시도
        URI uri = getUri(input);
        if (uri != null && uri.getQuery() != null) {
            return uri.getQuery();
        }
        
        // 5. 그 외는 쿼리 문자열로 간주
        return input;
    }

    /**
     * 쿼리 문자열에서 예상되는 파라미터 개수를 추정합니다.
     * 
     * @param queryString 쿼리 문자열
     * @return 예상 파라미터 개수
     */
    private static int estimateParameterCount(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return 0;
        }
        
        // '&' 기준으로 파라미터 개수 추정
        int count = 1;
        for (int i = 0; i < queryString.length(); i++) {
            if (queryString.charAt(i) == '&') {
                count++;
            }
        }
        
        return count;
    }

    /**
     * 예상 파라미터 개수를 기반으로 최적의 초기 용량을 계산합니다.
     * 
     * <p>HashMap의 loadFactor(0.75)를 고려하여 리사이징이 발생하지 않도록
     * 충분한 용량을 계산합니다.</p>
     * 
     * @param estimatedSize 예상 파라미터 개수
     * @return 최적 초기 용량
     */
    private static int calculateOptimalCapacity(int estimatedSize) {
        if (estimatedSize <= 0) {
            return MapFactory.getDefaultInitialCapacity();
        }
        
        // loadFactor가 0.75이므로 리사이징을 피하기 위해 여유 공간 추가
        // (estimatedSize / loadFactor) + 1
        float loadFactor = MapFactory.getDefaultLoadFactor();
        int capacity = (int) Math.ceil(estimatedSize / loadFactor) + 1;
        
        // 최소값은 기본 초기 용량
        return Math.max(capacity, MapFactory.getDefaultInitialCapacity());
    }
}