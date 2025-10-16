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
        logger.info("════════════════════════════════════════════════════════════════");
        logger.info("Starting Task Scanning / Task 스캔 시작");
        logger.info("Target Package / 대상 패키지: {}", basePackage);
        logger.info("════════════════════════════════════════════════════════════════");
        
        List<TaskConfig> taskConfigs = new ArrayList<>();
        
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            String path = basePackage.replace('.', '/');
            Enumeration<URL> resources = classLoader.getResources(path);
            
            int resourceCount = 0;
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                resourceCount++;
                logger.debug("Scanning resource #{}: {}", resourceCount, resource);
                
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
        
        logger.info("════════════════════════════════════════════════════════════════");
        logger.info("Task Scanning Completed / Task 스캔 완료");
        logger.info("Total Tasks Found / 발견된 총 Task 수: {}", taskConfigs.size());
        logger.info("════════════════════════════════════════════════════════════════\n");
        
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
                    logger.error("✗ Task class '{}' has @Task but does not implement Runnable", 
                        className);
                    logger.error("  클래스 '{}'는 @Task가 있지만 Runnable을 구현하지 않았습니다", 
                        className);
                    return;
                }
                
                @SuppressWarnings("unchecked")
                Class<? extends Runnable> taskClass = (Class<? extends Runnable>) clazz;
                
                // TaskConfig 생성
                TaskConfig taskConfig = createTaskConfig(annotation, taskClass);
                taskConfigs.add(taskConfig);
                
                logger.info("✓ Found Task / Task 발견: {}", className);
            }
            
        } catch (ClassNotFoundException e) {
            logger.debug("Could not load class / 클래스 로드 불가: {}", className);
        } catch (NoClassDefFoundError e) {
            logger.debug("Skipping class due to dependency / 의존성 문제로 스킵: {}", className);
        } catch (Exception e) {
            logger.warn("Error processing class / 클래스 처리 중 오류: {}", className, e);
        }
    }
    
    /**
     * @Task Annotation으로부터 TaskConfig 생성
     */
    private static TaskConfig createTaskConfig(Task annotation, Class<? extends Runnable> taskClass) {
        // time 파싱
        LocalTime scheduledTime = parseTime(annotation.time());
        
        // period 파싱
        Duration period = parsePeriod(annotation.period());
        
        // 월 단위 주기인 경우 startDay 필수 검증
        if (period.toDays() == -1 && annotation.startDay().isBlank()) {
            throw new IllegalArgumentException(
                String.format("Task '%s': Monthly period (M) requires startDay / " +
                             "월 단위 주기(M)는 startDay가 필수입니다", annotation.name())
            );
        }
        
        // daysOfWeek 파싱
        Set<DayOfWeek> daysOfWeek = Set.of(annotation.daysOfWeek());
        
        // startDay 파싱
        LocalDate startDate = null;
        if (!annotation.startDay().isBlank()) {
            startDate = LocalDate.parse(annotation.startDay(), DATE_FORMATTER);
        }
        
        // endDay 파싱
        LocalDate endDate = null;
        if (!annotation.endDay().isBlank()) {
            endDate = LocalDate.parse(annotation.endDay(), DATE_FORMATTER);
        }
        
        return new TaskConfig(
            annotation.name(),
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
                logger.warn("Unknown period unit '{}', defaulting to 1 day / " +
                           "알 수 없는 주기 단위 '{}', 기본값 1일 적용", unit, unit);
                yield Duration.ofDays(1);
            }
        };
    }
    
    /**
     * 발견된 Task 상세 정보 출력
     */
    private static void printTaskDetails(List<TaskConfig> taskConfigs) {
        logger.info("╔════════════════════════════════════════════════════════════════╗");
        logger.info("║           Discovered Tasks / 발견된 Task 목록                  ║");
        logger.info("╚════════════════════════════════════════════════════════════════╝\n");
        
        for (int i = 0; i < taskConfigs.size(); i++) {
            TaskConfig config = taskConfigs.get(i);
            
            StringBuilder sb = new StringBuilder();
            
            sb.append(String.format("Task #%d / %d %n", i + 1, taskConfigs.size()));
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
            
            // 한 번에 로그 출력
            logger.info("\n{}", sb);
        }
    }
}