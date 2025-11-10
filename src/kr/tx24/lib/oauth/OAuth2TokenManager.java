package kr.tx24.lib.oauth;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.oauth.OAuth2Client.OAuth2Exception;

/**
 * 범용 OAuth 2.0 Access Token 관리자
 * 
 * <p>Access Token의 생명주기를 관리하고, Refresh Token 기반 자동 갱신을 지원합니다.</p>
 * 
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>Thread-safe한 토큰 관리</li>
 *   <li>Refresh Token 기반 자동 토큰 갱신</li>
 *   <li>백그라운드 자동 갱신 스케줄러 (만료 10분 전)</li>
 *   <li>갱신 실패 시 최대 3회 재시도 (지수 백오프)</li>
 *   <li>Refresh Token이 없을 경우 폴백 전략 지원</li>
 *   <li>Read-Write Lock을 통한 동시성 제어</li>
 * </ul>
 * 
 * <h3>토큰 갱신 전략:</h3>
 * <ol>
 *   <li>백그라운드 스케줄러가 1분마다 토큰 만료 시간 체크</li>
 *   <li>만료 10분 전부터 자동 갱신 시도</li>
 *   <li>Refresh Token이 있으면 우선 사용</li>
 *   <li>Refresh Token 실패 시 폴백 함수 호출 (재발급 로직)</li>
 *   <li>갱신 실패 시 최대 3회 재시도 (2초, 4초, 6초 백오프)</li>
 * </ol>
 * 
 * <h3>사용 예시:</h3>
 * <pre>{@code
 * // 1. TokenManager 생성
 * OAuth2TokenManager manager = new OAuth2TokenManager(
 *     oauth2Client,
 *     () -> {
 *         // 폴백 전략: JWT로 새 토큰 발급
 *         String jwt = JwtUtils.builder()
 *             .privateKeyPem(privateKeyPem)
 *             .issuer(clientId)
 *             .subject(serviceAccount)
 *             .expirationHours(1)
 *             .build();
 *         return oauth2Client.getAccessTokenByJwt(
 *             jwt, clientId, clientSecret, "bot"
 *         );
 *     }
 * );
 * 
 * // 2. 초기 토큰 설정
 * manager.setToken(initialTokenResponse);
 * 
 * // 3. 자동 갱신 시작
 * manager.startAutoRefresh();
 * 
 * // 4. 유효한 토큰 획득 (자동 갱신됨)
 * String token = manager.getValidToken();
 * 
 * // 5. Authorization 헤더 획득
 * String authHeader = manager.getAuthorizationHeader();
 * }</pre>
 * 
 * @author TX24
 */
public class OAuth2TokenManager {
    
    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenManager.class);
    
    /** 토큰 갱신 버퍼 시간 (초) - 만료 10분 전에 갱신 */
    private static final long REFRESH_BUFFER_SECONDS = 600L; // 10 minutes
    
    /** 백그라운드 갱신 체크 주기 (초) */
    private static final long AUTO_REFRESH_CHECK_INTERVAL = 60L; // 1 minute
    
    /** 최대 재시도 횟수 */
    private static final int MAX_RETRY_COUNT = 3;
    
    /** 재시도 기본 간격 (밀리초) */
    private static final long RETRY_BASE_DELAY_MILLIS = 2000L; // 2 seconds
    
    /** OAuth 클라이언트 */
    private final OAuth2Client oauthClient;
    
    /** 토큰 재발급 폴백 함수 (Refresh Token이 없거나 실패 시) */
    private final TokenFallbackFunction fallbackFunction;
    
    /** 현재 토큰 정보 */
    private volatile LinkedMap<String, Object> tokenInfo;
    
    /** Read-Write Lock for thread safety */
    private final ReadWriteLock lock;
    
    /** 자동 갱신 활성화 여부 */
    private final AtomicBoolean autoRefreshEnabled;
    
    /** 백그라운드 갱신 스케줄러 */
    private volatile ScheduledFuture<?> refreshScheduler;
    
    /**
     * OAuth2TokenManager 생성자
     * 
     * @param oauthClient OAuth2Client 인스턴스
     * @param fallbackFunction 토큰 재발급 폴백 함수 (null 가능)
     */
    public OAuth2TokenManager(OAuth2Client oauthClient, TokenFallbackFunction fallbackFunction) {
        if (oauthClient == null) {
            throw new IllegalArgumentException("oauthClient는 필수입니다.");
        }
        
        this.oauthClient = oauthClient;
        this.fallbackFunction = fallbackFunction;
        this.lock = new ReentrantReadWriteLock();
        this.autoRefreshEnabled = new AtomicBoolean(false);
        this.refreshScheduler = null;
        
        logger.info("OAuth2TokenManager 초기화 완료 - 자동갱신버퍼: {}분, 재시도: {}회", 
            REFRESH_BUFFER_SECONDS / 60, MAX_RETRY_COUNT);
    }
    
    /**
     * 토큰 정보 설정
     * 
     * @param tokenInfo 토큰 정보 (access_token, refresh_token, expires_in 등)
     */
    public void setToken(LinkedMap<String, Object> tokenInfo) {
        if (tokenInfo == null || !tokenInfo.containsKey("access_token")) {
            throw new IllegalArgumentException("유효한 토큰 정보가 아닙니다.");
        }
        
        lock.writeLock().lock();
        try {
            this.tokenInfo = tokenInfo;
            
            // issued_at이 없으면 현재 시간으로 설정
            if (!tokenInfo.containsKey("issued_at")) {
                tokenInfo.put("issued_at", System.currentTimeMillis() / 1000);
            }
            
            logger.info("토큰 설정 완료 - expires_in: {}초, refresh_token: {}", 
                tokenInfo.getInt("expires_in", 0),
                tokenInfo.containsKey("refresh_token") ? "있음" : "없음");
            
        } finally {
            lock.writeLock().unlock();
        }
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
                return tokenInfo.getString("access_token");
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
                return tokenInfo.getString("access_token");
            }
            
            // 토큰 갱신
            refreshTokenInternal();
            return tokenInfo.getString("access_token");
            
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
            logger.info("토큰 강제 갱신 요청");
            refreshTokenInternal();
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 토큰 유효성 검증
     * 
     * @return 토큰이 유효하면 true
     */
    private boolean isTokenValid() {
        if (tokenInfo == null || !tokenInfo.containsKey("access_token")) {
            return false;
        }
        
        long currentTime = System.currentTimeMillis() / 1000; // Unix timestamp (초)
        long issuedAt = tokenInfo.getLong("issued_at", 0);
        long expiresIn = tokenInfo.getLong("expires_in", 0);
        long expiresAt = issuedAt + expiresIn;
        long validUntil = expiresAt - REFRESH_BUFFER_SECONDS;
        
        return currentTime < validUntil;
    }
    
    /**
     * Access Token 갱신 (내부 메서드)
     * 
     * <p>갱신 전략:</p>
     * <ol>
     *   <li>Refresh Token이 있으면 우선 사용</li>
     *   <li>Refresh Token 실패 시 폴백 함수 호출</li>
     * </ol>
     * 
     * @throws RuntimeException Token 발급 실패 시
     */
    private void refreshTokenInternal() {
        LinkedMap<String, Object> newTokenInfo = null;
        boolean usedRefreshToken = false;
        
        try {
            // 1. Refresh Token이 있으면 우선 사용
            if (tokenInfo != null && tokenInfo.containsKey("refresh_token")) {
                String refreshToken = tokenInfo.getString("refresh_token");
                String clientId = tokenInfo.getString("client_id", "");
                String clientSecret = tokenInfo.getString("client_secret", "");
                
                if (!refreshToken.isEmpty() && !clientId.isEmpty() && !clientSecret.isEmpty()) {
                    try {
                        logger.info("Refresh Token을 사용하여 토큰 갱신 시도");
                        newTokenInfo = oauthClient.refreshAccessToken(
                            refreshToken, clientId, clientSecret
                        );
                        usedRefreshToken = true;
                        logger.info("Refresh Token으로 토큰 갱신 성공");
                    } catch (Exception e) {
                        logger.warn("Refresh Token 갱신 실패, 폴백으로 전환: {}", e.getMessage());
                    }
                }
            }
            
            // 2. Refresh Token이 없거나 실패한 경우 폴백 함수 호출
            if (newTokenInfo == null) {
                if (fallbackFunction != null) {
                    logger.info("폴백 함수를 사용하여 새 토큰 발급");
                    newTokenInfo = fallbackFunction.getToken();
                    usedRefreshToken = false;
                } else {
                    throw new IllegalStateException(
                        "Refresh Token이 없고 폴백 함수도 설정되지 않았습니다."
                    );
                }
            }
            
            // 3. 토큰 정보 업데이트
            if (newTokenInfo != null) {
                // 기존 client_id, client_secret 유지 (갱신 시 사용)
                if (tokenInfo != null) {
                    if (tokenInfo.containsKey("client_id")) {
                        newTokenInfo.put("client_id", tokenInfo.getString("client_id"));
                    }
                    if (tokenInfo.containsKey("client_secret")) {
                        newTokenInfo.put("client_secret", tokenInfo.getString("client_secret"));
                    }
                }
                
                this.tokenInfo = newTokenInfo;
                
                // 로그 출력
                long remainingSeconds = getRemainingSeconds();
                logger.info("토큰 갱신 완료 - 방법: {}, expires_in: {}초, 남은시간: {}초, refresh_token: {}", 
                    usedRefreshToken ? "Refresh Token" : "Fallback",
                    tokenInfo.getInt("expires_in", 0),
                    remainingSeconds,
                    tokenInfo.containsKey("refresh_token") ? "있음" : "없음");
            }
            
        } catch (Exception e) {
            logger.error("토큰 갱신 실패", e);
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
            return tokenInfo != null ? tokenInfo.getString("access_token") : null;
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
            return tokenInfo != null ? tokenInfo.getString("refresh_token") : null;
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
            return tokenInfo != null ? tokenInfo.getString("token_type", "Bearer") : "Bearer";
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
        String type = getTokenType();
        return type + " " + token;
    }
    
    /**
     * 토큰 만료까지 남은 시간 반환 (초)
     * 
     * @return 남은 시간 (초), 토큰이 없으면 0
     */
    public long getRemainingSeconds() {
        lock.readLock().lock();
        try {
            if (tokenInfo == null) {
                return 0L;
            }
            
            long currentTime = System.currentTimeMillis() / 1000;
            long issuedAt = tokenInfo.getLong("issued_at", 0);
            long expiresIn = tokenInfo.getLong("expires_in", 0);
            long expiresAt = issuedAt + expiresIn;
            
            return Math.max(0L, expiresAt - currentTime);
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
            return tokenInfo != null && tokenInfo.containsKey("refresh_token");
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 토큰 정보 전체 반환
     * 
     * @return 토큰 정보 (LinkedMap, 복사본)
     */
    public LinkedMap<String, Object> getTokenInfo() {
        lock.readLock().lock();
        try {
            return tokenInfo != null ? new LinkedMap<>(tokenInfo) : null;
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 백그라운드 자동 갱신 시작
     * 
     * @return 자동 갱신 시작 성공 여부
     */
    public boolean startAutoRefresh() {
        if (autoRefreshEnabled.compareAndSet(false, true)) {
            logger.info("백그라운드 자동 갱신 시작 - 체크 주기: {}초, 갱신 버퍼: {}분",
                AUTO_REFRESH_CHECK_INTERVAL, REFRESH_BUFFER_SECONDS / 60);
            
            refreshScheduler = AsyncExecutor.scheduleAtFixedRate(
                this::checkAndRefreshToken,
                AUTO_REFRESH_CHECK_INTERVAL,
                AUTO_REFRESH_CHECK_INTERVAL,
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
                if (tokenInfo == null || !tokenInfo.containsKey("access_token")) {
                    needsRefresh = true;
                    logger.debug("[자동갱신] 토큰 없음 - 갱신 필요");
                } else {
                    remainingSeconds = getRemainingSeconds();
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
                    long backoffDelay = RETRY_BASE_DELAY_MILLIS * retryCount; // 2초, 4초, 6초
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
     * 토큰 정보 출력 (디버깅용)
     */
    public void printTokenInfo() {
        lock.readLock().lock();
        try {
            if (tokenInfo == null) {
                System.out.println("토큰 정보가 없습니다.");
                return;
            }
            
            System.out.println("╔═══════════════════════════════════════════════════════════╗");
            System.out.println("║       OAuth 2.0 Access Token 정보                         ║");
            System.out.println("╠═══════════════════════════════════════════════════════════╣");
            
            String accessToken = tokenInfo.getString("access_token");
            System.out.printf("║ Access Token   : %-41s ║%n", 
                accessToken.length() > 10 ? "***" + accessToken.substring(Math.max(0, accessToken.length() - 10)) : "***");
            
            System.out.printf("║ Refresh Token  : %-41s ║%n", 
                hasRefreshToken() ? "있음" : "없음");
            
            System.out.printf("║ Token Type     : %-41s ║%n", getTokenType());
            System.out.printf("║ Expires In     : %-38s초 ║%n", tokenInfo.getInt("expires_in", 0));
            System.out.printf("║ 남은 시간      : %-38s초 ║%n", getRemainingSeconds());
            System.out.printf("║ 유효 여부      : %-41s ║%n", isTokenValid() ? "✓ 유효" : "✗ 만료/없음");
            System.out.printf("║ 자동 갱신      : %-41s ║%n", isAutoRefreshEnabled() ? "✓ 활성화" : "✗ 비활성화");
            
            System.out.println("╚═══════════════════════════════════════════════════════════╝");
        } finally {
            lock.readLock().unlock();
        }
    }
    
    /**
     * 리소스 정리 및 종료
     */
    public void shutdown() {
        logger.info("OAuth2TokenManager 종료 시작");
        
        stopAutoRefresh();
        
        lock.writeLock().lock();
        try {
            tokenInfo = null;
        } finally {
            lock.writeLock().unlock();
        }
        
        logger.info("OAuth2TokenManager 종료 완료");
    }
    
    /**
     * 토큰 재발급 폴백 함수 인터페이스
     */
    @FunctionalInterface
    public interface TokenFallbackFunction {
        /**
         * 새 토큰 발급
         * 
         * @return 새로 발급된 토큰 정보
         * @throws Exception 토큰 발급 실패 시
         */
        LinkedMap<String, Object> getToken() throws Exception;
    }
}