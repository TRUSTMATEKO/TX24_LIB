package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: MondayOneWeekLaterTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "monday_1week_later",
    time = "09:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.MONDAY},
    startDay = "20251024",
    enabled = true,
    desc = "1 week later, Monday only / 1주일 후 월요일만",
    priority = 58
)
public class MondayOneWeekLaterTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(MondayOneWeekLaterTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 5-3] Monday 1 week later task executed");
    }
}
