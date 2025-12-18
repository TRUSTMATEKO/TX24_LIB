package kr.tx24.lib.lang;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>
 * 개인정보 보호를 위해 문자열의 특정 부분을 마스킹(가림) 처리합니다.
 * 모든 입력은 전각/반각 변환을 거쳐 일관되게 처리됩니다.
 * </p>
 */
public class MaskUtils {

    private static final Logger logger = LoggerFactory.getLogger(MaskUtils.class);
    private static final char DEFAULT_REPLACE = '*';

    // 이메일 마스킹 정규식 패턴: @ 앞의 일부를 마스킹. (%s는 마스킹 시작 위치)
    private static final String REGEX_EMAIL_MASKING_FORMAT = "(?<=^.{%s}).(?=.*@)";

    // 정규표현식 패턴을 static final로 선언하여 컴파일 오버헤드를 제거
    // 전화번호 마스킹: (앞 2-3자리)-?(가운데 3-4자리)-?(끝 4자리)
    private static final Pattern MASK_PHONE = Pattern.compile("(\\d{2,3})-?(\\d{3,4})-?(\\d{4})$");

    // 주민등록번호 마스킹: (앞 6자리) [하이픈 옵션] (성별 1자리) (나머지 6자리)
    private static final Pattern MASK_RRN = Pattern.compile("(\\d{6})[\\-]?(\\d{1})\\d{6}$");

    /**
     * 운전면허번호 마스킹 패턴 (지역2 + 분류2) + [하이픈 옵션] + 일련번호6 + [하이픈 옵션] + 검증번호2
     * G1: 지역(2)+분류(2) (노출)
     * G2: Sep1(-) (옵션)
     * G3: 일련번호(6) (마스킹)
     * G4: Sep2(-) (옵션)
     * G5: 검증번호(2) (마스킹)
     */
    private static final Pattern MASK_DRIVER_LICENSE = Pattern.compile("([가-힣]{2}\\d{2})([\\-]?)(\\d{6})([\\-]?)(\\d{2})$");
    
    private static final int CARD_MASK_LENGTH = 5;      // 마스킹할 길이 (고정 5자리)
    private static final int CARD_VISIBLE_TAIL_LENGTH = 3; // 노출할 끝자리 길이 (고정 3자리)
    private static final int CARD_MIN_REQUIRED_LENGTH = CARD_MASK_LENGTH + CARD_VISIBLE_TAIL_LENGTH; // 최소 유효 길이 (8자리)


    /**
     * 입력 문자열의 전각(Full-width) 문자를 반각(Half-width) 문자로 변환합니다.
     * 모든 마스킹 처리 전에 호출되어 일관된 처리를 보장합니다.
     *
     * @param s 변환할 원본 문자열
     * @return 반각 문자로 변환된 문자열
     */
    private static String toHalfWidth(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            // 전각 아스키 문자 (U+FF01 ~ U+FF5E) -> 반각 아스키 문자 (U+0021 ~ U+007E)
            if (c >= 0xFF01 && c <= 0xFF5E) {
                sb.append((char) (c - 0xFEE0));
            // 전각 공백 (U+3000) -> 반각 공백 (U+0020)
            } else if (c == 0x3000) { 
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    /**
     * 문자열의 특정 범위를 대체 문자(replace)로 마스킹합니다.
     *
     * @param src 원본 문자열
     * @param replace 마스킹에 사용할 대체 문자
     * @param startIdx 마스킹을 시작할 인덱스 (0부터 시작)
     * @param length 마스킹할 길이
     * @return 마스킹된 문자열
     */
    public static String masking(String src, char replace, int startIdx, int length) {
        return masking(src, replace, null, startIdx, length);
    }
    
    /**
     * 문자열 마스킹 (startIdx, length)
     *
     * @param src 원본 문자열
     * @param replace 대체 문자
     * @param exclude 마스킹에서 제외할 문자 배열 (null 허용)
     * @param startIdx 마스킹 시작 인덱스
     * @param length 마스킹할 길이
     * @return 마스킹된 문자열
     */
    public static String masking(String src, char replace, char[] exclude, int startIdx, int length) {
        if (CommonUtils.isBlank(src)) {
            return "";
        }
        
        // 1. 전각/반각 변환
        String halfWidthSrc = toHalfWidth(src);
        int srcLength = halfWidthSrc.length();
        
        // 2. 인덱스 유효성 검사 및 조정
        if (startIdx < 0) {
            startIdx = 0;
        }
        if (startIdx >= srcLength) {
            return halfWidthSrc;
        }
        
        int actualLength = Math.min(length, srcLength - startIdx);
        if (actualLength <= 0) {
            return halfWidthSrc;
        }

        // 3. 제외 문자 처리 (Set으로 변환하여 O(1) 탐색 최적화)
        Set<Character> excludeSet = null;
        if (exclude != null && exclude.length > 0) {
            excludeSet = new HashSet<>(exclude.length);
            for (char c : exclude) {
                excludeSet.add(c);
            }
        }
        
        // 4. StringBuilder를 사용하여 마스킹 처리
        StringBuilder sb = new StringBuilder(halfWidthSrc);

        for (int i = startIdx; i < startIdx + actualLength; i++) {
            char current = halfWidthSrc.charAt(i);
            
            // 제외 문자 Set에 포함되지 않는 경우에만 마스킹
            if (excludeSet == null || !excludeSet.contains(current)) {
                sb.setCharAt(i, replace);
            }
        }

        return sb.toString();
    }


    /**
     * 문자열 전체를 기본 대체 문자('*')로 마스킹
     */
    public static String masking(String src) {
        if (CommonUtils.isBlank(src)) {
            return "";
        }
        return masking(src, DEFAULT_REPLACE, 0, src.length());
    }

    /**
     * 문자열 마스킹 (기본 대체 문자, 시작 위치부터 끝까지)
     */
    public static String masking(String src, int startIdx) {
        if (CommonUtils.isBlank(src)) {
            return "";
        }
        return masking(src, DEFAULT_REPLACE, startIdx, src.length());
    }
    
    /**
     * 계좌번호를 마스킹합니다.
     * <p><b>고정 규칙:</b> 마지막 2자리를 제외하고, 그 이전 3자리를 마스킹합니다.</p>
     * <p>예: 1234567890 -> 12345***90 (총 10자리 기준)</p>
     *
     * @param src 원본 계좌번호 문자열
     * @return 마스킹된 계좌번호 문자열
     */
    public static String account(String src) {
        if (CommonUtils.isBlank(src)) {
            return "";
        }
        
        if(isAlreadyMasked(src)) {
        	return src;
        }
        
        final int POST_MASK_KEEP_LENGTH = 2; // 노출할 마지막 자리수 (예: 90)
        final int MIDDLE_MASK_LENGTH = 3;    // 마스킹할 중간 자리수 (예: 678)
        final int MIN_LENGTH = POST_MASK_KEEP_LENGTH + MIDDLE_MASK_LENGTH; // 최소 5자리 필요
        
        
        int srcLength = src.length();
        
        try {
            if (srcLength < MIN_LENGTH) {
                // 문자열 길이가 마스킹 최소 길이(5) 미만이면 전체 노출
                return src;
            }

            // 1. 마스킹 시작 및 종료 인덱스 계산
            int endKeepIndex = srcLength - POST_MASK_KEEP_LENGTH;   // 마지막 2자리가 시작하는 인덱스 (예: 8)
            int startMaskIndex = endKeepIndex - MIDDLE_MASK_LENGTH; // 마스킹할 3자리가 시작하는 인덱스 (예: 5)
            
            // 2. 각 부분 문자열 구성
            // [Prefix] + [Mask] + [Suffix]
            String prefix = src.substring(0, startMaskIndex);
            String mask = String.valueOf(DEFAULT_REPLACE).repeat(MIDDLE_MASK_LENGTH);
            String suffix = src.substring(endKeepIndex);
            
            return prefix + mask + suffix;
            
        } catch (Exception e) {
            logger.warn("maskAccount error: {}", CommonUtils.getExceptionMessage(e));
            return src;
        }
    }

    /**
     * 이메일 주소를 마스킹합니다. (예: test****@example.com)
     *
     * @param email 원본 이메일 문자열
     * @param visiblePrefixLength 마스킹하지 않고 노출할 접두사 길이
     * @return 마스킹된 이메일 문자열
     */
    public static String email(String email, int visiblePrefixLength) {
        if (CommonUtils.isBlank(email)) {
            return "";
        }
        try {
            

            // 노출 길이 유효성 검사 (0 미만은 0으로 처리)
            int prefixLen = Math.max(0, visiblePrefixLength);
            
            // 이메일 정규식 패턴 생성 (prefixLen에 따라 마스킹 시작 위치 결정)
            String regex = String.format(REGEX_EMAIL_MASKING_FORMAT, prefixLen);
            Pattern pattern = Pattern.compile(regex);
            
            // 마스킹 로직
            Matcher matcher = pattern.matcher(email);
            
            return matcher.replaceAll(String.valueOf(DEFAULT_REPLACE));

        } catch (Exception e) {
            logger.warn("maskEmail error: {}", CommonUtils.getExceptionMessage(e));
            return email; // 오류 발생 시 원본 반환 (반각 변환 X)
        }
    }
    
    /**
     * 이메일 주소를 마스킹합니다. 노출 접두사 길이는 기본값 4를 사용합니다.
     */
    public static String email(String email) {
        return email(email, 4);
    }

    /**
     * 전화번호를 마스킹합니다. (예: 010-XXXX-5678)
     *
     * @param src 원본 전화번호 문자열
     * @return 마스킹된 전화번호 문자열
     */
    public static String phone(String src) {
        if (CommonUtils.isBlank(src)) {
            return "";
        }
        try {
            
            Matcher matcher = MASK_PHONE.matcher(src);
            
            if (matcher.find()) {
                // 그룹 2 (중앙 3~4자리)를 대체 문자로 마스킹
                String middleGroup = matcher.group(2);
                
                String replacement = String.valueOf(DEFAULT_REPLACE).repeat(middleGroup.length());
                
                // 정규식 매칭된 그룹 2만 대체
                return src.replaceFirst(Pattern.quote(middleGroup), replacement);
            }
            return src;
        } catch (Exception e) {
            logger.warn("maskPhone error: {}", CommonUtils.getExceptionMessage(e));
            return src;
        }
    }
    
    /**
     * 주민등록번호 마스킹 (생년월일 + 성별 1자리 노출, 나머지 마스킹)
     *
     * @param src 원본 주민등록번호
     * @return 마스킹된 주민등록번호
     */
    public static String rrn(String src) {
        if (CommonUtils.isBlank(src)) {
            return "";
        }
        try {
            Matcher matcher = MASK_RRN.matcher(src);
            
            if (matcher.find()) {
                // $1: 생년월일 6자리, $2: 성별 1자리 (노출)
                // 나머지 6자리를 마스킹
                String maskedTail = String.valueOf(DEFAULT_REPLACE).repeat(6);
                
                // $1$2 + ****** (하이픈은 패턴에 따라 생략 또는 포함됨)
                // replaceAll 대신 replaceFirst를 사용하여 일치하는 첫 번째 항목만 변경
                return matcher.replaceFirst("$1$2" + maskedTail);
            }
            return src;
        } catch (Exception e) {
            logger.warn("maskRRN error: {}", CommonUtils.getExceptionMessage(e));
            return src;
        }
    }
    
    /**
     * 운전면허번호 마스킹 (지역+분류 노출, 나머지 마스킹)
     * <p>예: 서울12-345678-90 -> 서울12-******-** 또는 서울1234567890 -> 서울12********</p>
     *
     * @param src 원본 운전면허번호
     * @return 마스킹된 운전면허번호
     */
    public static String driverLicense(String src) {
        if (CommonUtils.isBlank(src)) {
            return "";
        }
        try {
            
            Matcher matcher = MASK_DRIVER_LICENSE.matcher(src);
            
            if (matcher.find()) {
                // $1: 지역(2)+분류(2) (Keep)
                // $2: Sep1(-) (Keep)
                // $3: 일련번호(6) (Mask)
                // $4: Sep2(-) (Keep)
                // $5: 검증번호(2) (Mask)
                
                String maskedSerial = String.valueOf(DEFAULT_REPLACE).repeat(6);
                String maskedCheck = String.valueOf(DEFAULT_REPLACE).repeat(2);

                // Replacement: $1 (지역+분류) + $2 (하이픈1) + ****** (일련번호) + $4 (하이픈2) + ** (검증번호)
                String replacement = "$1" + "$2" + maskedSerial + "$4" + maskedCheck;
                return matcher.replaceFirst(replacement);
            }
            return src;
        } catch (Exception e) {
            logger.warn("maskDriverLicense error: {}", CommonUtils.getExceptionMessage(e));
            return src;
        }
    }
    
    
    public static String name(String src) {
    	if (CommonUtils.isBlank(src)) {
            return "";
        }
        String halfWidth = toHalfWidth(src);
        String normalized = Normalizer.normalize(halfWidth, Normalizer.Form.NFC);

        // 3. 유니코드 안전 마스킹
        return maskNameSafe(normalized);
    }
    
    
    /**
     * 카드 번호를 마스킹
     * <p><b>규칙:</b> 첫 부분은 노출, **마지막 3자리 앞 5자리**를 마스킹합니다.</p>
     * <p>입력 문자열에서 <b>하이픈('-'), 공백(' ') 등의 구분자를 제거하고</b> 마스킹을 수행합니다.</p>
     * <p>예시 (16자리): 1234-5678-9012-3456 -> 12345678*****456</p>
     *
     * @param src 원본 카드 번호 문자열
     * @return 마스킹된 카드 번호 문자열 (구분자 제거된 상태)
     */
    public static String card(String src) {
        if (CommonUtils.isBlank(src) || isAlreadyMasked(src)) { // 이중 마스킹 방지
            return src;
        }

        try {
            
            
            // 구분자('-', 공백 ' ') 제거하여 순수한 숫자 문자열만 남깁니다.
            // 정규식 [\\s\\-]+ 는 공백(탭, 줄바꿈 포함)과 하이픈을 모두 찾습니다.
            String cleanedSrc = src.replaceAll("[\\s\\-]+", "");
            final int length = cleanedSrc.length();
            
            // 2. 마스킹 유효 길이 검증
            if (length < CARD_MIN_REQUIRED_LENGTH) {
                 // 마스킹을 수행할 수 있는 최소 길이(8) 미만이면 마스킹 없이 반환
                 return src; 
            }
            
            // 3. 마스킹 영역 계산
            // 마스킹 시작 인덱스: length - 3(노출) - 5(마스크) = length - 8
            final int maskStartIndex = length - CARD_VISIBLE_TAIL_LENGTH - CARD_MASK_LENGTH;
            
            // 마스킹 종료 인덱스 (exclusive): length - 3(노출)
            final int maskEndIndex = length - CARD_VISIBLE_TAIL_LENGTH; 
            
            // 4. 고성능 마스킹 처리 (StringBuilder 사용)
            StringBuilder maskedCard = new StringBuilder(cleanedSrc);
            
            // 마스킹 영역 (maskStartIndex 부터 maskEndIndex 직전까지)의 문자를 '*'로 대체
            for (int i = maskStartIndex; i < maskEndIndex; i++) {
                maskedCard.setCharAt(i, DEFAULT_REPLACE);
            }
            
            return maskedCard.toString();
            
        } catch (Exception e) {
            logger.warn("maskCard error: {}", CommonUtils.getExceptionMessage(e));
            return src; // 오류 발생 시 원본 반환
        }
    }

    
    /**
     * 유니코드 코드포인트 기반 안전 마스킹
     * 길이별 규칙:
     * 1~2글자: 변경 없음
     * 3글자: 가운데 1개 *
     * 4글자: 가운데 2개 **
     * 5글자: 앞 2 + 가운데 2개 ** + 마지막 1
     * 6글자 이상: 앞 (len-4) + *** + 마지막 1
     */
    private static String maskNameSafe(String s) {
        int[] cps = s.codePoints().toArray(); // 유니코드 코드 포인트 배열
        int len = cps.length; 

        // 마스킹에 사용할 대체 문자의 코드 포인트 (char '*'의 코드 포인트 값 42)
        final int REPLACE_CP = (int) DEFAULT_REPLACE; 

        if (len <= 2) return s;

        if (len == 3) {
            // [C1] + [*] + [C3]
            return new String(new int[]{cps[0], REPLACE_CP, cps[2]}, 0, 3);
        }

        if (len == 4) {
            // [C1] + [*] + [*] + [C4]
            return new String(new int[]{cps[0], REPLACE_CP, REPLACE_CP, cps[3]}, 0, 4);
        }

        if (len == 5) {
            // [C1] + [C2] + [*] + [*] + [C5]
            return new String(new int[]{cps[0], cps[1], REPLACE_CP, REPLACE_CP, cps[4]}, 0, 5);
        }

        // len >= 6 → 앞 2 + *** + 마지막 (len-5)
        // [C1] + [C2] + [*] + [*] + [*] + [C(len-2)...C(len)]
        
        StringBuilder sb = new StringBuilder();
        
        // 1. 앞 두 글자 (C1, C2) 노출
        sb.appendCodePoint(cps[0]);
        sb.appendCodePoint(cps[1]);
        
        // 2. 마스킹 3글자 (***)
        sb.append(DEFAULT_REPLACE);
        sb.append(DEFAULT_REPLACE);
        sb.append(DEFAULT_REPLACE);

        // 3. 나머지 글자 (len-5 개) 노출
        // 시작 인덱스는 2 + 3 = 5 이며, 끝 인덱스는 len
        for (int i = 5; i < len; i++) {
            sb.appendCodePoint(cps[i]);
        }

        return sb.toString();
    }
    
    private static boolean isAlreadyMasked(String src) {
        if (CommonUtils.isBlank(src)) {
            return false;
        }
        return src.contains("*") || src.contains("x") || src.contains("X");
    }

}