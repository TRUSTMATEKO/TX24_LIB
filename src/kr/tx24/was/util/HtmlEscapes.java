package kr.tx24.was.util;

import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.io.CharacterEscapes;
import com.fasterxml.jackson.core.io.SerializedString;
import java.util.HashMap;
import java.util.Map;

/**
 * @author juseop
 * Jackson을 위한 HTML/XSS 특수 문자 이스케이프 핸들러.
 */
public class HtmlEscapes extends CharacterEscapes {

    private static final long serialVersionUID = 8538651749870976490L;
    private final int[] asciiEscapes;
    private final Map<Integer, SerializableString> escapeMap; // 룩업 테이블

    public HtmlEscapes() {
        // 1. 기본 아스키 이스케이프 배열 초기화
        asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
        escapeMap = new HashMap<>();

        // 2. XSS 방지 처리할 특수 문자 지정
        final char[] customChars = {'<', '>', '"', '\'', '(', ')', '#', '&'}; // '&' 추가 검토

        // 3. 커스텀 이스케이프 시퀀스 맵핑 (성능 최적화)
        // 안전한 HTML 엔티티 시퀀스를 직접 사용하거나, 유니코드 이스케이프 사용
        
        // 참고: StringEscapeUtils 사용 시 결과 예시를 직접 하드코딩합니다.
        // 유니코드 이스케이프 (\u003c 등) 대신 HTML 엔티티를 사용합니다.
        escapeMap.put((int) '<', new SerializedString("&lt;"));
        escapeMap.put((int) '>', new SerializedString("&gt;"));
        escapeMap.put((int) '"', new SerializedString("&quot;"));
        // HTML5에서 안전한 홑따옴표 이스케이프 (\u0027 또는 #39)
        escapeMap.put((int) '\'', new SerializedString("&#39;"));
        escapeMap.put((int) '(', new SerializedString("&#40;"));
        escapeMap.put((int) ')', new SerializedString("&#41;"));
        escapeMap.put((int) '#', new SerializedString("&#35;"));
        // 앰퍼샌드는 HTML 이스케이프의 기본이므로 추가 (StringEscapeUtils.escapeHtml4 동작 모방)
        escapeMap.put((int) '&', new SerializedString("&amp;")); 

        // 4. 배열에 커스텀 마커 설정
        for (char ch : customChars) {
            asciiEscapes[ch] = CharacterEscapes.ESCAPE_CUSTOM;
        }
        
        // 앰퍼샌드는 JSON 기본 설정에 이미 ESCAPE_CUSTOM이 아닌 ESCAPE_STANDARD가 설정되어 있을 수 있습니다.
        // 하지만 위에서 Map에 넣고 ESCAPE_CUSTOM을 설정하면 동작합니다.
    }
    
    @Override
    public int[] getEscapeCodesForAscii() {
        return asciiEscapes;    
    }
    
    @Override
    public SerializableString getEscapeSequence(int ch) {
        // 미리 정의된 맵에서 룩업하여 성능을 최적화합니다.
        return escapeMap.get(ch);
    }
}