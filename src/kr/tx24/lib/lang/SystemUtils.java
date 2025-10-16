package kr.tx24.lib.lang;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import kr.tx24.lib.conf.Configure;

/**
 * 시스템 초기화 유틸리티
 * JVM 실행 시 Main 호출 전에 반드시 초기화 필요
 */
public class SystemUtils {

    private static final String PROPERTY_NONE 		= "NONE";
    private static final String PROPERTY_CONFIG 	= "CONF";
    private static final String PROPERTY_PROCESS 	= "PROC";
    private static final String PROPERTY_REDIS 		= "REDIS";
    private static final String PROPERTY_LOADBALANCE= "NLB";
    private static final String PROPERTY_DBCONFIG 	= "DBSET";
    private static final String PROPERTY_LOG_DIR 	= "LOG_DIR";
    private static final String PROPERTY_LOG_MAX 	= "LOG_MAXDAY";
    private static final String PROPERTY_LOG_LEVEL 	= "LOG_LEVEL";
    private static final String PROPERTY_LOG_REDIS 	= "LOG_REDIS";
    private static final String PROPERTY_JVM_MONITOR= "JVM_MONITOR";

    private static final String CONFIG_LOADBALANCE 	= "nlb.json";
    private static final String CONFIG_DATABASE 	= "db.json";
    private static final String CONFIG_DEEPVIEW 	= "deep.view";

    public static final String LOG_ENV_DEFAULT 		= "true,false,false";

    public static final String REDIS_STORAGE_LOG 	= "SYS_MSG_LOG";
    public static final String REDIS_STORAGE_TRX 	= "SYS_MSG_TRX";
    public static final String REDIS_STORAGE_MESSAGE= "SYS_MSG_MESSAGE";
    public static final String REDIS_STORAGE_MESSAGE_RESULT = "SYS_MSG_MESSAGE_RESULT";

   

    public static final String REDIS_INITIAL 		= "redis://%s%s";
    public static String REDIS_LOG_KEY 				= "";
    public static String REDIS_CACHE_KEY 			= "";

    public static String KMS_KEY 					= "asdfghjklqwertyuiopzmiklink1015!";
    public static String KMS_IV 					= "iuytrewqkjhgfdsa";

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // JVM 로딩 시 즉시 초기화
    static {
        init();
    }

    public synchronized static void init() {
    	if (Boolean.getBoolean("SystemUtils.INITIALIZED")) {
            return;
        }
    	
    	
    	

        // Configure에서 설정 불러오기
        Configure configure 		= new Configure();
        Map<String, String> props 	= configure.load();

        REDIS_CACHE_KEY 			= props.getOrDefault("REDIS_KEY", "");
        REDIS_LOG_KEY 				= props.getOrDefault("REDIS_LOG_KEY", REDIS_CACHE_KEY);
        KMS_KEY 					= props.getOrDefault("KMS_KEY", KMS_KEY);
        KMS_IV 						= props.getOrDefault("KMS_IV", KMS_IV);
        
        Path path = Paths.get("../conf");
        if(!Files.isDirectory(path)) {
        	path = Paths.get("./conf");
        }

        // System property 기본값 설정
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_CONFIG	, path.toAbsolutePath().normalize().toString());
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_PROCESS	, PROPERTY_NONE);
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_LOG_DIR	, "../logs");
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_LOG_MAX	, "90");
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_LOG_LEVEL, "DEBUG");
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_LOG_REDIS, "");
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_JVM_MONITOR, "false");
        
        System.setProperty("SystemUtils.INITIALIZED", "true");
        
        
        
        if (Boolean.getBoolean(PROPERTY_JVM_MONITOR)) {
        	JvmStatusUtils.start();
        }

       

        // 초기 정보 출력
        System.err.println("__________________________");
        String art = """
                ╺┳╸╻ ╻┏━┓╻ ╻
                 ┃ ┏╋┛┏━┛┗━┫
                 ╹ ╹ ╹┗━╸  ╹      v.250926
                """;

        System.err.print(art);
        System.err.println("JDK     " + getJavaVersion());
        System.err.println("CONFIG  " + getConfigDirectory());
        System.err.println("PROC    " + getLocalProcessName() +",H:"+getLocalHostname()+",I:"+getLocalAddress()+",P:"+getLocalProcessId());
        // deep.view 감시
        scheduler.scheduleAtFixedRate(SystemUtils::checkDeepView, 100, 10*1000, TimeUnit.MILLISECONDS);
        
        //System.err.println("SystemUtils initialized"); 

    }
    


    private static void checkDeepView() {
        try {
            boolean exists = Files.exists(Paths.get(getConfigDirectory(), CONFIG_DEEPVIEW));
            if (exists != Boolean.getBoolean("SystemUtils.DEEP_VIEW")) {
            	boolean deepview = exists;
            	
                setLogLevel(deepview ? Level.DEBUG : getLogLevel());
                System.err.println("Deepview " + (deepview ? "enabled" : "disabled"));
                System.setProperty("SystemUtils.DEEP_VIEW", Boolean.toString(deepview));
                
            }else {
            	
            }
            
           System.err.println("deep.view : "+Boolean.getBoolean("SystemUtils.DEEP_VIEW"));
        } catch (Exception e) {}
    }

    public static void setLogLevel(Level level) {
        try {
            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
            ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(level);
        } catch (Exception e) {}
    }

    public static boolean deepview() { return Boolean.getBoolean("SystemUtils.DEEP_VIEW"); }

    
    public static String getConfigDirectory() {
        return System.getProperty(PROPERTY_CONFIG, getRootDirectory() + File.separator + "conf");
    }

    public static String getRootDirectory() {
        String dir = System.getProperty("user.dir");
        return dir != null ? dir : ".";
    }

    public static String getRedisSystemUri() {
        if (System.getProperty(PROPERTY_REDIS) == null) {
            return REDIS_INITIAL;
        } else if (CommonUtils.hasValue(REDIS_CACHE_KEY)) {
            return String.format(REDIS_INITIAL, REDIS_CACHE_KEY + "@", System.getProperty(PROPERTY_REDIS));
        } else {
            return String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_REDIS));
        }
    }

    public static String getRedisLogUri() {
        if (System.getProperty(PROPERTY_LOG_REDIS) == null) {
            return getRedisSystemUri();
        } else if (CommonUtils.hasValue(REDIS_LOG_KEY)) {
            return String.format(REDIS_INITIAL, REDIS_LOG_KEY + "@", System.getProperty(PROPERTY_LOG_REDIS));
        } else {
            return String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_REDIS));
        }
    }

    public static String getLocalProcessName() {
        return System.getProperty(PROPERTY_PROCESS, PROPERTY_NONE);
    }

    public static String getLocalAddress() {
        try {
            InetAddress candidate = null;
            for (Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces(); ifaces.hasMoreElements();) {
                NetworkInterface iface = ifaces.nextElement();
                for (Enumeration<InetAddress> addrs = iface.getInetAddresses(); addrs.hasMoreElements();) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress()) {
                        if (addr.isSiteLocalAddress()) return addr.getHostAddress();
                        if (candidate == null) candidate = addr;
                    }
                }
            }
            if (candidate != null) return candidate.getHostAddress();
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return PROPERTY_NONE;
        }
    }

    public static String getLocalHostname() {
        try { return InetAddress.getLocalHost().getHostName(); } 
        catch (Exception e) { return PROPERTY_NONE; }
    }

    public static String getLocalProcessId() {
        try {
            RuntimeMXBean mx = ManagementFactory.getRuntimeMXBean();
            if (mx != null) return mx.getName().split("@")[0];
        } catch (Exception e) {}
        return PROPERTY_NONE;
    }

    public static String getLoadBalanceConfig() {
        return System.getProperty(PROPERTY_LOADBALANCE, getConfigDirectory() + File.separator + CONFIG_LOADBALANCE);
    }

    public static String getDatabaseConfig() {
        return System.getProperty(PROPERTY_DBCONFIG, getConfigDirectory() + File.separator + CONFIG_DATABASE);
    }

    public static String getLogDirectory() {
        return System.getProperty(PROPERTY_LOG_DIR, getRootDirectory() + File.separator + "logs");
    }

    public static int getLogMaxDay() {
        try { return Integer.parseInt(System.getProperty(PROPERTY_LOG_MAX, "60")); } catch (Exception e) { return 60; }
    }

    public static Level getLogLevel() {
        switch (System.getProperty(PROPERTY_LOG_LEVEL, "DEBUG").toUpperCase().trim()) {
            case "DEBUG": return Level.DEBUG;
            case "ERROR": return Level.ERROR;
            case "INFO":  return Level.INFO;
            case "OFF":   return Level.OFF;
            case "TRACE": return Level.TRACE;
            case "WARN":  return Level.WARN;
            default:     return Level.DEBUG;
        }
    }

    public static String getElapsedTime(long time) {
        return String.format("elapsed Time in %.3fms%n", (time / 1e6d));
    }
    
    
    public static int getJavaMajorVersion() {
    	String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        } else {
            String[] parts = version.split("\\.");
            return Integer.parseInt(parts[0]);
        }
    }
    
    
    public static String getJavaVersion() {
    	return System.getProperty("java.version");
    }
    
    
    public static String getPackagePrefix(int depth) {
    	String packageName = SystemUtils.class.getPackageName();

        String[] parts = packageName.split("\\.");

        if (parts.length <= depth) {
            return packageName;
        }

        return String.join(".", Arrays.copyOf(parts, depth));
    }
    
    
    
    

}
