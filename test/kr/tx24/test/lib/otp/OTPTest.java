package kr.tx24.test.lib.otp;

import kr.tx24.lib.otp.TOTPUtils;

public class OTPTest {

	public static void main(String[] args) {
		String secret = TOTPUtils.generateSecretKey("JUSEOP");
		System.out.println(secret);
		String url = TOTPUtils.getOTPAuthURL("TX24","JUSEOP",secret);
		System.out.println(url);

	}

}
