package kr.tx24.lib.logback;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * Advanced LogBack Configurator
 * - Console / File / Redis Async Appender
 * - MaskConverter 적용
 * - Environment-based activation
 */
public class LogBackConfigure extends ContextAwareBase implements Configurator {

    private static final boolean[] LOG_APPENDER = new boolean[]{true, false, false};
    private static Logger ROOT_LOGGER;
    
    
    private final ScheduledExecutorService hookScheduler = 
            Executors.newSingleThreadScheduledExecutor(r -> {
                // JVM 종료를 막지 않도록 데몬 스레드로 설정합니다.
                Thread t = new Thread(r, "ShutdownHook-Thread");
                t.setDaemon(true); 
                return t;
            });
    
    static {
        // Logback 내부 상태 메시지를 OFF로 설정
        System.setProperty("logback.statusListener", "ch.qos.logback.core.status.NopStatusListener");
        // 또는, 콘솔 메시지 레벨 자체를 조정
        // System.setProperty("logback.status.level", "OFF");
    }
    

    @Override
    public ExecutionStatus configure(LoggerContext ctx) {
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
        
        //아래는 불필요해서 제거 
        //ctx.setMDCAdapter(MDC.getMDCAdapter());

        // MaskConverter 등록
        PatternLayout.DEFAULT_CONVERTER_MAP.put("masked", MaskConverter.class.getName());

        // Console Appender
        if (LOG_APPENDER[0]) {
            PatternLayoutEncoder consoleEncoder = new PatternLayoutEncoder();
            consoleEncoder.setContext(ctx);
            consoleEncoder.setPattern("%d{HH:mm:ss.SSS} [%.7thread] %-5level %logger{16} -%mdc:%masked%n");
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
            String redisPattern = String.join("||",
                    "V1",
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
        	System.err.println(MsgUtils.format("LOG   : console={}, local={}, remote={}", LOG_APPENDER[0], LOG_APPENDER[1], LOG_APPENDER[2]));
        }
        
        
        hookScheduler.schedule(() -> {
            try {
                // 실제 Shutdown Hook 등록
                Runtime.getRuntime().addShutdownHook(new ShutdownHook());
            } catch (IllegalStateException e) {
                addWarn("Application shutdown already in progress. Failed to register ShutdownHook.", e);
            }
        }, 100, TimeUnit.MILLISECONDS);
        
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
        ctx.getLogger("io.lettuce").setLevel(Level.INFO);
        ctx.getLogger("reactor.util").setLevel(Level.INFO);
        ctx.getLogger("org.thymeleaf").setLevel(Level.INFO);
        ctx.getLogger("org.springframework").setLevel(Level.INFO);
        ctx.getLogger("org.mariadb.jdbc").setLevel(Level.INFO);
        ctx.getLogger("com.password4j").setLevel(Level.INFO);
    }
}
