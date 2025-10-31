package kr.tx24.api.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.api.conf.ApiConfigLoader;
import kr.tx24.lib.lb.LoadBalancer;
import kr.tx24.lib.redis.RedisUtils;
import kr.tx24.was.util.UADetect;
public class Initializer {

	private static final Logger logger = LoggerFactory.getLogger(Initializer.class);
	
	
	public static void start() {
		ApiConfigLoader.start();
		UADetect.initialize();			//UADetect 의 초기 로딩이 3~4초 가량 소요되므로 미리 로딩한다.
		logger.info(". redis started .. : [{}]",RedisUtils.ping());
	}
}
