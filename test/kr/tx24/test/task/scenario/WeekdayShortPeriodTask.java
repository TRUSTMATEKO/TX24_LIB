package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: WeekdayShortPeriodTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "weekday_short_period",
    time = "12:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
                  DayOfWeek.THURSDAY, DayOfWeek.FRIDAY},
    startDay = "20251020",
    endDay = "20251031",
    enabled = true,
    desc = "Weekday short period / 평일 짧은 기간",
    priority = 20
)
public class WeekdayShortPeriodTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WeekdayShortPeriodTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 9-1] Weekday short period task executed");
    }
}
