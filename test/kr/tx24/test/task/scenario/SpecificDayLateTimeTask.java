package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: SpecificDayLateTimeTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "specific_day_late_time",
    time = "23:30",
    period = "1d",
    daysOfWeek = {DayOfWeek.TUESDAY, DayOfWeek.THURSDAY},
    startDay = "20251018",
    enabled = true,
    desc = "Tue/Thu late time / 화목 늦은 시간",
    priority = 19
)
public class SpecificDayLateTimeTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SpecificDayLateTimeTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 9-2] Specific day late time task executed");
    }
}
