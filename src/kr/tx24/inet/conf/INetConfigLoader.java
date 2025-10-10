package kr.tx24.inet.conf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.mapper.JacksonUtils;

public class INetConfigLoader {
	
	private static final Logger logger = LoggerFactory.getLogger(INetConfigLoader.class);
	private static Path CONFIG_PATH		= Paths.get(SystemUtils.getConfigDirectory(),"inet.json");
	private static volatile SharedMap<String,Object> configMap = null;
	
	
	public static String getHost() {
		return configMap.getString("host", "0.0.0.0");
	}
	
	
	public static int getPort() {
		return configMap.getInt("port", 10000);
	}
	
	public static String getBasePackage() {
		return configMap.getString("basePackage", "kr.tx24");
	}
	
	
	
	public static boolean enableLoggingHandler() {
		if(configMap.containsKey("logging")) {
			return configMap.getBoolean("logging");
		}else {
			return false;
		}
	}
	
	
	
	public static LinkedHashMap<String,Object> getMap(String key){
		return configMap.getLinkedHashMap(key);
	}
	

	public static <T> T get(String key, Class<T> type) {
		if(!configMap.containsKey(key)) {
			return null;
		}
		T instance = null;
		try {
			LinkedHashMap<String,Object> map = configMap.getLinkedHashMap(key);
			instance = new JacksonUtils().mapToObject(map, type);
		}catch(Exception e) {
		}
		return instance;
	}
	
	public static void start() {
		
		
		if(!Files.exists(CONFIG_PATH)) {
			System.out.println("{} is not found : "+ CONFIG_PATH.toAbsolutePath());
			logger.info("{} is not found : {}",CONFIG_PATH.toAbsolutePath());
			System.exit(1);			
		}else {
			
			try {
				
				if(configMap == null) {
					configMap = new JacksonUtils().fromJsonSharedMapObject(CommonUtils.toString(Files.readAllBytes(CONFIG_PATH),StandardCharsets.UTF_8));
				}
			
				if(CommonUtils.isEmpty(configMap)) {
                    throw new IllegalStateException("Loaded config is empty.");
                }
				
				if(configMap.get("property") != null) {
					LinkedHashMap<String,Object> propertiesMap = configMap.getLinkedHashMap("property");
					propertiesMap.forEach((key, value) -> {
				        if (key != null && value != null) {
				        	String v = CommonUtils.toString(value);
				            System.setProperty(key, v);
				            if(SystemUtils.deepview()) {
				            	logger.info("System property set: {}={}", key, v);
				            }
				        }
				    });
				}
				
				logger.info("Config reloaded: {}", CONFIG_PATH.toAbsolutePath());
				
			}catch(Exception e) {
				logger.warn("Failed to load config {} : {}",CONFIG_PATH.toAbsolutePath(),e.getMessage());
				System.out.println("config is not loaded -> system.exit");
				System.exit(1);
			}
		}
		
		
	}
	
}
