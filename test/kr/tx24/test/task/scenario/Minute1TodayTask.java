package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Minute15TodayTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "minute1_today",
    time = "00:00",
    period = "1m",
    startDay = "20251017",
    enabled = true,
    desc = "Every 1 minutes today / 1분마다 당일 시작",
    priority = 10
)
public class Minute1TodayTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Minute1TodayTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 10-1] 1 minutes interval today task executed");
    }
}
