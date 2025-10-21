package kr.tx24.api.server;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpServerExpectContinueHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import kr.tx24.api.conf.ApiConfigLoader;
import kr.tx24.api.handler.CompressorHandler;
import kr.tx24.api.util.ApiUtils;
import kr.tx24.inet.route.Router;

/**
 * API 수신 서버
 * 
 * Netty 기반 HTTP API 서버로 RESTful API 요청을 처리합니다.
 * - 비동기 이벤트 기반 아키텍처
 * - HTTP 프로토콜 최적화
 * - Connection pooling 및 buffer 최적화
 * - Graceful shutdown 지원
 */
public class ApiServer extends Thread {
    
    private static final Logger logger = LoggerFactory.getLogger(ApiServer.class);
    
    // Buffer size 설정 - HTTP API 특성에 맞춘 중간 크기
    private static final int MAX_MESSAGE_SIZE = 10 * 1024 * 1024;           	// 10MB - 송수신 버퍼
    private static final int HTTP_OBJECT_AGGREGATOR_MAX_SIZE = 8 * 1024 * 1024; // 8MB - HTTP 메시지 최대 크기
    
    // Write buffer water mark 설정 (백프레셔 제어)
    // HTTP API 특성상 중간 수준으로 설정
    // - 많은 동시 연결 처리 (100~1000개)
    // - 빠른 응답 시간 필요
    // - 중간 크기의 JSON/XML payload
    private static final int LOW_WATER_MARK = 512 * 1024;      // 512KB - 쓰기 재개 임계값
    private static final int HIGH_WATER_MARK = 2 * 1024 * 1024; // 2MB - 쓰기 중단 임계값
    
    private volatile EventLoopGroup bossGroup = null;
    private volatile EventLoopGroup workerGroup = null;
    
    // 중복 shutdown 방지
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
    
    
    public ApiServer() {
        ApiConfigLoader.start();
        Router.start(ApiConfigLoader.get().basePackage);
    }
    
    
    @Override
    public void run() {
        
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
        
        // Netty 4.2.x 최신 방식: MultiThreadIoEventLoopGroup with NioIoHandler
        bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()); // CPU * 2 기본
        
        try {
            
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
            bootstrap.option(ChannelOption.SO_REUSEADDR, true);
            bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    
                    // Child options - 각 클라이언트 연결에 적용되는 옵션들
                    .childOption(ChannelOption.SO_KEEPALIVE, false)                             // KeepAlive 설정 - HTTP는 짧은 연결이 많으므로 false
                    .childOption(ChannelOption.TCP_NODELAY, true)                               // Nagle 알고리즘 비활성화 - 지연 최소화
                    .childOption(ChannelOption.SO_LINGER, 0)                                    // 소켓 닫기 시 대기 시간 - 즉시 닫기
                    .childOption(ChannelOption.SO_SNDBUF, MAX_MESSAGE_SIZE)                     // 송신 버퍼 크기
                    .childOption(ChannelOption.SO_RCVBUF, MAX_MESSAGE_SIZE)                     // 수신 버퍼 크기
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)      // ByteBuf 풀링 사용으로 GC 부하 감소
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, 
                        new WriteBufferWaterMark(LOW_WATER_MARK, HIGH_WATER_MARK))              // 백프레셔 제어
                    .childOption(ChannelOption.AUTO_READ, true)                                 // 자동 읽기 활성화
                    .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)                   // Connection timeout - 30초
                    .childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR, 
                        io.netty.channel.DefaultMessageSizeEstimator.DEFAULT)                   // 메시지 크기 추정 (메모리 관리)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel sc) throws Exception {
                            ChannelPipeline p = sc.pipeline();
                            
                            // Logging handler (옵션)
                            if (ApiConfigLoader.get().logging) {
                                p.addLast(new LoggingHandler(LogLevel.INFO));
                            }
                            
                            // Idle state handler - 유휴 연결 감지
                            // HTTP API 특성: 읽기 타임아웃을 쓰기보다 짧게 설정
                            // - 읽기: 30초 (클라이언트 요청 대기)
                            // - 쓰기: 60초 (서버 응답 대기)
                            // - 전체: 0 (사용 안 함)
                            p.addLast("idleStateHandler", new IdleStateHandler(30, 60, 0));
                            
                            // HTTP codec - HTTP 요청/응답 인코딩/디코딩
                            p.addLast("httpCodec", new HttpServerCodec());
                            
                            // HTTP message aggregator - 청크된 메시지를 하나로 합침
                            // API 서버는 전체 요청 본문이 필요하므로 필수
                            p.addLast("httpAggregator", new HttpObjectAggregator(HTTP_OBJECT_AGGREGATOR_MAX_SIZE));
                            
                            // Chunked write handler - 대용량 응답 전송 시 사용
                            // 큰 JSON 응답이나 파일 다운로드 시 메모리 효율적
                            p.addLast("chunkedWriter", new ChunkedWriteHandler());
                            
                            // 100-continue 처리
                            // 클라이언트가 큰 본문을 보내기 전에 서버 확인
                            p.addLast("expectContinue", new HttpServerExpectContinueHandler());
                            
                            //Handler 등록 
                            ApiUtils.addHandlers(p, ApiConfigLoader.get().handlers);
                            
                            
                            //2KB 이상만 압축 사용
                            p.addLast("compressor", new CompressorHandler(2*1024));
                           
                        }
                    });
            
            logger.info("Server starting ... : [{}:{}]", 
                ApiConfigLoader.get().host, ApiConfigLoader.get().port);
            logger.info("Boss threads: 1, Worker threads: {}", 
                Runtime.getRuntime().availableProcessors() * 2);
            
            ChannelFuture future = bootstrap.bind(ApiConfigLoader.get().host, ApiConfigLoader.get().port).sync();
            
            future.addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    logger.info("Channel binded ...");
                } else {
                    logger.error("Failed to bind server", channelFuture.cause());
                }
            });
            
            logger.info("Server started successfully");
            
            future.channel().closeFuture().sync();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Server thread interrupted", e);
        } catch (Exception e) {
            logger.error("Server exception: {}", e.getMessage(), e);
        } finally {
            shutdown();
        }
    }
    
    
    /**
     * 서버 종료 처리
     * - Graceful shutdown으로 진행 중인 요청 완료 대기
     * - 중복 호출 방지
     */
    public void shutdown() {
        if (!isShutdown.compareAndSet(false, true)) {
            logger.debug("Shutdown already in progress or completed");
            return;
        }
        
        logger.info("Shutting down Server...");
        
        try {
            if (bossGroup != null) {
                bossGroup.shutdownGracefully().sync();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully().sync();
            }
            
            logger.info("... Server stopped! ...");
            
        } catch (InterruptedException e) {
            logger.warn("Shutdown exception: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }
    
    
    public static void main(String[] args) {
        new ApiServer().start();
    }
}