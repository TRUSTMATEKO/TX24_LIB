package kr.tx24.naverwork.oauth;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NwAccessTokenManager {

	
	/** Singleton 인스턴스 */
    private static volatile NwAccessTokenManager instance;
    
    /** 토큰 갱신 버퍼 시간 (초) - 만료 5분 전에 갱신 */
    private static final long REFRESH_BUFFER_SECONDS = 300L; // 5 minutes
    
    /** OAuth 클라이언트 */
    private final NwOAuthClient oauthClient;
    
    /** 권한 범위 */
    private final String scope;
    
    /** 현재 Access Token */
    private volatile String accessToken;
    
    /** Token 타입 */
    private volatile String tokenType;
    
    /** Token 만료 시간 (Epoch 초) */
    private volatile long expiresAt;
    
    /** Read-Write Lock for thread safety */
    private final ReadWriteLock lock;
    
    /**
     * Private 생성자 (Singleton 패턴)
     * 
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param serviceAccount Service Account ID
     * @param privateKeyPem Private Key (PEM 형식)
     * @param scope 권한 범위
     */
    private NwAccessTokenManager(String clientId, String clientSecret, 
                                         String serviceAccount, String privateKeyPem, String scope) {
        NwJwt jwtGenerator = new NwJwt(
            clientId, serviceAccount, privateKeyPem
        );
        
        this.oauthClient = new NwOAuthClient(jwtGenerator, clientId, clientSecret);
        this.scope = scope;
        this.lock = new ReentrantReadWriteLock();
    }
    
    /**
     * AccessTokenManager 초기화
     * 
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param serviceAccount Service Account ID
     * @param privateKeyPem Private Key (PEM 형식)
     * @param scope 권한 범위 (예: "general", "user", "bot")
     * @return 초기화된 인스턴스
     */
    public static NwAccessTokenManager initialize(String clientId, String clientSecret,
                                                          String serviceAccount, String privateKeyPem, String scope) {
        if (instance == null) {
            synchronized (NwAccessTokenManager.class) {
                if (instance == null) {
                    instance = new NwAccessTokenManager(
                        clientId, clientSecret, serviceAccount, privateKeyPem, scope
                    );
                }
            }
        }
        return instance;
    }
    
    /**
     * Singleton 인스턴스 반환
     * 
     * @return AccessTokenManager 인스턴스
     * @throws IllegalStateException initialize()가 호출되지 않은 경우
     */
    public static NwAccessTokenManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                "NaverWorksAccessTokenManager가 초기화되지 않았습니다. initialize()를 먼저 호출하세요."
            );
        }
        return instance;
    }
    
    /**
     * 유효한 Access Token 반환
     * 
     * <p>토큰이 없거나 만료된 경우 자동으로 갱신합니다.</p>
     * 
     * @return 유효한 Access Token
     * @throws RuntimeException Token 발급/갱신 실패 시
     */
    public String getValidToken() {
        // 먼저 읽기 락으로 토큰 유효성 확인
        lock.readLock().lock();
        try {
            if (isTokenValid()) {
                return accessToken;
            }
        } finally {
            lock.readLock().unlock();
        }
        
        // 토큰이 유효하지 않으면 쓰기 락으로 갱신
        lock.writeLock().lock();
        try {
            // Double-check: 다른 스레드가 이미 갱신했을 수 있음
            if (isTokenValid()) {
                return accessToken;
            }
            
            // 토큰 갱신
            refreshToken();
            return accessToken;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Access Token 강제 갱신
     * 
     * @throws RuntimeException Token 발급 실패 시
     */
    public void forceRefresh() {
        lock.writeLock().lock();
        try {
            refreshToken();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 토큰 유효성 검증
     * 
     * <p>토큰이 존재하고, 만료 시간이 현재 시간 + 버퍼보다 큰 경우 유효합니다.</p>
     * 
     * @return 토큰이 유효하면 true, 그렇지 않으면 false
     */
    private boolean isTokenValid() {
        if (accessToken == null || accessToken.isEmpty()) {
            return false;
        }
        
        long currentTime = Instant.now().getEpochSecond();
        long validUntil = expiresAt - REFRESH_BUFFER_SECONDS;
        
        return currentTime < validUntil;
    }
    
    /**
     * Access Token 갱신 (내부 메서드)
     * 
     * @throws RuntimeException Token 발급 실패 시
     */
    private void refreshToken() {
        try {
            NwOAuthClient.AccessTokenResponse response = oauthClient.getAccessToken(scope);
            
            this.accessToken = response.getAccessToken();
            this.tokenType = response.getTokenType();
            
            // 만료 시간 계산 (현재 시간 + expires_in)
            long currentTime = Instant.now().getEpochSecond();
            this.expiresAt = currentTime + response.getExpiresIn();
            
            System.out.printf("[NaverWorks] Access Token 갱신 완료 (만료: %s)%n", 
                Instant.ofEpochSecond(expiresAt));
            
        } catch (IOException e) {
            throw new RuntimeException("Access Token 갱신 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 현재 Access Token 반환 (유효성 검증 없음)
     * 
     * @return 현재 저장된 Access Token (없으면 null)
     */
    public String getCurrentToken() {
        lock.readLock().lock();
        try {
            return accessToken;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Token Type 반환
     * 
     * @return Token Type (일반적으로 "Bearer")
     */
    public String getTokenType() {
        lock.readLock().lock();
        try {
            return tokenType;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * Authorization 헤더 값 반환
     * 
     * @return "Bearer {access_token}" 형식의 문자열
     */
    public String getAuthorizationHeader() {
        String token = getValidToken();
        String type = tokenType != null ? tokenType : "Bearer";
        return type + " " + token;
    }
    
    /**
     * 토큰 만료까지 남은 시간 반환 (초)
     * 
     * @return 남은 시간 (초), 토큰이 없으면 0
     */
    public long getTimeUntilExpiration() {
        lock.readLock().lock();
        try {
            if (accessToken == null) {
                return 0L;
            }
            long currentTime = Instant.now().getEpochSecond();
            return Math.max(0L, expiresAt - currentTime);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 토큰 만료 시간 반환
     * 
     * @return 만료 시간 (Epoch 초), 토큰이 없으면 0
     */
    public long getExpiresAt() {
        lock.readLock().lock();
        try {
            return expiresAt;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 토큰 정보 출력 (디버깅용)
     */
    public void printTokenInfo() {
        lock.readLock().lock();
        try {
            System.out.println("=== NAVER WORKS Access Token 정보 ===");
            System.out.println("Token: " + (accessToken != null ? "***" + accessToken.substring(Math.max(0, accessToken.length() - 10)) : "없음"));
            System.out.println("Type: " + tokenType);
            System.out.println("만료 시간: " + (expiresAt > 0 ? Instant.ofEpochSecond(expiresAt) : "없음"));
            System.out.println("남은 시간: " + getTimeUntilExpiration() + "초");
            System.out.println("유효 여부: " + isTokenValid());
            System.out.println("=====================================");
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 인스턴스 초기화 (테스트용)
     */
    static void resetInstance() {
        instance = null;
    }
}
