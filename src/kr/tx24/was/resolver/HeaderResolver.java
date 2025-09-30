/**
 * LIBRARY
 * @author : juseop , 2023. 10. 6.
 */
package kr.tx24.was.resolver;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.was.annotation.Header;
import kr.tx24.was.util.Was;

@Component
public class HeaderResolver implements HandlerMethodArgumentResolver {

	private static final Logger logger = LoggerFactory.getLogger(HeaderResolver.class);
	
	
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 1. @Header annotation이 있는지 확인
		if (parameter.getParameterAnnotation(Header.class) == null) {
			return false;
		}
		// 2. 파라미터 타입이 Map 인터페이스를 구현했는지 확인 (복잡한 GenericType 검사 대체)
		return Map.class.isAssignableFrom(parameter.getParameterType());
	}
	
	
	@Override
	public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer modelAndViewContainer,
	            NativeWebRequest nativeWebRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {
	    
	    
	    // 1. 요청된 타입에 맞는 Map 인스턴스를 선언과 동시에 초기화합니다.
	    final Map<String, Object> targetMap = new Object() {
	        @SuppressWarnings("unchecked")
			Map<String, Object> createMap() {
	            Class<?> paramType = methodParameter.getParameterType();
	            
	            // ⭐ 모든 Map 구현체를 동적으로 인스턴스화 시도
	            if (Map.class.isAssignableFrom(paramType)) {
	                try {
	                    // 요청된 Map 타입의 인스턴스를 생성 (기본 생성자 필수)
	                    return (Map<String, Object>) paramType.getDeclaredConstructor().newInstance();
	                } catch (Exception e) {
	                    // 인스턴스 생성 실패 시 (예: 기본 생성자가 없거나 private인 경우)
	                    // LinkedMap, HashMap 등 모든 Map 구현체에 대해 발생 가능
	                    logger.warn("Failed to instantiate requested Map type ({}). Falling back to SharedMap.", paramType.getName(), e);
	                    // 실패 시 SharedMap으로 Fallback
	                    return new SharedMap<String, Object>(); 
	                }
	            }
	            
	            // Map 타입이 아닌 경우 (supportsParameter에서 걸러지지만 안전을 위해)
	            // 이 경로는 supportsParameter가 true를 반환하는 한 도달하지 않습니다.
	            return new SharedMap<String, Object>();
	        }
	    }.createMap();
	    
	    
	    // 2. HttpServletRequest 가져오기 (Jakarta EE)
	    HttpServletRequest httpServletRequest = nativeWebRequest.getNativeRequest(HttpServletRequest.class);
	    
	    // 3. 헤더 정보 추출
	    Enumeration<String> headers = httpServletRequest.getHeaderNames();
	    
	    // targetMap의 put 메서드를 호출하여 헤더 정보 채우기
	    Collections.list(headers).forEach(headerName -> {
	        // 헤더 이름은 소문자로 변환하여 저장
	        targetMap.put(headerName.toLowerCase(), CommonUtils.nToB(httpServletRequest.getHeader(headerName)));
	    });
	    
	    // IP 확인 및 SET
	    targetMap.put(Was.REMOTE_IP, Was.getRemoteIp(httpServletRequest));
	    
	    // 4. 로깅
	    /*
	    if(SystemUtils.deepview()) {
	        logger.info("@Header : {}", new JacksonUtils().toJson(targetMap));
	    }*/
	    
	    return targetMap;
	}
}
