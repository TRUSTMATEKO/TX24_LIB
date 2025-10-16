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
import java.util.Calendar;
import java.util.Date;

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

    // ---------------- Current Day / Time / Date ----------------
    
    public static int getRegDay() {
    	return CommonUtils.parseInt(getCurrentDay());
    }
    
    public static String getCurrentDay() {
        LocalDate today = LocalDate.now();
        return today.format(DTF_DAY);
    }

    public static String getCurrentDay(String pattern) {
        LocalDate today = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return today.format(formatter);
    }

    public static String getCurrentTime() {
        LocalTime now = LocalTime.now();
        return now.format(DTF_TIME);
    }

    public static String getCurrentTime(String pattern) {
        LocalTime now = LocalTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return now.format(formatter);
    }

    public static String getCurrentDate() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DTF_DATE);
    }

    public static String getCurrentDate(String pattern) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return now.format(formatter);
    }

    public static Timestamp getCurrentTimestamp() {
        Instant instant = Instant.now();
        return Timestamp.from(instant);
    }

    // ---------------- Parsing ----------------
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

    // ---------------- toString ----------------
    
    public static String toString(Date date) {
        if (date == null) return "";
        return DTF_DATE.format(date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    public static String toString(Timestamp ts) {
        if (ts == null) return "";
        return DTF_DATE.format(ts.toLocalDateTime());
    }

    public static String toString(Calendar cal) {
        if (cal == null) return "";
        return toString(cal.getTime());
    }
    
    public static String toString(LocalDateTime ldt) {
        if (ldt == null) return "";
        return DTF_DATE.format(ldt);
    }
    
    
    
    
    public static String toString(Temporal temporal, String... pattern) {
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

    // ---------------- Between ----------------
    public static long between(LocalDate from, LocalDate to, TemporalUnit unit) {
        return unit.between(from, to);
    }

    public static long between(LocalDateTime from, LocalDateTime to, TemporalUnit unit) {
        return unit.between(from, to);
    }

    public static long between(Timestamp from, Timestamp to, TemporalUnit unit) {
        return unit.between(from.toLocalDateTime(), to.toLocalDateTime());
    }

    public static long between(String fromStr, String toStr, String pattern, TemporalUnit unit) {
        LocalDateTime from = getDate(fromStr, pattern);
        LocalDateTime to = getDate(toStr, pattern);
        return between(from, to, unit);
    }

    // ---------------- Add / Subtract ----------------
    public static LocalDateTime plusDays(Temporal temporal, long days) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).plusDays(days);
        }

        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().plusDays(days);
        }

        throw new IllegalArgumentException("Unsupported temporal type");
    }

    public static LocalDateTime plusMonths(Temporal temporal, long months) {
        if (temporal instanceof LocalDateTime) {
            return ((LocalDateTime) temporal).plusMonths(months);
        }

        if (temporal instanceof LocalDate) {
            return ((LocalDate) temporal).atStartOfDay().plusMonths(months);
        }

        throw new IllegalArgumentException("Unsupported temporal type");
    }

    // ---------------- Epoch Conversion ----------------
    public static LocalDate toLocalDate(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate();
    }

    public static LocalDateTime toLocalDateTime(long epochMillis, ZoneId zone) {
        return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime();
    }

    public static LocalDateTime toLocalDateTime(long epochMillis) {
        return toLocalDateTime(epochMillis, ZoneId.systemDefault());
    }

    public static LocalDate toLocalDate(long epochMillis) {
        return toLocalDate(epochMillis, ZoneId.systemDefault());
    }

    // ---------------- Timestamp ----------------
    public static Timestamp toTimestamp(LocalDateTime ldt) {
        return Timestamp.valueOf(ldt);
    }

    public static Timestamp toTimestamp(String dateStr, String pattern) {
        return toTimestamp(getDate(dateStr, pattern));
    }

    // ---------------- Flexible Timestamp Parser ----------------
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

    // ---------------- Practical Utilities ----------------
    /** 오늘 기준 ±days 일 후 날짜 (YYYYMMDD) */
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

    /** 오늘 기준 ±months 개월 후 (YYYYMM) */
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

    /** 해당 월의 마지막 날짜 (DD) */
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
}
