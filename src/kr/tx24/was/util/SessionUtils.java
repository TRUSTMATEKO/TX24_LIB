package kr.tx24.was.util;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.IDUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.redis.Redis;
import kr.tx24.lib.redis.RedisUtils;

/**
 * @author juseop
 * Client 는 Cookie 에 CYSESSION 으로 SESSION_ID 만 저장한다.
 * 매 요청 시 Cookie 의 SESSION 을 가져와 SESSION_STORE(REDIS)의 SESSION 존재 여부를 확인 또는 갱신한다.
 * SESSION 을 사용하는 접속자는 응답시 Cookie 의 MaxAge 를 갱신한다.
 *
 */
public class SessionUtils {

	private static Logger logger = LoggerFactory.getLogger( SessionUtils.class );
	private static String REDIS_SESSION_STORE		= "WSDATA|";
	private static String KEYS_ALL					= "*";
	public static String SESSION_ID					= "session_id";
	public static String SESSION_DATE				= "session_create";
	public static String SESSION_USERID				= "session_userId";
	public static String SESSION_PROC				= "session_proc";
	
	/**
	 * session id 생성 
	 */
	public static String createSessionId(){
		return IDUtils.genKey(16).toUpperCase();
	}
	
	
	/**
	 * 세션 키 (sessionKey) 생성 후 세션 Store( REDIS ) 에 저장 후 반환  
	 * session id 는 자동 생성되어 반환된다.
	 * @param userId
	 * @param data
	 */
	public static String create(String userId, SharedMap<String,Object> data){
		return create(IDUtils.genKey(16).toUpperCase(), userId, data);
	}
	
	
	
	/**
	 * 세션 키 (sessionKey) 생성 후 세션 Store( REDIS ) 에 저장 후 반환  
	 * WS^SESSIONID^USER_ID^TIMESTAMP^PROC
	 * @param sessionId
	 * @param userId
	 * @param data
	 */
	public static String create(String sessionId,String userId ,SharedMap<String,Object> data){
		//세션 정보를 담는다.
		data.put(SESSION_ID		, sessionId);
		data.put(SESSION_USERID	, userId);
		data.put(SESSION_DATE	, System.currentTimeMillis());
		data.put(SESSION_PROC	, SystemUtils.getLocalProcessName());
		
		
		StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
		RedisCommands<String, SharedMap<String, Object>> cmd = conn.sync();
		cmd.set(REDIS_SESSION_STORE+sessionId	,data);
		cmd.expire(REDIS_SESSION_STORE+sessionId ,Was.SESSION_EXPIRE);
		conn.close();
		
		if(SystemUtils.deepview()) {
			logger.info("session created : {}",REDIS_SESSION_STORE+sessionId);
		}
		
		return sessionId;
	}
	
	
	/**
	 * 세션 업데이트 및 세션 저장 
	 * SharedMap<String,Object> data 의 sessionId 를 이용하여 자동 저장 
	 * @param key
	 * @param data
	 */
	public static void save(SharedMap<String,Object> data){
		save(data.getString(SESSION_ID),data);
	}
	
	/**
	 * sessionId 의 유효기간만 연장하기 
	 * @param sessionId
	 */
	public static void save(String sessionId) {
		StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
		
		RedisAsyncCommands<String, SharedMap<String, Object>> cmd = conn.async();
		cmd.expire(REDIS_SESSION_STORE+sessionId,Was.SESSION_EXPIRE);
		conn.close();
		
		if(SystemUtils.deepview()) {
			logger.info("session save : {}",REDIS_SESSION_STORE+sessionId);
		}
	}
	
	
	/**
	 * 세션 업데이트 및 세션 저장 
	 * @param sessionId
	 * @param data
	 */
	public static void save(String sessionId, SharedMap<String, Object> data) {
	    if (CommonUtils.isEmpty(sessionId)) {
	        logger.warn("SessionId is empty, cannot save session");
	        return;
	    }

	    try (StatefulRedisConnection<String, SharedMap<String, Object>> conn = Redis.getSharedMap()) {
	        RedisAsyncCommands<String, SharedMap<String, Object>> cmd = conn.async();

	        cmd.set(REDIS_SESSION_STORE + sessionId, data).thenCompose(v ->
	            cmd.expire(REDIS_SESSION_STORE + sessionId, Was.SESSION_EXPIRE)
	        ).toCompletableFuture().join();

	        if (SystemUtils.deepview()) {
	            logger.info("Session saved: {}", REDIS_SESSION_STORE + sessionId);
	        }
	    }
	}
	
	/**
	 * 세선에 저장되어 있는 데이터 가져오기 
	 * @param sessionId
	 * @return
	 */
	public static SharedMap<String,Object> getBySessionId(String sessionId){
		SharedMap<String,Object> result = null;
		StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
		RedisCommands<String, SharedMap<String, Object>> cmd = conn.sync();
		
		result = cmd.get(REDIS_SESSION_STORE+sessionId);
		if(result != null) {
			cmd.expire(REDIS_SESSION_STORE+sessionId,Was.SESSION_EXPIRE);
		}
		
		conn.close();
		return result;
	}
	
	
	/**
	 * 세선에 기록되어 있는 UserId 가져오기 
	 * @param sessionId
	 * @return
	 */
	public static String getUserIdBySessionId(String sessionId){
		SharedMap<String,Object> result = null;
		StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
		RedisCommands<String, SharedMap<String, Object>> cmd = conn.sync();
		
		result = cmd.get(REDIS_SESSION_STORE+sessionId);
		if(result != null) {
			cmd.expire(REDIS_SESSION_STORE+sessionId,Was.SESSION_EXPIRE);
		}
		
		conn.close();
		if(result == null) {
			return "";
		}else {
			return result.getString(SESSION_USERID);
		}
	}
	
	
	/***
	 * 유효한 세션인지 여부 확인 
	 * @param sessionId
	 * @return
	 */
	public static boolean exists(String sessionId){
		long count = 0;
		StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
		RedisCommands<String, SharedMap<String, Object>> cmd = conn.sync();
		
		count = cmd.exists(REDIS_SESSION_STORE+sessionId);
		if(count > 0) {
			return true;
		}else {
			return false;
		}
	}
	
	/**
	 * 현재 세션의 만료 시간 리턴 / 초 
	 * @param sessionId
	 * @return
	 */
	public static long getExpire(String sessionId){
		return RedisUtils.ttl(REDIS_SESSION_STORE+sessionId);
	}
	
	
	
	
	/**
	 * 현재 동일한 userId 로 접속해 있는 동접 정보 
	 * @param userId
	 * @return
	 */
	public static List<SharedMap<String,Object>> getConCurrent(String userId){
		List<SharedMap<String,Object>> list = new ArrayList<SharedMap<String,Object>>();
		List<String> keys = RedisUtils.scan(REDIS_SESSION_STORE+KEYS_ALL);
		
		if(!CommonUtils.isEmpty(keys)) {
			
			StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
			RedisCommands<String, SharedMap<String, Object>> cmd = conn.sync();
			
			for(String key: keys) {
				SharedMap<String,Object> result = cmd.get(key);
				if(result != null && result.isEquals(SESSION_USERID, userId)) {
					list.add(result);
				}
			}
		}
		
		if(SystemUtils.deepview()) {
			if(keys == null) {
				logger.info("current session count  : 0");
			}else {
				logger.info("current session count  : {}",keys.size());
			}
		}
		
		return list;
	}
	
	
	
	/**
	 * 현재 시스템에 접속한 모든 세션 정보 
	 * @return
	 */
	public static List<SharedMap<String,Object>> getSessions(){
		List<SharedMap<String,Object>> list = new ArrayList<SharedMap<String,Object>>();
		List<String> keys = RedisUtils.scan(REDIS_SESSION_STORE+KEYS_ALL);
		
		if(!CommonUtils.isEmpty(keys)) {
			
			StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
			RedisCommands<String, SharedMap<String, Object>> cmd = conn.sync();
			
			for(String key: keys) {
				SharedMap<String,Object> result = cmd.get(key);
				if(result != null) {
					list.add(result);
				}
			}
			
			 list = list.stream().sorted((o1, o2) ->  
			 		o1.getString(SESSION_DATE).compareTo(o2.getString(SESSION_DATE)) 
			 		).collect(Collectors.toList());
		}
		
		//logger.info("session ==?> {}", new JacksonUtils().toPrettyJson(list));
		
		if(SystemUtils.deepview()) {
			if(keys == null) {
				logger.info("current session count  : 0");
			}else {
				logger.info("current session count  : {}",keys.size());
			}
		}
		
		
		return list;
	}
	
	
	/**
	 * 현재 세션 삭제 
	 * @param sessionId
	 */
	public static void destory(String sessionId) {
		
		
		StatefulRedisConnection<String, SharedMap<String,Object>> conn = Redis.getSharedMap();
		RedisCommands<String, SharedMap<String, Object>> cmd = conn.sync();
		
		SharedMap<String,Object> sessionMap = cmd.get(REDIS_SESSION_STORE+sessionId);
		if(sessionMap != null) {
			if(SystemUtils.deepview()) {
				logger.info("session destory : {},{}",sessionMap.getString(SessionUtils.SESSION_USERID),REDIS_SESSION_STORE+sessionId);
			}
			
		}
		cmd.del(REDIS_SESSION_STORE+sessionId);
		
		conn.close();	
	}
	
	
	/**
	 * 나를 제외한 세션 삭제
	 * @param sessionId
	 * @param userId
	 */
	public static void destoryExcludeMe(String sessionId, String userId) {
		List<SharedMap<String,Object>> list = getConCurrent(userId);
		
		if(!CommonUtils.isEmpty(list) && list.size() > 1) {
			for(SharedMap<String,Object> map : list) {
				if(!map.isEquals(SESSION_ID, sessionId)) {
					RedisUtils.del(REDIS_SESSION_STORE+map.getString(SESSION_ID));
					if(SystemUtils.deepview()) {
						logger.info("session destory : {}",REDIS_SESSION_STORE+map.getString(SESSION_ID));
					}	
				}
			}
		}	
	}
	
	
	
	

}
