package kr.tx24.lib.lifecycle;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.BDUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.MapFactory;
import kr.tx24.lib.map.TypeRegistry;
import kr.tx24.lib.mapper.JacksonUtils;
import kr.tx24.lib.redis.RedisUtils;

public class JvmStatusManager {
	private static final Logger logger = LoggerFactory.getLogger(JvmStatusManager.class);
	
	
    private static final long SAMPLING_PERIOD_SECONDS = 60; 			// 데이터 샘플링 주기: 1분
    private static final long DEFAULT_REPORT_PERIOD_MINUTES = 5; 		// 보고 주기의 기본값 (5분)
    private static final double HIGH_USAGE_THRESHOLD = 0.70;			// 메모리 사용량 경고 임계치 (80%)
    private static final String JVM_MONITOR_PROPERTY = "JVM_MONITOR";	// 상세 로깅 활성화 시스템 프로퍼티
    
    // 보고 주기 동안 관찰된 최대 지표 값을 저장하는 스냅샷
    private static final AtomicReference<MetricsSnapshot> MAX_PERIOD_SNAPSHOT =	new AtomicReference<>(new MetricsSnapshot());
    
    // AsyncExecutor를 통해 등록된 작업 Future 관리
    private static volatile ScheduledFuture<?> samplingFuture = null;
    private static volatile ScheduledFuture<?> reportingFuture = null;
    
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    
    private static final long MB = 1024 * 1024;

    private JvmStatusManager() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

  
    //보고 주기 동안의 최대 지표를 저장하는 내부 클래스
    private static class MetricsSnapshot {
        // 메모리 최대값
        volatile double rate 	= 0.0;
        volatile double used 	= 0.0;
        volatile double total 	= 0.0;  
        volatile double free 	= 0.0;
        volatile double max 	= 0.0;
        // 스레드 최대값
        volatile int thread 	= 0;
        volatile int daemon 	= 0;
    }
    
    
    public static void initialize() {
        initialize(DEFAULT_REPORT_PERIOD_MINUTES);
    }
    
    /**
     * JVM 상태 모니터링 스케줄러를 시작합니다
     */
    public static void initialize(long reportPeriodMinutes) {
    	
        if (!isInitialized.compareAndSet(false, true)) {
            logger.info("JvmStatusManager tasks are already running.");
            return;
        }

        final long reportPeriod = (reportPeriodMinutes > 0) ? reportPeriodMinutes : DEFAULT_REPORT_PERIOD_MINUTES;

        // 1. 1분 간격의 샘플링 작업 스케줄링 (AsyncExecutor 사용)
        samplingFuture = AsyncExecutor.scheduleAtFixedRate(JvmStatusManager::sampleJvmStatus, 
				                    SAMPLING_PERIOD_SECONDS, 
				                    SAMPLING_PERIOD_SECONDS, 
				                    TimeUnit.SECONDS);
        
        // 2. 보고 주기 간격의 보고 작업 스케줄링 (AsyncExecutor 사용)
        reportingFuture = AsyncExecutor.scheduleAtFixedRate(JvmStatusManager::reportJvmStatus, 
					        		reportPeriod, 			
					        		reportPeriod, 
					        		TimeUnit.MINUTES);
    }
    

    /**
     * 60초 간격으로 실행되는 샘플링 및 최대값 갱신 로직입니다.
     */
    private static void sampleJvmStatus() {
        try {
            LinkedMap<String, Object> memoryMap = getMemoryInfo();
            LinkedMap<String, Object> threadMap = getThreadInfo();
          
            //logger.info("{}",new JacksonUtils().toJson(memoryMap));
            // 메모리 80% 사용 경고 (샘플링 시점에 즉시 경고)
            if (memoryMap.getDouble("rate") >= HIGH_USAGE_THRESHOLD) {
                logger.warn("JVM_MEMORY_HIGH_USAGE: Current utilization is {}% ({}MB / {}MB).", 
                            String.format("%.2f", memoryMap.getDouble("rate") * 100), 
                            String.format("%.2f", memoryMap.getDouble("used")), 
                            String.format("%.2f", memoryMap.getDouble("total")));
            }
            
            // 최대값 갱신 로직 (메모리, 스레드 모든 동적 값 반영)
            updateMaxSnapshot(memoryMap, threadMap);

        } catch (Exception e) {
            logger.warn("Error during JVM status sampling", e);
        }
    }
    
    /**
     * 보고 주기마다 실행되는 최대값 전달 및 로깅 로직입니다.
     */
    private static void reportJvmStatus() {
    	// 현재 최대값 스냅샷을 가져오고 즉시 리셋할 새 스냅샷을 생성하여 초기화
    	MetricsSnapshot maxSnapshot = MAX_PERIOD_SNAPSHOT.getAndSet(new MetricsSnapshot());
    	
        
        // 1. 최대값 기반 메모리 정보 재구성 ---
        LinkedMap<String,Object> memoryMap = MapFactory.createObjectMap(TypeRegistry.MAP_LINKEDMAP_OBJECT,5);
        memoryMap.put("used"	, BDUtils.valueOf(maxSnapshot.used,2).doubleValue()); 	// 최대 사용 힙
        memoryMap.put("rate"	, BDUtils.valueOf(maxSnapshot.rate,4).doubleValue()); 	// 최대 사용률
        memoryMap.put("total"	, BDUtils.valueOf(maxSnapshot.total,2).doubleValue()); 	// 할당된 총 힙 메모리 최대값
        memoryMap.put("free"	, BDUtils.valueOf(maxSnapshot.free,2).doubleValue());   	// 여유 힙 메모리 최대값
        memoryMap.put("max"		, BDUtils.valueOf(maxSnapshot.max,2).doubleValue());   	// 여유 힙 메모리 최대값
        
        // 2. 최대값 기반 스레드 정보 재구성 ---
        LinkedMap<String,Object> threadMap = MapFactory.createObjectMap(TypeRegistry.MAP_LINKEDMAP_OBJECT,2);
        threadMap.put("active", maxSnapshot.thread); 	// 최대 활성 스레드 수
        threadMap.put("daemon", maxSnapshot.daemon); 	// 최대 데몬 스레드 수
        
        LinkedMap<String,Object> map = MapFactory.createObjectMap(TypeRegistry.MAP_LINKEDMAP_OBJECT,3);
        map.put("timestamp"	, System.currentTimeMillis()); 
    	map.put("memory"	, memoryMap);
    	map.put("thread"	, threadMap);
        
    	try {
    		// System property가 true일 경우 상세 로깅 (최대값 기준)
            if (Boolean.parseBoolean(System.getProperty(JVM_MONITOR_PROPERTY, "false"))) {
        		// Redis에 JSON 문자열로 저장
        		RedisUtils.rpush(SystemUtils.REDIS_STORAGE_JVM, new JacksonUtils().toJson(map));
            }else {
            	logger.info("jvm status : {}",new JacksonUtils().toJson(map));
            }
    	}catch(Exception e) {
    		logger.info("RedisUtils.rpush failed for JVM monitoring data.", e);
    	}
    
    }

    /**
     * 측정된 값으로 최대값 스냅샷을 갱신합니다. (스레드 안전)
     */
    private static void updateMaxSnapshot(LinkedMap<String,Object> memory,LinkedMap<String,Object> thread) {
        MAX_PERIOD_SNAPSHOT.getAndUpdate(current -> {
            // Memory Peaks
            current.rate 	= Math.max(current.rate, memory.getDouble("rate"));
            current.used 	= Math.max(current.used, memory.getDouble("used"));
            current.total 	= Math.max(current.total, memory.getDouble("total")); 
            current.free 	= Math.max(current.free, memory.getDouble("free"));
            current.max 	= Math.max(current.free, memory.getDouble("max")); 

            // Thread Peaks
            current.thread 	= Math.max(current.thread, thread.getInt("total"));
            current.daemon 	= Math.max(current.daemon, thread.getInt("daemon")); 
            
            return current;
        });
    }


    
    /**
     * 현재 JVM 메모리 정보를 계산하고 LinkedMap으로 반환합니다.
     */
    private static LinkedMap<String,Object> getMemoryInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemoryBytes 	= runtime.totalMemory();
        long freeMemoryBytes 	= runtime.freeMemory();
        long usedMemoryBytes 	= totalMemoryBytes - freeMemoryBytes;
        long maxMemoryBytes 	= runtime.maxMemory();
        
        double total 			= totalMemoryBytes / (double) MB; 	// JVM에 할당된 총 힙메모리
        double free 			= freeMemoryBytes / (double) MB; 	// JVM에 할당된 여유 공간
        double used 			= usedMemoryBytes / (double) MB; 	// 사용중 힙 메모리
        double max 				= maxMemoryBytes / (double) MB; 	// 최대 사용 가능 힙 메모리 (Xmx)
        
        LinkedMap<String,Object> memoryMap = MapFactory.createObjectMap(TypeRegistry.MAP_LINKEDMAP_OBJECT);
        memoryMap.put("used"	, used);
        memoryMap.put("free"	, free);
        memoryMap.put("total"	, total);
        memoryMap.put("max"		, max);
        memoryMap.put("rate"	, used/total);
        return memoryMap;
    }

    /**
     * 현재 JVM 스레드 정보를 계산하고 LinkedMap으로 반환합니다.
     */
    private static LinkedMap<String,Object> getThreadInfo() {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        
        LinkedMap<String,Object> threadMap = MapFactory.createObjectMap(TypeRegistry.MAP_LINKEDMAP_OBJECT);
        threadMap.put("total"	, threadMXBean.getThreadCount());
        threadMap.put("daemon"	, threadMXBean.getDaemonThreadCount());
        threadMap.put("peak"	, threadMXBean.getPeakThreadCount()); // JVM Lifetime Peak
        
        /*
        List<LinkedMap<String,Object>> list = new ArrayList<LinkedMap<String,Object>>();
        Map<Thread.State, Integer> stateCounts = new HashMap<>();
        long[] threadIds = threadMXBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadMXBean.getThreadInfo(threadIds, 0); 
        
        for (ThreadInfo info : threadInfos) {
            if (info != null) {
                Thread.State state = info.getThreadState();
                stateCounts.put(state, stateCounts.getOrDefault(state, 0) + 1);
                
                LinkedMap<String,Object> infoMap = MapFactory.createObjectMap(TypeRegistry.MAP_LINKEDMAP_OBJECT,3);
                infoMap.put("id"	, info.getThreadId());
                infoMap.put("name"	, info.getThreadName());
                infoMap.put("state"	, state.name());
                list.add(infoMap);
            }
        }
        
        threadMap.put("threads", list);
        threadMap.put("state_summary", stateCounts);
        */
        
        
        return threadMap;
    }
    
    /**
     * 스케줄러를 안전하게 종료합니다. (AsyncExecutor에 등록된 작업 취소)
     */
    public static void shutdown() {
        if (!isInitialized.compareAndSet(true, false)) {
            return;
        }

        if (samplingFuture != null) {
            samplingFuture.cancel(false);
            samplingFuture = null;
        }
        if (reportingFuture != null) {
            reportingFuture.cancel(false);
            reportingFuture = null;
        }
    }
}