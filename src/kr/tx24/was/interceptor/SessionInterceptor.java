package kr.tx24.was.interceptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import io.netty.handler.codec.http.HttpMethod;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.was.annotation.SessionIgnore;
import kr.tx24.was.annotation.UserDid;
import kr.tx24.was.util.CookieUtils;
import kr.tx24.was.util.SessionUtils;
import kr.tx24.was.util.UserDidAware;
import kr.tx24.was.util.Was;

/**
 * @author juseop
 *
 */
public class SessionInterceptor implements HandlerInterceptor{
	private static Logger logger = LoggerFactory.getLogger( SessionInterceptor.class );
	
	private static final String HEADER_AJAX 		= "X-Requested-With";
	private static final String HEADER_FROM_AJAX 	= "X-FromAjax";
	private static final String AJAX_VALUE 			= "xmlhttprequest";
	private static final String AJAX_TRUE 			= "1";
	private static final String LOGIN_REDIRECT_URI 	= "/init";
	private static final String POPUP_PARAM 		= "P";
	private static final String FORM_SUFFIX 		= "form";
	private static final String LAST_URI_KEY 		= "LASTURI";
	private static final String AJAX_LOGIC_KEYWORD 	= "axios"; // reqUri.contains("axios")에 사용됨
	private static final String LASTURI_UPDATE_FLAG = "HAS_LASTURI_UPDATE";

	// ⭐ 사용자 행동 기록(USER_DID)을 건너뛸 URI 접두사 목록 (List로 통합)
	private static final List<String> URI_SKIP_PREFIXES = List.of(
	    "/axios/", 
	    "/hq/user-did/", 
	    "/dashboard/"
	);
	
 
	@Autowired
    private ListableBeanFactory beanFactory;
	
    private final List<UserDidAware> userDidLists = new ArrayList<>();

    @PostConstruct
    public void init() {
        Map<String, Object> beans = beanFactory.getBeansWithAnnotation(UserDid.class);
        for (Object bean : beans.values()) {
            if (bean instanceof UserDidAware aware) {
            	userDidLists.add(aware);
            }
        }
        if(!userDidLists.isEmpty()) {
        	logger.info("userdid beans : {}", userDidLists.toString());
        }
        
    }
	
	@Override
	public boolean preHandle(HttpServletRequest request,HttpServletResponse response, Object handler) throws java.lang.Exception {
		// 1. HTTP OPTIONS 요청 무시
		if (request.getMethod().equals(HttpMethod.OPTIONS.toString())) {
			return true;
		}
	
		// 2. SessionInterceptor 로직 실행
		if (!handleSessionCheck(request, response, handler)) {
			// 세션 체크 실패 (Redirect 또는 401 Error 발생) 시 처리 중단
			return false;
		}
	
		// 3. DownloadProxyInterceptor 로직 실행
		String searchType = request.getHeader("searchType");
		return switch (searchType != null ? searchType.toLowerCase() : "") {
		    case "download" -> {
		        String originalUrl = request.getRequestURI();
		        if (originalUrl.startsWith("/file")) {
		            yield true; // /file로 시작하면 그대로 진행
		        }

		        String modifiedUrl = "/file" + originalUrl;
		        request.getRequestDispatcher(modifiedUrl).forward(request, response);
		        yield false; // 프록시 후 처리 중단
		    }
		    default -> true; // searchType 헤더가 없거나 'download'가 아니면 그대로 진행
		};
		
	}
	
	private boolean handleSessionCheck(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
	    
	    // 1. Session 무시 여부 확인
	    boolean checkSession = true;
	    if (handler instanceof HandlerMethod handlerMethod) { // JDK 17+ pattern matching
	        checkSession = !handlerMethod.hasMethodAnnotation(SessionIgnore.class);
	    }
	    
	    // 인증 처리 로직
	    if (checkSession) {
	        // Ajax 요청 여부 확인
	        boolean isAjax = Was.getHeader(request, HEADER_AJAX).toLowerCase().equals(AJAX_VALUE) || Was.getHeader(request, HEADER_FROM_AJAX).equals(AJAX_TRUE);
	        
	        SharedMap<String,Object> sessionMap = SessionUtils.getBySessionId(CookieUtils.getValue(request, Was.SESSION_ID));	
	        
	        // 2. 세션 검증 및 처리
	        if (sessionMap == null) {	
	            if (isAjax) {
	                // Ajax: 401 Unauthorized 반환
	                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "사용자 세션이 종료되었습니다.");
	            } else { 
	                // 일반 요청: 로그인 페이지로 리다이렉트
	                logger.info("redirect init : {}", Collections.list(request.getHeaderNames()));
	                response.sendRedirect(LOGIN_REDIRECT_URI);
	            }
	            return false; // 세션 없으면 처리 중단
	        } 
	        
	        // 3. 세션 존재 시 후속 처리
	        
	        // MDC (Mapped Diagnostic Context)에 사용자 ID 저장
	        MDC.put("userId", sessionMap.getString(SessionUtils.SESSION_USERID));	
	        
	        String reqUri = request.getRequestURI();
	        
	        // ⭐ URI SKIP 조건: URI_SKIP_PREFIXES 중 하나로 시작하는지 확인
	        boolean isRequestUriSkipped = URI_SKIP_PREFIXES.stream().anyMatch(reqUri::startsWith);

	        	// GET 요청이 아닌 Ajax 요청(POST, PUT 등)은 세션 갱신 로직 블록을 건너뜁니다.
	        if (!(isAjax && !request.getMethod().equals(HttpMethod.GET.toString()))) { 
	            // 일반 요청 및 GET AJAX 처리 (세션 갱신)
	            if (!reqUri.startsWith(LOGIN_REDIRECT_URI) && reqUri.indexOf("[.]") < 1) {
	                if (!isRequestUriSkipped) {
	                    logger.info("Session Save: URI={}, UserID={}", reqUri, sessionMap.getString(SessionUtils.SESSION_USERID));
	                    SessionUtils.save(sessionMap); // Redis 세션 만료 시간 갱신
	                }
	            }
	        }
	        
	        // 4. 사용자 행동 기록 (USER_DID)
	        if (!isRequestUriSkipped && !userDidLists.isEmpty()) { // SKIP 조건 재사용
	            
	            SharedMap<String,Object> userDidMap = new SharedMap<String,Object>();
	            userDidMap.put("sessionId"	, sessionMap.getString(SessionUtils.SESSION_ID));
	            userDidMap.put("id"			, sessionMap.getString(SessionUtils.SESSION_USERID));
	            userDidMap.put("uri"		, reqUri);
	            userDidMap.put("method"		, request.getMethod());
	            userDidMap.put("pageId"		, "");
	            userDidMap.put("payload"	, "");
	            userDidMap.put("regDay"		, DateUtils.getRegDay());
	            
	            for (UserDidAware bean : userDidLists) {
	                bean.setUserDid(userDidMap);
	            }
	            
	            
	        }
	        
	        request.setAttribute(Was.SESSION_ID, sessionMap); // Attribute 세션 저장
	    } else {
	        // SessionIgnore 처리
	        if(SystemUtils.deepview() && !request.getRequestURI().equals("/error")){
	            logger.debug("ignore session :{}", request.getRequestURI());
	        }
	    }
	    
	    return true; // 세션 체크 통과 또는 무시됨
	}
	
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
		
		// ⭐ 1. 응답 코드가 200 OK일 때만 처리하도록 필터링 추가
	    if (response.getStatus() != HttpServletResponse.SC_OK) {
	        MDC.remove("userId");
	        return; // 200 OK가 아니면 처리 중단
	    }
	    
		
	    String reqUri = request.getRequestURI();
	    boolean isUriSkipped = URI_SKIP_PREFIXES.stream().anyMatch(reqUri::startsWith);
	    
	    if (!isUriSkipped) { // /axios/, /hq/user-did/, /dashboard/ 가 아닌 경우
	        
	        // Cookie 를 통한 세션 연장 처리 (기존 로직)
	        SharedMap<String,Object> sessionMap = (SharedMap<String,Object>)request.getAttribute(Was.SESSION_ID);
	        
	        	if(!CommonUtils.isEmpty(sessionMap)){
	            
	            // ⭐ LASTURI 임시 저장 로직 (View 오류를 감지하기 위해 세션에 임시 반영)
	            boolean isNotAxios = !reqUri.contains(AJAX_LOGIC_KEYWORD);
	            if (isNotAxios) {
	                boolean isNotPopup = !CommonUtils.nToB(request.getParameter(POPUP_PARAM)).equalsIgnoreCase("Y");
	                if (isNotPopup) {
	                    if (reqUri.endsWith(FORM_SUFFIX)) {
	                        // 세션 맵에 LASTURI를 임시 기록
	                        sessionMap.put(LAST_URI_KEY, reqUri);
	                        // afterCompletion에서 최종 저장을 위한 플래그 설정
	                        request.setAttribute(LASTURI_UPDATE_FLAG, true); 
	                    }
	                }
	            }
	            // ⭐ LASTURI 임시 저장 로직 끝

	            // Cookie 생성 및 ModelAndView 설정 (기존 로직)
	            String sessionId = sessionMap.getString(SessionUtils.SESSION_ID);
	            CookieUtils.create(response,request.getHeader("host"), "/", Was.SESSION_ID, sessionId, Was.SESSION_EXPIRE, false, true);
	            if (modelAndView != null) {
	                modelAndView.addObject(Was.SESSION_ID, sessionMap);
	            }
	        }
	    }

	 
		
	}
	
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
		
		// 1. LASTURI 최종 저장 로직 (View 렌더링 포함, 요청 처리가 성공했을 때만)
	    if (ex == null) { // 컨트롤러 실행이나 뷰 렌더링 과정에서 예외가 발생하지 않았을 경우
	    	boolean lastUriUpdate = CommonUtils.toBoolean(request.getAttribute(LASTURI_UPDATE_FLAG));
	        
	        // 플래그가 true이면 세션 맵을 Redis에 최종 반영
	        if (lastUriUpdate) {
	            @SuppressWarnings("unchecked")
				SharedMap<String,Object> sessionMap = (SharedMap<String,Object>)request.getAttribute(Was.SESSION_ID);
	            if (!CommonUtils.isEmpty(sessionMap)) {
	                SessionUtils.save(sessionMap); 
	            }
	        }
	    }
		
	    // 요청 처리가 완전히 끝난 후, Exception 발생 여부와 관계없이 MDC를 정리합니다.
	    MDC.remove("userId");
	}

}
