package kr.tx24.was.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.was.util.Was;

/**
 * @author juseop
 * Request/Response 로깅 필터
 * 
 * <p><b>성능 최적화:</b> SystemUtils.deepview()가 true일 때만 
 * ContentCachingWrapper를 사용하여 상세 로깅을 수행합니다.</p>
 */
public class LoggingFilter implements Filter {

	private static Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
			throws IOException, ServletException {
		
		HttpServletRequest httpRequest = (HttpServletRequest)request;
		HttpServletResponse httpResponse = (HttpServletResponse)response;
		
		long startTime = System.nanoTime();
		boolean isDynamicResource = isDynamicResource(httpRequest.getRequestURI().toLowerCase());
		boolean isDeepView = SystemUtils.deepview();
		
		
		if(isDeepView && isDynamicResource){
			logger.info("-------    START    -------");
		}
		
		// 보안: GET, POST 이외 메서드 차단
		String method = httpRequest.getMethod().toUpperCase();
		if(!method.equals("POST") && !method.equals("GET")) {
			logger.info("Request {} ,method '{}' not allowed : {}", 
					httpRequest.getRequestURI(), httpRequest.getMethod(), Was.getRemoteIp(httpRequest));
			httpResponse.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
			return;
		}
		
		// 정적 리소스는 로깅 없이 바로 처리
		if(!isDynamicResource) {
			chain.doFilter(request, response);
			return;
		}
		
		// 기본 로깅 (deepview 여부와 관계없이)
		logger.info("uri: {}, {}, ip: {}", 
				httpRequest.getRequestURI(), method, Was.getRemoteIp(httpRequest));
		
		if(!method.equals("GET")) {
			logger.info("type: {}, {}, size: {}", 
					CommonUtils.nToB(httpRequest.getContentType()), 
					httpRequest.getCharacterEncoding(), 
					httpRequest.getContentLengthLong());
		}
		
		// ⭐ deepview일 때만 ContentCachingWrapper 사용
		if(isDeepView) {
			processWithCaching(httpRequest, httpResponse, chain, startTime, isDynamicResource);
		} else {
			// deepview가 아니면 원본 request/response를 그대로 사용
			chain.doFilter(request, response);
			
			
			
		}
		if(isDeepView && isDynamicResource){
			logger.info("-------    END      ------- {}\n", 
					String.format("%.3fms", (System.nanoTime() - startTime) / 1e6d));
		}
	}
	
	/**
	 * ContentCachingWrapper를 사용한 상세 로깅 처리
	 * SystemUtils.deepview()가 true일 때만 호출됨
	 */
	private void processWithCaching(HttpServletRequest httpRequest, HttpServletResponse httpResponse, 
			FilterChain chain, long startTime, boolean isDynamicResource) 
			throws IOException, ServletException {
		
		// ContentCachingWrapper로 래핑
		ContentCachingRequestWrapper wrappedRequest = 
				new ContentCachingRequestWrapper(httpRequest);
		ContentCachingResponseWrapper wrappedResponse = 
				new ContentCachingResponseWrapper(httpResponse);
		
		try {
			chain.doFilter(wrappedRequest, wrappedResponse);
			
			// chain.doFilter() 이후에 캐시된 내용을 로깅
			logRequestAndResponse(wrappedRequest, wrappedResponse);
			
		} finally {
			// Response body를 실제 응답으로 복사 (필수!)
			wrappedResponse.copyBodyToResponse();
			
		}
	}
	
	/**
	 * Request와 Response 상세 로깅
	 * SystemUtils.deepview()가 true이고 chain.doFilter() 이후에만 호출됨
	 */
	private void logRequestAndResponse(ContentCachingRequestWrapper request, 
			ContentCachingResponseWrapper response) {
		
		// /axios로 시작하는 경로는 상세 로깅 제외
		if(request.getRequestURI().startsWith("/axios")) {
			return;
		}
		
		// Request Body 로깅
		String requestBody = getRequestBody(request);
		if(!requestBody.equals("{}")) {
			logger.info("request: {}", requestBody);
		}
		
		// Response 로깅
		String contentType = CommonUtils.nToB(response.getContentType()).toLowerCase();
		if(contentType.startsWith("application/json")) {
			logger.info("response: {}, {}", response.getStatus(), getResponseBody(response));
		} else {
			logger.info("response: {}, {}, {}", 
					response.getStatus(), contentType, response.getContentSize());
		}
	}
	
	/**
	 * 동적 리소스 여부 확인
	 */
	private static boolean isDynamicResource(String uri) {
		// 제외 접두사 확인
		for(String prefix : Was.EXCLUDE_PREFIXS) {
			if(uri.startsWith(prefix)) {
				return false;
			}
		}
		
		// 제외 접미사 확인
		for(String suffix : Was.EXCLUDE_SUFFIXS) {
			if(uri.endsWith(suffix)) {
				return false;
			}
		}
		
		return true;
	}
	
	/**
	 * Request Body 읽기
	 * ⭐ 주의: chain.doFilter() 이후에만 호출해야 캐시된 내용을 읽을 수 있음
	 */
	private String getRequestBody(ContentCachingRequestWrapper request) {
		byte[] buf = request.getContentAsByteArray();
		if (buf.length > 0) {
			try {
				return new String(buf, 0, buf.length, request.getCharacterEncoding());
			} catch (UnsupportedEncodingException e) {
				logger.error("Failed to decode request body: {}", e.getMessage());
				return "{}";
			}
		}
		return "{}";
	}
	
	/**
	 * Response Body 읽기
	 */
	private String getResponseBody(ContentCachingResponseWrapper response) {
		byte[] buf = response.getContentAsByteArray();
		if (buf.length > 0) {
			try {
				return new String(buf, 0, buf.length, response.getCharacterEncoding());
			} catch (UnsupportedEncodingException e) {
				logger.error("Failed to decode response body: {}", e.getMessage());
				return "{}";
			}
		}
		return "{}";
	}
}