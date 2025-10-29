package kr.tx24.inet.conf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.TypeRegistry;
import kr.tx24.lib.mapper.JacksonUtils;

public class INetConfigLoader {
	
	private static final Logger logger = LoggerFactory.getLogger(INetConfigLoader.class);
	private static Path CONFIG_PATH	   = SystemUtils.getINetConfigPath();
	private static volatile SharedMap<String,Object> configMap = null;
	
	private static final Object LOCK = new Object();
	
	private INetConfigLoader() {
		
	}
	
	
	private static SharedMap<String, Object> getConfigMap() {
		if (configMap == null) {
			synchronized (LOCK) {
				if (configMap == null) {
					start();
				}
			}
		}
		
		if (configMap == null) {
			throw new IllegalStateException("Configuration failed to load");
		}
		
		return configMap;
	}
	
	
	
	public static String getHost() {
		return getConfigMap().getString("host", "0.0.0.0");
	}
	
	
	public static int getPort() {
		return getConfigMap().getInt("port", 10000);
	}
	
	public static String getBasePackage() {
		return getConfigMap().getString("basePackage", "kr.tx24");
	}
	
	
	
	public static boolean enableLoggingHandler() {
		if(getConfigMap().containsKey("logging")) {
			return getConfigMap().getBoolean("logging");
		}else {
			return false;
		}
	}
	
	
	
	public static LinkedHashMap<String,Object> getMap(String key){
		return getConfigMap().getLinkedHashMap(key);
	}
	

	public static <T> T get(String key, Class<T> type) {
		if (key == null || type == null) {
			return null;
			
		}
		
		SharedMap<String, Object> config = getConfigMap();
		if(!config.containsKey(key)) {
			return null;
		}
		T instance = null;
		try {
			LinkedHashMap<String,Object> map = config.getLinkedHashMap(key);
			if(map == null) {
				return null;
			}
			return new JacksonUtils().mapToObject(map, type);
		}catch(Exception e) {
			return null;
		}
	}
	
	public static void start() {
		
		synchronized (LOCK) {
		
			if (configMap != null) {
				return;
			}
			
			if(!Files.exists(CONFIG_PATH)) {
				System.out.println("{} is not found : "+ CONFIG_PATH.toAbsolutePath());
				logger.info("{} is not found : {}",CONFIG_PATH.toAbsolutePath());
				System.exit(1);			
			}else {
				
				try {
					
					if(configMap == null) {
						configMap = new JacksonUtils().fromJson(CONFIG_PATH, TypeRegistry.MAP_SHAREDMAP_OBJECT);
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
					            	logger.info("set system property : {}={}", key, v);
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
	
}
