package kr.tx24.lib.map;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.core.type.TypeReference;

public enum TypeRegistry {
	
	
	
	STRING(new TypeReference<String>(){}),
	OBJECT(new TypeReference<Object>(){}),
	INTEGER(new TypeReference<Integer>(){}),
	LONG(new TypeReference<Long>(){}),
	BOOLEAN(new TypeReference<Boolean>(){}),
	DOUBLE(new TypeReference<Double>(){}),
	FLOAT(new TypeReference<Float>(){}),
	
	MAP_OBJECT(new TypeReference<Map<String,Object>>(){}),
	MAP_STRING(new TypeReference<Map<String,String>>(){}),
	MAP_INTEGER_OBJECT(new TypeReference<Map<Integer, Object>>(){}),
	MAP_LONG_OBJECT(new TypeReference<Map<Long, Object>>(){}),
	MAP_INTEGER_STRING(new TypeReference<Map<Integer, String>>(){}),
	MAP_LONG_STRING(new TypeReference<Map<Long, String>>(){}),
	MAP_LIST_STRING(new TypeReference<Map<String, List<String>>>(){}),
	MAP_LIST_OBJECT(new TypeReference<Map<String, List<Object>>>(){}),
	MAP_SET_STRING(new TypeReference<Map<String, Set<String>>>(){}),
	MAP_MAP_OBJECT(new TypeReference<Map<String, Map<String, Object>>>(){}),
	MAP_SHAREDMAP_OBJECT(new TypeReference<SharedMap<String,Object>>(){}),
	MAP_SHAREDMAP_STRING(new TypeReference<SharedMap<String,String>>(){}),
	MAP_LINKEDMAP_OBJECT(new TypeReference<LinkedMap<String,Object>>(){}),
	MAP_LINKEDMAP_STRING(new TypeReference<LinkedMap<String,String>>(){}),
	MAP_LINKEDHASHMAP_OBJECT(new TypeReference<LinkedHashMap<String,Object>>(){}),
	MAP_LINKEDHASHMAP_STRING(new TypeReference<LinkedHashMap<String,String>>(){}),
	MAP_CONCURRENTHASHMAP_OBJECT(new TypeReference<ConcurrentHashMap<String,Object>>(){}),
	MAP_CONCURRENTHASHMAP_STRING(new TypeReference<ConcurrentHashMap<String,String>>(){}),
	MAP_THREADSAFE_LINKEDMAP_OBJECT(new TypeReference<ThreadSafeLinkedMap<String,Object>>(){}),
	MAP_THREADSAFE_LINKEDMAP_STRING(new TypeReference<ThreadSafeLinkedMap<String,String>>(){}),
	MAP_TREEMAP_OBJECT(new TypeReference<TreeMap<String,Object>>(){}),
	MAP_TREEMAP_STRING(new TypeReference<TreeMap<String,String>>(){}),
	
	
	LIST_STRING(new TypeReference<List<String>>(){}),
	LIST_OBJECT(new TypeReference<List<Object>>(){}),
	LIST_INTEGER(new TypeReference<List<Integer>>(){}),
	LIST_LONG(new TypeReference<List<Long>>(){}),
	LIST_BOOLEAN(new TypeReference<List<Boolean>>(){}),
	LIST_DOUBLE(new TypeReference<List<Double>>(){}),
	LIST_FLOAT(new TypeReference<List<Float>>(){}),
	LIST_MAP_OBJECT(new TypeReference<List<Map<String,Object>>>(){}),
	LIST_MAP_STRING(new TypeReference<List<Map<String,String>>>(){}),
	LIST_SHAREDMAP_OBJECT(new TypeReference<List<SharedMap<String,Object>>>(){}),
	LIST_SHAREDMAP_STRING(new TypeReference<List<SharedMap<String,String>>>(){}),
	LIST_LINKEDMAP_OBJECT(new TypeReference<List<LinkedMap<String,Object>>>(){}),
	LIST_LINKEDMAP_STRING(new TypeReference<List<LinkedMap<String,String>>>(){}),
	LIST_LINKEDHASHMAP_OBJECT(new TypeReference<List<LinkedHashMap<String,Object>>>(){}),
	LIST_LINKEDHASHMAP_STRING(new TypeReference<List<LinkedHashMap<String,String>>>(){}),
	LIST_CONCURRENTHASHMAP_OBJECT(new TypeReference<List<ConcurrentHashMap<String,Object>>>(){}),
	LIST_CONCURRENTHASHMAP_STRING(new TypeReference<List<ConcurrentHashMap<String,String>>>(){}),
	LIST_THREADSAFE_LINKEDMAP_OBJECT(new TypeReference<List<ThreadSafeLinkedMap<String,Object>>>(){}),
	LIST_THREADSAFE_LINKEDMAP_STRING(new TypeReference<List<ThreadSafeLinkedMap<String,String>>>(){}),
	LIST_TREEMAP_OBJECT(new TypeReference<List<TreeMap<String,Object>>>(){}),
	LIST_TREEMAP_STRING(new TypeReference<List<TreeMap<String,String>>>(){}),
	
	LIST_LIST_STRING(new TypeReference<List<List<String>>>(){}),
	LIST_LIST_OBJECT(new TypeReference<List<List<Object>>>(){}),
	
	SET_STRING(new TypeReference<Set<String>>(){}),
	SET_OBJECT(new TypeReference<Set<Object>>(){}),
	SET_INTEGER(new TypeReference<Set<Integer>>(){}),
	SET_LONG(new TypeReference<Set<Long>>(){}),
	SET_BOOLEAN(new TypeReference<Set<Boolean>>(){}),
	SET_DOUBLE(new TypeReference<Set<Double>>(){}),
	SET_FLOAT(new TypeReference<Set<Float>>(){}),
	SET_MAP_OBJECT(new TypeReference<Set<Map<String,Object>>>(){}),
	SET_MAP_STRING(new TypeReference<Set<Map<String,String>>>(){}),
	SET_SHAREDMAP_OBJECT(new TypeReference<Set<SharedMap<String,Object>>>(){}),
	SET_SHAREDMAP_STRING(new TypeReference<Set<SharedMap<String,String>>>(){}),
	SET_LINKEDMAP_OBJECT(new TypeReference<Set<LinkedMap<String,Object>>>(){}),
	SET_LINKEDMAP_STRING(new TypeReference<Set<LinkedMap<String,String>>>(){}),
	SET_LINKEDHASHMAP_OBJECT(new TypeReference<Set<LinkedHashMap<String,Object>>>(){}),
	SET_LINKEDHASHMAP_STRING(new TypeReference<Set<LinkedHashMap<String,String>>>(){}),
	SET_CONCURRENTHASHMAP_OBJECT(new TypeReference<Set<ConcurrentHashMap<String,Object>>>(){}),
	SET_CONCURRENTHASHMAP_STRING(new TypeReference<Set<ConcurrentHashMap<String,String>>>(){}),
	SET_THREADSAFE_LINKEDMAP_OBJECT(new TypeReference<Set<ThreadSafeLinkedMap<String,Object>>>(){}),
	SET_THREADSAFE_LINKEDMAP_STRING(new TypeReference<Set<ThreadSafeLinkedMap<String,String>>>(){}),
	SET_TREEMAP_OBJECT(new TypeReference<Set<TreeMap<String,Object>>>(){}),
	SET_TREEMAP_STRING(new TypeReference<Set<TreeMap<String,String>>>(){});
	
	

    private final TypeReference<?> typeReference;
    
    TypeRegistry(TypeReference<?> typeReference) {
        this.typeReference = typeReference;
    }
    
    @SuppressWarnings("unchecked")
    public <T> TypeReference<T> get() {
        return (TypeReference<T>) typeReference;
    }
}
