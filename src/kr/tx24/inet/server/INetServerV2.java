package kr.tx24.inet.server;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import kr.tx24.inet.codec.INetDecoder;
import kr.tx24.inet.codec.INetEncoder;
import kr.tx24.inet.conf.INetConfigLoader;
import kr.tx24.inet.handler.INetHandlerV2;
import kr.tx24.inet.route.Router;
import kr.tx24.lib.lang.MsgUtils;
import kr.tx24.lib.lang.NetUtils;

/**
 * Bounded business executor와 overload backpressure를 적용한 INet server.
 *
 * <p>기존 {@link INetServer}와 독립적으로 사용할 수 있으며 기존 구현은 변경하지 않는다.</p>
 */
public class INetServerV2 {

    private static final Logger logger = LoggerFactory.getLogger(INetServerV2.class);

    private static final int LOW_WATER_MARK = 1 * 1024 * 1024;
    private static final int HIGH_WATER_MARK = 4 * 1024 * 1024;
    private static final int TCP_RCV_BUFFER_SIZE = 512 * 1024;
    private static final int TCP_SND_BUFFER_SIZE = 64 * 1024;

    private static final int BUSINESS_THREADS = 24;
    private static final int BUSINESS_QUEUE_CAPACITY = 200;
    private static final int BUSINESS_QUEUE_WARN_PERCENT = 80;
    private static final long BUSINESS_MONITOR_INTERVAL_SECONDS = 10L;
    private static final long BUSINESS_SLOW_REQUEST_THRESHOLD_MILLIS = 3_000L;
    private static final long BUSINESS_SHUTDOWN_TIMEOUT_SECONDS = 10L;

    private static volatile EventLoopGroup bossGroup;
    private static volatile EventLoopGroup workerGroup;
    private static volatile ThreadPoolExecutor businessExecutor;
    private static volatile ScheduledFuture<?> businessMonitorFuture;

    private static final AtomicBoolean isShutdown = new AtomicBoolean(false);
    private static final AtomicBoolean isInitialized = new AtomicBoolean(false);

    public INetServerV2() {
        INetConfigLoader.start();
        Router.start(INetConfigLoader.getBasePackage());
    }

    public void start() {
        if (!isInitialized.compareAndSet(false, true)) {
            logger.debug("INetServerV2 is already initialized or starting");
            return;
        }

        isShutdown.set(false);
        run();
    }

    private void run() {
        if (NetUtils.isAlive(
                INetConfigLoader.getHost(),
                INetConfigLoader.getPort())) {

            System.err.println(MsgUtils.format(
                    "{},{} already bounded",
                    INetConfigLoader.getHost(),
                    INetConfigLoader.getPort()
            ));
            System.err.println("Please stop the already running process.");
            System.exit(1);
        }

        bossGroup = new MultiThreadIoEventLoopGroup(
                1,
                NioIoHandler.newFactory()
        );
        workerGroup = new MultiThreadIoEventLoopGroup(
                NioIoHandler.newFactory()
        );
        businessExecutor = createBusinessExecutor();
        startBusinessMonitoring();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.option(ChannelOption.SO_BACKLOG, 1024);
            bootstrap.option(ChannelOption.SO_REUSEADDR, true);
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(
                            ChannelOption.ALLOCATOR,
                            PooledByteBufAllocator.DEFAULT
                    )
                    .childOption(ChannelOption.SO_KEEPALIVE, false)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(
                            ChannelOption.SO_SNDBUF,
                            TCP_SND_BUFFER_SIZE
                    )
                    .childOption(
                            ChannelOption.SO_RCVBUF,
                            TCP_RCV_BUFFER_SIZE
                    )
                    .childOption(
                            ChannelOption.ALLOCATOR,
                            PooledByteBufAllocator.DEFAULT
                    )
                    .childOption(
                            ChannelOption.WRITE_BUFFER_WATER_MARK,
                            new WriteBufferWaterMark(
                                    LOW_WATER_MARK,
                                    HIGH_WATER_MARK
                            )
                    )
                    .childOption(ChannelOption.AUTO_READ, true)
                    .childOption(
                            ChannelOption.CONNECT_TIMEOUT_MILLIS,
                            3000
                    )
                    .childOption(
                            ChannelOption.MESSAGE_SIZE_ESTIMATOR,
                            io.netty.channel.DefaultMessageSizeEstimator.DEFAULT
                    )
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        @SuppressWarnings("deprecation")
                        protected void initChannel(SocketChannel sc) {
                            ChannelPipeline pipeline = sc.pipeline();
                            if (INetConfigLoader.enableLoggingHandler()) {
                                pipeline.addLast(
                                        new LoggingHandler(LogLevel.INFO)
                                );
                            }
                            pipeline.addLast(
                                    "idleStateHandler",
                                    new IdleStateHandler(0, 0, 300)
                            );
                            pipeline.addLast(
                                    "inetDecoder",
                                    new INetDecoder()
                            );
                            pipeline.addLast(
                                    "inetEncoder",
                                    new INetEncoder()
                            );
                            /*
                             * Handler는 Netty I/O thread에서 실행하고 실제 업무만
                             * bounded business executor로 전달한다.
                             */
                            pipeline.addLast(
                                    "handlerV2",
                                    new INetHandlerV2(
                                            businessExecutor,
                                            BUSINESS_SLOW_REQUEST_THRESHOLD_MILLIS
                                    )
                            );
                        }
                    });

            ChannelFuture future = bootstrap.bind(
                    INetConfigLoader.getHost(),
                    INetConfigLoader.getPort()
            ).sync();

            future.addListener((ChannelFutureListener) channelFuture -> {
                if (channelFuture.isSuccess()) {
                    logThreadInfo();
                    logger.info(
                            "INetServerV2 started: [{}:{}]",
                            INetConfigLoader.getHost(),
                            INetConfigLoader.getPort()
                    );
                } else {
                    logger.error(
                            "Failed to bind INetServerV2",
                            channelFuture.cause()
                    );
                }
            });

            future.channel().closeFuture().sync();
        } catch (Exception e) {
            logger.error("INetServerV2 execution failed", e);
        } finally {
            shutdown();
        }
    }

    private static ThreadPoolExecutor createBusinessExecutor() {
        return new ThreadPoolExecutor(
                BUSINESS_THREADS,
                BUSINESS_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(BUSINESS_QUEUE_CAPACITY),
                new BusinessThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private static void startBusinessMonitoring() {
        businessMonitorFuture = workerGroup.next().scheduleAtFixedRate(
                INetServerV2::monitorBusinessExecutor,
                BUSINESS_MONITOR_INTERVAL_SECONDS,
                BUSINESS_MONITOR_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private static void monitorBusinessExecutor() {
        ThreadPoolExecutor executor = businessExecutor;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        int active = executor.getActiveCount();
        int pending = executor.getQueue().size();
        int usagePercent = pending * 100 / BUSINESS_QUEUE_CAPACITY;

        if (usagePercent >= BUSINESS_QUEUE_WARN_PERCENT) {
            logger.warn(
                    "INet business queue high: active={}/{}, pending={}/{}, "
                            + "usage={}%, rejected={}",
                    active,
                    BUSINESS_THREADS,
                    pending,
                    BUSINESS_QUEUE_CAPACITY,
                    usagePercent,
                    INetHandlerV2.getRejectedTaskCount()
            );
        } else if (logger.isDebugEnabled()) {
            logger.debug(
                    "INet business executor: active={}/{}, pending={}/{}, "
                            + "completed={}, rejected={}",
                    active,
                    BUSINESS_THREADS,
                    pending,
                    BUSINESS_QUEUE_CAPACITY,
                    executor.getCompletedTaskCount(),
                    INetHandlerV2.getRejectedTaskCount()
            );
        }
    }

    private static void logThreadInfo() {
        try {
            int bossThreads = bossGroup instanceof MultithreadEventLoopGroup
                    ? ((MultithreadEventLoopGroup) bossGroup).executorCount()
                    : 1;
            int workerThreads = workerGroup instanceof MultithreadEventLoopGroup
                    ? ((MultithreadEventLoopGroup) workerGroup).executorCount()
                    : 1;

            logger.info(
                    "Boss threads: {}, Worker threads: {}, "
                            + "Business threads: {}, Business queue: {}",
                    bossThreads,
                    workerThreads,
                    BUSINESS_THREADS,
                    BUSINESS_QUEUE_CAPACITY
            );
        } catch (Exception e) {
            logger.debug(
                    "Failed to read INetServerV2 thread information",
                    e
            );
        }
    }

    public static void shutdown() {
        if (!isInitialized.get()) {
            return;
        }
        if (!isShutdown.compareAndSet(false, true)) {
            logger.debug(
                    "INetServerV2 shutdown already in progress or completed"
            );
            return;
        }

        boolean interrupted = false;
        cancelBusinessMonitoring();
        interrupted |= shutdownEventLoopGroup(bossGroup, 3L, "bossGroup");
        interrupted |= shutdownBusinessExecutor();
        interrupted |= shutdownEventLoopGroup(workerGroup, 3L, "workerGroup");

        bossGroup = null;
        workerGroup = null;
        businessExecutor = null;
        businessMonitorFuture = null;
        isInitialized.set(false);

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        logger.info("INetServerV2 stopped successfully");
    }

    private static void cancelBusinessMonitoring() {
        ScheduledFuture<?> monitorFuture = businessMonitorFuture;
        if (monitorFuture != null) {
            monitorFuture.cancel(false);
        }
    }

    private static boolean shutdownBusinessExecutor() {
        ThreadPoolExecutor executor = businessExecutor;
        if (executor == null) {
            return false;
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(
                    BUSINESS_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS)) {

                int cancelledTasks = executor.shutdownNow().size();
                logger.warn(
                        "Business executor forced shutdown: cancelledTasks={}",
                        cancelledTasks
                );
            }
            return false;
        } catch (InterruptedException e) {
            int cancelledTasks = executor.shutdownNow().size();
            logger.warn(
                    "Business executor shutdown interrupted: cancelledTasks={}",
                    cancelledTasks,
                    e
            );
            return true;
        }
    }

    private static boolean shutdownEventLoopGroup(
            EventLoopGroup group,
            long timeoutSeconds,
            String groupName) {

        if (group == null) {
            return false;
        }

        try {
            group.shutdownGracefully(
                    0L,
                    timeoutSeconds,
                    TimeUnit.SECONDS
            ).sync();
            return false;
        } catch (InterruptedException e) {
            logger.warn("{} shutdown interrupted", groupName, e);
            return true;
        } catch (Exception e) {
            logger.warn("{} shutdown failed", groupName, e);
            return false;
        }
    }

    private static final class BusinessThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(
                    task,
                    "inet-business-" + sequence.getAndIncrement()
            );
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setUncaughtExceptionHandler((t, e) ->
                    logger.error(
                            "Uncaught exception in thread {}",
                            t.getName(),
                            e
                    )
            );
            return thread;
        }
    }

    public static void main(String[] args) {
        new INetServerV2().start();
    }
}
