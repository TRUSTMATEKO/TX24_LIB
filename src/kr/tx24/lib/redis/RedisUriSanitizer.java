package kr.tx24.lib.redis;

import java.util.regex.Pattern;

/** Internal log-safety helper. Connection code must continue to use the original URI. */
final class RedisUriSanitizer {

    private static final Pattern USER_INFO = Pattern.compile("(?i)(rediss?://)[^\\s,/@]*@");

    private RedisUriSanitizer() {
    }

    static String redact(String value) {
        return value == null ? "" : USER_INFO.matcher(value).replaceAll("$1***@");
    }

    static String errorMessage(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + redact(message));
    }
}
