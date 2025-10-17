package kr.tx24.task.main;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.task.annotation.Task;
import kr.tx24.task.config.TaskConfig;

/**
 * Classpath를 스캔하여 @Task Annotation이 붙은 클래스를 자동으로 찾음
 */
public class TaskScanner {
    private static final Logger logger = LoggerFactory.getLogger(TaskScanner.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    
    /**
     * 지정된 패키지에서 @Task Annotation이 있는 클래스 스캔
     * 
     * @param basePackage 스캔할 기본 패키지 (예: "kr.tx24.task")
     * @return 발견된 Task 설정 목록
     */
    public static List<TaskConfig> scanTasks(String basePackage) {
        
        List<TaskConfig> taskConfigs = new ArrayList<>();
        
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = basePackage.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);
            
            int resourceCount = 0;
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                resourceCount++;
                if(SystemUtils.deepview()) {
                	logger.debug("Scanning resource #{}: {}", resourceCount, resource);
                }
                
                if (resource.getProtocol().equals("file")) {
                    // 파일 시스템의 클래스 스캔 (개발 환경)
                    scanDirectory(new File(resource.getFile()), basePackage, taskConfigs);
                } else if (resource.getProtocol().equals("jar")) {
                    // JAR 파일 내부 클래스 스캔 (운영 환경)
                    scanJar(resource, basePackage, taskConfigs);
                }
            }
            
        } catch (IOException e) {
            logger.error("Failed to scan tasks / Task 스캔 실패", e);
        }
        
        // 우선순위 순으로 정렬 (높은 priority가 먼저)
        taskConfigs.sort(TaskConfig::compareTo);
        
        if(SystemUtils.deepview()) {
        	logger.debug("Total Tasks Found : {}", taskConfigs.size());
        }
        
        
        // 발견된 Task 상세 정보 출력
        if (!taskConfigs.isEmpty()) {
            printTaskDetails(taskConfigs);
        }
        
        return taskConfigs;
    }
    
    /**
     * 디렉토리 스캔 (개발 환경)
     */
    private static void scanDirectory(File directory, String packageName, List<TaskConfig> taskConfigs) {
        if (!directory.exists()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), taskConfigs);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + '.' + 
                    file.getName().substring(0, file.getName().length() - 6);
                processClass(className, taskConfigs);
            }
        }
    }
    
    /**
     * JAR 파일 스캔 (운영 환경)
     */
    private static void scanJar(URL resource, String basePackage, List<TaskConfig> taskConfigs) {
        try {
            String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
            JarFile jar = new JarFile(jarPath);
            
            Enumeration<JarEntry> entries = jar.entries();
            String basePath = basePackage.replace('.', '/');
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                
                if (name.startsWith(basePath) && name.endsWith(".class")) {
                    String className = name.substring(0, name.length() - 6)
                        .replace('/', '.');
                    processClass(className, taskConfigs);
                }
            }
            
            jar.close();
        } catch (IOException e) {
            logger.error("Failed to scan JAR file / JAR 파일 스캔 실패", e);
        }
    }
    
    /**
     * 클래스 로드 및 @Task Annotation 확인
     */
    private static void processClass(String className, List<TaskConfig> taskConfigs) {
        try {
            Class<?> clazz = Class.forName(className);
            
            // @Task Annotation 확인
            if (clazz.isAnnotationPresent(Task.class)) {
                Task annotation = clazz.getAnnotation(Task.class);
                
                // Runnable 구현 여부 확인
                if (!Runnable.class.isAssignableFrom(clazz)) {
                    logger.error("Task class '{}' has @Task but does not implement Runnable", 
                        className);
                    System.exit(1);
                    return;
                }
                
                @SuppressWarnings("unchecked")
                Class<? extends Runnable> taskClass = (Class<? extends Runnable>) clazz;
                
                // TaskConfig 생성
                TaskConfig taskConfig = createTaskConfig(annotation, taskClass);
                taskConfigs.add(taskConfig);
                if(SystemUtils.deepview()) {
                	logger.debug("Found Task : {}", className);
                }
            }
            
        } catch (ClassNotFoundException e) {
            logger.debug("Could not load class : {}", className);
        } catch (NoClassDefFoundError e) {
            logger.debug("Skipping class due to dependency : {}", className);
        } catch (Exception e) {
            logger.warn("Error processing class : {}", className, e);
        }
    }
    
    /**
     * @Task Annotation으로부터 TaskConfig 생성
     */
    private static TaskConfig createTaskConfig(Task annotation, Class<? extends Runnable> taskClass) {
        String taskName = annotation.name();
        
        // 1. time 파싱 및 검증
        LocalTime scheduledTime;
        if (annotation.time().isBlank()) {
            scheduledTime = LocalTime.now().withSecond(0).withNano(0);
        } else {
            scheduledTime = parseTimeWithValidation(annotation.time(), taskName);
        }
        
        // 2. period 파싱 및 검증
        Duration period = parsePeriodWithValidation(annotation.period(), taskName);
        
        // 3. 월 단위 주기인 경우 startDay 필수 검증
        if (period.toDays() == -1 && annotation.startDay().isBlank()) {
            throw new IllegalArgumentException(
                String.format("Task '%s': Monthly period (M) requires startDay / " +
                             "월 단위 주기(M)는 startDay가 필수입니다", taskName)
            );
        }
        
        // 4. 월 단위에 요일 지정 시 경고
        if (period.toDays() == -1 && annotation.daysOfWeek().length > 0) {
            logger.debug("Task '{}': daysOfWeek is ignored for monthly period (M) / " +
                       "월 단위 주기(M)에서는 daysOfWeek가 무시됩니다", taskName);
        }
        
        // 5. daysOfWeek 파싱
        Set<DayOfWeek> daysOfWeek = Set.of(annotation.daysOfWeek());
        
        // 6. startDay 파싱 및 검증
        LocalDate startDate;
        if (annotation.startDay().isBlank()) {
            startDate = DateUtils.getDay();
        } else {
            startDate = parseDateWithValidation(annotation.startDay(), "startDay", taskName);
        }
        
        // 7. endDay 파싱 및 검증
        LocalDate endDate;
        if (!annotation.endDay().isBlank()) {
            endDate = parseDateWithValidation(annotation.endDay(), "endDay", taskName);
            
            // endDay가 startDay보다 이전인지 검증
            if (endDate.isBefore(startDate)) {
                throw new IllegalArgumentException(
                    String.format("Task '%s': endDay (%s) must be after startDay (%s) / " +
                                 "endDay는 startDay 이후여야 합니다",
                        taskName, annotation.endDay(), annotation.startDay())
                );
            }
        } else {
            endDate = LocalDate.parse("20991231", DATE_FORMATTER);
        }
        
        return new TaskConfig(
            taskName,
            taskClass,
            scheduledTime,
            period,
            daysOfWeek,
            startDate,
            endDate,
            annotation.enabled(),
            annotation.desc(),
            annotation.priority()
        );
    }

    /**
     * "HH:mm" 형식의 시간 파싱 및 검증
     */
    private static LocalTime parseTimeWithValidation(String timeStr, String taskName) {
        if (timeStr == null || timeStr.isBlank()) {
            throw new IllegalArgumentException(
                String.format("Task '%s': time cannot be empty / time은 비어있을 수 없습니다", taskName)
            );
        }
        
        // 기본 형식 검증 (HH:mm)
        if (!timeStr.matches("^\\d{2}:\\d{2}$")) {
            throw new IllegalArgumentException(
                String.format("Task '%s': time must be in 'HH:mm' format (got: '%s') / " +
                             "time은 'HH:mm' 형식이어야 합니다",
                    taskName, timeStr)
            );
        }
        
        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            
            // 범위 검증
            if (hour < 0 || hour > 23) {
                throw new IllegalArgumentException(
                    String.format("Task '%s': hour must be between 0 and 23 (got: %d) / " +
                                 "시간은 0~23 사이여야 합니다",
                        taskName, hour)
                );
            }
            
            if (minute < 0 || minute > 59) {
                throw new IllegalArgumentException(
                    String.format("Task '%s': minute must be between 0 and 59 (got: %d) / " +
                                 "분은 0~59 사이여야 합니다",
                        taskName, minute)
                );
            }
            
            return LocalTime.of(hour, minute);
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("Task '%s': time contains invalid numbers (got: '%s') / " +
                             "time에 잘못된 숫자가 포함되어 있습니다",
                    taskName, timeStr)
            );
        }
    }

    /**
     * "M", "2w", "1d", "2h", "30m" 형식의 기간 파싱 및 검증
     */
    private static Duration parsePeriodWithValidation(String periodStr, String taskName) {
        if (periodStr == null || periodStr.isBlank()) {
            throw new IllegalArgumentException(
                String.format("Task '%s': period cannot be empty / period는 비어있을 수 없습니다", taskName)
            );
        }
        
        periodStr = periodStr.trim();
        
        // "M" 단독인 경우 (월 단위, startDay 기준)
        if ("M".equals(periodStr)) {
            return Duration.ofDays(-1); // 특수 플래그 (-1 = 월 단위)
        }
        
        // 형식 검증: 숫자 + 단위(w|d|h|m)
        if (!periodStr.matches("^\\d+[wdhm]$")) {
            throw new IllegalArgumentException(
                String.format("Task '%s': period must be in format 'M' or 'number+unit(w|d|h|m)' (got: '%s') / " +
                             "period는 'M' 또는 '숫자+단위(w|d|h|m)' 형식이어야 합니다 (예: 2w, 1d, 2h, 30m)",
                    taskName, periodStr)
            );
        }
        
        try {
            char unit = periodStr.charAt(periodStr.length() - 1);
            long value = Long.parseLong(periodStr.substring(0, periodStr.length() - 1));
            
            // 값 범위 검증
            if (value <= 0) {
                throw new IllegalArgumentException(
                    String.format("Task '%s': period value must be positive (got: %d) / " +
                                 "period 값은 양수여야 합니다",
                        taskName, value)
                );
            }
            
            // 비현실적인 큰 값 검증
            if (value > 365 && unit == 'w') {
                logger.warn("Task '{}': unusually large period value: {}w ({}weeks)", 
                    taskName, value, value);
            } else if (value > 3650 && unit == 'd') {
                logger.warn("Task '{}': unusually large period value: {}d ({}days)", 
                    taskName, value, value);
            }
            
            return switch (unit) {
                case 'w' -> Duration.ofDays(value * 7);   // 주
                case 'd' -> Duration.ofDays(value);       // 일
                case 'h' -> Duration.ofHours(value);      // 시간
                case 'm' -> Duration.ofMinutes(value);    // 분
                default -> throw new IllegalArgumentException(
                    String.format("Task '%s': unsupported period unit '%c' (allowed: w, d, h, m) / " +
                                 "지원하지 않는 단위입니다",
                        taskName, unit)
                );
            };
            
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                String.format("Task '%s': period contains invalid number (got: '%s') / " +
                             "period에 잘못된 숫자가 포함되어 있습니다",
                    taskName, periodStr)
            );
        }
    }

    /**
     * "yyyyMMdd" 형식의 날짜 파싱 및 검증
     */
    private static LocalDate parseDateWithValidation(String dateStr, String fieldName, String taskName) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new IllegalArgumentException(
                String.format("Task '%s': %s cannot be empty / %s는 비어있을 수 없습니다",
                    taskName, fieldName, fieldName)
            );
        }
        
        // 기본 형식 검증 (yyyyMMdd - 8자리 숫자)
        if (!dateStr.matches("^\\d{8}$")) {
            throw new IllegalArgumentException(
                String.format("Task '%s': %s must be in 'yyyyMMdd' format (got: '%s') / " +
                             "%s는 'yyyyMMdd' 형식이어야 합니다 (예: 20250101)",
                    taskName, fieldName, dateStr, fieldName)
            );
        }
        
        try {
            LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
            
            // 합리적인 날짜 범위 검증 (1900 ~ 2100)
            if (date.getYear() < 1900 || date.getYear() > 2100) {
                logger.warn("Task '{}': {} has unusual year: {} (expected: 1900-2100)", 
                    taskName, fieldName, date.getYear());
            }
            
            return date;
            
        } catch (Exception e) {
            throw new IllegalArgumentException(
                String.format("Task '%s': %s is not a valid date (got: '%s') / " +
                             "%s가 유효하지 않은 날짜입니다 (예: 20250101, 20251231)",
                    taskName, fieldName, dateStr, fieldName),
                e
            );
        }
    }
    
    /**
     * "HH:mm" 형식의 시간 파싱
     */
    private static LocalTime parseTime(String timeStr) {
        String[] parts = timeStr.split(":");
        return LocalTime.of(
            Integer.parseInt(parts[0]), 
            Integer.parseInt(parts[1])
        );
    }
    
    /**
     * "M", "2w", "1d", "2h", "30m" 형식의 기간 파싱
     */
    private static Duration parsePeriod(String periodStr) {
        periodStr = periodStr.trim();
        
        // "M" 단독인 경우 (월 단위, startDay 기준)
        if ("M".equals(periodStr)) {
            return Duration.ofDays(-1); // 특수 플래그 (-1 = 월 단위)
        }
        
        char unit = periodStr.charAt(periodStr.length() - 1);
        long value = Long.parseLong(periodStr.substring(0, periodStr.length() - 1));
        
        return switch (unit) {
            case 'w' -> Duration.ofDays(value * 7);   // 주
            case 'd' -> Duration.ofDays(value);       // 일
            case 'h' -> Duration.ofHours(value);      // 시간
            case 'm' -> Duration.ofMinutes(value);    // 분
            default -> {
                logger.warn("알 수 없는 주기 단위 '{}', 기본값 1일 적용", unit);
                yield Duration.ofDays(1);
            }
        };
    }
    
    /**
     * 발견된 Task 상세 정보 출력
     */
    private static void printTaskDetails(List<TaskConfig> taskConfigs) {
    	StringBuilder sb = new StringBuilder();
        for (int i = 0; i < taskConfigs.size(); i++) {
            TaskConfig config = taskConfigs.get(i);
            
            
            
            sb.append(String.format("%n#Task %d / %d %n", i + 1, taskConfigs.size()));
            sb.append(String.format("Name         : %s%n", config.name()));
            sb.append(String.format("Class        : %s%n", config.taskClass().getName()));
            sb.append(String.format("Priority     : %s%n", config.priority()));
            sb.append(String.format("Enabled      : %s%n", config.enabled() ? "Yes" : "No"));
            sb.append(String.format("Time         : %s%n", config.getScheduledTimeString()));
            sb.append(String.format("Period       : %s%n", config.getPeriodString()));
            
            if (!config.isMonthlyPeriod()) {
                sb.append(String.format("Days of Week : %s%n", config.getDaysOfWeekString()));
            }
            
            if (config.startDate() != null) {
                sb.append(String.format("Start Date   : %s%n", config.getStartDateString()));
            }
            if (config.endDate() != null) {
                sb.append(String.format("End Date     : %s%n", config.getEndDateString()));
            }
            if (config.description() != null && !config.description().isBlank()) {
                sb.append(String.format("Description  : %s%n", config.description()));
            }
            
            
        }
        logger.debug("\n{}", sb);
    }
}