package kr.tx24.lib.logback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.MaskUtils;

public class LogUtils {
	
	private static final Logger logger 				= LoggerFactory.getLogger( MaskUtils.class );
	
	
	public static final String LOG_DELIMITER_V1 = "||";
	public static final String LOG_DELIMITER_V2 = "\u001F";
	public static final int LOG_EXPECTED_FIELD_COUNT = 11;
	
	
	public static final int LOG_VERSION = 0;
	public static final int LOG_PROCESS_NAME = 1;
	public static final int LOG_PROCESS_ID = 2;
	public static final int LOG_HOST_INFO = 3;
	public static final int LOG_DATE = 4;
	public static final int LOG_TIME = 5;
	public static final int LOG_THREAD = 6;
	public static final int LOG_LEVEL = 7;
	public static final int LOG_LOGGER_NAME = 8;
	public static final int LOG_MDC = 9;
	public static final int LOG_MESSAGE = 10;
	
	
	public static List<String> parse(String log) throws IllegalArgumentException{
		if(log.startsWith("V1")) {
			return parseV1(log);
		}else if(log.startsWith("V2")) {
			return parseV2(log);
		}else {
			throw new IllegalArgumentException("Log line must starts with V1,V2");
		}
	}
	
	
	public static List<String> parseV1(String log){
		if (log == null || log.isEmpty()) {
			return null;
	    }
		
		String[] fields = log.split(Pattern.quote(LOG_DELIMITER_V1), -1);

        if (fields.length != LOG_EXPECTED_FIELD_COUNT) {
        	logger.info("Mismatched log field count. Expected: {},  Actual:{}. Log :{}",LOG_EXPECTED_FIELD_COUNT,fields.length,log);
        	//return CommonUtils.padList(Arrays.asList(fields), EXPECTED_FIELD_COUNT, "");
        	return null;
        }

        return Arrays.asList(fields);
		
	}
	
	
	public static List<String> parseV2(String log){
		if (log == null || log.isEmpty()) {
			return null;
	    }
		
		String[] fields = log.split(Pattern.quote(LOG_DELIMITER_V2), -1);

        if (fields.length != LOG_EXPECTED_FIELD_COUNT) {
        	logger.info("Mismatched log field count. Expected: {},  Actual:{}. Log :{}",LOG_EXPECTED_FIELD_COUNT,fields.length,log);
        	
        	//return CommonUtils.padList(Arrays.asList(fields), EXPECTED_FIELD_COUNT, "");
        	return null;
        }

        return Arrays.asList(fields);
	}
	
	
	public static List<List<String>> parseV2(List<String> logs)throws IllegalArgumentException{
		if (logs == null || logs.isEmpty()) {
			 throw new IllegalArgumentException("Log line must not be null or empty.");
	    }
		
		ArrayList<List<String>> logArray = new ArrayList<List<String>>();
		
		for(String log : logs) {
			List<String> result = parseV2(log);
			if(result != null) {
				logArray.add(result);
			}        
		}
		
		return logArray;
	}
	
	
}

