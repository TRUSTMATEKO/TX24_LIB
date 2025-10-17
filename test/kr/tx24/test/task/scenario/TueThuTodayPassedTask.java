package kr.tx24.test.task.scenario;

import java.time.DayOfWeek;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: TueThuTodayPassedTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "tue_thu_today_passed",
    time = "09:00",
    period = "1d",
    daysOfWeek = {DayOfWeek.TUESDAY, DayOfWeek.THURSDAY},
    startDay = "20251017",
    enabled = true,
    desc = "Tue/Thu today start but time passed / 화목 당일 시간 지남",
    priority = 69
)
public class TueThuTodayPassedTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TueThuTodayPassedTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 4-2] Tue/Thu today passed task executed");
    }
}
