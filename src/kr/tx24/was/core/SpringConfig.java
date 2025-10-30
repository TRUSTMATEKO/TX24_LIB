package kr.tx24.was.core;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.DefaultServletHandlerConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.ISpringTemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import kr.tx24.was.conf.TomcatConfigLoader;
import kr.tx24.was.interceptor.SessionInterceptor;
import kr.tx24.was.resolver.HeaderResolver;
import kr.tx24.was.resolver.ParamResolver;
import kr.tx24.was.resolver.SessionResolver;
import kr.tx24.was.util.HtmlEscapes;
import kr.tx24.was.util.Was;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;


/**
 * @author juseop
 * Spring 6.x / Thymeleaf 기반 WebMVC 설정
 * 
 * <p><b>중요:</b> Embedded Tomcat 환경에서는 @EnableWebMvc가 필수입니다.</p>
 */
@Configuration
@EnableWebMvc  // ⭐ 추가: 순수 Spring Framework 환경에서 필수!
@Import(ScanConfig.class)
public class SpringConfig implements WebMvcConfigurer {
	
	@Autowired
    private ApplicationContext applicationContext;
	
	
	/**
	 * JSON ObjectMapper를 Bean으로 정의
	 * - XSS 방지를 위한 HtmlEscapes 적용
	 * - 전체 애플리케이션에서 일관된 JSON 직렬화 설정 사용
	 */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // XSS 방지를 위한 HTML 특수문자 이스케이프 설정
        mapper.getFactory().setCharacterEscapes(new HtmlEscapes());
        
        // NULL 값은 JSON에 포함하지 않음
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        
        // JSON 포맷팅 및 날짜 형식 설정
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        	
        return mapper;
    }	
	
    
    /**
     * CORS 설정
     * <p><b>보안 권장사항:</b> 운영 환경에서는 allowedOrigins를 특정 도메인으로 제한하세요</p>
     */
	@Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*");
    }

	
	/**
	 * 뷰 컨트롤러 설정
	 * "/" 경로를 "index" 뷰로 매핑
	 */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("index");
    }
    

    /**
     * Default Servlet Handler 활성화
     * Spring MVC가 처리하지 못한 요청을 Tomcat의 Default Servlet으로 위임 (정적 자원 처리)
     */
    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        configurer.enable();
    }

    
    /**
     * 정적 자원 핸들러 설정
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("/assets/");
    }

    
    // --- Argument Resolver 설정 ---
    
    /**
     * 커스텀 아규먼트 리졸버 등록
     * - @Param: 요청 파라미터 및 Multipart 파일 처리
     * - @Header: HTTP 헤더 정보 처리
     * - @Session: Redis 기반 세션 정보 처리
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new ParamResolver());
        resolvers.add(new HeaderResolver());
        resolvers.add(new SessionResolver());
    }

    
    // --- Interceptor 설정 ---
    
    /**
     * 인터셉터 등록
     * 1. Locale 변경 인터셉터 (국제화 지원)
     * 2. 세션 인터셉터 (인증 및 세션 관리, 정적 자원 제외)
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Locale 변경 인터셉터
        registry.addInterceptor(localeChangeInterceptor());

        // 2. 세션 인터셉터 (정적 자원 경로 제외)
        registry.addInterceptor(getSessionInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/assets/**", "/static/**", "/upload/**", "/favicon.ico");
    }
    
    @Bean
    public SessionInterceptor getSessionInterceptor() {
        return new SessionInterceptor();
    }


    // --- Message Converter 설정 ---
    
    /**
     * HTTP Message Converter 설정 (Embedded Tomcat 환경)
     * 
     * <p><b>순수 Spring Framework 환경에서는 extendMessageConverters가 아닌
     * configureMessageConverters를 사용해야 합니다.</b></p>
     * 
     * <p>@EnableWebMvc와 함께 사용하면 기본 Converter가 자동 추가되지 않으므로,
     * 필요한 Converter를 명시적으로 추가해야 합니다.</p>
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 1. JSON Converter (가장 먼저 추가)
        MappingJackson2HttpMessageConverter jsonConverter = 
            new MappingJackson2HttpMessageConverter(objectMapper());
        
        // ⭐ 중요: application/json과 charset 변형을 모두 명시적으로 지원
        jsonConverter.setSupportedMediaTypes(List.of(
            MediaType.APPLICATION_JSON,
            new MediaType("application", "json", Was.DEFAULT_CHARSET),
            MediaType.valueOf("application/*+json")  // application/*+json도 지원
        ));
        
        converters.add(jsonConverter);
        
        // 2. String Converter (text/html, text/plain)
        StringHttpMessageConverter stringConverter = 
            new StringHttpMessageConverter(Was.DEFAULT_CHARSET);
        stringConverter.setSupportedMediaTypes(List.of(
            MediaType.TEXT_HTML,
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_XML,
            MediaType.valueOf("text/*")
        ));
        converters.add(stringConverter);
        
        // 3. Byte Array Converter (파일 다운로드/업로드)
        ByteArrayHttpMessageConverter byteArrayConverter = new ByteArrayHttpMessageConverter();
        converters.add(byteArrayConverter);
        
        // 4. Form Data Converter (자동 추가되지만 명시적으로 추가)
        // FormHttpMessageConverter는 Spring이 자동으로 추가하므로 생략 가능
    }
    
    
    // --- Thymeleaf 설정 ---

    /**
     * Thymeleaf ViewResolver 설정
     */
    @Bean
    public ViewResolver getViewResolver() {
    	ThymeleafViewResolver resolver = new ThymeleafViewResolver();
    	resolver.setTemplateEngine((ISpringTemplateEngine)templateEngine());
        resolver.setCharacterEncoding(Was.DEFAULT_CHARSET.name());
        resolver.setOrder(0);
        
        return resolver;
    }

    
    /**
     * Thymeleaf TemplateEngine 설정
     */
    @Bean
    public TemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver());
        engine.addDialect(new LayoutDialect());
        engine.setTemplateEngineMessageSource(messageSource());
        return engine;
    }

    
    /**
     * Thymeleaf TemplateResolver 설정
     */
    @Bean
    @Description("Thymeleaf Template Resolver")
    public SpringResourceTemplateResolver templateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("/");
        resolver.setSuffix(".html");
        resolver.setCacheTTLMs(60 * 1000L);
        resolver.setCacheable(TomcatConfigLoader.load().templateCacheable);
        resolver.setCharacterEncoding(Was.DEFAULT_CHARSET.name());
        resolver.setTemplateMode(TemplateMode.HTML);
        return resolver;
    }

    
    // --- 기타 유틸리티 설정 ---
    
    /**
     * Multipart Resolver 설정
     * Servlet 3.0+ 표준 기반
     */
    @Bean
    public MultipartResolver multipartResolver() {
        return new StandardServletMultipartResolver();
    }

    
    /**
     * 국제화 MessageSource 설정
     */
    @Bean
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(Was.DEFAULT_CHARSET.name());
        return messageSource;
    }

    
    /**
     * Locale Resolver 설정
     * 세션 기반 Locale 관리
     */
    @Bean
    public SessionLocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.KOREAN);
        return resolver;
    }

    
    /**
     * Locale 변경 인터셉터
     * URL 파라미터를 통한 Locale 변경 지원 (기본 파라미터명: 'locale')
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        return new LocaleChangeInterceptor();
    }
}