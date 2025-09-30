package kr.tx24.lib.lang;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class DecimalUtils {

    // ---------------- Value Of ----------------
    public static BigDecimal valueOf(int num) {
        return BigDecimal.valueOf(num);
    }

    public static BigDecimal valueOf(long num) {
        return BigDecimal.valueOf(num);
    }

    public static BigDecimal valueOf(double num) {
        return new BigDecimal(Double.toString(num));
    }

    public static BigDecimal valueOf(String num) {
        if (num == null || num.isEmpty()) {
            throw new IllegalArgumentException("String cannot be null or empty");
        }
        return new BigDecimal(num);
    }

    /** null 또는 빈 문자열을 0으로 변환 */
    public static BigDecimal valueOfNtoZ(String num) {
        if (num == null || num.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(num);
    }

    /** double 값에 scale 적용, 소수점 이하 버림 */
    public static BigDecimal valueOf(double num, int scale) {
        return new BigDecimal(Double.toString(num)).setScale(scale, RoundingMode.DOWN);
    }

    /** 0으로 초기화된 BigDecimal을 생성 */
    public static BigDecimal zero(int scale) {
        return BigDecimal.ZERO.setScale(scale);
    }

    // ---------------- Sign Check ----------------
    public static boolean isPositiveOrZero(BigDecimal d) {
        return d.signum() >= 0;
    }

    public static boolean isNegativeOrZero(BigDecimal d) {
        return d.signum() <= 0;
    }

    // ---------------- Sign Conversion ----------------
    public static BigDecimal toPositive(BigDecimal b) {
        return b.abs();
    }

    public static BigDecimal toNegative(BigDecimal b) {
        return b.signum() > 0 ? b.negate() : b;
    }

    // ---------------- Rounding ----------------
    /** 일반 반올림 (HALF_UP) */
    public static BigDecimal rounding(BigDecimal b, int scale) {
        return b.setScale(scale, RoundingMode.HALF_UP);
    }

    /** 버림 (DOWN) */
    public static BigDecimal down(BigDecimal b, int scale) {
        return b.setScale(scale, RoundingMode.DOWN);
    }

    /** 뱅커스 라운딩 (HALF_EVEN) */
    public static BigDecimal roundingVat(BigDecimal b, int scale) {
        return b.setScale(scale, RoundingMode.HALF_EVEN);
    }

    // ---------------- Comparison ----------------
    public static boolean isGE(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) >= 0;
    }

    public static boolean isGT(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) > 0;
    }

    public static boolean isEQ(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) == 0;
    }

    public static boolean isLE(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) <= 0;
    }

    public static boolean isLT(BigDecimal a, BigDecimal b) {
        return a.compareTo(b) < 0;
    }

    // ---------------- Main Test ----------------
    public static void main(String[] args) {
        BigDecimal a = DecimalUtils.valueOfNtoZ(null);
        System.out.println("valueOfNtoZ(null) = " + a);

        BigDecimal b = DecimalUtils.valueOf(123.456, 2);
        System.out.println("valueOf(123.456,2) = " + b);

        BigDecimal c = DecimalUtils.toNegative(new BigDecimal("10.5"));
        System.out.println("toNegative(10.5) = " + c);

        BigDecimal d = DecimalUtils.roundingVat(new BigDecimal("10.555"), 2);
        System.out.println("roundingVat(10.555,2) = " + d);

        System.out.println("isPositiveOrZero(-1) = " + DecimalUtils.isPositiveOrZero(new BigDecimal("-1")));
        System.out.println("isNegativeOrZero(0) = " + DecimalUtils.isNegativeOrZero(BigDecimal.ZERO));
    }
}
