package kr.tx24.lib.crypt;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Argon2 기반의 비밀번호 해싱 및 검증 유틸리티.
 * 
 * <p>두 가지 모드 제공:</p>
 * <ul>
 *   <li>{@link #hash(String)} / {@link #verify(String, String)} → 강력한 보안(고메모리/고비용)</li>
 *   <li>{@link #fastHash(String)} / {@link #fastVerify(String, String)} → 빠른 처리(저메모리/저비용)</li>
 * </ul>
 *
 * 반환 포맷은 다음과 같다:
 * <pre>
 * salt(Base64) + "$" + hash(Base64) 로 24+1+44 = 69 자리
 * </pre>
 *
 * <p>⚠️ 주의: fast 모드는 보안성이 낮으므로 실서비스에서는 권장하지 않는다.</p>
 */

public class Argon2 {

	private static final Logger logger = LoggerFactory.getLogger(Argon2.class);
	private static final SecureRandom secureRandom = new SecureRandom();

	
	// ---- Strong (hash) ----
	private static final int MEMORY_COST_HASH = 1048576;   // 1 GiB
    private static final int ITERATIONS_HASH = 4;
    private static final int PARALLELISM_HASH = 1;

    // ---- Fast (fastHash) ----
    private static final int MEMORY_COST_FAST = 262144;    // 256 MiB
    private static final int ITERATIONS_FAST = 3;
    private static final int PARALLELISM_FAST = 1;

    // ---- Common ----
    private static final int SALT_LENGTH  = 16;
    private static final int HASH_LENGTH  = 32;
    private static final int ARGON2_TYPE  = Argon2Parameters.ARGON2_id; // Argon2id , Argon2id > Argon2i > Argon2d
    
	
    
    public static String hash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_COST_HASH)
                .withIterations(ITERATIONS_HASH)
                .withParallelism(PARALLELISM_HASH)
                .withSalt(salt);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] hash = new byte[HASH_LENGTH];
        generator.generateBytes(password.getBytes(), hash);

        return Base64.getEncoder().encodeToString(salt) + "$" +
               Base64.getEncoder().encodeToString(hash);
    }
    
    
    /**
     * 빠른 처리 설정으로 Argon2 해시 생성 (보안성은 낮아짐)
     */
    public static String fastHash(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_COST_FAST)
                .withIterations(ITERATIONS_FAST)
                .withParallelism(PARALLELISM_FAST)
                .withSalt(salt);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] hash = new byte[HASH_LENGTH];
        generator.generateBytes(password.getBytes(), hash);

        return Base64.getEncoder().encodeToString(salt) + "$" +
               Base64.getEncoder().encodeToString(hash);
    }
    

    /**
     * 강력한 보안 해시 검증
     */
    public static boolean verify(String password, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Argon2 hash format.");
        }

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] actualHash = Base64.getDecoder().decode(parts[1]);

        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_COST_HASH)
                .withIterations(ITERATIONS_HASH)
                .withParallelism(PARALLELISM_HASH)
                .withSalt(salt);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] generatedHash = new byte[actualHash.length];
        generator.generateBytes(password.getBytes(), generatedHash);

        return Arrays.equals(actualHash, generatedHash);
    }
    
    
    /**
     * 빠른 처리 해시 검증
     */
    public static boolean fastVerify(String password, String storedHash) {
        String[] parts = storedHash.split("\\$");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid Argon2 hash format.");
        }

        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] actualHash = Base64.getDecoder().decode(parts[1]);

        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(ARGON2_TYPE)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(MEMORY_COST_FAST)
                .withIterations(ITERATIONS_FAST)
                .withParallelism(PARALLELISM_FAST)
                .withSalt(salt);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] generatedHash = new byte[actualHash.length];
        generator.generateBytes(password.getBytes(), generatedHash);

        return Arrays.equals(actualHash, generatedHash);
    }


    /**
     * 실행 예제
     */
    public static void main(String[] args) {
        String password = "MySecretPassword123!";

        // 1. 해시 생성
        long startHash = System.nanoTime();
        String argon2Hash = hash(password);
        long endHash = System.nanoTime();

        System.out.println("--- Bouncy Castle Argon2 Hashing Result ---");
        System.out.println("Original Password: " + password);
        System.out.println("Argon2 Hash: " + argon2Hash);
        System.out.printf("Hashing Time: %.2f ms%n", (endHash - startHash) / 1_000_000.0);
        
        System.out.println("\n--- Verification ---");

        // 2. 성공적인 검증
        long startVerify = System.nanoTime();
        boolean success = verify(password, argon2Hash);
        long endVerify = System.nanoTime();
        System.out.printf("Verification Success (%s): %b%n", password, success);
        System.out.printf("Verification Time: %.2f ms%n", (endVerify - startVerify) / 1_000_000.0);

        // 3. 실패하는 검증
        String wrongPassword = "WrongPassword!";
        boolean failure = verify(wrongPassword, argon2Hash);
        System.out.printf("Verification Failure (%s): %b%n", wrongPassword, failure);
        
        
        
        System.out.println("\n\n--- Bouncy Castle Argon2 Fast Hashing Result ---");
        startHash = System.nanoTime();
        argon2Hash = fastHash(password);
        endHash = System.nanoTime();

        System.out.println("--- Bouncy Castle Argon2 Hashing Result ---");
        System.out.println("Original Password: " + password);
        System.out.println("Argon2 Hash: " + argon2Hash);
        System.out.printf("Hashing Time: %.2f ms%n", (endHash - startHash) / 1_000_000.0);
        
        System.out.println("\n--- Verification ---");

        // 2. 성공적인 검증
        startVerify = System.nanoTime();
        success = fastVerify(password, argon2Hash);
        endVerify = System.nanoTime();
        System.out.printf("Verification Success (%s): %b%n", password, success);
        System.out.printf("Verification Time: %.2f ms%n", (endVerify - startVerify) / 1_000_000.0);

        // 3. 실패하는 검증
        failure = verify(wrongPassword, argon2Hash);
        System.out.printf("Verification Failure (%s): %b%n", wrongPassword, failure);
        
    }
}