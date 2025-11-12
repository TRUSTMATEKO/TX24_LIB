package kr.tx24.was.conf;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

public class TomcatConfigLoader {

	private static Logger logger 				= LoggerFactory.getLogger(TomcatConfigLoader.class );
	private static final Path CONFIG_PATH		= SystemUtils.getWasConfigPath();
	private static TomcatConfig tomcatConfig = null; 
	
	
	public static TomcatConfig load() {
		if(tomcatConfig == null) {
			try {
				tomcatConfig 	= new JacksonUtils().fromJson(CONFIG_PATH, TomcatConfig.class);
				
				if(tomcatConfig == null || CommonUtils.isEmpty(tomcatConfig)) {
					throw new Exception(CONFIG_PATH + " config is emtpy");
				}
				
				
				if(!CommonUtils.isEmpty(tomcatConfig.parameter)) {
					for(String key: tomcatConfig.parameter.keySet()) {
						System.setProperty(key, tomcatConfig.parameter.get(key));
					}
				}
			} catch (Exception e) {
				logger.error("server config read error : {} , {}",CONFIG_PATH, e.getMessage()); // System.exit(0) 대신 에러 로깅
				System.exit(0);
			}
		}
		return tomcatConfig;
		
	}
}
