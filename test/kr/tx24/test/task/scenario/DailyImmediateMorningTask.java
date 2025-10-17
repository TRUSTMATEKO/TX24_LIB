package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: DailyImmediateMorningTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "daily_immediate_morning",
    time = "09:00",
    period = "1d",
    enabled = true,
    desc = "Daily immediate start at 09:00 / 매일 즉시 시작 (09시)",
    priority = 100
)
public class DailyImmediateMorningTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DailyImmediateMorningTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 1-1] Daily immediate morning task executed");
    }
}
