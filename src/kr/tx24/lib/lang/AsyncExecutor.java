package kr.tx24.lib.lang;

import java.util.concurrent.Callable;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AsyncExecutor {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncExecutor.class);
    
    // Lazy initialization holder pattern (thread-safe singleton)
    private static class Holder {
        private static final ThreadPoolExecutor INSTANCE = createExecutor();
    }
    
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private static final AtomicInteger taskCounter = new AtomicInteger(0);
    
    // ========== Executor 생성 ==========
    
    /**
     * ThreadPoolExecutor 생성
     * 
     * 설정값:
     * - Core Pool Size: CPU 코어 수
     * - Max Pool Size: CPU 코어 수 * 2
     * - Keep-Alive Time: 60초 (idle 스레드 유지 시간)
     * - Queue: LinkedBlockingQueue (최대 1000개)
     * - Rejection Policy: CallerRunsPolicy (큐가 가득 차면 호출 스레드에서 실행)
     */
    private static ThreadPoolExecutor createExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = corePoolSize * 2;
        
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
        
        return executor;
    }
    
    // ========== Public API ==========
    
    /**
     * ExecutorService 반환
     * 
     * @return ExecutorService
     * @throws IllegalStateException shutdown된 경우
     */
    public static ExecutorService getExecutor() {
        checkShutdown();
        return Holder.INSTANCE;
    }
    
    /**
     * Runnable 작업 실행
     * 
     * @param task 실행할 작업
     * @return Future
     */
    public static Future<?> execute(Runnable task) {
        checkShutdown();
        taskCounter.incrementAndGet();
        return Holder.INSTANCE.submit(() -> {
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
        taskCounter.incrementAndGet();
        return Holder.INSTANCE.submit(() -> {
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
        ScheduledExecutorService scheduler = getScheduler();
        taskCounter.incrementAndGet();
        return scheduler.schedule(() -> {
            try {
                task.run();
            } finally {
                taskCounter.decrementAndGet();
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
        return getScheduler().scheduleAtFixedRate(task, initialDelay, period, unit);
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
        return getScheduler().scheduleWithFixedDelay(task, initialDelay, delay, unit);
    }
    
    // ========== 상태 조회 ==========
    
    /**
     * 현재 활성 작업 수
     */
    public static int getActiveCount() {
        if (isShutdown.get()) return 0;
        return Holder.INSTANCE.getActiveCount();
    }
    
    /**
     * 현재 풀 크기
     */
    public static int getPoolSize() {
        if (isShutdown.get()) return 0;
        return Holder.INSTANCE.getPoolSize();
    }
    
    /**
     * 큐 대기 작업 수
     */
    public static int getQueueSize() {
        if (isShutdown.get()) return 0;
        return Holder.INSTANCE.getQueue().size();
    }
    
    /**
     * 완료된 작업 수
     */
    public static long getCompletedTaskCount() {
        if (isShutdown.get()) return 0;
        return Holder.INSTANCE.getCompletedTaskCount();
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
        
        ThreadPoolExecutor executor = Holder.INSTANCE;
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
        
        ThreadPoolExecutor executor = Holder.INSTANCE;
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
    
    // ========== Shutdown ==========
    
    /**
     * Graceful shutdown
     * 
     * 1. 새 작업 거부
     * 2. 기존 작업 완료 대기 (최대 30초)
     * 3. 타임아웃 시 강제 종료
     * 4. 추가 10초 대기
     */
    public static void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            logger.debug("AsyncExecutor already shutdown");
            return;
        }
        
        logger.info("========== AsyncExecutor Shutdown Started ==========");
        
        // Main executor shutdown
        shutdownExecutor(Holder.INSTANCE, "Main");
        
        // Scheduler shutdown
        if (SchedulerHolder.INSTANCE != null) {
            shutdownScheduler(SchedulerHolder.INSTANCE);
        }
        
        logger.info("========== AsyncExecutor Shutdown Completed ==========");
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
                logger.info("{} executor shutdown successfully", name);
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
    
    private static class SchedulerHolder {
        private static final ScheduledExecutorService INSTANCE = createScheduler();
    }
    
    private static ScheduledExecutorService createScheduler() {
        logger.info("Creating scheduler...");
        return Executors.newScheduledThreadPool(
            2, 
            new CustomThreadFactory("scheduler")
        );
    }
    
    private static ScheduledExecutorService getScheduler() {
        checkShutdown();
        return SchedulerHolder.INSTANCE;
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
    
    public static class ExecutorStats {
        private final boolean isShutdown;
        private final int activeCount;
        private final int poolSize;
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int queueSize;
        private final long completedTaskCount;
        private final int totalTaskCount;
        
        ExecutorStats(boolean isShutdown) {
            this.isShutdown = isShutdown;
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
            return String.format(
                "ExecutorStats{active=%d, pool=%d/%d/%d, queue=%d, completed=%d, total=%d}",
                activeCount, poolSize, corePoolSize, maxPoolSize, 
                queueSize, completedTaskCount, totalTaskCount
            );
        }
    }
    
    // Private constructor
    private AsyncExecutor() {}
}