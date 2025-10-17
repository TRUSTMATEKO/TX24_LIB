package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Hourly2ImmediateTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "hourly2_immediate",
    time = "00:00",
    period = "2h",
    enabled = true,
    desc = "Every 2 hours immediate start / 2시간마다 즉시 시작",
    priority = 99
)
public class Hourly2ImmediateTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Hourly2ImmediateTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 1-2] Hourly(2h) immediate task executed");
    }
}
