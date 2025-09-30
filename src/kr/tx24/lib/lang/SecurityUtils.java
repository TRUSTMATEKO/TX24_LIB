package kr.tx24.lib.lang;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.regex.Pattern;

public class SecurityUtils {

	private SecurityUtils() {}

    // Patterns cached
    private static final Pattern EVIL_CHARS = Pattern.compile("[’‘`]");
    private static final Pattern SQL_META_CHARS = Pattern.compile("[’‘`'\"#;=]");
    private static final Pattern FILENAME_FORBIDDEN = Pattern.compile("[\\\\/:*?\"<>|]");
    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.");

    // Basic HTML entities (order matters: & must be replaced first when escaping)
    public static String escapeHtml(String input) {
        if (input == null) return null;

        String s = EVIL_CHARS.matcher(input).replaceAll("");
        // encode & first to avoid double-encoding issues
        s = s.replace("&", "&amp;");
        s = s.replace("<", "&lt;")
             .replace(">", "&gt;")
             .replace("\"", "&quot;")
             .replace("'", "&#39;")
             .replace("=", "&#61;");

        // simple script token defense: replace "script" -> "q-script"
        String lower = s.toLowerCase();
        if (lower.contains("script")) {
            s = lower.replace("script", "q-script");
        }
        return s;
    }

    public static String unescapeHtml(String input) {
        if (input == null) return null;
        String s = input.replace("&#39;", "'")
                        .replace("&quot;", "\"")
                        .replace("&#60;", "<")
                        .replace("&#62;", ">")
                        .replace("&#61;", "=")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&amp;", "&");
        // q-script -> script
        if (s.toLowerCase().contains("q-script")) {
            s = s.replace("q-script", "script");
        }
        return s;
    }

    /**
     * Lightweight SQL keyword sanitizer.
     * NOTE: Prefer PreparedStatement for SQL injection protection; this is a basic helper
     * for cases where raw SQL assembly is unavoidable.
     */
    public static String sanitizeSql(String input) {
        if (input == null) return null;

        String s = SQL_META_CHARS.matcher(input).replaceAll("");
        String lower = s.toLowerCase();

        // Replace dangerous keywords with prefixed tokens
        String[] keywords = {
            "union","select","insert","drop","update","delete",
            "and","or","join","substr","from","where","declare",
            "openrowset","information_schema","table_schema","table_name",
            "column_name","row_num"
        };
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                lower = lower.replace(kw, "q-" + kw);
            }
        }

        // basic dash protection
        lower = lower.replace("-", "&#45;");
        return lower;
    }

    public static String sanitizePath(String input) {
        if (input == null) return null;
        String s = input.replace("\\", "/");
        s = PATH_TRAVERSAL.matcher(s).replaceAll(""); // remove ".."
        s = s.replaceAll("[<>:\"|?*]", ""); // remove other verboten chars
        return s;
    }

    public static String sanitizeFilename(String input) {
        if (input == null) return null;
        String s = FILENAME_FORBIDDEN.matcher(input).replaceAll("_");
        return s.trim();
    }

    public static String sanitizeEmail(String input) {
        if (input == null) return null;
        return escapeHtml(input.trim());
    }

    public static String sanitizeDigits(String input) {
        if (input == null) return null;
        return input.replaceAll("[^0-9]", "");
    }

    /** Mask sensitive strings for safe logging. */
    public static String maskSensitive(String input, int visiblePrefix, int visibleSuffix) {
        if (input == null) return null;
        int len = input.length();
        if (len <= visiblePrefix + visibleSuffix) {
            return "*".repeat(len);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(input, 0, visiblePrefix);
        sb.append("*".repeat(len - visiblePrefix - visibleSuffix));
        sb.append(input, len - visibleSuffix, len);
        return sb.toString();
    }

    /** Convert a BigInteger to BigDecimal safely (helper). */
    public static BigDecimal toBigDecimal(BigInteger bi) {
        if (bi == null) return BigDecimal.ZERO;
        return new BigDecimal(bi);
    }
}