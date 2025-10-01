package kr.tx24.lib.otp;

import java.security.SecureRandom;
import java.time.Instant;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;

public class TOTPUtils {

    private static final int SECRET_SIZE = 10; // 10 bytes = 80 bits
    private static final String TOTP_ALGORITHM = "HmacSHA1";
    private static final int TIME_STEP_SECONDS = 30;
    private static final int OTP_DIGITS = 6;

    private static final SecureRandom random = new SecureRandom();
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    /*
     * 랜덤 기반 secretKey 생성 , 권장하지 않음
     */
    public static String generateSecretKey() {
        byte[] buffer = new byte[SECRET_SIZE];
        random.nextBytes(buffer);
        return new Base32().encodeAsString(buffer);
    }
    
    /*
     * 고유값을 반영한 secretKey 생성 
     */
    public static String generateSecretKey(String unique) {
        try {
            // 1. 랜덤 바이트
            byte[] randomBytes = new byte[SECRET_SIZE];
            random.nextBytes(randomBytes);

            // 2. 현재 시간
            long now = System.currentTimeMillis();

            // 3. 고유값(userId + timestamp + random) 섞기
            byte[] userBytes = unique.getBytes("UTF-8");
            byte[] combined = new byte[userBytes.length + randomBytes.length + 8];
            System.arraycopy(userBytes, 0, combined, 0, userBytes.length);
            System.arraycopy(randomBytes, 0, combined, userBytes.length, randomBytes.length);
            for (int i = 0; i < 8; i++) {
                combined[userBytes.length + randomBytes.length + i] = (byte)((now >> (i * 8)) & 0xFF);
            }

            // 4. SHA-1 해시
            java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest(combined);

            // 5. Base32 인코딩 (길이 SECRET_SIZE 유지)
            byte[] secretBytes = new byte[SECRET_SIZE];
            System.arraycopy(hash, 0, secretBytes, 0, SECRET_SIZE);
            return new Base32().encodeAsString(secretBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate secret key", e);
        }
    }


    
    public static String generateTOTP(String base32Secret) {
        byte[] key = new Base32().decode(base32Secret);
        long timeWindow = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        return generateHOTP(key, timeWindow);
    }

    
    public static boolean validateTOTP(String base32Secret, String otp) {
        byte[] key = new Base32().decode(base32Secret);
        long timeWindow = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;

        // ±1 timestep 허용
        for (long i = -1; i <= 1; i++) {
            String candidate = generateHOTP(key, timeWindow + i);
            if (candidate.equals(otp)) return true;
        }
        return false;
    }

    // Google/Microsoft Authenticator 등록 URL 생성
    public static String getOTPAuthURL(String issuer, String accountName, String secret) {
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=%d&period=%d",
                issuer, accountName, secret, issuer, OTP_DIGITS, TIME_STEP_SECONDS);
    }

    // HOTP 계산 (RFC 4226)
    private static String generateHOTP(byte[] key, long counter) {
        try {
            byte[] counterBytes = new byte[8];
            for (int i = 7; i >= 0; i--) {
                counterBytes[i] = (byte) (counter & 0xff);
                counter >>= 8;
            }

            Mac mac = Mac.getInstance(TOTP_ALGORITHM);
            mac.init(new SecretKeySpec(key, TOTP_ALGORITHM));
            byte[] hash = mac.doFinal(counterBytes);

            int offset = hash[hash.length - 1] & 0xf;
            int binary =
                    ((hash[offset] & 0x7f) << 24) |
                    ((hash[offset + 1] & 0xff) << 16) |
                    ((hash[offset + 2] & 0xff) << 8) |
                    (hash[offset + 3] & 0xff);

            int otp = binary % (int) Math.pow(10, OTP_DIGITS);
            return String.format("%0" + OTP_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


   
}
