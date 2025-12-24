package kr.tx24.was.main;

import java.io.File;
import java.net.InetAddress;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.catalina.LifecycleException;
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
import org.springframework.web.SpringServletContainerInitializer;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.http.ua.UADetect;
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.lib.lang.NetUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.was.conf.TomcatConfig;
import kr.tx24.was.conf.TomcatConfigLoader;
import kr.tx24.was.core.ReloadWatcher;
import kr.tx24.was.core.ServletConfiguration;
import kr.tx24.was.util.Was;

/**
 * Embedded Tomcat Server with Graceful Shutdown
 * 
 * @author juseop
 */
public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);
    
    private static volatile Tomcat tomcat;
    private static volatile ReloadWatcher reloadWatcher;
    
    
    private static final AtomicBoolean isShuttingDown 	= new AtomicBoolean(false);
    private static final AtomicBoolean isInitialized 	= new AtomicBoolean(false);
    
    
    public Server() {
    }
    
    
    //Server 시작 
    public void start() throws Exception {
        
    	if (isInitialized.get()) {
			logger.debug("Server is already initialized or starting");
			return;
		}
    	
    	
        TomcatConfig config = TomcatConfigLoader.load();
        
        if(NetUtils.isAlive(config.host, config.port)) {
        	isInitialized.set(false);
            System.err.println(MsgUtils.format("{},{} already bounded", config.host, config.port));
            System.err.println("Please stop the already running process.");
            System.exit(1);
        }
        
        
        // JUL to SLF4J 브릿지 설정
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        
        String docBase = new File(config.contextPath).getCanonicalPath();
        if(SystemUtils.deepview()) {
            logger.info("contextpath : {}", docBase);
            logger.info("host,port   : {},{}", config.host, config.port);
        }
        
        // Tomcat 인스턴스 생성 및 설정
        tomcat = new Tomcat();
        tomcat.setBaseDir(docBase);
        tomcat.setHostname(config.host);
        tomcat.getService().setName(config.serverName);
        
        // Connector 설정
        Connector connector = new Connector(Http11Nio2Protocol.class.getName());
        
        // HTTP/2 설정
        Http2Protocol http2Protocol = new Http2Protocol();
        http2Protocol.setMaxConcurrentStreams(128);
        http2Protocol.setMaxConcurrentStreamExecution(20);
        http2Protocol.setInitialWindowSize(65535);
        
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
        connector.setProperty("bindOnInit", "false");
        
        // Protocol 설정
        Http11Nio2Protocol protocol = (Http11Nio2Protocol)connector.getProtocolHandler();
        
        int cores = Runtime.getRuntime().availableProcessors();
        //최대 스레드 = max(config.maxThreads, 코어 * 4)
        int calculatedMaxThreads = Math.max(config.maxThreads, cores * 4);
        // 최소 스페어 스레드 = max(config.minSpareThreads, 코어 * 2)
        int calculatedMinSpareThreads = Math.max(config.minSpareThreads, cores * 2);
        
        if(SystemUtils.deepview()) {
        	logger.info("Tomcat Thread Pool : Cores={}, MaxThreads={}, MinSpareThreads={}",cores, calculatedMaxThreads, calculatedMinSpareThreads);
        }
        
        protocol.setAddress(InetAddress.getByName(config.host));
        protocol.setAcceptCount(config.acceptCount);
        protocol.setMaxThreads(calculatedMaxThreads);
        protocol.setMinSpareThreads(calculatedMinSpareThreads);
        protocol.setMaxConnections(config.maxConnection);
        protocol.setTcpNoDelay(config.tcpNoDelay);
        protocol.setConnectionTimeout(config.connectionTimeout);
        protocol.setDisableUploadTimeout(false);
        protocol.setMaxHttpHeaderSize(1024*4);
        protocol.setCompression("on");
        protocol.setCompressibleMimeType(Was.COMPRESSABLE);
        protocol.setCompressionMinSize(2048);
        protocol.setNoCompressionUserAgents("gozilla, traviata");
        protocol.setMaxHeaderCount(100);
        protocol.setConnectionUploadTimeout(300000);
        
        if(config.isRESTful) {
            protocol.setMaxKeepAliveRequests(1);
        } else {
            protocol.setMaxKeepAliveRequests(128);
        }
        
        tomcat.getService().addConnector(connector);
        tomcat.setConnector(connector);
        tomcat.getHost().setAutoDeploy(true);
        
        // Context 설정
        StandardContext ctx = (StandardContext) tomcat.addWebapp("", docBase + File.separator + "/webroot");
        ctx.setReloadable(config.reloadable);
        ctx.setDisplayName(config.serverName);
        ctx.setName(config.serverName);
        ctx.setWorkDir(docBase + File.separator + "/classes/work");
        ctx.setSessionCookieName(null);
        ctx.setSessionTimeout(-1);
        ctx.setManager(null);
        
        // Spring Initializer 등록
        ctx.addServletContainerInitializer(
            new SpringServletContainerInitializer(),
            Set.of(ServletConfiguration.class)
        );
        
        ctx.setCookieProcessor(new Rfc6265CookieProcessor());
        ctx.addLifecycleListener(new FixContextListener());
        
        ctx.setParentClassLoader(this.getClass().getClassLoader());
        ctx.addLocaleEncodingMappingParameter(Locale.KOREAN.toString(), Was.DEFAULT_CHARSET.displayName());
        ctx.addLocaleEncodingMappingParameter(Locale.ENGLISH.toString(), Was.DEFAULT_CHARSET.displayName());
        
        // JAR Scanner 설정
        if (ctx.getJarScanner() instanceof StandardJarScanner) {
            StandardJarScanner jarScanner = (StandardJarScanner) ctx.getJarScanner();
            jarScanner.setScanClassPath(false);
            jarScanner.setScanManifest(false);
        }
        
        // Web Resources 설정
        File additionWebInfClassesFolder = new File(docBase + File.separator + "/classes");
        WebResourceRoot resources = new StandardRoot(ctx);
        
        if(!config.templateCacheable) {
            logger.info("resource caching disabled");
            resources.setCachingAllowed(false);
            resources.setCacheTtl(0);
        }
        
        WebResourceSet resourceSet;
        if (additionWebInfClassesFolder.exists()) {
            resourceSet = new DirResourceSet(resources, "/WEB-INF/classes",additionWebInfClassesFolder.getAbsolutePath(), "/");
            resources.addPreResources(resourceSet);
            
            // Class reload 설정
            if(config.reloadable) {
                try {
                    reloadWatcher = new ReloadWatcher(ctx, additionWebInfClassesFolder);
                    reloadWatcher.start();
                } catch (Exception e) {
                    logger.error("ReloadWatcher failed : {}", e);
                }
            }
        } else {
            resourceSet = new EmptyResourceSet(resources);
        }
        
        ctx.setResources(resources);
        
        // Error page 설정
        ErrorPage errorPage = new ErrorPage();
        errorPage.setLocation("/error");
        ctx.addErrorPage(errorPage);
        
        ErrorPage throwable = new ErrorPage();
        throwable.setExceptionType("java.lang.Throwable");
        throwable.setLocation("/error");
        ctx.addErrorPage(throwable);
        
        ctx.addWelcomeFile("index.html");
        ctx.addWelcomeFile("/init");
        
        
        try {
        
	        // Tomcat 시작
	        tomcat.start();
	        isInitialized.set(true);
	        logger.info("Tomcat started on {}:{}", config.host, config.port);
	        
	        
	        // UADetect 초기화
	        AsyncExecutor.execute(() -> {
	            UADetect.initialize();
	        });
	        
	        // Tomcat await (blocking)
	        tomcat.getServer().await();
        }catch(Exception e) {
        	logger.info("Server startup failed", e);
        	cleanupResources();
            isInitialized.set(false);
        }finally {
        	shutdown();
        }
    }
    
    
    
    /**
     * Graceful Shutdown
     * - 새로운 요청 차단
     * - 진행 중인 요청 완료 대기
     * - 리소스 정리
     */
    public static void shutdown() {
    	if (!isInitialized.get()) {
            return;
        }
    	
        if (!isShuttingDown.compareAndSet(false, true)) {
            logger.debug("Server shutdown already in progress or completed");
            return;
        }

        logger.info("Starting graceful server shutdown...");
        
        try {
            // 1. ReloadWatcher 종료
            if (reloadWatcher != null) {
                try {
                    reloadWatcher.stop();
                } catch (Exception e) {
                    //logger.warn("Error stopping ReloadWatcher: {}", e.getMessage());
                }
            }
            
            // 2. Tomcat 종료
            if (tomcat != null) {
                try {
                    logger.info("Stopping Tomcat server...");
                    
                    // 새로운 연결 차단
                    tomcat.getConnector().pause();
                    
                    // 진행 중인 요청 완료 대기 (최대 10초)
                    int waitTime = 10;
                    logger.debug("Waiting up to {} seconds for active requests to complete...", waitTime);
                    
                    Thread shutdownThread = new Thread(() -> {
                        try {
                            tomcat.stop();
                            tomcat.destroy();
                        } catch (LifecycleException e) {
                           // logger.error("Error during Tomcat shutdown: {}", e.getMessage());
                        }
                    });
                    
                    shutdownThread.start();
                    shutdownThread.join(TimeUnit.SECONDS.toMillis(waitTime));
                    
                    if (shutdownThread.isAlive()) {
                        logger.debug("Forcing Tomcat shutdown after {} seconds", waitTime);
                        shutdownThread.interrupt();
                    }
                    
                    logger.info("Tomcat shutdown completed");
                    
                } catch (Exception e) {
                    logger.error("Error during Tomcat shutdown: {}", e.getMessage(), e);
                }
            }
            
            
            cleanupResources();
            
            
            isInitialized.set(false);
            logger.info("Server shutdown completed successfully");
            
        } catch (Exception e) {
            logger.error("Unexpected error during server shutdown", e);
        }
    }
    
    /**
     * 필요한 추가 리소스 정리
     */
    private static void cleanupResources() {
        try {
        	UADetect.shutdown();
        } catch (Exception e) {
            logger.warn("Error during resource cleanup: {}", e.getMessage());
        }
    }
     
    /**
     * 강제 종료 (긴급시 사용)
     */
    public void forceShutdown() {
        logger.warn("Force shutdown initiated");
        
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception e) {
                logger.error("Error during force shutdown: {}", e.getMessage());
            }
        }
        
        System.exit(1);
    }
    

    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            // IPv4 사용 강제
            System.setProperty("java.net.preferIPv4Stack", "true");
            new Server().start();
            
        } catch(Exception e) {
            logger.error("Server startup failed", e);
            System.exit(1);
        }
    }
}