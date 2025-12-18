package kr.tx24.test.http;

import com.google.common.net.HttpHeaders;

import kr.tx24.lib.http.ua.UADetect;
import kr.tx24.lib.http.ua.UserAgent;

public class UATest {

	public static void main(String[] args) {
		UserAgent ua = UADetect.set("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36");
	}

}
