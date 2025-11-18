package kr.tx24.lib.lifecycle;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.inet.server.INetServer;
import kr.tx24.lib.db.DBManager;
import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lb.LoadBalancer;
import kr.tx24.lib.logback.RedisAppender;
import kr.tx24.lib.redis.Redis;
import kr.tx24.lib.redis.RedisPubSub;
import kr.tx24.was.main.Server;

public class SystemManager extends Thread {

	protected static Logger logger = LoggerFactory.getLogger(SystemManager.class);
	public static final String PROCESS_STARTED	 	= "#started,";
	public static final String PROCESS_STOPPED	 	= "#shutdown,";
	private static long PROCESS_ID	= System.currentTimeMillis()/1000;
	
	private static final List<ShutdownManager> PRIORITY_SHUTDOWN_HOOKS = new LinkedList<>();
	
	public SystemManager(){
		try {
	        Thread.sleep(100);
	    } catch (InterruptedException e) {
	        Thread.currentThread().interrupt();
	    }
		logger.info("{}{}",PROCESS_STARTED,PROCESS_ID);
		
	}
	
	
	public static long getProcessId() {
		return PROCESS_ID;
	}
	
	
	
	public static void registerShutdownHook(ShutdownManager hook) {
        if (hook != null) {
            ((LinkedList<ShutdownManager>) PRIORITY_SHUTDOWN_HOOKS).addFirst(hook); 
            logger.debug("Registered shutdown hook: {}", hook.getClass().getName());
        }
	}
	
	@Override
	public void run() {
	    try {
	        logger.info("{}{}", PROCESS_STOPPED, PROCESS_ID);
	        logger.info("{}","\n\n\n");
	        
	        for (ShutdownManager hook : PRIORITY_SHUTDOWN_HOOKS) {
                String componentName = hook.getClass().getSimpleName();
                shutdownSafely(componentName, () -> hook.shutdown());
            }
	        
	        // 각 컴포넌트를 안전하게 종료
	        // 주의: 메서드 참조 대신 람다를 사용하여 지연 실행
	        shutdownSafely("INetServer"			, () -> INetServer.shutdown());
	        shutdownSafely("Tomcat Was"			, () -> Server.shutdown());
	        shutdownSafely("INet"				, () -> INet.shutdown());
	        shutdownSafely("LoadBalancer"		, () -> LoadBalancer.shutdown());
	        
	        sleep(300);
	        
	        shutdownSafely("JvmStatusManager"	, () -> JvmStatusManager.shutdown());
	        shutdownSafely("AsyncExecutor"		, () -> AsyncExecutor.shutdown());
	        
	        sleep(200);
	        
	        shutdownSafely("DBManager"			, () -> DBManager.shutdown());
	        
	        sleep(200);
	        
	        shutdownSafely("Redis"				, () -> Redis.shutdown());
	        shutdownSafely("RedisPubSub"		, () -> RedisPubSub.shutdown());
	        
	        sleep(100);
	        
	        shutdownSafely("RedisAppender"		, () -> RedisAppender.shutdown());  // 마지막에 실행
	        
	        sleep(100);
	        System.out.println("system shutdown ... ");
	        
	    } catch (Throwable t) {
	        //System.err.println("shutdown error " + CommonUtils.getExceptionMessage(t));
	    } finally {
	        System.out.println("system exit ");
	    }
	}

	/**
	 * 개별 컴포넌트 shutdown을 안전하게 처리
	 * NoClassDefFoundError를 포함한 모든 Throwable 처리
	 * 
	 * @param componentName 컴포넌트 이름
	 * @param shutdownAction shutdown 실행 Runnable
	 */
	private void shutdownSafely(String componentName, Runnable shutdownAction) {
	    try {
	        shutdownAction.run();        
	    } catch (NoClassDefFoundError e) {
	        // 클래스가 언로드되었거나 찾을 수 없는 경우
	    	//System.err.println(componentName + " shutdown skipped - class not available: " + e.getClass().getSimpleName());
	        
	    } catch (Error e) {
	        // 기타 Error (OutOfMemoryError, StackOverflowError 등)
	        // System.err.println(componentName + " shutdown failed with error: " + e.getClass().getSimpleName() + " - " + e.getMessage());
	        
	    } catch (Exception e) {
	        // 일반 Exception
	        //String errorMsg = componentName + " shutdown error: " + CommonUtils.getExceptionMessage(e);
	        try {
	            //System.err.println(errorMsg);
	        } catch (Throwable t) {
	            //System.err.println(errorMsg);
	        }
	        
	    } catch (Throwable t) {
	        // 예상치 못한 모든 문제
	        //System.err.println(componentName + " shutdown failed with unexpected error: " + t.getClass().getName());
	    }
	}
	
	
	

}
