package kr.tx24.lib.lifecycle;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import javax.management.ListenerNotFoundException;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.BDUtils;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.MapFactory;
import kr.tx24.lib.map.TypeRegistry;
import kr.tx24.lib.mapper.JacksonUtils;
import kr.tx24.lib.redis.RedisUtils;

/**
 * JVM 메모리와 스레드 상태를 주기적으로 수집하고 보고합니다.
 * <p>
 * GC 완료 알림에서 GC 직후 힙 사용률을 계산하며, 기본적으로 75% 이상 상태가 30분 동안
 * 최소 3회의 GC에서 반복되면 용량 검토 경고를 한 번 기록합니다. 사용률이 65% 이하로
 * 회복된 이후에는 다음 경고가 다시 활성화됩니다.
 * <p>
 * 시스템 프로퍼티:
 * <ul>
 *   <li>{@code JVM_GC_AFTER_USAGE_THRESHOLD}: 경고 사용률, 기본값 {@code 0.75}</li>
 *   <li>{@code JVM_GC_AFTER_USAGE_RECOVERY_THRESHOLD}: 경고 재활성화 사용률, 기본값 {@code 0.65}</li>
 *   <li>{@code JVM_GC_AFTER_USAGE_DURATION_MINUTES}: 지속시간(분), 기본값 {@code 30}</li>
 *   <li>{@code JVM_GC_AFTER_USAGE_MIN_COUNT}: 지속시간 동안 필요한 최소 GC 횟수, 기본값 {@code 3}</li>
 * </ul>
 */
public class JvmStatusManager {
	private static final Logger logger = LoggerFactory.getLogger(JvmStatusManager.class);
	
	
    private static final long SAMPLING_PERIOD_SECONDS = 60; 			// 데이터 샘플링 주기: 1분
    private static final long DEFAULT_REPORT_PERIOD_MINUTES = 5; 		// 보고 주기의 기본값 (5분)
    private static final String JVM_REPORT_PROPERTY = "JVM_REPORT";	// Redis 보고 활성화 시스템 프로퍼티

    private static final double DEFAULT_GC_AFTER_USAGE_THRESHOLD          = 0.75;
    private static final double DEFAULT_GC_AFTER_USAGE_RECOVERY_THRESHOLD = 0.65;
    private static final long   DEFAULT_GC_AFTER_USAGE_DURATION_MINUTES   = 30;
    private static final int    DEFAULT_GC_AFTER_USAGE_MIN_COUNT          = 3;

    private static final String GC_AFTER_USAGE_THRESHOLD_PROPERTY          = "JVM_GC_AFTER_USAGE_THRESHOLD";
    private static final String GC_AFTER_USAGE_RECOVERY_THRESHOLD_PROPERTY = "JVM_GC_AFTER_USAGE_RECOVERY_THRESHOLD";
    private static final String GC_AFTER_USAGE_DURATION_PROPERTY           = "JVM_GC_AFTER_USAGE_DURATION_MINUTES";
    private static final String GC_AFTER_USAGE_MIN_COUNT_PROPERTY          = "JVM_GC_AFTER_USAGE_MIN_COUNT";

    private static final double GC_AFTER_USAGE_THRESHOLD = getRatioProperty(
            GC_AFTER_USAGE_THRESHOLD_PROPERTY, DEFAULT_GC_AFTER_USAGE_THRESHOLD);
    private static final double GC_AFTER_USAGE_RECOVERY_THRESHOLD = getRecoveryRatioProperty(
            GC_AFTER_USAGE_RECOVERY_THRESHOLD_PROPERTY, DEFAULT_GC_AFTER_USAGE_RECOVERY_THRESHOLD,
            GC_AFTER_USAGE_THRESHOLD);
    private static final long GC_AFTER_USAGE_DURATION_MINUTES = getPositiveLongProperty(
            GC_AFTER_USAGE_DURATION_PROPERTY, DEFAULT_GC_AFTER_USAGE_DURATION_MINUTES);
    private static final long GC_AFTER_USAGE_DURATION_MILLIS =
            TimeUnit.MINUTES.toMillis(GC_AFTER_USAGE_DURATION_MINUTES);
    private static final int GC_AFTER_USAGE_MIN_COUNT = getPositiveIntProperty(
            GC_AFTER_USAGE_MIN_COUNT_PROPERTY, DEFAULT_GC_AFTER_USAGE_MIN_COUNT);
    
    // 보고 주기 동안 관찰된 최대 지표 값을 저장하는 스냅샷
    private static final AtomicReference<MetricsSnapshot> MAX_PERIOD_SNAPSHOT =
            new AtomicReference<>(new MetricsSnapshot());
    private static final AtomicReference<AfterGcSnapshot> MAX_PERIOD_AFTER_GC_SNAPSHOT =
            new AtomicReference<>(AfterGcSnapshot.EMPTY);

    private static final Set<String> HEAP_MEMORY_POOL_NAMES = getHeapMemoryPoolNames();
    private static final CopyOnWriteArrayList<NotificationEmitter> GC_NOTIFICATION_EMITTERS =
            new CopyOnWriteArrayList<>();
    private static final NotificationListener GC_NOTIFICATION_LISTENER = JvmStatusManager::handleGcNotification;
    private static final AtomicBoolean isGcMonitoringInitialized = new AtomicBoolean(false);
    private static final GcUsageWarningTracker GC_USAGE_WARNING_TRACKER = new GcUsageWarningTracker(
            GC_AFTER_USAGE_THRESHOLD, GC_AFTER_USAGE_RECOVERY_THRESHOLD,
            GC_AFTER_USAGE_DURATION_MILLIS, GC_AFTER_USAGE_MIN_COUNT);
    
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

    private record AfterGcSnapshot(double used, double rate) {
        private static final AfterGcSnapshot EMPTY = new AfterGcSnapshot(0.0, 0.0);
    }

    private record GcUsageEvaluation(boolean isWarningRequired, boolean isRecovered, long durationMillis, int gcCount) {}

    private static final class GcUsageWarningTracker {
        private final double highUsageThreshold;
        private final double recoveryThreshold;
        private final long   requiredDurationMillis;
        private final int    requiredGcCount;

        private long    highUsageSinceMillis  = -1L;
        private long    lastObservationMillis = -1L;
        private int     highUsageGcCount       = 0;
        private boolean isWarned               = false;

        GcUsageWarningTracker(double highUsageThreshold, double recoveryThreshold,
                              long requiredDurationMillis, int requiredGcCount) {
            this.highUsageThreshold     = highUsageThreshold;
            this.recoveryThreshold      = recoveryThreshold;
            this.requiredDurationMillis = requiredDurationMillis;
            this.requiredGcCount        = requiredGcCount;
        }

        synchronized GcUsageEvaluation evaluate(double usageRate, long gcEndTimeMillis) {
            long observationMillis = Math.max(gcEndTimeMillis, lastObservationMillis);
            lastObservationMillis  = observationMillis;

            if (usageRate >= highUsageThreshold) {
                if (highUsageSinceMillis < 0L) {
                    highUsageSinceMillis = observationMillis;
                    highUsageGcCount     = 1;
                } else {
                    highUsageGcCount++;
                }

                long durationMillis = observationMillis - highUsageSinceMillis;
                if (!isWarned && durationMillis >= requiredDurationMillis && highUsageGcCount >= requiredGcCount) {
                    isWarned = true;
                    return new GcUsageEvaluation(true, false, durationMillis, highUsageGcCount);
                }

                return new GcUsageEvaluation(false, false, durationMillis, highUsageGcCount);
            }

            if (usageRate <= recoveryThreshold) {
                boolean isRecovered = isWarned;
                reset();
                return new GcUsageEvaluation(false, isRecovered, 0L, 0);
            }

            highUsageSinceMillis = -1L;
            highUsageGcCount     = 0;
            return new GcUsageEvaluation(false, false, 0L, 0);
        }

        synchronized void reset() {
            highUsageSinceMillis  = -1L;
            lastObservationMillis = -1L;
            highUsageGcCount      = 0;
            isWarned              = false;
        }
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

        try {
            initializeGcMonitoring();

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
        } catch (RuntimeException e) {
            cancelScheduledTasks();
            shutdownGcMonitoring();
            isInitialized.set(false);
            throw e;
        }
    }
    

    /**
     * 60초 간격으로 실행되는 샘플링 및 최대값 갱신 로직입니다.
     */
    private static void sampleJvmStatus() {
        try {
            LinkedMap<String, Object> memoryMap = getMemoryInfo();
            LinkedMap<String, Object> threadMap = getThreadInfo();

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
        AfterGcSnapshot maxAfterGcSnapshot = MAX_PERIOD_AFTER_GC_SNAPSHOT.getAndSet(AfterGcSnapshot.EMPTY);
    	
        LinkedMap<String,Object> map = MapFactory.createObjectMap(TypeRegistry.MAP_LINKEDMAP_OBJECT,12);
    	
    	map.put("id"		, SystemManager.getProcessId());
    	map.put("regDay"	, DateUtils.getCurrentDay());
        map.put("regDate"	, DateUtils.getCurrentTimestamp());
        
        // 1. 최대값 기반 메모리 정보 재구성 ---
        map.put("mem_used"	, BDUtils.valueOf(maxSnapshot.used,2).doubleValue()); 	// 최대 사용 힙
        map.put("mem_rate"	, BDUtils.valueOf(maxSnapshot.rate,4).doubleValue()); 	// 최대 사용률
        map.put("mem_total"	, BDUtils.valueOf(maxSnapshot.total,2).doubleValue()); 	// 할당된 총 힙 메모리 최대값
        map.put("mem_free"	, BDUtils.valueOf(maxSnapshot.free,2).doubleValue());   	// 여유 힙 메모리 최대값
        map.put("mem_max"	, BDUtils.valueOf(maxSnapshot.max,2).doubleValue());   	// 최대 사용 가능 힙 메모리 (Xmx)
        if (maxAfterGcSnapshot.rate() > 0.0) {
            map.put("mem_after_gc_used", BDUtils.valueOf(maxAfterGcSnapshot.used(),2).doubleValue()); // GC 직후 최대 사용 힙
            map.put("mem_after_gc_rate", BDUtils.valueOf(maxAfterGcSnapshot.rate(),4).doubleValue()); // GC 직후 Xmx 사용률
        }
        
        // 2. 최대값 기반 스레드 정보 재구성 ---
        map.put("th_active", maxSnapshot.thread); 	// 최대 활성 스레드 수
        map.put("th_daemon", maxSnapshot.daemon); 	// 최대 데몬 스레드 수
        
        
        //0.1 로 기록된 경우는 배제한다.
        if(map.getDouble("mem_used", 0.0) < 0.1) {
        	return;
        }
        
    	try {
    		// System property가 true일 경우 상세 로깅 (최대값 기준)
            if (Boolean.parseBoolean(System.getProperty(JVM_REPORT_PROPERTY, "false"))) {
        		// Redis에 JSON 문자열로 저장
        		RedisUtils.rpush(SystemUtils.REDIS_STORAGE_JVM, map);
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
            current.rate 	= Math.max(current.rate, memory.getDouble("rate", 0.0));
            current.used 	= Math.max(current.used, memory.getDouble("used", 0.0));
            current.total 	= Math.max(current.total, memory.getDouble("total", 0.0));
            current.free 	= Math.max(current.free, memory.getDouble("free", 0.0));
            current.max 	= Math.max(current.max, memory.getDouble("max", 0.0));

            // Thread Peaks
            current.thread 	= Math.max(current.thread, thread.getInt("total", 0));
            current.daemon 	= Math.max(current.daemon, thread.getInt("daemon", 0));
            
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
     * GC 완료 알림을 등록하고 GC 직후 힙 사용량 모니터링을 시작합니다.
     */
    private static void initializeGcMonitoring() {
        if (!isGcMonitoringInitialized.compareAndSet(false, true)) {
            return;
        }

        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (!(gcBean instanceof NotificationEmitter emitter)) {
                continue;
            }

            try {
                emitter.addNotificationListener(GC_NOTIFICATION_LISTENER, null, null);
                GC_NOTIFICATION_EMITTERS.add(emitter);
            } catch (RuntimeException e) {
                logger.warn("Failed to register GC notification listener: {}", gcBean.getName(), e);
            }
        }

        if (GC_NOTIFICATION_EMITTERS.isEmpty()) {
            logger.warn("GC completion notifications are not supported. After-GC memory monitoring is disabled.");
            return;
        }

        logger.info(
                "JVM after-GC memory monitoring started: threshold={}%, recovery={}%, "
                + "duration={} minutes, minimum GC count={}",
                String.format("%.2f", GC_AFTER_USAGE_THRESHOLD * 100),
                String.format("%.2f", GC_AFTER_USAGE_RECOVERY_THRESHOLD * 100),
                GC_AFTER_USAGE_DURATION_MINUTES,
                GC_AFTER_USAGE_MIN_COUNT);
    }

    private static void handleGcNotification(Notification notification, Object handback) {
        if (!GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION.equals(notification.getType())) {
            return;
        }

        try {
            if (!(notification.getUserData() instanceof CompositeData compositeData)) {
                return;
            }

            GarbageCollectionNotificationInfo notificationInfo =
                    GarbageCollectionNotificationInfo.from(compositeData);
            GcInfo gcInfo = notificationInfo.getGcInfo();
            long usedBytes = getHeapUsedBytes(gcInfo.getMemoryUsageAfterGc());
            long maxBytes  = Runtime.getRuntime().maxMemory();

            if (usedBytes <= 0L || maxBytes <= 0L) {
                return;
            }

            double used      = usedBytes / (double) MB;
            double max       = maxBytes / (double) MB;
            double usageRate = usedBytes / (double) maxBytes;

            updateMaxAfterGcSnapshot(used, usageRate);

            GcUsageEvaluation evaluation = GC_USAGE_WARNING_TRACKER.evaluate(usageRate, gcInfo.getEndTime());
            if (evaluation.isWarningRequired()) {
                logger.warn(
                        "JVM_GC_MEMORY_HIGH_USAGE: GC 후 힙 사용률이 {}% 이상인 상태가 {}분 동안 지속되었습니다. "
                        + "해당 기간 GC 횟수={}회, 현재 사용률={}%, 사용={}MB, 최대={}MB, "
                        + "GC 이름={}, 동작={}, 원인={}.",
                        String.format("%.2f", GC_AFTER_USAGE_THRESHOLD * 100),
                        TimeUnit.MILLISECONDS.toMinutes(evaluation.durationMillis()),
                        evaluation.gcCount(),
                        String.format("%.2f", usageRate * 100),
                        String.format("%.2f", used),
                        String.format("%.2f", max),
                        notificationInfo.getGcName(),
                        notificationInfo.getGcAction(),
                        notificationInfo.getGcCause());
            } else if (evaluation.isRecovered()) {
                logger.warn(
                        "JVM_GC_MEMORY_USAGE_RECOVERED: GC 후 힙 사용률이 {}%로 감소했습니다. "
                        + "(사용={}MB / 최대={}MB).",
                        String.format("%.2f", usageRate * 100),
                        String.format("%.2f", used),
                        String.format("%.2f", max));
            }
        } catch (Exception e) {
            logger.warn("Error while processing GC memory usage notification", e);
        }
    }

    private static long getHeapUsedBytes(Map<String, MemoryUsage> memoryUsageAfterGc) {
        long usedBytes = 0L;

        for (String poolName : HEAP_MEMORY_POOL_NAMES) {
            MemoryUsage memoryUsage = memoryUsageAfterGc.get(poolName);
            if (memoryUsage != null && memoryUsage.getUsed() > 0L) {
                usedBytes += memoryUsage.getUsed();
            }
        }

        return usedBytes;
    }

    private static void updateMaxAfterGcSnapshot(double used, double rate) {
        AfterGcSnapshot snapshot = new AfterGcSnapshot(used, rate);
        MAX_PERIOD_AFTER_GC_SNAPSHOT.getAndUpdate(current -> current.rate() >= rate ? current : snapshot);
    }

    private static Set<String> getHeapMemoryPoolNames() {
        Set<String> poolNames = new HashSet<>();

        for (MemoryPoolMXBean memoryPool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (memoryPool.getType() == MemoryType.HEAP) {
                poolNames.add(memoryPool.getName());
            }
        }

        return Set.copyOf(poolNames);
    }

    private static double getRatioProperty(String property, double defaultValue) {
        double value = CommonUtils.parseDouble(System.getProperty(property, Double.toString(defaultValue)));
        return value > 0.0 && value <= 1.0 ? value : defaultValue;
    }

    private static double getRecoveryRatioProperty(String property, double defaultValue, double highUsageThreshold) {
        double value = getRatioProperty(property, defaultValue);
        return value < highUsageThreshold ? value : Math.min(defaultValue, highUsageThreshold * 0.8);
    }

    private static long getPositiveLongProperty(String property, long defaultValue) {
        long value = CommonUtils.parseLong(System.getProperty(property, Long.toString(defaultValue)));
        return value > 0L ? value : defaultValue;
    }

    private static int getPositiveIntProperty(String property, int defaultValue) {
        int value = CommonUtils.parseInt(System.getProperty(property, Integer.toString(defaultValue)));
        return value > 0 ? value : defaultValue;
    }

    private static void shutdownGcMonitoring() {
        if (!isGcMonitoringInitialized.compareAndSet(true, false)) {
            return;
        }

        for (NotificationEmitter emitter : GC_NOTIFICATION_EMITTERS) {
            try {
                emitter.removeNotificationListener(GC_NOTIFICATION_LISTENER);
            } catch (ListenerNotFoundException e) {
                logger.debug("GC notification listener was already removed", e);
            } catch (RuntimeException e) {
                logger.warn("Failed to remove GC notification listener", e);
            }
        }

        GC_NOTIFICATION_EMITTERS.clear();
        GC_USAGE_WARNING_TRACKER.reset();
        MAX_PERIOD_AFTER_GC_SNAPSHOT.set(AfterGcSnapshot.EMPTY);
    }

    private static void cancelScheduledTasks() {
        if (samplingFuture != null) {
            samplingFuture.cancel(false);
            samplingFuture = null;
        }
        if (reportingFuture != null) {
            reportingFuture.cancel(false);
            reportingFuture = null;
        }
    }

    /**
     * 스케줄러를 안전하게 종료합니다. (AsyncExecutor에 등록된 작업 취소)
     */
    public static void shutdown() {
        if (!isInitialized.compareAndSet(true, false)) {
            return;
        }

        cancelScheduledTasks();
        shutdownGcMonitoring();
    }
}
