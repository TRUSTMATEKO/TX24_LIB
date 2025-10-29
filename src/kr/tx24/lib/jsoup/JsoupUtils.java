package kr.tx24.lib.jsoup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.TypeRegistry;

/**
 * HTML Table 파싱 유틸리티
 * 
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>HTML Table을 List<Map<String,Object>>로 변환</li>
 *   <li>HTML Table을 TypeRegistry로 지정된 타입으로 변환</li>
 *   <li>class, id, CSS 선택자로 table 지정</li>
 *   <li>thead 자동 감지 (없으면 첫 번째 tr 사용)</li>
 *   <li>Jsoup Document/Element 지원</li>
 * </ul>
 * 
 * <p><b>필수 의존성:</b></p>
 * <pre>
 * {@code
 * <dependency>
 *     <groupId>org.jsoup</groupId>
 *     <artifactId>jsoup</artifactId>
 *     <version>1.21.2</version>
 * </dependency>
 * }
 * </pre>
 * 
 * <p><b>사용 예:</b></p>
 * <pre>
 * // HTML 파싱
 * Document doc = Jsoup.parse(htmlString);
 * 
 * // ID로 테이블 찾기 (Map)
 * List<Map<String, Object>> data = HtmlUtils.parseTableById(doc, "userTable");
 * 
 * // Class로 테이블 찾기 (객체)
 * List<User> users = HtmlUtils.parseTableByClass(doc, "data-table", User.class);
 * 
 * // TypeRegistry 사용
 * List<Map<String, Object>> data = HtmlUtils.parseTable(doc, "table", TypeRegistry.MAP_OBJECT);
 * 
 * // TypeReference 사용
 * List<User> users = HtmlUtils.parseTable(doc, "table", new TypeReference<List<User>>(){});
 * </pre>
 * 
 * @author TX24
 */
public class JsoupUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(JsoupUtils.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    
    // ==================== Map<String, Object> 반환 ====================
    
    /**
     * CSS 선택자로 테이블을 찾아서 파싱 (Map 반환)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Document doc = Jsoup.parse(html);
     * List<Map<String, Object>> data = HtmlUtils.parseTable(doc, "#userTable");
     * </pre>
     * 
     * @param doc Jsoup Document
     * @param selector CSS 선택자
     * @return 파싱된 데이터 (List<Map<String, Object>>)
     */
    public static List<Map<String, Object>> parseTable(Document doc, String selector) {
        if (doc == null || selector == null || selector.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            Element table = doc.selectFirst(selector);
            if (table == null) {
                logger.warn("테이블을 찾을 수 없음 : selector={}", selector);
                return new ArrayList<>();
            }
            
            return parseTableElement(table);
        } catch (Exception e) {
            logger.warn("테이블 파싱 실패 : {}", CommonUtils.getExceptionMessage(e));
            return new ArrayList<>();
        }
    }
    
    /**
     * ID로 테이블을 찾아서 파싱 (Map 반환)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Document doc = Jsoup.parse(html);
     * List<Map<String, Object>> data = HtmlUtils.parseTableById(doc, "userTable");
     * </pre>
     * 
     * @param doc Jsoup Document
     * @param id 테이블 ID
     * @return 파싱된 데이터
     */
    public static List<Map<String, Object>> parseTableById(Document doc, String id) {
        if (doc == null || id == null || id.isEmpty()) {
            return new ArrayList<>();
        }
        
        return parseTable(doc, "#" + id);
    }
    
    /**
     * Class명으로 테이블을 찾아서 파싱 (Map 반환)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Document doc = Jsoup.parse(html);
     * List<Map<String, Object>> data = HtmlUtils.parseTableByClass(doc, "data-table");
     * </pre>
     * 
     * @param doc Jsoup Document
     * @param className 테이블 class명
     * @return 파싱된 데이터
     */
    public static List<Map<String, Object>> parseTableByClass(Document doc, String className) {
        if (doc == null || className == null || className.isEmpty()) {
            return new ArrayList<>();
        }
        
        return parseTable(doc, "table." + className);
    }
    
    /**
     * Element로 테이블을 직접 파싱 (Map 반환)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Document doc = Jsoup.parse(html);
     * Element table = doc.selectFirst("table");
     * List<Map<String, Object>> data = HtmlUtils.parseTableElement(table);
     * </pre>
     * 
     * @param table 테이블 Element
     * @return 파싱된 데이터
     */
    public static List<Map<String, Object>> parseTableElement(Element table) {
        if (table == null) {
            return new ArrayList<>();
        }
        
        try {
            // 헤더 추출
            List<String> headers = extractHeaders(table);
            if (headers.isEmpty()) {
                logger.warn("테이블 헤더를 찾을 수 없음");
                return new ArrayList<>();
            }
            
            // 데이터 행 추출
            List<Map<String, Object>> result = new ArrayList<>();
            Elements dataRows = extractDataRows(table);
            
            for (Element row : dataRows) {
                Map<String, Object> rowData = parseRow(row, headers);
                if (!rowData.isEmpty()) {
                    result.add(rowData);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            logger.warn("테이블 파싱 실패 : {}", CommonUtils.getExceptionMessage(e));
            return new ArrayList<>();
        }
    }
    
    
    // ==================== TypeRegistry 지원 ====================
    
    /**
     * CSS 선택자로 테이블을 찾아서 파싱 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Document doc = Jsoup.parse(html);
     * 
     * // Map으로
     * List<Map<String, Object>> data = HtmlUtils.parseTable(doc, "#userTable", TypeRegistry.MAP_OBJECT);
     * 
     * // List<String>으로
     * List<List<String>> data = HtmlUtils.parseTable(doc, "#userTable", TypeRegistry.LIST_STRING);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param selector CSS 선택자
     * @param typeRegistry TypeRegistry
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTable(Document doc, String selector, TypeRegistry typeRegistry) {
        List<Map<String, Object>> mapList = parseTable(doc, selector);
        return convertToType(mapList, typeRegistry);
    }
    
    /**
     * ID로 테이블을 찾아서 파싱 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<Map<String, Object>> data = HtmlUtils.parseTableById(doc, "userTable", TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param id 테이블 ID
     * @param typeRegistry TypeRegistry
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableById(Document doc, String id, TypeRegistry typeRegistry) {
        List<Map<String, Object>> mapList = parseTableById(doc, id);
        return convertToType(mapList, typeRegistry);
    }
    
    /**
     * Class명으로 테이블을 찾아서 파싱 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<Map<String, Object>> data = HtmlUtils.parseTableByClass(doc, "data-table", TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param className 테이블 class명
     * @param typeRegistry TypeRegistry
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableByClass(Document doc, String className, TypeRegistry typeRegistry) {
        List<Map<String, Object>> mapList = parseTableByClass(doc, className);
        return convertToType(mapList, typeRegistry);
    }
    
    /**
     * Element로 테이블을 직접 파싱 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Element table = doc.selectFirst("table");
     * List<Map<String, Object>> data = HtmlUtils.parseTableElement(table, TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param table 테이블 Element
     * @param typeRegistry TypeRegistry
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableElement(Element table, TypeRegistry typeRegistry) {
        List<Map<String, Object>> mapList = parseTableElement(table);
        return convertToType(mapList, typeRegistry);
    }
    
    
    // ==================== Class 타입 지원 ====================
    
    /**
     * CSS 선택자로 테이블을 찾아서 파싱 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<User> users = HtmlUtils.parseTable(doc, "#userTable", User.class);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param selector CSS 선택자
     * @param type 변환할 클래스 타입
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTable(Document doc, String selector, Class<T> type) {
        List<Map<String, Object>> mapList = parseTable(doc, selector);
        return convertToType(mapList, type);
    }
    
    /**
     * ID로 테이블을 찾아서 파싱 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<User> users = HtmlUtils.parseTableById(doc, "userTable", User.class);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param id 테이블 ID
     * @param type 변환할 클래스 타입
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableById(Document doc, String id, Class<T> type) {
        List<Map<String, Object>> mapList = parseTableById(doc, id);
        return convertToType(mapList, type);
    }
    
    /**
     * Class명으로 테이블을 찾아서 파싱 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<User> users = HtmlUtils.parseTableByClass(doc, "data-table", User.class);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param className 테이블 class명
     * @param type 변환할 클래스 타입
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableByClass(Document doc, String className, Class<T> type) {
        List<Map<String, Object>> mapList = parseTableByClass(doc, className);
        return convertToType(mapList, type);
    }
    
    /**
     * Element로 테이블을 직접 파싱 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Element table = doc.selectFirst("table");
     * List<User> users = HtmlUtils.parseTableElement(table, User.class);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param table 테이블 Element
     * @param type 변환할 클래스 타입
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableElement(Element table, Class<T> type) {
        List<Map<String, Object>> mapList = parseTableElement(table);
        return convertToType(mapList, type);
    }
    
    
    // ==================== TypeReference 지원 ====================
    
    /**
     * CSS 선택자로 테이블을 찾아서 파싱 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<User> users = HtmlUtils.parseTable(doc, "#userTable", new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param selector CSS 선택자
     * @param typeRef TypeReference
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTable(Document doc, String selector, TypeReference<List<T>> typeRef) {
        List<Map<String, Object>> mapList = parseTable(doc, selector);
        return convertToType(mapList, typeRef);
    }
    
    /**
     * ID로 테이블을 찾아서 파싱 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<User> users = HtmlUtils.parseTableById(doc, "userTable", new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param id 테이블 ID
     * @param typeRef TypeReference
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableById(Document doc, String id, TypeReference<List<T>> typeRef) {
        List<Map<String, Object>> mapList = parseTableById(doc, id);
        return convertToType(mapList, typeRef);
    }
    
    /**
     * Class명으로 테이블을 찾아서 파싱 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<User> users = HtmlUtils.parseTableByClass(doc, "data-table", new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param className 테이블 class명
     * @param typeRef TypeReference
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableByClass(Document doc, String className, TypeReference<List<T>> typeRef) {
        List<Map<String, Object>> mapList = parseTableByClass(doc, className);
        return convertToType(mapList, typeRef);
    }
    
    /**
     * Element로 테이블을 직접 파싱 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Element table = doc.selectFirst("table");
     * List<User> users = HtmlUtils.parseTableElement(table, new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param table 테이블 Element
     * @param typeRef TypeReference
     * @return 파싱된 데이터
     */
    public static <T> List<T> parseTableElement(Element table, TypeReference<List<T>> typeRef) {
        List<Map<String, Object>> mapList = parseTableElement(table);
        return convertToType(mapList, typeRef);
    }
    
    
    // ==================== 여러 테이블 파싱 ====================
    
    /**
     * 여러 테이블을 한번에 파싱 (Map)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<List<Map<String, Object>>> allTables = HtmlUtils.parseAllTables(doc, "table.data");
     * </pre>
     * 
     * @param doc Jsoup Document
     * @param selector CSS 선택자
     * @return 각 테이블의 파싱된 데이터 리스트
     */
    public static List<List<Map<String, Object>>> parseAllTables(Document doc, String selector) {
        if (doc == null || selector == null || selector.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            Elements tables = doc.select(selector);
            List<List<Map<String, Object>>> result = new ArrayList<>();
            
            for (Element table : tables) {
                List<Map<String, Object>> tableData = parseTableElement(table);
                if (!tableData.isEmpty()) {
                    result.add(tableData);
                }
            }
            
            return result;
        } catch (Exception e) {
            logger.warn("테이블 파싱 실패 : {}", CommonUtils.getExceptionMessage(e));
            return new ArrayList<>();
        }
    }
    
    /**
     * 여러 테이블을 한번에 파싱 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * List<List<User>> allTables = HtmlUtils.parseAllTables(doc, "table.data", User.class);
     * </pre>
     * 
     * @param <T> 반환 타입
     * @param doc Jsoup Document
     * @param selector CSS 선택자
     * @param type 변환할 클래스 타입
     * @return 각 테이블의 파싱된 데이터 리스트
     */
    public static <T> List<List<T>> parseAllTables(Document doc, String selector, Class<T> type) {
        if (doc == null || selector == null || selector.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            Elements tables = doc.select(selector);
            List<List<T>> result = new ArrayList<>();
            
            for (Element table : tables) {
                List<T> tableData = parseTableElement(table, type);
                if (!tableData.isEmpty()) {
                    result.add(tableData);
                }
            }
            
            return result;
        } catch (Exception e) {
            logger.warn("테이블 파싱 실패 : {}", CommonUtils.getExceptionMessage(e));
            return new ArrayList<>();
        }
    }
    
    
    // ==================== 내부 헬퍼 메서드 ====================
    
    /**
     * Map 리스트를 TypeRegistry 타입으로 변환
     * 
     * @param <T> 반환 타입
     * @param mapList Map 리스트
     * @param typeRegistry TypeRegistry
     * @return 변환된 리스트
     */
    private static <T> List<T> convertToType(List<Map<String, Object>> mapList, TypeRegistry typeRegistry) {
        if (mapList == null || mapList.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            List<T> result = new ArrayList<>();
            for (Map<String, Object> map : mapList) {
                T converted = mapper.convertValue(map, typeRegistry.get());
                result.add(converted);
            }
            return result;
        } catch (Exception e) {
            logger.warn("타입 변환 실패 : {}", CommonUtils.getExceptionMessage(e));
            return new ArrayList<>();
        }
    }
    
    /**
     * Map 리스트를 Class 타입으로 변환
     * 
     * @param <T> 반환 타입
     * @param mapList Map 리스트
     * @param type 변환할 클래스
     * @return 변환된 리스트
     */
    private static <T> List<T> convertToType(List<Map<String, Object>> mapList, Class<T> type) {
        if (mapList == null || mapList.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            List<T> result = new ArrayList<>();
            for (Map<String, Object> map : mapList) {
                T converted = mapper.convertValue(map, type);
                result.add(converted);
            }
            return result;
        } catch (Exception e) {
            logger.warn("타입 변환 실패 : {}", CommonUtils.getExceptionMessage(e));
            return new ArrayList<>();
        }
    }
    
    /**
     * Map 리스트를 TypeReference 타입으로 변환
     * 
     * @param <T> 반환 타입
     * @param mapList Map 리스트
     * @param typeRef TypeReference
     * @return 변환된 리스트
     */
    private static <T> List<T> convertToType(List<Map<String, Object>> mapList, TypeReference<List<T>> typeRef) {
        if (mapList == null || mapList.isEmpty()) {
            return new ArrayList<>();
        }
        
        try {
            // Map List를 JSON으로 변환 후 다시 역직렬화
            String json = mapper.writeValueAsString(mapList);
            return mapper.readValue(json, typeRef);
        } catch (Exception e) {
            logger.warn("타입 변환 실패 : {}", CommonUtils.getExceptionMessage(e));
            return new ArrayList<>();
        }
    }
    
    /**
     * 테이블에서 헤더 추출
     * 
     * 우선순위:
     * 1. thead > tr > th
     * 2. thead > tr > td
     * 3. tbody > tr:first-child > th
     * 4. tbody > tr:first-child > td
     * 
     * @param table 테이블 Element
     * @return 헤더 리스트
     */
    private static List<String> extractHeaders(Element table) {
        List<String> headers = new ArrayList<>();
        
        // 1. thead에서 찾기
        Element thead = table.selectFirst("thead");
        if (thead != null) {
            Element headerRow = thead.selectFirst("tr");
            if (headerRow != null) {
                // th 우선
                Elements ths = headerRow.select("th");
                if (!ths.isEmpty()) {
                    for (Element th : ths) {
                        headers.add(th.text());
                    }
                    return headers;
                }
                
                // td 대체
                Elements tds = headerRow.select("td");
                for (Element td : tds) {
                    headers.add(td.text());
                }
                return headers;
            }
        }
        
        // 2. tbody의 첫 번째 tr을 헤더로 사용
        Element tbody = table.selectFirst("tbody");
        if (tbody != null) {
            Element firstRow = tbody.selectFirst("tr");
            if (firstRow != null) {
                // th 우선
                Elements ths = firstRow.select("th");
                if (!ths.isEmpty()) {
                    for (Element th : ths) {
                        headers.add(th.text());
                    }
                    return headers;
                }
                
                // td 대체
                Elements tds = firstRow.select("td");
                for (Element td : tds) {
                    headers.add(td.text());
                }
                return headers;
            }
        }
        
        // 3. tbody 없이 바로 tr (thead도 tbody도 없는 경우)
        Element firstRow = table.selectFirst("tr");
        if (firstRow != null) {
            // th 우선
            Elements ths = firstRow.select("th");
            if (!ths.isEmpty()) {
                for (Element th : ths) {
                    headers.add(th.text());
                }
                return headers;
            }
            
            // td 대체
            Elements tds = firstRow.select("td");
            for (Element td : tds) {
                headers.add(td.text());
            }
        }
        
        return headers;
    }
    
    /**
     * 데이터 행 추출
     * 
     * @param table 테이블 Element
     * @return 데이터 행 Elements
     */
    private static Elements extractDataRows(Element table) {
        Elements dataRows = new Elements();
        
        // thead가 있는 경우: tbody의 모든 행
        Element thead = table.selectFirst("thead");
        if (thead != null) {
            Element tbody = table.selectFirst("tbody");
            if (tbody != null) {
                return tbody.select("tr");
            }
            return dataRows;
        }
        
        // thead가 없는 경우: tbody의 두 번째 행부터
        Element tbody = table.selectFirst("tbody");
        if (tbody != null) {
            Elements allRows = tbody.select("tr");
            if (allRows.size() > 1) {
                // 첫 번째 행은 헤더로 사용했으므로 제외
                for (int i = 1; i < allRows.size(); i++) {
                    dataRows.add(allRows.get(i));
                }
            }
            return dataRows;
        }
        
        // tbody도 없는 경우: 두 번째 tr부터
        Elements allRows = table.select("tr");
        if (allRows.size() > 1) {
            for (int i = 1; i < allRows.size(); i++) {
                dataRows.add(allRows.get(i));
            }
        }
        
        return dataRows;
    }
    
    /**
     * 행을 Map으로 변환
     * 
     * @param row 행 Element
     * @param headers 헤더 리스트
     * @return Map<String, Object>
     */
    private static Map<String, Object> parseRow(Element row, List<String> headers) {
        Map<String, Object> rowData = new LinkedHashMap<>();
        
        Elements cells = row.select("td, th");
        
        for (int i = 0; i < headers.size() && i < cells.size(); i++) {
            String header = headers.get(i);
            Element cell = cells.get(i);
            
            // 셀 값 추출 (텍스트)
            String value = cell.text();
            
            // 빈 헤더는 "column_N" 형식으로
            if (header == null || header.isEmpty()) {
                header = "column_" + i;
            }
            
            rowData.put(header, value);
        }
        
        return rowData;
    }
    
    /**
     * 테이블 정보 반환 (디버깅용)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Element table = doc.selectFirst("table");
     * String info = JsoupUtils.getTableInfo(table);
     * System.out.println(info);
     * </pre>
     * 
     * @param table 테이블 Element
     * @return 테이블 정보 문자열
     */
    public String getTableInfo(Element table) {
        StringBuilder sb = new StringBuilder();
        
        if (table == null) {
            sb.append("Table is null");
            return sb.toString();
        }
        
        sb.append("=== Table Info ===\n");
        sb.append("Tag: ").append(table.tagName()).append("\n");
        sb.append("ID: ").append(table.id()).append("\n");
        sb.append("Class: ").append(table.className()).append("\n");
        
        Element thead = table.selectFirst("thead");
        sb.append("Has thead: ").append(thead != null).append("\n");
        
        Element tbody = table.selectFirst("tbody");
        sb.append("Has tbody: ").append(tbody != null).append("\n");
        
        Elements rows = table.select("tr");
        sb.append("Total rows: ").append(rows.size()).append("\n");
        
        List<String> headers = extractHeaders(table);
        sb.append("Headers: ").append(headers).append("\n");
        sb.append("==================");
        
        return sb.toString();
    }
}