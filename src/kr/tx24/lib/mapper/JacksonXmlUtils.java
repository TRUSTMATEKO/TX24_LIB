package kr.tx24.lib.mapper;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.TypeRegistry;

/**
 * XML 전용 Jackson Utility
 * 
 * <p><b>주요 기능:</b></p>
 * <ul>
 *   <li>XML ↔ Object 변환</li>
 *   <li>TypeReference, TypeRegistry 지원</li>
 *   <li>Pretty/Compact 포맷 지원</li>
 *   <li>파일 입출력 지원 (Path)</li>
 *   <li>XML 유효성 검사</li>
 *   <li>객체 복사 (JacksonAbstract 상속)</li>
 * </ul>
 * 
 * <p><b>필수 의존성:</b></p>
 * <pre>
 * {@code
 * <dependency>
 *     <groupId>com.fasterxml.jackson.dataformat</groupId>
 *     <artifactId>jackson-dataformat-xml</artifactId>
 *     <version>2.15.0</version>
 * </dependency>
 * }
 * </pre>
 * 
 * <p><b>사용 예:</b></p>
 * <pre>
 * JacksonXmlUtils xml = new JacksonXmlUtils();
 * 
 * // Serialize
 * String xmlString = xml.toXml(object);
 * 
 * // Deserialize - 세 가지 방법
 * User user = xml.fromXml(xmlString, User.class);
 * List<String> list = xml.fromXml(xmlString, new TypeReference<List<String>>(){});
 * Map<String, Object> map = xml.fromXml(xmlString, TypeRegistry.MAP_OBJECT);
 * 
 * // 파일 처리
 * Path xmlFile = Paths.get("data.xml");
 * Map<String, Object> data = xml.fromXml(xmlFile, TypeRegistry.MAP_OBJECT);
 * 
 * // Pretty/Compact
 * String pretty = xml.pretty().toXml(object);
 * String compact = xml.compact().toXml(object);
 * </pre>
 * 
 * @author TX24
 * @see JacksonAbstract
 * @see JacksonUtils
 * @see JacksonYamlUtils
 */
public class JacksonXmlUtils extends JacksonAbstract<XmlMapper> {
    private static final Logger logger = LoggerFactory.getLogger(JacksonXmlUtils.class);

    /**
     * 기본 생성자 - Pretty 포맷으로 초기화
     */
    public JacksonXmlUtils() {
        super(createDefaultMapper());
    }

    /**
     * 내부용 생성자 - 커스텀 XmlMapper 사용
     * 
     * @param mapper 사용할 XmlMapper
     */
    private JacksonXmlUtils(XmlMapper mapper) {
        super(mapper);
    }

    /**
     * 기본 XmlMapper 생성
     * 
     * <p>기본 설정:</p>
     * <ul>
     *   <li>null 값 제외 (NON_NULL)</li>
     *   <li>Pretty 포맷 활성화 (INDENT_OUTPUT)</li>
     * </ul>
     * 
     * @return 기본 설정이 적용된 XmlMapper
     */
    private static XmlMapper createDefaultMapper() {
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return xmlMapper;
    }

    /**
     * Pretty 포맷 인스턴스 반환 (들여쓰기 O)
     * 
     * <p>이미 Pretty 포맷이면 this 반환, 아니면 새 인스턴스 생성</p>
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * JacksonXmlUtils pretty = new JacksonXmlUtils().pretty();
     * String xml = pretty.toXml(object);
     * // {@code
     * // <User>
     * //   <n>John</n>
     * //   <age>30</age>
     * // </User>
     * // }
     * </pre>
     * 
     * @return Pretty 포맷 JacksonXmlUtils 인스턴스
     */
    @Override
    public JacksonXmlUtils pretty() {
        if (mapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
            return this;
        }
        XmlMapper newMapper = mapper.copy();
        newMapper.enable(SerializationFeature.INDENT_OUTPUT);
        return new JacksonXmlUtils(newMapper);
    }

    /**
     * Compact 포맷 인스턴스 반환 (들여쓰기 X)
     * 
     * <p>이미 Compact 포맷이면 this 반환, 아니면 새 인스턴스 생성</p>
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * JacksonXmlUtils compact = new JacksonXmlUtils().compact();
     * String xml = compact.toXml(object);
     * // {@code <User><n>John</n><age>30</age></User>}
     * </pre>
     * 
     * @return Compact 포맷 JacksonXmlUtils 인스턴스
     */
    @Override
    public JacksonXmlUtils compact() {
        if (!mapper.isEnabled(SerializationFeature.INDENT_OUTPUT)) {
            return this;
        }
        XmlMapper newMapper = mapper.copy();
        newMapper.disable(SerializationFeature.INDENT_OUTPUT);
        return new JacksonXmlUtils(newMapper);
    }

    /**
     * 새로운 JacksonXmlUtils 인스턴스 생성 (Factory 패턴)
     * 
     * @param mapper 사용할 XmlMapper
     * @return 새 JacksonXmlUtils 인스턴스
     */
    @Override
    protected JacksonXmlUtils createNewInstance(XmlMapper mapper) {
        return new JacksonXmlUtils(mapper);
    }
    
    
    
    // ==================== Serialization ====================
    
    /**
     * 객체를 XML 문자열로 변환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * User user = new User("John", 30);
     * String xml = xml.toXml(user);
     * // {@code
     * // <User>
     * //   <n>John</n>
     * //   <age>30</age>
     * // </User>
     * // }
     * </pre>
     * 
     * @param value 변환할 객체
     * @return XML 문자열
     */
    public String toXml(Object value) {
        try {
        	return super.serialize(value);
        } catch (Exception e) {
        	logger.warn("XML 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return "";
        }
    }
    
    /**
     * 객체를 XML byte[] 로 변환
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * User user = new User("John", 30);
     * byte[] xmlBytes = xml.toXmlBytes(user);
     * </pre>
     * 
     * @param value 변환할 객체
     * @return XML byte 배열
     */
    public byte[] toXmlBytes(Object value) {
        if (value == null) return null;
        try {
        	return super.serializeBytes(value);
        } catch (Exception e) {
        	logger.warn("XML 직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }

    // ==================== String XML → Object ====================
    
    /**
     * XML 문자열을 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String xml = "{@code <User><n>John</n><age>30</age></User>}";
     * User user = xml.fromXml(xml, User.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param xml XML 문자열
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromXml(String xml, Class<V> type) {
        try {
        	return super.deserialize(xml,type);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * XML 문자열을 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String xml = "{@code <list><item>Apple</item><item>Banana</item></list>}";
     * List<String> fruits = xml.fromXml(xml, 
     *     new TypeReference<List<String>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param xml XML 문자열
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromXml(String xml, TypeReference<V> typeRef) {
        try {
        	return super.deserialize(xml,typeRef);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * XML 문자열을 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * String xml = "{@code <root><n>Product A</n><price>10000</price></root>}";
     * Map<String, Object> product = xml.fromXml(xml, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param xml XML 문자열
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromXml(String xml, TypeRegistry typeRegistry) {
        try {
        	return super.deserialize(xml,typeRegistry);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    // ==================== byte[] XML → Object ====================
    
    /**
     * byte[] XML을 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * byte[] xmlBytes = "{@code <User><n>John</n></User>}".getBytes();
     * User user = xml.fromXml(xmlBytes, User.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param xml XML byte 배열
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromXml(byte[] xml, Class<V> type) {
        try {
        	return super.deserialize(xml,type);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * byte[] XML을 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * byte[] xmlBytes = "{@code <list><item>1</item><item>2</item></list>}".getBytes();
     * List<Integer> numbers = xml.fromXml(xmlBytes, 
     *     new TypeReference<List<Integer>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param xml XML byte 배열
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromXml(byte[] xml, TypeReference<V> typeRef) {
        try {
        	return super.deserialize(xml,typeRef);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * byte[] XML을 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * byte[] xmlBytes = "{@code <root><id>1</id></root>}".getBytes();
     * Map<String, Object> data = xml.fromXml(xmlBytes, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param xml XML byte 배열
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromXml(byte[] xml, TypeRegistry typeRegistry) {
        try {
        	return super.deserialize(xml,typeRegistry);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    // ==================== Path (파일) XML → Object ====================
    
    /**
     * 파일에서 XML을 읽어 객체로 변환 (Class 타입)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path xmlFile = Paths.get("config.xml");
     * Config config = xml.fromXml(xmlFile, Config.class);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param type 대상 클래스
     * @return 변환된 객체
     */
    public <V> V fromXml(Path path, Class<V> type) {
        try {
        	return super.deserialize(Files.readAllBytes(path),type);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * 파일에서 XML을 읽어 객체로 변환 (TypeReference)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path xmlFile = Paths.get("users.xml");
     * List<User> users = xml.fromXml(xmlFile, 
     *     new TypeReference<List<User>>(){});
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param typeRef TypeReference
     * @return 변환된 객체
     */
    public <V> V fromXml(Path path, TypeReference<V> typeRef) {
        try {
        	return super.deserialize(Files.readAllBytes(path),typeRef);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
    /**
     * 파일에서 XML을 읽어 객체로 변환 (TypeRegistry)
     * 
     * <p><b>사용 예:</b></p>
     * <pre>
     * Path xmlFile = Paths.get("data.xml");
     * Map<String, Object> data = xml.fromXml(xmlFile, 
     *     TypeRegistry.MAP_OBJECT);
     * </pre>
     * 
     * @param <V> 반환 타입
     * @param path 파일 경로
     * @param typeRegistry TypeRegistry
     * @return 변환된 객체
     */
    public <V> V fromXml(Path path, TypeRegistry typeRegistry) {
        try {
        	return super.deserialize(Files.readAllBytes(path),typeRegistry);
        } catch (Exception e) {
            logger.warn("XML 역직렬화 실패 : {}", CommonUtils.getExceptionMessage(e));
            return null;
        }
    }
    
}