package kr.tx24.test.lang;

import kr.tx24.lib.crypt.Argon2;

public class PWTest {


    public static void main(String[] args){
        // 현재 일반 모드의 메모리 설정은 1GiB이므로 VM options에 -Xmx2g를 지정한다.
        String password      = "Tmate3730!";
        String wrongPassword = "WrongPassword123!";

        // 1. 일반 모드: 반환된 salt$hash 문자열 전체를 저장하고 verify로 검증한다.
        String  storedHash   = Argon2.hash(password);
        boolean matched      = Argon2.verify(password, storedHash);
        boolean wrongMatched = Argon2.verify(wrongPassword, storedHash);

        System.out.println("[일반 모드]");
        System.out.println("해시: " + storedHash);
        System.out.println("해시 길이: " + storedHash.length());
        System.out.println("정상 비밀번호 검증 (예상: true): " + matched);
        System.out.println("잘못된 비밀번호 검증 (예상: false): " + wrongMatched);

        if (!matched || wrongMatched) {
            throw new AssertionError("일반 모드 비밀번호 검증 결과가 예상과 다릅니다.");
        }

        // 2. 비교 테스트용 fast 모드: fastHash 결과는 fastVerify로 검증한다.
        String  fastStoredHash   = Argon2.fastHash(password);
        boolean fastMatched      = Argon2.fastVerify(password, fastStoredHash);
        boolean fastWrongMatched = Argon2.fastVerify(wrongPassword, fastStoredHash);

        System.out.println();
        System.out.println("[Fast 모드]");
        System.out.println("해시: " + fastStoredHash);
        System.out.println("해시 길이: " + fastStoredHash.length());
        System.out.println("정상 비밀번호 검증 (예상: true): " + fastMatched);
        System.out.println("잘못된 비밀번호 검증 (예상: false): " + fastWrongMatched);

        if (!fastMatched || fastWrongMatched) {
            throw new AssertionError("Fast 모드 비밀번호 검증 결과가 예상과 다릅니다.");
        }
    }
}
