/**
 * @author : juseop , 2023. 10. 6.
 */
package kr.tx24.was.resolver;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.mapper.JacksonUtils;
import kr.tx24.was.annotation.Session;
import kr.tx24.was.util.CookieUtils;
import kr.tx24.was.util.SessionUtils;
import kr.tx24.was.util.Was;


@Component
public class SessionResolver implements HandlerMethodArgumentResolver{

	private static final Logger logger = LoggerFactory.getLogger( SessionResolver.class );
	
	
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 1. @Session annotation이 있는지 확인
		if (parameter.getParameterAnnotation(Session.class) == null) {
			return false;
		}
		// 2. 파라미터 타입이 java.util.Map 인터페이스를 구현했는지 확인 (모든 Map 허용)
		return Map.class.isAssignableFrom(parameter.getParameterType());
	}
	
    // ⭐ Spring 6 표준 시그니처 (4개의 인자)
	@Override
	public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer modelAndViewContainer,NativeWebRequest nativeWebRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {
		
		
		
		final SharedMap<String,Object> sessionData = new Object() {
	        SharedMap<String, Object> getSessionData() {
	            @SuppressWarnings("unchecked")
	            SharedMap<String,Object> map = (SharedMap<String,Object>)nativeWebRequest.getAttribute(Was.SESSION_ID, RequestAttributes.SCOPE_REQUEST);
	            
	            if(map == null) {
	                HttpServletRequest httpServletRequest = nativeWebRequest.getNativeRequest(HttpServletRequest.class);
	                map = SessionUtils.getBySessionId(CookieUtils.getValue(httpServletRequest, Was.SESSION_ID));	
	                if(map == null) {
	                    // 세션이 없으면 빈 SharedMap을 반환
	                    return new SharedMap<String,Object>();
	                }
	            }
	            return map;
	        }
	    }.getSessionData(); // 즉시 실행하여 sessionData에 최종 값을 할당
	    
	    
	    if(SystemUtils.deepview()) {
			logger.info("@Session : {}", new JacksonUtils().toJson(sessionData));
		}
	    
	    if(SharedMap.class.isAssignableFrom(methodParameter.getParameterType())) {
	    	return sessionData;
	    }

	    
        // 2. 요청된 Map 타입의 인스턴스를 동적으로 생성 (IILE 패턴 사용)
        final Map<String, Object> targetMap = new Object() {
            Map<String, Object> createMap() {
                Class<?> paramType = methodParameter.getParameterType();
                
                if (Map.class.isAssignableFrom(paramType)) {
                    try {
                        // 요청된 Map 타입의 인스턴스를 생성
                        Map<String, Object> newMap = (Map<String, Object>) paramType.getDeclaredConstructor().newInstance();
                        // 생성된 맵에 세션 데이터를 복사
                        newMap.putAll(sessionData);
                        return newMap;
                    } catch (Exception e) {
                        // 인스턴스 생성 실패 시 SharedMap으로 Fallback
                        logger.warn("Failed to instantiate requested Map type ({}). Falling back to SharedMap.", paramType.getName(), e);
                        return sessionData; // 이미 가져온 SharedMap 데이터를 그대로 반환
                    }
                }
                // Map이 아닌 경우 (supportsParameter에서 걸러지지만 안전을 위해)
                return sessionData;
            }
        }.createMap();
        
		
		return targetMap;
	}
}