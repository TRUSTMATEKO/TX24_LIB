package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: MonthlyPast15thTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "monthly_past_15th",
    time = "14:00",
    period = "M",
    startDay = "20240115",
    enabled = true,
    desc = "Monthly on 15th, past start / 매월 15일, 과거 시작",
    priority = 39
)
public class MonthlyPast15thTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MonthlyPast15thTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 7-2] Monthly 15th past start task executed");
    }
}
