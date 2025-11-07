package kr.tx24.naverwork.oauth;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class NwConfig {

	/** Client ID */
    private final String clientId;
    
    /** Client Secret */
    private final String clientSecret;
    
    /** Service Account ID */
    private final String serviceAccount;
    
    /** Private Key (PEM 형식) */
    private final String privateKey;
    
    /** 권한 범위 */
    private final String scope;
    
    /**
     * NaverWorksConfig 생성자
     * 
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param serviceAccount Service Account ID
     * @param privateKey Private Key (PEM 형식)
     * @param scope 권한 범위
     */
    public NwConfig(String clientId, String clientSecret, String serviceAccount, 
                           String privateKey, String scope) {
        validateConfig(clientId, clientSecret, serviceAccount, privateKey, scope);
        
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.serviceAccount = serviceAccount;
        this.privateKey = privateKey;
        this.scope = scope != null ? scope : "bot";
    }
    
    /**
     * Properties 파일에서 설정 로드
     * 
     * @param propertiesFilePath Properties 파일 경로
     * @return NaverWorksConfig 인스턴스
     * @throws IOException 파일 읽기 실패 시
     * @throws IllegalArgumentException 필수 설정이 없는 경우
     */
    public static NwConfig fromPropertiesFile(String propertiesFilePath) throws IOException {
        Properties props = new Properties();
        
        try (InputStream input = new FileInputStream(propertiesFilePath)) {
            props.load(input);
        }
        
        String clientId = props.getProperty("naver.works.client.id");
        String clientSecret = props.getProperty("naver.works.client.secret");
        String serviceAccount = props.getProperty("naver.works.service.account");
        String privateKeyPath = props.getProperty("naver.works.private.key.path");
        String scope = props.getProperty("naver.works.scope", "bot");
        
        // Private Key 파일 읽기
        String privateKey;
        if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
            privateKey = new String(Files.readAllBytes(Paths.get(privateKeyPath)));
        } else {
            // Private Key가 직접 Properties에 있는 경우
            privateKey = props.getProperty("naver.works.private.key");
        }
        
        return new NwConfig(clientId, clientSecret, serviceAccount, privateKey, scope);
    }
    
    /**
     * 환경변수에서 설정 로드
     * 
     * <p>다음 환경변수를 사용합니다:</p>
     * <ul>
     *   <li>NAVER_WORKS_CLIENT_ID</li>
     *   <li>NAVER_WORKS_CLIENT_SECRET</li>
     *   <li>NAVER_WORKS_SERVICE_ACCOUNT</li>
     *   <li>NAVER_WORKS_PRIVATE_KEY (또는 NAVER_WORKS_PRIVATE_KEY_PATH)</li>
     *   <li>NAVER_WORKS_SCOPE (선택, 기본값: "bot")</li>
     * </ul>
     * 
     * @return NaverWorksConfig 인스턴스
     * @throws IOException Private Key 파일 읽기 실패 시
     * @throws IllegalArgumentException 필수 환경변수가 없는 경우
     */
    public static NwConfig fromEnvironment() throws IOException {
        String clientId = System.getenv("NAVER_WORKS_CLIENT_ID");
        String clientSecret = System.getenv("NAVER_WORKS_CLIENT_SECRET");
        String serviceAccount = System.getenv("NAVER_WORKS_SERVICE_ACCOUNT");
        String privateKey = System.getenv("NAVER_WORKS_PRIVATE_KEY");
        String privateKeyPath = System.getenv("NAVER_WORKS_PRIVATE_KEY_PATH");
        String scope = System.getenv("NAVER_WORKS_SCOPE");
        
        // Private Key 처리
        if (privateKey == null || privateKey.isEmpty()) {
            if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
                privateKey = new String(Files.readAllBytes(Paths.get(privateKeyPath)));
            }
        }
        
        return new NwConfig(clientId, clientSecret, serviceAccount, privateKey, scope);
    }
    
    /**
     * System Properties에서 설정 로드
     * 
     * @return NaverWorksConfig 인스턴스
     * @throws IOException Private Key 파일 읽기 실패 시
     * @throws IllegalArgumentException 필수 설정이 없는 경우
     */
    public static NwConfig fromSystemProperties() throws IOException {
        String clientId = System.getProperty("naver.works.client.id");
        String clientSecret = System.getProperty("naver.works.client.secret");
        String serviceAccount = System.getProperty("naver.works.service.account");
        String privateKey = System.getProperty("naver.works.private.key");
        String privateKeyPath = System.getProperty("naver.works.private.key.path");
        String scope = System.getProperty("naver.works.scope");
        
        // Private Key 처리
        if (privateKey == null || privateKey.isEmpty()) {
            if (privateKeyPath != null && !privateKeyPath.isEmpty()) {
                privateKey = new String(Files.readAllBytes(Paths.get(privateKeyPath)));
            }
        }
        
        return new NwConfig(clientId, clientSecret, serviceAccount, privateKey, scope);
    }
    
    /**
     * 설정 유효성 검증
     * 
     * @param clientId Client ID
     * @param clientSecret Client Secret
     * @param serviceAccount Service Account ID
     * @param privateKey Private Key
     * @param scope 권한 범위
     * @throws IllegalArgumentException 필수 설정이 없는 경우
     */
    private void validateConfig(String clientId, String clientSecret, String serviceAccount, 
                                String privateKey, String scope) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Client ID가 설정되지 않았습니다.");
        }
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("Client Secret이 설정되지 않았습니다.");
        }
        if (serviceAccount == null || serviceAccount.trim().isEmpty()) {
            throw new IllegalArgumentException("Service Account가 설정되지 않았습니다.");
        }
        if (privateKey == null || privateKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Private Key가 설정되지 않았습니다.");
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
     * Client Secret 반환
     * 
     * @return Client Secret
     */
    public String getClientSecret() {
        return clientSecret;
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
     * Private Key 반환
     * 
     * @return Private Key (PEM 형식)
     */
    public String getPrivateKey() {
        return privateKey;
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
     * 설정 정보 출력 (민감 정보는 마스킹)
     */
    public void printConfig() {
        System.out.println("=== NAVER WORKS 설정 정보 ===");
        System.out.println("Client ID: " + maskSensitiveData(clientId));
        System.out.println("Client Secret: " + maskSensitiveData(clientSecret));
        System.out.println("Service Account: " + serviceAccount);
        System.out.println("Private Key: " + (privateKey != null ? "설정됨 (***)" : "없음"));
        System.out.println("Scope: " + scope);
        System.out.println("==============================");
    }
    
    /**
     * 민감 정보 마스킹
     * 
     * @param data 원본 데이터
     * @return 마스킹된 데이터
     */
    private String maskSensitiveData(String data) {
        if (data == null || data.length() < 8) {
            return "***";
        }
        return data.substring(0, 4) + "***" + data.substring(data.length() - 4);
    }
    
    @Override
    public String toString() {
        return "NaverWorksConfig{" +
            "clientId='" + maskSensitiveData(clientId) + '\'' +
            ", serviceAccount='" + serviceAccount + '\'' +
            ", scope='" + scope + '\'' +
            '}';
    }
}
