package kr.tx24.lib.map;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;

/**
 * Generic TimeoutCache
 * 모든 Map 타입을 캐시 값으로 저장 가능
 */
public abstract class TimeoutCache<K, V extends Map<?, ?>> {

    private Cache<K, V> cache;

    /**
     * 생성자: 캐시 만료 시간(분)
     */
    public TimeoutCache(int expireInMinutes) {
        init(expireInMinutes);
    }

    private void init(int expireInMinutes) {
        RemovalListener<K, V> removalListener = (key, value, cause) -> {
            if (cause == RemovalCause.EXPIRED && value != null) {
                processAfterExpire(key, value);
            }
        };

        cache = Caffeine.newBuilder()
                .expireAfterWrite(expireInMinutes, TimeUnit.MINUTES)
                .maximumSize(10_000_000)
                .removalListener(removalListener)
                .build();
    }

    /** 캐시에서 값 조회 */
    public V get(K key) {
        return cache.getIfPresent(key);
    }

    /** 캐시에 값 저장 */
    public V put(K key, V value) {
        if (value != null) {
            cache.put(key, value);
        }
        return value;
    }

    public void add(K key, V value) {
        put(key, value);
    }

    /** 캐시에서 삭제 */
    public void delete(K key) {
        cache.invalidate(key);
    }

    /** 캐시 크기 */
    public long size() {
        return cache.estimatedSize();
    }

    /** 캐시 키셋 반환 */
    public Set<K> keySet() {
        return cache.asMap().keySet();
    }

    /** 캐시 전체 Map 반환 */
    public Map<K, V> asMap() {
        return cache.asMap();
    }

    /** Caffeine Cache 직접 접근 */
    public Cache<K, V> getCache() {
        return cache;
    }

    /** 캐시 만료 시 처리 */
    public abstract void processAfterExpire(K key, V value);
}
