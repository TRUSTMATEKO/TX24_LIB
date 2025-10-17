package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: WeekendTodayEveningTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "weekend_today_evening",
    time = "20:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.SATURDAY, DayOfWeek.SUNDAY},
    startDay = "20251017",
    enabled = true,
    desc = "Weekend today start at 20:00 / 당일 저녁 주말만",
    priority = 79
)
public class WeekendTodayEveningTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WeekendTodayEveningTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 3-2] Weekend today evening start task executed");
    }
}
