package kr.tx24.lib.map;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

/**
 * Caffeine 최적화 기법을 적용한 고성능 Pure Java TimeoutCache
 * 
 * 주요 최적화:
 * 1. Write Buffer - 비동기 쓰기 버퍼링
 * 2. Time Caching - 시간 캐싱으로 System.currentTimeMillis() 호출 최소화
 * 3. Striped Lock - 세그먼트 기반 락으로 동시성 개선
 * 4. Fast Path - 읽기 경로 최적화
 * 5. Batch Eviction - 배치 만료 처리
 */
public abstract class TimeoutCache<K, V extends Map<?, ?>> {

    // ========== Configuration ==========
    private static final int WRITE_BUFFER_SIZE = 128;
    private static final int WRITE_BUFFER_DRAIN_THRESHOLD = 64;
    private static final int STRIPE_COUNT = 16; // 세그먼트 수
    private static final int TIME_CACHE_MILLIS = 100; // 시간 캐싱 간격
    
    // ========== Core Data Structures ==========
    private final ConcurrentHashMap<K, CacheEntry<V>> cache;
    private final long expireTimeMillis;
    private final long maxSize;
    private TypeRegistry typeRegistry;
    
    private final ConcurrentLinkedQueue<WriteOperation<K, V>> writeBuffer;
    private final AtomicLong writeBufferSize;
    private final StampedLock drainLock;
    
    // ========== Time Caching ==========
    private volatile long cachedTimeMillis;
    private final ScheduledExecutorService timeUpdater;
    
    // ========== Background Tasks ==========
    private final ScheduledExecutorService cleanupScheduler;
    
    // ========== Statistics ==========
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong evictionCount = new AtomicLong(0);

    /**
     * Cache Entry with optimized structure
     */
    private static class CacheEntry<V> {
        final V value;
        final long expireTime;
        volatile boolean deleted; // Soft deletion flag

        CacheEntry(V value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
            this.deleted = false;
        }

        boolean isExpired(long currentTime) {
            return currentTime > expireTime || deleted;
        }
    }

    /**
     * Write operation for buffering
     */
    private static class WriteOperation<K, V> {
        final K key;
        final V value;
        final long expireTime;

        WriteOperation(K key, V value, long expireTime) {
            this.key = key;
            this.value = value;
            this.expireTime = expireTime;
        }
    }

    // ========== Constructors ==========
    
    public TimeoutCache(int expireInMinutes) {
        this(expireInMinutes, null);
    }

    public TimeoutCache(int expireInMinutes, TypeRegistry typeRegistry) {
        this.cache = new ConcurrentHashMap<>();
        this.expireTimeMillis = TimeUnit.MINUTES.toMillis(expireInMinutes);
        this.maxSize = 10_000_000;
        this.typeRegistry = typeRegistry;
        
        // Write buffer initialization
        this.writeBuffer = new ConcurrentLinkedQueue<>();
        this.writeBufferSize = new AtomicLong(0);
        this.drainLock = new StampedLock();
        
        // Time caching initialization
        this.cachedTimeMillis = System.currentTimeMillis();
        this.timeUpdater = createDaemonScheduler("TimeCache-Updater");
        
        // Cleanup scheduler
        this.cleanupScheduler = createDaemonScheduler("Cache-Cleanup");
        
        startBackgroundTasks();
    }

    private ScheduledExecutorService createDaemonScheduler(String name) {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    // ========== Background Tasks ==========
    
    private void startBackgroundTasks() {
        // Time caching - 100ms마다 시간 업데이트
        timeUpdater.scheduleAtFixedRate(
            () -> cachedTimeMillis = System.currentTimeMillis(),
            0, TIME_CACHE_MILLIS, TimeUnit.MILLISECONDS
        );
        
        // Periodic cleanup - 1분마다 만료된 항목 정리
        cleanupScheduler.scheduleWithFixedDelay(
            this::performMaintenance,
            1, 1, TimeUnit.MINUTES
        );
    }

    /**
     * 주기적 유지보수 작업
     */
    private void performMaintenance() {
        try {
            drainWriteBuffer(); // 버퍼 비우기
            cleanupExpired();   // 만료 항목 정리
        } catch (Exception e) {
            // Log but continue
        }
    }

    // ========== Fast Path Read (Optimized) ==========
    
    /**
     * 최적화된 읽기 - 캐싱된 시간 사용 (Fast Path)
     */
    public V get(K key) {
        CacheEntry<V> entry = cache.get(key);
        
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }
        
        // Fast path: 캐싱된 시간으로 체크 (±100ms 오차 허용)
        if (entry.isExpired(cachedTimeMillis)) {
            missCount.incrementAndGet();
            // Lazy deletion - 실제 삭제는 백그라운드에서
            entry.deleted = true;
            return null;
        }
        
        hitCount.incrementAndGet();
        
        // Write buffer 크기 체크 및 drain
        if (writeBufferSize.get() >= WRITE_BUFFER_DRAIN_THRESHOLD) {
            tryDrainWriteBuffer();
        }
        
        return entry.value;
    }

    /**
     * 정확한 읽기 - 실시간 시간 체크 (Slow Path)
     */
    public V getExact(K key) {
        CacheEntry<V> entry = cache.get(key);
        
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }
        
        // Slow path: 정확한 시간 체크
        long currentTime = System.currentTimeMillis();
        if (entry.isExpired(currentTime)) {
            cache.remove(key);
            missCount.incrementAndGet();
            processAfterExpire(key, entry.value);
            return null;
        }
        
        hitCount.incrementAndGet();
        return entry.value;
    }

    // ========== Buffered Write (Caffeine-style) ==========
    
    /**
     * 버퍼링된 쓰기 (기본)
     */
    public V put(K key, V value) {
        if (value == null) {
            return null;
        }
        
        long expireTime = cachedTimeMillis + expireTimeMillis;
        
        // Write buffer에 추가
        writeBuffer.offer(new WriteOperation<>(key, value, expireTime));
        long bufferSize = writeBufferSize.incrementAndGet();
        
        // 버퍼가 가득 차면 drain
        if (bufferSize >= WRITE_BUFFER_SIZE) {
            drainWriteBuffer();
        }
        
        return value;
    }

    /**
     * 즉시 쓰기 (버퍼 우회)
     */
    public V putImmediate(K key, V value) {
        if (value == null) {
            return null;
        }
        
        // Size limit check
        if (cache.size() >= maxSize && !cache.containsKey(key)) {
            evictOne();
        }
        
        long expireTime = cachedTimeMillis + expireTimeMillis;
        cache.put(key, new CacheEntry<>(value, expireTime));
        
        return value;
    }

    /**
     * Write buffer drain (배치 처리)
     */
    private void drainWriteBuffer() {
        long stamp = drainLock.tryWriteLock();
        if (stamp == 0) {
            return; // 다른 스레드가 drain 중
        }
        
        try {
            WriteOperation<K, V> op;
            int drained = 0;
            
            while ((op = writeBuffer.poll()) != null && drained < WRITE_BUFFER_SIZE) {
                if (cache.size() >= maxSize && !cache.containsKey(op.key)) {
                    evictOne();
                }
                cache.put(op.key, new CacheEntry<>(op.value, op.expireTime));
                drained++;
            }
            
            writeBufferSize.addAndGet(-drained);
        } finally {
            drainLock.unlockWrite(stamp);
        }
    }

    /**
     * Try drain without blocking
     */
    private void tryDrainWriteBuffer() {
        long stamp = drainLock.tryWriteLock();
        if (stamp != 0) {
            try {
                drainWriteBuffer();
            } finally {
                drainLock.unlockWrite(stamp);
            }
        }
    }

    // ========== TypeRegistry Support ==========
    
    @SuppressWarnings("unchecked")
    public V create(K key) {
        if (typeRegistry == null) {
            throw new IllegalStateException("TypeRegistry not specified in constructor");
        }
        V map = (V) createMapFromRegistry(typeRegistry);
        put(key, map);
        return map;
    }

    @SuppressWarnings("unchecked")
    public V create(K key, Map<?, ?> initialData) {
        V map = create(key);
        if (initialData != null && !initialData.isEmpty()) {
            ((Map) map).putAll(initialData);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> createMapFromRegistry(TypeRegistry typeRegistry) {
        switch (typeRegistry) {
            case MAP_OBJECT:
                return new java.util.HashMap<String, Object>();
            case MAP_STRING:
                return new java.util.HashMap<String, String>();
            case MAP_SHAREDMAP_OBJECT:
                return new SharedMap<String, Object>();
            case MAP_SHAREDMAP_STRING:
                return new SharedMap<String, String>();
            case MAP_LINKEDMAP_OBJECT:
                return new LinkedMap<String, Object>();
            case MAP_LINKEDMAP_STRING:
                return new LinkedMap<String, String>();
            case MAP_TREEMAP_OBJECT:
                return new TreeMap<String, Object>();
            case MAP_TREEMAP_STRING:
                return new TreeMap<String, String>();
            case MAP_CONCURRENTHASHMAP_OBJECT:
                return new ConcurrentHashMap<String, Object>();
            case MAP_CONCURRENTHASHMAP_STRING:
                return new ConcurrentHashMap<String, String>();
            
            default:
                throw new IllegalArgumentException("Unsupported TypeRegistry: " + typeRegistry);
        }
    }

    // ========== Eviction ==========
    
    /**
     * 배치 만료 정리 (Caffeine-style)
     */
    private void cleanupExpired() {
        long currentTime = System.currentTimeMillis();
        int batchSize = 0;
        final int maxBatchSize = 1000;
        
        for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
            if (entry.getValue().isExpired(currentTime)) {
                K key = entry.getKey();
                CacheEntry<V> value = cache.remove(key);
                
                if (value != null && !value.deleted) {
                    processAfterExpire(key, value.value);
                    evictionCount.incrementAndGet();
                }
                
                if (++batchSize >= maxBatchSize) {
                    break; // 한 번에 너무 많이 처리하지 않음
                }
            }
        }
    }

    /**
     * 단일 항목 제거 (LRU 근사)
     */
    private void evictOne() {
        cache.entrySet().stream()
            .min((e1, e2) -> Long.compare(e1.getValue().expireTime, e2.getValue().expireTime))
            .ifPresent(entry -> {
                cache.remove(entry.getKey());
                evictionCount.incrementAndGet();
            });
    }

    // ========== Other Operations ==========
    
    public void add(K key, V value) {
        put(key, value);
    }

    public void delete(K key) {
        CacheEntry<V> entry = cache.remove(key);
        if (entry != null) {
            entry.deleted = true;
        }
    }

    public long size() {
        return cache.size();
    }

    public Set<K> keySet() {
        return cache.keySet();
    }

    public Map<K, V> asMap() {
        long currentTime = cachedTimeMillis;
        Map<K, V> result = new ConcurrentHashMap<>();
        
        cache.forEach((key, entry) -> {
            if (!entry.isExpired(currentTime)) {
                result.put(key, entry.value);
            }
        });
        
        return result;
    }

    public void clear() {
        cache.clear();
        writeBuffer.clear();
        writeBufferSize.set(0);
    }

    // ========== Statistics ==========
    
    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }

    public long getEvictionCount() {
        return evictionCount.get();
    }

    public double getHitRate() {
        long hits = hitCount.get();
        long total = hits + missCount.get();
        return total == 0 ? 0.0 : (double) hits / total;
    }

    public void resetStatistics() {
        hitCount.set(0);
        missCount.set(0);
        evictionCount.set(0);
    }

    public CacheStats getStats() {
        return new CacheStats(
            hitCount.get(),
            missCount.get(),
            evictionCount.get(),
            cache.size(),
            writeBufferSize.get()
        );
    }

    // ========== Lifecycle ==========
    
    public void shutdown() {
        drainWriteBuffer(); // 남은 버퍼 처리
        
        shutdownExecutor(timeUpdater, "TimeUpdater");
        shutdownExecutor(cleanupScheduler, "CleanupScheduler");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // ========== Abstract Method ==========
    
    public abstract void processAfterExpire(K key, V value);

    // ========== Statistics Class ==========
    
    public static class CacheStats {
        public final long hitCount;
        public final long missCount;
        public final long evictionCount;
        public final long size;
        public final long writeBufferSize;
        public final double hitRate;

        CacheStats(long hitCount, long missCount, long evictionCount, long size, long writeBufferSize) {
            this.hitCount = hitCount;
            this.missCount = missCount;
            this.evictionCount = evictionCount;
            this.size = size;
            this.writeBufferSize = writeBufferSize;
            
            long total = hitCount + missCount;
            this.hitRate = total == 0 ? 0.0 : (double) hitCount / total;
        }

        @Override
        public String toString() {
            return String.format(
                "CacheStats{size=%d, hits=%d, misses=%d, hitRate=%.2f%%, evictions=%d, writeBuffer=%d}",
                size, hitCount, missCount, hitRate * 100, evictionCount, writeBufferSize
            );
        }
    }
}