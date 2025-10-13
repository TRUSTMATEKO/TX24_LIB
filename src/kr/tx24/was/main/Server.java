package kr.tx24.was.main;


import java.io.File;
import java.net.InetAddress;
import java.util.Locale;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.startup.Tomcat.FixContextListener;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.EmptyResourceSet;
import org.apache.catalina.webresources.StandardRoot;
import org.apache.coyote.http11.Http11Nio2Protocol;
import org.apache.coyote.http2.Http2Protocol;
import org.apache.tomcat.util.descriptor.web.ErrorPage;
import org.apache.tomcat.util.http.Rfc6265CookieProcessor;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.lb.LoadBalancer;
import kr.tx24.was.conf.TomcatConfig;
import kr.tx24.was.conf.TomcatConfigLoader;
import kr.tx24.was.util.Was;




/**
 * @author juseop
 *
 */
public class Server{

	private static Logger logger 				= LoggerFactory.getLogger(Server.class );
	
	private static final String COMPRESSABLE	= "text/html,text/xml,text/plain,application/javascript,application/json,text/css,text/csv,application/x-javascript,application/vnd.api+json,image/svg+xml";


	
	public void start()throws Exception {
		
		TomcatConfig config = TomcatConfigLoader.load();
		
		
		// 기존 JUL 핸들러를 제거하여 JUL의 콘솔/파일 출력 중복을 방지합니다.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        // JUL 로깅 요청을 SLF4J로 전달하도록 설치합니다.
        SLF4JBridgeHandler.install();
        // 이 시점 이후부터 Tomcat이나 다른 라이브러리에서 발생하는 JUL 로그는
        // SLF4J (Logback) 설정을 통해 제어됩니다.
        //logger.debug("Java Util Logging (JUL) 비릿징으로 SLF4J 사용");
        
		
		
		String docBase = new File(config.contextPath).getCanonicalPath();
		if(SystemUtils.deepview()) {
			logger.info("contextpath : {}",docBase);
			logger.info("host,port   : {},{}",config.host,config.port);
		}
		
		Tomcat tomcat = new Tomcat();
		tomcat.setBaseDir(docBase);
		tomcat.setHostname(config.host);
		tomcat.getService().setName(config.serverName);
		
		

		
		// 설정시 아래 URL 참고 https://bcho.tistory.com/788 (참고용)
		
		Connector connector = new Connector(Http11Nio2Protocol.class.getName());
		/*
		if (SystemUtils.getJavaMajorVersion() >= 21) {
		    connector = new Connector("org.apache.coyote.http11.Http11VirtualThreadProtocol");
		} else {
		    connector = new Connector(Http11Nio2Protocol.class.getName());
		}*/
		
		
		
		Http2Protocol http2Protocol = new Http2Protocol();
		http2Protocol.setMaxConcurrentStreams(128);  // 동시 스트림 수
		http2Protocol.setMaxConcurrentStreamExecution(20);  // 동시 실행 수
		http2Protocol.setInitialWindowSize(65535);  // 초기 윈도우 크기
		connector.addUpgradeProtocol(http2Protocol);
		
		
		connector.setProperty("server", config.host);
		connector.setPort(config.port);
		connector.setSecure(false);
		connector.setScheme("http");
		connector.setMaxPostSize(config.maxPostSize);
		connector.setURIEncoding(Was.DEFAULT_CHARSET.name());
		connector.setXpoweredBy(false);
		connector.addUpgradeProtocol(http2Protocol);
		connector.setEnableLookups(false);
		connector.setAllowTrace(false);
		
		connector.setThrowOnFailure(true);
		// Don't bind to the socket prematurely if ApplicationContext is slow to start
		connector.setProperty("bindOnInit", "false");
	
		
		//JDK 17 기준 으로 작성됨.  JDK21 사용시는 Http11VirtualThreadProtocol 를 고려해야함.
		Http11Nio2Protocol protocol = (Http11Nio2Protocol)connector.getProtocolHandler();
		protocol.setAddress(InetAddress.getByName(config.host));
		protocol.setAcceptCount(config.acceptCount);
		protocol.setMaxThreads(config. maxThreads);
		protocol.setMinSpareThreads(config.minSpareThreads);
		protocol.setMaxConnections(config.maxConnection);
		protocol.setTcpNoDelay(config.tcpNoDelay);
		protocol.setConnectionTimeout(config.connectionTimeout);		
		protocol.setDisableUploadTimeout(false);
		protocol.setMaxHttpHeaderSize(1024*4);
		protocol.setCompression("on");
		protocol.setCompressibleMimeType(COMPRESSABLE);
		protocol.setCompressionMinSize(2048);
		protocol.setNoCompressionUserAgents("gozilla, traviata");
		protocol.setMaxHeaderCount(100);
		protocol.setMaxHttpHeaderSize(1024*4);
		protocol.setConnectionUploadTimeout(300000); 
	

		if(config.isRESTful) {						//REST API 여부에 따른 Keep Alive 조정
			protocol.setMaxKeepAliveRequests(1);	//REST 는 1로 그외 웹은 100정도
		}else {
			protocol.setMaxKeepAliveRequests(128);	//REST 는 1로 그외 웹은 100정도
		}
		
		/*
		// JDK 21 버전에 따라 Virtual Thread Executor 적용
        if (SystemUtils.getJavaMajorVersion() >= 21 ) {
            Executor virtualExecutor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().factory());
            protocol.setExecutor(virtualExecutor);
        }
		*/
		
		
		tomcat.getService().addConnector(connector);
		tomcat.setConnector(connector);
		tomcat.getHost().setAutoDeploy(true);
		
		StandardContext ctx = (StandardContext) tomcat.addWebapp("", docBase+File.separator+"/webroot");
		ctx.setReloadable(config.reloadable);		//개발시는 TRUE 하여 사용해도 됨.
		ctx.setDisplayName(config.serverName);
		ctx.setWorkDir(docBase+File.separator+"/classes/work");
		ctx.setSessionCookieName(null);   // JSESSIONID 쿠키 생성 방지
		ctx.setSessionTimeout(-1);        // 명시적으로 세션 무효화
		ctx.setManager(null);             // 세션 매니저 제거
		
	
		
		ctx.setCookieProcessor(new Rfc6265CookieProcessor());
		ctx.addLifecycleListener(new FixContextListener());
		// Tomcat 10.1 이상에서는 JreMemoryLeakPreventionListener와 ThreadLocalLeakPreventionListener가 제거되거나 필요하지 않습니다.
        // ctx.addLifecycleListener(new JreMemoryLeakPreventionListener()); 
        // ctx.addLifecycleListener(new ThreadLocalLeakPreventionListener());
		//ctx.addLifecycleListener(new LifeListner());
		
		
		ctx.setParentClassLoader(this.getClass().getClassLoader());
		ctx.addLocaleEncodingMappingParameter(Locale.KOREAN.toString(), Was.DEFAULT_CHARSET.displayName());
		ctx.addLocaleEncodingMappingParameter(Locale.ENGLISH.toString(), Was.DEFAULT_CHARSET.displayName());
		
	
		if (ctx.getJarScanner() instanceof StandardJarScanner) {
			StandardJarScanner jarScanner = (StandardJarScanner) ctx.getJarScanner();
			jarScanner.setScanClassPath(false);
			jarScanner.setScanManifest(false);
		//ctx.setJarScanner(jarScanner);
		}
		 
		
		
		File additionWebInfClassesFolder = new File(docBase+File.separator+"/classes");
		WebResourceRoot resources = new StandardRoot(ctx);
		WebResourceSet resourceSet;
		if (additionWebInfClassesFolder.exists()) {
			resourceSet = new DirResourceSet(resources, "/WEB-INF/classes", additionWebInfClassesFolder.getAbsolutePath(), "/");
		}else{
			resourceSet = new EmptyResourceSet(resources);
		}
		resources.addPreResources(resourceSet);
		ctx.setResources(resources);
		
		// 에러 페이지 경로 설정
		ErrorPage errorPage = new ErrorPage();
		errorPage.setLocation("/error");
		ctx.addErrorPage(errorPage);
		
		// 예외 타입별 에러 페이지 설정 (java.lang.Throwable은 java.lang.Exception을 포함하므로 보통 하나만 충분)
		ErrorPage throwable = new ErrorPage();
		throwable.setExceptionType("java.lang.Throwable");
		throwable.setLocation("/error");
		ctx.addErrorPage(throwable);
		

		ctx.addWelcomeFile("index.html");
		ctx.addWelcomeFile("/init");

		
		
		
		
		
		tomcat.start();
		logger.info("Tomcat started on {}:{}", config.host, config.port);
		tomcat.getServer().await();
		
		/* 현재 시스템이 살아있는지 모니터링 , JvmStatus 로 확인이 가능해서 아래는 필요없다.
		ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler.scheduleAtFixedRate(() -> {
		    if (SystemUtils.deepview()) {
		        logger.info("running.....");
		    }
		}, 0, 60, TimeUnit.SECONDS); // 60초마다 실행
		
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
		    scheduler.shutdown();
		}));*/
		
	}
	
	
	
	

	
	public static void main(String[] args) {
		//기본 로깅에 대해서 console, file, remote 에 대한 설정
        // 이 부분은 Logback 설정을 통해 이루어져야 합니다.

		try{
			// IPv4를 사용하도록 강제
	        System.setProperty("java.net.preferIPv4Stack", "true");
	        // JVM 모니터링 
	        System.setProperty("JVM_MONITOR", "true");
	    
			LoadBalancer.start(10);
			new Server().start();
			
		}catch(Exception e){
			logger.error("Server startup failed", e); // 스택 트레이스를 로거로 출력
			System.exit(1); // 비정상 종료
		}
	}
}