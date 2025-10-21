package kr.tx24.api.util;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
 
public class CacheMap  {
	private static Logger logger = LoggerFactory.getLogger( CacheMap.class );
	private LoadingCache<String, byte[]> cache = null;
	
	public CacheMap(){
		this(10);
	}

	public CacheMap(int expireInMinutes) {
		init(expireInMinutes);
	};
	
	private void init(int expireInMinutes) {
		RemovalListener<String, byte[]> removalListener = new RemovalListener<String, byte[]>() {
			public void onRemoval(RemovalNotification<String, byte[]> removal) {
				if (removal.getCause() == RemovalCause.EXPIRED) {
					processAfterExpire(removal.getKey(), removal.getValue());
				} else if (removal.getCause() == RemovalCause.REPLACED) {
				} else {
				}
			}
		};

		cache = CacheBuilder.newBuilder()
		.maximumSize(99999999)
		.expireAfterWrite(expireInMinutes, TimeUnit.MINUTES)
		.removalListener(removalListener).build(new CacheLoader<String, byte[]>() {
			public byte[] load(String key) {
				return getUnchecked(key);
			}
		});

	}
	
	/**
	 * Cache의 Key와 관련된 값을 반환한다.
	 * @param key
	 * @return
	 */
	public byte[] getUnchecked(String key){
		byte[] val = null;
		try{
			val =cache.getUnchecked(key);
		}catch(Exception e){}
		return val;
	}
	
	/**
	 * 해당 Key 값이 있는지 확인한다.
	 * @param key
	 * @return
	 */
	public boolean containsKey(String key){
		return cache.asMap().containsKey(key);
	}
	
	/**
	 * 저장된 Key 값을 반환한다.
	 * @param key
	 * @return
	 */
	public Set<String> keySet(String key){
		return cache.asMap().keySet();
	}
	
	/**
	 * Data를 Cache Memory에 저장 후 값을 반환한다.
	 * @param key
	 * @param value
	 * @return
	 */
	public byte[] put(String key , byte[] value){
		if(value != null){
			cache.put(key, value);
		}
		return value;
	}
	
	/**
	 * Data를 Cache Memory에 저장한다.
	 * @param key
	 * @param value
	 */
	public void add(String key , byte[] value){
		if(value != null){
			cache.put(key, value);
		}
	}
	
	/**
	 * Key 값을 삭제한다.
	 * @param key
	 */
	public void delete(String key) {
		cache.invalidate(key);
		
	}
	
	/**
	 * Key 값을 가져온다.
	 * @param key
	 * @return
	 */
	public byte[] get(String key){
		byte[] val = null;
		try{
			val =cache.get(key);
		}catch(Exception e){}
		return val;
	}
	
	/**
	 * Cache Memory의 Size를 가져온다.
	 * @return
	 */
	public long size(){
		return cache.size();
	}
	
	/**
	 * Cache Memory를 정리한다.
	 */
	public void cleanUp(){
		cache.cleanUp();
	}
	

	/**
	 * Cache 객체를 반환한다.
	 * @return
	 */
	public LoadingCache<String, byte[]> getCache() {
		return cache;
	}
	
	
	public void processAfterExpire(String key, byte[] value){
		logger.debug("cache timeout : key : {} ",key);
	}
	

}
