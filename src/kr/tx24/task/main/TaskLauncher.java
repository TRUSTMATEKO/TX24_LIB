package kr.tx24.task.main;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.task.config.TaskConfig;

/**
 * Task 런처 - 애플리케이션 시작점
 * 
 * 실행 방법:
 * java -jar task-launcher.jar
 * 
 * 시스템 속성:
 * -DbasePackage=kr.tx24.task  (스캔할 패키지)
 */
public class TaskLauncher {
    private static final Logger logger = LoggerFactory.getLogger(TaskLauncher.class);
    
    private static TaskScheduler scheduler;
    
    public static void main(String[] args) {
        
    	logger.info("Task Scheduler System");
        
        try {
        	
        	// 설정 로드, basePackage 가 미지정된 경우는 기본적으로 kr.tx24 전체를 확인한다.
            String basePackage = System.getProperty("basePackage", SystemUtils.getPackagePrefix(2));
            
            // 1. Task 스캔
            List<TaskConfig> taskLists = TaskScanner.scanTasks(basePackage);
            
            if (taskLists.isEmpty()) {
            	logger.info("Task를 찾을 수 없습니다! 패키지 경로와 @Task annotation을 확인하세요");
                logger.info("-DbasePakckage={}",basePackage);
                return;
            }
            
            
            // 2. 스케줄러 생성 및 시작
            scheduler = new TaskScheduler(taskLists, "Asia/Seoul");
            scheduler.start();
            
            
            
            // 3. Shutdown Hook 등록
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {            
                try {
                    // 1. 스케줄러 중지
                    if (scheduler != null) {
                        scheduler.cancelAll();
                    }
                    
                    //logger shutdown 과 중복일수 있으나 안전한 종료를 위하여 
                    AsyncExecutor.shutdown();

                } catch (Exception e) {
                    //logger.error("Error during shutdown / 종료 중 오류 발생", e);
                }
            }, "ShutdownHook-TaskLauncher"));
            
            
            
            
            // 4. 메인 스레드 유지
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            logger.warn("Main thread interrupted / 메인 스레드 인터럽트", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.warn("Fatal error in TaskLauncher / TaskLauncher에서 치명적 오류 발생", e);
            System.exit(1);
        }
    }
    


    

}
