package kr.tx24.naverwork.oauth;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.naverwork.oauth.NwOAuthClient.AccessTokenResponse;

/**
 * NAVER WORKS Access Token 관리자
 * 
 * <p>Access Token의 생명주기를 관리하고, Refresh Token 기반 자동 갱신을 지원합니다.</p>
 * 
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>Thread-safe한 토큰 관리 (Singleton 패턴)</li>
 *   <li>Refresh Token 기반 자동 토큰 갱신</li>
 *   <li>백그라운드 자동 갱신 스케줄러 (만료 10분 전)</li>
 *   <li>갱신 실패 시 JWT Assertion으로 폴백</li>
 *   <li>Read-Write Lock을 통한 동시성 제어</li>
 * </ul>
 * 
 * <h3>토큰 갱신 전략:</h3>
 * <ol>
 *   <li>백그라운드 스케줄러가 1분마다 토큰 만료 시간 체크</li>
 *   <li>만료 10분 전부터 자동 갱신 시도</li>
 *   <li>Refresh Token이 있으면 우선 사용</li>
 *   <li>Refresh Token 실패 시 JWT Assertion으로 새 토큰 발급</li>
 *   <li>갱신 성공 시 새 Refresh Token 저장</li>
 * </ol>
 * 
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * // 1. 초기화
 * NwAccessTokenManager manager = NwAccessTokenManager.initialize(
 *     clientId, clientSecret, serviceAccount, privateKey, "bot"
 * );
 * 
 * // 2. 유효한 토큰 획득 (자동 갱신됨)
 * String token = manager.getValidToken();
 * 
 * // 3. Authorization 헤더 획득
 * String authHeader = manager.getAuthorizationHeader();
 * 
 * // 4. 토큰 정보 출력
 * manager.printTokenInfo();
 * }</pre>
 * 
 * @author TX24
 */
public class NwAccessTokenManager {
    
    private static final Logger logger = LoggerFactory.getLogger(NwAccessTokenManager.class);
    
    /** Singleton 인스턴스 */
    private static volatile NwAccessTokenManager instance;
    
    /** 토큰 갱신 버퍼 시간 (초) - 만료 10분 전에 갱신 */
    private static final long REFRESH_BUFFER_SECONDS = 600L; // 10 minutes
    
    /** 백그라운드 갱신 체크 주기 (초) */
    private static final long AUTO_REFRESH_CHECK_INTERVAL = 60L; // 1 minute
    
    /** 최대 재시도 횟수 */
    private static final int MAX_RETRY_COUNT = 3;
    
    /** OAuth 클라이언트 */
    private final NwOAuthClient oauthClient;
    
    /** 권한 범위 */
    private final String scope;
    
    /** 현재 Access Token */
    private volatile String accessToken;
    
    /** 현재 Refresh Token */
    private volatile String refreshToken;
    
    /** Token 타입 */
    private volatile String tokenType;
    
    /** Token 만료 시간 (Epoch 초) */
    private volatile long expiresAt;
    
    /** Read-Write Lock for thread safety */
    private final ReadWriteLock lock;
    
    /** 자동 갱신 활성화 여부 */
    private final AtomicBoolean autoRefreshEnabled;
    
    /** 백그라운드 갱신 스케줄러 */
    private volatile ScheduledFuture<?> refreshScheduler;
    
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
        NwJwt jwtGenerator = new NwJwt(clientId, serviceAccount, privateKeyPem);
        
        this.oauthClient = new NwOAuthClient(jwtGenerator, clientId, clientSecret);
        this.scope = scope;
        this.lock = new ReentrantReadWriteLock();
        this.autoRefreshEnabled = new AtomicBoolean(false);
        this.refreshScheduler = null;
        
        logger.info("NwAccessTokenManager 초기화 완료 - scope: {}, 자동갱신버퍼: {}분", 
            scope, REFRESH_BUFFER_SECONDS / 60);
    }
    
    /**
     * AccessTokenManager 초기화
     * 
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param serviceAccount Service Account ID
     * @param privateKeyPem Private Key (PEM 형식)
     * @param scope 권한 범위 (예: "bot", "user", "directory")
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
                    logger.info("NwAccessTokenManager Singleton 인스턴스 생성 완료");
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
                "NwAccessTokenManager가 초기화되지 않았습니다. initialize()를 먼저 호출하세요."
            );
        }
        return instance;
    }
    
    /**
     * 유효한 Access Token 반환
     * 
     * <p>토큰이 없거나 만료된 경우 자동으로 갱신합니다.
     * Refresh Token이 있으면 우선 사용하고, 실패하면 JWT Assertion으로 폴백합니다.</p>
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
                logger.debug("다른 스레드가 이미 토큰을 갱신함");
                return accessToken;
            }
            
            // 토큰 갱신
            refreshTokenInternal();
            return accessToken;
            
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Access Token 강제 갱신
     * 
     * <p>토큰이 유효하더라도 강제로 갱신합니다.
     * Refresh Token이 있으면 우선 사용합니다.</p>
     * 
     * @throws RuntimeException Token 발급 실패 시
     */
    public void forceRefresh() {
        lock.writeLock().lock();
        try {
            logger.info("토큰 강제 갱신 요청");
            refreshTokenInternal();
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
     * <p>갱신 전략:</p>
     * <ol>
     *   <li>Refresh Token이 있으면 우선 사용</li>
     *   <li>Refresh Token 실패 시 JWT Assertion으로 폴백</li>
     * </ol>
     * 
     * @throws RuntimeException Token 발급 실패 시
     */
    private void refreshTokenInternal() {
        AccessTokenResponse response = null;
        boolean usedRefreshToken = false;
        
        try {
            // 1. Refresh Token이 있으면 우선 사용
            if (refreshToken != null && !refreshToken.isEmpty()) {
                try {
                    logger.info("Refresh Token을 사용하여 토큰 갱신 시도");
                    response = oauthClient.refreshAccessToken(refreshToken);
                    usedRefreshToken = true;
                    logger.info("Refresh Token으로 토큰 갱신 성공");
                } catch (Exception e) {
                    logger.warn("Refresh Token 갱신 실패, JWT Assertion으로 폴백: {}", e.getMessage());
                    // Refresh Token 실패 시 null로 초기화하여 다음엔 바로 JWT 사용
                    refreshToken = null;
                }
            }
            
            // 2. Refresh Token이 없거나 실패한 경우 JWT Assertion 사용
            if (response == null) {
                logger.info("JWT Assertion을 사용하여 새 토큰 발급");
                response = oauthClient.getAccessToken(scope);
                usedRefreshToken = false;
            }
            
            // 3. 토큰 정보 업데이트
            updateTokenInfo(response);
            
            // 4. 로그 출력
            long remainingSeconds = expiresAt - Instant.now().getEpochSecond();
            logger.info("토큰 갱신 완료 - 방법: {}, 만료: {}, 남은시간: {}초, refresh_token: {}", 
                usedRefreshToken ? "Refresh Token" : "JWT Assertion",
                Instant.ofEpochSecond(expiresAt),
                remainingSeconds,
                refreshToken != null ? "있음" : "없음");
            
        } catch (IOException e) {
            logger.error("토큰 갱신 실패", e);
            throw new RuntimeException("Access Token 갱신 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 토큰 정보 업데이트
     * 
     * @param response Access Token 응답
     */
    private void updateTokenInfo(AccessTokenResponse response) {
        this.accessToken = response.getAccessToken();
        this.tokenType = response.getTokenType();
        
        // Refresh Token 업데이트 (있는 경우에만)
        if (response.getRefreshToken() != null && !response.getRefreshToken().isEmpty()) {
            this.refreshToken = response.getRefreshToken();
            logger.debug("새로운 Refresh Token 저장 완료");
        }
        
        // 만료 시간 계산 (현재 시간 + expires_in)
        long currentTime = Instant.now().getEpochSecond();
        this.expiresAt = currentTime + response.getExpiresIn();
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
     * 현재 Refresh Token 반환
     * 
     * @return 현재 저장된 Refresh Token (없으면 null)
     */
    public String getCurrentRefreshToken() {
        lock.readLock().lock();
        try {
            return refreshToken;
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
     * <p>유효한 토큰을 자동으로 획득하여 "Bearer {access_token}" 형식으로 반환합니다.</p>
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
     * Refresh Token 존재 여부 확인
     * 
     * @return Refresh Token이 있으면 true
     */
    public boolean hasRefreshToken() {
        lock.readLock().lock();
        try {
            return refreshToken != null && !refreshToken.isEmpty();
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
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║       NAVER WORKS Access Token 정보                       ║");
            System.out.println("╠═══════════════════════════════════════════════════════════╣");
            System.out.printf("║ Scope          : %-41s ║%n", scope);
            System.out.printf("║ Access Token   : %-41s ║%n", 
                accessToken != null ? "***" + accessToken.substring(Math.max(0, accessToken.length() - 10)) : "없음");
            System.out.printf("║ Refresh Token  : %-41s ║%n", 
                refreshToken != null ? "있음 (***" + refreshToken.substring(Math.max(0, refreshToken.length() - 6)) + ")" : "없음");
            System.out.printf("║ Token Type     : %-41s ║%n", tokenType);
            System.out.printf("║ 만료 시간      : %-41s ║%n", 
                expiresAt > 0 ? Instant.ofEpochSecond(expiresAt) : "없음");
            System.out.printf("║ 남은 시간      : %-38s초 ║%n", getTimeUntilExpiration());
            System.out.printf("║ 유효 여부      : %-41s ║%n", isTokenValid() ? "✓ 유효" : "✗ 만료/없음");
            System.out.printf("║ 갱신 방법      : %-41s ║%n", 
                refreshToken != null ? "Refresh Token 사용 가능" : "JWT Assertion만 사용");
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 간단한 토큰 상태 출력
     */
    public void printTokenStatus() {
        lock.readLock().lock();
        try {
            long remaining = getTimeUntilExpiration();
            System.out.printf("[Token Status] Valid: %s | Remaining: %ds | Has Refresh: %s | Auto-Refresh: %s%n",
                isTokenValid() ? "YES" : "NO",
                remaining,
                hasRefreshToken() ? "YES" : "NO",
                autoRefreshEnabled.get() ? "ENABLED" : "DISABLED");
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 백그라운드 자동 갱신 시작
     * 
     * <p>백그라운드 스케줄러를 시작하여 주기적으로 토큰 만료 시간을 체크하고,
     * 만료 10분 전이면 자동으로 Refresh Token을 사용하여 갱신합니다.</p>
     * 
     * <h3>동작 방식:</h3>
     * <ol>
     *   <li>1분마다 토큰 만료 시간 체크</li>
     *   <li>만료 10분 전이면 갱신 시도</li>
     *   <li>Refresh Token 우선 사용, 실패 시 JWT Assertion으로 폴백</li>
     *   <li>갱신 실패 시 최대 3회 재시도</li>
     * </ol>
     * 
     * @return 자동 갱신 시작 성공 여부
     */
    public boolean startAutoRefresh() {
        if (autoRefreshEnabled.compareAndSet(false, true)) {
            logger.info("백그라운드 자동 갱신 시작 - 체크 주기: {}초, 갱신 버퍼: {}분",
                AUTO_REFRESH_CHECK_INTERVAL, REFRESH_BUFFER_SECONDS / 60);
            
            refreshScheduler = AsyncExecutor.scheduleAtFixedRate(
                this::checkAndRefreshToken,
                AUTO_REFRESH_CHECK_INTERVAL,  // 초기 지연
                AUTO_REFRESH_CHECK_INTERVAL,  // 주기
                TimeUnit.SECONDS
            );
            
            logger.info("✓ 백그라운드 자동 갱신 활성화됨");
            return true;
        } else {
            logger.warn("백그라운드 자동 갱신이 이미 실행 중입니다");
            return false;
        }
    }
    
    /**
     * 백그라운드 자동 갱신 중지
     * 
     * @return 자동 갱신 중지 성공 여부
     */
    public boolean stopAutoRefresh() {
        if (autoRefreshEnabled.compareAndSet(true, false)) {
            if (refreshScheduler != null && !refreshScheduler.isCancelled()) {
                refreshScheduler.cancel(false);
                logger.info("✓ 백그라운드 자동 갱신 중지됨");
            }
            return true;
        } else {
            logger.warn("백그라운드 자동 갱신이 실행 중이 아닙니다");
            return false;
        }
    }
    
    /**
     * 자동 갱신 활성화 여부 확인
     * 
     * @return 자동 갱신이 활성화되어 있으면 true
     */
    public boolean isAutoRefreshEnabled() {
        return autoRefreshEnabled.get();
    }
    
    /**
     * 토큰 체크 및 자동 갱신 (백그라운드 스케줄러에서 호출)
     */
    private void checkAndRefreshToken() {
        try {
            lock.readLock().lock();
            boolean needsRefresh = false;
            long remainingSeconds = 0;
            
            try {
                // 토큰이 없거나 만료 임박한지 확인
                if (accessToken == null || accessToken.isEmpty()) {
                    needsRefresh = true;
                    logger.debug("[자동갱신] 토큰 없음 - 갱신 필요");
                } else {
                    remainingSeconds = getTimeUntilExpiration();
                    if (remainingSeconds <= REFRESH_BUFFER_SECONDS) {
                        needsRefresh = true;
                        logger.info("[자동갱신] 토큰 만료 임박 (남은시간: {}초) - 갱신 시작", remainingSeconds);
                    } else {
                        logger.debug("[자동갱신] 토큰 유효 (남은시간: {}초)", remainingSeconds);
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
            
            // 갱신이 필요하면 쓰기 락을 획득하고 갱신
            if (needsRefresh) {
                refreshWithRetry();
            }
            
        } catch (Exception e) {
            logger.error("[자동갱신] 토큰 체크 중 오류 발생", e);
        }
    }
    
    /**
     * 재시도 로직을 포함한 토큰 갱신
     */
    private void refreshWithRetry() {
        int retryCount = 0;
        Exception lastException = null;
        
        while (retryCount < MAX_RETRY_COUNT) {
            try {
                lock.writeLock().lock();
                try {
                    // Double-check: 다른 스레드가 이미 갱신했을 수 있음
                    if (isTokenValid()) {
                        logger.debug("[자동갱신] 다른 스레드가 이미 토큰을 갱신함");
                        return;
                    }
                    
                    // 토큰 갱신
                    refreshTokenInternal();
                    logger.info("[자동갱신] ✓ 토큰 갱신 성공 (재시도: {}/{})", retryCount, MAX_RETRY_COUNT);
                    return;
                    
                } finally {
                    lock.writeLock().unlock();
                }
                
            } catch (Exception e) {
                lastException = e;
                retryCount++;
                
                if (retryCount < MAX_RETRY_COUNT) {
                    long backoffDelay = retryCount * 2000L; // 2초, 4초, 6초
                    logger.warn("[자동갱신] 토큰 갱신 실패 (재시도 {}/{}) - {}ms 후 재시도: {}",
                        retryCount, MAX_RETRY_COUNT, backoffDelay, e.getMessage());
                    
                    try {
                        Thread.sleep(backoffDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        logger.error("[자동갱신] 재시도 대기 중 인터럽트 발생");
                        return;
                    }
                } else {
                    logger.error("[자동갱신] ✗ 토큰 갱신 최종 실패 ({}회 재시도)", MAX_RETRY_COUNT, lastException);
                }
            }
        }
    }
    
    /**
     * 리소스 정리 및 종료
     * 
     * <p>백그라운드 자동 갱신 스케줄러를 중지하고 리소스를 정리합니다.</p>
     */
    public void shutdown() {
        logger.info("NwAccessTokenManager 종료 시작");
        
        // 자동 갱신 중지
        stopAutoRefresh();
        
        // 토큰 정보 초기화
        lock.writeLock().lock();
        try {
            accessToken = null;
            refreshToken = null;
            tokenType = null;
            expiresAt = 0;
        } finally {
            lock.writeLock().unlock();
        }
        
        logger.info("NwAccessTokenManager 종료 완료");
    }
    
    /**
     * 인스턴스 초기화 (테스트용)
     */
    static void resetInstance() {
        if (instance != null) {
            instance.shutdown();
        }
        instance = null;
        logger.debug("NwAccessTokenManager 인스턴스 리셋");
    }
}