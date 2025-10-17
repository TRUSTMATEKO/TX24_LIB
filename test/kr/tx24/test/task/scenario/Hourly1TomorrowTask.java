package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Hourly1TomorrowTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "hourly1_tomorrow",
    time = "00:00",
    period = "1h",
    startDay = "20251018",
    enabled = true,
    desc = "Every 1 hour tomorrow / 1시간마다 내일 시작",
    priority = 9
)
public class Hourly1TomorrowTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Hourly1TomorrowTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 10-2] 1 hour interval tomorrow task executed");
    }
}
