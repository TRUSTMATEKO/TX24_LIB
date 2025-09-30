package kr.tx24.lib.lang;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

public final class CompareUtils {

    private CompareUtils() {
        throw new AssertionError("Utility class cannot be instantiated.");
    }

    // ------------------ 숫자 비교 ------------------

    public static boolean isEquals(Number a, Number b) {
        return compare(a, b) == 0;
    }

    public static boolean isNotEquals(Number a, Number b) {
        return !isEquals(a, b);
    }

    public static boolean isLessThan(Number a, Number b) {
        return compare(a, b) < 0;
    }

    public static boolean isLessEqThan(Number a, Number b) {
        return compare(a, b) <= 0;
    }

    public static boolean isGreaterThan(Number a, Number b) {
        return compare(a, b) > 0;
    }

    public static boolean isGreaterEqThan(Number a, Number b) {
        return compare(a, b) >= 0;
    }

    public static boolean isBetween(Number s, Number min, Number max) {
        return isGreaterEqThan(s, min) && isLessEqThan(s, max);
    }

    public static boolean isNotBetween(Number s, Number min, Number max) {
        return !isBetween(s, min, max);
    }

    // ------------------ 문자열 길이 비교 ------------------

    public static boolean isEqualLength(String str, Number len) {
        return length(str) == toInt(len);
    }

    public static boolean isNotEqualLength(String str, Number len) {
        return !isEqualLength(str, len);
    }

    public static boolean isEqualLength(String a, String b) {
        return length(a) == length(b);
    }

    public static boolean isNotEqualLength(String a, String b) {
        return !isEqualLength(a, b);
    }

    public static boolean isLikeLength(String str, Number... lengths) {
        int len = length(str);
        return Arrays.stream(lengths).mapToInt(CompareUtils::toInt).anyMatch(l -> l == len);
    }

    public static boolean isNotLikeLength(String str, Number... lengths) {
        return !isLikeLength(str, lengths);
    }

    public static boolean isLessThanLength(String str, Number len) {
        return length(str) < toInt(len);
    }

    public static boolean isLessEqThanLength(String str, Number len) {
        return length(str) <= toInt(len);
    }

    public static boolean isGreaterThanLength(String str, Number len) {
        return length(str) > toInt(len);
    }

    public static boolean isGreaterEqThanLength(String str, Number len) {
        return length(str) >= toInt(len);
    }

    public static boolean isBetweenLength(String str, Number min, Number max) {
        int len = length(str);
        return len >= toInt(min) && len <= toInt(max);
    }

    public static boolean isNotBetweenLength(String str, Number min, Number max) {
        return !isBetweenLength(str, min, max);
    }

    // ------------------ 내부 헬퍼 ------------------

    private static int length(String str) {
        return str == null ? 0 : str.length();
    }

    private static int toInt(Number n) {
        return n == null ? 0 : n.intValue();
    }

    /**
     * null-safe 숫자 비교 (BigDecimal 기반)
     */
    public static int compare(Number a, Number b) {
        BigDecimal bdA = toBigDecimal(Objects.requireNonNullElse(a, 0));
        BigDecimal bdB = toBigDecimal(Objects.requireNonNullElse(b, 0));
        return bdA.compareTo(bdB);
    }

    private static BigDecimal toBigDecimal(Number n) {
        if (n instanceof BigDecimal) return (BigDecimal) n;
        if (n instanceof BigInteger) return new BigDecimal((BigInteger) n);
        if (n instanceof Byte || n instanceof Short || n instanceof Integer || n instanceof Long) {
            return BigDecimal.valueOf(n.longValue());
        }
        return BigDecimal.valueOf(n.doubleValue()); // float, double, etc.
    }
}
