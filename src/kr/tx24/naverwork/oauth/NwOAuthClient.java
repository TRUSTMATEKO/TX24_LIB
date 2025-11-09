package kr.tx24.naverwork.oauth;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.tx24.lib.executor.AsyncExecutor;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * NAVER WORKS OAuth 클라이언트
 * 
 * <p>JWT 기반 OAuth 2.0 인증과 Refresh Token을 통한 토큰 갱신을 지원합니다.</p>
 * 
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>JWT Assertion 기반 Access Token 획득</li>
 *   <li>Refresh Token을 사용한 토큰 갱신</li>
 *   <li>비동기 토큰 획득 및 갱신</li>
 *   <li>Thread-safe 구현</li>
 * </ul>
 * 
 * <h3>토큰 갱신 전략:</h3>
 * <ol>
 *   <li>Refresh Token이 있으면 우선 사용</li>
 *   <li>Refresh Token이 없거나 실패하면 JWT Assertion으로 새 토큰 발급</li>
 * </ol>
 * 
 * @author TX24
 */
public class NwOAuthClient {
    
    private static final Logger logger = LoggerFactory.getLogger(NwOAuthClient.class);
    
    /** 네이버웍스 OAuth Token Endpoint */
    private static final String TOKEN_ENDPOINT = "https://auth.worksmobile.com/oauth2/v2.0/token";
    
    /** HTTP 클라이언트 연결 타임아웃 (초) */
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    
    /** HTTP 클라이언트 읽기 타임아웃 (초) */
    private static final int READ_TIMEOUT_SECONDS = 30;
    
    /** HTTP 클라이언트 쓰기 타임아웃 (초) */
    private static final int WRITE_TIMEOUT_SECONDS = 30;
    
    /** JWT 생성기 */
    private final NwJwt jwtGenerator;
    
    /** Client ID */
    private final String clientId;
    
    /** Client Secret */
    private final String clientSecret;
    
    /** OkHttp 클라이언트 */
    private final OkHttpClient okHttpClient;
    
    /** JSON 매퍼 */
    private final ObjectMapper objectMapper;
    
    /**
     * NwOAuthClient 생성자
     * 
     * @param jwtGenerator JWT 생성기
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @throws IllegalArgumentException 파라미터가 null이거나 비어있는 경우
     */
    public NwOAuthClient(NwJwt jwtGenerator, String clientId, String clientSecret) {
        if (jwtGenerator == null) {
            throw new IllegalArgumentException("NwJwt는 필수입니다.");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId는 필수 입력값입니다.");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("clientSecret는 필수 입력값입니다.");
        }
        
        this.jwtGenerator = jwtGenerator;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.okHttpClient = createOkHttpClient();
        this.objectMapper = new ObjectMapper();
        
        logger.info("NwOAuthClient 초기화 완료 - clientId: {}", maskClientId(clientId));
    }
    
    /**
     * OkHttp 클라이언트 생성
     * 
     * @return 설정된 OkHttpClient 인스턴스
     */
    private OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Access Token 획득 (JWT Assertion 기반)
     * 
     * <p>JWT Assertion을 생성하여 OAuth 2.0 토큰을 획득합니다.
     * 응답에는 access_token과 refresh_token이 포함됩니다.</p>
     * 
     * @param scope 요청할 권한 범위 (예: "bot", "user", "directory" 등)
     * @return AccessTokenResponse 객체 (access_token, refresh_token 포함)
     * @throws IOException HTTP 통신 중 오류 발생 시
     * @throws RuntimeException Access Token 획득 실패 시
     */
    public AccessTokenResponse getAccessToken(String scope) throws IOException {
        logger.debug("[{}] JWT Assertion 기반 Access Token 요청", scope);
        
        String assertion = jwtGenerator.generateOAuthAssertion();
        
        // Form URL Encoded 요청 바디 생성
        okhttp3.RequestBody requestBody = new FormBody.Builder()
                .add("assertion", assertion)
                .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("scope", scope)
                .build();
        
        Request request = new Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build();
        
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                logger.error("[{}] Access Token 획득 실패: HTTP {}", scope, response.code());
                throw new RuntimeException(
                    String.format("Access Token 획득 실패: HTTP %d - %s", 
                        response.code(), responseBody)
                );
            }
            
            AccessTokenResponse tokenResponse = objectMapper.readValue(responseBody, AccessTokenResponse.class);
            tokenResponse.setIssuedAt(System.currentTimeMillis());
            
            logger.info("[{}] Access Token 발급 성공 (유효기간: {}초, refresh_token: {})", 
                scope, tokenResponse.getExpiresIn(), 
                tokenResponse.getRefreshToken() != null ? "있음" : "없음");
            
            return tokenResponse;
        }
    }
    
    /**
     * Refresh Token을 사용한 Access Token 갱신 (동기)
     * 
     * <p>기존 refresh_token을 사용하여 새로운 access_token을 발급받습니다.
     * 갱신 시 새로운 refresh_token도 함께 발급됩니다.</p>
     * 
     * <h3>갱신 시 발급되는 항목:</h3>
     * <ul>
     *   <li>새로운 access_token</li>
     *   <li>새로운 refresh_token</li>
     *   <li>토큰 유효기간 (expires_in)</li>
     * </ul>
     * 
     * @param refreshToken 갱신에 사용할 Refresh Token
     * @return AccessTokenResponse 객체 (새로운 access_token, refresh_token 포함)
     * @throws IOException HTTP 통신 중 오류 발생 시
     * @throws IllegalArgumentException refreshToken이 null이거나 비어있는 경우
     * @throws RuntimeException Token 갱신 실패 시
     */
    public AccessTokenResponse refreshAccessToken(String refreshToken) throws IOException {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalArgumentException("Refresh Token이 비어있습니다.");
        }
        
        logger.debug("Refresh Token 기반 Access Token 갱신 요청");
        
        // Form URL Encoded 요청 바디 생성
        okhttp3.RequestBody requestBody = new FormBody.Builder()
                .add("refresh_token", refreshToken)
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .build();
        
        Request request = new Request.Builder()
                .url(TOKEN_ENDPOINT)
                .post(requestBody)
                .build();
        
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                logger.error("Token 갱신 실패: HTTP {} - {}", response.code(), responseBody);
                throw new RuntimeException(
                    String.format("Token 갱신 실패: HTTP %d - %s", 
                        response.code(), responseBody)
                );
            }
            
            AccessTokenResponse tokenResponse = objectMapper.readValue(responseBody, AccessTokenResponse.class);
            tokenResponse.setIssuedAt(System.currentTimeMillis());
            
            logger.info("Refresh Token으로 Access Token 갱신 성공 (유효기간: {}초, 새 refresh_token: {})", 
                tokenResponse.getExpiresIn(),
                tokenResponse.getRefreshToken() != null ? "있음" : "없음");
            
            return tokenResponse;
            
        } catch (IOException e) {
            logger.error("Refresh Token 갱신 중 IO 오류 발생", e);
            throw e;
        }
    }
    
    /**
     * Access Token 비동기 획득
     * 
     * <p>AsyncExecutor를 사용하여 비동기적으로 토큰을 획득합니다.</p>
     * 
     * @param scope 요청할 권한 범위
     * @return CompletableFuture&lt;AccessTokenResponse&gt;
     */
    public CompletableFuture<AccessTokenResponse> getAccessTokenAsync(String scope) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAccessToken(scope);
            } catch (IOException e) {
                logger.error("[{}] 비동기 Access Token 획득 실패", scope, e);
                throw new RuntimeException("비동기 Access Token 획득 실패", e);
            }
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * Refresh Token 비동기 갱신
     * 
     * <p>AsyncExecutor를 사용하여 비동기적으로 토큰을 갱신합니다.</p>
     * 
     * @param refreshToken 갱신에 사용할 Refresh Token
     * @return CompletableFuture&lt;AccessTokenResponse&gt;
     */
    public CompletableFuture<AccessTokenResponse> refreshAccessTokenAsync(String refreshToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return refreshAccessToken(refreshToken);
            } catch (IOException e) {
                logger.error("비동기 Token 갱신 실패", e);
                throw new RuntimeException("비동기 Token 갱신 실패", e);
            }
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * OkHttpClient 반환 (고급 사용자용)
     * 
     * @return OkHttpClient 인스턴스
     */
    public OkHttpClient getOkHttpClient() {
        return okHttpClient;
    }
    
    /**
     * Client ID 마스킹 (로그용)
     * 
     * @param clientId Client ID
     * @return 마스킹된 Client ID
     */
    private String maskClientId(String clientId) {
        if (clientId == null || clientId.length() < 8) {
            return "***";
        }
        return clientId.substring(0, 4) + "***" + clientId.substring(clientId.length() - 4);
    }
    
    /**
     * Access Token 응답 객체
     * 
     * <p>NAVER WORKS OAuth 2.0 토큰 응답을 표현합니다.</p>
     */
    public static class AccessTokenResponse {
        
        @JsonProperty("access_token")
        private String accessToken;
        
        @JsonProperty("token_type")
        private String tokenType;
        
        @JsonProperty("refresh_token")
        private String refreshToken;
        
        @JsonProperty("expires_in")
        private long expiresIn;
        
        @JsonProperty("scope")
        private String scope;
        
        /**
         * 토큰 발급 시각 (밀리초 단위 Unix timestamp)
         * 서버 응답에는 포함되지 않으며, 클라이언트에서 설정합니다.
         */
        private long issuedAt;
        
        /**
         * 기본 생성자
         */
        public AccessTokenResponse() {
            this.issuedAt = System.currentTimeMillis();
        }
        
        /**
         * 토큰 만료 여부 확인
         * 
         * @return 토큰이 만료되었으면 true, 아니면 false
         */
        public boolean isExpired() {
            return getRemainingSeconds() <= 0;
        }
        
        /**
         * 토큰이 곧 만료될 예정인지 확인
         * 
         * @param thresholdSeconds 임계값 (초 단위)
         * @return 남은 시간이 임계값보다 작으면 true
         */
        public boolean isExpiringSoon(long thresholdSeconds) {
            return getRemainingSeconds() <= thresholdSeconds;
        }
        
        /**
         * 토큰 남은 유효 시간 계산 (초 단위)
         * 
         * @return 남은 시간 (초)
         */
        public long getRemainingSeconds() {
            long elapsedSeconds = (System.currentTimeMillis() - issuedAt) / 1000;
            return expiresIn - elapsedSeconds;
        }
        
        /**
         * Refresh Token 존재 여부 확인
         * 
         * @return Refresh Token이 있으면 true
         */
        public boolean hasRefreshToken() {
            return refreshToken != null && !refreshToken.trim().isEmpty();
        }
        
        // Getters and Setters
        
        public String getAccessToken() {
            return accessToken;
        }
        
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }
        
        public String getTokenType() {
            return tokenType;
        }
        
        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }
        
        public String getRefreshToken() {
            return refreshToken;
        }
        
        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }
        
        public long getExpiresIn() {
            return expiresIn;
        }
        
        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }
        
        public String getScope() {
            return scope;
        }
        
        public void setScope(String scope) {
            this.scope = scope;
        }
        
        public long getIssuedAt() {
            return issuedAt;
        }
        
        public void setIssuedAt(long issuedAt) {
            this.issuedAt = issuedAt;
        }
        
        @Override
        public String toString() {
            return String.format(
                "AccessTokenResponse{scope='%s', tokenType='%s', expiresIn=%d, remainingSeconds=%d, hasRefreshToken=%s}",
                scope, tokenType, expiresIn, getRemainingSeconds(), hasRefreshToken()
            );
        }
    }
}