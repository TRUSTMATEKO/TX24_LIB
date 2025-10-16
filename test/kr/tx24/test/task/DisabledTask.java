package kr.tx24.test.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

//예시 8: 비활성화된 Task
@Task(
 name = "disabledTask",
 time = "00:00",
 period = "1h",
 enabled = false,
 desc = "Disabled task example / 비활성화된 Task 예시",
 priority = 30
)
public class DisabledTask implements Runnable {
 private static final Logger logger = LoggerFactory.getLogger(DisabledTask.class);
 
 @Override
 public void run() {
     logger.info("This task should not run / 이 Task는 실행되면 안 됨");
 }
}