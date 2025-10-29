package kr.tx24.lib.mapper;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.TypeRegistry;

/**
 * YAML 전용 Jackson Utility
 * 
 * <p><b>필수 의존성:</b></p>
 * <pre>
 * {@code
 * <dependency>
 *     <groupId>com.fasterxml.jackson.dataformat</groupId>
 *     <artifactId>jackson-dataformat-yaml</artifactId>
 *     <version>2.15.0</version>
 * </dependency>
 * }
 * </pre>
 * 
 * <p><b>사용 예:</b></p>
 * <pre>
 * JacksonYamlUtils yamlUtils = new JacksonYamlUtils();
 * 
 * // Serialize
 * String yaml = yamlUtils.toYaml(object);
 * 
 * // Deserialize
 * User user = yamlUtils.fromYaml(yaml, User.class);
 * List<String> list = yamlUtils.fromYaml(yaml, new TypeReference<List<String>>(){});
 * Map<String, Object> map = yamlUtils.fromYaml(yaml, TypeRegistry.MAP_OBJECT);
 * </pre>
 */
public class JacksonYamlUtils extends JacksonAbstract<YAMLMapper> {
    private static final Logger logger = LoggerFactory.getLogger(JacksonYamlUtils.class);

    public JacksonYamlUtils() {
        super(createDefaultMapper());
    }

    private JacksonYamlUtils(YAMLMapper mapper) {
        super(mapper);
    }

    /**
     * 기본 YAMLMapper 생성
     * 
     * @return 기본 설정이 적용된 YAMLMapper
     */
    private static YAMLMapper createDefaultMapper() {
        YAMLMapper yamlMapper = new YAMLMapper();
        yamlMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        yamlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return yamlMapper;
    }

    @Override
    public JacksonYamlUtils pretty() {
        if (mapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
            return this;
        }
        YAMLMapper newMapper = mapper.copy();
        newMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return new JacksonYamlUtils(newMapper);
    }

    @Override
    public JacksonYamlUtils compact() {
        if (!mapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
            return this;
        }
        YAMLMapper newMapper = mapper.copy();
        newMapper.disable(SerializationFeature.INDENT_OUTPUT);
        return new JacksonYamlUtils(newMapper);
    }

    @Override
    protected JacksonYamlUtils createNewInstance(YAMLMapper mapper) {
        return new JacksonYamlUtils(mapper);
    }
    
    
    // ==================== Validation ====================
    
    /**
     * YAML 문자열 유효성 검사
     * 
     * @param yaml YAML 문자열
     * @return 유효하면 true, 아니면 false
     */
    public boolean isValid(String yaml) {
        if (yaml == null || yaml.isEmpty()) return false;
        try {
            mapper.readTree(yaml);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * YAML 문자열을 특정 타입으로 변환 가능한지 검사
     * 
     * @param yaml YAML 문자열
     * @param valueType 대상 타입
     * @return 변환 가능하면 true, 아니면 false
     */
    public boolean isValid(String yaml, Class<?> valueType) {
        if (yaml == null || yaml.isEmpty()) return false;
        try {
            mapper.readValue(yaml, valueType);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * YAML 문자열을 TypeReference로 변환 가능한지 검사
     * 
     * @param yaml YAML 문자열
     * @param typeRef TypeReference
     * @return 변환 가능하면 true, 아니면 false
     */
    public boolean isValid(String yaml, TypeReference<?> typeRef) {
        if (yaml == null || yaml.isEmpty()) return false;
        try {
            mapper.readValue(yaml, typeRef);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    
    // ==================== Serialization ====================
    
    /**
     * 객체를 YAML 문자열로 변환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * User user = new User("John", 30);
     * String yaml = yamlUtils.toYaml(user);
     * // name: John
     * // age: 30
     * </pre>
     * 
     * @param value 변환할 객체
     * @return YAML 문자열
     */
    public String toYaml(Object value) {
        if (value == null) return "";
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
        	logger.warn("YAML 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return "";
        }
    }
    
    /**
     * 객체를 YAML byte[]로 변환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * User user = new User("John", 30);
     * byte[] yamlBytes = yamlUtils.toYamlBytes(user);
     * </pre>
     * 
     * @param value 변환할 객체
     * @return YAML byte 배열
     */
    public byte[] toYamlBytes(Object value) {
        if (value == null) return null;
        try {
            return mapper.writeValueAsBytes(value);
        } catch (Exception e) {
        	logger.warn("YAML 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }

    // ==================== String YAML → Object ====================
    
    /**
     * YAML 문자열을 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String yaml = "name: John\nage: 30";
     * User user = yamlUtils.fromYaml(yaml, User.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param yaml YAML 문자열
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromYaml(String yaml, Class<V> type) {
        if (yaml == null || yaml.isEmpty()) return null;
        try {
            return mapper.readValue(yaml, type);
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * YAML 문자열을 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String yaml = "- Apple\n- Banana\n- Cherry";
     * List<String> fruits = yamlUtils.fromYaml(yaml, 
     *     new TypeReference<List<String>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param yaml YAML 문자열
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromYaml(String yaml, TypeReference<V> typeRef) {
        if (yaml == null || yaml.isEmpty()) return null;
        try {
            return mapper.readValue(yaml, typeRef);
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * YAML 문자열을 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String yaml = "name: Product A\nprice: 10000";
     * Map<String, Object> product = yamlUtils.fromYaml(yaml, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param yaml YAML 문자열
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromYaml(String yaml, TypeRegistry typeRegistry) {
        if (yaml == null || yaml.isEmpty()) return null;
        try {
            return mapper.readValue(yaml, typeRegistry.get());
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    // ==================== byte[] YAML → Object ====================
    
    /**
     * byte[] YAML을 객체로 변환 (Class 타입)
     * 
     * @param <V> 반환 타입
     * @param yaml YAML byte 배열
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromYaml(byte[] yaml, Class<V> type) {
        if (yaml == null || yaml.length == 0) return null;
        try {
            return mapper.readValue(yaml, type);
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * byte[] YAML을 객체로 변환 (TypeReference)
     * 
     * @param <V> 반환 타입
     * @param yaml YAML byte 배열
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromYaml(byte[] yaml, TypeReference<V> typeRef) {
        if (yaml == null || yaml.length == 0) return null;
        try {
            return mapper.readValue(yaml, typeRef);
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * byte[] YAML을 객체로 변환 (TypeRegistry)
     * 
     * @param <V> 반환 타입
     * @param yaml YAML byte 배열
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromYaml(byte[] yaml, TypeRegistry typeRegistry) {
        if (yaml == null || yaml.length == 0) return null;
        try {
            return mapper.readValue(yaml, typeRegistry.get());
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    // ==================== Path (파일) YAML → Object ====================
    
    /**
     * 파일에서 YAML을 읽어 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path yamlFile = Paths.get("config.yaml");
     * Config config = yamlUtils.fromYaml(yamlFile, Config.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromYaml(Path path, Class<V> type) {
        if (path == null) return null;
        try {
            return mapper.readValue(path.toFile(), type);
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * 파일에서 YAML을 읽어 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path yamlFile = Paths.get("users.yaml");
     * List<User> users = yamlUtils.fromYaml(yamlFile, 
     *     new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromYaml(Path path, TypeReference<V> typeRef) {
        if (path == null) return null;
        try {
            return mapper.readValue(path.toFile(), typeRef);
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * 파일에서 YAML을 읽어 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path yamlFile = Paths.get("data.yaml");
     * Map<String, Object> data = yamlUtils.fromYaml(yamlFile, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromYaml(Path path, TypeRegistry typeRegistry) {
        if (path == null) return null;
        try {
            return mapper.readValue(path.toFile(), typeRegistry.get());
        } catch (Exception e) {
            logger.warn("YAML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
}