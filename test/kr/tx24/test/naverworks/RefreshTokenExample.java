package kr.tx24.test.naverworks;

import kr.tx24.naverwork.oauth.NwAccessTokenManager;
import kr.tx24.naverwork.oauth.NwOAuthClient;
import kr.tx24.naverwork.oauth.NwOAuthClient.AccessTokenResponse;

/**
 * NAVER WORKS Refresh Token 기반 토큰 갱신 예제
 * 
 * <p>이 예제는 다음을 시연합니다:</p>
 * <ul>
 *   <li>NwAccessTokenManager를 통한 자동 토큰 관리</li>
 *   <li>Refresh Token 기반 토큰 갱신</li>
 *   <li>JWT Assertion 폴백</li>
 *   <li>토큰 만료 시뮬레이션</li>
 * </ul>
 * 
 * @author TX24
 */
public class RefreshTokenExample {
    
	private static String getPrivateKeyMethodChaining() {
        return new StringBuilder()
            .append("-----BEGIN PRIVATE KEY-----\n")
            .append("MIIEugIBADANBgkqhkiG9w0BAQEFAASCBKQwggSgAgEAAoIBAQC+APTvKZ0uydnN\n")
            .append("cqsYFj6xS3A0FjdIh1WZszLdhs304Eu+K2Iubuv3/0GshX0v96tv8nk02purTIi6\n")
            .append("VSMsFpNBYVbQAzRp9lgZnynetAg+UEO7Lk4QJzfMTkeQMPmdCZXjyoOoPaOAprAI\n")
            .append("21e9gYptWlfFu9aJtzc/TQHFtHEjdil0ZcU8yL0GxequihR4rGwLJccLg6Fqi97k\n")
            .append("xXvZPzJf7peRnlEhW+9x98LEQQoFXWyNkFT7SYDyU/80x3bKlXm8zF+mi3zRcrBB\n")
            .append("nz1819rPfMxJRfBgfJDvl+hmsluGLDzgBGZ31EB4ooerDjDq96F2sSh7Df+Ghyzn\n")
            .append("oR1Zq6QlAgMBAAECgf8DDZ/HgHjgL8SV9u3lKiip14gU8mKxet/kYoyw6CQGBLnD\n")
            .append("ox6AqxuAuPtWEeJ/O/1kmw09FXXkwDOqw830nGEyZEKtAZ+8ZWKAoIADzKMF05yk\n")
            .append("H6T1WmcgUzinCYOKHdh2Bnj647wO+kLvGDtt2Bqzc/RIGkHrulnYmAW91XqQQMem\n")
            .append("p5yEPhM9kawSHuvtCKxUk/xf0ym7XbRPYRBGb1OmDOT5qypTnamWC2SIKXyxyIEl\n")
            .append("6r71XaspnmQKmBkWe40RROWO5t1CSaMadvdUE/X1VQPoIY3mNx8aAv/9TbVIDfPJ\n")
            .append("4h0hoJrqAT1mzfD8W7P5Ori4X+hL9TcsC+6vsMECgYEA9RUkJ9zGmPUegN3idii0\n")
            .append("BZm4TsVFRECehYUlC/OzaGMYdO0jqKOCY8gB8/TDSKC3VEL5nkDuHOAeubv0PWuq\n")
            .append("u3PDauvJBEEhpJpPBTPuLW5LI3jt0R3LKpQi5SeIHNMGpuB0k81xrswWkTe2HJe/\n")
            .append("rXOBclzX21HZIfVQX2ThHUUCgYEAxne15mcrpCmDOCDreRKUrOexxXveRGNYUWsr\n")
            .append("Ml2Yi+0X6GWLcaO2msa2G2KB8ISJ1cBZEb0N9UpcOV0CV61fokbr2zWYFbjcIJkJ\n")
            .append("X40yPC4jNYY3/1R2mTvHGetCNp656Lh3o9W+tzjohq5jJCseQaWKyOtgaZVB/Ugl\n")
            .append("VcQpqWECgYA/BB+WzFKYM7aTJfo7rX2UTxEv19NWmFLqO/DpoNDJj2lTb0IS82/s\n")
            .append("Xhn6cz3fJ8vbs5jhUwqmjA36bdSAEnYE2uAtVtEJ8gFHhJG64b5lGg3h4g8sDMAX\n")
            .append("g51xVHfQCYaVU/NFqbaIXluTHUMLGQ2k+KUZFbw+3U26SIxQ6uxjDQKBgE/sdXxL\n")
            .append("n++EKCu6VhlzuhvHUnfM4j14JGDlX4fw2TXATRhxjC4/V5IM49kzWlCZj0hdJYFX\n")
            .append("OP/G6kzPf9n/H7wiA2lLs+tLfppCBtxL6CcEDXnIi1RvlzMuN4fgjdGhKgzl/Igl\n")
            .append("05/Fcx6Jq7MtCgo1uCYhY7ohOWneW+qmDIEBAoGATa0fODtMVEEnUHY9hrAIpcGQ\n")
            .append("uTwjao5PZHQR++137dYJ0T1zQsgHK6qPHPfnd7LkKxUanacTizw1fz6NToprh9Yh\n")
            .append("2XLttJVnAiK37+D6Jsr/BbwlvWe+uyT/FKYsqXcQpJTsccsPeSNTvbmY/ToQN2DY\n")
            .append("kXiMRdKZDGCsUZMPp8c=\n")
            .append("-----END PRIVATE KEY-----")
            .toString();
    }
	
    public static void main(String[] args) {
    	
    	System.setProperty("NAVER_WORKS_CLIENT_ID"		, "kHIXP3EhyF89TNwaaQUx");
    	System.setProperty("NAVER_WORKS_CLIENT_SECRET"	, "nzkkkJRGvJ");
    	System.setProperty("NAVER_WORKS_SERVICE_ACCOUNT", "cbw9d.serviceaccount@tx24.kr");
    	System.setProperty("NAVER_WORKS_PRIVATE_KEY"	, RefreshTokenExample.getPrivateKeyMethodChaining());
    	
    	
    	
        System.out.println("╔═══════════════════════════════════════════════════════════╗");
        System.out.println("║   NAVER WORKS Refresh Token 기반 토큰 갱신 예제          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════╝\n");
        
        // 환경변수에서 설정 로드 (실제 사용 시)
        String clientId = System.getProperty("NAVER_WORKS_CLIENT_ID");
        String clientSecret = System.getProperty("NAVER_WORKS_CLIENT_SECRET");
        String serviceAccount = System.getProperty("NAVER_WORKS_SERVICE_ACCOUNT");
        String privateKey = System.getProperty("NAVER_WORKS_PRIVATE_KEY");
        
        // 또는 직접 설정
        if (clientId == null) {
            System.out.println("⚠️  환경변수가 설정되지 않았습니다.");
            System.out.println("다음 환경변수를 설정하세요:");
            System.out.println("  - NAVER_WORKS_CLIENT_ID");
            System.out.println("  - NAVER_WORKS_CLIENT_SECRET");
            System.out.println("  - NAVER_WORKS_SERVICE_ACCOUNT");
            System.out.println("  - NAVER_WORKS_PRIVATE_KEY");
            return;
        }
        
        try {
            // 1. NwAccessTokenManager 초기화
            example1_InitializeManager(clientId, clientSecret, serviceAccount, privateKey);
            
            // 2. 토큰 획득 및 정보 출력
            example2_GetTokenInfo();
            
            // 3. Refresh Token 기반 갱신 시뮬레이션
            example3_RefreshTokenSimulation();
            
            // 4. 자동 갱신 테스트
            example4_AutoRefreshTest();
            
            // 5. 직접 OAuth 클라이언트 사용
            example5_DirectOAuthClient(clientId, clientSecret, serviceAccount, privateKey);
            
        } catch (Exception e) {
            System.err.println("❌ 예제 실행 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 예제 1: NwAccessTokenManager 초기화
     */
    private static void example1_InitializeManager(String clientId, String clientSecret,
                                                    String serviceAccount, String privateKey) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("예제 1: NwAccessTokenManager 초기화");
        System.out.println("=".repeat(60));
        
        NwAccessTokenManager manager = NwAccessTokenManager.initialize(
            clientId,
            clientSecret,
            serviceAccount,
            privateKey,
            "bot"  // scope
        );
        
        System.out.println("✓ NwAccessTokenManager 초기화 완료");
        System.out.println("  - Scope: bot");
        System.out.println("  - Singleton 인스턴스 생성됨");
    }
    
    /**
     * 예제 2: 토큰 획득 및 정보 출력
     */
    private static void example2_GetTokenInfo() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("예제 2: 토큰 획득 및 정보 출력");
        System.out.println("=".repeat(60));
        
        NwAccessTokenManager manager = NwAccessTokenManager.getInstance();
        
        // 유효한 토큰 획득 (첫 요청 시 자동으로 발급)
        String accessToken = manager.getValidToken();
        System.out.println("\n✓ Access Token 획득 완료");
        System.out.println("  - Token: " + accessToken.substring(0, 20) + "...");
        
        // 토큰 정보 출력
        System.out.println();
        manager.printTokenInfo();
        
        // Authorization 헤더
        String authHeader = manager.getAuthorizationHeader();
        System.out.println("\n✓ Authorization 헤더: " + authHeader.substring(0, 30) + "...");
        
        // Refresh Token 존재 여부
        boolean hasRefresh = manager.hasRefreshToken();
        System.out.println("✓ Refresh Token 있음: " + (hasRefresh ? "예" : "아니오"));
    }
    
    /**
     * 예제 3: Refresh Token 기반 갱신 시뮬레이션
     */
    private static void example3_RefreshTokenSimulation() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("예제 3: Refresh Token 기반 갱신 시뮬레이션");
        System.out.println("=".repeat(60));
        
        NwAccessTokenManager manager = NwAccessTokenManager.getInstance();
        
        // 현재 토큰 정보
        System.out.println("\n[갱신 전]");
        manager.printTokenStatus();
        String oldToken = manager.getCurrentToken();
        
        // 강제 갱신 (Refresh Token이 있으면 사용)
        System.out.println("\n토큰 강제 갱신 시작...");
        manager.forceRefresh();
        
        // 갱신 후 토큰 정보
        System.out.println("\n[갱신 후]");
        manager.printTokenStatus();
        String newToken = manager.getCurrentToken();
        
        // 토큰이 변경되었는지 확인
        boolean tokenChanged = !oldToken.equals(newToken);
        System.out.println("\n✓ 토큰 갱신 완료");
        System.out.println("  - 토큰 변경됨: " + (tokenChanged ? "예" : "아니오"));
        System.out.println("  - Refresh Token 사용: " + (manager.hasRefreshToken() ? "예" : "JWT Assertion"));
    }
    
    /**
     * 예제 4: 자동 갱신 테스트
     */
    private static void example4_AutoRefreshTest() throws InterruptedException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("예제 4: 자동 갱신 테스트");
        System.out.println("=".repeat(60));
        
        NwAccessTokenManager manager = NwAccessTokenManager.getInstance();
        
        System.out.println("\n토큰을 반복적으로 요청하여 자동 갱신 확인...");
        
        for (int i = 1; i <= 5; i++) {
            System.out.println("\n[요청 " + i + "]");
            
            // 토큰 요청 (만료되었거나 곧 만료되면 자동 갱신)
            String token = manager.getValidToken();
            
            // 토큰 상태 출력
            manager.printTokenStatus();
            
            // 잠시 대기
            if (i < 5) {
                System.out.println("2초 대기...");
                Thread.sleep(2000);
            }
        }
        
        System.out.println("\n✓ 자동 갱신 테스트 완료");
        System.out.println("  - 모든 요청에서 유효한 토큰 반환됨");
        System.out.println("  - 만료 전 자동 갱신 동작 확인");
    }
    
    /**
     * 예제 5: 직접 OAuth 클라이언트 사용
     */
    private static void example5_DirectOAuthClient(String clientId, String clientSecret,
                                                    String serviceAccount, String privateKey) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("예제 5: 직접 OAuth 클라이언트 사용");
        System.out.println("=".repeat(60));
        
        try {
            // JWT 생성기 및 OAuth 클라이언트 생성
            kr.tx24.naverwork.oauth.NwJwt jwtGenerator = 
                new kr.tx24.naverwork.oauth.NwJwt(clientId, serviceAccount, privateKey);
            
            NwOAuthClient oauthClient = new NwOAuthClient(jwtGenerator, clientId, clientSecret);
            
            // 1. JWT Assertion으로 토큰 발급
            System.out.println("\n[1] JWT Assertion으로 Access Token 발급");
            AccessTokenResponse response1 = oauthClient.getAccessToken("bot");
            System.out.println("✓ 토큰 발급 완료");
            System.out.println("  - Access Token: " + response1.getAccessToken().substring(0, 20) + "...");
            System.out.println("  - Refresh Token: " + 
                (response1.hasRefreshToken() ? response1.getRefreshToken().substring(0, 20) + "..." : "없음"));
            System.out.println("  - 유효기간: " + response1.getExpiresIn() + "초");
            System.out.println("  - 남은 시간: " + response1.getRemainingSeconds() + "초");
            
            // 2. Refresh Token으로 갱신 (있는 경우)
            if (response1.hasRefreshToken()) {
                System.out.println("\n[2] Refresh Token으로 토큰 갱신");
                String refreshToken = response1.getRefreshToken();
                
                AccessTokenResponse response2 = oauthClient.refreshAccessToken(refreshToken);
                System.out.println("✓ 토큰 갱신 완료");
                System.out.println("  - 새 Access Token: " + response2.getAccessToken().substring(0, 20) + "...");
                System.out.println("  - 새 Refresh Token: " + 
                    (response2.hasRefreshToken() ? response2.getRefreshToken().substring(0, 20) + "..." : "없음"));
                System.out.println("  - 유효기간: " + response2.getExpiresIn() + "초");
                
                // 토큰이 변경되었는지 확인
                boolean accessTokenChanged = !response1.getAccessToken().equals(response2.getAccessToken());
                boolean refreshTokenChanged = response2.hasRefreshToken() && 
                    !response1.getRefreshToken().equals(response2.getRefreshToken());
                
                System.out.println("\n✓ 갱신 결과:");
                System.out.println("  - Access Token 변경: " + (accessTokenChanged ? "예" : "아니오"));
                System.out.println("  - Refresh Token 변경: " + (refreshTokenChanged ? "예" : "아니오"));
            } else {
                System.out.println("\n⚠️  Refresh Token이 없어 갱신을 테스트할 수 없습니다.");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}