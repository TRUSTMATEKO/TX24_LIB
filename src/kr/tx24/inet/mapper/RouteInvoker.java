package kr.tx24.inet.mapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.function.Supplier;

import io.netty.channel.ChannelHandlerContext;
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
        // 생성자 파라미터 확인
    	Constructor<?>[] constructors = controllerClass.getDeclaredConstructors();
        
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            
            if (paramTypes.length == 0) {
                return constructor.newInstance();
            }
            
            // 생성자 파라미터 준비
            Object[] params = new Object[paramTypes.length];
            for (int i = 0; i < paramTypes.length; i++) {
                params[i] = resolveParameter(paramTypes[i], ctx, inet);
            }
            
            return constructor.newInstance(params);
        }
        
        throw new IllegalStateException("No suitable constructor found for " + controllerClass);
    }
    
    private Object[] prepareArguments(ChannelHandlerContext ctx, INet inet) {
        Object[] args = new Object[parameterSuppliers.length];
        
        for (int i = 0; i < parameterSuppliers.length; i++) {
            // Context 설정
        	ThreadLocalContext.set(ctx, inet);
            try {
                args[i] = parameterSuppliers[i].get();
            } finally {
                ThreadLocalContext.clear();
            }
        }
        
        return args;
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
    
    private Object resolveParameter(Class<?> type, ChannelHandlerContext ctx, INet inet) 
            throws Exception {
        
        if (INet.class.isAssignableFrom(type)) {
            return inet;
        } else if (ChannelHandlerContext.class.isAssignableFrom(type)) {
            return ctx;
        } else {
            return type.getDeclaredConstructor().newInstance();
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
