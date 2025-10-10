package kr.tx24.inet.server;

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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import kr.tx24.inet.codec.INetDecoder;
import kr.tx24.inet.codec.INetEncoder;
import kr.tx24.inet.conf.INetConfigLoader;

public class INetServer extends Thread{

	private static final Logger logger = LoggerFactory.getLogger(INetServer.class);
	private static final int MAX_MESSAGE_SIZE = 100*1024*1024; //100 Mega
	
	// Write buffer water mark 설정 (백프레셔 제어)
	// 대용량 패킷이 증가하면 (5% 이상): ~ 1MB / 4MB로 변경
	// 연결 수가 급증하면 (500개 이상): ~ 256KB / 1MB로 축소
	// 응답 시간이 중요하면: 	→ 2MB / 8MB로 확대
	private static final int LOW_WATER_MARK = 512 * 1024;      // 512KB
    private static final int HIGH_WATER_MARK = 2 * 1024 * 1024; // 2MB
	
	private volatile  EventLoopGroup bossGroup 	= null;
	private volatile  EventLoopGroup workerGroup 	= null;
	
	// 중복 shutdown 방지
    private final AtomicBoolean isShutdown = new AtomicBoolean(false);
	
	
	
	public INetServer(){
		//Router.start("syslink.ext.v1");	
	}
	
	@Override
	public void run(){
		
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
		
		// Netty 4.2.x 최신 방식: MultiThreadIoEventLoopGroup with NioIoHandler
		bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
		workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory()); // CPU * 2 기본
		
		
		try{
			
			ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
            bootstrap.option(ChannelOption.SO_REUSEADDR, true);
            bootstrap.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
                    .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    
                    // Child options - 각 클라이언트 연결에 적용되는 옵션들
                    .childOption(ChannelOption.SO_KEEPALIVE, false)							//KeepAlive 설정
                    .childOption(ChannelOption.TCP_NODELAY, true)							//Nagle 알고리즘 비활성화
                    .childOption(ChannelOption.SO_LINGER, 0)								//소켓 닫기 시 대기 시간
                    .childOption(ChannelOption.SO_SNDBUF, MAX_MESSAGE_SIZE)					//송신 버퍼 크기 
                    .childOption(ChannelOption.SO_RCVBUF, MAX_MESSAGE_SIZE)					//수신 버퍼 크기
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)	//
                    .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(LOW_WATER_MARK, HIGH_WATER_MARK)) // Write buffer water mark - 백프레셔 제어
                    .childOption(ChannelOption.AUTO_READ, true)								//Auto read - 자동으로 읽기를 계속할지 여부
                    .childOption(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30000)				// Connection timeout
                    .childOption(ChannelOption.MESSAGE_SIZE_ESTIMATOR,io.netty.channel.DefaultMessageSizeEstimator.DEFAULT) // Message size estimator - 메시지 크기 추정 (메모리 관리)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel sc) throws Exception {
                            ChannelPipeline p = sc.pipeline();
                            if (INetConfigLoader.enableLoggingHandler()) {
                                p.addLast(new LoggingHandler(LogLevel.INFO)); //로그 확인 시
                            }
                            p.addLast("idleStateHandler", new IdleStateHandler(30, 30, 0));
                            p.addLast("inetDecoder", new INetDecoder());
                            p.addLast("chunkedWriter", new ChunkedWriteHandler());
                            p.addLast("inetEncoder", new INetEncoder());
                            //p.addLast("handler", new Handler());
                        }
                    });	
					
					
            logger.info("... server started ... : [{}:{}]", INetConfigLoader.getHost(), INetConfigLoader.getPort());
            logger.info("Boss threads: 1, Worker threads: {}", Runtime.getRuntime().availableProcessors() * 2);
            
            ChannelFuture future = bootstrap.bind( INetConfigLoader.getHost(), INetConfigLoader.getPort()).sync();

            future.addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                	 logger.info("... channel binded ...");
                } else {
                    logger.error("Failed to bind server", channelFuture.cause());
                }
            });

            logger.info("Server started successfully");
            
            future.channel().closeFuture().sync();
		}catch (Exception e){
			logger.error("server exception : {}",e.getMessage());
		}finally {
			shutdown();
		}
	}
	
	
	
	public void shutdown() {
		if (!isShutdown.compareAndSet(false, true)) {
            logger.debug("Shutdown already in progress or completed");
            return;
        }
        try {
            if (bossGroup != null) {
            	bossGroup.shutdownGracefully().sync();
            }
            if (workerGroup != null) {
            	workerGroup.shutdownGracefully().sync();
            }
            logger.info("... server stop! ...");
        } catch (InterruptedException  ex) {
            logger.info("shutdown exception : {}", ex.getMessage(), ex);
        	Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.info("Error during shutdown", e);
        }
    }
	
	
	public static void main(String[] args) {
		INetConfigLoader.start();
		new INetServer().start();
	}
}
