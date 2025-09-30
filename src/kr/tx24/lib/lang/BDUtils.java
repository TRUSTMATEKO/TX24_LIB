package kr.tx24.lib.lang;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BDUtils {

    /**
     * Number를 BigDecimal로 변환
     * @param n Number
     * @return BigDecimal
     */
    public static BigDecimal valueOf(Number n) {
        if (n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte) {
            return BigDecimal.valueOf(n.longValue());
        }
        return BigDecimal.valueOf(n.doubleValue());
    }

    /**
     * double 값을 소수점 scale로 리턴 (버림)
     * @param db double 값
     * @param scale 소수점 자리
     * @return BigDecimal
     */
    public static BigDecimal valueOf(double db, int scale) {
        return BigDecimal.valueOf(db).setScale(scale, RoundingMode.DOWN);
    }

    /**
     * BigDecimal을 문자열로 변환
     * @param bigDecimal
     * @return String
     */
    public static String toString(BigDecimal bigDecimal) {
        return bigDecimal == null ? BigDecimal.ZERO.toPlainString() : bigDecimal.toPlainString();
    }

    /**
     * 0으로 초기화된 BigDecimal 생성
     * @param scale 소수점 자리
     * @return BigDecimal
     */
    public static BigDecimal zero(int scale) {
        return BigDecimal.ZERO.setScale(scale);
    }

    /**
     * 값이 0인지 확인
     */
    public static boolean isZero(BigDecimal digit) {
        return digit.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 음수인지 확인
     */
    public static boolean isNegative(BigDecimal digit) {
        return digit.signum() < 0;
    }

    /**
     * 양수인지 확인 (0 포함)
     */
    public static boolean isPositive(BigDecimal digit) {
        return digit.signum() >= 0;
    }

    /**
     * 음수를 양수로 변환
     */
    public static BigDecimal toPositive(BigDecimal digit) {
        return digit.signum() < 0 ? digit.negate() : digit;
    }

    /**
     * 양수를 음수로 변환
     */
    public static BigDecimal toNegative(BigDecimal digit) {
        return digit.signum() > 0 ? digit.negate() : digit;
    }
}
