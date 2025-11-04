package kr.tx24.test.task.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import kr.tx24.task.annotation.Task;

/**
 * Scenario Task: Minute30PastStartTask
 * Generated for Task Scheduler testing
 */
@Task(
	    name = "minute1_today333333",
	    time = "00:00",
	    period = "1m",
	    startDay = "20251017",
	    enabled = true,
	    desc = "Every 1 minutes today / 1분마다 당일 시작",
	    priority = 5
	)
public class Minute30PastStartTask implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(Minute30PastStartTask.class);
    
    @Override
    public void run() {
        logger.info("[SCENARIO 2-3] 30 minutes interval past start task executed");
    }
}
