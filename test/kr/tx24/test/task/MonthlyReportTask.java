package kr.tx24.test.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

//예시 5-2: 월간 작업 (매월 15일 실행)
@Task(
 name = "monthlyReport",
 time = "14:00",
 period = "M",
 startDay = "20250115",
 enabled = true,
 desc = "Monthly report on 15th / 매월 15일 월간 리포트",
 priority = 55
)
public class MonthlyReportTask implements Runnable {
 private static final Logger logger = LoggerFactory.getLogger(MonthlyReportTask.class);
 
 @Override
 public void run() {
     logger.info("Executing MonthlyReportTask / MonthlyReportTask 실행");
     logger.info("Generating monthly report / 월간 리포트 생성 중...");
     
     try {
         // 리포트 생성
         Thread.sleep(3000);
         
         logger.info("Monthly report completed / 월간 리포트 완료");
     } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         logger.warn("Task interrupted / Task 중단됨");
     }
 }
}