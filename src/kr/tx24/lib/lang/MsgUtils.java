package kr.tx24.lib.lang;

import java.util.HashMap;
import java.util.Map;

public class MsgUtils {

	private static final String DELIM_STR = "{}";
    private static final char ESCAPE_CHAR = '\\';

    public static String format(String messagePattern, Object... argArray) {
        if (CommonUtils.isEmpty(messagePattern)) return "";
        if (argArray == null || argArray.length == 0) return messagePattern;

        StringBuilder sb = new StringBuilder(messagePattern.length() + 50);
        int i = 0;
        for (int argIndex = 0; argIndex < argArray.length; argIndex++) {
            int j = messagePattern.indexOf(DELIM_STR, i);
            if (j == -1) {
                sb.append(messagePattern, i, messagePattern.length());
                break;
            }
            if (isEscapedDelimiter(messagePattern, j) && !isDoubleEscaped(messagePattern, j)) {
                sb.append(messagePattern, i, j - 1).append('{');
                i = j + 1;
                argIndex--;
            } else {
                sb.append(messagePattern, i, j);
                deeplyAppendParameter(sb, argArray[argIndex], new HashMap<>());
                i = j + 2;
            }
        }
        if (i < messagePattern.length()) sb.append(messagePattern, i, messagePattern.length());
        return sb.toString();
    }

    private static boolean isEscapedDelimiter(String msg, int idx) { return idx > 0 && msg.charAt(idx - 1) == ESCAPE_CHAR; }
    private static boolean isDoubleEscaped(String msg, int idx) { return idx > 1 && msg.charAt(idx - 2) == ESCAPE_CHAR; }

    private static void deeplyAppendParameter(StringBuilder sb, Object o, Map<Object[], Object> seen) {
        if (o == null) { sb.append("null"); return; }
        Class<?> cls = o.getClass();
        if (!cls.isArray()) { sb.append(CommonUtils.toString(o)); return; }

        if (cls == boolean[].class || cls == byte[].class || cls == char[].class ||
            cls == short[].class || cls == int[].class || cls == long[].class ||
            cls == float[].class || cls == double[].class) {
            appendPrimitiveArray(sb, o);
        } else {
            objectArrayAppend(sb, (Object[]) o, seen);
        }
    }

    private static void appendPrimitiveArray(StringBuilder sb, Object array) {
        sb.append('[');
        int len = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < len; i++) {
            sb.append(java.lang.reflect.Array.get(array, i));
            if (i < len - 1) sb.append(", ");
        }
        sb.append(']');
    }

    private static void objectArrayAppend(StringBuilder sb, Object[] a, Map<Object[], Object> seen) {
        sb.append('[');
        if (!seen.containsKey(a)) {
            seen.put(a, null);
            for (int i = 0; i < a.length; i++) {
                deeplyAppendParameter(sb, a[i], seen);
                if (i < a.length - 1) sb.append(", ");
            }
            seen.remove(a);
        } else sb.append("...");
        sb.append(']');
    }
    
    
    
}
