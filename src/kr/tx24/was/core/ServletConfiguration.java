package kr.tx24.was.core;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.DispatcherServlet;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRegistration;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.was.conf.TomcatConfig;
import kr.tx24.was.conf.TomcatConfigLoader;
import kr.tx24.was.util.Was;

/**
 * @author juseop
 * WEB-INF/web.xml  을 대체하는 설정파일 SET 
 */
@Configuration
public class ServletConfiguration implements WebApplicationInitializer {
	private static Logger logger = LoggerFactory.getLogger(ServletConfiguration.class );
	


	
	@Override
	public void onStartup(ServletContext servletContext) throws ServletException {
		
		//아래는 jar/ classes 에 의한 onStartUp 이 두번 이상 실행되는 것을 방지하기 위함입니다.
		String key = "spring.onstartup.done";
		if (servletContext.getAttribute(key) != null) {
	        return;
	    }
		servletContext.setAttribute(key, Boolean.TRUE);

		
		
		TomcatConfig config = TomcatConfigLoader.load();
		
		Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
		
		// 1. Root Application Context (Service, Repository, etc.)
		AnnotationConfigWebApplicationContext rootContext = new AnnotationConfigWebApplicationContext();
		rootContext.setDisplayName(config.serverName);
		if(!CommonUtils.isBlank(config.basePackage)) {
			rootContext.scan(config.basePackage.split(","));
		}
		rootContext.register(SpringConfig.class);
		
		
		// 2. ContextLoaderListener를 사용하여 Root Context를 웹 애플리케이션에 연결
		// 이 리스너가 context.refresh() 호출을 담당합니다.
		servletContext.addListener(new ContextLoaderListener(rootContext));
		
		
		
		// 3. DispatcherServlet Context (Controller, View Resolver, etc.)
		// 일반적으로 Root Context를 그대로 사용하거나, 별도의 Web 전용 Context를 사용합니다.
		// 여기서는 Root Context와 동일한 설정을 사용하도록 등록합니다.
		DispatcherServlet dispatcher = new DispatcherServlet(rootContext);
		/*
		dispatcher.setContextInitializers(ctx -> {
		    if (ctx instanceof AbstractApplicationContext aac) {
		        aac.setDisplayName(config.serverName);
		    }
		});*/
		ServletRegistration.Dynamic servlet = servletContext.addServlet("dispatcher", dispatcher);
		
		
		
		// 4. Encoding Filter 설정
		CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
		encodingFilter.setEncoding(Was.DEFAULT_CHARSET.name());
		encodingFilter.setForceEncoding(true);
		
		EnumSet<DispatcherType> dispatcherTypes = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD);
		FilterRegistration.Dynamic characterEncoding = servletContext.addFilter("CharacterEncodingFilter", encodingFilter);
		characterEncoding.addMappingForUrlPatterns(dispatcherTypes, true, "/*");
		
		// 5. Debug Filter 설정 (LoggingFilter)
		LoggingFilter debugFilter = new LoggingFilter();		
		FilterRegistration.Dynamic debug = servletContext.addFilter("DebugFilter", debugFilter);
		debug.addMappingForUrlPatterns(dispatcherTypes, true, "/*");
		
		// 6. Multipart Config 설정 (File Upload)
		String uploadDirectory = TomcatConfigLoader.load().uploadDirectory; 
		if(CommonUtils.isBlank(uploadDirectory)) {
			// 시스템 임시 디렉토리 사용 (null to blank 변환은 필요 없음)
			uploadDirectory = System.getProperty("java.io.tmpdir"); 
			if(SystemUtils.deepview()) {
				logger.info("uploadDirectory : {}",uploadDirectory);
			}
		}

		// (10MB * 5 = 50MB) Max File Size, (10MB * 5 = 50MB) Max Request Size, 0 = 임계값
		servlet.setMultipartConfig(new MultipartConfigElement(uploadDirectory, 1024*1024*10*5, 1024*1024*10*5, 0));
		
		// 7. Session Timeout (세션 비활성화 목적이라면 Server.java에서 NullManager 사용 권장)
		// ServletContext 레벨에서 세션 비활성화를 명확하게 표현하기 위해 -1 사용 고려 (컨테이너 의존적)
		servletContext.setSessionTimeout(-1); // -1: 세션 무제한 또는 비활성화 (컨테이너 해석에 따름)
		// servletContext.setSessionTimeout(0); // 0은 즉시 만료 (비활성화 아님)

		// 8. Static resource handling using "default" servlet
		// Tomcat Default Servlet을 사용한 정적 파일 매핑 (유지)
		ServletRegistration registration = servletContext.getServletRegistration ("default");
		registration.addMapping ("/assets/*","/static/*","/upload/*","*.js", "*.css", "*.jpg", "*.gif", "*.png","*.eot","*.svg","*.ttf","*.woff","*.woff2","*.otf","*.ico","*.zip","*.map");
		
		// 9. DispatcherServlet 매핑 및 Start Up
		servlet.setLoadOnStartup(1);
		servlet.addMapping("/");
		
	}
	
	
	
	
	
	

}
