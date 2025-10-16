package kr.tx24.test.task;

import java.time.DayOfWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

//예시 10: 복잡한 비즈니스 로직
@Task(
 name = "complexBusinessTask",
 time = "23:30",
 period = "1d",
 daysOfWeek = {DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY},
 startDay = "20250101",
 enabled = true,
 desc = "Complex business logic task / 복잡한 비즈니스 로직 Task",
 priority = 10
)
public class ComplexBusinessTask implements Runnable {
 private static final Logger logger = LoggerFactory.getLogger(ComplexBusinessTask.class);
 
 @Override
 public void run() {
     logger.info("Executing ComplexBusinessTask / ComplexBusinessTask 실행");
     logger.info("Processing complex business logic / 복잡한 비즈니스 로직 처리 중...");
     
     try {
         // Step 1
         logger.info("Step 1: Data validation / 1단계: 데이터 검증");
         Thread.sleep(1000);
         
         // Step 2
         logger.info("Step 2: Business rules application / 2단계: 비즈니스 규칙 적용");
         Thread.sleep(1000);
         
         // Step 3
         logger.info("Step 3: Result generation / 3단계: 결과 생성");
         Thread.sleep(1000);
         
         logger.info("Complex business task completed / 복잡한 비즈니스 Task 완료");
         
     } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         logger.warn("Task interrupted / Task 중단됨");
     } catch (Exception e) {
         logger.error("Business logic error / 비즈니스 로직 오류", e);
         throw e; // 상위로 전파하여 TaskScheduler에서 처리
     }
 }
}