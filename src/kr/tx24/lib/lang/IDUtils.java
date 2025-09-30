package kr.tx24.lib.lang;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;

/**
 * UNIQUE 한 값 생성 , publicKey, privateKey 등의 Key Generation 
 * 인증번호등 6자리 문자열 생성 등 ID를 생성할 때 사용한다.
 * 
 */
public class IDUtils {
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();
	private static final int DEFAULT_SALT = 8;


	public IDUtils() {
		// TODO Auto-generated constructor stub
	}
	
	
	/**
	 * 거래번호 등에 대한 UNIQUE 한 값을 생성 할때 사용하며.
	 * initial+17자리 시간 TIMESTAMP + RANDOM 값으로 구성된다.
	 * @param initial
	 * @return
	 */
	public static String getUnique(String initial) {
		 String timestamp = String.valueOf(System.currentTimeMillis()).substring(2, 13);
		 String uuidPart = UUID.randomUUID().toString().substring(0, 6);
		 return (initial + timestamp + uuidPart).toUpperCase();
	}
	
	
	/**
     * HEX 기반 키 생성.
     * <br>{@code salt} 바이트 길이의 난수 → HEX 문자열로 변환.
     *
     * @param salt 최소 8 이상 권장
     * @return HEX 문자열
     */
    public static String genKey(int salt) {
        int size = Math.max(salt, DEFAULT_SALT);
        byte[] buf = new byte[size];
        SECURE_RANDOM.nextBytes(buf);
        return CommonUtils.byteToHex(buf);
    }
	
	
	/**
	 * 32Byte BASE64 포맷으로 UNIQUE 한 값을 회신한다.
	 * 세션아이디/패스워드 변경 시 안내 키 생성 등 시간이 제한된 프로세스에 시간값을 동봉하여 사용할 수 있다. 
	 * Base64.decode 를 이용하면 앞의 13자리는 시간에 대한 Timestamp value 이다.
	 * @return
	 */
	public static String genKeyWithTime(){
		String id = System.currentTimeMillis() + genKey(8).substring(0, 11);
        return Base64.getEncoder().encodeToString(id.getBytes());
	}
	
	
	/**
	 * 숫자로 구성된 문자열 생성 
	 * 인증번호등에 사용할 수 있으며 length 에 따른 숫자 문자열이 회신된다.
	 * @param length
	 * @return
	 */
	public static String genDigit(int length) {
		StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(SECURE_RANDOM.nextInt(10)); // 0 ~ 9
        }
        return sb.toString();
	}
	
	
	/**
	 * 일반적인 UUID 는 SERVER, PC 등의 값을 사용하므로 일정 비슷한 경우가 많다.
	 * 이 부분을 보정하기 위하여 custom UUID 를 사용할 수 있다. 
	 * @return
	 */
	public static String getUUID() {
		byte[] randomBytes = new byte[16];
		new Random().nextBytes(randomBytes);

		long mostSigBits = 0;
		for (int i = 0; i < 8; i++) {
			mostSigBits = (mostSigBits << 8) | (randomBytes[i] & 0xff);
		}

		long leastSigBits = 0;
		for (int i = 8; i < 16; i++) {
			leastSigBits = (leastSigBits << 8) | (randomBytes[i] & 0xff);
		}

		return new UUID(mostSigBits, leastSigBits).toString();
	}
	
	
	/**
	 * tk_uuid 토큰 생성 
	 * @param length
	 * @return
	 */
	public static String getToken(int length) {
		String uuid = getUUID().replace("-", "");
        int tokenBodyLength = Math.max(0, length - 3);
        String body = uuid.length() >= tokenBodyLength ? uuid.substring(0, tokenBodyLength) : uuid;
        return "tk_" + body;
	}
	
	

	
	
	

}
