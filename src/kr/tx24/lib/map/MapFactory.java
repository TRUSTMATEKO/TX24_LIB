package kr.tx24.lib.map;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Map 인스턴스 생성을 위한 팩토리 유틸리티
 * 
 * <p>TypeRegistry의 MAP_* 타입들을 기반으로 실제 Map 구현체 인스턴스를 생성합니다.</p>
 * 
 * <h3>기본 설정</h3>
 * <ul>
 *   <li><b>initialCapacity</b>: 16 (HashMap, LinkedHashMap 계열)</li>
 *   <li><b>loadFactor</b>: 0.75f (HashMap, LinkedHashMap 계열)</li>
 *   <li>설정이 가능한 클래스에만 적용되며, 지원하지 않는 클래스는 기본 생성자 사용</li>
 * </ul>
 * 
 * <h3>지원하는 Map 타입</h3>
 * <ul>
 *   <li>HashMap (MAP_OBJECT, MAP_STRING, MAP_INTEGER_*, MAP_LONG_*)</li>
 *   <li>LinkedHashMap (MAP_LINKEDHASHMAP_*)</li>
 *   <li>ConcurrentHashMap (MAP_CONCURRENTHASHMAP_*)</li>
 *   <li>TreeMap (MAP_TREEMAP_*) </li>
 *   <li>SharedMap (MAP_SHAREDMAP_*)</li>
 *   <li>LinkedMap (MAP_LINKEDMAP_*)</li>
 *   <li>ThreadSafeLinkedMap (MAP_THREADSAFE_LINKEDMAP_*)</li>
 * </ul>
 * 
 * @author TX24
 * @version 1.1
 * @see TypeRegistry
 */
public final class MapFactory {
    
    /**
     * 인스턴스화 방지
     */
    private MapFactory() {
        throw new AssertionError("MapFactory is a utility class and cannot be instantiated");
    }
    
    // 기본 설정 상수
    private static final int DEFAULT_INITIAL_CAPACITY = 16;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    

    public static int getDefaultInitialCapacity() {
        return DEFAULT_INITIAL_CAPACITY;
    }
    

    public static float getDefaultLoadFactor() {
        return DEFAULT_LOAD_FACTOR;
    }
    
    /**
     * TypeRegistry를 기반으로 Map 인스턴스 생성
     * 
     * <p>HashMap과 LinkedHashMap 계열은 기본 initialCapacity=16, loadFactor=0.75f로 생성됩니다.</p>
     * 
     * @param <K> 키 타입
     * @param <V> 값 타입
     * @param typeRegistry Map 타입 레지스트리
     * @return 생성된 Map 인스턴스
     * @throws IllegalArgumentException 지원하지 않는 TypeRegistry이거나 Map 타입이 아닌 경우
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> create(TypeRegistry typeRegistry) {
        if (typeRegistry == null) {
            throw new IllegalArgumentException("TypeRegistry cannot be null");
        }
        
        return switch (typeRegistry) {
            case MAP_OBJECT, MAP_STRING, MAP_INTEGER_OBJECT, MAP_LONG_OBJECT,
                 MAP_INTEGER_STRING, MAP_LONG_STRING, MAP_LIST_STRING, 
                 MAP_LIST_OBJECT, MAP_SET_STRING, MAP_MAP_OBJECT -> 
                (Map<K, V>) new HashMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
            
            case MAP_LINKEDHASHMAP_OBJECT, MAP_LINKEDHASHMAP_STRING -> 
                (Map<K, V>) new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
            
            case MAP_CONCURRENTHASHMAP_OBJECT, MAP_CONCURRENTHASHMAP_STRING -> 
                (Map<K, V>) new ConcurrentHashMap<>(DEFAULT_INITIAL_CAPACITY,DEFAULT_LOAD_FACTOR);
            
            case MAP_TREEMAP_OBJECT, MAP_TREEMAP_STRING -> 
                (Map<K, V>) new TreeMap<>();
            
            case MAP_SHAREDMAP_OBJECT, MAP_SHAREDMAP_STRING -> 
                (Map<K, V>) new SharedMap<>(DEFAULT_INITIAL_CAPACITY,DEFAULT_LOAD_FACTOR);
            
            case MAP_LINKEDMAP_OBJECT, MAP_LINKEDMAP_STRING -> 
                (Map<K, V>) new LinkedMap<>(DEFAULT_INITIAL_CAPACITY,DEFAULT_LOAD_FACTOR);
            
            case MAP_THREADSAFE_LINKEDMAP_OBJECT, MAP_THREADSAFE_LINKEDMAP_STRING -> 
                (Map<K, V>) new ThreadSafeLinkedMap<>(DEFAULT_INITIAL_CAPACITY,DEFAULT_LOAD_FACTOR);
            
            default -> throw new IllegalArgumentException(
                "MAP_ 으로 시작하는 것만 지원됩니다. " + typeRegistry
            );
        };
    }
    
    /**
     * 초기 용량을 지정하여 Map 인스턴스 생성
     * 
     * <p>초기 용량을 지정하면 Map이 리사이징되는 빈도를 줄여 성능을 향상시킬 수 있습니다.</p>
     * <p>loadFactor는 기본값(0.75f)이 적용됩니다.</p>
     * 
     * @param <K> 키 타입
     * @param <V> 값 타입
     * @param typeRegistry Map 타입 레지스트리
     * @param initialCapacity 초기 용량 (양수)
     * @return 생성된 Map 인스턴스
     * @throws IllegalArgumentException typeRegistry가 null이거나 initialCapacity가 음수인 경우
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> create(TypeRegistry typeRegistry, int initialCapacity) {
        if (typeRegistry == null) {
            throw new IllegalArgumentException("TypeRegistry cannot be null");
        }
        
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                "Initial capacity cannot be negative: " + initialCapacity
            );
        }
        
        return switch (typeRegistry) {
            case MAP_OBJECT, MAP_STRING, MAP_INTEGER_OBJECT, MAP_LONG_OBJECT,
                 MAP_INTEGER_STRING, MAP_LONG_STRING, MAP_LIST_STRING,
                 MAP_LIST_OBJECT, MAP_SET_STRING, MAP_MAP_OBJECT -> 
                (Map<K, V>) new HashMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
            
            case MAP_LINKEDHASHMAP_OBJECT, MAP_LINKEDHASHMAP_STRING -> 
                (Map<K, V>) new LinkedHashMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
            
            case MAP_CONCURRENTHASHMAP_OBJECT, MAP_CONCURRENTHASHMAP_STRING -> 
                (Map<K, V>) new ConcurrentHashMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
            
            case MAP_TREEMAP_OBJECT, MAP_TREEMAP_STRING -> 
                (Map<K, V>) new TreeMap<>();
            
            case MAP_SHAREDMAP_OBJECT, MAP_SHAREDMAP_STRING -> 
                (Map<K, V>) new SharedMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
            
            case MAP_LINKEDMAP_OBJECT, MAP_LINKEDMAP_STRING -> 
                (Map<K, V>) new LinkedMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
            
            case MAP_THREADSAFE_LINKEDMAP_OBJECT, MAP_THREADSAFE_LINKEDMAP_STRING -> 
                (Map<K, V>) new ThreadSafeLinkedMap<>(initialCapacity, DEFAULT_LOAD_FACTOR);
            
            default -> throw new IllegalArgumentException(
            		"MAP_ 으로 시작하는 것만 지원됩니다. " + typeRegistry
            );
        };
    }
    
    /**
     * 초기 용량과 로드 팩터를 지정하여 Map 인스턴스 생성
     * 
     * <p>로드 팩터는 Map이 리사이징되는 시점을 결정합니다. (기본값: 0.75)</p>
     * 
     * @param <K> 키 타입
     * @param <V> 값 타입
     * @param typeRegistry Map 타입 레지스트리
     * @param initialCapacity 초기 용량 (양수)
     * @param loadFactor 로드 팩터 (0.0 < loadFactor <= 1.0)
     * @return 생성된 Map 인스턴스
     * @throws IllegalArgumentException 파라미터가 유효하지 않은 경우
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> create(TypeRegistry typeRegistry, 
                                          int initialCapacity, 
                                          float loadFactor) {
        if (typeRegistry == null) {
            throw new IllegalArgumentException("TypeRegistry cannot be null");
        }
        
        if (initialCapacity < 0) {
            throw new IllegalArgumentException(
                "Initial capacity cannot be negative: " + initialCapacity
            );
        }
        
        if (loadFactor <= 0 || loadFactor > 1.0f) {
            throw new IllegalArgumentException(
                "Load factor must be between 0 and 1: " + loadFactor
            );
        }
        
        return switch (typeRegistry) {
            case MAP_OBJECT, MAP_STRING, MAP_INTEGER_OBJECT, MAP_LONG_OBJECT,
                 MAP_INTEGER_STRING, MAP_LONG_STRING, MAP_LIST_STRING,
                 MAP_LIST_OBJECT, MAP_SET_STRING, MAP_MAP_OBJECT -> 
                (Map<K, V>) new HashMap<>(initialCapacity, loadFactor);
            
            case MAP_LINKEDHASHMAP_OBJECT, MAP_LINKEDHASHMAP_STRING -> 
                (Map<K, V>) new LinkedHashMap<>(initialCapacity, loadFactor);
            
            default -> create(typeRegistry, initialCapacity);
        };
    }
    

    public static Map<String, Object> createHashMap() {
        return new HashMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }
    
   
    public static Map<String, Object> createLinkedHashMap() {
        return new LinkedHashMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }
    
 
    public static Map<String, Object> createConcurrentHashMap() {
        return new ConcurrentHashMap<>(DEFAULT_INITIAL_CAPACITY);
    }
    
    /**
     * 기본 TreeMap&lt;String, Object&gt; 생성 (정렬됨)
     * 
     * @return TreeMap 인스턴스
     */
    public static Map<String, Object> createTreeMap() {
        return new TreeMap<>();
    }
    
    /**
     * SharedMap&lt;String, Object&gt; 생성
     * 
     * @return SharedMap 인스턴스
     */
    public static Map<String, Object> createSharedMap() {
        return new SharedMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }
    
    /**
     * LinkedMap&lt;String, Object&gt; 생성
     * 
     * @return LinkedMap 인스턴스
     */
    public static Map<String, Object> createLinkedMap() {
        return new LinkedMap<>(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }
    
    
    /**
     * TypeRegistry를 기반으로 초기 용량을 지정하여 Map&lt;String, Object&gt; 인스턴스 생성
     * 
     * @param <T> Map&lt;String, Object&gt;를 상속하는 타입
     * @param typeRegistry Map 타입 레지스트리
     * @return 생성된 Map&lt;String, Object&gt; 인스턴스
     */
	public static <T extends Map<String,Object>> T createObjectMap(TypeRegistry typeRegistry) {
	    return createObjectMap(typeRegistry,DEFAULT_INITIAL_CAPACITY);
	}
    
    /**
     * TypeRegistry를 기반으로 초기 용량을 지정하여 Map&lt;String, Object&gt; 인스턴스 생성
     * 
     * @param <T> Map&lt;String, Object&gt;를 상속하는 타입
     * @param typeRegistry Map 타입 레지스트리
     * @param initialCapacity 초기 용량
     * @return 생성된 Map&lt;String, Object&gt; 인스턴스
     */
    @SuppressWarnings("unchecked")
	public static <T extends Map<String,Object>> T createObjectMap(TypeRegistry typeRegistry, int initialCapacity) {
	    switch (typeRegistry) {
	        case MAP_OBJECT:
	            return (T) new java.util.HashMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_SHAREDMAP_OBJECT:
	            return (T) new SharedMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_LINKEDMAP_OBJECT:
	            return (T) new LinkedMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_THREADSAFE_LINKEDMAP_OBJECT:
	            return (T) new ThreadSafeLinkedMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_LINKEDHASHMAP_OBJECT:
	            return (T) new LinkedHashMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_CONCURRENTHASHMAP_OBJECT:
	            return (T) new ConcurrentHashMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_TREEMAP_OBJECT:
	            return (T) new TreeMap<String, Object>();
	        case MAP_STRING:
	            return (T) new java.util.HashMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_LINKEDHASHMAP_STRING:
	            return (T) new LinkedHashMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_CONCURRENTHASHMAP_STRING:
	            return (T) new ConcurrentHashMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_TREEMAP_STRING:
	            return (T) new TreeMap<String, Object>();
	        case MAP_SHAREDMAP_STRING:
	            return (T) new SharedMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_LINKEDMAP_STRING:
	            return (T) new LinkedMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        case MAP_THREADSAFE_LINKEDMAP_STRING:
	            return (T) new ThreadSafeLinkedMap<String, Object>(initialCapacity, DEFAULT_LOAD_FACTOR);
	        default:
	            throw new IllegalArgumentException("Unsupported TypeRegistry: " + typeRegistry);
	    }
	}
    
    public static <T extends Map<String, Object>> T createObjectMap(Class<T> clazz) {
        return createObjectMap(clazz, DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }
    
    
 
    public static <T extends Map<String, Object>> T createObjectMap(
            Class<T> clazz, 
            int initialCapacity) {
        return createObjectMap(clazz, initialCapacity, DEFAULT_LOAD_FACTOR);
    }
    
    @SuppressWarnings("unchecked")
    public static <T extends Map<String, Object>> T createObjectMap(
            Class<T> clazz,
            int initialCapacity,
            float loadFactor) {

        if (clazz == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }

        // HashMap 계열
        if (clazz == HashMap.class || "HashMap".equals(clazz.getSimpleName())) {
            return (T) new HashMap<String, Object>(initialCapacity, loadFactor);
        }

        // LinkedHashMap
        if (clazz == LinkedHashMap.class || "LinkedHashMap".equals(clazz.getSimpleName())) {
            return (T) new LinkedHashMap<String, Object>(initialCapacity, loadFactor);
        }

        // ConcurrentHashMap
        if (clazz == ConcurrentHashMap.class || "ConcurrentHashMap".equals(clazz.getSimpleName())) {
            return (T) new ConcurrentHashMap<String, Object>(initialCapacity, loadFactor);
        }

        // TreeMap (initialCapacity, loadFactor 미지원)
        if (clazz == TreeMap.class || "TreeMap".equals(clazz.getSimpleName())) {
            return (T) new TreeMap<String, Object>();
        }

        // SharedMap
        if ("SharedMap".equals(clazz.getSimpleName())) {
            return (T) new SharedMap<String, Object>(initialCapacity, loadFactor);
        }

        // LinkedMap
        if ("LinkedMap".equals(clazz.getSimpleName())) {
            return (T) new LinkedMap<String, Object>(initialCapacity, loadFactor);
        }

        // ThreadSafeLinkedMap
        if ("ThreadSafeLinkedMap".equals(clazz.getSimpleName())) {
        	return (T) new ThreadSafeLinkedMap<String, Object>(initialCapacity, loadFactor);
        }
 
        try {
       
            try {
                return clazz.getConstructor(int.class, float.class)
                           .newInstance(initialCapacity, loadFactor);
            } catch (NoSuchMethodException e) {
                return clazz.getDeclaredConstructor().newInstance();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(
                "Cannot create instance of " + clazz.getName() +
                ". Ensure the class has a public no-arg constructor or " +
                "(int, float) constructor.", e);
        }
    }
    

    
}