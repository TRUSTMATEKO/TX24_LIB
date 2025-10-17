package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: DailyNextYearTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "daily_next_year",
    time = "09:00",
    period = "1d",
    startDay = "20260101",
    enabled = true,
    desc = "Next year start / 내년 시작",
    priority = 48
)
public class DailyNextYearTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DailyNextYearTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 6-3] Next year start task executed");
    }
}
