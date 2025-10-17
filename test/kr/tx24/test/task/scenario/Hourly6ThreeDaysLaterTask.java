package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Hourly6ThreeDaysLaterTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "hourly6_3days_later",
    time = "00:00",
    period = "6h",
    startDay = "20251020",
    enabled = true,
    desc = "3 days later, every 6 hours / 3일 후 6시간마다",
    priority = 59
)
public class Hourly6ThreeDaysLaterTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Hourly6ThreeDaysLaterTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 5-2] 6 hours interval 3 days later task executed");
    }
}
