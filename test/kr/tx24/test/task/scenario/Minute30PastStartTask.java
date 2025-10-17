package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Minute30PastStartTask
 * Generated for Task Scheduler testing
 */
@Task(
    name = "minute30_past_start",
    time = "00:00",
    period = "30m",
    startDay = "20241015",
    enabled = true,
    desc = "Every 30 minutes with past start / 30분마다 과거 시작일",
    priority = 88
)
public class Minute30PastStartTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Minute30PastStartTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 2-3] 30 minutes interval past start task executed");
    }
}
