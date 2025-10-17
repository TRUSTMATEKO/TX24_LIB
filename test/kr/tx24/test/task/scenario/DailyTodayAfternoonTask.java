package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: DailyTodayAfternoonTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "daily_today_afternoon",
    time = "15:00",
    period = "1d",
    startDay = "20251017",
    enabled = true,
    desc = "Today start at 15:00 / 당일 오후 3시 시작",
    priority = 80
)
public class DailyTodayAfternoonTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DailyTodayAfternoonTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 3-1] Today afternoon start task executed");
    }
}
