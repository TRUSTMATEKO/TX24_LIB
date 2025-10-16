package kr.tx24.test.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

//예시 5: 월간 작업 (매월 startDay 기준 같은 일자)
@Task(
 name = "monthlySettlement",
 time = "09:00",
 period = "M",
 startDay = "20250101",
 endDay = "20251231",
 enabled = true,
 desc = "Monthly settlement - runs on 1st of every month / 월간 정산 - 매월 1일 실행",
 priority = 60
)
public class MonthlyTask implements Runnable {
 private static final Logger logger = LoggerFactory.getLogger(MonthlyTask.class);
 
 @Override
 public void run() {
     logger.info("Executing MonthlySettlementTask");
     logger.info("Processing monthly settlement");
     
     try {
         // 정산 로직
         Thread.sleep(5000);
         
         logger.info("Monthly settlement completed");
     } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         logger.warn("Task interrupted / Task 중단됨");
     }
 }
}
