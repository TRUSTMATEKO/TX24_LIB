package kr.tx24.lib.oauth;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.inter.INet.INMessage;
import kr.tx24.lib.map.LinkedMap;

/**
 * 범용 OAuth 2.0 클라이언트
 * 
 * <p>INet을 통한 HTTP 통신을 사용하는 범용 OAuth 2.0 클라이언트입니다.</p>
 * 
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>JWT Assertion을 통한 Access Token 발급</li>
 *   <li>Refresh Token을 통한 토큰 갱신</li>
 *   <li>Client Credentials Grant 지원</li>
 *   <li>Authorization Code Grant 지원</li>
 *   <li>INet 기반 HTTP 통신</li>
 *   <li>응답을 LinkedMap&lt;String, Object&gt;로 반환</li>
 *   <li>비동기 처리 지원</li>
 * </ul>
 * 
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * // 1. JWT Assertion 방식
 * OAuth2Client client = new OAuth2Client(
 *     "tokenServerHost",
 *     8080,
 *     "/oauth2/v2.0/token"
 * );
 * 
 * String jwt = JwtUtils.builder()
 *     .privateKeyPem(privateKeyPem)
 *     .issuer(clientId)
 *     .subject(serviceAccount)
 *     .audience("https://auth.example.com/token")
 *     .expirationHours(1)
 *     .build();
 * 
 * LinkedMap<String, Object> tokenResponse = client.getAccessTokenByJwt(
 *     jwt, clientId, clientSecret, "bot"
 * );
 * 
 * // 2. Refresh Token 방식
 * LinkedMap<String, Object> refreshed = client.refreshAccessToken(
 *     refreshToken, clientId, clientSecret
 * );
 * 
 * // 3. Client Credentials 방식
 * LinkedMap<String, Object> token = client.getAccessTokenByClientCredentials(
 *     clientId, clientSecret, "read write"
 * );
 * }</pre>
 * 
 * @author TX24
 */
public class OAuth2Client {
    
    private static final Logger logger = LoggerFactory.getLogger(OAuth2Client.class);
    
    /** Token Server Host */
    private final String tokenServerHost;
    
    /** Token Server Port */
    private final int tokenServerPort;
    
    /** Token Endpoint Path (예: /oauth2/v2.0/token) */
    private final String tokenEndpointPath;
    
    /** 연결 타임아웃 (밀리초) */
    private final int connectTimeout;
    
    /**
     * OAuth2Client 생성자
     * 
     * @param tokenServerHost Token Server Host
     * @param tokenServerPort Token Server Port
     * @param tokenEndpointPath Token Endpoint Path
     */
    public OAuth2Client(String tokenServerHost, int tokenServerPort, String tokenEndpointPath) {
        this(tokenServerHost, tokenServerPort, tokenEndpointPath, 30000);
    }
    
    /**
     * OAuth2Client 생성자 (타임아웃 지정)
     * 
     * @param tokenServerHost Token Server Host
     * @param tokenServerPort Token Server Port
     * @param tokenEndpointPath Token Endpoint Path
     * @param connectTimeout 연결 타임아웃 (밀리초)
     */
    public OAuth2Client(String tokenServerHost, int tokenServerPort, 
                       String tokenEndpointPath, int connectTimeout) {
        if (tokenServerHost == null || tokenServerHost.trim().isEmpty()) {
            throw new IllegalArgumentException("tokenServerHost는 필수입니다.");
        }
        if (tokenServerPort <= 0 || tokenServerPort > 65535) {
            throw new IllegalArgumentException("tokenServerPort는 1-65535 범위여야 합니다.");
        }
        if (tokenEndpointPath == null || tokenEndpointPath.trim().isEmpty()) {
            throw new IllegalArgumentException("tokenEndpointPath는 필수입니다.");
        }
        
        this.tokenServerHost = tokenServerHost;
        this.tokenServerPort = tokenServerPort;
        this.tokenEndpointPath = tokenEndpointPath;
        this.connectTimeout = connectTimeout;
        
        logger.info("OAuth2Client 초기화 - endpoint: {}:{}{}", 
            tokenServerHost, tokenServerPort, tokenEndpointPath);
    }
    
    /**
     * JWT Assertion을 통한 Access Token 발급
     * 
     * <p>JWT Bearer Token Grant (RFC 7523) 방식으로 토큰을 발급받습니다.</p>
     * 
     * @param assertion JWT Assertion
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param scope 권한 범위 (null 가능)
     * @return 토큰 응답 (LinkedMap)
     * @throws IOException 통신 실패 시
     * @throws OAuth2Exception OAuth 오류 응답 시
     */
    public LinkedMap<String, Object> getAccessTokenByJwt(
            String assertion, String clientId, String clientSecret, String scope) 
            throws IOException, OAuth2Exception {
        
        logger.debug("[JWT] Access Token 요청 - clientId: {}, scope: {}", 
            maskClientId(clientId), scope);
        
        Map<String, Object> params = new HashMap<>();
        params.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        params.put("assertion", assertion);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        
        if (scope != null && !scope.trim().isEmpty()) {
            params.put("scope", scope);
        }
        
        return executeTokenRequest(params);
    }
    
    /**
     * Refresh Token을 통한 Access Token 갱신
     * 
     * @param refreshToken Refresh Token
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @return 토큰 응답 (LinkedMap)
     * @throws IOException 통신 실패 시
     * @throws OAuth2Exception OAuth 오류 응답 시
     */
    public LinkedMap<String, Object> refreshAccessToken(
            String refreshToken, String clientId, String clientSecret) 
            throws IOException, OAuth2Exception {
        
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new IllegalArgumentException("refreshToken은 필수입니다.");
        }
        
        logger.debug("[Refresh] Token 갱신 요청 - clientId: {}", maskClientId(clientId));
        
        Map<String, Object> params = new HashMap<>();
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        
        return executeTokenRequest(params);
    }
    
    /**
     * Client Credentials Grant를 통한 Access Token 발급
     * 
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param scope 권한 범위 (null 가능)
     * @return 토큰 응답 (LinkedMap)
     * @throws IOException 통신 실패 시
     * @throws OAuth2Exception OAuth 오류 응답 시
     */
    public LinkedMap<String, Object> getAccessTokenByClientCredentials(
            String clientId, String clientSecret, String scope) 
            throws IOException, OAuth2Exception {
        
        logger.debug("[ClientCredentials] Access Token 요청 - clientId: {}, scope: {}", 
            maskClientId(clientId), scope);
        
        Map<String, Object> params = new HashMap<>();
        params.put("grant_type", "client_credentials");
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        
        if (scope != null && !scope.trim().isEmpty()) {
            params.put("scope", scope);
        }
        
        return executeTokenRequest(params);
    }
    
    /**
     * Authorization Code Grant를 통한 Access Token 발급
     * 
     * @param code Authorization Code
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param redirectUri Redirect URI
     * @return 토큰 응답 (LinkedMap)
     * @throws IOException 통신 실패 시
     * @throws OAuth2Exception OAuth 오류 응답 시
     */
    public LinkedMap<String, Object> getAccessTokenByAuthorizationCode(
            String code, String clientId, String clientSecret, String redirectUri) 
            throws IOException, OAuth2Exception {
        
        if (code == null || code.trim().isEmpty()) {
            throw new IllegalArgumentException("code는 필수입니다.");
        }
        
        logger.debug("[AuthCode] Access Token 요청 - clientId: {}", maskClientId(clientId));
        
        Map<String, Object> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("client_id", clientId);
        params.put("client_secret", clientSecret);
        
        if (redirectUri != null && !redirectUri.trim().isEmpty()) {
            params.put("redirect_uri", redirectUri);
        }
        
        return executeTokenRequest(params);
    }
    
    /**
     * 비동기 JWT Assertion Token 발급
     * 
     * @param assertion JWT Assertion
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param scope 권한 범위
     * @return CompletableFuture&lt;LinkedMap&lt;String, Object&gt;&gt;
     */
    public CompletableFuture<LinkedMap<String, Object>> getAccessTokenByJwtAsync(
            String assertion, String clientId, String clientSecret, String scope) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return getAccessTokenByJwt(assertion, clientId, clientSecret, scope);
            } catch (Exception e) {
                logger.error("[JWT-Async] Token 발급 실패", e);
                throw new RuntimeException("비동기 Token 발급 실패", e);
            }
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * 비동기 Refresh Token 갱신
     * 
     * @param refreshToken Refresh Token
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @return CompletableFuture&lt;LinkedMap&lt;String, Object&gt;&gt;
     */
    public CompletableFuture<LinkedMap<String, Object>> refreshAccessTokenAsync(
            String refreshToken, String clientId, String clientSecret) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                return refreshAccessToken(refreshToken, clientId, clientSecret);
            } catch (Exception e) {
                logger.error("[Refresh-Async] Token 갱신 실패", e);
                throw new RuntimeException("비동기 Token 갱신 실패", e);
            }
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * Token 요청 실행 (INet 기반)
     * 
     * @param params 요청 파라미터
     * @return 토큰 응답 (LinkedMap)
     * @throws IOException 통신 실패 시
     * @throws OAuth2Exception OAuth 오류 응답 시
     */
    private LinkedMap<String, Object> executeTokenRequest(Map<String, Object> params) 
            throws IOException, OAuth2Exception {
        
        // INet으로 HTTP POST 요청 (form-urlencoded 형식)
        INet inet = new INet("OAuth2Client", "OAuth2Server");
        
        // Head에 요청 정보 설정
        inet.head("method", "POST");
        inet.head("path", tokenEndpointPath);
        inet.head("Content-Type", "application/x-www-form-urlencoded");
        
        // Body에 OAuth 파라미터 설정
        inet.data(params);
        
        // 요청 전송
        INMessage response = inet.connect(tokenServerHost, tokenServerPort, connectTimeout);
        
        // 응답 확인
        if (!response.getHead().isTrue("result")) {
            String errorMsg = response.getHead().getString("message");
            logger.error("OAuth2 Token 요청 실패: {}", errorMsg);
            throw new IOException("OAuth2 Token 요청 실패: " + errorMsg);
        }
        
        // 응답 데이터를 LinkedMap으로 변환
        LinkedMap<String, Object> tokenResponse = new LinkedMap<>();
        tokenResponse.putAll(response.getData());
        
        // OAuth 오류 응답 체크
        if (tokenResponse.containsKey("error")) {
            String error = tokenResponse.getString("error");
            String errorDescription = tokenResponse.getString("error_description", "");
            
            logger.error("OAuth2 오류 응답: error={}, description={}", error, errorDescription);
            throw new OAuth2Exception(error, errorDescription);
        }
        
        // 필수 필드 검증
        if (!tokenResponse.containsKey("access_token")) {
            logger.error("응답에 access_token이 없습니다: {}", tokenResponse);
            throw new OAuth2Exception("invalid_response", "응답에 access_token이 없습니다");
        }
        
        // 발급 시각 추가 (클라이언트에서 계산용)
        tokenResponse.put("issued_at", System.currentTimeMillis() / 1000); // Unix timestamp (초)
        
        logger.info("OAuth2 Token 발급 성공 - expires_in: {}초, refresh_token: {}", 
            tokenResponse.getInt("expires_in", 0),
            tokenResponse.containsKey("refresh_token") ? "있음" : "없음");
        
        return tokenResponse;
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
     * OAuth 2.0 예외 클래스
     */
    public static class OAuth2Exception extends Exception {
        
        private static final long serialVersionUID = 1L;
        
        private final String error;
        private final String errorDescription;
        
        public OAuth2Exception(String error, String errorDescription) {
            super(String.format("OAuth2 오류 - error: %s, description: %s", 
                error, errorDescription));
            this.error = error;
            this.errorDescription = errorDescription;
        }
        
        public String getError() {
            return error;
        }
        
        public String getErrorDescription() {
            return errorDescription;
        }
    }
}