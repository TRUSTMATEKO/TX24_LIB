package kr.tx24.inter.server;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import kr.tx24.inter.mapper.Data;
import kr.tx24.inter.mapper.Head;
import kr.tx24.inter.mapper.RouteMap;
import kr.tx24.inter.mapper.Router;
import kr.tx24.inter.util.INetUtils;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * INet 프로토콜 핸들러
 * 
 * 중요: @Sharable 어노테이션이 없으므로 각 클라이언트 연결마다 새 인스턴스가 생성됩니다.
 * 따라서 startTime과 같은 인스턴스 변수는 스레드 안전합니다.
 */
public class INetHandler extends SimpleChannelInboundHandler<INet> {
    
    private static final Logger logger = LoggerFactory.getLogger(INetHandler.class);
    private static final String SYSTEM_STORAGE_TRX_EXT = "SYSTEM_STORAGE_TRX_EXT";
    
    // 각 연결마다 별도의 인스턴스이므로 스레드 안전
    private long startTime = 0L;
    
    // JSON 직렬화를 위한 ThreadLocal (성능 최적화)
    private static final ThreadLocal<JacksonUtils> jsonUtils = 
            ThreadLocal.withInitial(JacksonUtils::new);

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        startTime = System.nanoTime();
        logger.debug("Channel active: {}", ctx.channel().id().asShortText());
        super.channelActive(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, INet inet) throws Exception {
        // 트랜잭션 ID 생성 및 설정
        String extTrxId = INetUtils.getTrxId();
        inet.data(INetUtils.INET_TRX_ID, extTrxId);
        
        if (!inet.head().containsKey(INetUtils.INET_TRX_ID)) {
            inet.head().put(INetUtils.INET_TRX_ID, extTrxId);
        }

        // MDC에 트랜잭션 ID 설정 (로깅 컨텍스트)
        MDC.put("id", extTrxId);
        
        try {
            String target = inet.head().getString("target");
            logger.info("Processing request - target: {}", target);

            // 라우트 검색
            RouteMap routeMap = Router.getRoute(target);
            
            if (routeMap == null) {
                logger.warn("Route not found: {}", target);
                sendError(ctx, "Target not found: " + target);
                return;
            }

            // 요청 처리
            processRequest(ctx, routeMap, inet, extTrxId);
            
        } catch (Exception e) {
            logger.error("Unexpected error processing request", e);
            sendError(ctx, "내부 시스템 오류: " + e.getMessage());
        } finally {
            MDC.remove("id");
        }
    }

    /**
     * 요청을 처리하고 응답을 전송합니다.
     */
    private void processRequest(ChannelHandlerContext ctx, RouteMap routeMap, 
                                INet inet, String extTrxId) {
        // 기본 응답 객체 생성
        INet resInet = new INet()
                .head("id", ctx.channel().id().asShortText())
                .head("result", true)
                .head("message", "successful");

        try {
            // 요청 로깅
            if (routeMap.loggable || SystemUtils.deepview()) {
                JacksonUtils json = jsonUtils.get().pretty();
                logger.info("Request head:\n{}", json.toJson(inet.head()));
                logger.info("Request data:\n{}", json.toJson(inet.data()));
            }

            // 메서드 호출
            Object returnObj = null;
            try {
                returnObj = invoke(ctx, routeMap, inet);
            } catch (Exception e) {
                handleInvokeException(e, resInet);
                returnObj = resInet;
            }

            // Void 타입이 아닌 경우에만 응답 전송
            if (!routeMap.method.getReturnType().equals(Void.TYPE)) {
                prepareResponse(resInet, returnObj);
                sendResponse(ctx, resInet, routeMap, extTrxId);
            }
            // Void 타입인 경우 메서드 내부에서 직접 응답 처리

        } catch (Exception e) {
            logger.error("Error processing request", e);
            String errorMsg = e.getCause() != null ? 
                    CommonUtils.toString(e.getCause()) : e.getMessage();
            sendError(ctx, "내부 시스템 오류: " + errorMsg);
        }
    }

    /**
     * invoke 중 발생한 예외를 처리합니다.
     */
    private void handleInvokeException(Exception e, INet resInet) {
        Throwable cause = e.getCause();
        if (cause != null) {
            logger.error("Business logic exception: {}", cause.getMessage(), cause);
            
            String errorDetail = cause.getMessage();
            if (cause.getStackTrace().length > 0) {
                errorDetail += " at " + cause.getStackTrace()[0].toString();
            }
            
            resInet.head("result", false)
                   .head("message", "서버 내부 오류가 발생하였습니다.")
                   .data("response", errorDetail);
        } else {
            logger.error("Unexpected exception during invoke", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 반환 객체를 기반으로 응답을 준비합니다.
     */
    private void prepareResponse(INet resInet, Object returnObj) {
        if (returnObj == null) {
            return;
        }

        if (returnObj instanceof String) {
            resInet.data("response", returnObj);
        } else if (returnObj instanceof INet) {
            // INet 객체를 직접 반환한 경우 교체
            INet customInet = (INet) returnObj;
            resInet.head().putAll(customInet.head());
            resInet.data().putAll(customInet.data());
        } else if (returnObj instanceof Map) {
            resInet.data((Map<String, Object>) returnObj);
        } else {
            // 기타 객체는 JSON으로 변환
            resInet.data("response", jsonUtils.get().pretty().toJson(returnObj));
        }
    }

    /**
     * 응답을 전송합니다.
     */
    private void sendResponse(ChannelHandlerContext ctx, INet resInet, 
                             RouteMap routeMap, String extTrxId) {
        // 응답 로깅
        if (routeMap.loggable || SystemUtils.deepview()) {
            JacksonUtils json = jsonUtils.get().pretty();
            logger.info("Response head:\n{}", json.toJson(resInet.head()));
            logger.info("Response data:\n{}", json.toJson(resInet.data()));
        }

        logger.info("Response - result: {}, message: {}", 
                resInet.head().getString("result"), 
                resInet.head().getString("message"));

        resInet.data("extTrxId", extTrxId);

        // 응답 전송 및 연결 종료
        ctx.writeAndFlush(resInet).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                double elapsedMs = (System.nanoTime() - startTime) / 1e6d;
                logger.info("Response sent successfully - elapsed: {:.3f}ms", elapsedMs);
            } else {
                logger.error("Failed to send response", future.cause());
            }

            // 클라이언트에서 먼저 끊는 것을 방지하기 위한 지연
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            future.channel().close();
        });
    }

    /**
     * 리플렉션을 사용하여 라우트 메서드를 호출합니다.
     */
    private Object invoke(ChannelHandlerContext ctx, RouteMap routeMap, INet inet) 
            throws Exception {
        
        // 1. 객체 생성 (생성자 주입)
        Object instance = createInstance(routeMap.cls, ctx, inet);
        
        if (instance == null) {
            logger.error("Failed to create instance for route: {}", routeMap.method);
            throw new IllegalStateException("Route instance creation failed");
        }

        // 2. 필드 주입 (@Autowired)
        injectFields(instance, ctx, inet);

        // 3. 메서드 파라미터 준비
        List<Object> methodParams = prepareMethodParameters(
                routeMap.method.getParameters(), ctx, inet);

        // 4. 메서드 호출
        return routeMap.method.invoke(instance, methodParams.toArray());
    }

    /**
     * 생성자를 통해 인스턴스를 생성합니다.
     */
    private Object createInstance(Class<?> cls, ChannelHandlerContext ctx, INet inet) 
            throws Exception {
        
        Constructor<?>[] constructors = cls.getDeclaredConstructors();
        
        for (Constructor<?> constructor : constructors) {
            Class<?>[] paramTypes = constructor.getParameterTypes();
            
            if (paramTypes.length == 0) {
                return constructor.newInstance();
            }
            
            List<Object> params = new ArrayList<>();
            for (Class<?> paramType : paramTypes) {
                if (INet.class.isAssignableFrom(paramType)) {
                    params.add(inet);
                } else if (ChannelHandlerContext.class.isAssignableFrom(paramType)) {
                    params.add(ctx);
                } else {
                    params.add(paramType.getDeclaredConstructor().newInstance());
                }
            }
            
            return constructor.newInstance(params.toArray());
        }
        
        return null;
    }

    /**
     * @Autowired 필드를 주입합니다.
     */
    private void injectFields(Object instance, ChannelHandlerContext ctx, INet inet) 
            throws Exception {
        
        Field[] fields = instance.getClass().getDeclaredFields();
        
        for (Field field : fields) {
            field.setAccessible(true);
            
            // static 또는 final 필드는 건너뜀
            if (Modifier.isStatic(field.getModifiers()) || 
                Modifier.isFinal(field.getModifiers())) {
                continue;
            }

            // @Autowired 어노테이션 확인
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                if (annotation instanceof Autowired && !field.getType().isInterface()) {
                    Object fieldValue = createFieldValue(field.getType(), ctx, inet);
                    if (fieldValue != null) {
                        field.set(instance, fieldValue);
                    }
                }
            }
        }
    }

    /**
     * 필드 타입에 맞는 값을 생성합니다.
     */
    private Object createFieldValue(Class<?> type, ChannelHandlerContext ctx, INet inet) 
            throws Exception {
        
        if (INet.class.isAssignableFrom(type)) {
            return inet;
        } else if (ChannelHandlerContext.class.isAssignableFrom(type)) {
            return ctx;
        } else {
            return type.getDeclaredConstructor().newInstance();
        }
    }

    /**
     * 메서드 파라미터를 준비합니다.
     */
    private List<Object> prepareMethodParameters(Parameter[] parameters, 
                                                 ChannelHandlerContext ctx, 
                                                 INet inet) throws Exception {
        List<Object> params = new ArrayList<>();
        
        for (Parameter param : parameters) {
            Class<?> paramType = param.getType();
            
            if (INet.class.isAssignableFrom(paramType)) {
                params.add(inet);
            } else if (ChannelHandlerContext.class.isAssignableFrom(paramType)) {
                params.add(ctx);
            } else if (param.isAnnotationPresent(Head.class)) {
                params.add(extractHeadMap(param, inet));
            } else if (param.isAnnotationPresent(Data.class)) {
                params.add(extractDataMap(param, inet));
            } else {
                params.add(paramType.getDeclaredConstructor().newInstance());
            }
        }
        
        return params;
    }

    /**
     * @Head 어노테이션이 있는 파라미터에서 head 맵을 추출합니다.
     */
    private Object extractHeadMap(Parameter param, INet inet) throws Exception {
        if (isMapWithExpectedTypes(param)) {
            LinkedMap<String, Object> map = new LinkedMap<>();
            map.putAll(inet.head());
            return map;
        } else {
            logger.warn("@Head annotation requires LinkedMap<String, Object> type");
            return param.getType().getDeclaredConstructor().newInstance();
        }
    }

    /**
     * @Data 어노테이션이 있는 파라미터에서 data 맵을 추출합니다.
     */
    private Object extractDataMap(Parameter param, INet inet) throws Exception {
        if (isMapWithExpectedTypes(param)) {
            LinkedMap<String, Object> map = new LinkedMap<>();
            map.putAll(inet.data());
            return map;
        } else {
            logger.warn("@Data annotation requires LinkedMap<String, Object> type");
            return param.getType().getDeclaredConstructor().newInstance();
        }
    }

    /**
     * 파라미터가 LinkedMap<String, Object> 타입인지 확인합니다.
     */
    private static boolean isMapWithExpectedTypes(Parameter parameter) {
        if (!(parameter.getParameterizedType() instanceof ParameterizedType)) {
            return false;
        }

        ParameterizedType parameterizedType = (ParameterizedType) parameter.getParameterizedType();
        
        if (!parameterizedType.getRawType().equals(LinkedMap.class)) {
            return false;
        }

        Type[] typeArgs = parameterizedType.getActualTypeArguments();
        return typeArgs.length == 2 && 
               typeArgs[0].equals(String.class) && 
               typeArgs[1].equals(Object.class);
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("Channel inactive: {}", ctx.channel().id().asShortText());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 스택 트레이스 로깅
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] trace = cause.getStackTrace();

        if (trace != null && trace.length > 0) {
            int depth = Math.min(trace.length, 40);
            for (int i = 0; i < depth; i++) {
                sb.append(trace[i].toString()).append(System.lineSeparator());
            }
        }

        logger.error("Exception caught in channel: {}\n{}", cause.getMessage(), sb.toString());
        sendError(ctx, "내부 시스템 오류: " + cause.getMessage());
    }

    /**
     * 에러 응답을 전송합니다.
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        INet errorInet = new INet()
                .head("result", false)
                .head("message", message);

        ctx.writeAndFlush(errorInet).addListener((ChannelFutureListener) future -> {
            double elapsedMs = (System.nanoTime() - startTime) / 1e6d;
            logger.info("Error response sent - elapsed: {:.3f}ms", elapsedMs);
            future.channel().close();
        });
    }
}