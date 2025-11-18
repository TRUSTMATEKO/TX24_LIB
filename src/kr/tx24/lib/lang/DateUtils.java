package kr.tx24.lib.lang;

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 날짜 및 시간 처리 유틸리티
 * 
 * <p>LocalDate, LocalDateTime, Timestamp 등의 변환 및 포맷팅 기능을 제공합니다.</p>
 */
public class DateUtils {

    private static final String DAY_PATTERN = "yyyyMMdd";
    private static final String MONTH_PATTERN = "yyyyMM";
    private static final String TIME_PATTERN = "HHmmss";
    private static final String DATE_PATTERN = "yyyyMMddHHmmss";
    private static final String DATE_PATTERN2 = "yyyy-MM-dd HH:mm:ss";

    private static final DateTimeFormatter DTF_DAY = DateTimeFormatter.ofPattern(DAY_PATTERN);
    private static final DateTimeFormatter DTF_TIME = DateTimeFormatter.ofPattern(TIME_PATTERN);
    private static final DateTimeFormatter DTF_DATE = DateTimeFormatter.ofPattern(DATE_PATTERN);
    private static final DateTimeFormatter DTF_DATE2 = DateTimeFormatter.ofPattern(DATE_PATTERN2);

    /**
     * 현재 날짜를 정수로 반환합니다 (yyyyMMdd).
     */
    public static int getRegDay() {
        return CommonUtils.parseInt(getCurrentDay());
    }
    
    /**
     * 현재 날짜를 문자열로 반환합니다 (yyyyMMdd).
     */
    public static String getCurrentDay() {
        LocalDate today = LocalDate.now();
        return today.format(DTF_DAY);
    }

    /**
     * 현재 날짜를 지정된 패턴으로 반환합니다.
     */
    public static String getCurrentDay(String pattern) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return today.format(formatter);
    }

    /**
     * 현재 시간을 반환합니다 (HHmmss).
     */
    public static String getCurrentTime() {
        LocalTime now = LocalTime.now();
        return now.format(DTF_TIME);
    }

    /**
     * 현재 시간을 지정된 패턴으로 반환합니다.
     */
    public static String getCurrentTime(String pattern) {
        LocalTime now = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return now.format(formatter);
    }

    /**
     * 현재 날짜와 시간을 반환합니다 (yyyyMMddHHmmss).
     */
    public static String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DTF_DATE);
    }

    /**
     * 현재 날짜와 시간을 지정된 패턴으로 반환합니다.
     */
    public static String getCurrentDate(String pattern) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return now.format(formatter);
    }

    /**
     * 현재 Timestamp를 반환합니다.
     */
    public static Timestamp getCurrentTimestamp() {
        Instant instant = Instant.now();
        return Timestamp.from(instant);
    }

    /**
     * 문자열을 LocalDate로 파싱합니다.
     * 인자 없음: 현재 날짜, 1개: yyyyMMdd 파싱, 2개: 커스텀 패턴 파싱
     */
    public static LocalDate getDay(String... args) {
        if (args.length == 0) {
            return LocalDate.now();
        } else if (args.length == 1) {
            return LocalDate.parse(args[0], DTF_DAY);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(args[1]);
            return LocalDate.parse(args[0], formatter);
        }
    }

    /**
     * 문자열을 LocalTime으로 파싱합니다.
     * 인자 없음: 현재 시간, 1개: HHmmss 파싱, 2개: 커스텀 패턴 파싱
     */
    public static LocalTime getTime(String... args) {
        if (args.length == 0) {
            return LocalTime.now();
        } else if (args.length == 1) {
            return LocalTime.parse(args[0], DTF_TIME);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(args[1]);
            return LocalTime.parse(args[0], formatter);
        }
    }

    /**
     * 문자열을 LocalDateTime으로 파싱합니다.
     * 인자 없음: 현재 시간, 1개: yyyyMMddHHmmss 파싱, 2개: 커스텀 패턴 파싱
     */
    public static LocalDateTime getDate(String... args) {
        if (args.length == 0) {
            return LocalDateTime.now();
        } else if (args.length == 1) {
            return LocalDateTime.parse(args[0], DTF_DATE);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(args[1]);
            return LocalDateTime.parse(args[0], formatter);
        }
    }

    /**
     * 문자열을 ZonedDateTime으로 파싱합니다.
     * 인자 없음: 현재 시간, 1개: yyyyMMddHHmmss 파싱, 2개: 커스텀 패턴 파싱
     */
    public static ZonedDateTime getZonedDate(ZoneId zoneId, String... args) {
        if (args.length == 0) {
            return ZonedDateTime.now(zoneId);
        } else if (args.length == 1) {
            LocalDateTime ldt = LocalDateTime.parse(args[0], DTF_DATE);
            return ldt.atZone(zoneId);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(args[1]);
            LocalDateTime ldt = LocalDateTime.parse(args[0], formatter);
            return ldt.atZone(zoneId);
        }
    }

    /**
     * Date를 문자열로 변환합니다 (yyyyMMddHHmmss).
     */
    public static String toString(Date date) {
        if (date == null) return "";
        return DTF_DATE.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    /**
     * Timestamp를 문자열로 변환합니다 (yyyyMMddHHmmss).
     */
    public static String toString(Timestamp ts) {
        if (ts == null) return "";
        return DTF_DATE.format(ts.toLocalDateTime());
    }
    
    
    public static String toString(Timestamp ts,String pattern) {
        if (ts == null) return "";
        if(CommonUtils.isEmpty(pattern)) {
        	pattern = DATE_PATTERN;
        }
        
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return ts.toLocalDateTime().format(formatter);
    }

    /**
     * Calendar를 문자열로 변환합니다 (yyyyMMddHHmmss).
     */
    public static String toString(Calendar cal) {
        if (cal == null) return "";
        return toString(cal.getTime());
    }
    
    /**
     * LocalDateTime을 문자열로 변환합니다 (yyyyMMddHHmmss).
     */
    public static String toString(LocalDateTime ldt) {
        if (ldt == null) return "";
        return DTF_DATE.format(ldt);
    }
    
    /**
     * Temporal 객체를 문자열로 변환합니다.
     * 패턴 미지정 시 타입별 기본 포맷 사용
     */
    public static String toString(Temporal temporal, String... pattern) {
        if (temporal == null) { return ""; }
        
        DateTimeFormatter formatter = pattern.length == 0
                ? null
                : DateTimeFormatter.ofPattern(pattern[0]);

        if (temporal instanceof LocalDateTime ldt) {
            return (formatter != null) ? ldt.format(formatter) : ldt.format(DTF_DATE);
        } else if (temporal instanceof LocalDate ld) {
            return (formatter != null) ? ld.format(formatter) : ld.format(DTF_DAY);
        } else if (temporal instanceof LocalTime lt) {
            return (formatter != null) ? lt.format(formatter) : lt.format(DTF_TIME);
        } else if (temporal instanceof ZonedDateTime zdt) {
            return (formatter != null) ? zdt.format(formatter) : zdt.format(DTF_DATE);
        } else if (temporal instanceof OffsetDateTime odt) {
            return (formatter != null) ? odt.format(formatter) : odt.format(DTF_DATE);
        }

        return temporal.toString();
    }

    /**
     * 두 날짜 사이의 기간을 계산합니다.
     */
    public static long between(LocalDate from, LocalDate to, TemporalUnit unit) {
        return unit.between(from, to);
    }

    /**
     * 두 날짜시간 사이의 기간을 계산합니다.
     */
    public static long between(LocalDateTime from, LocalDateTime to, TemporalUnit unit) {
        return unit.between(from, to);
    }

    /**
     * 두 Timestamp 사이의 기간을 계산합니다.
     */
    public static long between(Timestamp from, Timestamp to, TemporalUnit unit) {
        return unit.between(from.toLocalDateTime(), to.toLocalDateTime());
    }

    /**
     * 두 날짜 문자열 사이의 기간을 계산합니다.
     */
    public static long between(String fromStr, String toStr, String pattern, TemporalUnit unit) {
        LocalDateTime from = getDate(fromStr, pattern);
        LocalDateTime to = getDate(toStr, pattern);
        return between(from, to, unit);
    }

    /**
     * 지정된 일수를 더합니다.
     */
    public static LocalDateTime plusDays(Temporal temporal, long days) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).plusDays(days);
        }

        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().plusDays(days);
        }

        throw new IllegalArgumentException("Unsupported temporal type");
    }

    /**
     * 지정된 개월수를 더합니다.
     */
    public static LocalDateTime plusMonths(Temporal temporal, long months) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).plusMonths(months);
        }

        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().plusMonths(months);
        }

        throw new IllegalArgumentException("Unsupported temporal type");
    }

    /**
     * Epoch 밀리초를 LocalDate로 변환합니다.
     */
    public static LocalDate toLocalDate(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
    }

    /**
     * Epoch 밀리초를 LocalDateTime으로 변환합니다.
     */
    public static LocalDateTime toLocalDateTime(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime();
    }

    /**
     * Epoch 밀리초를 LocalDateTime으로 변환합니다 (시스템 기본 타임존).
     */
    public static LocalDateTime toLocalDateTime(long epochMillis) {
        return toLocalDateTime(epochMillis, ZoneId.systemDefault());
    }

    /**
     * Epoch 밀리초를 LocalDate로 변환합니다 (시스템 기본 타임존).
     */
    public static LocalDate toLocalDate(long epochMillis) {
        return toLocalDate(epochMillis, ZoneId.systemDefault());
    }

    /**
     * LocalDateTime을 Timestamp로 변환합니다.
     */
    public static Timestamp toTimestamp(LocalDateTime ldt) {
        return Timestamp.valueOf(ldt);
    }

    /**
     * 날짜 문자열을 Timestamp로 변환합니다.
     */
    public static Timestamp toTimestamp(String dateStr, String pattern) {
        return toTimestamp(getDate(dateStr, pattern));
    }

    /**
     * 다양한 형식의 날짜 문자열을 Timestamp로 자동 파싱합니다.
     * 숫자만 추출하여 길이에 따라 적절한 포맷 추론
     */
    public static Timestamp toTimestampNotDefine(String str) {
        if (str == null || str.isEmpty()) return getCurrentTimestamp();

        str = str.replaceAll("[^0-9\\.]", "");
        boolean hasDot = str.contains(".");
        int len = hasDot ? str.indexOf('.') : str.length();
        String pattern;

        switch (len) {
            case 14: pattern = "yyyyMMddHHmmss"; break;
            case 12: pattern = "yyMMddHHmmss"; break;
            case 10: pattern = "yyMMddHHmm"; break;
            case 8:  pattern = "yyyyMMdd"; break;
            case 6:  pattern = "yyyyMM"; break;
            case 4:  pattern = "yyMM"; break;
            default: pattern = null;
        }

        if (pattern == null) return getCurrentTimestamp();

        if (hasDot) {
            String fraction = str.substring(str.indexOf('.') + 1);
            pattern += "." + ("SSS".substring(0, Math.min(fraction.length(), 3)));
        }

        try {
            return new Timestamp(new SimpleDateFormat(pattern).parse(str).getTime());
        } catch (ParseException e) {
            return getCurrentTimestamp();
        }
    }

    /**
     * 기준 날짜에서 지정된 일수 이후의 날짜를 반환합니다 (yyyyMMdd).
     * temporal 미지정 시 오늘 기준
     */
    public static String getDay(long days, Temporal... temporal) {
        LocalDateTime dt;

        if (temporal.length == 0) {
            dt = LocalDateTime.now();
        } else if (temporal[0] instanceof LocalDateTime) {
            dt = (LocalDateTime) temporal[0];
        } else if (temporal[0] instanceof LocalDate) {
            dt = ((LocalDate) temporal[0]).atStartOfDay();
        } else {
            dt = LocalDateTime.now();
        }

        return dt.plusDays(days).format(DTF_DAY);
    }

    /**
     * 기준 날짜에서 지정된 개월수 이후의 월을 반환합니다 (yyyyMM).
     * temporal 미지정 시 오늘 기준
     */
    public static String getMonth(long months, Temporal... temporal) {
        LocalDateTime dt;

        if (temporal.length == 0) {
            dt = LocalDateTime.now();
        } else if (temporal[0] instanceof LocalDateTime) {
            dt = (LocalDateTime) temporal[0];
        } else if (temporal[0] instanceof LocalDate) {
            dt = ((LocalDate) temporal[0]).atStartOfDay();
        } else {
            dt = LocalDateTime.now();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(MONTH_PATTERN);
        return dt.plusMonths(months).format(formatter);
    }

    /**
     * 해당 월의 마지막 날짜를 반환합니다 (DD).
     * temporal 미지정 시 현재 월 기준
     */
    public static String getEndDay(Temporal... temporal) {
        YearMonth ym;

        if (temporal.length == 0) {
            ym = YearMonth.now();
        } else if (temporal[0] instanceof LocalDateTime) {
            ym = YearMonth.from((LocalDateTime) temporal[0]);
        } else if (temporal[0] instanceof LocalDate) {
            ym = YearMonth.from((LocalDate) temporal[0]);
        } else {
            ym = YearMonth.now();
        }

        return String.format("%02d", ym.atEndOfMonth().getDayOfMonth());
    }
    
    
    /**
     * 월의 마지막일을 반환합니다 (말일 23:59:59.999999999).
     * 
     * @param temporal 기준 날짜 (미지정 시 현재 월)
     * @return 해당 월의 말일 23:59:59.999999999
     * 
     * @example
     * <pre>
     * getEndOfMonth()                              // 2025-01-31 23:59:59.999999999
     * getEndOfMonth(LocalDate.parse("20250215"))   // 2025-02-28 23:59:59.999999999
     * </pre>
     */
    public static LocalDateTime getEndOfMonth(Temporal... temporal) {
        LocalDate date;
        
        if (temporal.length == 0) {
            date = LocalDate.now();
        } else if (temporal[0] instanceof LocalDateTime) {
            date = ((LocalDateTime) temporal[0]).toLocalDate();
        } else if (temporal[0] instanceof LocalDate) {
            date = (LocalDate) temporal[0];
        } else {
            date = LocalDate.now();
        }
        
        return date.withDayOfMonth(date.lengthOfMonth()).atTime(LocalTime.MAX);
    }

    
    
    /**
     * 지정된 일수를 뺍니다.
     * 
     * @param temporal 기준 날짜
     * @param days 뺄 일수
     * @return days만큼 이전 날짜
     * 
     * @example
     * <pre>
     * minusDays(LocalDate.parse("20250115"), 5)  // 2025-01-10
     * minusDays(LocalDateTime.now(), 7)          // 7일 전
     * </pre>
     */
    public static LocalDateTime minusDays(Temporal temporal, long days) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).minusDays(days);
        }
        
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().minusDays(days);
        }
        
        throw new IllegalArgumentException("Unsupported temporal type");
    }

    /**
     * 지정된 개월수를 뺍니다.
     * 
     * @param temporal 기준 날짜
     * @param months 뺄 개월수
     * @return months만큼 이전 날짜
     * 
     * @example
     * <pre>
     * minusMonths(LocalDate.parse("20250315"), 2)  // 2025-01-15
     * minusMonths(LocalDateTime.now(), 6)          // 6개월 전
     * </pre>
     */
    public static LocalDateTime minusMonths(Temporal temporal, long months) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).minusMonths(months);
        }
        
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().minusMonths(months);
        }
        
        throw new IllegalArgumentException("Unsupported temporal type");
    }

    /**
     * 지정된 년수를 뺍니다.
     * 
     * @param temporal 기준 날짜
     * @param years 뺄 년수
     * @return years만큼 이전 날짜
     * 
     * @example
     * <pre>
     * minusYears(LocalDate.parse("20250115"), 3)  // 2022-01-15
     * minusYears(LocalDateTime.now(), 1)          // 1년 전
     * </pre>
     */
    public static LocalDateTime minusYears(Temporal temporal, long years) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).minusYears(years);
        }
        
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().minusYears(years);
        }
        
        throw new IllegalArgumentException("Unsupported temporal type");
    }
    
    
    
    /**
     * 주의 시작일을 반환합니다 (월요일 00:00:00).
     * 
     * @param temporal 기준 날짜 (미지정 시 오늘)
     * @return 해당 주의 월요일 00:00:00
     * 
     * @example
     * <pre>
     * // 2025-01-15(수) 기준
     * getStartOfWeek(LocalDate.parse("20250115"))  // 2025-01-13 (월) 00:00:00
     * </pre>
     */
    public static LocalDateTime getStartOfWeek(Temporal... temporal) {
        LocalDate date;
        
        if (temporal.length == 0) {
            date = LocalDate.now();
        } else if (temporal[0] instanceof LocalDateTime) {
            date = ((LocalDateTime) temporal[0]).toLocalDate();
        } else if (temporal[0] instanceof LocalDate) {
            date = (LocalDate) temporal[0];
        } else {
            date = LocalDate.now();
        }
        
        return date.with(java.time.DayOfWeek.MONDAY).atStartOfDay();
    }

    /**
     * 주의 마지막일을 반환합니다 (일요일 23:59:59.999999999).
     * 
     * @param temporal 기준 날짜 (미지정 시 오늘)
     * @return 해당 주의 일요일 23:59:59.999999999
     * 
     * @example
     * <pre>
     * // 2025-01-15(수) 기준
     * getEndOfWeek(LocalDate.parse("20250115"))    // 2025-01-19 (일) 23:59:59.999999999
     * </pre>
     */
    public static LocalDateTime getEndOfWeek(Temporal... temporal) {
        LocalDate date;
        
        if (temporal.length == 0) {
            date = LocalDate.now();
        } else if (temporal[0] instanceof LocalDateTime) {
            date = ((LocalDateTime) temporal[0]).toLocalDate();
        } else if (temporal[0] instanceof LocalDate) {
            date = (LocalDate) temporal[0];
        } else {
            date = LocalDate.now();
        }
        
        return date.with(java.time.DayOfWeek.SUNDAY).atTime(LocalTime.MAX);
    }
    
    
    /**
     * 두 날짜 사이의 일수를 계산합니다.
     * 
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 일수 차이 (음수 가능)
     * 
     * @example
     * <pre>
     * getDaysBetween(
     *   LocalDate.parse("20250101"), 
     *   LocalDate.parse("20250115")
     * )  // 14
     * 
     * getDaysBetween(
     *   LocalDate.parse("20250115"), 
     *   LocalDate.parse("20250101")
     * )  // -14
     * </pre>
     */
    public static long getDaysBetween(Temporal from, Temporal to) {
        return java.time.temporal.ChronoUnit.DAYS.between(from, to);
    }

    /**
     * 두 날짜시간 사이의 시간수를 계산합니다.
     * 
     * @param from 시작 시간
     * @param to 종료 시간
     * @return 시간 차이 (음수 가능)
     * 
     * @example
     * <pre>
     * getHoursBetween(
     *   LocalDateTime.parse("2025-01-15T10:00:00"), 
     *   LocalDateTime.parse("2025-01-15T15:30:00")
     * )  // 5
     * </pre>
     */
    public static long getHoursBetween(Temporal from, Temporal to) {
        return java.time.temporal.ChronoUnit.HOURS.between(from, to);
    }

    /**
     * 두 날짜시간 사이의 분을 계산합니다.
     * 
     * @param from 시작 시간
     * @param to 종료 시간
     * @return 분 차이 (음수 가능)
     * 
     * @example
     * <pre>
     * getMinutesBetween(
     *   LocalDateTime.parse("2025-01-15T10:00:00"), 
     *   LocalDateTime.parse("2025-01-15T10:45:00")
     * )  // 45
     * </pre>
     */
    public static long getMinutesBetween(Temporal from, Temporal to) {
        return java.time.temporal.ChronoUnit.MINUTES.between(from, to);
    }

    /**
     * 두 날짜 사이의 개월수를 계산합니다.
     * 
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 개월수 차이 (음수 가능)
     * 
     * @example
     * <pre>
     * getMonthsBetween(
     *   LocalDate.parse("20250101"), 
     *   LocalDate.parse("20250401")
     * )  // 3
     * </pre>
     */
    public static long getMonthsBetween(Temporal from, Temporal to) {
        return java.time.temporal.ChronoUnit.MONTHS.between(from, to);
    }

    /**
     * 두 날짜 사이의 년수를 계산합니다.
     * 
     * @param from 시작 날짜
     * @param to 종료 날짜
     * @return 년수 차이 (음수 가능)
     * 
     * @example
     * <pre>
     * getYearsBetween(
     *   LocalDate.parse("20200101"), 
     *   LocalDate.parse("20250101")
     * )  // 5
     * </pre>
     */
    public static long getYearsBetween(Temporal from, Temporal to) {
        return java.time.temporal.ChronoUnit.YEARS.between(from, to);
    }

    // ==================== 주말/평일 확인 ====================

    /**
     * 주말인지 확인합니다 (토요일 또는 일요일).
     * 
     * @param temporal 확인할 날짜 (미지정 시 오늘)
     * @return 주말이면 true
     * 
     * @example
     * <pre>
     * isWeekend(LocalDate.parse("20250118"))  // true (토요일)
     * isWeekend(LocalDate.parse("20250119"))  // true (일요일)
     * isWeekend(LocalDate.parse("20250120"))  // false (월요일)
     * </pre>
     */
    public static boolean isWeekend(Temporal... temporal) {
        LocalDate date;
        
        if (temporal.length == 0) {
            date = LocalDate.now();
        } else if (temporal[0] instanceof LocalDateTime) {
            date = ((LocalDateTime) temporal[0]).toLocalDate();
        } else if (temporal[0] instanceof LocalDate) {
            date = (LocalDate) temporal[0];
        } else {
            date = LocalDate.now();
        }
        
        java.time.DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dayOfWeek == java.time.DayOfWeek.SATURDAY || 
               dayOfWeek == java.time.DayOfWeek.SUNDAY;
    }

    /**
     * 평일인지 확인합니다 (월~금).
     * 
     * @param temporal 확인할 날짜 (미지정 시 오늘)
     * @return 평일이면 true
     * 
     * @example
     * <pre>
     * isWeekday(LocalDate.parse("20250120"))  // true (월요일)
     * isWeekday(LocalDate.parse("20250118"))  // false (토요일)
     * </pre>
     */
    public static boolean isWeekday(Temporal... temporal) {
        return !isWeekend(temporal);
    }

    // ==================== 날짜 비교 ====================

    /**
     * a가 b보다 이전 날짜인지 확인합니다.
     * 
     * @param a 비교할 날짜
     * @param b 기준 날짜
     * @return a < b이면 true
     * 
     * @example
     * <pre>
     * isBefore(
     *   LocalDate.parse("20250101"), 
     *   LocalDate.parse("20250115")
     * )  // true
     * </pre>
     */
    public static boolean isBefore(Temporal a, Temporal b) {
        if (a instanceof LocalDate && b instanceof LocalDate) {
            return ((LocalDate) a).isBefore((LocalDate) b);
        }
        if (a instanceof LocalDateTime && b instanceof LocalDateTime) {
            return ((LocalDateTime) a).isBefore((LocalDateTime) b);
        }
        throw new IllegalArgumentException("Unsupported temporal types");
    }

    /**
     * a가 b보다 이후 날짜인지 확인합니다.
     * 
     * @param a 비교할 날짜
     * @param b 기준 날짜
     * @return a > b이면 true
     * 
     * @example
     * <pre>
     * isAfter(
     *   LocalDate.parse("20250115"), 
     *   LocalDate.parse("20250101")
     * )  // true
     * </pre>
     */
    public static boolean isAfter(Temporal a, Temporal b) {
        if (a instanceof LocalDate && b instanceof LocalDate) {
            return ((LocalDate) a).isAfter((LocalDate) b);
        }
        if (a instanceof LocalDateTime && b instanceof LocalDateTime) {
            return ((LocalDateTime) a).isAfter((LocalDateTime) b);
        }
        throw new IllegalArgumentException("Unsupported temporal types");
    }

    /**
     * 두 날짜가 같은 날인지 확인합니다.
     * 
     * @param a 비교할 날짜
     * @param b 기준 날짜
     * @return 같은 날이면 true
     * 
     * @example
     * <pre>
     * isSameDay(
     *   LocalDate.parse("20250115"), 
     *   LocalDate.parse("20250115")
     * )  // true
     * 
     * isSameDay(
     *   LocalDateTime.parse("2025-01-15T10:00:00"),
     *   LocalDateTime.parse("2025-01-15T15:00:00")
     * )  // true (같은 날)
     * </pre>
     */
    public static boolean isSameDay(Temporal a, Temporal b) {
        LocalDate dateA = a instanceof LocalDateTime 
            ? ((LocalDateTime) a).toLocalDate() 
            : (LocalDate) a;
        LocalDate dateB = b instanceof LocalDateTime 
            ? ((LocalDateTime) b).toLocalDate() 
            : (LocalDate) b;
        
        return dateA.isEqual(dateB);
    }

    /**
     * 두 날짜가 같은 월인지 확인합니다.
     * 
     * @param a 비교할 날짜
     * @param b 기준 날짜
     * @return 같은 월이면 true
     * 
     * @example
     * <pre>
     * isSameMonth(
     *   LocalDate.parse("20250115"), 
     *   LocalDate.parse("20250120")
     * )  // true
     * </pre>
     */
    public static boolean isSameMonth(Temporal a, Temporal b) {
        LocalDate dateA = a instanceof LocalDateTime 
            ? ((LocalDateTime) a).toLocalDate() 
            : (LocalDate) a;
        LocalDate dateB = b instanceof LocalDateTime 
            ? ((LocalDateTime) b).toLocalDate() 
            : (LocalDate) b;
        
        return dateA.getYear() == dateB.getYear() && 
               dateA.getMonth() == dateB.getMonth();
    }

    /**
     * 두 날짜가 같은 년도인지 확인합니다.
     * 
     * @param a 비교할 날짜
     * @param b 기준 날짜
     * @return 같은 년도면 true
     * 
     * @example
     * <pre>
     * isSameYear(
     *   LocalDate.parse("20250115"), 
     *   LocalDate.parse("20251220")
     * )  // true
     * </pre>
     */
    public static boolean isSameYear(Temporal a, Temporal b) {
        LocalDate dateA = a instanceof LocalDateTime 
            ? ((LocalDateTime) a).toLocalDate() 
            : (LocalDate) a;
        LocalDate dateB = b instanceof LocalDateTime 
            ? ((LocalDateTime) b).toLocalDate() 
            : (LocalDate) b;
        
        return dateA.getYear() == dateB.getYear();
    }

    /**
     * 오늘인지 확인합니다.
     * 
     * @param temporal 확인할 날짜
     * @return 오늘이면 true
     * 
     * @example
     * <pre>
     * isToday(LocalDate.now())  // true
     * isToday(LocalDate.parse("20250101"))  // false
     * </pre>
     */
    public static boolean isToday(Temporal temporal) {
        return isSameDay(temporal, LocalDate.now());
    }

    /**
     * 미래 날짜인지 확인합니다.
     * 
     * @param temporal 확인할 날짜
     * @return 미래면 true
     * 
     * @example
     * <pre>
     * isFuture(LocalDate.parse("20991231"))  // true
     * isFuture(LocalDate.parse("20200101"))  // false
     * </pre>
     */
    public static boolean isFuture(Temporal temporal) {
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).isAfter(LocalDate.now());
        }
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).isAfter(LocalDateTime.now());
        }
        throw new IllegalArgumentException("Unsupported temporal type");
    }

    /**
     * 과거 날짜인지 확인합니다.
     * 
     * @param temporal 확인할 날짜
     * @return 과거면 true
     * 
     * @example
     * <pre>
     * isPast(LocalDate.parse("20200101"))  // true
     * isPast(LocalDate.parse("20991231"))  // false
     * </pre>
     */
    public static boolean isPast(Temporal temporal) {
        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).isBefore(LocalDate.now());
        }
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).isBefore(LocalDateTime.now());
        }
        throw new IllegalArgumentException("Unsupported temporal type");
    }

    
    
    /**
     * 시작일부터 종료일까지의 모든 날짜 리스트를 반환합니다.
     *
     * @param start 시작 날짜 (yyyyMMdd)
     * @param end 종료 날짜 (yyyyMMdd, 포함)
     * @param pattern 출력 포맷 (미지정 시 yyyyMMdd)
     * @return 날짜 문자열 리스트
     *
     * @example
     * <pre>
     * getDateRange("20250101", "20250105")
     * // ["20250101", "20250102", "20250103", "20250104", "20250105"]
     * 
     * getDateRange("20250101", "20250105", "yyyy-MM-dd")
     * // ["2025-01-01", "2025-01-02", "2025-01-03", "2025-01-04", "2025-01-05"]
     * 
     * getDateRange("20250101", "20250105", "MM/dd")
     * // ["01/01", "01/02", "01/03", "01/04", "01/05"]
     * </pre>
     */
    public static List<String> getDateRange(String start, String end, String... pattern) {
        LocalDate startDate = LocalDate.parse(start, DTF_DAY);
        LocalDate endDate = LocalDate.parse(end, DTF_DAY);
        
        DateTimeFormatter formatter = (pattern.length > 0) 
            ? DateTimeFormatter.ofPattern(pattern[0])
            : DTF_DAY;
        
        List<String> dates = new ArrayList<>();
        LocalDate current = startDate;
        
        while (!current.isAfter(endDate)) {
            dates.add(current.format(formatter));
            current = current.plusDays(1);
        }
        
        return dates;
    }

    /**
     * 특정 월의 모든 날짜 리스트를 반환합니다.
     *
     * @param dateStr 기준 날짜 (yyyyMMdd, 미지정 시 현재 월)
     * @param pattern 출력 포맷 (미지정 시 yyyyMMdd)
     * @return 해당 월의 모든 날짜 문자열 리스트
     *
     * @example
     * <pre>
     * getDatesOfMonth("20250201")
     * // ["20250201", "20250202", ..., "20250228"]
     * 
     * getDatesOfMonth("20250201", "yyyy-MM-dd")
     * // ["2025-02-01", "2025-02-02", ..., "2025-02-28"]
     * 
     * getDatesOfMonth()  // 현재 월
     * // ["20250101", "20250102", ..., "20250131"]
     * 
     * getDatesOfMonth("20250201", "dd")
     * // ["01", "02", ..., "28"]
     * </pre>
     */
    public static List<String> getDatesOfMonth(String dateStr, String... pattern) {
        LocalDate date = LocalDate.parse(dateStr, DTF_DAY);
        
        String startDay = date.withDayOfMonth(1).format(DTF_DAY);
        String endDay = date.withDayOfMonth(date.lengthOfMonth()).format(DTF_DAY);
        
        return getDateRange(startDay, endDay, pattern);
    }

    /**
     * 특정 월의 모든 날짜 리스트를 반환합니다 (날짜 미지정 시 현재 월).
     *
     * @param pattern 출력 포맷 (미지정 시 yyyyMMdd)
     * @return 해당 월의 모든 날짜 문자열 리스트
     *
     * @example
     * <pre>
     * getDatesOfMonth()  // 현재 월, yyyyMMdd 포맷
     * getDatesOfMonth("yyyy-MM-dd")  // 현재 월, 지정 포맷
     * </pre>
     */
    public static List<String> getDatesOfMonth(String... pattern) {
        String currentDay = LocalDate.now().format(DTF_DAY);
        return getDatesOfMonth(currentDay, pattern);
    }

    /**
     * 특정 월의 평일만 리스트로 반환합니다 (월~금).
     *
     * @param dateStr 기준 날짜 (yyyyMMdd, 미지정 시 현재 월)
     * @param pattern 출력 포맷 (미지정 시 yyyyMMdd)
     * @return 해당 월의 평일 문자열 리스트
     *
     * @example
     * <pre>
     * getWeekdaysOfMonth("20250201")
     * // ["20250203", "20250204", ..., "20250228"]  // 토,일 제외
     * 
     * getWeekdaysOfMonth("20250201", "yyyy-MM-dd (E)")
     * // ["2025-02-03 (월)", "2025-02-04 (화)", ...]
     * </pre>
     */
    public static List<String> getWeekdaysOfMonth(String dateStr, String... pattern) {
        LocalDate date = LocalDate.parse(dateStr, DTF_DAY);
        
        DateTimeFormatter formatter = (pattern.length > 0) 
            ? DateTimeFormatter.ofPattern(pattern[0])
            : DTF_DAY;
        
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        
        List<String> weekdays = new ArrayList<>();
        LocalDate current = start;
        
        while (!current.isAfter(end)) {
            if (!isWeekend(current)) {
                weekdays.add(current.format(formatter));
            }
            current = current.plusDays(1);
        }
        
        return weekdays;
    }

    /**
     * 특정 월의 평일만 리스트로 반환합니다 (날짜 미지정 시 현재 월).
     */
    public static List<String> getWeekdaysOfMonth(String... pattern) {
        String currentDay = LocalDate.now().format(DTF_DAY);
        return getWeekdaysOfMonth(currentDay, pattern);
    }

    /**
     * 특정 월의 주말만 리스트로 반환합니다 (토,일).
     *
     * @param dateStr 기준 날짜 (yyyyMMdd, 미지정 시 현재 월)
     * @param pattern 출력 포맷 (미지정 시 yyyyMMdd)
     * @return 해당 월의 주말 문자열 리스트
     *
     * @example
     * <pre>
     * getWeekendsOfMonth("20250201")
     * // ["20250201", "20250202", "20250208", "20250209", ...]
     * 
     * getWeekendsOfMonth("20250201", "yyyy-MM-dd (E)")
     * // ["2025-02-01 (토)", "2025-02-02 (일)", ...]
     * </pre>
     */
    public static List<String> getWeekendsOfMonth(String dateStr, String... pattern) {
        LocalDate date = LocalDate.parse(dateStr, DTF_DAY);
        
        DateTimeFormatter formatter = (pattern.length > 0) 
            ? DateTimeFormatter.ofPattern(pattern[0])
            : DTF_DAY;
        
        LocalDate start = date.withDayOfMonth(1);
        LocalDate end = date.withDayOfMonth(date.lengthOfMonth());
        
        List<String> weekends = new ArrayList<>();
        LocalDate current = start;
        
        while (!current.isAfter(end)) {
            if (isWeekend(current)) {
                weekends.add(current.format(formatter));
            }
            current = current.plusDays(1);
        }
        
        return weekends;
    }
    

    
    /**
     * 요일을 한글로 반환합니다.
     * 
     * @param dateStr 날짜 문자열 (yyyyMMdd, 미지정 시 오늘)
     * @return 요일 (월/화/수/목/금/토/일)
     * 
     * @example
     * <pre>
     * getDayOfWeekKorean("20250115")  // "수"
     * getDayOfWeekKorean("20250118")  // "토"
     * getDayOfWeekKorean()            // 오늘 요일
     * </pre>
     */
    public static String getDayOfWeekKorean(String... dateStr) {
        LocalDate date = (dateStr.length == 0) 
            ? LocalDate.now() 
            : LocalDate.parse(dateStr[0], DTF_DAY);
        
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    /**
     * 요일을 영문으로 반환합니다.
     * 
     * @param dateStr 날짜 문자열 (yyyyMMdd, 미지정 시 오늘)
     * @return 요일 (MONDAY/TUESDAY/...)
     * 
     * @example
     * <pre>
     * getDayOfWeekEnglish("20250115")  // "WEDNESDAY"
     * getDayOfWeekEnglish()            // 오늘 요일 영문
     * </pre>
     */
    public static String getDayOfWeekEnglish(String... dateStr) {
        LocalDate date = (dateStr.length == 0) 
            ? LocalDate.now() 
            : LocalDate.parse(dateStr[0], DTF_DAY);
        
        return date.getDayOfWeek().name();
    }

    /**
     * 요일을 숫자로 반환합니다 (1=월요일 ~ 7=일요일).
     * 
     * @param dateStr 날짜 문자열 (yyyyMMdd, 미지정 시 오늘)
     * @return 요일 숫자 (1~7)
     * 
     * @example
     * <pre>
     * getDayOfWeekNumber("20250113")  // 1 (월요일)
     * getDayOfWeekNumber("20250119")  // 7 (일요일)
     * getDayOfWeekNumber()            // 오늘 요일 숫자
     * </pre>
     */
    public static int getDayOfWeekNumber(String... dateStr) {
        LocalDate date = (dateStr.length == 0) 
            ? LocalDate.now() 
            : LocalDate.parse(dateStr[0], DTF_DAY);
        
        return date.getDayOfWeek().getValue();
    }

    /**
     * 분기를 반환합니다 (1~4).
     * 
     * @param dateStr 날짜 문자열 (yyyyMMdd, 미지정 시 오늘)
     * @return 분기 (1~4)
     * 
     * @example
     * <pre>
     * getQuarter("20250115")  // 1 (1~3월)
     * getQuarter("20250415")  // 2 (4~6월)
     * getQuarter("20250715")  // 3 (7~9월)
     * getQuarter("20251015")  // 4 (10~12월)
     * getQuarter()            // 오늘 기준 분기
     * </pre>
     */
    public static int getQuarter(String... dateStr) {
        LocalDate date = (dateStr.length == 0) 
            ? LocalDate.now() 
            : LocalDate.parse(dateStr[0], DTF_DAY);
        
        return (date.getMonthValue() - 1) / 3 + 1;
    }

    /**
     * 연중 몇 번째 주인지 반환합니다.
     * 
     * @param dateStr 날짜 문자열 (yyyyMMdd, 미지정 시 오늘)
     * @return 주차 (1~53)
     * 
     * @example
     * <pre>
     * getWeekOfYear("20250115")  // 3
     * getWeekOfYear("20251231")  // 1 (다음 해 1주차)
     * getWeekOfYear()            // 오늘 기준 주차
     * </pre>
     */
    public static int getWeekOfYear(String... dateStr) {
        LocalDate date = (dateStr.length == 0) 
            ? LocalDate.now() 
            : LocalDate.parse(dateStr[0], DTF_DAY);
        
        return date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear());
    }

    /**
     * 연중 몇 번째 날인지 반환합니다.
     * 
     * @param dateStr 날짜 문자열 (yyyyMMdd, 미지정 시 오늘)
     * @return 일자 (1~366)
     * 
     * @example
     * <pre>
     * getDayOfYear("20250101")  // 1
     * getDayOfYear("20250115")  // 15
     * getDayOfYear("20251231")  // 365
     * getDayOfYear()            // 오늘 기준
     * </pre>
     */
    public static int getDayOfYear(String... dateStr) {
        LocalDate date = (dateStr.length == 0) 
            ? LocalDate.now() 
            : LocalDate.parse(dateStr[0], DTF_DAY);
        
        return date.getDayOfYear();
    }
    
    /**
     * 해당 년도의 총 일수를 반환합니다.
     * 윤년은 366일, 평년은 365일
     * 
     * @param year 년도 (yyyy)
     * @return 총 일수 (365 또는 366)
     * 
     * @example
     * <pre>
     * getDaysInYear(2024)  // 366 (윤년)
     * getDaysInYear(2025)  // 365 (평년)
     * getDaysInYear(2000)  // 366 (윤년)
     * getDaysInYear(1900)  // 365 (평년 - 100의 배수지만 400의 배수 아님)
     * getDaysInYear("2024")  // 366
     * </pre>
     */
    public static int getDaysInYear(int year) {
        return java.time.Year.of(year).length();
    }

    /**
     * 해당 년도의 총 일수를 반환합니다 (문자열 입력).
     * 
     * @param year 년도 문자열 (yyyy)
     * @return 총 일수 (365 또는 366)
     * 
     * @example
     * <pre>
     * getDaysInYear("2024")  // 366
     * getDaysInYear("2025")  // 365
     * </pre>
     */
    public static int getDaysInYear(String year) {
        return getDaysInYear(Integer.parseInt(year));
    }

}