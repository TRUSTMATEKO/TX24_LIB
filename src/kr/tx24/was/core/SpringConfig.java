package kr.tx24.was.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
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
 */
@Configuration
@EnableWebMvc
@ComponentScan(basePackages = {"kr.tx24.app"})
public class SpringConfig implements WebMvcConfigurer {
	
	
	@Autowired
    private ApplicationContext applicationContext;
	
	
	// JSON ObjectMapper를 Bean으로 정의하여 전체 애플리케이션에서 일관되게 사용
    @Bean
    public ObjectMapper objectMapper() {
        // HtmlEscapes는 JSON 변환 시 XSS 방지를 위해 필수적으로 설정
        ObjectMapper mapper = new ObjectMapper();
        mapper.getFactory().setCharacterEscapes(new HtmlEscapes());
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        
        // 날짜 형식 및 직렬화 설정 통합
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO 8601 대신 DateFormat 사용
        	
        return mapper;
    }	
	
	@Override
    public void addCorsMappings(CorsRegistry registry) {
        // Cross-Origin Resource Sharing 설정
        registry.addMapping("/**")
                .allowedOrigins("*"); // 보안상 특정 도메인으로 제한하는 것을 권장
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 뷰 컨트롤러: "/" 경로를 "index.html" (Thymeleaf)로 매핑
        registry.addViewController("/").setViewName("index");
    }
    


    @Override
    public void configureDefaultServletHandling(DefaultServletHandlerConfigurer configurer) {
        // Spring MVC가 처리하지 못한 요청은 Tomcat의 Default Servlet으로 위임 (정적 자원 처리 보조)
        configurer.enable();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 정적 자원 핸들러 추가 (Default Servlet Handling과 역할 분담)
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("/assets/");
        // WAS의 /webroot 경로가 "/" 매핑에 포함된다면, /webroot/assets/가 됨
    }

    // --- Resolver & Interceptor 설정 ---

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        // 커스텀 아규먼트 리졸버 등록
        resolvers.add(new ParamResolver());
        resolvers.add(new HeaderResolver());
        resolvers.add(new SessionResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. Locale 변경 인터셉터 (국제화 지원)
        registry.addInterceptor(localeChangeInterceptor());

        // 2. 세션 인터셉터 (특정 경로 제외)
        // 정적 자원 제외는 DispatcherServlet의 addMapping 범위와 DefaultServletHandlerConfigurer에 의존
        registry.addInterceptor(getSessionInterceptor())
                .addPathPatterns("/**")
                // 정적 파일은 DefaultServletHandlerConfigurer에서 처리되지만, 안전을 위해 기본 경로 제외
                .excludePathPatterns("/assets/**", "/static/**", "/upload/**", "/favicon.ico");
    }
    
    @Bean
    public SessionInterceptor getSessionInterceptor() {
        return new SessionInterceptor();
    }


    // --- Message Converter 설정 (JSON/XML/String) ---
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        // 모든 JSON/XML 설정을 ObjectMapper Bean을 통해 중앙 집중화
        
        // 1. JSON Converter (HtmlEscapes 적용된 ObjectMapper 사용)
        converters.add(new MappingJackson2HttpMessageConverter(objectMapper()));

        // 2. XML Converter
        Jackson2ObjectMapperBuilder xmlBuilder = Jackson2ObjectMapperBuilder.xml();
        xmlBuilder.indentOutput(true).dateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        converters.add(new MappingJackson2XmlHttpMessageConverter(xmlBuilder.build()));
        
        // 3. String Converter (text/html, text/plain 인코딩 설정)
        List<MediaType> stringMediaTypes = new ArrayList<>();
        stringMediaTypes.add(MediaType.TEXT_HTML);
        stringMediaTypes.add(MediaType.TEXT_PLAIN);
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(Was.DEFAULT_CHARSET);
        stringConverter.setSupportedMediaTypes(stringMediaTypes);
        converters.add(stringConverter);
        
        // 4. Byte Array Converter (파일 다운로드/처리)
        converters.add(new ByteArrayHttpMessageConverter());
        
        // **중요:** extendMessageConverters() 로직은 configureMessageConverters()와 중복되므로 제거하거나 통합해야 합니다.
    }
    
    // --- Thymeleaf 설정 ---

    @Bean
    public ViewResolver getViewResolver() {
    	ThymeleafViewResolver resolver = new ThymeleafViewResolver();
    	resolver.setTemplateEngine((ISpringTemplateEngine)templateEngine());
        resolver.setCharacterEncoding(Was.DEFAULT_CHARSET.name());
        resolver.setOrder(0); // ViewResolver의 우선순위 설정
        
        return resolver;
    }

    @Bean
    public TemplateEngine templateEngine() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templateResolver());
        engine.addDialect(new LayoutDialect()); // Thymeleaf Layout Dialect 추가
        engine.setTemplateEngineMessageSource(messageSource()); // 국제화 MessageSource 연결
        return engine;
    }

    @Bean
    @Description("Thymeleaf Template Resolver")
    public SpringResourceTemplateResolver templateResolver() {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix("/"); // 웹 루트를 기준으로 템플릿 탐색 시작
        resolver.setSuffix(".html");
        resolver.setCacheTTLMs(60 * 1000L); // 1분 TTL
        resolver.setCacheable(false); // 개발 모드 설정
        resolver.setCharacterEncoding(Was.DEFAULT_CHARSET.name());
        resolver.setTemplateMode(TemplateMode.HTML);
        return resolver;
    }

    // --- 기타 유틸리티 설정 ---
    
    @Bean
    public MultipartResolver multipartResolver() {
        // Servlet 3.0+ 스펙 기반의 MultipartResolver 사용
        return new StandardServletMultipartResolver();
    }

    @Bean
    public ResourceBundleMessageSource messageSource() {
        // 국제화 MessageSource 설정
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding(Was.DEFAULT_CHARSET.name()); // 인코딩 설정 추가
        // messageSource.setUseCodeAsDefaultMessage(true); // 코드를 기본 메시지로 사용 (필요시)
        return messageSource;
    }

    @Bean
    public SessionLocaleResolver localeResolver() {
        // Locale 정보를 세션에 저장하여 사용
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(Locale.KOREAN); // 기본 Locale 설정 고려
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        // URL 파라미터를 통한 Locale 변경 지원 (기본 파라미터명: 'locale')
        return new LocaleChangeInterceptor();
    }
}
