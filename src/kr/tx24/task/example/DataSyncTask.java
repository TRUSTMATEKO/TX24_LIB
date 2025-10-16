package kr.tx24.task.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

//예시 7: 매 2시간마다 실행
@Task(
 name = "dataSync",
 time = "00:00",
 period = "2h",
 enabled = true,
 desc = "Data synchronization / 데이터 동기화",
 priority = 40
)
public class DataSyncTask implements Runnable {
 private static final Logger logger = LoggerFactory.getLogger(DataSyncTask.class);
 
 @Override
 public void run() {
     logger.info("Executing DataSyncTask / DataSyncTask 실행");
     logger.info("Synchronizing data / 데이터 동기화 중...");
     
     try {
         // 동기화 로직
         Thread.sleep(500);
         
         logger.info("Data sync completed / 데이터 동기화 완료");
     } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         logger.warn("Task interrupted / Task 중단됨");
     }
 }
}
