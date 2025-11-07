package kr.tx24.task.main;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.task.annotation.Task.ScheduleType;
import kr.tx24.task.config.TaskConfig;

/**
 * Task 스케줄링 및 실행 관리자
 */
public class TaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);
    
    private static final String LOG_FORMAT = "Scheduled Task\n"
			+ " - Name        : {}\n"
			+ " - Class       : {}\n"
			+ " - Schedule    : {}\n"
			+ " - Type        : {}\n"
			+ " - Days of week: {}\n"
			+ " - Start day   : {}\n"
			+ " - End day     : {}\n"
			+ " - Time        : {}\n"
			+ " - First run   : {}\n"
			+ " - Next run    : {}\n"
			+ " - Description : {}\n"
			+ " - Status      : {}";
    
    
    private final List<TaskConfig> taskConfigs;
    private final ZoneId zoneId;
    private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();
    private final ScheduledExecutorService metaScheduler = Executors.newSingleThreadScheduledExecutor();
    
    public TaskScheduler(List<TaskConfig> taskConfigs, String timezone) {
        this.taskConfigs = taskConfigs;
        this.zoneId = ZoneId.of(timezone);
        logger.info("TaskScheduler initialized , Timezone : {}", zoneId);
    }
    
    
    
    /**
     * 스케줄러 시작
     */
    public void start() {
        if (taskConfigs.isEmpty()) {
            logger.warn("스케줄할 Task가 없습니다");
            return;
        }
        
        logger.info("Starting Task Scheduler");
        
        // 활성화된 Task만 스케줄링
        taskConfigs.stream()
            .filter(TaskConfig::enabled)
            .forEach(this::scheduleOrPlanTask);
        
        // 통계 출력
        logTaskStatistics();
    }
    
    /**
     * Task를 즉시 스케줄링하거나 미래에 스케줄링하도록 계획
     */
    private void scheduleOrPlanTask(TaskConfig taskConfig) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        // 1. 종료일 확인
        if (taskConfig.endDate() != null && today.isAfter(taskConfig.endDate())) {
            logger.info("Task '{}'는 {}에 종료되었습니다", 
                taskConfig.name(), taskConfig.getEndDateString());
            logTaskDetails(taskConfig, "EXPIRED", null, null, false);
            return;
        }
        
        // 2. 시작일이 미래인 경우
        if (taskConfig.startDate() != null && today.isBefore(taskConfig.startDate())) {
            scheduleFutureTask(taskConfig, today);
            return;
        }
        
        // 3. 시작일 당일이면서 스케줄 시간 전인 경우
        if (taskConfig.startDate() != null && today.isEqual(taskConfig.startDate())) {
            LocalDateTime scheduledDateTime = LocalDateTime.of(today, taskConfig.scheduledTime());
            
            if (now.isBefore(scheduledDateTime)) {
                long delayMinutes = ChronoUnit.MINUTES.between(now, scheduledDateTime);
                logger.info("Task '{}'는 오늘 {}에 시작됩니다 ({}분 후)", 
                    taskConfig.name(), taskConfig.getScheduledTimeString(), delayMinutes);
                scheduleTaskNow(taskConfig);
                return;
            }
            
            logger.info("Task '{}' 시작일 당일이지만 스케줄 시간({})이 지났습니다. 다음 실행 시간으로 스케줄링합니다.",
                taskConfig.name(), taskConfig.getScheduledTimeString());
        }
        
        // 4. 즉시 스케줄링 (현재 유효한 Task 또는 시작일 지난 경우)
        scheduleTaskNow(taskConfig);
    }
    
    /**
     * 미래 Task 예약 스케줄링
     */
    private void scheduleFutureTask(TaskConfig taskConfig, LocalDate today) {
        long daysUntilStart = ChronoUnit.DAYS.between(today, taskConfig.startDate());
        logger.info("Task '{}'는 {}일 후({})에 자동으로 시작됩니다", 
            taskConfig.name(), daysUntilStart, taskConfig.getStartDateString());
        
        LocalDateTime futureFirstRun = LocalDateTime.of(
            taskConfig.startDate(), 
            taskConfig.scheduledTime()
        );
        // 미래 Task는 First run만 표시 (Next run 없음)
        logTaskDetails(taskConfig, "SCHEDULED", futureFirstRun, null, taskConfig.isMonthlyPeriod());
        
        // 시작일시에 자동 스케줄링
        LocalDateTime now = LocalDateTime.now(zoneId);
        long delayMillis = ChronoUnit.MILLIS.between(now, futureFirstRun);
        
        if (delayMillis < 0) {
            logger.warn("Task '{}': 시작일시가 과거입니다. 즉시 스케줄링합니다.", taskConfig.name());
            scheduleTaskNow(taskConfig);
            return;
        }
        
        logger.debug("Task '{}' 예약 스케줄링: {}ms 후 실행 ({})", 
            taskConfig.name(), delayMillis, DateUtils.toString(futureFirstRun, "yyyy-MM-dd HH:mm:ss"));
        
        metaScheduler.schedule(() -> activateFutureTask(taskConfig), delayMillis, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 미래 Task 활성화
     */
    private void activateFutureTask(TaskConfig taskConfig) {
        try {
            logger.info("Task '{}' 시작일 도래 - 스케줄링 시작", taskConfig.name());
            
            Runnable task = taskConfig.taskClass().getDeclaredConstructor().newInstance();
            
            if (taskConfig.isMonthlyPeriod()) {
                scheduleMonthlyTask(taskConfig, task);
            } else {
                scheduleRegularTask(taskConfig, task);
            }
            
        } catch (Exception e) {
            logger.error("미래 Task 스케줄 등록 실패: '{}' [{}]", 
                taskConfig.name(), taskConfig.taskClass().getSimpleName(), e);
        }
    }
    
    /**
     * 현재 유효한 Task 즉시 스케줄링
     */
    private void scheduleTaskNow(TaskConfig taskConfig) {
        try {
            Runnable task = taskConfig.taskClass().getDeclaredConstructor().newInstance();
            
            if (taskConfig.isMonthlyPeriod()) {
                scheduleMonthlyTask(taskConfig, task);
            } else {
                scheduleRegularTask(taskConfig, task);
            }
            
        } catch (Exception e) {
            logger.error("Task 스케줄 등록 실패: '{}' [{}]", 
                taskConfig.name(), taskConfig.taskClass().getSimpleName(), e);
        }
    }
    
    /**
     * 일반 Task 스케줄링 (고정 주기)
     * scheduleType에 따라 Fixed Rate 또는 Fixed Delay 사용
     */
    private void scheduleRegularTask(TaskConfig taskConfig, Runnable task) {
        Runnable wrappedTask = createWrappedTask(taskConfig, task);
        
        LocalDateTime now = LocalDateTime.now(zoneId);
        long initialDelay = calculateInitialDelay(taskConfig, now);
        long period = taskConfig.period().toMillis();
        
        ScheduledFuture<?> future;
        
        // scheduleType에 따라 적절한 스케줄링 메서드 호출
        if (taskConfig.type() == ScheduleType.RATE) {
            future = AsyncExecutor.scheduleAtFixedRate(
                wrappedTask,
                initialDelay,
                period,
                TimeUnit.MILLISECONDS
            );
            logger.debug("Task '{}' scheduled with RATE", taskConfig.name());
        } else {
            future = AsyncExecutor.scheduleWithFixedDelay(
                wrappedTask,
                initialDelay,
                period,
                TimeUnit.MILLISECONDS
            );
            logger.debug("Task '{}' scheduled with DELAY", taskConfig.name());
        }
        
        scheduledFutures.add(future);
        
        // First run과 Next run 구분
        LocalDateTime nextRun = calculateFirstRunTime(taskConfig, now);
        LocalDateTime firstRun = getInitialScheduledTime(taskConfig, now);
        
        logTaskDetails(taskConfig, "ACTIVE", firstRun, nextRun, false);
    }

    /**
     * 월 단위 Task 스케줄링 (일회성 + 재스케줄링)
     */
    private void scheduleMonthlyTask(TaskConfig taskConfig, Runnable task) {
        LocalDateTime now = LocalDateTime.now(zoneId);
        long initialDelay = calculateMonthlyInitialDelay(taskConfig, now);
        
        Runnable wrappedTask = () -> {
            if (taskConfig.isValidDate(LocalDate.now(zoneId))) {
                AsyncExecutor.execute(() -> executeTask(taskConfig, task));
                scheduleMonthlyTask(taskConfig, task); // 재스케줄링
            } else {
                logger.debug("Skipping monthly task '{}' - Outside valid date range", taskConfig.name());
            }
        };
        
        ScheduledFuture<?> future = AsyncExecutor.schedule(wrappedTask, initialDelay, TimeUnit.MILLISECONDS);
        scheduledFutures.add(future);
        
        // ✅ First run과 Next run 구분
        LocalDateTime nextRun = calculateFirstRunTimeMonthly(taskConfig, now);
        LocalDateTime firstRun = getInitialScheduledTimeMonthly(taskConfig);
        
        logTaskDetails(taskConfig, "ACTIVE", firstRun, nextRun, true);
    }
    
    /**
     * 초기 스케줄 시간 계산 (일반 주기)
     * - 설정된 원래 시간을 반환 (시작일 + 스케줄 시간)
     */
    private LocalDateTime getInitialScheduledTime(TaskConfig taskConfig, LocalDateTime now) {
        LocalDate startDate = taskConfig.startDate();
        if (startDate == null) {
            startDate = now.toLocalDate();
        }
        return LocalDateTime.of(startDate, taskConfig.scheduledTime());
    }
    
    /**
     * 초기 스케줄 시간 계산 (월 단위)
     */
    private LocalDateTime getInitialScheduledTimeMonthly(TaskConfig taskConfig) {
        return LocalDateTime.of(taskConfig.startDate(), taskConfig.scheduledTime());
    }
    
    /**
     * Task Wrapper 생성 (날짜/요일 체크 및 비동기 실행)
     */
    private Runnable createWrappedTask(TaskConfig taskConfig, Runnable task) {
        return () -> {
            LocalDate executionDate = LocalDate.now(zoneId);
            DayOfWeek executionDay = executionDate.getDayOfWeek();
            
            if (!taskConfig.isValidDate(executionDate)) {
                logger.debug("Skipping task : '{}' - Outside valid date range", taskConfig.name());
                return;
            }
            
            if (!taskConfig.isValidDayOfWeek(executionDay)) {
                logger.debug("Skipping task : '{}' - Not scheduled for {}", taskConfig.name(), executionDay);
                return;
            }
            
            AsyncExecutor.execute(() -> executeTask(taskConfig, task));
        };
    }
    
    /**
     * Task 실행 및 로깅
     */
    private void executeTask(TaskConfig taskConfig, Runnable task) {
        LocalDateTime startTime = LocalDateTime.now(zoneId);
        
        try {
            LocalDateTime nextRun = calculateNextRunTime(taskConfig, startTime);
            
            task.run();
            
            long duration = ChronoUnit.MILLIS.between(startTime, LocalDateTime.now(zoneId));
            
            logger.info("\n#Task {}\n - complete , Duration :{}ms\n - next scheduled : {}", 
                taskConfig.name(),
                duration,
                DateUtils.toString(nextRun, "yyyy-MM-dd HH:mm:ss"));
                
        } catch (Exception e) {
            logger.warn("Task execution failed : '{}' , Error : {}", 
                taskConfig.name(), e.getMessage(), e);
        }
    }
    
    /**
     * 첫 실행 시간 계산 (밀리초 단위)
     * 주의: startDate보다 이전인지 반드시 확인 필요
     */
    private long calculateInitialDelay(TaskConfig taskConfig, LocalDateTime now) {
        Duration period = taskConfig.period();
        
        // 분/시간/초 단위 주기인 경우 특별 처리
        if (period.toDays() < 1) {
            return calculateShortPeriodInitialDelay(taskConfig, now, period);
        }
        
        // 일 단위 이상 주기 처리
        LocalDateTime scheduledDateTime = now.with(taskConfig.scheduledTime());
        
        // 시작일이 지정되어 있고 아직 도래하지 않았다면 시작일 기준으로 계산
        if (taskConfig.startDate() != null) {
            LocalDate startDate = taskConfig.startDate();
            LocalDate today = now.toLocalDate();
            
            // 시작일이 미래라면 시작일의 scheduledTime
            if (today.isBefore(startDate)) {
                scheduledDateTime = LocalDateTime.of(startDate, taskConfig.scheduledTime());
                return ChronoUnit.MILLIS.between(now, scheduledDateTime);
            }
            
            // 시작일이 오늘이고 스케줄 시간이 지나지 않았다면 오늘의 scheduledTime
            if (today.isEqual(startDate)) {
                LocalDateTime startDateTime = LocalDateTime.of(startDate, taskConfig.scheduledTime());
                if (now.isBefore(startDateTime)) {
                    return ChronoUnit.MILLIS.between(now, startDateTime);
                }
            }
        }
        
        // 오늘 실행 시간이 지났거나 요일이 안 맞으면 다음 유효 시간 찾기
        if (scheduledDateTime.isBefore(now) || scheduledDateTime.isEqual(now) ||
            !taskConfig.isValidDayOfWeek(scheduledDateTime.getDayOfWeek())) {
            scheduledDateTime = findNextValidDateTime(taskConfig, scheduledDateTime);
        }
        
        return ChronoUnit.MILLIS.between(now, scheduledDateTime);
    }
    
    /**
     * 짧은 주기 (초/분/시간) 초기 지연 시간 계산
     * - 현재 시간과 동일하면 주기만큼 더함
     */
    private long calculateShortPeriodInitialDelay(TaskConfig taskConfig, LocalDateTime now, Duration period) {
        LocalDateTime scheduledDateTime = now.with(taskConfig.scheduledTime());
        
        // 시작일이 지정되어 있는 경우
        if (taskConfig.startDate() != null) {
            LocalDate startDate = taskConfig.startDate();
            LocalDate today = now.toLocalDate();
            
            // 시작일이 미래라면 시작일의 scheduledTime
            if (today.isBefore(startDate)) {
                scheduledDateTime = LocalDateTime.of(startDate, taskConfig.scheduledTime());
                return ChronoUnit.MILLIS.between(now, scheduledDateTime);
            }
            
            // 시작일이 오늘이고 스케줄 시간이 지나지 않았다면 오늘의 scheduledTime
            if (today.isEqual(startDate)) {
                LocalDateTime startDateTime = LocalDateTime.of(startDate, taskConfig.scheduledTime());
                if (now.isBefore(startDateTime)) {
                    return ChronoUnit.MILLIS.between(now, startDateTime);
                }
                // 시작일 당일인데 시간이 지났으면 주기만큼 더함
                scheduledDateTime = startDateTime.plus(period);
                return ChronoUnit.MILLIS.between(now, scheduledDateTime);
            }
        }
        
        // 현재 시간과 스케줄 시간이 같거나 지났으면 다음 주기로 설정
        if (scheduledDateTime.isBefore(now) || scheduledDateTime.isEqual(now)) {
            // 주기만큼 더한 시간을 다음 실행 시간으로 설정
            scheduledDateTime = scheduledDateTime.plus(period);
            
            // period 성격에 따라 동일한 초/분/시간 체크하여 즉시 실행 방지
            String periodString = taskConfig.getPeriodString();
            
            if (periodString.endsWith("s (Second)")) {
                // 초 단위 주기: 동일한 초면 +1초 추가 지연
                if (now.getSecond() == scheduledDateTime.getSecond()) {
                    scheduledDateTime = scheduledDateTime.plusSeconds(1);
                    logger.debug("Task '{}': 동일한 초 감지 ({}) - 1초 추가 지연", taskConfig.name(), periodString);
                }
            } else if (periodString.endsWith("m (Minute)")) {
                // 분 단위 주기: 동일한 분이면 +1분 추가 지연
                if (now.getMinute() == scheduledDateTime.getMinute()) {
                    scheduledDateTime = scheduledDateTime.plusMinutes(1);
                    logger.debug("Task '{}': 동일한 분 감지 ({}) - 1분 추가 지연", taskConfig.name(), periodString);
                }
            } else if (periodString.endsWith("h (Hour)")) {
                // 시간 단위 주기: 동일한 시간이면 +1시간 추가 지연
                if (now.getHour() == scheduledDateTime.getHour()) {
                    scheduledDateTime = scheduledDateTime.plusHours(1);
                    logger.debug("Task '{}': 동일한 시간 감지 ({}) - 1시간 추가 지연", taskConfig.name(), periodString);
                }
            }
            return ChronoUnit.MILLIS.between(now, scheduledDateTime);
        }
        
        // 아직 스케줄 시간이 안 됐으면 그 시간까지
        return ChronoUnit.MILLIS.between(now, scheduledDateTime);
    }
    
    /**
     * 월 단위 첫 실행 시간 계산
     */
    private long calculateMonthlyInitialDelay(TaskConfig taskConfig, LocalDateTime now) {
        LocalDate startDate = taskConfig.startDate();
        LocalTime scheduledTime = taskConfig.scheduledTime();
        int targetDay = startDate.getDayOfMonth();
        LocalDate today = now.toLocalDate();
        
        LocalDateTime scheduledDateTime;
        
        // 시작일이 미래면 시작일
        if (today.isBefore(startDate)) {
            scheduledDateTime = LocalDateTime.of(startDate, scheduledTime);
        } 
        // 시작일이 오늘이면 오늘의 스케줄 시간 확인
        else if (today.isEqual(startDate)) {
            scheduledDateTime = LocalDateTime.of(startDate, scheduledTime);
            // 오늘의 스케줄 시간이 지났으면 다음 달
            if (scheduledDateTime.isBefore(now) || scheduledDateTime.isEqual(now)) {
                LocalDate nextMonthTarget = taskConfig.getNextMonthlyDate(today);
                scheduledDateTime = LocalDateTime.of(nextMonthTarget, scheduledTime);
            }
        }
        // 시작일이 과거면 이번 달 또는 다음 달
        else {
            int lastDayOfMonth = today.lengthOfMonth();
            int actualDay = Math.min(targetDay, lastDayOfMonth);
            LocalDate thisMonthTarget = today.withDayOfMonth(actualDay);
            scheduledDateTime = LocalDateTime.of(thisMonthTarget, scheduledTime);
            
            if (scheduledDateTime.isBefore(now) || scheduledDateTime.isEqual(now)) {
                LocalDate nextMonthTarget = taskConfig.getNextMonthlyDate(today);
                scheduledDateTime = LocalDateTime.of(nextMonthTarget, scheduledTime);
            }
        }
        
        return ChronoUnit.MILLIS.between(now, scheduledDateTime);
    }
    
    /**
     * 다음 유효한 실행 날짜/시간 찾기
     * 주의: startDate 이후만 반환되도록 보장
     */
    private LocalDateTime findNextValidDateTime(TaskConfig taskConfig, LocalDateTime current) {
        LocalDateTime candidate = current;
        Duration period = taskConfig.period();
        LocalDate startDate = taskConfig.startDate();
        
        for (int i = 0; i < 365; i++) {
            candidate = period.toDays() >= 1 ? candidate.plusDays(1) : candidate.plus(period);
            
            LocalDate candidateDate = candidate.toLocalDate();
            
            // startDate 체크: 시작일 이전이면 continue
            if (startDate != null && candidateDate.isBefore(startDate)) {
                continue;
            }
            
            // endDate 체크
            if (!taskConfig.isValidDate(candidateDate)) {
                if (taskConfig.endDate() != null && candidateDate.isAfter(taskConfig.endDate())) {
                    return candidate.plusYears(1);
                }
                continue;
            }
            
            // 요일 체크
            if (taskConfig.isValidDayOfWeek(candidate.getDayOfWeek())) {
                return candidate;
            }
        }
        
        logger.warn("365일 내에 Task '{}'의 유효한 실행 날짜를 찾을 수 없습니다", taskConfig.name());
        return candidate;
    }
    
    /**
     * 첫 실행 시간 정확하게 계산 (일반 주기, 로그용)
     */
    private LocalDateTime calculateFirstRunTime(TaskConfig taskConfig, LocalDateTime now) {
        Duration period = taskConfig.period();
        
        // 초/분/시간 단위는 특별 계산
        if (period.toDays() < 1) {
            LocalDateTime scheduledDateTime = now.with(taskConfig.scheduledTime());
            
            // 시작일이 미래인 경우
            if (taskConfig.startDate() != null && now.toLocalDate().isBefore(taskConfig.startDate())) {
                return LocalDateTime.of(taskConfig.startDate(), taskConfig.scheduledTime());
            }
            
            // 시작일이 오늘이고 스케줄 시간이 안 지났으면 오늘
            if (taskConfig.startDate() != null && 
                now.toLocalDate().isEqual(taskConfig.startDate())) {
                LocalDateTime startDateTime = LocalDateTime.of(taskConfig.startDate(), taskConfig.scheduledTime());
                if (now.isBefore(startDateTime)) {
                    return startDateTime;
                }
                // 시간이 지났으면 주기만큼 더함
                return startDateTime.plus(period);
            }
            
            // 현재 시간과 같거나 지났으면 다음 주기
            if (scheduledDateTime.isBefore(now) || scheduledDateTime.isEqual(now)) {
                long millisSinceScheduled = ChronoUnit.MILLIS.between(scheduledDateTime, now);
                long periodMillis = period.toMillis();
                long remainingMillis = periodMillis - (millisSinceScheduled % periodMillis);
                
                if (remainingMillis == 0) {
                    remainingMillis = periodMillis;
                }
                
                return now.plus(remainingMillis, ChronoUnit.MILLIS);
            }
            
            return scheduledDateTime;
        }
        
        // 일 단위 이상 계산 (기존 로직)
        if (taskConfig.startDate() != null && now.toLocalDate().isBefore(taskConfig.startDate())) {
            return LocalDateTime.of(taskConfig.startDate(), taskConfig.scheduledTime());
        }
        
        LocalDateTime todayScheduled = now.toLocalDate().atTime(taskConfig.scheduledTime());
        
        if (taskConfig.startDate() != null && 
            now.toLocalDate().isEqual(taskConfig.startDate()) &&
            now.isBefore(todayScheduled)) {
            return todayScheduled;
        }
        
        if (now.isBefore(todayScheduled) && taskConfig.isValidDayOfWeek(now.getDayOfWeek())) {
            return todayScheduled;
        }
        
        LocalDateTime candidate = todayScheduled;
        if (now.isAfter(todayScheduled) || !taskConfig.isValidDayOfWeek(now.getDayOfWeek())) {
            candidate = candidate.plusDays(1);
        }
        
        LocalDate startDate = taskConfig.startDate();
        if (startDate != null && candidate.toLocalDate().isBefore(startDate)) {
            return LocalDateTime.of(startDate, taskConfig.scheduledTime());
        }
        
        int daysSearched = 0;
        while (!taskConfig.isValidDayOfWeek(candidate.getDayOfWeek()) && daysSearched < 7) {
            candidate = candidate.plusDays(1);
            daysSearched++;
        }
        
        return candidate;
    }

    /**
     * 첫 실행 시간 정확하게 계산 (월 단위, 로그용)
     */
    private LocalDateTime calculateFirstRunTimeMonthly(TaskConfig taskConfig, LocalDateTime now) {
        LocalDate startDate = taskConfig.startDate();
        LocalTime scheduledTime = taskConfig.scheduledTime();
        int targetDay = startDate.getDayOfMonth();
        LocalDate today = now.toLocalDate();
        
        // 시작일이 미래면 시작일
        if (today.isBefore(startDate)) {
            return LocalDateTime.of(startDate, scheduledTime);
        }
        
        // 시작일이 오늘이면 오늘의 스케줄 시간 확인
        if (today.isEqual(startDate)) {
            LocalDateTime startDateTime = LocalDateTime.of(startDate, scheduledTime);
            if (now.isBefore(startDateTime)) {
                return startDateTime;
            }
            // 시간이 지났으면 다음 달
            LocalDate nextMonthTarget = taskConfig.getNextMonthlyDate(today);
            return LocalDateTime.of(nextMonthTarget, scheduledTime);
        }
        
        // 시작일이 과거면 이번 달 또는 다음 달
        int lastDayOfMonth = today.lengthOfMonth();
        int actualDay = Math.min(targetDay, lastDayOfMonth);
        LocalDate thisMonthTarget = today.withDayOfMonth(actualDay);
        LocalDateTime thisMonthScheduled = LocalDateTime.of(thisMonthTarget, scheduledTime);
        
        if (now.isBefore(thisMonthScheduled)) {
            return thisMonthScheduled;
        }
        
        LocalDate nextMonthTarget = taskConfig.getNextMonthlyDate(today);
        return LocalDateTime.of(nextMonthTarget, scheduledTime);
    }
    
    /**
     * 다음 실행 시각 계산 (실행 후 로깅용)
     */
    private LocalDateTime calculateNextRunTime(TaskConfig taskConfig, LocalDateTime current) {
        if (taskConfig.isMonthlyPeriod()) {
            LocalDate nextDate = taskConfig.getNextMonthlyDate(current.toLocalDate());
            return LocalDateTime.of(nextDate, taskConfig.scheduledTime());
        }
        
        Duration period = taskConfig.period();
        
        if (period.toDays() < 1) {
            return current.plus(period);
        }
        
        LocalDate nextDate = current.toLocalDate().plusDays(1);
        LocalDateTime next = LocalDateTime.of(nextDate, taskConfig.scheduledTime());
        
        if (!taskConfig.daysOfWeek().isEmpty()) {
            int daysSearched = 0;
            while (!taskConfig.isValidDayOfWeek(next.getDayOfWeek()) && daysSearched < 7) {
                next = next.plusDays(1);
                daysSearched++;
            }
            
            if (daysSearched >= 7) {
                logger.warn("7일 내에 유효한 요일을 찾을 수 없습니다: '{}'", taskConfig.name());
            }
        }
        
        if (taskConfig.endDate() != null && next.toLocalDate().isAfter(taskConfig.endDate())) {
            logger.info("Task '{}'가 종료일에 도달했습니다", taskConfig.name());
            return next.plusYears(10);
        }
        
        return next;
    }
    
    /**
     * 모든 스케줄된 Task 취소 및 리소스 정리
     */
    public void cancelAll() {
        logger.info("Cancelling all scheduled tasks");
        
        scheduledFutures.forEach(future -> future.cancel(false));
        scheduledFutures.clear();
        
        metaScheduler.shutdown();
        try {
            if (!metaScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                metaScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            metaScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("All scheduled tasks cancelled");
    }
    
    /**
     * Task 통계 로깅
     */
    private void logTaskStatistics() {
        LocalDate today = LocalDate.now(zoneId);
        
        long activeCount = taskConfigs.stream()
            .filter(TaskConfig::enabled)
            .filter(config -> config.isValidDate(today))
            .count();
        
        long pendingCount = taskConfigs.stream()
            .filter(TaskConfig::enabled)
            .filter(config -> config.startDate() != null && today.isBefore(config.startDate()))
            .count();
        
        logger.info("Task Scheduler Started , Active Tasks : {} , Pending Tasks : {}", 
            activeCount, pendingCount);
    }
    
    /**
     * Task 상세 정보 로그 출력 (통합 버전)
     * @param taskConfig Task 설정
     * @param status 상태 (ACTIVE, SCHEDULED, EXPIRED)
     * @param firstRun 최초 스케줄된 시간 (설정 기준)
     * @param nextRun 다음 실행 시간 (실제 실행될 시간)
     * @param isMonthly 월 단위 여부
     */
    private void logTaskDetails(TaskConfig taskConfig, String status, LocalDateTime firstRun, 
                                 LocalDateTime nextRun, boolean isMonthly) {
        
        logger.info(
            MsgUtils.format(LOG_FORMAT, 
                taskConfig.name(),
                taskConfig.taskClass().getName(),
                taskConfig.getScheduledTimeString() + " every " + (isMonthly ? "M (Monthly / 매월)" : taskConfig.getPeriodString()),
                taskConfig.getScheduleTypeString(),
                taskConfig.getDaysOfWeekString(), 
                taskConfig.getStartDateString(),
                taskConfig.getEndDateString(), 
                taskConfig.getScheduledTimeString(), 
                firstRun != null ? DateUtils.toString(firstRun, "yyyy-MM-dd HH:mm:ss") : "",
                nextRun != null ? DateUtils.toString(nextRun, "yyyy-MM-dd HH:mm:ss") : firstRun != null ? DateUtils.toString(firstRun, "yyyy-MM-dd HH:mm:ss") : "",
                taskConfig.description(), 
                status + ("SCHEDULED".equals(status) ? " (시작 대기 중)" : "") + ("EXPIRED".equals(status) ? " (종료됨)" : "")
            )
        );
    }
}