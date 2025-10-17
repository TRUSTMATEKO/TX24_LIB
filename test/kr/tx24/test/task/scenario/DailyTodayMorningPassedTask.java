package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: DailyTodayMorningPassedTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "daily_today_morning_passed",
    time = "08:00",
    period = "1d",
    startDay = "20251017",
    enabled = true,
    desc = "Today start but time passed (08:00) / 당일이지만 시간 지남",
    priority = 70
)
public class DailyTodayMorningPassedTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(DailyTodayMorningPassedTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 4-1] Today morning passed task executed");
    }
}
