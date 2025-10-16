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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.AsyncExecutor;
import kr.tx24.task.config.TaskConfig;

/**
 * Task 스케줄링 및 실행 관리자
 */
public class TaskScheduler {
    private static final Logger logger = LoggerFactory.getLogger(TaskScheduler.class);
    
    private final List<TaskConfig> taskConfigs;
    private final ZoneId zoneId;
    private final List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>();
    
    public TaskScheduler(List<TaskConfig> taskConfigs, String timezone) {
        this.taskConfigs = taskConfigs;
        this.zoneId = ZoneId.of(timezone);
        
        logger.info("TaskScheduler initialized / TaskScheduler 초기화");
        logger.info("Timezone / 시간대: {}", zoneId);
    }
    
    public void start() {
        if (taskConfigs.isEmpty()) {
            logger.warn("No tasks to schedule / 스케줄할 Task가 없습니다");
            return;
        }
        
        logger.info("\n╔════════════════════════════════════════════════════════════════╗");
        logger.info("║        Starting Task Scheduler / Task 스케줄러 시작           ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝");
        
        taskConfigs.stream()
            .filter(TaskConfig::enabled)
            .forEach(this::scheduleTask);
        
        long activeTaskCount = taskConfigs.stream()
            .filter(TaskConfig::enabled)
            .count();
        
        logger.info("\n════════════════════════════════════════════════════════════════");
        logger.info("Task Scheduler Started / Task 스케줄러 시작 완료");
        logger.info("Active Tasks / 활성화된 Task 수: {}", activeTaskCount);
        logger.info("════════════════════════════════════════════════════════════════\n");
    }
    
    private void scheduleTask(TaskConfig taskConfig) {
        try {
            // 날짜 범위 확인
            LocalDate today = LocalDate.now(zoneId);
            if (!taskConfig.isValidDate(today)) {
                if (taskConfig.startDate() != null && today.isBefore(taskConfig.startDate())) {
                    logger.info("⏸ Task '{}' will start from {} / Task '{}'는 {}부터 시작됩니다", 
                        taskConfig.name(), taskConfig.getStartDateString(),
                        taskConfig.name(), taskConfig.getStartDateString());
                } else if (taskConfig.endDate() != null && today.isAfter(taskConfig.endDate())) {
                    logger.info("⏹ Task '{}' has ended on {} / Task '{}'는 {}에 종료되었습니다", 
                        taskConfig.name(), taskConfig.getEndDateString(),
                        taskConfig.name(), taskConfig.getEndDateString());
                }
                return;
            }
            
            // Task 인스턴스 생성
            Runnable task = taskConfig.taskClass().getDeclaredConstructor().newInstance();
            
            // ✅ 월 단위 Task는 별도 처리
            if (taskConfig.isMonthlyPeriod()) {
                scheduleMonthlyTask(taskConfig, task);
            } else {
                scheduleRegularTask(taskConfig, task);
            }
            
        } catch (Exception e) {
            logger.error("✗ Failed to schedule task / Task 스케줄 등록 실패: '{}' [{}]", 
                taskConfig.name(), 
                taskConfig.taskClass().getSimpleName(), 
                e
            );
        }
    }
    
    /**
     * 일반 Task 스케줄링 (고정 주기)
     */
    private void scheduleRegularTask(TaskConfig taskConfig, Runnable task) {
        // Wrapper로 감싸서 요일/날짜 체크 및 비동기 실행
        Runnable wrappedTask = createWrappedTask(taskConfig, task);
        
        // 첫 실행까지의 지연 시간 계산
        long initialDelay = calculateInitialDelay(taskConfig);
        long period = taskConfig.period().toMillis();  // ✅ 양수
        
        // 스케줄링
        ScheduledFuture<?> future = AsyncExecutor.scheduleAtFixedRate(
            wrappedTask,
            initialDelay,
            period,
            TimeUnit.MILLISECONDS
        );
        
        scheduledFutures.add(future);
        
        LocalDateTime firstRun = LocalDateTime.now(zoneId)
            .plus(initialDelay, ChronoUnit.MILLIS);
        
        logger.info("✓ Scheduled Task / Task 스케줄 등록: '{}'", taskConfig.name());
        logger.info("  - Class / 클래스: {}", taskConfig.taskClass().getSimpleName());
        logger.info("  - First run / 첫 실행: {}", 
            firstRun.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        logger.info("  - Schedule / 스케줄: {} every {}", 
            taskConfig.getScheduledTimeString(), 
            taskConfig.getPeriodString()
        );
        
        if (!taskConfig.daysOfWeek().isEmpty()) {
            logger.info("  - Days of week / 실행 요일: {}", taskConfig.getDaysOfWeekString());
        }
        
        if (taskConfig.startDate() != null) {
            logger.info("  - Start date / 시작일: {}", taskConfig.getStartDateString());
        }
        if (taskConfig.endDate() != null) {
            logger.info("  - End date / 종료일: {}", taskConfig.getEndDateString());
        }
        if (!taskConfig.description().isBlank()) {
            logger.info("  - Description / 설명: {}", taskConfig.description());
        }
        logger.info("");
    }

    /**
     * 월 단위 Task 스케줄링 (일회성 + 재스케줄링)
     */
    private void scheduleMonthlyTask(TaskConfig taskConfig, Runnable task) {
        // 첫 실행까지의 지연 시간 계산
        long initialDelay = calculateInitialDelay(taskConfig);
        
        // ✅ 일회성 스케줄 + 실행 후 자동 재스케줄링
        Runnable wrappedTask = () -> {
            LocalDate executionDate = LocalDate.now(zoneId);
            
            // 날짜 범위 확인
            if (!taskConfig.isValidDate(executionDate)) {
                logger.debug("⏭ Skipping monthly task / 월간 Task 스킵: '{}' - Outside valid date range", 
                    taskConfig.name());
                return;
            }
            
            // 비동기 실행
            AsyncExecutor.execute(() -> executeTask(taskConfig, task));
            
            // ✅ 실행 후 다음 달 같은 시각으로 재스케줄링
            scheduleMonthlyTask(taskConfig, task);
        };
        
        ScheduledFuture<?> future = AsyncExecutor.schedule(
            wrappedTask,
            initialDelay,
            TimeUnit.MILLISECONDS
        );
        
        scheduledFutures.add(future);
        
        LocalDateTime firstRun = LocalDateTime.now(zoneId)
            .plus(initialDelay, ChronoUnit.MILLIS);
        
        logger.info("✓ Scheduled Monthly Task / 월간 Task 스케줄 등록: '{}'", taskConfig.name());
        logger.info("  - Class / 클래스: {}", taskConfig.taskClass().getSimpleName());
        logger.info("  - First run / 첫 실행: {}", 
            firstRun.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        logger.info("  - Schedule / 스케줄: {} every M (Monthly / 매월)", 
            taskConfig.getScheduledTimeString()
        );
        
        if (taskConfig.startDate() != null) {
            logger.info("  - Start date / 시작일: {}", taskConfig.getStartDateString());
        }
        if (taskConfig.endDate() != null) {
            logger.info("  - End date / 종료일: {}", taskConfig.getEndDateString());
        }
        if (!taskConfig.description().isBlank()) {
            logger.info("  - Description / 설명: {}", taskConfig.description());
        }
        logger.info("");
    }
    
    /**
     * Task를 Wrapper로 감싸서 날짜/요일 체크 및 비동기 실행 추가
     */
    private Runnable createWrappedTask(TaskConfig taskConfig, Runnable task) {
        return () -> {
            LocalDate executionDate = LocalDate.now(zoneId);
            DayOfWeek executionDay = executionDate.getDayOfWeek();
            
            // 날짜 범위 확인
            if (!taskConfig.isValidDate(executionDate)) {
                logger.debug("⏭ Skipping task / Task 스킵: '{}' - Outside valid date range / 유효 날짜 범위 밖", 
                    taskConfig.name());
                return;
            }
            
            // 요일 확인
            if (!taskConfig.isValidDayOfWeek(executionDay)) {
                logger.debug("⏭ Skipping task / Task 스킵: '{}' - Not scheduled for {} / {}에 스케줄되지 않음", 
                    taskConfig.name(), executionDay, executionDay);
                return;
            }
            
            // 비동기 실행 (AsyncExecutor 사용)
            AsyncExecutor.execute(() -> executeTask(taskConfig, task));
        };
    }
    
    /**
     * Task 실행 및 로깅
     */
    private void executeTask(TaskConfig taskConfig, Runnable task) {
        String taskName = taskConfig.name();
        LocalDateTime startTime = LocalDateTime.now(zoneId);
        
        try {
            logger.info("▶ Executing task / Task 실행 중: '{}' at / 시각: {}", 
                taskName, 
                startTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            );
            
            task.run();
            
            LocalDateTime endTime = LocalDateTime.now(zoneId);
            long duration = ChronoUnit.MILLIS.between(startTime, endTime);
            
            // 다음 실행 예정 시각 계산 및 로깅
            LocalDateTime nextRun = calculateNextRunTime(taskConfig, endTime);
            
            logger.info("✓ Task completed / Task 완료: '{}' (Duration / 소요시간: {}ms)", 
                taskName, duration);
            logger.info("  Next scheduled / 다음 실행 예정: {}", 
                nextRun.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
        } catch (Exception e) {
            logger.warn("✗ Task execution failed / Task 실행 실패: '{}'", taskName);
            logger.warn("  Error / 오류: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            logger.warn("  Stack trace / 스택 트레이스:", e);
        }
    }
    
    /**
     * 다음 실행 시각 계산
     */
    private LocalDateTime calculateNextRunTime(TaskConfig taskConfig, LocalDateTime current) {
        if (taskConfig.isMonthlyPeriod()) {
            // 월 단위: 다음 달 같은 일자
            LocalDate nextDate = taskConfig.getNextMonthlyDate(current.toLocalDate());
            return LocalDateTime.of(nextDate, taskConfig.scheduledTime());
        }
        
        Duration period = taskConfig.period();
        
        // 시간 단위 주기 (1일 미만): 단순 더하기
        if (period.toDays() < 1) {
            return current.plus(period);
        }
        
        // 일 단위 이상: scheduledTime 기준 + 요일 고려
        LocalDate nextDate = current.toLocalDate().plusDays(1); // 최소 다음 날부터
        LocalDateTime next = LocalDateTime.of(nextDate, taskConfig.scheduledTime());
        
        // 요일 조건 확인
        if (!taskConfig.daysOfWeek().isEmpty()) {
            // 유효한 요일 찾기 (최대 7일 검색)
            int daysSearched = 0;
            while (!taskConfig.isValidDayOfWeek(next.getDayOfWeek()) && daysSearched < 7) {
                next = next.plusDays(1);
                daysSearched++;
            }
            
            if (daysSearched >= 7) {
                logger.warn("Could not find valid day of week for task '{}' within 7 days / " +
                           "7일 내에 유효한 요일을 찾을 수 없습니다: '{}'", 
                    taskConfig.name(), taskConfig.name());
            }
        }
        
        // 종료일 확인
        if (taskConfig.endDate() != null && next.toLocalDate().isAfter(taskConfig.endDate())) {
            logger.info("Task '{}' has reached end date / Task '{}'가 종료일에 도달했습니다", 
                taskConfig.name(), taskConfig.name());
            return next.plusYears(10); // 실질적으로 종료
        }
        
        return next;
    }
    
    /**
     * 첫 실행 시간 계산 (요일 및 시작일 고려)
     */
    private long calculateInitialDelay(TaskConfig taskConfig) {
        LocalDateTime now = LocalDateTime.now(zoneId);
        
        // 월 단위 주기 처리
        if (taskConfig.isMonthlyPeriod()) {
            return calculateMonthlyInitialDelay(taskConfig, now);
        }
        
        // 일반 주기 처리
        LocalDateTime scheduledDateTime = now.with(taskConfig.scheduledTime());
        
        // 시작일이 지정되어 있고 아직 도래하지 않았다면
        if (taskConfig.startDate() != null) {
            LocalDate startDate = taskConfig.startDate();
            if (now.toLocalDate().isBefore(startDate)) {
                scheduledDateTime = LocalDateTime.of(startDate, taskConfig.scheduledTime());
                return ChronoUnit.MILLIS.between(now, scheduledDateTime);
            }
        }
        
        // 오늘 실행 시간이 지났으면 다음 실행 시간 찾기
        if (scheduledDateTime.isBefore(now) || scheduledDateTime.isEqual(now)) {
            scheduledDateTime = findNextValidDateTime(taskConfig, scheduledDateTime);
        } else {
            // 오늘 실행 시간이 안 지났어도 요일이 맞지 않으면 다음 유효 날짜 찾기
            if (!taskConfig.isValidDayOfWeek(scheduledDateTime.getDayOfWeek())) {
                scheduledDateTime = findNextValidDateTime(taskConfig, scheduledDateTime);
            }
        }
        
        return ChronoUnit.MILLIS.between(now, scheduledDateTime);
    }
    
    /**
     * 월 단위 주기의 첫 실행 시간 계산
     */
    private long calculateMonthlyInitialDelay(TaskConfig taskConfig, LocalDateTime now) {
        LocalDate startDate = taskConfig.startDate();
        LocalTime scheduledTime = taskConfig.scheduledTime();
        int targetDay = startDate.getDayOfMonth();
        
        LocalDate today = now.toLocalDate();
        LocalDateTime scheduledDateTime;
        
        // 시작일이 아직 안 됐으면 시작일로
        if (today.isBefore(startDate)) {
            scheduledDateTime = LocalDateTime.of(startDate, scheduledTime);
        } else {
            // 이번 달 목표 일자 계산
            int lastDayOfMonth = today.lengthOfMonth();
            int actualDay = Math.min(targetDay, lastDayOfMonth);
            LocalDate thisMonthTarget = today.withDayOfMonth(actualDay);
            
            scheduledDateTime = LocalDateTime.of(thisMonthTarget, scheduledTime);
            
            // 이번 달 실행 시간이 지났으면 다음 달로
            if (scheduledDateTime.isBefore(now) || scheduledDateTime.isEqual(now)) {
                LocalDate nextMonthTarget = taskConfig.getNextMonthlyDate(today);
                scheduledDateTime = LocalDateTime.of(nextMonthTarget, scheduledTime);
            }
        }
        
        long delay = ChronoUnit.MILLIS.between(now, scheduledDateTime);
        
        logger.debug("Monthly task '{}': Current={}, Next execution={}, Delay={}ms", 
            taskConfig.name(),
            now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            scheduledDateTime.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            delay
        );
        
        return delay;
    }
    
    /**
     * 다음 유효한 실행 날짜/시간 찾기
     */
    private LocalDateTime findNextValidDateTime(TaskConfig taskConfig, LocalDateTime current) {
        LocalDateTime candidate = current;
        Duration period = taskConfig.period();
        
        // 최대 365일까지만 검색
        for (int i = 0; i < 365; i++) {
            if (period.toDays() >= 1) {
                candidate = candidate.plusDays(1);
            } else {
                candidate = candidate.plus(period);
            }
            
            LocalDate candidateDate = candidate.toLocalDate();
            DayOfWeek candidateDay = candidateDate.getDayOfWeek();
            
            if (!taskConfig.isValidDate(candidateDate)) {
                if (taskConfig.endDate() != null && candidateDate.isAfter(taskConfig.endDate())) {
                    return candidate.plusYears(1);
                }
                continue;
            }
            
            if (taskConfig.isValidDayOfWeek(candidateDay)) {
                return candidate;
            }
        }
        
        logger.warn("Could not find valid execution date for task '{}' within 365 days / " +
                   "365일 내에 Task '{}'의 유효한 실행 날짜를 찾을 수 없습니다", 
            taskConfig.name(), taskConfig.name());
        return candidate;
    }
    
    /**
     * 모든 스케줄된 Task 취소
     */
    public void cancelAll() {
        logger.info("Cancelling all scheduled tasks / 모든 스케줄된 Task 취소 중...");
        
        for (ScheduledFuture<?> future : scheduledFutures) {
            future.cancel(false);
        }
        
        scheduledFutures.clear();
        logger.info("All scheduled tasks cancelled / 모든 스케줄된 Task 취소 완료");
    }
}