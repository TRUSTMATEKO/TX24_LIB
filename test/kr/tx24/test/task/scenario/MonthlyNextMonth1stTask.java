package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: MonthlyNextMonth1stTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "monthly_next_month_1st",
    time = "10:00",
    period = "M",
    startDay = "20251101",
    endDay = "20261231",
    enabled = true,
    desc = "Monthly next month 1st / 다음 달부터 매월 1일",
    priority = 30
)
public class MonthlyNextMonth1stTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MonthlyNextMonth1stTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 8-1] Monthly next month 1st task executed");
    }
}
