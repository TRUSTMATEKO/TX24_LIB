package kr.tx24.lib.logback;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

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
    private static final String delimiter = "\u001F";
    private static Logger ROOT_LOGGER;
    
    
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
            String redisPattern = String.join(delimiter,
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
        	System.err.println(MsgUtils.format("LOG   : console={}, local={}, remote={}", LOG_APPENDER[0], LOG_APPENDER[1], LOG_APPENDER[2]));
        }
        

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
    
    
    public class LogParserUtils {

        // LogBackConfigure.java에서 사용된 Unit Separator (ASCII 31)
        private static final String DELIMITER = "\u001F";
        // 로그 필드의 총 개수 (V1부터 %masked까지 11개)
        private static final int EXPECTED_FIELD_COUNT = 11;

        /**
         * Unit Separator를 사용하여 로그 라인을 파싱하고 필드 목록을 반환합니다.
         * @param logLine Redis에서 수신된 로그 문자열
         * @return 파싱된 로그 필드의 리스트
         * @throws IllegalArgumentException 필드 개수가 일치하지 않을 경우
         */
        public static List<String> parseLog(String logLine) {
            if (logLine == null || logLine.isEmpty()) {
                throw new IllegalArgumentException("Log line must not be null or empty.");
            }
            
            // 1. String.split() 사용 시의 안정성 확보
            //    - limit 인자(-1)는 마지막 필드가 비어 있더라도 배열에 포함되도록 보장합니다.
            //    - Pattern.quote()는 구분자가 정규식 특수 문자가 아닌 일반 문자열로 취급되도록 합니다.
            //      (현재 \u001F는 안전하지만, 습관적으로 사용하면 좋습니다.)
            String[] fields = logLine.split(Pattern.quote(DELIMITER), -1);

            // 2. 필드 개수 확인
            if (fields.length != EXPECTED_FIELD_COUNT) {
                throw new IllegalArgumentException(
                    "Mismatched log field count. Expected: " + EXPECTED_FIELD_COUNT + 
                    ", Actual: " + fields.length + ". Log: " + logLine
                );
            }

            return Arrays.asList(fields);
        }
        
        // 로그 필드 인덱스를 정의하여 접근 시 가독성을 높일 수 있습니다.
        public static class LogFields {
            public static final int VERSION = 0;
            public static final int PROCESS_NAME = 1;
            public static final int PROCESS_ID = 2;
            public static final int HOST_INFO = 3;
            public static final int DATE = 4;
            public static final int TIME = 5;
            public static final int THREAD = 6;
            public static final int LEVEL = 7;
            public static final int LOGGER_NAME = 8;
            public static final int MDC = 9;
            public static final int MESSAGE = 10;
        }
        
        // 사용 예시
        public static void main(String[] args) {
            // LogBackConfigure에서 생성된 가상의 로그 라인
            String sampleLog = 
                "V1" + DELIMITER + 
                "MyService" + DELIMITER + 
                "12345" + DELIMITER + 
                "server1.local,192.168.1.10" + DELIMITER + 
                "20231025" + DELIMITER + 
                "103000500" + DELIMITER + 
                "http-nio-8080-exec-1" + DELIMITER + 
                "INFO " + DELIMITER + 
                "c.a.b.MyClass" + DELIMITER + 
                "{reqId=abc}" + DELIMITER + 
                "User login success: {id: *****, name: 홍**}";

            try {
                List<String> parsedLog = parseLog(sampleLog);
                
                System.out.println("Log Version: " + parsedLog.get(LogFields.VERSION));
                System.out.println("Process Name: " + parsedLog.get(LogFields.PROCESS_NAME));
                System.out.println("Level: " + parsedLog.get(LogFields.LEVEL).trim()); // trim() 처리 필요
                System.out.println("Message: " + parsedLog.get(LogFields.MESSAGE));
                
            } catch (IllegalArgumentException e) {
                System.err.println("Parsing Error: " + e.getMessage());
            }
        }
    }
}
