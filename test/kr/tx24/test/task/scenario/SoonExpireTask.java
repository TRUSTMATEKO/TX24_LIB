package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: SoonExpireTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "soon_expire",
    time = "15:00",
    period = "1d",
    startDay = "20251015",
    endDay = "20251020",
    enabled = true,
    desc = "Soon to expire task / 곧 종료될 Task",
    priority = 4
)
public class SoonExpireTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(SoonExpireTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 11-2] Soon expire task executed");
    }
}
