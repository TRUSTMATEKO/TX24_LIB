package kr.tx24.lib.lang;


/**
 * 문자열을 지정된 구분자(delimiter) 기준으로 분리하여,
 * 목표 길이(targetLength)에 맞게 축약(abbreviation)하는 유틸리티.
 * <p>
 * 예:
 * <pre>
 * System.out.println(Abbreviator.format("abc.ddd.ccc.ddd.Name", '.', 15));
 * System.out.println(Abbreviator.format("abc/ddd/ccc/ddd/Name", '/', 15));
 * </pre>
 */
public final class Abbreviator {

    private static final int MAX_DELIMITERS = 16;

    private Abbreviator() {
        // Utility class, prevent instantiation
    }

    /**
     * 주어진 문자열을 구분자 단위로 잘라 목표 길이에 맞게 축약한다.
     *
     * @param fqClassName  축약할 전체 문자열 (예: FQCN)
     * @param delimiter    구분자 (예: '.', '/')
     * @param targetLength 목표 길이
     * @return 축약된 문자열
     */
    public static String format(String fqClassName, char delimiter, int targetLength) {
        if (fqClassName == null) {
            throw new IllegalArgumentException("Input string (fqClassName) must not be null");
        }

        int inLen = fqClassName.length();
        if (inLen <= targetLength) {
            return fqClassName;
        }

        int[] delimiterIndexes = new int[MAX_DELIMITERS];
        int[] lengthArray = new int[MAX_DELIMITERS + 1];

        int delimiterCount = computeDelimiterIndexes(fqClassName, delimiter, delimiterIndexes);

        if (delimiterCount == 0) {
            return fqClassName;
        }

        computeLengthArray(fqClassName, delimiterIndexes, lengthArray, delimiterCount, targetLength);

        StringBuilder buf = new StringBuilder(Math.min(targetLength, inLen));
        for (int i = 0; i <= delimiterCount; i++) {
            if (i == 0) {
                buf.append(fqClassName, 0, lengthArray[i] - 1);
            } else {
                int start = delimiterIndexes[i - 1];
                buf.append(fqClassName, start, start + lengthArray[i]);
            }
        }

        return buf.toString();
    }

    /**
     * 문자열 내에서 구분자의 위치를 배열에 저장한다.
     *
     * @param className          원본 문자열
     * @param delimiter          구분자
     * @param delimiterIndexes   구분자 인덱스 배열
     * @return 구분자 개수
     */
    private static int computeDelimiterIndexes(final String className, char delimiter, int[] delimiterIndexes) {
        int count = 0;
        int pos = 0;
        while (true) {
            pos = className.indexOf(delimiter, pos);
            if (pos != -1 && count < MAX_DELIMITERS) {
                delimiterIndexes[count++] = pos;
                pos++; // move past the found delimiter
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * 각 세그먼트의 최대 길이를 계산하여 lengthArray에 저장한다.
     */
    private static void computeLengthArray(final String className, int[] delimiterIndexes,
                                           int[] lengthArray, int delimiterCount, int targetLength) {
        int toTrim = className.length() - targetLength;

        for (int i = 0; i < delimiterCount; i++) {
            int previousDelimiterPos = (i == 0) ? -1 : delimiterIndexes[i - 1];
            int charactersInSegment = delimiterIndexes[i] - previousDelimiterPos - 1;

            int len = (toTrim > 0) ? Math.min(1, charactersInSegment) : charactersInSegment;

            toTrim -= (charactersInSegment - len);
            lengthArray[i] = len + 1; // include delimiter
        }

        int lastDelimiterIndex = delimiterCount - 1;
        lengthArray[delimiterCount] = className.length() - delimiterIndexes[lastDelimiterIndex];
    }



    
    public static void main(String[] args) {
    	System.out.println(Abbreviator.format("abc.ddd.ccc.ddd.Name",'.', 15));
    	System.out.println(Abbreviator.format("abc/ddd/ccc/ddd/Name",'/', 15));
    }

}
