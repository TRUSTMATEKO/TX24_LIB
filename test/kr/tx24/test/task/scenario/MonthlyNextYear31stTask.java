package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: MonthlyNextYear31stTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "monthly_next_year_31st",
    time = "23:00",
    period = "M",
    startDay = "20260131",
    enabled = true,
    desc = "Monthly next year 31st / 내년부터 매월 31일",
    priority = 29
)
public class MonthlyNextYear31stTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MonthlyNextYear31stTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 8-2] Monthly next year 31st task executed");
    }
}
