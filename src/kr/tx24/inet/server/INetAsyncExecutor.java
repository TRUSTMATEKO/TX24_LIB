package kr.tx24.inet.server;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class INetAsyncExecutor {

private static final Logger logger = LoggerFactory.getLogger(INetAsyncExecutor.class);
    
    
    private static class Holder {
        private static final ExecutorService INSTANCE = createExecutorService();
    }
    
    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    private static ExecutorService createExecutorService() {
        int coreSize = Runtime.getRuntime().availableProcessors();
        int maxSize = coreSize * 2;
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            coreSize,                           // core pool size
            maxSize,                            // maximum pool size
            60L, TimeUnit.SECONDS,              // keep-alive time
            new LinkedBlockingQueue<>(1000),    // work queue
            new ThreadFactory() {
                private int threadCount = 0;
                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r);
                    thread.setName("async-executor-" + (++threadCount));
                    thread.setDaemon(false);    // non-daemon thread
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy
        );
        
        logger.info("AsyncExecutor created: coreSize={}, maxSize={}", coreSize, maxSize);
        return executor;
    }
    
    /**
     * ExecutorService 반환
     */
    public static ExecutorService getExecutor() {
        if (isShutdown.get()) {
            throw new IllegalStateException("AsyncExecutor has been shutdown");
        }
        return Holder.INSTANCE;
    }
    
    /**
     * Graceful shutdown
     * INetServer.shutdown()에서 호출됨
     */
    public static void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            logger.debug("AsyncExecutor already shutdown");
            return;
        }
        
        ExecutorService executor = Holder.INSTANCE;
        logger.info("Shutting down AsyncExecutor...");
        
        executor.shutdown(); // 새 작업 거부, 기존 작업 완료 대기
        
        try {
            // 30초 대기
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warn("AsyncExecutor did not terminate in 30s, forcing shutdown...");
                
                // 강제 종료
                executor.shutdownNow();
                
                // 추가 10초 대기
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("AsyncExecutor did not terminate after forced shutdown");
                }
            }
            
            logger.info("AsyncExecutor shutdown completed");
            
        } catch (InterruptedException e) {
            logger.error("AsyncExecutor shutdown interrupted", e);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 현재 상태 정보
     */
    public static String getStatus() {
        if (isShutdown.get()) {
            return "SHUTDOWN";
        }
        
        ExecutorService executor = Holder.INSTANCE;
        if (executor instanceof ThreadPoolExecutor tpe) {
            return String.format(
                "Active: %d, Pool: %d, Queue: %d, Completed: %d",
                tpe.getActiveCount(),
                tpe.getPoolSize(),
                tpe.getQueue().size(),
                tpe.getCompletedTaskCount()
            );
        }
        
        return "RUNNING";
    }
    
    private INetAsyncExecutor() {}
}
