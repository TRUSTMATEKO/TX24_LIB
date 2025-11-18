package kr.tx24.lib.logback;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.DateUtils;

public class LogUtils {
	
	private static final Logger logger 			= LoggerFactory.getLogger( LogUtils.class );
	
	
	public static final String LOG_DELIMITER 	= "\u001F";
	
	public static final String TRACE 			= "TRACE";
    public static final String DEBUG 			= "DEBUG";
    public static final String INFO 			= "INFO";
    public static final String WARN 			= "WARN";
    public static final String ERROR 			= "ERROR";
    public static final String OFF 				= "OFF";
    public static final String ALL 				= "ALL";
    
    // 정수 상수
    public static final int TRACE_INT 			= 5000;
    public static final int DEBUG_INT 			= 10000;
    public static final int INFO_INT 			= 20000;
    public static final int WARN_INT 			= 30000;
    public static final int ERROR_INT 			= 40000;
    public static final int OFF_INT 			= Integer.MAX_VALUE;
    public static final int ALL_INT 			= Integer.MIN_VALUE;
	
	
    
    
	private static final Map<String,Integer> LEVEL_MAP= Map.of(
			OFF		, OFF_INT,
			ERROR	, ERROR_INT,
			WARN	, WARN_INT,
			INFO	, INFO_INT,
			DEBUG	, DEBUG_INT,
			TRACE	, TRACE_INT,
			ALL		, ALL_INT
	);
	
	private static final int FIELD_COUNT 		= 10;
	private static final int PROC_NAME 			= 0;
	private static final int PROC_ID 			= 1;
	private static final int HOST 				= 2;
	private static final int DATE 				= 3;
	private static final int TIME 				= 4;
	private static final int THREAD 			= 5;
	private static final int LEVEL 				= 6;
	private static final int CLZ 				= 7;
	private static final int MDC 				= 8;
	private static final int MESSAGE 			= 9;
	
	
	
	
	
	public static LogEntry parse(String log){
		if (log == null || log.isEmpty()) {
			return null;
	    }
		
		String[] fields = log.split(Pattern.quote(LOG_DELIMITER), -1);

        if (fields.length != FIELD_COUNT) {
        	logger.warn("Mismatched log field count. Expected: {},  Actual:{}. Log :{}",FIELD_COUNT,fields.length,log);
        	return null;
        }

        try {
            return new LogEntry(fields);
        } catch (Exception e) {
            logger.error("Error creating LogEntry from fields: {}", e.getMessage());
            return null;
        }
	}
	
	
	
	
	public static List<LogEntry> parse(List<String> logs){
		if (logs == null || logs.isEmpty()) {
			return new ArrayList<>();
	    }
		
		ArrayList<LogEntry> logArray = new ArrayList<>();
		
		for(String log : logs) {
			LogEntry entry = parse(log);
			if(entry != null) {
				logArray.add(entry);
			}        
		}
		
		return logArray;
	}
	
	
	
	public static class LogEntry {
        // 모든 필드를 final로 선언하여 불변 객체(Immutable Object)로 만듭니다.
        public final String procName;
        public final String procId;
        public final String host;
        public final String day;
        public final String time;
        public final Timestamp regDate;
        public final String thread;
        public final String level;
        public final int levelInt;
        public final String clz;
        public final String mdc;
        public final String message;
        public final String tag;

        public LogEntry(String[] fields) {
            if (fields.length != FIELD_COUNT) {
                throw new IllegalArgumentException("Field count mismatch during LogEntry creation.");
            }
            this.procName 	= fields[PROC_NAME];
            this.procId 	= fields[PROC_ID];
            this.host 		= fields[HOST];
            this.day 		= fields[DATE];
            this.time 		= fields[TIME];
            this.regDate 	= DateUtils.toTimestamp(this.day+this.time,"yyyyMMddHHmmssSSS");
            this.thread 	= fields[THREAD];
            this.level 		= fields[LEVEL];
            this.levelInt 	= LEVEL_MAP.get(this.level);
            this.clz 		= fields[CLZ];
            this.mdc 		= fields[MDC];
            String job = "";
            String msg = fields[MESSAGE];
            
            if(this.levelInt != DEBUG_INT){
				if(msg.startsWith("#") && msg.indexOf(",") == 5){
					try {
						job = msg.substring(1,msg.indexOf(","));
						msg = msg.substring(msg.indexOf(",")+1);
					}catch(Exception e) {}
				}
			}
            this.tag = job;
            this.message=msg;
        }
        
        
    }
	
	
	
	
	
	
}

