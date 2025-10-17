package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: DailyTomorrowStartTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "daily_tomorrow_start",
    time = "10:00",
    period = "1d",
    startDay = "20251018",
    enabled = true,
    desc = "Tomorrow start at 10:00 / 내일 10시 시작",
    priority = 60
)
public class DailyTomorrowStartTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DailyTomorrowStartTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 5-1] Tomorrow start task executed");
    }
}
