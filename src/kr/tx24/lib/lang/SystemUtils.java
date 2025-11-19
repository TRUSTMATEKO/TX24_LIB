package kr.tx24.lib.lang;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import kr.tx24.lib.conf.Configure;
import kr.tx24.lib.lb.LoadBalancer;
import kr.tx24.lib.lifecycle.JvmStatusManager;

/**
 * 시스템 초기화 유틸리티
 * JVM 실행 시 Main 호출 전에 반드시 초기화 필요
 */
public class SystemUtils {

	
    private static final String PROPERTY_NONE 		= "N/A";
    private static final String PROPERTY_CONFIG 	= "CONF";
    private static final String PROPERTY_PROCESS 	= "PROC";
    private static final String PROPERTY_REDIS		= "REDIS";
    private static final String PROPERTY_REDIS1		= "REDIS1";
    private static final String PROPERTY_REDIS2 	= "REDIS2";
    private static final String PROPERTY_LOADBALANCE= "NLB";
    private static final String PROPERTY_DBCONFIG 	= "DBSET";
    private static final String PROPERTY_LOG_DIR 	= "LOG_DIR";
    private static final String PROPERTY_LOG_MAX 	= "LOG_MAXDAY";
    private static final String PROPERTY_LOG_LEVEL 	= "LOG_LEVEL";
    private static final String PROPERTY_LOG_REDIS 	= "LOG_REDIS";
    private static final String PROPERTY_LOG_REDIS1	= "LOG_REDIS1";
    private static final String PROPERTY_LOG_REDIS2	= "LOG_REDIS2";

    private static final String CONFIG_LOADBALANCE 	= "nlb.json";
    private static final String CONFIG_DATABASE 	= "db.json";
    private static final String CONFIG_INET			= "inet.json";
    private static final String CONFIG_API			= "api.json";
    private static final String CONFIG_SERVER		= "server.json";
    private static final String CONFIG_DEEPVIEW 	= "deep.view";

    public static final String LOG_ENV_DEFAULT 		= "true,false,false";

    public static final String REDIS_STORAGE_LOG 	= "SYS_MSG_LOG";
    public static final String REDIS_STORAGE_TRX 	= "SYS_MSG_TRX";
    public static final String REDIS_STORAGE_JVM 	= "SYS_MSG_JVM";
    public static final String REDIS_STORAGE_MESSAGE= "SYS_MSG_MESSAGE";
    public static final String REDIS_STORAGE_MESSAGE_QUEUE= "SYS_MSG_MESSAGE_QUEUE";
    public static final String REDIS_STORAGE_MESSAGE_RESULT = "SYS_MSG_MESSAGE_RESULT";

   

    public static final String REDIS_INITIAL 		= "redis://%s%s";
    public static String REDIS_LOG_KEY 				= "";
    public static String REDIS_CACHE_KEY 			= "";

    public static String KMS_KEY 					= "asdfghjklqwertyuiopzmiklink1015!";
    public static String KMS_IV 					= "iuytrewqkjhgfdsa";

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static volatile boolean startLazyLoader = false;

    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private static final AtomicBoolean enabledDeepView  = new AtomicBoolean(false);
    
    
    // JVM 로딩 시 즉시 초기화
    static {
        init();
        startAsync();
    }

    public synchronized static void init() {
    	if (isInitialized.get()) {
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
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_LOG_LEVEL, "INFO");
        CommonUtils.setSystemPropertyIfAbsent(PROPERTY_LOG_REDIS, "");
        
        isInitialized.set(true);
        

        // 초기 정보 출력
        System.err.println("__________________________");
        String art = """
                ╺┳╸╻ ╻┏━┓╻ ╻
                 ┃ ┏╋┛┏━┛┗━┫
                 ╹ ╹ ╹┗━╸  ╹      v.251201
                """;

        System.err.print(art);
        System.err.print("\n\n");
        System.err.println("JDK     " + getJavaVersion());
        System.err.println("CONFIG  " + getConfigDirectory());
        System.err.println("PROC    " + getLocalProcessName() +",H:"+getLocalHostname()+",I:"+getLocalAddress()+",P:"+getLocalProcessId());
        
        
        
        
        //System.err.println("SystemUtils initialized"); 

    }
    
    
    private static void startAsync() {
    	
    	scheduler.scheduleAtFixedRate(SystemUtils::checkDeepView, 0, 10*1000, TimeUnit.MILLISECONDS);
    	
        scheduler.execute(() -> {
            try {
                Thread.sleep(100);
                synchronized (SystemUtils.class) {
                    if (!startLazyLoader) {
                        JvmStatusManager.initialize();
                        if(Files.exists(getLoadBalanceConfigPath())) {
                        	LoadBalancer.start(getLoadBalanceConfigPath());
                        }
                        startLazyLoader = true;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("Failed to start lazy loader: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    private static void checkDeepView() {
    	
    	try {
            Path deepViewPath = Paths.get(getConfigDirectory(), CONFIG_DEEPVIEW);
            boolean fileExists = Files.exists(deepViewPath);
            
            // 현재 설정된 값
            boolean currentValue = enabledDeepView.get();
            
            // 파일 존재 여부에 따라 설정 변경
            if(fileExists) {
                if(!currentValue) {
                    // false → true 변경
                	enabledDeepView.set(true);
                    setLogLevel(Level.DEBUG);
                    System.err.println("deepview enabled ");
                }
            } else {
                if(currentValue) {
                    // true → false 변경
                	enabledDeepView.set(false);
                    setLogLevel(Level.INFO);
                    System.err.println("deepview disabled ");
                }
            }
            
        } catch (Exception e) {
        	enabledDeepView.set(false);
            System.err.println("error checking deep.view: " + e.getMessage());
        }
    }

    public static void setLogLevel(Level level) {
        try {
            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
            ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).setLevel(level);
        } catch (Exception e) {}
    }

    public static boolean deepview() { return enabledDeepView.get(); }

    
    public static String getConfigDirectory() {
        return System.getProperty(PROPERTY_CONFIG, getRootDirectory() + File.separator + "conf");
    }
    
    
    public static Path getConfigPath(String configFile) {
    	return Paths.get(getConfigDirectory(),configFile);
    }
    

    public static String getRootDirectory() {
        String dir = System.getProperty("user.dir");
        return dir != null ? dir : ".";
    }

    public static String getRedisSystemUri() {
        
        List<String> rediss = new ArrayList<String>();
        
        if(CommonUtils.hasValue(PROPERTY_REDIS)) {
        	if (CommonUtils.hasValue(REDIS_CACHE_KEY)) {
                rediss.add(String.format(REDIS_INITIAL, REDIS_CACHE_KEY + "@", System.getProperty(PROPERTY_REDIS)));
            } else {
            	rediss.add(String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_REDIS)));
            }
        }else {
        
	        if(CommonUtils.hasValue(PROPERTY_REDIS1)) {
	        	if (CommonUtils.hasValue(REDIS_CACHE_KEY)) {
	                rediss.add(String.format(REDIS_INITIAL, REDIS_CACHE_KEY + "@", System.getProperty(PROPERTY_REDIS1)));
	            } else {
	            	rediss.add(String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_REDIS1)));
	            }
	        }
	        
	        if(CommonUtils.hasValue(PROPERTY_REDIS2)) {
	        	if (CommonUtils.hasValue(REDIS_CACHE_KEY)) {
	        		rediss.add(String.format(REDIS_INITIAL, REDIS_CACHE_KEY + "@", System.getProperty(PROPERTY_REDIS2)));
	            } else {
	            	rediss.add(String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_REDIS2)));
	            }
	        }
        }
        
        if(rediss.isEmpty()) {
        	return "System properties '-DREDIS , -DREDIS1, -DREDIS2' not found "; 
        }
        
        return String.join(",", rediss);
        
    }

    public static String getRedisLogUri() {
    	
    	List<String> rediss = new ArrayList<String>();
    	
    	
    	if(CommonUtils.hasValue(PROPERTY_LOG_REDIS)) {
        	if (CommonUtils.hasValue(REDIS_CACHE_KEY)) {
                rediss.add(String.format(REDIS_INITIAL, REDIS_CACHE_KEY + "@", System.getProperty(PROPERTY_LOG_REDIS)));
            } else {
            	rediss.add(String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_LOG_REDIS)));
            }
        }else {
        
	        if(CommonUtils.hasValue(PROPERTY_LOG_REDIS1)) {
	        	if (CommonUtils.hasValue(REDIS_CACHE_KEY)) {
	                rediss.add(String.format(REDIS_INITIAL, REDIS_CACHE_KEY + "@", System.getProperty(PROPERTY_LOG_REDIS1)));
	            } else {
	            	rediss.add(String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_LOG_REDIS1)));
	            }
	        }
	        
	        if(CommonUtils.hasValue(PROPERTY_LOG_REDIS2)) {
	        	if (CommonUtils.hasValue(REDIS_CACHE_KEY)) {
	        		rediss.add(String.format(REDIS_INITIAL, REDIS_CACHE_KEY + "@", System.getProperty(PROPERTY_LOG_REDIS2)));
	            } else {
	            	rediss.add(String.format(REDIS_INITIAL, "", System.getProperty(PROPERTY_LOG_REDIS2)));
	            }
	        }
        }
        
        if(rediss.isEmpty()) {
        	System.out.println("log redis using defaut redis");
        	return getRedisSystemUri(); 
        }
        
        return String.join(",", rediss);
    	
    	
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

    
    public static Path getLoadBalanceConfigPath() {
    	return getSystemPathProperty(PROPERTY_LOADBALANCE, Paths.get(getConfigDirectory(), CONFIG_LOADBALANCE));
    }

    public static Path getDatabaseConfigPath() {
    	return getSystemPathProperty(PROPERTY_DBCONFIG, Paths.get(getConfigDirectory(), CONFIG_DATABASE));
    }
    
    public static Path getWasConfigPath() {
    	return Paths.get(getConfigDirectory(), CONFIG_SERVER);
    }
    
    public static Path getApiConfigPath() {
    	return Paths.get(getConfigDirectory(), CONFIG_API);
    }
    
    public static Path getINetConfigPath() {
    	return Paths.get(getConfigDirectory(), CONFIG_INET);
    }
    
    

    public static Path getLogDirectory() {
    	return getSystemPathProperty(PROPERTY_LOG_DIR, Paths.get(getRootDirectory(), "logs"));
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
    
    
    public static String getSystemProperty(String key, String def) {
    	return System.getProperty(key, def);
    }
    
    
    public static Path getSystemPathProperty(String key, Path path) {
    	if(CommonUtils.isEmpty(System.getProperty(key))){
    		return path;
    	}else {
    		return Paths.get(System.getProperty(key));
    	}
    	
    }
    
    
    
    

}
