package kr.tx24.lib.mapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.TypeRegistry;

/**
 * CSV 전용 Jackson Utility
 * 
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>CSV ↔ Object 변환</li>
 *   <li>TypeReference, TypeRegistry 지원</li>
 *   <li>헤더 포함/제외 옵션</li>
 *   <li>구분자 설정 (comma, tab, semicolon 등)</li>
 *   <li>파일 입출력 지원 (Path)</li>
 *   <li>CSV 유효성 검사</li>
 *   <li>한글 헤더 지원</li>
 *   <li>객체 복사 (JacksonAbstract 상속)</li>
 * </ul>
 * 
 * <p><b>필수 의존성:</b></p>
 * <pre>
 * {@code
 * <dependency>
 *     <groupId>com.fasterxml.jackson.dataformat</groupId>
 *     <artifactId>jackson-dataformat-csv</artifactId>
 *     <version>2.15.0</version>
 * </dependency>
 * }
 * </pre>
 * 
 * <p><b>사용 예:</b></p>
 * <pre>
 * JacksonCsvUtils csv = new JacksonCsvUtils();
 * 
 * // 1. 기본 사용 (영문 헤더)
 * List<User> users = List.of(
 *     new User("John", 30),
 *     new User("Jane", 25)
 * );
 * String csvString = csv.toCsv(users, User.class);
 * // name,age
 * // John,30
 * // Jane,25
 * 
 * // 2. 한글 헤더로 쓰기
 * Map<String, String> headerMap = Map.of(
 *     "name", "이름",
 *     "age", "나이"
 * );
 * String koreanCsv = csv.toCsvWithCustomHeaders(users, User.class, headerMap);
 * // 이름,나이
 * // John,30
 * // Jane,25
 * 
 * // 3. 한글 헤더 CSV 읽기
 * String csv = "이름,나이\n홍길동,30\n김철수,25";
 * List<User> userList = csv.fromCsvWithCustomHeaders(csv, User.class, headerMap);
 * 
 * // 4. @JsonProperty 사용 (권장)
 * // public class User {
 * //     {@literal @}JsonProperty("이름")
 * //     private String name;
 * //     {@literal @}JsonProperty("나이")
 * //     private int age;
 * // }
 * String csv2 = csv.toCsv(users, User.class);  // 자동으로 한글 헤더 사용
 * </pre>
 * 
 * <p><b>CSV 특성:</b></p>
 * <ul>
 *   <li>CSV는 기본적으로 List 형태의 데이터를 처리합니다</li>
 *   <li>단일 객체는 1행짜리 CSV로 변환됩니다</li>
 *   <li>기본값은 헤더 포함, comma(,) 구분자입니다</li>
 *   <li>헤더는 클래스의 필드명을 사용합니다</li>
 *   <li>한글 헤더는 Map 또는 @JsonProperty로 지원합니다</li>
 * </ul>
 * 
 * @author TX24
 * @see JacksonAbstract
 * @see JacksonUtils
 * @see JacksonXmlUtils
 * @see JacksonYamlUtils
 */
public class JacksonCsvUtils {
    private static final Logger logger = LoggerFactory.getLogger(JacksonCsvUtils.class);
    
    private final CsvMapper mapper;
    private final boolean withHeader;
    private final char separator;

    /**
     * 기본 생성자 - 헤더 포함, comma 구분자로 초기화
     */
    public JacksonCsvUtils() {
    	this.mapper = createDefaultMapper();
        this.withHeader = true;
        this.separator = ',';
    }

    /**
     * 내부용 생성자 - 커스텀 CsvMapper 및 옵션 사용
     * 
     * @param mapper 사용할 CsvMapper
     * @param withHeader 헤더 포함 여부
     * @param separator 구분자
     */
    private JacksonCsvUtils(CsvMapper mapper, boolean withHeader, char separator) {
        this.mapper = mapper;
        this.withHeader = withHeader;
        this.separator = separator;
    }

    /**
     * 기본 CsvMapper 생성
     * 
     * <p>기본 설정:</p>
     * <ul>
     *   <li>null 값 제외 (NON_NULL)</li>
     *   <li>Pretty 포맷 비활성화 (CSV는 compact만 지원)</li>
     * </ul>
     * 
     * @return 기본 설정이 적용된 CsvMapper
     */
    private static CsvMapper createDefaultMapper() {
        CsvMapper csvMapper = new CsvMapper();
        csvMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        csvMapper.disable(SerializationFeature.INDENT_OUTPUT);
        csvMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return csvMapper;
    }
    
    
    public ObjectMapper getMapper() {
        return mapper.copy();
    }
    
    
    /**
     * Null 값 포함 여부 설정
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * JacksonCsvUtils csvNullable = csv.nullable(true);
     * </pre>
     * 
     * @param nullable true면 null 포함, false면 제외
     * @return 설정이 적용된 새 인스턴스
     */
    public JacksonCsvUtils nullable(boolean nullable) {
        CsvMapper tmp = mapper.copy();
        JsonInclude.Include inclusion = nullable
                ? JsonInclude.Include.USE_DEFAULTS
                : JsonInclude.Include.NON_NULL;
        tmp.setDefaultPropertyInclusion(JsonInclude.Value.construct(inclusion, inclusion));
        return new JacksonCsvUtils(tmp, this.withHeader, this.separator);
    }
    
    
    /**
     * 날짜 포맷 설정
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
     * JacksonCsvUtils csvWithDateFormat = csv.dateformat(sdf);
     * </pre>
     * 
     * @param df DateFormat
     * @return 설정이 적용된 새 인스턴스
     */
    public JacksonCsvUtils dateformat(DateFormat df) {
        CsvMapper tmp = mapper.copy();
        tmp.setDateFormat(df);
        return new JacksonCsvUtils(tmp, this.withHeader, this.separator);
    }
    
    
    
    
    /**
     * 헤더 포함 인스턴스 반환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * JacksonCsvUtils csvWithHeader = csv.withHeader();
     * String csv = csvWithHeader.toCsv(users, User.class);
     * // name,age
     * // John,30
     * // Jane,25
     * </pre>
     * 
     * @return 헤더 포함 JacksonCsvUtils 인스턴스
     */
    public JacksonCsvUtils withHeader() {
        if (this.withHeader) {
            return this;
        }
        return new JacksonCsvUtils(mapper.copy(), true, this.separator);
    }
    
    
    /**
     * 헤더 제외 인스턴스 반환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * JacksonCsvUtils csvWithoutHeader = csv.withoutHeader();
     * String csv = csvWithoutHeader.toCsv(users, User.class);
     * // John,30
     * // Jane,25
     * </pre>
     * 
     * @return 헤더 제외 JacksonCsvUtils 인스턴스
     */
    public JacksonCsvUtils withoutHeader() {
        if (!this.withHeader) {
            return this;
        }
        return new JacksonCsvUtils(mapper.copy(), false, this.separator);
    }
    
    
    /**
     * 구분자 설정 인스턴스 반환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * // Tab 구분자 (TSV)
     * JacksonCsvUtils tsv = csv.withSeparator('\t');
     * String tsvString = tsv.toCsv(users, User.class);
     * 
     * // Semicolon 구분자
     * JacksonCsvUtils semiCsv = csv.withSeparator(';');
     * String semiCsvString = semiCsv.toCsv(users, User.class);
     * 
     * // Pipe 구분자
     * JacksonCsvUtils pipeCsv = csv.withSeparator('|');
     * String pipeCsvString = pipeCsv.toCsv(users, User.class);
     * </pre>
     * 
     * @param separator 구분자 문자
     * @return 구분자가 설정된 JacksonCsvUtils 인스턴스
     */
    public JacksonCsvUtils withSeparator(char separator) {
        if (this.separator == separator) {
            return this;
        }
        return new JacksonCsvUtils(mapper.copy(), this.withHeader, separator);
    }
    
    
    
    /**
     * 클래스 타입으로부터 CsvSchema 생성
     * 
     * @param type 대상 클래스
     * @return CsvSchema
     */
    private CsvSchema createSchema(Class<?> type) {
        CsvSchema.Builder builder = CsvSchema.builder();
        CsvSchema baseSchema = mapper.schemaFor(type);
        
        // 기본 스키마 복사
        baseSchema.iterator().forEachRemaining(builder::addColumn);
        
        // 옵션 적용
        if (withHeader) {
            builder.setUseHeader(true);
        } else {
            builder.setUseHeader(false);
        }
        
        builder.setColumnSeparator(separator);
        
        return builder.build();
    }
    
    
    /**
     * 커스텀 헤더 매핑을 사용하여 CsvSchema 생성
     * 
     * @param type 대상 클래스
     * @param fieldToHeaderMap 필드명 → 헤더명 매핑 (예: "name" → "이름")
     * @return CsvSchema
     */
    private CsvSchema createSchemaWithCustomHeaders(Class<?> type, Map<String, String> fieldToHeaderMap) {
        CsvSchema.Builder builder = CsvSchema.builder();
        CsvSchema baseSchema = mapper.schemaFor(type);
        
        // 기본 스키마를 순회하면서 커스텀 헤더로 변경
        baseSchema.iterator().forEachRemaining(column -> {
            String fieldName = column.getName();
            String customHeader = fieldToHeaderMap.getOrDefault(fieldName, fieldName);
            builder.addColumn(customHeader, column.getType());
        });
        
        // 옵션 적용
        if (withHeader) {
            builder.setUseHeader(true);
        } else {
            builder.setUseHeader(false);
        }
        
        builder.setColumnSeparator(separator);
        
        return builder.build();
    }
    
    /**
     * CSV 문자열 유효성 검사
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String csv = "name,age\nJohn,30";
     * boolean valid = csv.isValid(csv);  // true
     * 
     * String invalid = "invalid csv content";
     * boolean result = csv.isValid(invalid);  // false (구문 오류)
     * </pre>
     * 
     * @param csv CSV 문자열
     * @return 유효하면 true, 아니면 false
     */
    public boolean isValid(String csv) {
        if (csv == null || csv.isEmpty()) return false;
        try {
            // 기본 파싱 시도
            mapper.readerFor(Object.class)
                  .with(CsvSchema.emptySchema().withHeader())
                  .readValue(csv);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    
    /**
     * CSV 문자열을 특정 타입으로 변환 가능한지 검사
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String csv = "name,age\nJohn,30";
     * boolean valid = csv.isValid(csv, User.class);  // true
     * </pre>
     * 
     * @param csv CSV 문자열
     * @param valueType 대상 타입
     * @return 변환 가능하면 true, 아니면 false
     */
    public boolean isValid(String csv, Class<?> valueType) {
        if (csv == null || csv.isEmpty()) return false;
        try {
            CsvSchema schema = createSchema(valueType);
            mapper.readerFor(valueType)
                  .with(schema)
                  .readValues(csv)
                  .readAll();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    /**
    * List 객체를 CSV 문자열로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * List<User> users = List.of(
    *     new User("John", 30),
    *     new User("Jane", 25)
    * );
    * String csv = csv.toCsv(users, User.class);
    * // name,age
    * // John,30
    * // Jane,25
    * </pre>
    * 
    * @param <T> 객체 타입
    * @param list 변환할 List
    * @param type 객체의 클래스
    * @return CSV 문자열
    */
   public <T> String toCsv(List<T> list, Class<T> type) {
       if (list == null || list.isEmpty()) return "";
       try {
           CsvSchema schema = createSchema(type);
           return mapper.writer(schema).writeValueAsString(list);
       } catch (Exception e) {
           logger.warn("CSV 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return "";
       }
   }
   
   
   
   /**
    * 단일 객체를 CSV 문자열로 변환 (1행)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * User user = new User("John", 30);
    * String csv = csv.toCsv(user, User.class);
    * // name,age
    * // John,30
    * </pre>
    * 
    * @param <T> 객체 타입
    * @param value 변환할 객체
    * @param type 객체의 클래스
    * @return CSV 문자열
    */
   public <T> String toCsv(T value, Class<T> type) {
       if (value == null) return "";
       return toCsv(List.of(value), type);
   }
   
   

   
   
   // ==================== Serialization (한글 헤더) ====================
   
   /**
    * List 객체를 커스텀 헤더(한글 등)로 CSV 문자열로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * List<User> users = List.of(
    *     new User("홍길동", 30),
    *     new User("김철수", 25)
    * );
    * 
    * Map<String, String> headerMap = Map.of(
    *     "name", "이름",
    *     "age", "나이",
    *     "address", "주소"
    * );
    * 
    * String csv = csv.toCsvWithCustomHeaders(users, User.class, headerMap);
    * // 이름,나이,주소
    * // 홍길동,30,서울
    * // 김철수,25,부산
    * </pre>
    * 
    * @param <T> 객체 타입
    * @param list 변환할 List
    * @param type 객체의 클래스
    * @param fieldToHeaderMap 필드명 → 헤더명 매핑 (예: "name" → "이름")
    * @return CSV 문자열
    */
   public <T> String toCsvWithCustomHeaders(List<T> list, Class<T> type, Map<String, String> fieldToHeaderMap) {
       if (list == null || list.isEmpty()) return "";
       if (fieldToHeaderMap == null || fieldToHeaderMap.isEmpty()) {
           return toCsv(list, type);  // 매핑이 없으면 기본 메서드 사용
       }
       
       try {
           CsvSchema schema = createSchemaWithCustomHeaders(type, fieldToHeaderMap);
           return mapper.writer(schema).writeValueAsString(list);
       } catch (Exception e) {
           logger.warn("CSV 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return "";
       }
   }
   
   /**
    * 단일 객체를 커스텀 헤더(한글 등)로 CSV 문자열로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * User user = new User("홍길동", 30, "서울");
    * 
    * Map<String, String> headerMap = Map.of(
    *     "name", "이름",
    *     "age", "나이",
    *     "address", "주소"
    * );
    * 
    * String csv = csv.toCsvWithCustomHeaders(user, User.class, headerMap);
    * // 이름,나이,주소
    * // 홍길동,30,서울
    * </pre>
    * 
    * @param <T> 객체 타입
    * @param value 변환할 객체
    * @param type 객체의 클래스
    * @param fieldToHeaderMap 필드명 → 헤더명 매핑
    * @return CSV 문자열
    */
   public <T> String toCsvWithCustomHeaders(T value, Class<T> type, Map<String, String> fieldToHeaderMap) {
       if (value == null) return "";
       return toCsvWithCustomHeaders(List.of(value), type, fieldToHeaderMap);
   }

   
   // ==================== String CSV → List (기본) ====================
   
   /**
    * CSV 문자열을 List로 변환 (Class 타입)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * String csv = "name,age\nJohn,30\nJane,25";
    * List<User> users = csv.fromCsv(csv, User.class);
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param csv CSV 문자열
    * @param type 대상 클래스
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsv(String csv, Class<T> type) {
       if (csv == null || csv.isEmpty()) return Collections.emptyList();
       try {
           CsvSchema schema = createSchema(type);
           return (List<T>) mapper.readerFor(type)
                                  .with(schema)
                                  .readValues(csv)
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   /**
    * CSV 문자열을 List로 변환 (TypeReference)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * String csv = "name,age\nJohn,30\nJane,25";
    * List<User> users = csv.fromCsv(csv, 
    *     new TypeReference<List<User>>(){});
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param csv CSV 문자열
    * @param typeRef TypeReference
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsv(String csv, TypeReference<List<T>> typeRef) {
       if (csv == null || csv.isEmpty()) return Collections.emptyList();
       try {
           // TypeReference에서 실제 타입 추출은 복잡하므로
           // 기본 스키마를 사용한 파싱
           CsvSchema schema = CsvSchema.emptySchema();
           if (withHeader) {
               schema = schema.withHeader();
           }
           schema = schema.withColumnSeparator(separator);
           
           return (List<T>) mapper.readerFor(typeRef)
                                  .with(schema)
                                  .readValues(csv)
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   
   // ==================== String CSV → List (한글 헤더) ====================
   
   /**
    * 커스텀 헤더(한글 등)를 가진 CSV 문자열을 List로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * String csv = "이름,나이,주소\n홍길동,30,서울\n김철수,25,부산";
    * 
    * Map<String, String> headerMap = Map.of(
    *     "name", "이름",
    *     "age", "나이",
    *     "address", "주소"
    * );
    * 
    * List<User> users = csv.fromCsvWithCustomHeaders(csv, User.class, headerMap);
    * // User 객체의 name, age, address 필드에 매핑됨
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param csv CSV 문자열
    * @param type 대상 클래스
    * @param fieldToHeaderMap 필드명 → 헤더명 매핑 (예: "name" → "이름")
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsvWithCustomHeaders(String csv, Class<T> type, Map<String, String> fieldToHeaderMap) {
       if (csv == null || csv.isEmpty()) return Collections.emptyList();
       if (fieldToHeaderMap == null || fieldToHeaderMap.isEmpty()) {
           return fromCsv(csv, type);  // 매핑이 없으면 기본 메서드 사용
       }
       
       try {
           CsvSchema schema = createSchemaWithCustomHeaders(type, fieldToHeaderMap);
           return (List<T>) mapper.readerFor(type)
                                  .with(schema)
                                  .readValues(csv)
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   
   // ==================== byte[] CSV → List (기본) ====================
   
   /**
    * byte[] CSV를 List로 변환 (Class 타입)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * byte[] csvBytes = "name,age\nJohn,30".getBytes();
    * List<User> users = csv.fromCsv(csvBytes, User.class);
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param csv CSV byte 배열
    * @param type 대상 클래스
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsv(byte[] csv, Class<T> type) {
       if (csv == null || csv.length == 0) return Collections.emptyList();
       try {
           CsvSchema schema = createSchema(type);
           return (List<T>) mapper.readerFor(type)
                                  .with(schema)
                                  .readValues(csv)
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   /**
    * byte[] CSV를 List로 변환 (TypeReference)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * byte[] csvBytes = "name,age\nJohn,30".getBytes();
    * List<User> users = csv.fromCsv(csvBytes, 
    *     new TypeReference<List<User>>(){});
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param csv CSV byte 배열
    * @param typeRef TypeReference
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsv(byte[] csv, TypeReference<List<T>> typeRef) {
       if (csv == null || csv.length == 0) return Collections.emptyList();
       try {
           CsvSchema schema = CsvSchema.emptySchema();
           if (withHeader) {
               schema = schema.withHeader();
           }
           schema = schema.withColumnSeparator(separator);
           
           return (List<T>) mapper.readerFor(typeRef)
                                  .with(schema)
                                  .readValues(csv)
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   
   public List<LinkedMap<String, Object>> fromCsvList(String csv) {
	    if (csv == null || csv.isEmpty()) return Collections.emptyList();
	    try {
	        CsvSchema schema = CsvSchema.emptySchema();
	        if (withHeader) {
	            schema = schema.withHeader();
	        }
	        schema = schema.withColumnSeparator(separator);
	        
	        com.fasterxml.jackson.databind.MappingIterator<Map<String, String>> iterator = 
	            mapper.readerFor(Map.class)
	                  .with(schema)
	                  .readValues(csv);
	        
	        List<LinkedMap<String, Object>> result = new ArrayList<>();
	        while (iterator.hasNext()) {
	            Map<String, String> raw = iterator.next();
	            if(CommonUtils.isNotEmpty(raw)) {
		            LinkedMap<String, Object> map = new LinkedMap<>();
		            raw.forEach(map::put);
		            result.add(map);
	            }
	        }
	        return result;
	    } catch (Exception e) {
	        logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
	        return Collections.emptyList();
	    }
	}
   
   
   public List<LinkedMap<String, Object>> fromCsvList(byte[] csv) {
	    if (csv == null || csv.length == 0) return Collections.emptyList();
	    return fromCsvList(new String(csv, java.nio.charset.StandardCharsets.UTF_8));
	}
   
   
   // ==================== byte[] CSV → List (한글 헤더) ====================
   
   /**
    * 커스텀 헤더를 가진 byte[] CSV를 List로 변환
    * 
    * @param <T> 반환 타입
    * @param csv CSV byte 배열
    * @param type 대상 클래스
    * @param fieldToHeaderMap 필드명 → 헤더명 매핑
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsvWithCustomHeaders(byte[] csv, Class<T> type, Map<String, String> fieldToHeaderMap) {
       if (csv == null || csv.length == 0) return Collections.emptyList();
       if (fieldToHeaderMap == null || fieldToHeaderMap.isEmpty()) {
           return fromCsv(csv, type);
       }
       
       try {
           CsvSchema schema = createSchemaWithCustomHeaders(type, fieldToHeaderMap);
           return (List<T>) mapper.readerFor(type)
                                  .with(schema)
                                  .readValues(csv)
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   
   // ==================== Path (파일) CSV → List (기본) ====================
   
   /**
    * 파일에서 CSV를 읽어 List로 변환 (Class 타입)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * Path csvFile = Paths.get("users.csv");
    * List<User> users = csv.fromCsv(csvFile, User.class);
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param path 파일 경로
    * @param type 대상 클래스
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsv(Path path, Class<T> type) {
       if (path == null) return Collections.emptyList();
       try {
           CsvSchema schema = createSchema(type);
           return (List<T>) mapper.readerFor(type)
                                  .with(schema)
                                  .readValues(path.toFile())
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   /**
    * 파일에서 CSV를 읽어 List로 변환 (TypeReference)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * Path csvFile = Paths.get("users.csv");
    * List<User> users = csv.fromCsv(csvFile, 
    *     new TypeReference<List<User>>(){});
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param path 파일 경로
    * @param typeRef TypeReference
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsv(Path path, TypeReference<List<T>> typeRef) {
       if (path == null) return Collections.emptyList();
       try {
           CsvSchema schema = CsvSchema.emptySchema();
           if (withHeader) {
               schema = schema.withHeader();
           }
           schema = schema.withColumnSeparator(separator);
           
           return (List<T>) mapper.readerFor(typeRef)
                                  .with(schema)
                                  .readValues(path.toFile())
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   
   // ==================== Path (파일) CSV → List (한글 헤더) ====================
   
   /**
    * 커스텀 헤더를 가진 CSV 파일을 읽어 List로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * Path csvFile = Paths.get("users.csv");
    * // CSV 파일 내용: 이름,나이,주소
    * //              홍길동,30,서울
    * 
    * Map<String, String> headerMap = Map.of(
    *     "name", "이름",
    *     "age", "나이",
    *     "address", "주소"
    * );
    * 
    * List<User> users = csv.fromCsvWithCustomHeaders(csvFile, User.class, headerMap);
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param path 파일 경로
    * @param type 대상 클래스
    * @param fieldToHeaderMap 필드명 → 헤더명 매핑
    * @return 변환된 List
    */
   @SuppressWarnings("unchecked")
   public <T> List<T> fromCsvWithCustomHeaders(Path path, Class<T> type, Map<String, String> fieldToHeaderMap) {
       if (path == null) return Collections.emptyList();
       if (fieldToHeaderMap == null || fieldToHeaderMap.isEmpty()) {
           return fromCsv(path, type);
       }
       
       try {
           CsvSchema schema = createSchemaWithCustomHeaders(type, fieldToHeaderMap);
           return (List<T>) mapper.readerFor(type)
                                  .with(schema)
                                  .readValues(path.toFile())
                                  .readAll();
       } catch (Exception e) {
           logger.warn("CSV 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
           return Collections.emptyList();
       }
   }
   
   
   // ==================== Object Copy (from JacksonAbstract) ====================
   
   /**
    * 객체 복사 (Class 타입)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * User original = new User("John", 30);
    * User copied = csv.copyObject(original, User.class);
    * </pre>
    * 
    * @param <V> 객체 타입
    * @param source 원본 객체
    * @param type 객체 클래스
    * @return 복사된 객체
    */
   public <V> V copyObject(V source, Class<V> type) {
       if (source == null) return null;
       try {
           byte[] bytes = mapper.writeValueAsBytes(source);
           return mapper.readValue(bytes, type);
       } catch (Exception e) {
           throw new RuntimeException("Object copy failed", e);
       }
   }

   /**
    * 객체 복사 (TypeReference)
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * List<User> original = List.of(new User("John", 30));
    * List<User> copied = csv.copyObject(original, 
    *     new TypeReference<List<User>>(){});
    * </pre>
    * 
    * @param <V> 객체 타입
    * @param source 원본 객체
    * @param typeRef TypeReference
    * @return 복사된 객체
    */
   public <V> V copyObject(V source, TypeReference<V> typeRef) {
       if (source == null) return null;
       try {
           byte[] bytes = mapper.writeValueAsBytes(source);
           return mapper.readValue(bytes, typeRef);
       } catch (Exception e) {
           throw new RuntimeException("Object copy failed", e);
       }
   }
   
   
   // ==================== Map → Object (from JacksonAbstract) ====================
   
   /**
    * Map을 객체로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * Map<String, Object> map = Map.of(
    *     "name", "John",
    *     "age", 30
    * );
    * User user = csv.mapToObject(map, User.class);
    * </pre>
    * 
    * @param <V> 반환 타입
    * @param map 변환할 Map
    * @param type 대상 클래스
    * @return 변환된 객체 또는 null
    */
   public <V> V mapToObject(Map<?, ?> map, Class<V> type) {
       try {
           CsvMapper tmpMapper = mapper.copy();
           tmpMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
           tmpMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
           tmpMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
           return tmpMapper.convertValue(map, type);
       } catch (Exception e) {
           return null;
       }
   }
   
   /**
    * Map을 TypeRegistry 타입으로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * Map<String, Object> map = Map.of("name", "John", "age", 30);
    * Map<String, Object> result = csv.convertValue(map, TypeRegistry.MAP_OBJECT);
    * </pre>
    * 
    * @param <T> 반환 타입
    * @param map 변환할 Map
    * @param typeRegistry TypeRegistry
    * @return 변환된 객체 또는 null
    */
   public <T> T convertValue(Map<?, ?> map, TypeRegistry typeRegistry) {
       try {
           return mapper.convertValue(map, typeRegistry.get());
       } catch (Exception e) {
           return null;
       }
   }
   
   
   /**
    * 문자열을 JsonNode로 변환
    * 
    * <p><b>사용 예:</b></p>
    * <pre>
    * String csv = "name,age\nJohn,30";
    * JsonNode node = csv.toJsonNode(csv);
    * </pre>
    * 
    * @param value CSV 문자열
    * @return JsonNode 또는 null
    */
   public JsonNode toJsonNode(String value) {
       try {
           return mapper.readTree(value);
       } catch (Exception e) {
           return null;
       }
   }
   
   
   
   /**
    * 객체를 문자열로 직렬화
    * 
    * <p>CSV가 아닌 일반 JSON 형식으로 변환됩니다.</p>
    * 
    * @param value 변환할 객체
    * @return 직렬화된 문자열
    */
   public String serialize(Object value) {
       if (value == null) return "";
       try {
           return mapper.writeValueAsString(value);
       } catch (Exception e) {
           throw new RuntimeException("Serialization failed", e);
       }
   }
   
   /**
    * 객체를 byte[]로 직렬화
    * 
    * @param value 변환할 객체
    * @return 직렬화된 byte 배열
    */
   public byte[] serializeBytes(Object value) {
       if (value == null) return null;
       try {
           return mapper.writeValueAsBytes(value);
       } catch (Exception e) {
           throw new RuntimeException("Serialization failed", e);
       }
   }

   /**
    * 문자열을 객체로 역직렬화 (Class 타입)
    * 
    * @param <V> 반환 타입
    * @param str 문자열
    * @param type 대상 클래스
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(String str, Class<V> type) {
       try {
           return mapper.readValue(str, type);
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }

   /**
    * 문자열을 객체로 역직렬화 (TypeReference)
    * 
    * @param <V> 반환 타입
    * @param str 문자열
    * @param typeRef TypeReference
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(String str, TypeReference<V> typeRef) {
       try {
           return mapper.readValue(str, typeRef);
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }
   
   /**
    * 문자열을 객체로 역직렬화 (TypeRegistry)
    * 
    * @param <V> 반환 타입
    * @param str 문자열
    * @param typeRegistry TypeRegistry
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(String str, TypeRegistry typeRegistry) {
       try {
           return mapper.readValue(str, typeRegistry.get());
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }
   
   /**
    * byte[]를 객체로 역직렬화 (Class 타입)
    * 
    * @param <V> 반환 타입
    * @param str byte 배열
    * @param type 대상 클래스
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(byte[] str, Class<V> type) {
       try {
           return mapper.readValue(str, type);
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }

   /**
    * byte[]를 객체로 역직렬화 (TypeReference)
    * 
    * @param <V> 반환 타입
    * @param str byte 배열
    * @param typeRef TypeReference
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(byte[] str, TypeReference<V> typeRef) {
       try {
           return mapper.readValue(str, typeRef);
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }
   
   /**
    * byte[]를 객체로 역직렬화 (TypeRegistry)
    * 
    * @param <V> 반환 타입
    * @param str byte 배열
    * @param typeRegistry TypeRegistry
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(byte[] str, TypeRegistry typeRegistry) {
       try {
           return mapper.readValue(str, typeRegistry.get());
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }
   
   /**
    * 파일을 객체로 역직렬화 (Class 타입)
    * 
    * @param <V> 반환 타입
    * @param path 파일 경로
    * @param type 대상 클래스
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(Path path, Class<V> type) {
       try {
           return mapper.readValue(Files.readAllBytes(path), type);
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }

   /**
    * 파일을 객체로 역직렬화 (TypeReference)
    * 
    * @param <V> 반환 타입
    * @param path 파일 경로
    * @param typeRef TypeReference
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(Path path, TypeReference<V> typeRef) {
       try {
           return mapper.readValue(Files.readAllBytes(path), typeRef);
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }
   
   /**
    * 파일을 객체로 역직렬화 (TypeRegistry)
    * 
    * @param <V> 반환 타입
    * @param path 파일 경로
    * @param typeRegistry TypeRegistry
    * @return 역직렬화된 객체
    */
   public <V> V deserialize(Path path, TypeRegistry typeRegistry) {
       try {
           return mapper.readValue(Files.readAllBytes(path), typeRegistry.get());
       } catch (Exception e) {
           throw new RuntimeException("Deserialization failed", e);
       }
   }
}