package kr.tx24.was.util;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.lettuce.core.TransactionResult;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.lang.IDUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.TypeRegistry;
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
	private static final String REDIS_SESSION_STORE			= "WS_";
	private static final String REDIS_PERSISTENT_SESSION	= "WPS_";
	
	private static int SESSION_EXPIRE_MINUTE 				= 30;
	private static int PERSISTENT_SESSION_EXPIRE_HOUR 		= 4;
	private static String KEYS_ALL					= "*";
	
	public static final String SESSION_KEY			= "XSESSION";
	public static final String SESSION_ID			= "wsid";
	public static final String SESSION_DATE			= "create";
	public static final String SESSION_USERID		= "userId";
	public static final String SESSION_PROC			= "proc";
	
	/**
	 * session id 생성 
	 */
	public static String createSessionId(){
		return IDUtils.genKey(16);
	}
	
	public static void setSessionExpireMinute(int minutes) {
		SessionUtils.SESSION_EXPIRE_MINUTE = minutes;
	}
	
	/**
	 * 영구 유지 세션의 시간을 설정한다.
	 * @param hour
	 */
	public static void setSessioinPersistentExpireHour(int hour) {
		SessionUtils.PERSISTENT_SESSION_EXPIRE_HOUR = hour;
	}
	
	
	public static int getSessionExpireMinute() {
		return SESSION_EXPIRE_MINUTE;
	}
	
	
	public static int getSessionPersistentExpireHour() {
		return PERSISTENT_SESSION_EXPIRE_HOUR;
	}
	
	
	/**
	 * 세션 키 (sessionKey) 생성 후 세션 Store( REDIS ) 에 저장 후 반환  
	 * session id 는 자동 생성되어 반환된다.
	 * @param userId
	 * @param data
	 */
	public static String create(String userId, boolean persistent, SharedMap<String,Object> data){
		return create(createSessionId(), userId, persistent, data);
	}
	
	
	
	/**
	 * 세션 키 (sessionKey) 생성 후 세션 Store( REDIS ) 에 저장 후 반환  
	 * WS^SESSIONID^USER_ID^TIMESTAMP^PROC
	 * @param sessionId
	 * @param userId
	 * @param data
	 */
	public static String create(String sessionId,String userId, boolean persistent ,SharedMap<String,Object> data){
		//세션 정보를 담는다.
		data.put(SESSION_ID		, sessionId);
		data.put(SESSION_USERID	, userId);
		data.put(SESSION_DATE	, DateUtils.getCurrentDate());
		data.put(SESSION_PROC	, SystemUtils.getLocalProcessName());
		
		RedisUtils.set(REDIS_SESSION_STORE+sessionId, data, Duration.ofMinutes(SESSION_EXPIRE_MINUTE).getSeconds());
		if(persistent) {
		     RedisUtils.set(REDIS_PERSISTENT_SESSION+sessionId, data, Duration.ofHours(PERSISTENT_SESSION_EXPIRE_HOUR).getSeconds());
		     if(SystemUtils.deepview()) {
					logger.info("persistent session created : {}",REDIS_PERSISTENT_SESSION+sessionId);
			}
		}
		
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
		
		TransactionResult result = RedisUtils.executeTransaction(tx -> {
			 tx.expire(REDIS_SESSION_STORE + sessionId		, Duration.ofMinutes(SESSION_EXPIRE_MINUTE).getSeconds());
			 tx.expire(REDIS_PERSISTENT_SESSION + sessionId	, Duration.ofHours(PERSISTENT_SESSION_EXPIRE_HOUR).getSeconds());
		});
		
		if (SystemUtils.deepview()) {
			for(int i=0; i < result.size() ; i++) {
				logger.info("session save : {}", CommonUtils.toString(result.get(i)));
			}
		}

	}
	
	
	/**
	 * 세션 업데이트 및 세션 저장 
	 * @param sessionId
	 * @param data
	 */
	public static void save(String sessionId, SharedMap<String, Object> data) {
	    if (CommonUtils.isBlank(sessionId)) {
	        logger.warn("SessionId is empty, cannot save session");
	        return;
	    }
	    
	    RedisUtils.setXx(REDIS_SESSION_STORE + sessionId			,data, Duration.ofMinutes(SESSION_EXPIRE_MINUTE).getSeconds());
	    RedisUtils.setXx(REDIS_PERSISTENT_SESSION + sessionId		,data, Duration.ofHours(PERSISTENT_SESSION_EXPIRE_HOUR).getSeconds());
	    
	}
	
	/**
	 * 세선에 저장되어 있는 데이터 가져오기
	 * 세션 연장여부 
	 * @param sessionId
	 * @param extend
	 * @return
	 */
	public static SharedMap<String,Object> getSession(String sessionId,boolean extend){
		if (CommonUtils.isBlank(sessionId)) {
			return null;
		}
		
		SharedMap<String, Object> result = null;
		if(extend) {
			result = RedisUtils.getEx(REDIS_SESSION_STORE + sessionId ,Duration.ofMinutes(SESSION_EXPIRE_MINUTE).getSeconds(),TypeRegistry.MAP_SHAREDMAP_OBJECT);
			RedisUtils.setXx(REDIS_PERSISTENT_SESSION + sessionId, Duration.ofHours(PERSISTENT_SESSION_EXPIRE_HOUR).getSeconds());
		}else {
			result = RedisUtils.get(REDIS_SESSION_STORE + sessionId ,TypeRegistry.MAP_SHAREDMAP_OBJECT);
		}
		
		return result;
	}
	
	
	/**
	 * 지속관리 세선에 저장되어 있는 데이터 가져오기
	 * 세션 연장여부 
	 * @param sessionId
	 * @param extend
	 * @return
	 */
	public static SharedMap<String,Object> getPersistentSession(String sessionId,boolean extend){
		if (CommonUtils.isBlank(sessionId)) {
			return null;
		}
		
		SharedMap<String, Object> result = null;
		if(extend) {
			result = RedisUtils.getEx(REDIS_PERSISTENT_SESSION + sessionId ,Duration.ofMinutes(SESSION_EXPIRE_MINUTE).getSeconds(),TypeRegistry.MAP_SHAREDMAP_OBJECT);
		}else {
			result = RedisUtils.get(REDIS_PERSISTENT_SESSION + sessionId ,TypeRegistry.MAP_SHAREDMAP_OBJECT);
		}
		
		return result;
	}
	
	
	
	/**
	 * 세선에 기록되어 있는 UserId 가져오기 
	 * @param sessionId
	 * @return
	 */
	public static String getUserIdBySessionId(String sessionId){
		
		if (CommonUtils.isBlank(sessionId)) {
			return "";
		}
		
		SharedMap<String, Object> result = getSession(sessionId,false);
		if (result == null || result.isEmpty()) {
			return "";
		} else {
			return result.getString(SESSION_USERID);
		}
		
	}
	
	
	/***
	 * 유효한 세션인지 여부 확인 
	 * @param sessionId
	 * @return
	 */
	public static boolean exists(String sessionId){
		if (CommonUtils.isBlank(sessionId)) {
			return false;
		}
		
		return RedisUtils.exists(REDIS_SESSION_STORE + sessionId);
	}
	
	/**
	 * 현재 세션의 만료 시간 리턴 / 초 
	 * 
	 * <p><b>변경:</b> RedisUtils.ttl() 사용</p>
	 * 
	 * @param sessionId
	 * @return TTL (초 단위), 키가 없으면 -2, TTL 없으면 -1
	 */
	public static long getExpire(String sessionId) {
		if (CommonUtils.isBlank(sessionId)) {
			return -2;
		}
		
		return RedisUtils.ttl(REDIS_SESSION_STORE + sessionId);
	}
	
	
	
	
	/**
	 * 현재 동일한 userId 로 접속해 있는 동접 정보 
	 * 
	 * <p><b>변경:</b> RedisUtils.scan() 및 Redis.sync() 사용, close() 제거</p>
	 * 
	 * @param userId
	 * @return
	 */
	
	public static List<SharedMap<String, Object>> getConCurrent(String userId) {
		List<SharedMap<String, Object>> list = new ArrayList<>();
		
		if (CommonUtils.isBlank(userId)) {
			return list;
		}
		
		// RedisUtils.scan()으로 패턴 매칭
		List<String> keys = RedisUtils.scan(REDIS_SESSION_STORE + KEYS_ALL);
		if(CommonUtils.isEmpty(keys)) {
			return list;
		}
		
		List<SharedMap<String, Object>> values = RedisUtils.mget(keys, TypeRegistry.LIST_SHAREDMAP_OBJECT);
		
		for (SharedMap<String,Object> result : values) {
			if (result != null && result.isEquals(SESSION_USERID, userId)) {
				list.add(result);
			}
		}
		
		list = list.stream()
				.sorted((o1, o2) -> o1.getString(SESSION_DATE).compareTo(o2.getString(SESSION_DATE)))
				.collect(Collectors.toList());
		
		return list;
	}
	
	
	
	/**
	 * 현재 시스템에 접속한 모든 세션 정보 
	 * 
	 * <p><b>변경:</b> RedisUtils 활용, close() 제거</p>
	 * 
	 * @return
	 */

	public static List<SharedMap<String, Object>> getSessions() {
		List<SharedMap<String, Object>> list = new ArrayList<>();
		
		// RedisUtils.scan()으로 패턴 매칭
		List<String> keys = RedisUtils.scan(REDIS_SESSION_STORE + KEYS_ALL);
		if(CommonUtils.isEmpty(keys)) {
			return list;
		}
		
		// 2. MGET으로 한 번에 조회 
	    list = RedisUtils.mget(keys, TypeRegistry.LIST_SHAREDMAP_OBJECT);
	    
		// 생성 시간 순으로 정렬
		list = list.stream()
				.sorted((o1, o2) -> o1.getString(SESSION_DATE).compareTo(o2.getString(SESSION_DATE)))
				.collect(Collectors.toList());
		
		return list;
	}
	
	/**
	 * 현재 세션 삭제 
	 * 
	 * <p><b>변경:</b> RedisUtils.del() 사용, close() 제거</p>
	 * 
	 * @param sessionId
	 */
	public static void destory(String sessionId) {
		if (CommonUtils.isBlank(sessionId)) {
			return;
		}
		
		String redisKey = REDIS_SESSION_STORE + sessionId;
		
		// 삭제 전 세션 정보 조회 (로깅용)
		SharedMap<String, Object> sessionMap = RedisUtils.get(redisKey, TypeRegistry.MAP_SHAREDMAP_OBJECT);
		if (sessionMap != null) {
			if (SystemUtils.deepview()) {
				logger.info("session destory : {}, {}", 
						sessionMap.getString(SESSION_USERID), redisKey);
			}
		}
		
		// RedisUtils.del()로 삭제
		RedisUtils.del(redisKey);
	}

}
