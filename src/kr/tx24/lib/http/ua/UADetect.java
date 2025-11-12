package kr.tx24.lib.http.ua;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blueconic.browscap.BrowsCapField;
import com.blueconic.browscap.Capabilities;
import com.blueconic.browscap.UserAgentParser;
import com.blueconic.browscap.UserAgentService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

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
	private static volatile LoadingCache<String, UserAgent> CACHE = null;
	
	
	private static final List<BrowsCapField> REQUIRED_FIELDS = List.of(
		        BrowsCapField.BROWSER, BrowsCapField.BROWSER_TYPE,
		        BrowsCapField.BROWSER_MAJOR_VERSION,
		        BrowsCapField.DEVICE_TYPE, BrowsCapField.PLATFORM, BrowsCapField.PLATFORM_VERSION,
		        BrowsCapField.IS_CRAWLER, BrowsCapField.IS_FAKE,
		        BrowsCapField.IS_MOBILE_DEVICE
		    );
	
	// Thread-Safe Lazy Initialization을 위한 volatile 변수 사용
	private static volatile UserAgentParser parser = null; 
	
	
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
                        logger.info("Failed to initialize UserAgentParser: {}", e.getMessage(), e);
                        // 파서 초기화 실패 시 null 상태를 유지하고, parse 메서드에서 재시도할 수 있도록 처리
                    }
                }
            }
        }
        
        if (parser != null && CACHE == null) {
            synchronized (UADetect.class) {
               if (CACHE == null) {
                    CACHE = CacheBuilder.newBuilder()
                       .maximumSize(10_000) 					// 최대 10,000개 엔트리 (크기 기반 LRU)
                       .softValues() 							// 메모리 압박 시 GC 대상이 되도록 설정
                       .expireAfterWrite(6, TimeUnit.HOURS) 	// ⭐ 6시간 만료 정책 추가
                       .build(
                           new CacheLoader<String, UserAgent>() {
                               @Override
                               public UserAgent load(String userAgent) throws Exception {
                                   // 캐시 미스 시 parseUserAgentInternal 호출
                                   return parseUserAgentInternal(userAgent);
                               }
                           }
                       );
                    logger.info("UADetect Guava Cache initialized with maximum size 10,000 and 6-hour expiration.");
               }
            }
       }
        
        
        
    }
	
	private static UserAgent parseUserAgentInternal(String userAgent) {
        UserAgent ua = new UserAgent();
        
        if (parser == null) {
            logger.debug("UserAgentParser is not initialized for parsing. UA: {}", userAgent);
            return ua;
        }

        try {
            Capabilities capabilities = parser.parse(userAgent);
            
            ua.browser              = capabilities.getBrowser();
            ua.browserType          = capabilities.getBrowserType();
            ua.browserMajorVersion  = capabilities.getBrowserMajorVersion();
            ua.deviceType           = capabilities.getDeviceType();
            ua.platform             = capabilities.getPlatform();
            ua.platformVersion      = capabilities.getPlatformVersion();
            
            ua.isCrawler            = CommonUtils.toBoolean(capabilities.getValue(BrowsCapField.IS_CRAWLER));
            ua.isFake               = CommonUtils.toBoolean(capabilities.getValue(BrowsCapField.IS_FAKE));
            ua.isMobile             = CommonUtils.toBoolean(capabilities.getValue(BrowsCapField.IS_MOBILE_DEVICE));
            
        } catch (Exception e) {
            logger.info("UADetect parse error for UA: {}. Details: {}", userAgent, e.getMessage(), e);
        }
        return ua;
    }
	
	
	/**
	 * User-Agent 문자열을 캐시에서 조회하거나(Hit) 파싱 후(Miss) 결과를 반환합니다.
	 */
	public static UserAgent set(String userAgent) {
		// 1. 초기화 확인 
        if (CACHE == null) {
            initialize();
        }

        if (CACHE == null) {
            logger.debug("UADetect Cache is not initialized. Parsing without caching: {}", userAgent);
            // 캐시 초기화 실패 시 fallback: 직접 파싱
            return parseUserAgentInternal(userAgent);
        }

        // 2. Guava LoadingCache를 사용하여 조회 (미스 시 CacheLoader.load()가 자동 호출됨)
        try {
            return CACHE.getUnchecked(userAgent);
        } catch (Exception e) {
            logger.info("Guava Cache lookup/load failed for UA: {}", userAgent, e);
            return new UserAgent();
        }
    }
	
	
	public static void shutdown() {
        if (CACHE != null) {
        	try {
        		CACHE.invalidateAll(); // Guava Cache의 모든 엔트리 무효화
        	}catch(Exception e) {}
        }
	}
	



}
