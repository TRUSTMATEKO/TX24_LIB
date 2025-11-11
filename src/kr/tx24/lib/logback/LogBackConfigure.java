package kr.tx24.lib.logback;

import java.io.File;
import java.util.Arrays;
import java.util.Queue;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.LoggerFactory;
import org.slf4j.helpers.SubstituteLoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;
import ch.qos.logback.core.status.StatusManager;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Advanced LogBack Configurator
 * - Console / File / Redis Async Appender
 * - MaskConverter 적용
 * - Environment-based activation
 * - INFO 상태 메시지 차단, ERROR 메시지 보존
 */
public class LogBackConfigure extends ContextAwareBase implements Configurator {

    private static final boolean[] LOG_APPENDER = new boolean[]{true, false, false};
    private static Logger ROOT_LOGGER;
    
    static {
        // Logback 상태 메시지 부분 비활성화
        System.setProperty("logback.configurationFile", "DISABLE");
        
        // SLF4J 초기화 메시지 비활성화
        System.setProperty("slf4j.internal.verbosity", "ERROR");
        
        //초기화 작업 
        SystemUtils.init();
    }
    
    /**
     * ERROR 레벨만 통과시키는 StatusListener
     */
    private static class ErrorOnlyStatusListener extends OnConsoleStatusListener {
        @Override
        public void addStatusEvent(Status status) {
            // ERROR와 WARN만 출력
            if (status.getLevel() >= Status.ERROR) {
                super.addStatusEvent(status);
            }
            // INFO는 무시
        }
    }
    
    /**
     * 선택적 필터링을 수행하는 StatusManager
     * - ERROR, WARN 레벨: 통과
     * - INFO 레벨: 차단
     */
    private static class SelectiveStatusManager implements StatusManager {
        private final StatusManager delegate;
        private List<StatusListener> listeners = new ArrayList<>();
        
        public SelectiveStatusManager(StatusManager original) {
            this.delegate = original;
            // 기존 리스너 모두 제거
            if (delegate != null) {
                delegate.getCopyOfStatusListenerList().forEach(delegate::remove);
            }
            // ERROR만 출력하는 리스너 추가
            ErrorOnlyStatusListener errorListener = new ErrorOnlyStatusListener();
            errorListener.start();
            this.add(errorListener);
        }
        
        @Override
        public void add(Status status) {
            // ERROR와 WARN 레벨만 전달
            if (status.getLevel() >= Status.ERROR) {
                if (delegate != null) {
                    delegate.add(status);
                }
                // 리스너에게도 전달
                for (StatusListener listener : listeners) {
                    listener.addStatusEvent(status);
                }
            }
            // INFO 레벨은 무시
        }
        
        @Override
        public List<Status> getCopyOfStatusList() {
            if (delegate != null) {
                List<Status> filtered = new ArrayList<>();
                for (Status s : delegate.getCopyOfStatusList()) {
                    if (s.getLevel() >= Status.ERROR) {
                        filtered.add(s);
                    }
                }
                return filtered;
            }
            return new ArrayList<>();
        }
        
        @Override
        public int getCount() {
            if (delegate != null) {
                return (int) delegate.getCopyOfStatusList().stream()
                    .filter(s -> s.getLevel() >= Status.ERROR)
                    .count();
            }
            return 0;
        }
        
        @Override
        public boolean add(StatusListener listener) {
            listeners.add(listener);
            return true;
        }
        
        @Override
        public void remove(StatusListener listener) {
            listeners.remove(listener);
        }
        
        @Override
        public void clear() {
            if (delegate != null) {
                delegate.clear();
            }
        }
        
        @Override
        public List<StatusListener> getCopyOfStatusListenerList() {
            return new ArrayList<>(listeners);
        }
    }

    @Override
    public ExecutionStatus configure(LoggerContext ctx) {
        
        // 원래 StatusManager를 SelectiveStatusManager로 래핑
        StatusManager originalStatusManager = ctx.getStatusManager();
        ctx.setStatusManager(new SelectiveStatusManager(originalStatusManager));
        
        // 컨텍스트 초기화
        ctx.reset();
        
        // LOGGER 환경변수 기반 설정
        String[] udfs = Arrays.stream(System.getProperty("LOGGER", SystemUtils.LOG_ENV_DEFAULT).split(","))
                .map(String::trim).toArray(String[]::new);
        for (int i = 0; i < udfs.length && i < LOG_APPENDER.length; i++) {
            LOG_APPENDER[i] = Boolean.parseBoolean(udfs[i]);
        }

        ROOT_LOGGER = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        ROOT_LOGGER.setLevel(SystemUtils.getLogLevel());
        ROOT_LOGGER.setAdditive(false);

        // MaskConverter 등록
        PatternLayout.DEFAULT_CONVERTER_MAP.put("masked", MaskConverter.class.getName());

        // Console Appender
        if (LOG_APPENDER[0]) {
            PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
            consoleEncoder.setContext(ctx);
            consoleEncoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{16} -%mdc:%masked%n");
            consoleEncoder.start();

            ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
            consoleAppender.setContext(ctx);
            consoleAppender.setEncoder(consoleEncoder);
            consoleAppender.start();

            ROOT_LOGGER.addAppender(consoleAppender);
        }

        // Rolling File Appender  
        if (LOG_APPENDER[1]) {
            PatternLayoutEncoder fileEncoder = new PatternLayoutEncoder();
            fileEncoder.setContext(ctx);
            fileEncoder.setPattern("%d{HH:mm:ss.SSS} [%.7thread] %-5level %logger{16} -%mdc:%masked%n");
            fileEncoder.start();

            RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
            fileAppender.setContext(ctx);
            fileAppender.setName("LOCAL");
            fileAppender.setEncoder(fileEncoder);
            fileAppender.setAppend(true);
            fileAppender.setFile(SystemUtils.getLogDirectory() + File.separator + "root.txt");

            TimeBasedRollingPolicy<ILoggingEvent> policy = new TimeBasedRollingPolicy<>();
            policy.setContext(ctx);
            policy.setParent(fileAppender);
            policy.setFileNamePattern(SystemUtils.getLogDirectory() + File.separator + "backup" + File.separator + "back.%d{yyyyMMdd}.zip");
            policy.setMaxHistory(SystemUtils.getLogMaxDay());
            policy.start();

            fileAppender.setRollingPolicy(policy);
            fileAppender.start();

            ROOT_LOGGER.addAppender(fileAppender);
        }

        // Redis Appender
        if (LOG_APPENDER[2]) {
            String redisPattern = String.join(LogUtils.LOG_DELIMITER_V2,
                    "V2",
                    SystemUtils.getLocalProcessName(),
                    SystemUtils.getLocalProcessId(),
                    SystemUtils.getLocalHostname() + "," + SystemUtils.getLocalAddress(),
                    "%d{yyyyMMdd}",
                    "%d{HHmmssSSS}",
                    "%thread",
                    "%-5level",
                    "%logger{16}",
                    "%mdc",
                    "%masked"
            );

            PatternLayoutEncoder redisEncoder = new PatternLayoutEncoder();
            redisEncoder.setContext(ctx);
            redisEncoder.setPattern(redisPattern);
            redisEncoder.start();

            RedisAppender redisAppender = new RedisAppender();
            redisAppender.setContext(ctx);
            redisAppender.setName("REMOTE");
            redisAppender.setLayout(redisEncoder.getLayout());
            redisAppender.start();

            ROOT_LOGGER.addAppender(redisAppender);
        }

        // 외부 라이브러리 로그 레벨 조정
        setExternalLogLevel(ctx);

        if(SystemUtils.deepview()) {
            System.err.println(MsgUtils.format("LOG     console={}, local={}, remote={}", LOG_APPENDER[0], LOG_APPENDER[1], LOG_APPENDER[2]));
        }
        
        printLostLogs();
        return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
    }

    private void setExternalLogLevel(LoggerContext ctx) {
        ctx.getLogger("ch.qos.logback.classic").setLevel(Level.OFF);
        ctx.getLogger("javax.activation").setLevel(Level.INFO);
        ctx.getLogger("javax.mail").setLevel(Level.INFO);
        ctx.getLogger("io.netty").setLevel(Level.INFO);
        ctx.getLogger("org.apache").setLevel(Level.INFO);
        ctx.getLogger("org.hibernate").setLevel(Level.INFO);
        
        ctx.getLogger("com.zaxxer.hikari").setLevel(Level.OFF);
        ctx.getLogger("com.zaxxer.hikari.pool.HikariPool").setLevel(Level.ERROR);
        
        ctx.getLogger("io.lettuce").setLevel(Level.INFO);
        ctx.getLogger("io.lettuce.core.AbstractRedisClient").setLevel(Level.ERROR);
        
        ctx.getLogger("reactor.util").setLevel(Level.INFO);
        ctx.getLogger("org.thymeleaf").setLevel(Level.INFO);
        ctx.getLogger("org.springframework").setLevel(Level.INFO);
        ctx.getLogger("org.mariadb.jdbc").setLevel(Level.INFO);
        ctx.getLogger("com.password4j").setLevel(Level.INFO);
    }
    
    /**
     * SLF4J 큐에 저장된 로스트 로그를 출력합니다.
     * INFO 레벨 이상(INFO, WARN, ERROR)만 출력합니다.
     * LogBack 초기화 전에 호출해야 합니다.
     */
    public static void printLostLogs() {
        try {
            org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            
            if (!(factory instanceof SubstituteLoggerFactory)) {
                return; // 이미 초기화됨
            }
            
            SubstituteLoggerFactory subFactory = (SubstituteLoggerFactory) factory;
            // Reflection으로 큐 접근
            java.lang.reflect.Field field = SubstituteLoggerFactory.class.getDeclaredField("eventQueue");
            field.setAccessible(true);
            
            @SuppressWarnings("unchecked")
            Queue<Object> queue = (Queue<Object>) field.get(subFactory);
            
            if (queue == null || queue.isEmpty()) {
                return;
            }
            
            // INFO 레벨 이상만 카운트
            int infoOrHigherCount = 0;
            java.util.List<Object> filteredEvents = new java.util.ArrayList<>();
            
            for (Object event : queue) {
                try {
                    java.lang.reflect.Method getLevel = event.getClass().getMethod("getLevel");
                    Object levelObj = getLevel.invoke(event);
                    String levelStr = levelObj.toString();
                    
                    // INFO, WARN, ERROR만 수집
                    if ("INFO".equals(levelStr) || "WARN".equals(levelStr) || "ERROR".equals(levelStr)) {
                        infoOrHigherCount++;
                        filteredEvents.add(event);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
            
            // INFO 레벨 이상의 로그만 출력
            if (infoOrHigherCount > 0) {
                System.out.println("SLF4J Lost Logs [INFO or higher] (" + infoOrHigherCount + " of " + queue.size() + " total):");
                
                int i = 1;
                for (Object event : filteredEvents) {
                    try {
                        java.lang.reflect.Method getLevel = event.getClass().getMethod("getLevel");
                        java.lang.reflect.Method getLogger = event.getClass().getMethod("getLoggerName");
                        java.lang.reflect.Method getMessage = event.getClass().getMethod("getMessage");
                        
                        System.out.printf("[%d] %-5s %s - %s%n", 
                            i++, 
                            getLevel.invoke(event), 
                            getLogger.invoke(event), 
                            getMessage.invoke(event)
                        );
                    } catch (Exception e) {
                        // ignore
                    }
                }
                System.out.println();
            }
            // DEBUG/TRACE만 있으면 아무것도 출력하지 않음
            
        } catch (Exception e) {
            // 조용히 실패
        }
    }
}