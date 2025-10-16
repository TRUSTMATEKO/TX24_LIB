package kr.tx24.test.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

//예시 6: 특정 기간 한정 (시즌 프로모션)
@Task(
 name = "seasonalPromotion",
 time = "10:00",
 period = "1d",
 startDay = "20251201",
 endDay = "20251231",
 enabled = true,
 desc = "December seasonal promotion / 12월 시즌 프로모션",
 priority = 50
)
public class SeasonalPromotionTask implements Runnable {
 private static final Logger logger = LoggerFactory.getLogger(SeasonalPromotionTask.class);
 
 @Override
 public void run() {
     logger.info("Executing SeasonalPromotionTask / SeasonalPromotionTask 실행");
     logger.info("Running seasonal promotion / 시즌 프로모션 실행 중...");
     
     try {
         // 프로모션 로직
         Thread.sleep(1500);
         
         logger.info("Seasonal promotion completed / 시즌 프로모션 완료");
     } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         logger.warn("Task interrupted / Task 중단됨");
     }
 }
}