package kr.tx24.test.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

//예시 9: 오류 처리 예시
@Task(
 name = "errorProneTask",
 time = "12:00",
 period = "1d",
 enabled = false, // 테스트를 위해 비활성화
 desc = "Task that demonstrates error handling / 오류 처리 시연 Task",
 priority = 20
)
public class ErrorPhoneTask implements Runnable {
 private static final Logger logger = LoggerFactory.getLogger(ErrorPhoneTask.class);
 
 @Override
 public void run() {
     logger.info("Executing ErrorProneTask / ErrorProneTask 실행");
     
     // 의도적으로 예외 발생
     throw new RuntimeException("Simulated error / 시뮬레이션된 오류");
 }
}
