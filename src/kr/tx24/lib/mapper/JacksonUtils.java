package kr.tx24.lib.mapper;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.TypeRegistry;

/**
 * JSON 전용 Jackson Utility
 * 
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>JSON ↔ Object 변환</li>
 *   <li>TypeReference, TypeRegistry 지원</li>
 *   <li>Pretty/Compact 포맷 지원</li>
 *   <li>파일 입출력 지원 (Path)</li>
 *   <li>JSON 유효성 검사</li>
 *   <li>객체 복사 (JacksonAbstract 상속)</li>
 * </ul>
 * 
 * <p><b>필수 의존성:</b></p>
 * <pre>
 * {@code
 * <dependency>
 *     <groupId>com.fasterxml.jackson.core</groupId>
 *     <artifactId>jackson-databind</artifactId>
 *     <version>2.15.0</version>
 * </dependency>
 * }
 * </pre>
 * 
 * <p><b>사용 예:</b></p>
 * <pre>
 * JacksonUtils json = new JacksonUtils();
 * 
 * // Serialize
 * String jsonString = json.toJson(object);
 * 
 * // Deserialize - 세 가지 방법
 * User user = json.fromJson(jsonString, User.class);
 * List<String> list = json.fromJson(jsonString, new TypeReference<List<String>>(){});
 * Map<String, Object> map = json.fromJson(jsonString, TypeRegistry.MAP_OBJECT);
 * 
 * // 파일 처리
 * Path jsonFile = Paths.get("data.json");
 * Map<String, Object> data = json.fromJson(jsonFile, TypeRegistry.MAP_OBJECT);
 * 
 * // Pretty/Compact
 * String pretty = json.pretty().toJson(object);
 * String compact = json.compact().toJson(object);
 * </pre>
 * 
 * @author TX24
 * @see JacksonAbstract
 * @see JacksonXmlUtils
 * @see JacksonYamlUtils
 */
public class JacksonUtils extends JacksonAbstract<ObjectMapper> {
	
	private static final Logger logger = LoggerFactory.getLogger(JacksonUtils.class);
	
    /**
     * 기본 생성자 - Pretty 포맷으로 초기화
     */
    public JacksonUtils() {
        super(createDefaultMapper());
    }

    /**
     * 내부용 생성자 - 커스텀 ObjectMapper 사용
     * 
     * @param mapper 사용할 ObjectMapper
     */
    private JacksonUtils(ObjectMapper mapper) {
        super(mapper);
    }

    /**
     * Pretty 포맷 인스턴스 반환 (들여쓰기 O)
     * 
     * <p>이미 Pretty 포맷이면 this 반환, 아니면 새 인스턴스 생성</p>
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * JacksonUtils pretty = new JacksonUtils().pretty();
     * String json = pretty.toJson(object);
     * // {
     * //   "name" : "John",
     * //   "age" : 30
     * // }
     * </pre>
     * 
     * @return Pretty 포맷 JacksonUtils 인스턴스
     */
    @Override
    public JacksonUtils pretty() {
        if (mapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
            return this;
        }
        ObjectMapper newMapper = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        return new JacksonUtils(newMapper);
    }

    /**
     * Compact 포맷 인스턴스 반환 (들여쓰기 X)
     * 
     * <p>이미 Compact 포맷이면 this 반환, 아니면 새 인스턴스 생성</p>
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * JacksonUtils compact = new JacksonUtils().compact();
     * String json = compact.toJson(object);
     * // {"name":"John","age":30}
     * </pre>
     * 
     * @return Compact 포맷 JacksonUtils 인스턴스
     */
    @Override
    public JacksonUtils compact() {
        if (!mapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
            return this;
        }
        return new JacksonUtils(mapper.copy().disable(SerializationFeature.INDENT_OUTPUT));
    }

    /**
     * 새로운 JacksonUtils 인스턴스 생성 (Factory 패턴)
     * 
     * @param mapper 사용할 ObjectMapper
     * @return 새 JacksonUtils 인스턴스
     */
    @Override
    protected JacksonUtils createNewInstance(ObjectMapper mapper) {
        return new JacksonUtils(mapper);
    }
    
    /**
     * 기본 ObjectMapper 생성
     * 
     * <p>기본 설정:</p>
     * <ul>
     *   <li>null 값 제외 (NON_NULL)</li>
     *   <li>Pretty 포맷 활성화 (INDENT_OUTPUT)</li>
     * </ul>
     * 
     * @return 기본 설정이 적용된 ObjectMapper
     */
    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        mapper.enable(SerializationFeature.INDENT_OUTPUT); // 기본 pretty format
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
    
    
    
    
    
    // ==================== Serialization ====================
    
    /**
     * 객체를 JSON 문자열로 변환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * User user = new User("John", 30);
     * String json = json.toJson(user);
     * // {
     * //   "name" : "John",
     * //   "age" : 30
     * // }
     * </pre>
     * 
     * @param value 변환할 객체
     * @return JSON 문자열
     */
    public String toJson(Object value) {
        try {
            return super.serialize(value);
        } catch (Exception e) {
            logger.warn("Json 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return "";
        }
    }
    
    
    /**
     * 객체를 JSON byte[]로 변환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * User user = new User("John", 30);
     * byte[] jsonBytes = json.toJsonBytes(user);
     * </pre>
     * 
     * @param value 변환할 객체
     * @return JSON byte 배열
     */
    public byte[] toJsonBytes(Object value) {
        try {
        	return super.serializeBytes(value);
        } catch (Exception e) {
            logger.warn("Json 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    

    // ==================== String JSON → Object ====================
    
    /**
     * JSON 문자열을 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String json = "{\"name\":\"John\",\"age\":30}";
     * User user = json.fromJson(json, User.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param json JSON 문자열
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromJson(String json, Class<V> type) {
        try {
        	return super.deserialize(json,type);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * JSON 문자열을 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String json = "[\"Apple\",\"Banana\",\"Cherry\"]";
     * List<String> fruits = json.fromJson(json, 
     *     new TypeReference<List<String>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param json JSON 문자열
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromJson(String json, TypeReference<V> typeRef) {
        try {
        	return super.deserialize(json,typeRef);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * JSON 문자열을 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String json = "{\"name\":\"Product A\",\"price\":10000}";
     * Map<String, Object> product = json.fromJson(json, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param json JSON 문자열
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromJson(String json, TypeRegistry typeRegistry) {
        try {
        	return super.deserialize(json,typeRegistry);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    // ==================== byte[] JSON → Object ====================
    
    /**
     * byte[] JSON을 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * byte[] jsonBytes = "{\"name\":\"John\"}".getBytes();
     * User user = json.fromJson(jsonBytes, User.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param json JSON byte 배열
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromJson(byte[] json, Class<V> type) {
        try {
        	return super.deserialize(json,type);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * byte[] JSON을 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * byte[] jsonBytes = "[1,2,3]".getBytes();
     * List<Integer> numbers = json.fromJson(jsonBytes, 
     *     new TypeReference<List<Integer>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param json JSON byte 배열
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromJson(byte[] json, TypeReference<V> typeRef) {
        try {
        	return super.deserialize(json,typeRef);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * byte[] JSON을 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * byte[] jsonBytes = "{\"id\":1}".getBytes();
     * Map<String, Object> data = json.fromJson(jsonBytes, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param json JSON byte 배열
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromJson(byte[] json, TypeRegistry typeRegistry) {
        try {
        	return super.deserialize(json,typeRegistry);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    // ==================== Path (파일) JSON → Object ====================
    
    /**
     * 파일에서 JSON을 읽어 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path jsonFile = Paths.get("config.json");
     * Config config = json.fromJson(jsonFile, Config.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromJson(Path path, Class<V> type) {
        try {
        	return super.deserialize(Files.readAllBytes(path),type);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * 파일에서 JSON을 읽어 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path jsonFile = Paths.get("users.json");
     * List<User> users = json.fromJson(jsonFile, 
     *     new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromJson(Path path, TypeReference<V> typeRef) {
        if (path == null) return null;
        try {
        	return super.deserialize(Files.readAllBytes(path),typeRef);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * 파일에서 JSON을 읽어 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path jsonFile = Paths.get("data.json");
     * Map<String, Object> data = json.fromJson(jsonFile, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromJson(Path path, TypeRegistry typeRegistry) {
        if (path == null) return null;
        try {
        	return super.deserialize(Files.readAllBytes(path),typeRegistry);
        } catch (Exception e) {
            logger.warn("Json 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    
    
    public JsonNode toJsonNode(String value) {
        try {
            return mapper.readTree(value);
        } catch (Exception e) {
            // logger는 구현 클래스에서 정의
            return null;
        }
    }
    
}