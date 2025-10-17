package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: WeekdayMWFPastStartTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "weekday_mwf_past_start",
    time = "14:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY},
    startDay = "20240901",
    enabled = true,
    desc = "Mon/Wed/Fri with past start / 월수금 과거 시작일",
    priority = 89
)
public class WeekdayMWFPastStartTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WeekdayMWFPastStartTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 2-2] Mon/Wed/Fri past start task executed");
    }
}
