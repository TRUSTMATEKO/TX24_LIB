package kr.tx24.inet.route;
 
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;

import kr.tx24.inet.mapper.Controller;
import kr.tx24.inet.mapper.Route;
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
        
        logger.info("Router scan packages: {}", packageNames);
        
        try {
            if (CommonUtils.isBlank(packageNames)) {
                throw new IllegalArgumentException("Package name cannot be empty");
            }
            
            String[] packages = packageNames.split(",");
            for (String pkg : packages) {
                String trimmed = pkg.trim();
                if (!trimmed.isEmpty()) {
                    //logger.info("Scanning package: {}", trimmed);
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
        

        int controllerCount = 0;
        for (ClassPath.ClassInfo clp : classes) {
            try {
                Class<?> clazz = Class.forName(clp.getName());
                if (clazz.isAnnotationPresent(Controller.class)) {
                    controllerCount++;
                   
                    registerController(clazz);
                }
            } catch (ClassNotFoundException e) {
                logger.warn("Failed to load class: {}", clp.getName(), e);
            } catch (Exception e) {
                logger.error("Error processing class: {}", clp.getName(), e);
            }
        }
        
        if(SystemUtils.deepview()) {
        	logger.debug("Processed {} controllers in package: {}", controllerCount, packageName);
        }
    }
    
    private static void registerController(Class<?> clazz) {
        Controller controllerAnnotation = clazz.getAnnotation(Controller.class);
        String rootTarget = normalizeTarget(controllerAnnotation.target());
        
        //logger.debug("Registering controller: {} with root target: '{}'", clazz.getSimpleName(), rootTarget);
        
        int routeCount = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Route.class)) {
                routeCount++;
                try {
                    registerRoute(clazz, method, rootTarget);
                } catch (Exception e) {
                    logger.error("Failed to register route for method: {}.{}", 
                            clazz.getName(), method.getName(), e);
                }
            }
        }
        
        if (routeCount == 0) {
            logger.warn("No @Route methods found in controller: {}", clazz.getName());
        } else {
            //logger.debug("Found {} route methods in controller: {}", routeCount, clazz.getSimpleName());
        }
    }
    
    private static void registerRoute(Class<?> clazz, Method method, String rootTarget) {
        Route route = method.getAnnotation(Route.class);
        
        // target 배열 검증
        String[] targets = route.target();
        if (targets == null || targets.length == 0) {
            logger.warn("Empty target array for method: {}.{}", 
                    clazz.getName(), method.getName());
            return;
        }
        
        // RouteInvoker 생성 (메타데이터 캐싱)
        RouteInvoker invoker = new RouteInvoker(method, clazz, 
                route.loggable(), route.authRequired());
        
        for (String target : targets) {
            // 빈 target 체크
            if (target == null || target.trim().isEmpty()) {
                logger.warn("Empty target in route for method: {}.{}", 
                        clazz.getName(), method.getName());
                continue;
            }
            
            String fullTarget = rootTarget + normalizeTarget(target);
            
            // fullTarget이 빈 문자열인 경우 체크
            if (fullTarget.isEmpty()) {
                logger.warn("Resolved target is empty for method: {}.{}, rootTarget='{}', target='{}'", 
                        clazz.getName(), method.getName(), rootTarget, target);
                continue;
            }
            
            String normalizedTarget = fullTarget.toLowerCase();
            
            // 중복 체크 및 등록
            RouteInvoker existing = ROUTE_MAP.putIfAbsent(normalizedTarget, invoker);
            if (existing != null) {
                logger.warn("Duplicate route detected: '{}' - Overwriting {} with {}", 
                        normalizedTarget, existing, invoker);
                ROUTE_MAP.put(normalizedTarget, invoker);
            }
            /*
            logger.info("Registered route: '{}' -> {}.{} (loggable={}, authRequired={})", 
                    normalizedTarget, 
                    clazz.getSimpleName(), 
                    method.getName(),
                    invoker.isLoggable(),
                    invoker.isAuthRequired());
            */
        }
    }
    
    private static String normalizeTarget(String target) {
        if (CommonUtils.isEmpty(target)) {
            return "";
        }
        
        String normalized = target.toLowerCase().trim();
        
        // 시작 슬래시 확인
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        
        // 끝 슬래시 제거
        if (normalized.length() > 1 && normalized.endsWith("/")) {
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
        
        if (CommonUtils.isBlank(target)) {
            return null;
        }
        
        String normalizedTarget = normalizeTarget(target);
        
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
        if (ROUTE_MAP.isEmpty()) {
            logger.info("=Route Map is EMPTY =");
            return;
        }
        
        StringBuilder sb = new StringBuilder("\n= Route Map =\n");
        
        ROUTE_MAP.forEach((target, invoker) -> {
            sb.append(String.format("%-30s => %-40s (loggable: %s, authRequired: %s)\n",
                    Abbreviator.format(target, '/', 29),
                    invoker.toString(),
                    invoker.isLoggable(),
                    invoker.isAuthRequired()));
        });
        
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