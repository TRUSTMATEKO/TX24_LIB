package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: DailyPastStartTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "daily_past_start",
    time = "11:00",
    period = "1d",
    startDay = "20241001",
    endDay = "20991231",
    enabled = true,
    desc = "Daily with past start date / 과거 시작일 매일 실행",
    priority = 90
)
public class DailyPastStartTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DailyPastStartTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 2-1] Daily past start task executed");
    }
}
