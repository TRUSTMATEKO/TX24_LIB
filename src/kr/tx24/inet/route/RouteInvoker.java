package kr.tx24.inet.route;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.inet.mapper.Autowired;
import kr.tx24.inet.mapper.Data;
import kr.tx24.inet.mapper.Head;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.map.LinkedMap;

public class RouteInvoker {
    private static final Logger logger = LoggerFactory.getLogger(RouteInvoker.class);
    private static final Set<Class<?>> AUTOWIRED_FIELD_INSPECTED_TYPES =
        ConcurrentHashMap.newKeySet();

	private final Method method;
    private final Class<?> controllerClass;
    private final Supplier<?>[] parameterSuppliers;
    private final boolean loggable;
    private final boolean authRequired;
    
    // 생성자에서 메타데이터 캐싱 (한 번만 수행)
    public RouteInvoker(Method method, Class<?> controllerClass, boolean loggable, boolean authRequired) {
        this.method = method;
        this.controllerClass = controllerClass;
        this.loggable = loggable;
        this.authRequired = authRequired;
        this.method.setAccessible(true);
       
        // 파라미터 공급자 미리 생성 (성능 최적화)
        Parameter[] parameters = method.getParameters();
        this.parameterSuppliers = new Supplier<?>[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            parameterSuppliers[i] = createParameterSupplier(parameters[i]);
        }
    }
    
    /**
     * 라우트 메서드 실행
     */
    public Object invoke(ChannelHandlerContext ctx, INet inet) throws Exception {
        // 1. 요청별 Bean 컨텍스트와 컨트롤러 인스턴스 생성
        RequestBeanContext requestContext = new RequestBeanContext(ctx, inet);
        Object controller = createController(requestContext);
        
        // 2. 파라미터 준비 (미리 캐싱된 Supplier 사용)
        Object[] args = prepareArguments(ctx, inet);
        
        // 3. 메서드 호출
        return method.invoke(controller, args);
    }
    
    
    private Object createController(RequestBeanContext requestContext) throws Exception {
        return instantiateType(controllerClass, requestContext);
    }

    /**
     * 타입의 @Autowired 생성자를 찾아 생성자 의존성까지 재귀적으로 주입한다.
     * @Autowired 생성자가 없을 때만 기본 생성자를 사용한다.
     */
    private Object instantiateType(Class<?> type, RequestBeanContext requestContext)
            throws Exception {
        if (INet.class.isAssignableFrom(type)) {
            return requestContext.inet;
        }
        if (ChannelHandlerContext.class.isAssignableFrom(type)) {
            return requestContext.ctx;
        }

        warnUnsupportedAutowiredFields(type);

        Object cachedBean = requestContext.beans.get(type);
        if (cachedBean != null) {
            return cachedBean;
        }

        if (requestContext.dependencyPath.contains(type)) {
            throw new IllegalStateException(
                "Circular constructor dependency detected: "
                    + formatDependencyPath(requestContext.dependencyPath, type)
            );
        }

        requestContext.dependencyPath.addLast(type);
        try {
            Constructor<?> autowiredConstructor = null;
            int autowiredCount = 0;

            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (constructor.isAnnotationPresent(Autowired.class)) {
                    autowiredConstructor = constructor;
                    autowiredCount++;
                }
            }

            if (autowiredCount > 1) {
                throw new IllegalStateException(
                    "Multiple @Autowired constructors found in " + type.getName()
                        + ". Only one @Autowired constructor is allowed."
                );
            }

            if (autowiredConstructor != null) {
                Object bean = instantiateWithAutowired(autowiredConstructor, requestContext);
                requestContext.beans.put(type, bean);
                return bean;
            }

            try {
                Constructor<?> defaultConstructor = type.getDeclaredConstructor();
                defaultConstructor.setAccessible(true);
                Object bean = defaultConstructor.newInstance();
                requestContext.beans.put(type, bean);
                return bean;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                    "No injectable constructor found in " + type.getName()
                        + ". Add one @Autowired constructor or a default constructor."
                        + " Dependency path: "
                        + formatDependencyPath(requestContext.dependencyPath, null),
                    e
                );
            }
        } finally {
            requestContext.dependencyPath.removeLast();
        }
    }

    /**
     * 지원하지 않는 필드 주입을 한 번만 경고하고 실제 주입은 수행하지 않는다.
     */
    private void warnUnsupportedAutowiredFields(Class<?> type) {
        if (!AUTOWIRED_FIELD_INSPECTED_TYPES.add(type)) {
            return;
        }

        Class<?> currentType = type;
        while (currentType != null && currentType != Object.class) {
            for (Field field : currentType.getDeclaredFields()) {
                if (field.isAnnotationPresent(Autowired.class)) {
                    logger.warn(
                        "@Autowired field injection is not supported and will be ignored: {}.{}. "
                            + "Use constructor injection instead.",
                        currentType.getName(), field.getName()
                    );
                }
            }
            currentType = currentType.getSuperclass();
        }
    }

    private Object instantiateWithAutowired(Constructor<?> constructor,
                                            RequestBeanContext requestContext) throws Exception {
        constructor.setAccessible(true);
        
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = resolveParameter(paramTypes[i], requestContext);
            
            // null 체크
            if (params[i] == null) {
                throw new IllegalArgumentException(
                    "Cannot resolve parameter type " + paramTypes[i].getName() + 
                    " in @Autowired constructor of " + constructor.getDeclaringClass().getName()
                );
            }
        }
        
        return constructor.newInstance(params);
    }

    private String formatDependencyPath(Deque<Class<?>> dependencyPath, Class<?> repeatedType) {
        StringBuilder path = new StringBuilder();
        for (Class<?> dependencyType : dependencyPath) {
            if (path.length() > 0) {
                path.append(" -> ");
            }
            path.append(dependencyType.getName());
        }
        if (repeatedType != null) {
            path.append(" -> ").append(repeatedType.getName());
        }
        return path.toString();
    }
    
    
    
    private Object[] prepareArguments(ChannelHandlerContext ctx, INet inet) {
        Object[] args = new Object[parameterSuppliers.length];
        
        ThreadLocalContext.set(ctx, inet);
        try {
            for (int i = 0; i < parameterSuppliers.length; i++) {
                args[i] = parameterSuppliers[i].get();
            }
            return args;
        } finally {
            ThreadLocalContext.clear(); // 항상 실행되도록
        }
        
    }
    
    private Supplier<?> createParameterSupplier(Parameter param) {
        Class<?> type = param.getType();
        
        // INet 타입
        if (INet.class.isAssignableFrom(type)) {
            return () -> ThreadLocalContext.getINet();
        }
        
        // ChannelHandlerContext 타입
        if (ChannelHandlerContext.class.isAssignableFrom(type)) {
            return () -> ThreadLocalContext.getContext();
        }
        
        // @Head 어노테이션
        if (param.isAnnotationPresent(Head.class)) {
            return () -> {
                LinkedMap<String, Object> map = new LinkedMap<>();
                map.putAll(ThreadLocalContext.getINet().head());
                return map;
            };
        }
        
        // @Data 어노테이션
        if (param.isAnnotationPresent(Data.class)) {
            return () -> {
                LinkedMap<String, Object> map = new LinkedMap<>();
                map.putAll(ThreadLocalContext.getINet().data());
                return map;
            };
        }
        
        // 기본 생성자로 생성
        return () -> {
            try {
                return type.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create parameter: " + type, e);
            }
        };
    }
    
    private Object resolveParameter(Class<?> type, RequestBeanContext requestContext)
            throws Exception {
        return instantiateType(type, requestContext);
    }

    /**
     * invoke() 한 번에만 유지되는 요청 범위 Bean 저장소다.
     * 요청마다 별도 인스턴스를 사용하므로 동시 요청 사이에는 공유되지 않는다.
     */
    private static final class RequestBeanContext {
        private final ChannelHandlerContext ctx;
        private final INet inet;
        private final Map<Class<?>, Object> beans = new HashMap<>();
        private final Deque<Class<?>> dependencyPath = new ArrayDeque<>();

        private RequestBeanContext(ChannelHandlerContext ctx, INet inet) {
            this.ctx = ctx;
            this.inet = inet;
        }
    }
    
    public Method getMethod() {
        return method;
    }
    
    public Class<?> getControllerClass() {
        return controllerClass;
    }
    
    public boolean isLoggable() {
        return loggable;
    }
    
    public boolean isAuthRequired() {
    	return authRequired;
    }
    
    @Override
    public String toString() {
        return controllerClass.getSimpleName() + "." + method.getName() + 
               "(" + method.getParameterCount() + ")";
    }
}
