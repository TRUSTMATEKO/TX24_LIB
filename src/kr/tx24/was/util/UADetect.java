package kr.tx24.was.util;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blueconic.browscap.BrowsCapField;
import com.blueconic.browscap.Capabilities;
import com.blueconic.browscap.UserAgentParser;
import com.blueconic.browscap.UserAgentService;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * @author juseop
 * UserAgent sample :
 * https://github.com/blueconic/browscap-java/blob/master/src/test/resources/useragents.txt
 * https://github.com/blueconic/browscap-java/blob/master/src/test/resources/useragents_2.txt 
 * 
IS_MASTER_PARENT,
IS_LITE_MODE,
PARENT,
COMMENT,
BROWSER(true),
BROWSER_TYPE(true),
BROWSER_BITS,
BROWSER_MAKER,
BROWSER_MODUS,
BROWSER_VERSION,
BROWSER_MAJOR_VERSION(true),
BROWSER_MINOR_VERSION,
PLATFORM(true),
PLATFORM_VERSION(true),
PLATFORM_DESCRIPTION,
PLATFORM_BITS,
PLATFORM_MAKER,
IS_ALPHA,
IS_BETA,
IS_WIN16,
IS_WIN32,
IS_WIN64,
IS_IFRAMES,
IS_FRAMES,
IS_TABLES,
IS_COOKIES,
IS_BACKGROUND_SOUNDS,
IS_JAVASCRIPT,
IS_VBSCRIPT,
IS_JAVA_APPLETS,
IS_ACTIVEX_CONTROLS,
IS_MOBILE_DEVICE,
IS_TABLET,
IS_SYNDICATION_READER,
IS_CRAWLER,
IS_FAKE,
IS_ANONYMIZED,
IS_MODIFIED,
CSS_VERSION,
AOL_VERSION,
DEVICE_NAME,
DEVICE_MAKER,
DEVICE_TYPE(true),
DEVICE_POINTING_METHOD,
DEVICE_CODE_NAME,
DEVICE_BRAND_NAME,
RENDERING_ENGINE_NAME,
RENDERING_ENGINE_VERSION,
RENDERING_ENGINE_DESCRIPTION,
RENDERING_ENGINE_MAKER;
 */
public final class UADetect {
	private static Logger logger = LoggerFactory.getLogger(UADetect.class);
	
	private static final List<BrowsCapField> REQUIRED_FIELDS = List.of(
		        BrowsCapField.BROWSER, BrowsCapField.BROWSER_TYPE,
		        BrowsCapField.BROWSER_MAJOR_VERSION,
		        BrowsCapField.DEVICE_TYPE, BrowsCapField.PLATFORM, BrowsCapField.PLATFORM_VERSION,
		        BrowsCapField.IS_CRAWLER, BrowsCapField.IS_FAKE,
		        BrowsCapField.IS_MOBILE_DEVICE
		    );
	
	// Thread-Safe Lazy Initialization을 위한 volatile 변수 사용
	private static volatile UserAgentParser parser = null; 
	
	static {
        // 서버 시작(main 스레드)을 막지 않도록 백그라운드 스레드에서 실행
        Thread initializationThread = new Thread(() -> {
            initialize();
        }, "UserAgentParser-Initializer");
        
        initializationThread.setDaemon(true); // 서버 종료 시 함께 종료되도록 데몬 스레드로 설정
        initializationThread.start();
    }
	
	private UADetect() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
	
	public static void initialize() {
        if (parser == null) {
            synchronized (UADetect.class) {
                if (parser == null) {
                    try {
                    	if(SystemUtils.deepview()) {
	                        logger.info("Initializing UserAgentParser with fields: {}", 
	                                REQUIRED_FIELDS.stream().map(BrowsCapField::name).collect(Collectors.joining(", "))
	                        );
                    	}
                        parser = new UserAgentService().loadParser(REQUIRED_FIELDS);
                        logger.info("UserAgentParser initialization successful.");
                       
                    } catch (Exception e) {
                        logger.error("Failed to initialize UserAgentParser: {}", e.getMessage(), e);
                        // 파서 초기화 실패 시 null 상태를 유지하고, parse 메서드에서 재시도할 수 있도록 처리
                    }
                }
            }
        }
    }
	
	

	 
	
	public static UserAgent set(String userAgent) {
		UserAgent ua = new UserAgent(); 
		
        
        if (parser == null) {
            initialize(); // 혹시 모를 늦은 초기화 재시도
            if (parser == null) {
                logger.warn("UserAgentParser is not initialized. Cannot parse User-Agent: {}", userAgent);
                return ua; // 파서가 여전히 null이면 빈 객체 반환
            }
        }

        try {
            Capabilities capabilities = parser.parse(userAgent);
            
            ua.browser              = capabilities.getBrowser();
            ua.browserType          = capabilities.getBrowserType();
            ua.browserMajorVersion  = capabilities.getBrowserMajorVersion();
            ua.deviceType           = capabilities.getDeviceType();
            ua.platform             = capabilities.getPlatform();
            ua.platformVersion      = capabilities.getPlatformVersion();
            
            // ⭐ getBoolean 메서드를 사용하여 Boolean 필드 설정
            ua.isCrawler            = CommonUtils.toBoolean(capabilities.getValue(BrowsCapField.IS_CRAWLER));
            ua.isFake               = CommonUtils.toBoolean(capabilities.getValue(BrowsCapField.IS_FAKE));
            ua.isMobile             = CommonUtils.toBoolean(capabilities.getValue(BrowsCapField.IS_MOBILE_DEVICE));
            
        } catch (Exception e) {
            logger.error("UADetect parse error for UA: {}. Details: {}", userAgent, e.getMessage(), e);
        }
        return ua;
    }
	
	


}
