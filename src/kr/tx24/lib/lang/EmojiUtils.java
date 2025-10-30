package kr.tx24.lib.lang;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 이모지(Emoji) 처리를 위한 유틸리티 클래스
 * 
 * <p>이 클래스는 문자열에서 이모지를 감지, 제거, 대체, 변환하는 기능을 제공합니다.
 * 유니코드 표준에 정의된 다양한 이모지 블록과 변형자들을 처리합니다.</p>
 * 
 * <h3>주요 기능</h3>
 * <ul>
 *   <li>이모지 제거 및 대체</li>
 *   <li>이모지 감지 및 카운팅</li>
 *   <li>이모지 추출 및 분류</li>
 *   <li>이모지 ↔ 텍스트 변환</li>
 *   <li>이모지 검증 및 필터링</li>
 * </ul>
 * 
 * @author Your Name
 * @version 1.0
 * @since 2025-10-30
 */
public class EmojiUtils {
    
    /**
     * 기본 이모지 패턴 (캐싱용)
     */
    private static final Pattern EMOJI_PATTERN = Pattern.compile(
        "(?:[\\x{1F600}-\\x{1F64F}]|" +
        "[\\x{1F300}-\\x{1F5FF}]|" +
        "[\\x{1F680}-\\x{1F6FF}]|" +
        "[\\x{1F700}-\\x{1F77F}]|" +
        "[\\x{1F780}-\\x{1F7FF}]|" +
        "[\\x{1F800}-\\x{1F8FF}]|" +
        "[\\x{1F900}-\\x{1F9FF}]|" +
        "[\\x{1FA00}-\\x{1FA6F}]|" +
        "[\\x{1FA70}-\\x{1FAFF}]|" +
        "[\\x{2600}-\\x{26FF}]|" +
        "[\\x{2700}-\\x{27BF}])" +
        "[\\x{FE0F}\\x{200D}\\x{1F3FB}-\\x{1F3FF}]*"
    );
    
    private static final Pattern FLAG_PATTERN = Pattern.compile("[\\x{1F1E6}-\\x{1F1FF}]{2}");
    private static final Pattern KEYCAP_PATTERN = Pattern.compile("[0-9#*]\\x{FE0F}?\\x{20E3}");
    private static final Pattern MODIFIER_PATTERN = Pattern.compile("[\\x{FE0F}\\x{200D}]");
    
    /**
     * 이모지 카테고리 열거형
     */
    public enum Category {
        EMOTICONS,      // 감정 표현 😀
        SYMBOLS,        // 기호 및 픽토그램 🎉
        TRANSPORT,      // 교통 및 지도 🚗
        FLAGS,          // 국기 🇰🇷
        ANIMALS,        // 동물 🐶
        FOOD,           // 음식 🍕
        ACTIVITIES,     // 활동 ⚽
        OBJECTS,        // 사물 📱
        NATURE,         // 자연 🌸
        PEOPLE,         // 사람 👨
        UNKNOWN         // 알 수 없음
    }
    
    /**
     * 일반적인 이모지와 텍스트 별칭 매핑
     */
    private static final Map<String, String> EMOJI_TO_TEXT = new HashMap<>();
    private static final Map<String, String> TEXT_TO_EMOJI = new HashMap<>();
    
    static {
        // 자주 사용되는 이모지 매핑
        addEmojiMapping("😀", ":smile:");
        addEmojiMapping("😂", ":joy:");
        addEmojiMapping("❤️", ":heart:");
        addEmojiMapping("😍", ":heart_eyes:");
        addEmojiMapping("😊", ":blush:");
        addEmojiMapping("👍", ":thumbsup:");
        addEmojiMapping("👎", ":thumbsdown:");
        addEmojiMapping("🎉", ":tada:");
        addEmojiMapping("🔥", ":fire:");
        addEmojiMapping("💯", ":100:");
        addEmojiMapping("🙏", ":pray:");
        addEmojiMapping("😭", ":sob:");
        addEmojiMapping("😱", ":scream:");
        addEmojiMapping("🤔", ":thinking:");
        addEmojiMapping("👏", ":clap:");
        // 더 많은 매핑 추가 가능
    }
    
    private static void addEmojiMapping(String emoji, String text) {
        EMOJI_TO_TEXT.put(emoji, text);
        TEXT_TO_EMOJI.put(text, emoji);
    }
    
    private EmojiUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    // ==================== 제거 관련 메서드 ====================
    
    /**
     * 문자열에서 모든 이모지를 제거합니다.
     * 
     * @param str 처리할 원본 문자열
     * @return 이모지가 제거된 문자열
     */
    public static String remove(String str) {
        if (isEmpty(str)) return "";
        
        String result = str.replaceAll("[\\p{So}\\p{Sc}]([\\x{200D}\\x{FE0F}][\\p{So}\\p{Sc}])+", "");
        result = result.replaceAll("[\\x{1F1E6}-\\x{1F1FF}]{2}", "");
        result = result.replaceAll("[0-9#*]\\x{FE0F}?\\x{20E3}", "");
        result = result.replaceAll("[\\p{So}\\p{Sc}][\\x{1F3FB}-\\x{1F3FF}]?", "");
        result = result.replaceAll("\\x{FE0F}", "");
        result = result.replaceAll("\\x{200D}", "");
        result = result.replaceAll("[\\x{2600}-\\x{26FF}\\x{2700}-\\x{27BF}]", "");
        
        return result;
    }
    
    /**
     * 모든 이모지를 엄격하게 제거합니다 (권장).
     * 
     * @param str 처리할 원본 문자열
     * @return 이모지가 제거된 문자열
     */
    public static String removeStrict(String str) {
        if (isEmpty(str)) return "";
        
        String result = EMOJI_PATTERN.matcher(str).replaceAll("");
        result = FLAG_PATTERN.matcher(result).replaceAll("");
        result = KEYCAP_PATTERN.matcher(result).replaceAll("");
        result = MODIFIER_PATTERN.matcher(result).replaceAll("");
        
        return result;
    }
    
    /**
     * 특정 카테고리의 이모지만 선택적으로 제거합니다.
     * 
     * @param str 처리할 원본 문자열
     * @param keepSymbols true면 기호/픽토그램 유지, false면 제거
     * @return 선택적으로 이모지가 제거된 문자열
     */
    public static String removeSelective(String str, boolean keepSymbols) {
        if (isEmpty(str)) return "";
        
        String result = str;
        result = result.replaceAll("[\\x{1F600}-\\x{1F64F}]", "");
        
        if (!keepSymbols) {
            result = result.replaceAll("[\\x{1F300}-\\x{1F5FF}]", "");
            result = result.replaceAll("[\\x{1F680}-\\x{1F6FF}]", "");
            result = result.replaceAll("[\\x{1F900}-\\x{1F9FF}]", "");
            result = result.replaceAll("[\\x{2600}-\\x{26FF}]", "");
        }
        
        return result;
    }
    
    /**
     * 이모지를 공백으로 대체하여 가독성을 유지합니다.
     * 
     * @param str 처리할 원본 문자열
     * @return 이모지가 공백으로 대체되고 정리된 문자열
     */
    public static String replaceWithSpace(String str) {
        if (isEmpty(str)) return "";
        
        return removeStrict(str)
            .replaceAll("\\s{2,}", " ")
            .trim();
    }
    
    /**
     * 이모지를 지정한 문자열로 대체합니다.
     * 
     * @param str 처리할 원본 문자열
     * @param replacement 대체할 문자열
     * @return 이모지가 대체된 문자열
     * 
     * @example
     * <pre>
     * EmojiUtils.replaceWith("안녕 😀", "[emoji]")  // "안녕 [emoji]"
     * EmojiUtils.replaceWith("😀😀", "*")          // "**"
     * </pre>
     */
    public static String replaceWith(String str, String replacement) {
        if (isEmpty(str)) return "";
        if (replacement == null) replacement = "";
        
        String result = EMOJI_PATTERN.matcher(str).replaceAll(replacement);
        result = FLAG_PATTERN.matcher(result).replaceAll(replacement);
        result = KEYCAP_PATTERN.matcher(result).replaceAll(replacement);
        
        return result;
    }
    
    // ==================== 감지 및 분석 관련 메서드 ====================
    
    /**
     * 문자열에 이모지가 포함되어 있는지 확인합니다.
     * 
     * @param str 검사할 문자열
     * @return 이모지가 포함되어 있으면 true
     */
    public static boolean contains(String str) {
        if (isEmpty(str)) return false;
        
        return str.matches(".*[" +
            "\\x{1F600}-\\x{1F64F}" +
            "\\x{1F300}-\\x{1F5FF}" +
            "\\x{1F680}-\\x{1F6FF}" +
            "\\x{1F900}-\\x{1F9FF}" +
            "\\x{1FA00}-\\x{1FAFF}" +
            "\\x{2600}-\\x{26FF}" +
            "\\x{2700}-\\x{27BF}" +
            "].*");
    }
    
    /**
     * 문자열에 포함된 이모지의 개수를 반환합니다.
     * 
     * @param str 검사할 문자열
     * @return 이모지 개수
     */
    public static int count(String str) {
        if (isEmpty(str)) return 0;
        
        int count = 0;
        Matcher matcher = EMOJI_PATTERN.matcher(str);
        while (matcher.find()) {
            count++;
        }
        
        matcher = FLAG_PATTERN.matcher(str);
        while (matcher.find()) {
            count++;
        }
        
        return count;
    }
    
    /**
     * 이모지만 추출하여 반환합니다.
     * 
     * @param str 처리할 원본 문자열
     * @return 이모지만 포함된 문자열
     */
    public static String extract(String str) {
        if (isEmpty(str)) return "";
        
        StringBuilder result = new StringBuilder();
        
        Matcher matcher = EMOJI_PATTERN.matcher(str);
        while (matcher.find()) {
            result.append(matcher.group());
        }
        
        matcher = FLAG_PATTERN.matcher(str);
        while (matcher.find()) {
            result.append(matcher.group());
        }
        
        return result.toString();
    }
    
    /**
     * 문자열에서 모든 이모지를 List로 추출합니다.
     * 
     * @param str 처리할 원본 문자열
     * @return 이모지 리스트
     * 
     * @example
     * <pre>
     * EmojiUtils.extractAsList("안녕 😀 하세요 🎉")
     * // 결과: ["😀", "🎉"]
     * </pre>
     */
    public static List<String> extractAsList(String str) {
        if (isEmpty(str)) return Collections.emptyList();
        
        List<String> result = new ArrayList<>();
        
        Matcher matcher = EMOJI_PATTERN.matcher(str);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        
        matcher = FLAG_PATTERN.matcher(str);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        
        return result;
    }
    
    /**
     * 문자열에서 이모지의 위치(인덱스) 정보를 반환합니다.
     * 
     * @param str 검사할 문자열
     * @return 이모지와 시작 위치의 맵
     * 
     * @example
     * <pre>
     * EmojiUtils.findPositions("안녕😀하세요🎉")
     * // 결과: {2=😀, 6=🎉}
     * </pre>
     */
    public static Map<Integer, String> findPositions(String str) {
        if (isEmpty(str)) return Collections.emptyMap();
        
        Map<Integer, String> positions = new LinkedHashMap<>();
        
        Matcher matcher = EMOJI_PATTERN.matcher(str);
        while (matcher.find()) {
            positions.put(matcher.start(), matcher.group());
        }
        
        matcher = FLAG_PATTERN.matcher(str);
        while (matcher.find()) {
            positions.put(matcher.start(), matcher.group());
        }
        
        return positions;
    }
    
    // ==================== 변환 관련 메서드 ====================
    
    /**
     * 이모지를 텍스트 별칭으로 변환합니다.
     * 
     * @param str 원본 문자열
     * @return 이모지가 텍스트로 변환된 문자열
     * 
     * @example
     * <pre>
     * EmojiUtils.toText("안녕 😀")  // "안녕 :smile:"
     * EmojiUtils.toText("좋아요 👍")  // "좋아요 :thumbsup:"
     * </pre>
     */
    public static String toText(String str) {
        if (isEmpty(str)) return "";
        
        String result = str;
        for (Map.Entry<String, String> entry : EMOJI_TO_TEXT.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    /**
     * 텍스트 별칭을 이모지로 변환합니다.
     * 
     * @param str 원본 문자열
     * @return 텍스트가 이모지로 변환된 문자열
     * 
     * @example
     * <pre>
     * EmojiUtils.fromText("안녕 :smile:")     // "안녕 😀"
     * EmojiUtils.fromText("좋아요 :thumbsup:") // "좋아요 👍"
     * </pre>
     */
    public static String fromText(String str) {
        if (isEmpty(str)) return "";
        
        String result = str;
        for (Map.Entry<String, String> entry : TEXT_TO_EMOJI.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
    
    /**
     * 이모지를 유니코드 이스케이프 시퀀스로 변환합니다.
     * 
     * @param str 원본 문자열
     * @return 유니코드 이스케이프 형식의 문자열
     * 
     * @example
     * <pre>
     * EmojiUtils.toUnicode("😀")  // "\\uD83D\\uDE00"
     * </pre>
     */
    public static String toUnicode(String str) {
        if (isEmpty(str)) return "";
        
        StringBuilder sb = new StringBuilder();
        for (char c : str.toCharArray()) {
            if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                sb.append(String.format("\\u%04X", (int) c));
            } else if (c > 127) {
                sb.append(String.format("\\u%04X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
    
    // ==================== 검증 및 필터링 관련 메서드 ====================
    
    /**
     * 문자열이 이모지로만 구성되어 있는지 확인합니다.
     * 
     * @param str 검사할 문자열
     * @return 이모지만 있으면 true
     * 
     * @example
     * <pre>
     * EmojiUtils.isOnlyEmoji("😀🎉")     // true
     * EmojiUtils.isOnlyEmoji("안녕 😀")  // false
     * </pre>
     */
    public static boolean isOnlyEmoji(String str) {
        if (isEmpty(str)) return false;
        return removeStrict(str).isEmpty();
    }
    
    /**
     * 허용된 이모지만 남기고 나머지는 제거합니다.
     * 
     * @param str 원본 문자열
     * @param allowedEmojis 허용할 이모지 목록
     * @return 필터링된 문자열
     * 
     * @example
     * <pre>
     * Set<String> allowed = Set.of("😀", "👍");
     * EmojiUtils.filterAllowed("😀🎉👍", allowed)  // "😀👍"
     * </pre>
     */
    public static String filterAllowed(String str, Set<String> allowedEmojis) {
        if (isEmpty(str) || allowedEmojis == null || allowedEmojis.isEmpty()) {
            return removeStrict(str);
        }
        
        List<String> emojis = extractAsList(str);
        StringBuilder result = new StringBuilder(str);
        
        for (String emoji : emojis) {
            if (!allowedEmojis.contains(emoji)) {
                result = new StringBuilder(result.toString().replace(emoji, ""));
            }
        }
        
        return result.toString();
    }
    
    /**
     * 차단된 이모지를 제거합니다.
     * 
     * @param str 원본 문자열
     * @param blockedEmojis 차단할 이모지 목록
     * @return 필터링된 문자열
     * 
     * @example
     * <pre>
     * Set<String> blocked = Set.of("🎉", "🔥");
     * EmojiUtils.filterBlocked("😀🎉🔥", blocked)  // "😀"
     * </pre>
     */
    public static String filterBlocked(String str, Set<String> blockedEmojis) {
        if (isEmpty(str) || blockedEmojis == null || blockedEmojis.isEmpty()) {
            return str;
        }
        
        String result = str;
        for (String blocked : blockedEmojis) {
            result = result.replace(blocked, "");
        }
        
        return result;
    }
    
    // ==================== 통계 관련 메서드 ====================
    
    /**
     * 이모지 사용 빈도를 계산합니다.
     * 
     * @param str 검사할 문자열
     * @return 이모지와 빈도수의 맵
     * 
     * @example
     * <pre>
     * EmojiUtils.getFrequency("😀😀🎉😀")
     * // 결과: {😀=3, 🎉=1}
     * </pre>
     */
    public static Map<String, Integer> getFrequency(String str) {
        if (isEmpty(str)) return Collections.emptyMap();
        
        Map<String, Integer> frequency = new HashMap<>();
        List<String> emojis = extractAsList(str);
        
        for (String emoji : emojis) {
            frequency.put(emoji, frequency.getOrDefault(emoji, 0) + 1);
        }
        
        return frequency;
    }
    
    /**
     * 가장 많이 사용된 이모지를 반환합니다.
     * 
     * @param str 검사할 문자열
     * @return 가장 빈도가 높은 이모지 (없으면 빈 문자열)
     * 
     * @example
     * <pre>
     * EmojiUtils.getMostFrequent("😀😀🎉😀🎉🎉🎉")  // "🎉"
     * </pre>
     */
    public static String getMostFrequent(String str) {
        Map<String, Integer> frequency = getFrequency(str);
        if (frequency.isEmpty()) return "";
        
        return frequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("");
    }
    
    // ==================== 크기 관련 메서드 ====================
    
    /**
     * 이모지의 바이트 크기를 계산합니다.
     * 
     * @param str 검사할 문자열
     * @param charset 사용할 문자셋
     * @return 바이트 크기
     */
    public static int getByteSize(String str, Charset charset) {
        if (isEmpty(str)) return 0;
        return extract(str).getBytes(charset).length;
    }
    
    /**
     * 이모지의 UTF-8 바이트 크기를 계산합니다.
     * 
     * @param str 검사할 문자열
     * @return UTF-8 바이트 크기
     */
    public static int getByteSize(String str) {
        return getByteSize(str, StandardCharsets.UTF_8);
    }
    
    /**
     * 문자열이 null이거나 비어있는지 확인하는 헬퍼 메서드
     */
    private static boolean isEmpty(String str) {
        return str == null || str.length() == 0;
    }
}