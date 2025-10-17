package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: WeekendEarlyFutureTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "weekend_early_future",
    time = "06:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.SATURDAY, DayOfWeek.SUNDAY},
    startDay = "20251025",
    enabled = true,
    desc = "Weekend early morning future / 주말 이른 시간 미래 시작",
    priority = 18
)
public class WeekendEarlyFutureTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(WeekendEarlyFutureTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 9-3] Weekend early future task executed");
    }
}
