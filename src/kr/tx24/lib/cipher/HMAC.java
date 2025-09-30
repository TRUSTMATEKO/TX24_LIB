package kr.tx24.lib.cipher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * @author Administrator
 *
 */
public class HMAC {

	/**
	 * 
	 */
	public HMAC() {
		// TODO Auto-generated constructor stub
	}
	
	/**
	 * HMAC(Hash-based Message Authentication Code), SHA-256(Secure Hash Algorithm)의 합성어이며, 인증 코드를 해시값을 이용해 만드는 방법을 말한다.
	 * 단방향 암호화 기법으로 복호화가 불가능하다.
	 * @param key 비밀 키
	 * @param value 변경할 값
	 * @return
	 */
	public static String hmacSha256(String key, String value) {
		return hmacSha(key,value,"HmacSHA256");
	}
	
	
	
	public static String hmacSha(String key, String value, String SHA) {
		try {
			SecretKeySpec signingKey = new SecretKeySpec(key.getBytes("UTF-8"), SHA);
			Mac mac = Mac.getInstance(SHA);
			mac.init(signingKey);
			byte[] rawHmac = mac.doFinal(value.getBytes("UTF-8"));
			byte[] hexArray = {(byte)'0', (byte)'1', (byte)'2', (byte)'3', (byte)'4', (byte)'5', (byte)'6', (byte)'7', (byte)'8', (byte)'9', (byte)'a', (byte)'b', (byte)'c', (byte)'d', (byte)'e', (byte)'f'};
			byte[] hexChars = new byte[rawHmac.length * 2];
			for ( int j = 0; j < rawHmac.length; j++ ) {
				int v = rawHmac[j] & 0xFF;
				hexChars[j * 2] = hexArray[v >>> 4];
				hexChars[j * 2 + 1] = hexArray[v & 0x0F];
			}
			return new String(hexChars);
		}catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}
}
