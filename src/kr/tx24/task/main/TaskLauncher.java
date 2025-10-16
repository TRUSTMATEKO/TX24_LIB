package kr.tx24.task.main;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.AsyncExecutor;
import kr.tx24.task.config.TaskConfig;

/**
 * Task 런처 - 애플리케이션 시작점
 * 
 * 실행 방법:
 * java -jar task-launcher.jar
 * 
 * 시스템 속성:
 * -Dtask.base.package=kr.tx24.task  (스캔할 패키지)
 * -Dtask.timezone=Asia/Seoul        (시간대)
 */
public class TaskLauncher {
    private static final Logger logger = LoggerFactory.getLogger(TaskLauncher.class);
    
    private static TaskScheduler scheduler;
    
    public static void main(String[] args) {
        // 시작 배너 출력
        printBanner();
        
        try {
            // 설정 로드
            String basePackage = System.getProperty("task.base.package", "kr.tx24.test");
            String timezone = System.getProperty("task.timezone", "Asia/Seoul");
            
            logger.info("╔════════════════════════════════════════════════════════════════╗");
            logger.info("║          Task Launcher Starting / Task Launcher 시작           ║");
            logger.info("╚════════════════════════════════════════════════════════════════╝");
            logger.info("Base Package / 기본 패키지: {}", basePackage);
            logger.info("Timezone / 시간대: {}", timezone);
            logger.info("Java Version / Java 버전: {}", System.getProperty("java.version"));
            logger.info("════════════════════════════════════════════════════════════════\n");
            
            // 1. Task 스캔
            List<TaskConfig> taskConfigs = TaskScanner.scanTasks(basePackage);
            
            if (taskConfigs.isEmpty()) {
                logger.warn("═══════════════════════════════════════════════════════════════");
                logger.warn("No tasks found! Please check your base package and @Task annotations");
                logger.warn("Task를 찾을 수 없습니다! 패키지 경로와 @Task annotation을 확인하세요");
                logger.warn("═══════════════════════════════════════════════════════════════");
                return;
            }
            
            // 2. 스케줄러 생성 및 시작
            scheduler = new TaskScheduler(taskConfigs, timezone);
            scheduler.start();
            
            // 3. Shutdown Hook 등록
            registerShutdownHook();
            
            logger.info("╔════════════════════════════════════════════════════════════════╗");
            logger.info("║    Task Launcher Running / Task Launcher 실행 중              ║");
            logger.info("║    Press Ctrl+C to stop / 종료하려면 Ctrl+C를 누르세요        ║");
            logger.info("╚════════════════════════════════════════════════════════════════╝\n");
            
            // 4. AsyncExecutor 상태 모니터링 (선택사항)
            startMonitoring();
            
            // 5. 메인 스레드 유지
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            logger.error("Main thread interrupted / 메인 스레드 인터럽트", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Fatal error in TaskLauncher / TaskLauncher에서 치명적 오류 발생", e);
            System.exit(1);
        }
    }
    
    /**
     * 시작 배너 출력
     */
    private static void printBanner() {
        logger.info("\n");
        logger.info("  ╔════════════════════════════════════════════════════════╗");
        logger.info("  ║                                                        ║");
        logger.info("  ║              Task Scheduler System                     ║");
        logger.info("  ║          스케줄링 기반 Task 실행 시스템                 ║");
        logger.info("  ║                                                        ║");
        logger.info("  ║                  Version 1.0.0                         ║");
        logger.info("  ║                                                        ║");
        logger.info("  ╚════════════════════════════════════════════════════════╝");
        logger.info("\n");
    }
    
    /**
     * Graceful Shutdown Hook 등록
     */
    private static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("\n");
            logger.info("╔════════════════════════════════════════════════════════════════╗");
            logger.info("║       Shutdown Signal Received / 종료 신호 수신               ║");
            logger.info("╚════════════════════════════════════════════════════════════════╝");
            
            try {
                // 1. 스케줄러 중지
                if (scheduler != null) {
                    logger.info("Stopping scheduler / 스케줄러 중지 중...");
                    scheduler.cancelAll();
                }
                
                // 2. AsyncExecutor 종료
                logger.info("Shutting down AsyncExecutor / AsyncExecutor 종료 중...");
                AsyncExecutor.shutdown();
                
                logger.info("╔════════════════════════════════════════════════════════════════╗");
                logger.info("║         Shutdown Completed / 종료 완료                        ║");
                logger.info("╚════════════════════════════════════════════════════════════════╝\n");
                
            } catch (Exception e) {
                logger.error("Error during shutdown / 종료 중 오류 발생", e);
            }
        }, "shutdown-hook"));
        
        logger.info("Shutdown hook registered / Shutdown hook 등록 완료");
    }
    
    /**
     * AsyncExecutor 상태 모니터링 (10분마다)
     */
    private static void startMonitoring() {
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(600_000); // 10분
                    
                    AsyncExecutor.ExecutorStats stats = AsyncExecutor.getStats();
                    
                    if (!stats.isShutdown()) {
                        logger.info("═══ AsyncExecutor Status / 상태 ═══");
                        logger.info("  Active / 활성: {}", stats.getActiveCount());
                        logger.info("  Pool Size / 풀 크기: {}/{}", stats.getPoolSize(), stats.getMaxPoolSize());
                        logger.info("  Queue / 대기: {}", stats.getQueueSize());
                        logger.info("  Completed / 완료: {}", stats.getCompletedTaskCount());
                        logger.info("═══════════════════════════════════\n");
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "monitor-thread");
        
        monitorThread.setDaemon(true);
        monitorThread.start();
    }
}
