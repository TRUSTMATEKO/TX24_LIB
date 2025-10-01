package kr.tx24.was.conf;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.JvmStatusUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

public class TomcatConfigLoader {

	private static Logger logger 				= LoggerFactory.getLogger(TomcatConfigLoader.class );
	private static final String CONFIG_PATH		= SystemUtils.getConfigDirectory()+File.separator+"server.json";
	private static TomcatConfig tomcatConfig = null; 
	
	
	public static TomcatConfig load() {
		if(tomcatConfig == null) {
			try {
				tomcatConfig 	= new JacksonUtils().deserialize(new File(CONFIG_PATH).toPath(), TomcatConfig.class);
				
				if(tomcatConfig == null || CommonUtils.isEmpty(tomcatConfig)) {
					throw new Exception(CONFIG_PATH + " config is emtpy");
				}
				
				
				if(!CommonUtils.isEmpty(tomcatConfig.parameter)) {
					for(String key: tomcatConfig.parameter.keySet()) {
						System.setProperty(key, tomcatConfig.parameter.get(key));
					}
				}
				
				JvmStatusUtils.setMaxThreads(tomcatConfig.maxThreads);
				
			} catch (Exception e) {
				logger.error("server config read error : {} , {}",CONFIG_PATH, e.getMessage()); // System.exit(0) 대신 에러 로깅
				System.exit(0);
			}
		}
		return tomcatConfig;
		
	}
}
