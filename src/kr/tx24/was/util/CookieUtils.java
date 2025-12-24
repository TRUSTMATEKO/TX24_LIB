package kr.tx24.was.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.mapper.JacksonUtils;

/**
 * @author juseop
 *
 */
public class CookieUtils {

	private static Logger logger = LoggerFactory.getLogger(CookieUtils.class);
	

	

	
	public static Cookie get(HttpServletRequest request){
		return get(request,SessionUtils.SESSION_KEY);
	}
	
	
	public static Cookie get(HttpServletRequest request,String name){
		Cookie cookie = null;
	
		Cookie[] cookies = request.getCookies();
		if(cookies != null){
			for(Cookie ck : cookies){
				if(ck.getName().equals(name)){
					cookie = ck;
					break;
				}
			}
		}
		
		return cookie;
	}
	
	
	
	public static void extend(HttpServletRequest request,String name,int maxAge){
		Cookie cookie = get(request, name);
		if(cookie != null){
			cookie.setMaxAge(maxAge);
			
		}
	}
	
	
	public static String getValue(HttpServletRequest request){
		return getValue(request,SessionUtils.SESSION_KEY);
	}
	
	public static String getValue(HttpServletRequest request,String name){
		Cookie cookie = get(request,name);
		if(cookie == null){
			return "";
		}else{
			return cookie.getValue();
		}
	}
	
	
	public static Cookie create(HttpServletResponse response,String domain, String path, String name, String value, int maxAge, boolean secured, boolean httpOnly){
		Cookie cookie = new Cookie(name,value);
		//cookie.setDomain(domain);
		cookie.setHttpOnly(httpOnly);
		cookie.setMaxAge(maxAge);
		cookie.setSecure(secured);
		cookie.setPath(path);
		
		if(SystemUtils.deepview()) {
			logger.info("create cookie :{}",new JacksonUtils().toJson(cookie));
		}
		response.addCookie(cookie);
		return cookie;
	}
	
	public static void remove(HttpServletResponse response){
		remove(response,SessionUtils.SESSION_KEY);
	}
	
	
	public static void remove(HttpServletResponse response,String name){
		Cookie cookie = new Cookie(name,"");
		cookie.setMaxAge(0);
		cookie.setValue("");
		
		response.addCookie(cookie);
		logger.debug("remove cookie name :{},{}",cookie.getName(),cookie.getValue());
	}
	
	public static void removeAll(HttpServletRequest request,HttpServletResponse response){
		Cookie[] cookies = request.getCookies();
		if(cookies != null){
			for(Cookie cookie : cookies){
				logger.debug("remove cookie name :{},{}",cookie.getName(),cookie.getDomain());
				cookie.setMaxAge(0);
				cookie.setValue("");
				response.addCookie(cookie);
			}
		}	
	}
	

}
