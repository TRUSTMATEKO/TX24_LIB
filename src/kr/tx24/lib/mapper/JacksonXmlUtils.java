package kr.tx24.lib.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;

    /**
     * XML 전용 Jackson Utility + JSON Map/List 변환 지원
     */
    public class JacksonXmlUtils extends JacksonAbstract<XmlMapper> {
        private static final Logger logger = LoggerFactory.getLogger(JacksonXmlUtils.class);

        /** 기본 생성자 */
        public JacksonXmlUtils() {
            super(createDefaultMapper());
        }

        /** 내부용 생성자 */
        private JacksonXmlUtils(XmlMapper mapper) {
            super(mapper);
        }

        /** 기본 XmlMapper 생성 */
        private static XmlMapper createDefaultMapper() {
            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
            xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
            return xmlMapper;
        }

        @Override
        public JacksonXmlUtils pretty() {
            return this;
        }

        @Override
        public JacksonXmlUtils compact() {
        	XmlMapper newMapper = mapper.copy();
            newMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
            return new JacksonXmlUtils(newMapper);
        }

        @Override
        protected JacksonXmlUtils createNewInstance(XmlMapper mapper) {
            return new JacksonXmlUtils(mapper);
        }
        
        
        public boolean isValid(String xml) {
            if (xml == null || xml.isEmpty()) return false;
            try {
                mapper.readTree(xml);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        
        public boolean isValid(String xml, Class<?> valueType) {
            if (xml == null || xml.isEmpty()) return false;
            try {
                mapper.readValue(xml, valueType);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        
        
        
        

        // ---------------- XML 직렬화 / 역직렬화 ----------------
        public String toXml(Object value) {
            if (value == null) return "";
            try {
                return mapper.writeValueAsString(value);
            } catch (Exception e) {
                logger.error("XML 직렬화 실패", e);
                throw new RuntimeException("XML 직렬화 실패", e);
            }
        }

        public <V> V fromXml(String xml, Class<V> type) {
            if (xml == null || xml.isEmpty()) return null;
            try {
                return mapper.readValue(xml, type);
            } catch (Exception e) {
                logger.error("XML 역직렬화 실패", e);
                throw new RuntimeException("XML 역직렬화 실패", e);
            }
        }
        


        // ---------------- Map 변환 ----------------
        public <V> Map<String, V> fromMap(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<Map<String, V>>() {});
        }

        public Map<String, String> fromMap(String json) {
            return deserialize(json, new TypeReference<Map<String, String>>() {});
        }

        public Map<String, Object> fromMapObject(String json) {
            return deserialize(json, new TypeReference<Map<String, Object>>() {});
        }

        public <V> List<Map<String, V>> fromMapList(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<List<Map<String, V>>>() {});
        }

        public List<Map<String, String>> fromMapList(String json) {
            return deserialize(json, new TypeReference<List<Map<String, String>>>() {});
        }

        public List<Map<String, Object>> fromMapListObject(String json) {
            return deserialize(json, new TypeReference<List<Map<String, Object>>>() {});
        }

        // ---------------- SharedMap 변환 ----------------
        public <V> SharedMap<String, V> fromSharedMap(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<SharedMap<String, V>>() {});
        }

        public SharedMap<String, String> fromSharedMap(String json) {
            return deserialize(json, new TypeReference<SharedMap<String, String>>() {});
        }

        public SharedMap<String, Object> fromSharedMapObject(String json) {
            return deserialize(json, new TypeReference<SharedMap<String, Object>>() {});
        }

        public <V> List<SharedMap<String, V>> fromSharedMapList(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<List<SharedMap<String, V>>>() {});
        }

        public List<SharedMap<String, String>> fromSharedMapList(String json) {
            return deserialize(json, new TypeReference<List<SharedMap<String, String>>>() {});
        }

        public List<SharedMap<String, Object>> fromSharedMapListObject(String json) {
            return deserialize(json, new TypeReference<List<SharedMap<String, Object>>>() {});
        }

        // ---------------- LinkedMap 변환 ----------------
        public <V> LinkedMap<String, V> fromLinkedMap(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<LinkedMap<String, V>>() {});
        }

        public LinkedMap<String, String> fromLinkedMap(String json) {
            return deserialize(json, new TypeReference<LinkedMap<String, String>>() {});
        }

        public LinkedMap<String, Object> fromLinkedMapObject(String json) {
            return deserialize(json, new TypeReference<LinkedMap<String, Object>>() {});
        }

        public <V> List<LinkedMap<String, V>> fromLinkedMapList(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<List<LinkedMap<String, V>>>() {});
        }

        public List<LinkedMap<String, String>> fromLinkedMapList(String json) {
            return deserialize(json, new TypeReference<List<LinkedMap<String, String>>>() {});
        }

        public List<LinkedMap<String, Object>> fromLinkedMapListObject(String json) {
            return deserialize(json, new TypeReference<List<LinkedMap<String, Object>>>() {});
        }

        // ---------------- LinkedHashMap 변환 ----------------
        public <V> LinkedHashMap<String, V> fromLinkedHashMap(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<LinkedHashMap<String, V>>() {});
        }

        public LinkedHashMap<String, String> fromLinkedHashMap(String json) {
            return deserialize(json, new TypeReference<LinkedHashMap<String, String>>() {});
        }

        public LinkedHashMap<String, Object> fromLinkedHashMapObject(String json) {
            return deserialize(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        }

        public <V> List<LinkedHashMap<String, V>> fromLinkedHashMapList(String json, Class<V> valueClass) {
            return deserialize(json, new TypeReference<List<LinkedHashMap<String, V>>>() {});
        }

        public List<LinkedHashMap<String, String>> fromLinkedHashMapList(String json) {
            return deserialize(json, new TypeReference<List<LinkedHashMap<String, String>>>() {});
        }

        public List<LinkedHashMap<String, Object>> fromLinkedHashMapListObject(String json) {
            return deserialize(json, new TypeReference<List<LinkedHashMap<String, Object>>>() {});
        }
    
    
    
    
}
