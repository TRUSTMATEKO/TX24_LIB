package kr.tx24.lib.lang;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * 숫자 및 문자열 길이 비교 유틸리티
 * 
 * <p>null-safe한 숫자 비교와 문자열 길이 검증 기능을 제공합니다.</p>
 */
public final class CompareUtils {
    
    private CompareUtils() {
        throw new AssertionError("Utility class cannot be instantiated.");
    }
    
    // ==================== 숫자 비교 ====================
    
    /**
     * 두 숫자가 같은지 비교합니다.
     */
    public static boolean isEquals(Number a, Number b) {
        return compare(a, b) == 0;
    }
    
    /**
     * 두 숫자가 다른지 비교합니다.
     */
    public static boolean isNotEquals(Number a, Number b) {
        return !isEquals(a, b);
    }
    
    /**
     * a가 b보다 작은지 확인합니다.
     */
    public static boolean isLessThan(Number a, Number b) {
        return compare(a, b) < 0;
    }
    
    /**
     * a가 b보다 작거나 같은지 확인합니다.
     */
    public static boolean isLessEqThan(Number a, Number b) {
        return compare(a, b) <= 0;
    }
    
    /**
     * a가 b보다 큰지 확인합니다.
     */
    public static boolean isGreaterThan(Number a, Number b) {
        return compare(a, b) > 0;
    }
    
    /**
     * a가 b보다 크거나 같은지 확인합니다.
     */
    public static boolean isGreaterEqThan(Number a, Number b) {
        return compare(a, b) >= 0;
    }
    
    /**
     * s가 min과 max 사이(포함)에 있는지 확인합니다.
     */
    public static boolean isBetween(Number s, Number min, Number max) {
        return isGreaterEqThan(s, min) && isLessEqThan(s, max);
    }
    
    /**
     * s가 min과 max 사이(포함)에 없는지 확인합니다.
     */
    public static boolean isNotBetween(Number s, Number min, Number max) {
        return !isBetween(s, min, max);
    }
    
    // ==================== 문자열 길이 비교 ====================
    
    /**
     * 문자열 길이가 지정된 길이와 같은지 확인합니다.
     */
    public static boolean isEqualLength(String str, Number len) {
        return length(str) == toInt(len);
    }
    
    /**
     * 문자열 길이가 지정된 길이와 다른지 확인합니다.
     */
    public static boolean isNotEqualLength(String str, Number len) {
        return !isEqualLength(str, len);
    }
    
    /**
     * 두 문자열의 길이가 같은지 확인합니다.
     */
    public static boolean isEqualLength(String a, String b) {
        return length(a) == length(b);
    }
    
    /**
     * 두 문자열의 길이가 다른지 확인합니다.
     */
    public static boolean isNotEqualLength(String a, String b) {
        return !isEqualLength(a, b);
    }
    
    /**
     * 문자열 길이가 지정된 길이들 중 하나와 같은지 확인합니다.
     */
    public static boolean isLikeLength(String str, Number... lengths) {
        int len = length(str);
        return Arrays.stream(lengths).mapToInt(CompareUtils::toInt).anyMatch(l -> l == len);
    }
    
    /**
     * 문자열 길이가 지정된 길이들 중 어느 것과도 같지 않은지 확인합니다.
     */
    public static boolean isNotLikeLength(String str, Number... lengths) {
        return !isLikeLength(str, lengths);
    }
    
    /**
     * 문자열 길이가 지정된 길이보다 작은지 확인합니다.
     */
    public static boolean isLessThanLength(String str, Number len) {
        return length(str) < toInt(len);
    }
    
    /**
     * 문자열 길이가 지정된 길이보다 작거나 같은지 확인합니다.
     */
    public static boolean isLessEqThanLength(String str, Number len) {
        return length(str) <= toInt(len);
    }
    
    /**
     * 문자열 길이가 지정된 길이보다 큰지 확인합니다.
     */
    public static boolean isGreaterThanLength(String str, Number len) {
        return length(str) > toInt(len);
    }
    
    /**
     * 문자열 길이가 지정된 길이보다 크거나 같은지 확인합니다.
     */
    public static boolean isGreaterEqThanLength(String str, Number len) {
        return length(str) >= toInt(len);
    }
    
    /**
     * 문자열 길이가 min과 max 사이(포함)에 있는지 확인합니다.
     */
    public static boolean isBetweenLength(String str, Number min, Number max) {
        int len = length(str);
        return len >= toInt(min) && len <= toInt(max);
    }
    
    /**
     * 문자열 길이가 min과 max 사이(포함)에 없는지 확인합니다.
     */
    public static boolean isNotBetweenLength(String str, Number min, Number max) {
        return !isBetweenLength(str, min, max);
    }
    
    // ==================== Helper 메서드 ====================
    
    /**
     * 문자열 길이를 반환합니다 (null은 0).
     */
    private static int length(String str) {
        return str == null ? 0 : str.length();
    }
    
    /**
     * Number를 int로 변환합니다 (null은 0).
     */
    private static int toInt(Number n) {
        return n == null ? 0 : n.intValue();
    }
    
    /**
     * null-safe 숫자 비교 (BigDecimal 기반).
     * 
     * @param a 첫 번째 숫자 (null은 0으로 처리)
     * @param b 두 번째 숫자 (null은 0으로 처리)
     * @return a < b: 음수, a == b: 0, a > b: 양수
     */
    public static int compare(Number a, Number b) {
        BigDecimal bdA = toBigDecimal(Objects.requireNonNullElse(a, 0));
        BigDecimal bdB = toBigDecimal(Objects.requireNonNullElse(b, 0));
        return bdA.compareTo(bdB);
    }
    
    /**
     * Number를 BigDecimal로 변환합니다.
     */
    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal) return (BigDecimal) n;
        if (n instanceof BigInteger) return new BigDecimal((BigInteger) n);
        if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long) {
            return BigDecimal.valueOf(n.longValue());
        }
        return BigDecimal.valueOf(n.doubleValue()); // float, double, etc.
    }
}