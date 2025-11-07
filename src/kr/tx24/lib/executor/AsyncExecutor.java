package kr.tx24.lib.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;

/**
 * 비동기 작업 실행을 위한 Thread Pool Executor 래퍼
 * <p>
 * <b>특징:</b><br>
 * - Lazy initialization (실제 사용할 때만 초기화)<br>
 * - Thread-safe singleton pattern<br>
 * - Graceful shutdown 지원<br>
 * - 선택적 모니터링 기능
 * <p>
 * <b>모니터링 활성화:</b><br>
 * -Dasync.monitor.enabled=true 시스템 속성 설정
 */
public class AsyncExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncExecutor.class);
    
    // Volatile로 thread-safe하게 lazy initialization
    private static volatile ThreadPoolExecutor executor = null;
    private static volatile ScheduledExecutorService scheduler = null;
    
    // Task 추적용
    private static final AtomicLong taskIdGenerator = new AtomicLong(0);
    private static final ConcurrentHashMap<Long, Long> taskStartTimes = new ConcurrentHashMap<>();
    
    
    private static final String PROP_RUNNING_THRESHOLD_WARN = "async.threshold.warn";
    private static final String PROP_RUNNING_THRESHOLD_MILLIES = "async.threshold.millies";
    
    private static volatile boolean thresholdWarn = true;
    private static volatile long  thresholdMillies= -1;
    
    
    private static final Object EXECUTOR_LOCK = new Object();
    private static final Object SCHEDULER_LOCK = new Object();
    
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    private static final AtomicBoolean monitoringStarted = new AtomicBoolean(false);
    private static final AtomicBoolean monitoringChecked = new AtomicBoolean(false);
    
    // ========== Executor 생성 ==========
    
    /**
     * ThreadPoolExecutor 생성
     * <p>
     * <b>설정값:</b><br>
     * - Core Pool Size: CPU 코어 수<br>
     * - Max Pool Size: CPU 코어 수 * 2<br>
     * - Keep-Alive Time: 60초 (idle 스레드 유지 시간)<br>
     * - Queue: LinkedBlockingQueue (최대 1000개)<br>
     * - Rejection Policy: CallerRunsPolicy (큐가 가득 차면 호출 스레드에서 실행)
     */
    private static ThreadPoolExecutor createExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2;
        
        
        thresholdMillies = CommonUtils.parseLong(System.getProperty(PROP_RUNNING_THRESHOLD_MILLIES,"5"));
        thresholdWarn 	 = Boolean.getBoolean(System.getProperty(PROP_RUNNING_THRESHOLD_WARN,"true"));
        
        
        logger.info("Creating AsyncExecutor: core={}, max={}", corePoolSize, maxPoolSize);
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new CustomThreadFactory(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        // 코어 스레드도 타임아웃 허용 (리소스 효율)
        executor.allowCoreThreadTimeOut(true);
        
        Runtime.getRuntime().addShutdownHook(new Thread(AsyncExecutor::shutdown, "ShutdownHook-AsyncExecutor"));
        
        return executor;
    }
    
    /**
     * Executor 인스턴스 반환 (Lazy initialization with double-checked locking)
     */
    private static ThreadPoolExecutor getExecutorInstance() {
        if (executor == null) {
            synchronized (EXECUTOR_LOCK) {
                if (executor == null) {
                    executor = createExecutor();
                }
            }
        }
        return executor;
    }
    
    /**
     * AsyncExecutor 첫 사용 시 모니터링 자동 시작 체크
     * <p>
     * 시스템 속성 'async.monitor.enabled=true' 설정 시 자동으로 모니터링 시작<br>
     * 한 번만 실행됨 (중복 체크 방지)
     */
    private static void checkAndStartMonitoring() {
        // 이미 체크했으면 리턴
        if (monitoringChecked.get()) {
            return;
        }
        
        // 첫 체크 시도
        if (monitoringChecked.compareAndSet(false, true)) {
            String monitorEnabled = System.getProperty("async.monitor.enabled", "false");
            if ("true".equalsIgnoreCase(monitorEnabled)) {
                startMonitoringInternal();
            }
        }
    }
    
    // ========== Public API ==========
    
    /**
     * ExecutorService 반환
     * <p>
     * 첫 호출 시 Executor를 초기화하고 모니터링 체크 수행
     * 
     * @return ExecutorService
     * @throws IllegalStateException shutdown된 경우
     */
    public static ExecutorService getExecutor() {
        checkShutdown();
        checkAndStartMonitoring();
        return getExecutorInstance();
    }
    
    /**
     * Runnable 작업 실행
     * 
     * @param task 실행할 작업
     * @return Future
     */
    public static Future<?> execute(Runnable task) {
        checkShutdown();
        checkAndStartMonitoring();
        taskCounter.incrementAndGet();
        return getExecutorInstance().submit(() -> {
        	
            try {
                task.run();
            } finally {
                taskCounter.decrementAndGet();
            }
        });
    }
    
    /**
     * Callable 작업 실행
     * 
     * @param task 실행할 작업
     * @param <T> 반환 타입
     * @return Future
     */
    public static <T> Future<T> submit(Callable<T> task) {
        checkShutdown();
        checkAndStartMonitoring();
        taskCounter.incrementAndGet();
        return getExecutorInstance().submit(() -> {
            try {
                return task.call();
            } finally {
                taskCounter.decrementAndGet();
            }
        });
    }
    
    /**
     * 지연 실행 (일정 시간 후 실행)
     * 
     * @param task 실행할 작업
     * @param delay 지연 시간
     * @param unit 시간 단위
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        checkShutdown();
        checkAndStartMonitoring();
        
        String taskName = extractTaskName(task);
        long taskId = taskIdGenerator.incrementAndGet();
        
        
        
        return getSchedulerInstance().schedule(() -> {
            long startTime = System.currentTimeMillis();
            taskStartTimes.put(taskId, startTime);
            taskCounter.incrementAndGet();
            
            try {
                task.run();
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Scheduled task failed: [{}] ({}ms) - {}", 
                    taskName, duration, e.getMessage(), e);
                throw e;
                
            } finally {
                taskStartTimes.remove(taskId);
                taskCounter.decrementAndGet();
                
                long duration = System.currentTimeMillis() - startTime;
                if (duration > thresholdMillies) {
                	if(thresholdWarn) {
                		logger.warn("Long running scheduled task: [{}] ({}ms) , to disalbe =-Dasync.threshold.warn=false", taskName, duration);
                	}else {
                		logger.info("Long running scheduled task: [{}] ({}ms)", taskName, duration);
                	}
                }
            }
        }, delay, unit);
    }
    
    /**
     * 주기적 실행 (고정 간격)
     * 
     * @param task 실행할 작업
     * @param initialDelay 초기 지연
     * @param period 주기
     * @param unit 시간 단위
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, 
                                                          long period, TimeUnit unit) {
        checkShutdown();
        checkAndStartMonitoring();
        
        String taskName = extractTaskName(task);
        
        return getSchedulerInstance().scheduleAtFixedRate(() -> {
        	long taskId = taskIdGenerator.incrementAndGet();
        	long startTime = System.currentTimeMillis();
            taskStartTimes.put(taskId, startTime);
            taskCounter.incrementAndGet();
            
            try {
                task.run();
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Scheduled task failed: [{}] ({}ms) - {}", 
                    taskName, duration, e.getMessage(), e);
                throw e;
                
            } finally {
                taskStartTimes.remove(taskId);
                taskCounter.decrementAndGet();
                
                long duration = System.currentTimeMillis() - startTime;
                
                if (duration > thresholdMillies) {
                	if(thresholdWarn) {
                		logger.warn("Long running scheduled task: [{}] ({}ms)", taskName, duration);
                	}else {
                		logger.info("Long running scheduled task: [{}] ({}ms)", taskName, duration);
                	}
                }
                
                
            }
        }, initialDelay, period, unit);
    }
    
    /**
     * 주기적 실행 (고정 딜레이)
     * 
     * @param task 실행할 작업
     * @param initialDelay 초기 지연
     * @param delay 딜레이
     * @param unit 시간 단위
     * @return ScheduledFuture
     */
    public static ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, 
                                                             long delay, TimeUnit unit) {
        checkShutdown();
        checkAndStartMonitoring();
        
        String taskName = extractTaskName(task);
        
        return getSchedulerInstance().scheduleWithFixedDelay(() -> {
            long taskId = taskIdGenerator.incrementAndGet();
            long startTime = System.currentTimeMillis();
            taskStartTimes.put(taskId, startTime);
            taskCounter.incrementAndGet();
            
            try {
                task.run();
                
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                logger.error("Fixed delay task failed: [{}] ({}ms) - {}", 
                    taskName, duration, e.getMessage(), e);
                
            } finally {
                taskStartTimes.remove(taskId);
                taskCounter.decrementAndGet();
                
                long duration = System.currentTimeMillis() - startTime;
                if (duration > thresholdMillies) {
                	if(thresholdWarn) {
                		logger.warn("Long running scheduled task: [{}] ({}ms)", taskName, duration);
                	}else {
                		logger.info("Long running scheduled task: [{}] ({}ms)", taskName, duration);
                	}
                }
            }
        }, initialDelay, delay, unit);
    }
    
    // ========== 상태 조회 ==========
    
    /**
     * 현재 활성 작업 수
     * <p>
     * Executor가 초기화되지 않았으면 0 반환 (초기화하지 않음)
     */
    public static int getActiveCount() {
        if (isShutdown.get() || executor == null) return 0;
        return executor.getActiveCount();
    }
    
    /**
     * 현재 풀 크기
     * <p>
     * Executor가 초기화되지 않았으면 0 반환 (초기화하지 않음)
     */
    public static int getPoolSize() {
        if (isShutdown.get() || executor == null) return 0;
        return executor.getPoolSize();
    }
    
    /**
     * 큐 대기 작업 수
     * <p>
     * Executor가 초기화되지 않았으면 0 반환 (초기화하지 않음)
     */
    public static int getQueueSize() {
        if (isShutdown.get() || executor == null) return 0;
        return executor.getQueue().size();
    }
    
    /**
     * 완료된 작업 수
     * <p>
     * Executor가 초기화되지 않았으면 0 반환 (초기화하지 않음)
     */
    public static long getCompletedTaskCount() {
        if (isShutdown.get() || executor == null) return 0;
        return executor.getCompletedTaskCount();
    }
    
    /**
     * 총 실행 중/대기 중 작업 수
     */
    public static int getTotalTaskCount() {
        return taskCounter.get();
    }
    
    /**
     * 상태 정보 문자열
     */
    public static String getStatus() {
        if (isShutdown.get()) {
            return "SHUTDOWN";
        }
        
        if (executor == null) {
            return "NOT_INITIALIZED";
        }
        
        return String.format(
            "Active: %d, Pool: %d/%d, Queue: %d, Completed: %d, Total: %d",
            executor.getActiveCount(),
            executor.getPoolSize(),
            executor.getMaximumPoolSize(),
            executor.getQueue().size(),
            executor.getCompletedTaskCount(),
            taskCounter.get()
        );
    }
    
    /**
     * 상세 상태 정보
     */
    public static ExecutorStats getStats() {
        if (isShutdown.get()) {
            return new ExecutorStats(true);
        }
        
        if (executor == null) {
            return new ExecutorStats(false, true); // not initialized
        }
        
        return new ExecutorStats(
            executor.getActiveCount(),
            executor.getPoolSize(),
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getQueue().size(),
            executor.getCompletedTaskCount(),
            taskCounter.get()
        );
    }
    
    // ========== 모니터링 ==========
    
    /**
     * 모니터링 시작 (내부용)
     * <p>
     * 10분마다 AsyncExecutor 상태를 로그에 출력
     */
    private static void startMonitoringInternal() {
        if (!monitoringStarted.compareAndSet(false, true)) {
            logger.debug("AsyncExecutor monitoring already started");
            return; // 이미 시작됨
        }
        
        logger.info("Starting AsyncExecutor monitoring (interval: 10 minutes)...");
        
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(600_000); // 10분
                    
                    ExecutorStats stats = getStats();
                    
                    if (!stats.isShutdown() && !stats.isNotInitialized()) {
                        String status = String.format(
                            "AsyncExecutor Status │ Active: %3d │ Pool: %3d/%3d │ Queue: %4d │ Completed: %8d",
                            stats.getActiveCount(),
                            stats.getPoolSize(),
                            stats.getMaxPoolSize(),
                            stats.getQueueSize(),
                            stats.getCompletedTaskCount()
                        );
                        logger.info(status);
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            logger.info("AsyncExecutor monitoring stopped");
        }, "async-monitor");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
    
    /**
     * 모니터링 수동 시작
     * <p>
     * 시스템 속성 설정 없이 프로그래밍 방식으로 모니터링을 시작하고 싶을 때 사용<br>
     * AsyncExecutor가 아직 초기화되지 않았다면 초기화도 함께 수행
     */
    public static void startMonitoring() {
        checkShutdown();
        // Executor 초기화 보장
        getExecutor();
        startMonitoringInternal();
    }
    
    // ========== Shutdown ==========
    
    /**
     * Graceful shutdown
     * <p>
     * <b>종료 절차:</b><br>
     * 1. 새 작업 거부<br>
     * 2. 기존 작업 완료 대기 (최대 30초)<br>
     * 3. 타임아웃 시 강제 종료<br>
     * 4. 추가 10초 대기
     */
    public static void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            logger.debug("AsyncExecutor already shutdown");
            return;
        }
        
        
        // Main executor shutdown (초기화되었을 경우만)
        if (executor != null) {
            shutdownExecutor(executor, "Main");
        }
        
        // Scheduler shutdown (초기화되었을 경우만)
        if (scheduler != null) {
            shutdownScheduler(scheduler);
        }
        
      
    }
    
    private static void shutdownExecutor(ExecutorService executor, String name) {
        logger.info("Shutting down {} executor...", name);
        logger.info("Pending tasks: {}", ((ThreadPoolExecutor) executor).getQueue().size());
        
        executor.shutdown();
        
        try {
            // 30초 대기
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("{} executor did not terminate in 30s, forcing shutdown...", name);
                
                // 강제 종료
                executor.shutdownNow();
                
                // 추가 10초 대기
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("{} executor did not terminate after forced shutdown", name);
                }
            } else {
                logger.info("{} executor shutdown completed", name);
            }
            
        } catch (InterruptedException e) {
            logger.error("{} executor shutdown interrupted", name, e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private static void shutdownScheduler(ScheduledExecutorService scheduler) {
        logger.info("Shutting down scheduler...");
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Shutdown 여부 확인
     */
    public static boolean isShutdown() {
        return isShutdown.get();
    }
    
    private static void checkShutdown() {
        if (isShutdown.get()) {
            throw new IllegalStateException("AsyncExecutor has been shutdown");
        }
    }
    
    // ========== Scheduler (Lazy initialization) ==========
    
    private static ScheduledExecutorService createScheduler() {
        logger.info("Creating scheduler...");
        return Executors.newScheduledThreadPool(
            4, 
            new CustomThreadFactory("scheduler")
        );
    }
    
    private static ScheduledExecutorService getSchedulerInstance() {
        if (scheduler == null) {
            synchronized (SCHEDULER_LOCK) {
                if (scheduler == null) {
                    scheduler = createScheduler();
                }
            }
        }
        return scheduler;
    }
    
    // ========== Custom ThreadFactory ==========
    
    private static class CustomThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        
        CustomThreadFactory() {
            this("async-executor");
        }
        
        CustomThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(namePrefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(false); // non-daemon thread
            thread.setPriority(Thread.NORM_PRIORITY);
            
            // Uncaught exception handler
            thread.setUncaughtExceptionHandler((t, e) -> {
                logger.error("Uncaught exception in thread {}", t.getName(), e);
            });
            
            return thread;
        }
    }
    
    // ========== ExecutorStats 클래스 ==========
    
    /**
     * AsyncExecutor 상태 정보를 담는 클래스
     */
    public static class ExecutorStats {
        private final boolean isShutdown;
        private final boolean isNotInitialized;
        private final int activeCount;
        private final int poolSize;
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int queueSize;
        private final long completedTaskCount;
        private final int totalTaskCount;
        
        ExecutorStats(boolean isShutdown) {
            this.isShutdown = isShutdown;
            this.isNotInitialized = false;
            this.activeCount = 0;
            this.poolSize = 0;
            this.corePoolSize = 0;
            this.maxPoolSize = 0;
            this.queueSize = 0;
            this.completedTaskCount = 0;
            this.totalTaskCount = 0;
        }
        
        ExecutorStats(boolean isShutdown, boolean isNotInitialized) {
            this.isShutdown = isShutdown;
            this.isNotInitialized = isNotInitialized;
            this.activeCount = 0;
            this.poolSize = 0;
            this.corePoolSize = 0;
            this.maxPoolSize = 0;
            this.queueSize = 0;
            this.completedTaskCount = 0;
            this.totalTaskCount = 0;
        }
        
        ExecutorStats(int activeCount, int poolSize, int corePoolSize, int maxPoolSize,
                     int queueSize, long completedTaskCount, int totalTaskCount) {
            this.isShutdown = false;
            this.isNotInitialized = false;
            this.activeCount = activeCount;
            this.poolSize = poolSize;
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.queueSize = queueSize;
            this.completedTaskCount = completedTaskCount;
            this.totalTaskCount = totalTaskCount;
        }
        
        // Getters
        public boolean isShutdown() { return isShutdown; }
        public boolean isNotInitialized() { return isNotInitialized; }
        public int getActiveCount() { return activeCount; }
        public int getPoolSize() { return poolSize; }
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getQueueSize() { return queueSize; }
        public long getCompletedTaskCount() { return completedTaskCount; }
        public int getTotalTaskCount() { return totalTaskCount; }
        
        @Override
        public String toString() {
            if (isShutdown) {
                return "ExecutorStats{isShutdown=true}";
            }
            if (isNotInitialized) {
                return "ExecutorStats{isNotInitialized=true}";
            }
            return String.format(
                "ExecutorStats{active=%d, pool=%d/%d/%d, queue=%d, completed=%d, total=%d}",
                activeCount, poolSize, corePoolSize, maxPoolSize, 
                queueSize, completedTaskCount, totalTaskCount
            );
        }
    }
    
    
    private static String extractTaskName(Runnable task) {
        Class<?> clazz = task.getClass();
        String className = clazz.getSimpleName();
        
        // SimpleName이 비어있거나 '$'를 포함하는 경우 = 익명 클래스 또는 람다
        if (className.isEmpty() || className.contains("$")) {
            String fullName = clazz.getName();
            int dollarIndex = fullName.indexOf('$');
            
            if (dollarIndex > 0) {
                int lastDotIndex = fullName.lastIndexOf('.', dollarIndex);
                String baseName = fullName.substring(lastDotIndex + 1, dollarIndex);
                return baseName + "-anonymous";
            }
            
            // 부모 클래스를 찾을 수 없는 경우
            return "anonymous-task";
        }
        
        return className;
    }
    
    /*
    private static String extractTaskName(Callable<?> task) {
        Class<?> clazz = task.getClass();
        String className = clazz.getSimpleName();
        
        if (className.isEmpty() || className.contains("$")) {
            String fullName = clazz.getName();
            int dollarIndex = fullName.indexOf('$');
            
            if (dollarIndex > 0) {
                int lastDotIndex = fullName.lastIndexOf('.', dollarIndex);
                String baseName = fullName.substring(lastDotIndex + 1, dollarIndex);
                return baseName + "-callable";
            }
            
            return "anonymous-callable";
        }
        
        return className;
    }
    */
    
    
    
    // Private constructor
    private AsyncExecutor() {}
}