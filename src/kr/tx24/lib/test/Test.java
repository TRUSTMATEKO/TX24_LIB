package kr.tx24.lib.test;

import java.util.Scanner;

import kr.tx24.lib.otp.TOTPUtils;

public class Test {

	public static void main(String[] args) {
		String secretKey = "NFZNCAIWBQ5EVLWW";
		
		//System.out.println(TOTPUtils.getOTPAuthURL("JJU", "JUSEOP", secretKey));
		/*
		String serverExpectedOTP = TOTPUtils.generateTOTP(secretKey); // 서버가 예상하는 현재 OTP
		System.out.println("✅ 당신이 입력한 내용은: [" + input + "] 입니다.");
		System.out.println("⏰ 서버 예상 OTP: [" + serverExpectedOTP + "]"); // 추가!
		System.out.println("✅ TOTP 검증 : [" + TOTPUtils.validateTOTP(secretKey, input) + "] 입니다.");
		
		
		System.out.println(TOTPUtils.getOTPAuthURL("JJU", "JUSEOPT", secretKey));
		*/
		Scanner scanner = new Scanner(System.in);
        String input;

        System.out.println("반복 입력 프로그램 시작! '종료'를 입력하면 끝납니다.");
        System.out.println("------------------------------------------");
	
        // 2. 무한 루프 시작 (조건에 따라 break를 사용하여 빠져나옴)
        while (true) {
            System.out.print("입력하세요 > ");
            
            // 3. 사용자 입력 받기 (엔터를 칠 때까지 기다림)
            // nextLine()은 한 줄 전체(엔터 포함)를 읽고 엔터를 버립니다.
            input = scanner.nextLine();
            
            // 4. 입력 내용에 따른 조건 처리
            if (input.equalsIgnoreCase("종료")) {
                System.out.println("👋 프로그램을 종료합니다.");
                break; // '종료'를 입력하면 반복문 탈출
            }

            // 5. 입력 처리 로직 실행
            System.out.println("✅ 당신이 입력한 내용은: [" + input + "] 입니다.");
            System.out.println("✅ TOTP 검증 : [" + TOTPUtils.validateTOTP(secretKey, input) + "] 입니다.");
        }

        // 6. 사용한 Scanner 객체 닫기 (메모리 누수 방지)
        scanner.close();
        
	}
}
