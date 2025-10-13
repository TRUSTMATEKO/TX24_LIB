package kr.tx24.lib.logback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.db.DBManager;
import kr.tx24.lib.lang.AsyncExecutor;
import kr.tx24.lib.lang.JvmStatusUtils;
import kr.tx24.lib.redis.Redis;


/**
 * 
 */
public class ShutdownHook extends Thread {

	protected static Logger logger = LoggerFactory.getLogger(ShutdownHook.class);
	public static final String PROCESS_STARTED	 	= "#process started,";
	public static final String PROCESS_STOPPED	 	= "#process shutdown,";
	private static long PROCESS_ID					= System.currentTimeMillis();
	
	public ShutdownHook(){
		System.err.println("ShutdownHook enabled");
		logger.info("{}{}",PROCESS_STARTED,PROCESS_ID);
		
	}
	
	@Override
	public void run(){
		try{
			logger.info("{}{},{}",PROCESS_STOPPED,PROCESS_ID,System.currentTimeMillis());
			JvmStatusUtils.shutdown();
			System.err.println("Jdbc         : connection shutdown ");
			DBManager.shutdown();
			System.err.println("Process      : shutting down ..... ");
			sleep(200);
			RedisAppender.shutdown();
			Redis.shutdown();
			System.err.println("Redis        : shutting down ..... ");
			 // AsyncExecutor 종료 추가
            AsyncExecutor.shutdown();
		}catch(Exception e){
			
		}finally{
			System.err.println("System exit ..... ");
		}
		
	}
	

}
