/**
 * LIBRARY
 * @author : juseop , 2023. 10. 6.
 */
package kr.tx24.was.resolver;

import java.util.Arrays;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartRequest;

import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.mapper.JacksonUtils;
import kr.tx24.was.annotation.Param;
import kr.tx24.was.util.Was;

@Component
public class ParamResolver implements HandlerMethodArgumentResolver {

	private static final Logger logger = LoggerFactory.getLogger(ParamResolver.class);
	// private static final String JSON_BODY_ATTRIBUTE = "JSON_REQUEST_BODY"; // JSON 처리 제거로 불필요
	
	
	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		// 1. @Param annotation이 있는지 확인
		if (parameter.getParameterAnnotation(Param.class) == null) {
			return false;
		}
		// 2. 파라미터 타입이 java.util.Map 인터페이스를 구현했는지 확인 (모든 Map 허용)
		return Map.class.isAssignableFrom(parameter.getParameterType());
	}
	
	@Override
	public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer modelAndViewContainer,NativeWebRequest nativeWebRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {
		

		// 1. 요청된 타입에 맞는 Map 인스턴스를 동적으로 생성 (IILE 패턴 사용)
		final Map<String, Object> targetMap = new Object() {
	        Map<String, Object> createMap() {
	            Class<?> paramType = methodParameter.getParameterType();
	            
	            if (Map.class.isAssignableFrom(paramType)) {
	                try {
	                    // 요청된 Map 타입의 인스턴스를 생성 (기본 생성자 필수)
	                    // 이 로직은 SharedMap, LinkedMap, HashMap 등 기본 생성자가 있는 모든 Map에 작동합니다.
	                    return (Map<String, Object>) paramType.getDeclaredConstructor().newInstance();
	                } catch (Exception e) {
	                    // 인스턴스 생성 실패 시 (기본 생성자 없음 등) SharedMap으로 Fallback
	                    logger.warn("Failed to instantiate requested Map type ({}). Falling back to SharedMap.", paramType.getName(), e);
	                    return new SharedMap<String, Object>(); 
	                }
	            }
	            // supportsParameter에서 걸러지지만, 예외 상황 대비 기본 SharedMap 반환
	            return new SharedMap<String, Object>();
	        }
	    }.createMap();
		
	
	    //4. 로깅
	    if(SystemUtils.deepview()) {
	    	logger.info("@Param : {}", new JacksonUtils().toJson(targetMap));
	    }
	
		
		
        // 2. 파라미터 맵핑 로직 (Query String 및 Form Data)
	    Map<String, String[]> map = nativeWebRequest.getParameterMap();

	    if(map != null && !map.isEmpty()) {
	    	for(String key : map.keySet()) {
	    		String[] value = map.get(key);
	    		if(value == null || value.length == 0) {
	    			targetMap.put(key, "");
	    		}else {
	    			if(value.length == 1) {
	    				targetMap.put(key, value[0]);
	    			}else {
	    				targetMap.put(key, Arrays.asList(value));
	    			}
	    		}
	    	}
	    }
	    
        // 3. Multipart 파일 맵핑
	    MultipartRequest multipartRequest = nativeWebRequest.getNativeRequest(MultipartRequest.class);
	    if(multipartRequest != null) {
	    	Map<String, MultipartFile> fileMap = multipartRequest.getFileMap();
	    	if(fileMap != null) {
	    		targetMap.put(Was.FILE_MAP, fileMap);
	    	}
	    }
	    
        

	    return targetMap;
	}
}