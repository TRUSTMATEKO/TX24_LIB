package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Weekend2MonthsLaterTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "weekend_2months_later",
    time = "10:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.SATURDAY, DayOfWeek.SUNDAY},
    startDay = "20251201",
    endDay = "20251231",
    enabled = true,
    desc = "2 months later, weekend only / 2개월 후 주말만",
    priority = 49
)
public class Weekend2MonthsLaterTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Weekend2MonthsLaterTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 6-2] Weekend 2 months later task executed");
    }
}
