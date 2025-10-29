package kr.tx24.api.conf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

public class ApiConfigLoader {

	private static final Logger logger = LoggerFactory.getLogger(ApiConfigLoader.class);
	private static Path CONFIG_PATH	   = SystemUtils.getApiConfigPath();
	private static volatile ApiConfig configBean = null;
	
	private static final Object LOCK = new Object();
	
	public ApiConfigLoader() {
	}
	
	public static ApiConfig get() {
        if(configBean == null) {
            synchronized (LOCK) {  // synchronized 블록 추가
                if(configBean == null) {  // double-check
                    start();
                }
            }
        }
        return configBean;
    }
	
		
	public static void start() {
		
		if(configBean != null) {
			return;
		}
		
		if(!Files.exists(CONFIG_PATH)) {
			System.out.println("{} is not found : "+ CONFIG_PATH.toAbsolutePath());
			logger.info("{} is not found : {}",CONFIG_PATH.toAbsolutePath());
			System.exit(1);			
		}else {
			
			try {
				
				if(configBean == null) {
					configBean = new JacksonUtils().fromJson(CommonUtils.toString(Files.readAllBytes(CONFIG_PATH),StandardCharsets.UTF_8),ApiConfig.class);
				}
			
				if(CommonUtils.isEmpty(configBean)) {
                    throw new IllegalStateException("Loaded config is empty.");
                }
				
				if (configBean.property != null && !configBean.property.isEmpty()) {
				    configBean.property.forEach((key, value) -> {  
				        if (key != null && value != null) {
				            String v = CommonUtils.toString(value);
				            System.setProperty(key, v);
				            if (SystemUtils.deepview()) {
				                logger.info("set system property : {}={}", key, v);
				            }
				        }
				    });
				}
				
				logger.info("config loaded : {}", CONFIG_PATH.toAbsolutePath());
				
			}catch(Exception e) {
				logger.warn("Failed to load config {} : {}",CONFIG_PATH.toAbsolutePath(),e.getMessage());
				System.out.println("config is not loaded -> system.exit");
				System.exit(1);
			}
		}
		
		
	}
}
