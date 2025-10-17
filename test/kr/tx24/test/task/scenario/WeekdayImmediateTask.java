package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: WeekdayImmediateTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "weekday_immediate",
    time = "10:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
                  DayOfWeek.THURSDAY, DayOfWeek.FRIDAY},
    enabled = true,
    desc = "Weekday only immediate start / 평일만 즉시 시작",
    priority = 98
)
public class WeekdayImmediateTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WeekdayImmediateTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 1-3] Weekday immediate task executed");
    }
}
