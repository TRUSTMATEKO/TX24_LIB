package kr.tx24.naverwork.oauth;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.web.bind.annotation.RequestBody;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NwOAuthClient {

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
     * NaverWorksOAuthClient 생성자
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
     * Access Token 획득
     * 
     * @param scope 요청할 권한 범위 (예: "general", "user", "bot" 등)
     * @return AccessTokenResponse 객체
     * @throws IOException HTTP 통신 중 오류 발생 시
     * @throws RuntimeException Access Token 획득 실패 시
     */
    public AccessTokenResponse getAccessToken(String scope) throws IOException {
        String assertion = jwtGenerator.generateOAuthAssertion();
        
        // Form URL Encoded 요청 바디 생성
        FormBody.Builder formBuilder = new FormBody.Builder()
            .add("assertion", assertion)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("scope", scope);
        
        RequestBody requestBody = formBuilder.build();
        
        Request request = new Request.Builder()
            .url(TOKEN_ENDPOINT)
            .post(requestBody)
            .build();
        
        try (Response response = okHttpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            
            if (!response.isSuccessful()) {
                throw new RuntimeException(
                    String.format("Access Token 획득 실패: HTTP %d - %s", 
                        response.code(), responseBody)
                );
            }
            
            return objectMapper.readValue(responseBody, AccessTokenResponse.class);
        }
    }
    
    /**
     * Access Token 획득 (비동기)
     * 
     * @param scope 요청할 권한 범위
     * @return AccessTokenResponse의 CompletableFuture
     */
    public CompletableFuture<AccessTokenResponse> getAccessTokenAsync(String scope) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAccessToken(scope);
            } catch (Exception e) {
                throw new RuntimeException("비동기 Access Token 획득 실패", e);
            }
        });
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
     * Access Token 응답 객체
     */
    public static class AccessTokenResponse {
        
        @JsonProperty("access_token")
        private String accessToken;
        
        @JsonProperty("token_type")
        private String tokenType;
        
        @JsonProperty("expires_in")
        private long expiresIn;
        
        @JsonProperty("scope")
        private String scope;
        
        /**
         * 기본 생성자
         */
        public AccessTokenResponse() {
        }
        
        /**
         * Access Token 반환
         * 
         * @return Access Token
         */
        public String getAccessToken() {
            return accessToken;
        }
        
        /**
         * Access Token 설정
         * 
         * @param accessToken Access Token
         */
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }
        
        /**
         * Token Type 반환
         * 
         * @return Token Type (일반적으로 "Bearer")
         */
        public String getTokenType() {
            return tokenType;
        }
        
        /**
         * Token Type 설정
         * 
         * @param tokenType Token Type
         */
        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }
        
        /**
         * 토큰 만료 시간 반환 (초)
         * 
         * @return 만료 시간 (초 단위)
         */
        public long getExpiresIn() {
            return expiresIn;
        }
        
        /**
         * 토큰 만료 시간 설정
         * 
         * @param expiresIn 만료 시간 (초 단위)
         */
        public void setExpiresIn(long expiresIn) {
            this.expiresIn = expiresIn;
        }
        
        /**
         * 권한 범위 반환
         * 
         * @return 권한 범위
         */
        public String getScope() {
            return scope;
        }
        
        /**
         * 권한 범위 설정
         * 
         * @param scope 권한 범위
         */
        public void setScope(String scope) {
            this.scope = scope;
        }
        
        @Override
        public String toString() {
            return "AccessTokenResponse{" +
                "accessToken='" + (accessToken != null ? "***" : "null") + '\'' +
                ", tokenType='" + tokenType + '\'' +
                ", expiresIn=" + expiresIn +
                ", scope='" + scope + '\'' +
                '}';
        }
    }
}
