package kr.tx24.lib.jwt;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

/**
 * 범용 JWT 생성 및 검증 유틸리티
 * 
 * <p>JWT(JSON Web Token) 생성, 파싱, 검증을 지원하는 범용 유틸리티입니다.</p>
 * 
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>다양한 알고리즘 지원 (HS256, HS384, HS512, RS256, RS384, RS512)</li>
 *   <li>표준 Claims 및 Custom Claims 지원</li>
 *   <li>Builder 패턴을 통한 유연한 JWT 생성</li>
 *   <li>JWT 파싱 및 검증</li>
 *   <li>PEM 형식 Private Key 지원</li>
 * </ul>
 * 
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * // 1. HMAC 기반 JWT 생성
 * String token = JwtUtils.builder()
 *     .secretKey("mySecretKey123456789012345678901234567890")
 *     .algorithm(SignatureAlgorithm.HS256)
 *     .issuer("my-service")
 *     .subject("user123")
 *     .audience("api.example.com")
 *     .expirationMinutes(60)
 *     .claim("role", "admin")
 *     .build();
 * 
 * // 2. RSA 기반 JWT 생성
 * String token = JwtUtils.builder()
 *     .privateKeyPem(privateKeyPem)
 *     .algorithm(SignatureAlgorithm.RS256)
 *     .issuer("oauth-client")
 *     .subject("service-account@example.com")
 *     .audience("https://auth.example.com/token")
 *     .expirationSeconds(3600)
 *     .build();
 * 
 * // 3. JWT 파싱
 * Claims claims = JwtUtils.parse(token, secretKey);
 * String subject = claims.getSubject();
 * }</pre>
 * 
 * @author TX24
 */
public class JwtUtils {
    
    /**
     * JWT 빌더
     * 
     * @return JwtBuilder 인스턴스
     */
    public static JwtBuilder builder() {
        return new JwtBuilder();
    }
    
    /**
     * JWT 파싱 (HMAC 검증)
     * 
     * @param token JWT 토큰
     * @param secretKey Secret Key
     * @return Claims 객체
     * @throws io.jsonwebtoken.JwtException JWT 파싱 실패 시
     */
    public static Claims parse(String token, String secretKey) {
        SecretKey key = new SecretKeySpec(
            secretKey.getBytes(), 
            SignatureAlgorithm.HS256.getJcaName()
        );
        
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    /**
     * JWT 파싱 (RSA 검증)
     * 
     * @param token JWT 토큰
     * @param publicKey Public Key
     * @return Claims 객체
     * @throws io.jsonwebtoken.JwtException JWT 파싱 실패 시
     */
    public static Claims parse(String token, java.security.PublicKey publicKey) {
        return Jwts.parser()
            .verifyWith(publicKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
    
    /**
     * JWT 검증 (서명 검증 없음)
     * 
     * <p><b>주의:</b> 서명을 검증하지 않으므로 신뢰할 수 없는 토큰에는 사용하지 마세요.</p>
     * 
     * @param token JWT 토큰
     * @return Claims 객체
     */
    public static Claims parseUnsigned(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token format");
        }
        
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        
        return Jwts.parser()
            .unsecured()
            .build()
            .parseUnsecuredClaims(token)
            .getPayload();
    }
    
    /**
     * PEM 형식의 Private Key를 PrivateKey 객체로 변환
     * 
     * @param privateKeyPem PEM 형식의 Private Key 문자열
     * @return PrivateKey 객체
     * @throws RuntimeException Private Key 파싱 실패 시
     */
    public static PrivateKey parsePrivateKeyPem(String privateKeyPem) {
        try {
            // PEM 헤더/푸터 제거 및 공백 제거
            String privateKeyContent = privateKeyPem
                .replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("-----BEGIN RSA PRIVATE KEY-----", "")
                .replaceAll("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
            
            // Base64 디코딩
            byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
            
            // PKCS8 형식으로 Private Key 생성
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            
            return keyFactory.generatePrivate(keySpec);
            
        } catch (Exception e) {
            throw new RuntimeException("Private Key 파싱 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * HMAC Secret Key 생성
     * 
     * @param algorithm 알고리즘 (HS256, HS384, HS512)
     * @return SecretKey 객체
     */
    public static SecretKey generateSecretKey(SignatureAlgorithm algorithm) {
        return Keys.secretKeyFor(algorithm);
    }
    
    /**
     * JWT Builder 클래스
     */
    public static class JwtBuilder {
        
        private String issuer;
        private String subject;
        private String audience;
        private Date issuedAt;
        private Date expiration;
        private Date notBefore;
        private String jwtId;
        private Map<String, Object> claims;
        
        private SignatureAlgorithm algorithm;
        private Key signingKey;
        
        /**
         * 기본 생성자
         */
        public JwtBuilder() {
            this.claims = new HashMap<>();
            this.algorithm = SignatureAlgorithm.HS256; // 기본 알고리즘
        }
        
        /**
         * Issuer 설정 (iss)
         * 
         * @param issuer JWT를 발급한 주체
         * @return JwtBuilder
         */
        public JwtBuilder issuer(String issuer) {
            this.issuer = issuer;
            return this;
        }
        
        /**
         * Subject 설정 (sub)
         * 
         * @param subject JWT의 주제 (일반적으로 사용자 ID)
         * @return JwtBuilder
         */
        public JwtBuilder subject(String subject) {
            this.subject = subject;
            return this;
        }
        
        /**
         * Audience 설정 (aud)
         * 
         * @param audience JWT의 수신자
         * @return JwtBuilder
         */
        public JwtBuilder audience(String audience) {
            this.audience = audience;
            return this;
        }
        
        /**
         * Issued At 설정 (iat)
         * 
         * @param issuedAt JWT 발급 시간
         * @return JwtBuilder
         */
        public JwtBuilder issuedAt(Date issuedAt) {
            this.issuedAt = issuedAt;
            return this;
        }
        
        /**
         * Expiration 설정 (exp)
         * 
         * @param expiration JWT 만료 시간
         * @return JwtBuilder
         */
        public JwtBuilder expiration(Date expiration) {
            this.expiration = expiration;
            return this;
        }
        
        /**
         * Expiration 설정 (초 단위)
         * 
         * @param expirationSeconds 현재 시간으로부터 만료까지의 초
         * @return JwtBuilder
         */
        public JwtBuilder expirationSeconds(long expirationSeconds) {
            long now = System.currentTimeMillis();
            this.expiration = new Date(now + expirationSeconds * 1000);
            return this;
        }
        
        /**
         * Expiration 설정 (분 단위)
         * 
         * @param expirationMinutes 현재 시간으로부터 만료까지의 분
         * @return JwtBuilder
         */
        public JwtBuilder expirationMinutes(long expirationMinutes) {
            return expirationSeconds(expirationMinutes * 60);
        }
        
        /**
         * Expiration 설정 (시간 단위)
         * 
         * @param expirationHours 현재 시간으로부터 만료까지의 시간
         * @return JwtBuilder
         */
        public JwtBuilder expirationHours(long expirationHours) {
            return expirationSeconds(expirationHours * 3600);
        }
        
        /**
         * Not Before 설정 (nbf)
         * 
         * @param notBefore JWT가 유효해지는 시간
         * @return JwtBuilder
         */
        public JwtBuilder notBefore(Date notBefore) {
            this.notBefore = notBefore;
            return this;
        }
        
        /**
         * JWT ID 설정 (jti)
         * 
         * @param jwtId JWT 고유 식별자
         * @return JwtBuilder
         */
        public JwtBuilder jwtId(String jwtId) {
            this.jwtId = jwtId;
            return this;
        }
        
        /**
         * JWT ID 자동 생성 (UUID)
         * 
         * @return JwtBuilder
         */
        public JwtBuilder generateJwtId() {
            this.jwtId = UUID.randomUUID().toString();
            return this;
        }
        
        /**
         * Custom Claim 추가
         * 
         * @param key Claim 키
         * @param value Claim 값
         * @return JwtBuilder
         */
        public JwtBuilder claim(String key, Object value) {
            this.claims.put(key, value);
            return this;
        }
        
        /**
         * 여러 Custom Claims 추가
         * 
         * @param claims Claim 맵
         * @return JwtBuilder
         */
        public JwtBuilder claims(Map<String, Object> claims) {
            if (claims != null) {
                this.claims.putAll(claims);
            }
            return this;
        }
        
        /**
         * 서명 알고리즘 설정
         * 
         * @param algorithm 서명 알고리즘
         * @return JwtBuilder
         */
        public JwtBuilder algorithm(SignatureAlgorithm algorithm) {
            this.algorithm = algorithm;
            return this;
        }
        
        /**
         * Secret Key 설정 (HMAC 알고리즘용)
         * 
         * @param secretKey Secret Key 문자열
         * @return JwtBuilder
         */
        public JwtBuilder secretKey(String secretKey) {
            this.signingKey = new SecretKeySpec(
                secretKey.getBytes(), 
                this.algorithm.getJcaName()
            );
            return this;
        }
        
        /**
         * Secret Key 설정 (HMAC 알고리즘용)
         * 
         * @param secretKey SecretKey 객체
         * @return JwtBuilder
         */
        public JwtBuilder secretKey(SecretKey secretKey) {
            this.signingKey = secretKey;
            return this;
        }
        
        /**
         * Private Key 설정 (RSA 알고리즘용)
         * 
         * @param privateKey PrivateKey 객체
         * @return JwtBuilder
         */
        public JwtBuilder privateKey(PrivateKey privateKey) {
            this.signingKey = privateKey;
            return this;
        }
        
        /**
         * Private Key 설정 (PEM 형식)
         * 
         * @param privateKeyPem PEM 형식의 Private Key 문자열
         * @return JwtBuilder
         */
        public JwtBuilder privateKeyPem(String privateKeyPem) {
            this.signingKey = parsePrivateKeyPem(privateKeyPem);
            return this;
        }
        
        /**
         * JWT 생성
         * 
         * @return JWT 토큰 문자열
         * @throws IllegalStateException Signing Key가 설정되지 않은 경우
         * @throws RuntimeException JWT 생성 실패 시
         */
        public String build() {
            if (signingKey == null) {
                throw new IllegalStateException(
                    "Signing Key가 설정되지 않았습니다. secretKey() 또는 privateKey()를 호출하세요."
                );
            }
            
            try {
                // 기본값 설정
                if (issuedAt == null) {
                    issuedAt = new Date();
                }
                
                io.jsonwebtoken.JwtBuilder jwtBuilder = Jwts.builder()
                    .claims(claims);
                
                // 표준 Claims 설정
                if (issuer != null) {
                    jwtBuilder.issuer(issuer);
                }
                if (subject != null) {
                    jwtBuilder.subject(subject);
                }
                if (audience != null) {
                    jwtBuilder.audience().add(audience);
                }
                if (jwtId != null) {
                    jwtBuilder.id(jwtId);
                }
                
                jwtBuilder
                    .issuedAt(issuedAt)
                    .expiration(expiration)
                    .notBefore(notBefore)
                    .signWith(signingKey, algorithm);
                
                return jwtBuilder.compact();
                
            } catch (Exception e) {
                throw new RuntimeException("JWT 생성 실패: " + e.getMessage(), e);
            }
        }
    }
}