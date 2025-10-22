package kr.tx24.lib.lang;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;


/**
 * 
 {
  "memory" : {
    // 메모리 상태 섹션
    "unit" : "MB",                  // 메모리 측정 단위
    "used" : "25.98",               // 현재 JVM이 사용 중인 힙 메모리 (25.98 MB)
    "free" : "54.02",               // 현재 JVM에 할당된 메모리 중 여유 공간 (54.02 MB)
    "total_allocated" : "80.00",    // OS로부터 JVM에 현재 할당된 총 힙 메모리 (Used + Free)
    "max_available" : "16288.00",   // JVM이 최대로 사용할 수 있도록 설정된 힙 메모리 한계 (약 16 GB)
    "used_percent" : "0.16%",       // max_available 대비 현재 used 메모리 사용률 (매우 낮음)
    "delta_since_last_scan" : {     // 직전 10초 측정 주기 대비 변화량
      "used_in_bytes" : 67128,      // 10초 동안 증가한 사용 메모리 (바이트 단위)
      "used_in_mb" : "0.06",        // 10초 동안 증가한 사용 메모리 (MB 단위)
      "percent_change" : "0.25%"    // 직전 사용량 대비 사용 메모리 증가율 (매우 미미함)
    }
  },
  "threads" : {
    // 스레드 상태 섹션
    "total_count" : 142,            // 현재 활성화된 총 스레드 수
    "peak_count" : 142,             // JVM 시작 이후 가장 많았던 스레드 수 (현재 초기 상태와 동일)
    "daemon_count" : 136,           // 백그라운드에서 실행되는 데몬 스레드 수
    "delta_since_last_scan" : {     // 직전 10초 측정 주기 대비 변화량
      "count_in_period" : 0,        // 10초 동안 스레드 수의 변화 (새로 생성/종료된 스레드가 없음)
      "percent_change" : "0.00%"    // 스레드 수 변화율 (안정적)
    }
  },
  "class_loading" : {
    // 클래스 로딩 상태 섹션
    "total_loaded" : 7032,          // JVM 시작 이후 총 로드된 클래스 수
    "current_loaded" : 7032,        // 현재 메모리에 로드되어 있는 클래스 수
    "unloaded_count" : 0,           // GC에 의해 언로드된 클래스 수 (클래스 로더 누수 징후 없음)
    "delta_since_last_scan" : {     // 직전 10초 측정 주기 대비 변화량
      "loaded_in_period" : 0,       // 10초 동안 새로 로드된 클래스 수 (초기 부팅 완료 상태)
      "unloaded_in_period" : 0      // 10초 동안 언로드된 클래스 수
    }
  },
  "gc" : {
    // 가비지 컬렉션 상태 섹션
    "g1_young_generation" : {
      "collection_count_total" : 4,         // 영 영역 GC (Minor GC) 누적 실행 횟수
      "collection_time_ms_total" : 11,      // 영 영역 GC에 누적 소요된 총 시간 (밀리초)
      "delta_since_last_scan" : {           // 직전 10초 측정 주기 대비 변화량
        "count_in_period" : 0,              // 10초 동안 Minor GC 실행 횟수 (GC 발생 없음)
        "time_ms_in_period" : 0,            // 10초 동안 Minor GC 소요 시간
        "time_percent_change" : "0.00%"     // 직전 GC 시간 대비 변화율
      }
    },
    "g1_concurrent_gc" : {
      "collection_count_total" : 2,         // G1 Concurrent Cycle 누적 실행 횟수 (주로 백그라운드 GC)
      "collection_time_ms_total" : 0,
      "delta_since_last_scan" : {
        "count_in_period" : 0,
        "time_ms_in_period" : 0,
        "time_percent_change" : "0.00%"
      }
    },
    "g1_old_generation" : {
      "collection_count_total" : 0,         // 올드 영역 GC (Full GC) 누적 실행 횟수 (매우 좋음)
      "collection_time_ms_total" : 0,
      "delta_since_last_scan" : {
        "count_in_period" : 0,
        "time_ms_in_period" : 0,
        "time_percent_change" : "0.00%"
      }
    }
  },
  "is_critical" : false // Critical 임계치 (메모리 80%, GC 시간 500ms, 스레드 90%)를 초과한 항목이 없음
}
 */

public class JvmStatusUtils {
	private static final Logger logger = LoggerFactory.getLogger(JvmStatusUtils.class);
	
	private static volatile ScheduledExecutorService scheduler = null;
    private static final AtomicBoolean IS_RUNNING = new AtomicBoolean(false); 
    private static final long INITIAL_DELAY_SECONDS = 0;
    
    private static final double MB = 1024.0 * 1024.0;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    // Criticality 임계값 정의
    private static final long GC_TIME_CRITICAL_MS = 500; 
    private static final double MEMORY_CRITICAL_PERCENT = 0.80;
    
    //스레드 임계값 정의 (최대 스레드의 90%)
    private static final double THREAD_CRITICAL_PERCENT = 0.90; 
    
    //Tomcat Connector의 maxThreads 값을 저장할 필드 (기본값 0)
    private static volatile int MAX_THREADS_LIMIT = 0; 

    //첫 실행 플래그 추가
    private static final AtomicBoolean IS_FIRST_RUN = new AtomicBoolean(true); 
    
    // 델타 비교를 위한 이전 상태 저장소
    private static JvmStatus previousStatus = new JvmStatus();
    

    // 이전 상태를 저장하고 현재 상태를 캡처하기 위한 내부 클래스
    private static class JvmStatus {
        long usedMemory = 0; 
        long totalLoadedClassCount = 0;
        long unloadedClassCount = 0;
        int totalThreadCount = 0;
        Map<String, GcStats> gcStats = new HashMap<>();

        static class GcStats {
            long collectionCount = 0;
            long collectionTimeMs = 0;
        }
    }
    
    
    private static long PERIOD_SECONDS = 5*60; // 💡 측정 주기: 300초 (5분)
    
    private JvmStatusUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    //외부에서 Tomcat maxThreads 값을 설정하는 메서드
    public static void setMaxThreads(int maxThreads) {
        if (maxThreads > 0) {
            MAX_THREADS_LIMIT = maxThreads;
            logger.info("JvmStatusUtils: MAX_THREADS_LIMIT set to {}.", maxThreads);
        } else {
             logger.warn("JvmStatusUtils: MAX_THREADS_LIMIT must be a positive number.");
        }
    }
    
    //실해오디고 있는지 여부 확인 
    public static boolean isRunning() {
    	return IS_RUNNING.get();
    }
    
    
    public static void startWithMaxThread(int maxThreads) {
    	setMaxThreads(maxThreads);
    	start(PERIOD_SECONDS);
    }
    
    public static void startWithMaxThread(long period, int maxThreads) {
    	setMaxThreads(maxThreads);
    	start(period);
    }
    
    
    public static void start() {
    	start(PERIOD_SECONDS);
    }
    
    public static void start(long period) {
        if (IS_RUNNING.getAndSet(true)) {
            logger.info("JvmStatusLogger is already running.");
            return;
        }
        
        //start 시점에 첫 실행 플래그를 true로 초기화
        IS_FIRST_RUN.set(true); 

        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "JvmStatus-Monitor");
            thread.setDaemon(true); 
            return thread;
        });

        scheduler.scheduleAtFixedRate(JvmStatusUtils::logJvmStatus, 
        							period, 
                                    period, 
                                    TimeUnit.SECONDS);
        
        logger.info("JvmStatusLogger started: monitoring every {} seconds.", PERIOD_SECONDS);
        
        Runtime.getRuntime().addShutdownHook(new Thread(JvmStatusUtils::shutdown, "ShutdownHook-JvmStatusUtils"));
    }
    
    /**
     * JVM 메모리 및 스레드 상태를 로깅하는 실제 로직
     */
    private static void logJvmStatus() {
    	JvmStatus currentStatus = new JvmStatus();
        List<String> criticalReasons = new ArrayList<>();
        
        try {
            ObjectNode statusNode = MAPPER.createObjectNode();
            
            //첫 실행 여부 확인 및 델타 로직 수행
            boolean isFirstRun = IS_FIRST_RUN.compareAndSet(true, false); 
            
            addMemoryStatus(statusNode, currentStatus, criticalReasons, isFirstRun); 
            //criticalReasons 파라미터 추가
            addThreadStatus(statusNode, currentStatus, criticalReasons, isFirstRun);
           // addClassLoadingStatus(statusNode, currentStatus, isFirstRun);
            addGcStatus(statusNode, currentStatus, criticalReasons, isFirstRun); 
            
            //Criticality 체크 및 로그 기록
            boolean isCritical = !criticalReasons.isEmpty();
            statusNode.put("is_critical", isCritical);
            
            if (isCritical) {
                String reasonsString = String.join(" | ", criticalReasons);
                //로그 포맷: 원인들을 파이프(|)로 구분하여 한 줄에 출력
                logger.warn("Jvm critical status detected : [{}]", PERIOD_SECONDS, reasonsString);
                
                // JSON에 critical_reasons 배열 추가
                ArrayNode reasonsNode = statusNode.putArray("critical_reasons");
                criticalReasons.forEach(reasonsNode::add);
            }else {
            	statusNode.putArray("critical_reasons");
            }
            
            // 현재 상태를 다음 루프의 이전 상태로 저장
            previousStatus = currentStatus;
            
            String prettyJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(statusNode);
            logger.info("JVM Status:\n{}", prettyJson);
            
        } catch (Exception e) {
            logger.error("Error during JVM status logging.", e);
        }
    }
    
    //메모리 상태
    private static void addMemoryStatus(ObjectNode statusNode, JvmStatus current, List<String> criticalReasons, boolean isFirstRun) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long currentUsedMemory = totalMemory - freeMemory;
        
        double usedPercent = (maxMemory > 0) ? (double) currentUsedMemory / maxMemory : 0.0;
        
        current.usedMemory = currentUsedMemory;

        ObjectNode memoryNode = statusNode.putObject("memory");
        memoryNode.put("unit", "MB");
        memoryNode.put("used", String.format("%.2f", currentUsedMemory / MB));
        memoryNode.put("free", String.format("%.2f", freeMemory / MB));
        memoryNode.put("total_allocated", String.format("%.2f", totalMemory / MB));
        memoryNode.put("max_available", String.format("%.2f", maxMemory / MB));
        memoryNode.put("used_percent", String.format("%.2f%%", usedPercent * 100));
        
        ObjectNode deltaNode = memoryNode.putObject("delta_since_last_scan");

        if (isFirstRun) {
            deltaNode.putNull("used_in_bytes");
            deltaNode.putNull("used_in_mb");
            deltaNode.put("percent_change", "0.00%");
        } else {
            long deltaUsedMemory = currentUsedMemory - previousStatus.usedMemory;
            double percentChange = (previousStatus.usedMemory != 0) ? 
                                    ((double)deltaUsedMemory / previousStatus.usedMemory) * 100 : 
                                    (deltaUsedMemory > 0 ? 100.0 : 0.0);
            
            deltaNode.put("used_in_bytes", deltaUsedMemory);
            deltaNode.put("used_in_mb", String.format("%.2f", deltaUsedMemory / MB));
            deltaNode.put("percent_change", String.format("%.2f%%", percentChange));
        }

        // Criticality 체크
        if (maxMemory > 0 && usedPercent >= MEMORY_CRITICAL_PERCENT) {
            criticalReasons.add(String.format("Memory Used (%.2f%%) exceeds critical threshold (%.0f%%).", 
                                                usedPercent * 100, MEMORY_CRITICAL_PERCENT * 100));
        }
    }
    
    //스레드 상태: 델타 처리 및 Criticality 체크 로직 추가
    private static void addThreadStatus(ObjectNode statusNode, JvmStatus current, List<String> criticalReasons, boolean isFirstRun) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int currentCount = threadMXBean.getThreadCount();
        
        current.totalThreadCount = currentCount;
        
        //Criticality 체크
        if (MAX_THREADS_LIMIT > 0) {
            double usedPercent = (double) currentCount / MAX_THREADS_LIMIT;
            
            if (usedPercent >= THREAD_CRITICAL_PERCENT) {
                criticalReasons.add(String.format("Thread count (%d) exceeds critical threshold (%.0f%% of %d).", 
                                                currentCount, THREAD_CRITICAL_PERCENT * 100, MAX_THREADS_LIMIT));
            }
            
            // JSON에 최대 스레드 정보 및 사용률 추가
            ObjectNode infoNode = statusNode.putObject("thread_limit");
            infoNode.put("max_threads", MAX_THREADS_LIMIT);
            infoNode.put("usage_percent", String.format("%.2f%%", usedPercent * 100));
        }


        ObjectNode threadNode = statusNode.putObject("threads");
        threadNode.put("total_count", currentCount);
        threadNode.put("peak_count", threadMXBean.getPeakThreadCount());
        threadNode.put("daemon_count", threadMXBean.getDaemonThreadCount());
        
        ObjectNode deltaNode = threadNode.putObject("delta_since_last_scan");

        if (isFirstRun) {
            deltaNode.putNull("count_in_period");
            deltaNode.put("percent_change", "0.00%");
        } else {
            int deltaCount = currentCount - previousStatus.totalThreadCount;
            double percentChange = (previousStatus.totalThreadCount != 0) ? 
                                    ((double)deltaCount / previousStatus.totalThreadCount) * 100 : 
                                    (deltaCount > 0 ? 100.0 : 0.0);
            
            deltaNode.put("count_in_period", deltaCount);
            deltaNode.put("percent_change", String.format("%.2f%%", percentChange));
        }
    }

    //클래스 로딩 상태
    private static void addClassLoadingStatus(ObjectNode statusNode, JvmStatus current, boolean isFirstRun) {
        long totalLoaded = ManagementFactory.getClassLoadingMXBean().getTotalLoadedClassCount();
        long unloaded = ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();
        
        current.totalLoadedClassCount = totalLoaded;
        current.unloadedClassCount = unloaded;

        ObjectNode classNode = statusNode.putObject("class_loading");
        classNode.put("total_loaded", totalLoaded);
        classNode.put("current_loaded", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
        classNode.put("unloaded_count", unloaded);
        
        ObjectNode deltaNode = classNode.putObject("delta_since_last_scan");

        if (isFirstRun) {
            deltaNode.putNull("loaded_in_period");
            deltaNode.putNull("unloaded_in_period");
        } else {
            long deltaTotalLoaded = totalLoaded - previousStatus.totalLoadedClassCount;
            long deltaUnloaded = unloaded - previousStatus.unloadedClassCount;
            
            deltaNode.put("loaded_in_period", deltaTotalLoaded);
            deltaNode.put("unloaded_in_period", deltaUnloaded);
        }
    }
    
    //GC 상태
    private static void addGcStatus(ObjectNode statusNode, JvmStatus current, List<String> criticalReasons, boolean isFirstRun) {
        ObjectNode gcNode = statusNode.putObject("gc");
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String name = gcBean.getName().replace(' ', '_').toLowerCase(); 
            long currentCount = gcBean.getCollectionCount();
            long currentTime = gcBean.getCollectionTime();
            
            JvmStatus.GcStats prevStats = previousStatus.gcStats.getOrDefault(name, new JvmStatus.GcStats());
            
            ObjectNode beanNode = gcNode.putObject(name);
            beanNode.put("collection_count_total", currentCount);
            beanNode.put("collection_time_ms_total", currentTime);
            
            ObjectNode deltaNode = beanNode.putObject("delta_since_last_scan");

            if (isFirstRun) {
                deltaNode.putNull("count_in_period");
                deltaNode.putNull("time_ms_in_period");
                deltaNode.put("time_percent_change", "0.00%");
            } else {
                long deltaCount = currentCount - prevStats.collectionCount;
                long deltaTimeSeries = currentTime - prevStats.collectionTimeMs;
                
                // Criticality 체크
                if (deltaTimeSeries >= GC_TIME_CRITICAL_MS) { 
                    criticalReasons.add(String.format("GC Time (%s) consumed %dms in last %ds period.", 
                                                      name, deltaTimeSeries, PERIOD_SECONDS));
                }
                
                double percentChange = (prevStats.collectionTimeMs != 0) ? 
                                       ((double)deltaTimeSeries / prevStats.collectionTimeMs) * 100 : 
                                       (deltaTimeSeries > 0 ? 100.0 : 0.0);
                
                deltaNode.put("count_in_period", deltaCount);
                deltaNode.put("time_ms_in_period", deltaTimeSeries);
                deltaNode.put("time_percent_change", String.format("%.2f%%", percentChange));
            }
            
            // 현재 통계 저장
            JvmStatus.GcStats currentStats = new JvmStatus.GcStats();
            currentStats.collectionCount = currentCount;
            currentStats.collectionTimeMs = currentTime;
            current.gcStats.put(name, currentStats);
        }
    }
    
    public static void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            try {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            IS_RUNNING.set(false);
            logger.info("JvmStatusLogger scheduler shutdown completed.");
        }
    }
}