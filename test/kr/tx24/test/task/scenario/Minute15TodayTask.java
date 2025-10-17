package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Minute15TodayTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "minute15_today",
    time = "00:00",
    period = "15m",
    startDay = "20251017",
    enabled = true,
    desc = "Every 15 minutes today / 15분마다 당일 시작",
    priority = 10
)
public class Minute15TodayTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Minute15TodayTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 10-1] 15 minutes interval today task executed");
    }
}
