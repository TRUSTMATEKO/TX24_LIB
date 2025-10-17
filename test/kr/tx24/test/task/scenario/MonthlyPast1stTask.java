package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: MonthlyPast1stTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "monthly_past_1st",
    time = "09:00",
    period = "M",
    startDay = "20240101",
    enabled = true,
    desc = "Monthly on 1st, past start / 매월 1일, 과거 시작",
    priority = 40
)
public class MonthlyPast1stTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MonthlyPast1stTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 7-1] Monthly 1st past start task executed");
    }
}
