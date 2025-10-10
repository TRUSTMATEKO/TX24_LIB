package kr.tx24.inet.mapper;
 
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;

import kr.tx24.lib.lang.Abbreviator;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

public class Router {
	
	private static final Logger logger = LoggerFactory.getLogger(Router.class);
	
	// ConcurrentHashMap - 스레드 안전
    private static final ConcurrentHashMap<String, RouteInvoker> ROUTE_MAP = 
            new ConcurrentHashMap<>();
    
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final AtomicBoolean initializing = new AtomicBoolean(false);
	
    public static void start(String packageNames) {
        if (initialized.get()) {
            logger.debug("Router already initialized");
            return;
        }
        
        if (!initializing.compareAndSet(false, true)) {
            logger.debug("Another thread is initializing router");
            waitForInitialization();
            return;
        }
        
        try {
            if (CommonUtils.isEmpty(packageNames)) {
                throw new IllegalArgumentException("Package name cannot be empty");
            }
            
            String[] packages = packageNames.split(",");
            for (String pkg : packages) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    logger.info("Scanning package: {}", trimmed);
                    scanPackage(trimmed);
                }
            }
            
            if (ROUTE_MAP.isEmpty()) {
                logger.warn("No routes found in package: {}", packageNames);
            } else {
                logger.info("Registered {} routes", ROUTE_MAP.size());
            }
            
            if (SystemUtils.deepview()) {
                printRouteMap();
            }
            
            initialized.set(true);
            
        } catch (Exception e) {
            logger.error("Failed to initialize router", e);
            throw new RuntimeException("Router initialization failed", e);
        } finally {
            initializing.set(false);
        }
    }
    
    private static void waitForInitialization() {
        int attempts = 0;
        while (!initialized.get() && attempts < 100) {
            try {
                Thread.sleep(50);
                attempts++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for router init", e);
            }
        }
        
        if (!initialized.get()) {
            throw new RuntimeException("Router initialization timeout");
        }
    }
    
    private static void scanPackage(String packageName) throws Exception {
        ClassPath classPath = ClassPath.from(getDefaultClassLoader());
        ImmutableSet<ClassPath.ClassInfo> classes = 
                classPath.getTopLevelClassesRecursive(packageName);
        
        for (ClassPath.ClassInfo clp : classes) {
            try {
                Class<?> clazz = Class.forName(clp.getName());
                if (clazz.isAnnotationPresent(Controller.class)) {
                    registerController(clazz);
                }
            } catch (ClassNotFoundException e) {
                logger.warn("Failed to load class: {}", clp.getName(), e);
            }
        }
    }
    
    private static void registerController(Class<?> clazz) {
        String rootTarget = normalizeTarget(
                clazz.getAnnotation(Controller.class).target());
        
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Route.class)) {
                registerRoute(clazz, method, rootTarget);
            }
        }
    }
    
    private static void registerRoute(Class<?> clazz, Method method, String rootTarget) {
        Route route = method.getAnnotation(Route.class);
        
        // RouteInvoker 생성 (메타데이터 캐싱)
        RouteInvoker invoker = new RouteInvoker(method, clazz, route.loggable());
        
        for (String target : route.target()) {
            String fullTarget = rootTarget + normalizeTarget(target);
            String normalizedTarget = fullTarget.toLowerCase();
            
            RouteInvoker existing = ROUTE_MAP.putIfAbsent(normalizedTarget, invoker);
            if (existing != null) {
                logger.warn("Duplicate route: {} (overwritten)", normalizedTarget);
                ROUTE_MAP.put(normalizedTarget, invoker);
            } else {
                logger.debug("Registered route: {} -> {}", normalizedTarget, invoker);
            }
        }
    }
    
    private static String normalizeTarget(String target) {
        if (target == null || target.isEmpty()) {
            return "";
        }
        
        String normalized = target.toLowerCase().trim();
        
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        
        return normalized;
    }
    
    /**
     * 라우트 조회
     */
    public static RouteInvoker getRoute(String target) {
        if (!initialized.get()) {
            throw new IllegalStateException("Router not initialized");
        }
        
        if (target == null || target.isEmpty()) {
            return null;
        }
        
        String normalizedTarget = target.toLowerCase().trim();
        
        // 정확히 일치하는 라우트 찾기
        RouteInvoker invoker = ROUTE_MAP.get(normalizedTarget);
        if (invoker != null) {
            return invoker;
        }
        
        // '/' 기준으로 점진적으로 축소하면서 찾기
        int lastSlash = normalizedTarget.lastIndexOf('/');
        while (lastSlash > 0) {
            normalizedTarget = normalizedTarget.substring(0, lastSlash);
            invoker = ROUTE_MAP.get(normalizedTarget);
            if (invoker != null) {
                return invoker;
            }
            lastSlash = normalizedTarget.lastIndexOf('/');
        }
        
        return null;
    }
    
    private static void printRouteMap() {
        StringBuilder sb = new StringBuilder("\n========== Route Map ==========\n");
        
        ROUTE_MAP.forEach((target, invoker) -> {
            sb.append(String.format("%-30s => %s (loggable: %s)\n",
                    Abbreviator.format(target, '/', 29),
                    invoker.toString(),
                    invoker.isLoggable()));
        });
        
        sb.append("==============================");
        logger.info(sb.toString());
    }
    
    private static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) {
            logger.debug("Failed to get context class loader", ex);
        }
        
        if (cl == null) {
            try {
                cl = ClassLoader.getSystemClassLoader();
            } catch (Throwable ex) {
                logger.debug("Failed to get system class loader", ex);
            }
        }
        
        return cl;
    }
    
    public static int getRouteCount() {
        return ROUTE_MAP.size();
    }
    
    public static boolean isInitialized() {
        return initialized.get();
    }
    
    private Router() {}
	

	

	
	
	


	
	
	

}

