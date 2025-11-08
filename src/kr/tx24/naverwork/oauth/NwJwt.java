package kr.tx24.naverwork.oauth;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class NwJwt {

	/** 네이버웍스 OAuth 서버 URL */
    private static final String NAVER_WORKS_TOKEN_URL = "https://auth.worksmobile.com/oauth2/v2.0/token";
    
    /** JWT 토큰 유효 시간 (기본: 1시간) */
    private static final long TOKEN_EXPIRATION_TIME = 60*60; // 1 hour in milliseconds
    
    /** Client ID (네이버웍스 Developer Console에서 발급) */
    private final String clientId;
    
    /** Service Account ID (Bot ID) */
    private final String serviceAccount;
    
    /** Private Key (RSA 개인키) */
    private final PrivateKey privateKey;
    
    /**
     * NaverWorksJwtGenerator 생성자
     * 
     * @param clientId 네이버웍스 Client ID
     * @param serviceAccount Service Account ID (Bot ID)
     * @param privateKeyPem RSA 개인키 (PEM 형식)
     * @throws IllegalArgumentException 파라미터가 null이거나 비어있는 경우
     * @throws RuntimeException Private Key 파싱 실패 시
     */
    public NwJwt(String clientId, String serviceAccount, String privateKeyPem) {
        validateParameters(clientId, serviceAccount, privateKeyPem);
        
        this.clientId = clientId;
        this.serviceAccount = serviceAccount;
        this.privateKey = parsePrivateKey(privateKeyPem);
    }
    
    /**
     * 입력 파라미터 유효성 검증
     * 
     * @param clientId Client ID
     * @param serviceAccount Service Account ID
     * @param privateKeyPem Private Key PEM 문자열
     * @throws IllegalArgumentException 파라미터가 유효하지 않은 경우
     */
    private void validateParameters(String clientId, String serviceAccount, String privateKeyPem) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("clientId는 필수 입력값입니다.");
        }
        if (serviceAccount == null || serviceAccount.trim().isEmpty()) {
            throw new IllegalArgumentException("serviceAccount는 필수 입력값입니다.");
        }
        if (privateKeyPem == null || privateKeyPem.trim().isEmpty()) {
            throw new IllegalArgumentException("privateKeyPem은 필수 입력값입니다.");
        }
    }
    
    /**
     * PEM 형식의 Private Key를 PrivateKey 객체로 변환
     * 
     * @param privateKeyPem PEM 형식의 Private Key 문자열
     * @return PrivateKey 객체
     * @throws RuntimeException Private Key 파싱 실패 시
     */
    private PrivateKey parsePrivateKey(String privateKeyPem) {
        try {
            // PEM 헤더/푸터 제거 및 공백 제거
            String privateKeyContent = privateKeyPem
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
            
            // Base64 디코딩
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
            
            // PKCS8 형식으로 Private Key 생성
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            
            return keyFactory.generatePrivate(keySpec);
            
        } catch (Exception e) {
            throw new RuntimeException("Private Key 파싱에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * JWT 토큰 생성
     * 
     * <p>네이버웍스 API 인증을 위한 JWT 토큰을 생성합니다.
     * 생성된 토큰은 OAuth 2.0 인증 플로우에서 사용됩니다.</p>
     * 
     * @return 생성된 JWT 토큰 문자열
     * @throws RuntimeException JWT 생성 실패 시
     */
    public String generateToken() {
        return generateToken(TOKEN_EXPIRATION_TIME);
    }
    
    /**
     * JWT 토큰 생성 (만료 시간 지정)
     * 
     * @param expirationTimeMillis 토큰 만료 시간 (밀리초)
     * @return 생성된 JWT 토큰 문자열
     * @throws RuntimeException JWT 생성 실패 시
     */
    public String generateToken(long expirationTimeMillis) {
        try {
            long now = System.currentTimeMillis();
            Date issuedAt = new Date(now);
            Date expiration = new Date(now + expirationTimeMillis);
            
            return Jwts.builder()
                    .issuer(clientId)
                    .subject(serviceAccount)
                    .issuedAt(issuedAt)
                    .expiration(expiration)
                    .signWith(privateKey)
                    .compact();
                
        } catch (Exception e) {
            throw new RuntimeException("JWT 토큰 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * Access Token을 얻기 위한 JWT 생성 (네이버웍스 OAuth 2.0 스펙)
     * 
     * <p>이 메서드는 네이버웍스 OAuth 2.0의 JWT assertion을 생성합니다.
     * 생성된 JWT는 Token Endpoint에 전송하여 Access Token을 획득하는 데 사용됩니다.</p>
     * 
     * @return OAuth 2.0 JWT assertion
     */
    public String generateOAuthAssertion() {
        try {
            long now = System.currentTimeMillis();
            Date issuedAt = new Date(now);
            Date expiration = new Date(now + TOKEN_EXPIRATION_TIME);
            
            return Jwts.builder()
                    .issuer(clientId)
                    .subject(serviceAccount)
                    .audience().add(NAVER_WORKS_TOKEN_URL).and()  // 특별 처리
                    .issuedAt(issuedAt)
                    .expiration(expiration)
                    .signWith(privateKey)
                    .compact();
                
        } catch (Exception e) {
            throw new RuntimeException("OAuth Assertion 생성에 실패했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * Client ID 반환
     * 
     * @return Client ID
     */
    public String getClientId() {
        return clientId;
    }
    
    /**
     * Service Account ID 반환
     * 
     * @return Service Account ID
     */
    public String getServiceAccount() {
        return serviceAccount;
    }
    
    /**
     * 토큰 URL 반환
     * 
     * @return 네이버웍스 Token URL
     */
    public static String getTokenUrl() {
        return NAVER_WORKS_TOKEN_URL;
    }
}
