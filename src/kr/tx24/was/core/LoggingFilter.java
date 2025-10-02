package kr.tx24.was.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

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
 *
 */
public class LoggingFilter implements Filter {

	private static Logger logger = LoggerFactory.getLogger( LoggingFilter.class );
	
	

	
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		
		HttpServletRequest httpRequest = (HttpServletRequest)request;
		
		 
		
		long startTime = System.nanoTime();
		
		boolean isDynamicResource 	= isDynamicResource(httpRequest.getRequestURI().toLowerCase());
		
		
		if(SystemUtils.deepview() && isDynamicResource){
			logger.info("-------    START    -------");
		}
		
		
		
		//보안적인 측면을 위하여 GET, POST 이외에는 허용하지 않는다.
		if(httpRequest.getMethod().toUpperCase().equals("POST") || httpRequest.getMethod().toUpperCase().equals("GET") ) {
			
			
			if(isDynamicResource) {
				
				logger.info("uri :{},{},ip:{}",httpRequest.getRequestURI(),httpRequest.getMethod(),Was.getRemoteIp(httpRequest));	
				if(!httpRequest.getMethod().equals("GET")) {
					logger.info("type:{},{},size :{}"
							,CommonUtils.nToB(httpRequest.getContentType()),httpRequest.getCharacterEncoding(),httpRequest.getContentLengthLong());
				}
				
				if(request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
					ContentCachingRequestWrapper httpServletRequest = new ContentCachingRequestWrapper((HttpServletRequest) request);
					ContentCachingResponseWrapper httpServletResponse = new ContentCachingResponseWrapper((HttpServletResponse) response);
					
					chain.doFilter(httpServletRequest, httpServletResponse);
					
					SharedMap<String,Object> userDidMap = new SharedMap<String,Object>();
					
					userDidMap.put("uri"	, httpServletRequest.getRequestURI());
					userDidMap.put("method"	, httpServletRequest.getMethod());
					userDidMap.put("pageId"	, CommonUtils.nToB(httpServletRequest.getHeader("X-PAGE-ID")));
					userDidMap.put("payload", getRequestBody(httpServletRequest));
					
					
					if(SystemUtils.deepview() && !httpServletRequest.getRequestURI().startsWith("/axios")) {
						logger.info("request :{}",userDidMap.getString("payload"));
						String contentType = CommonUtils.nToB((httpServletResponse.getContentType())).toLowerCase();
						if(contentType.startsWith("application/json")) {
							logger.info("response:{},{}",httpServletResponse.getStatus(),getResponseBody(httpServletResponse));
						}else {
							logger.info("response:{},{},{}",httpServletResponse.getStatus(),contentType,httpServletResponse.getContentSize());
						}
					}
				    
					
					httpServletResponse.copyBodyToResponse();
					
				}else {
					chain.doFilter(request, response);
				}
			}else {
				chain.doFilter(request, response);
			}
			
		}else {	
			logger.info("Request {} ,method '{}' not allowed : {}",httpRequest.getRequestURI(),httpRequest.getMethod(),Was.getRemoteIp(httpRequest));
			((HttpServletResponse) response).sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		}
		
		
		if(SystemUtils.deepview() && isDynamicResource){
			logger.info("-------    END      ------- {}\n",String.format("%.3fms",(System.nanoTime()- startTime) / 1e6d));
		}
		
	}
	
	
	private static boolean isDynamicResource(String uri){
		
		boolean isDynamic = true;
		for(String s: Was.EXCLUDE_PREFIXS) {
			if(uri.startsWith(s)) {
				isDynamic = false;
				break;
			}
		}
		if(!isDynamic) {
			return false;
		}
		
		for(String s: Was.EXCLUDE_SUFFIXS) {
			if(uri.endsWith(s)) {
				isDynamic = false;
				break;
			}
		}
		if(!isDynamic) {
			return false;
		}
		
		
		
		return true;
		
	}
	
	
	private String getRequestBody(ContentCachingRequestWrapper request) {
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper != null) {
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                try {
                    return new String(buf, 0, buf.length, wrapper.getCharacterEncoding());
                } catch (UnsupportedEncodingException e) {
                    return "{}";
                }
            }
        }
        return "{}";
    }
	
	
	private String getResponseBody(HttpServletResponse response) throws IOException {
        String payload = null;
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            wrapper.setCharacterEncoding("UTF-8");
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                payload = new String(buf, 0, buf.length, wrapper.getCharacterEncoding());
                wrapper.copyBodyToResponse();
            }
        }
        return null == payload ? "{}" : payload;
    }
	
	
	

}
