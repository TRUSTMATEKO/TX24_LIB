package kr.tx24.inet.route;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.inet.mapper.Autowired;
import kr.tx24.inet.mapper.Data;
import kr.tx24.inet.mapper.Head;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.map.LinkedMap;

public class RouteInvoker {
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
        // 1. 컨트롤러 인스턴스 생성
        Object controller = createController(ctx, inet);
        
        // 2. 파라미터 준비 (미리 캐싱된 Supplier 사용)
        Object[] args = prepareArguments(ctx, inet);
        
        // 3. 메서드 호출
        return method.invoke(controller, args);
    }
    
    
    private Object createController(ChannelHandlerContext ctx, INet inet) throws Exception {
        return instantiateType(controllerClass, ctx, inet, new ArrayDeque<>());
    }

    /**
     * 타입의 @Autowired 생성자를 찾아 생성자 의존성까지 재귀적으로 주입한다.
     * @Autowired 생성자가 없을 때만 기본 생성자를 사용한다.
     */
    private Object instantiateType(Class<?> type, ChannelHandlerContext ctx, INet inet,
                                   Deque<Class<?>> dependencyPath) throws Exception {
        if (dependencyPath.contains(type)) {
            throw new IllegalStateException(
                "Circular constructor dependency detected: "
                    + formatDependencyPath(dependencyPath, type)
            );
        }

        dependencyPath.addLast(type);
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
                return instantiateWithAutowired(
                    autowiredConstructor, ctx, inet, dependencyPath
                );
            }

            try {
                Constructor<?> defaultConstructor = type.getDeclaredConstructor();
                defaultConstructor.setAccessible(true);
                return defaultConstructor.newInstance();
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(
                    "No injectable constructor found in " + type.getName()
                        + ". Add one @Autowired constructor or a default constructor."
                        + " Dependency path: " + formatDependencyPath(dependencyPath, null),
                    e
                );
            }
        } finally {
            dependencyPath.removeLast();
        }
    }

    private Object instantiateWithAutowired(Constructor<?> constructor,
                                            ChannelHandlerContext ctx,
                                            INet inet,
                                            Deque<Class<?>> dependencyPath) throws Exception {
        constructor.setAccessible(true);
        
        Class<?>[] paramTypes = constructor.getParameterTypes();
        Object[] params = new Object[paramTypes.length];
        
        for (int i = 0; i < paramTypes.length; i++) {
            params[i] = resolveParameter(paramTypes[i], ctx, inet, dependencyPath);
            
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
    
    private Object resolveParameter(Class<?> type, ChannelHandlerContext ctx, INet inet,
                                    Deque<Class<?>> dependencyPath) throws Exception {
        
        if (INet.class.isAssignableFrom(type)) {
            return inet;
        } else if (ChannelHandlerContext.class.isAssignableFrom(type)) {
            return ctx;
        } else {
            return instantiateType(type, ctx, inet, dependencyPath);
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
