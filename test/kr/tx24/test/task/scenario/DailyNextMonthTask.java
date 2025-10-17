package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: DailyNextMonthTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "daily_next_month",
    time = "11:00",
    period = "1d",
    startDay = "20251101",
    endDay = "20251130",
    enabled = true,
    desc = "Next month start / 다음 달 시작",
    priority = 50
)
public class DailyNextMonthTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DailyNextMonthTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 6-1] Next month start task executed");
    }
}
