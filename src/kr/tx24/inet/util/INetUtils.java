package kr.tx24.inet.util;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.redis.RedisUtils;

public class INetUtils {

public static String INET_TRX_ID = "INET_TRX_ID";
	
	
	public INetUtils() {
	}
	
	 
	public static String getTrxId() {
		long seqNo = RedisUtils.incr(INET_TRX_ID);
		if(seqNo > 999999){
			RedisUtils.set(INET_TRX_ID,"1");
			seqNo = 1;
		}
		
		return DateUtils.getCurrentDay("yyMMdd") +
				CommonUtils.paddingZero(seqNo,6);
	}
}
