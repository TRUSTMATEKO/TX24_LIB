package kr.tx24.lib.inter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.lb.LoadBalancer;
import kr.tx24.lib.map.MapFactory;
import kr.tx24.lib.map.TypeRegistry;

/**
 * INet - Internetwork 통신 프레임워크 (Netty 4.2.6 기반)
 * 
 * <p>내부 시스템 간 고성능 TCP 기반 객체 통신을 제공합니다.
 * 
 * <h3>주요 특징</h3>
 * <ul>
 *   <li>Netty 4.2.6 기반 고성능 비동기 네트워크 통신</li>
 *   <li>Externalizable 최적화 (ObjectOutputStream 오버헤드 제거)</li>
 *   <li>ThreadLocal 버퍼 풀링으로 GC 압력 감소</li>
 *   <li>로드밸런서 통합 지원</li>
 *   <li>자동 재연결 및 장애 서버 관리</li>
 *   <li>자동 프레임 분할/조립 (LengthFieldBasedFrameDecoder)</li>
 *   <li>타임아웃 자동 처리 (ReadTimeout/WriteTimeout Handler)</li>
 * </ul>
 * 
 * <h3>사용 방법</h3>
 * 
 * <p><b>1. 직접 연결</b></p>
 * <pre>{@code
 * INMessage response = new INet("출발시스템명", "도착시스템명")
 *     .head("customKey", "customValue")
 *     .data("requestData", dataObject)
 *     .connect("192.168.1.100", 3333, 120000);
 * 
 * if (response.head().isTrue("result")) {
 *     // 성공 처리
 * }
 * }</pre>
 * 
 * <p><b>2. 로드밸런서 사용</b></p>
 * <pre>{@code
 * INMessage response = new INet("출발시스템명", "도착시스템명")
 *     .head("customKey", "customValue")
 *     .data("requestData", dataObject)
 *     .connectLb("backend-service", 120000);
 * }</pre>
 * 
 * <p><b>3. 로드밸런서 + 재시도 설정</b></p>
 * <pre>{@code
 * INMessage response = new INet("출발시스템명", "도착시스템명")
 *     .head("customKey", "customValue")
 *     .data("requestData", dataObject)
 *     .retry(3)  // 실패 시 최대 3회 재시도
 *     .connectLb("backend-service", 120000);
 * }</pre>
 * <h3>메시지 구조</h3>
 * 
 * <p><b>Head 영역</b> (메타데이터)</p>
 * <ul>
 *   <li>{@code proc} - 송신 프로그램 명칭 (자동 설정)</li>
 *   <li>{@code procId} - 송신 프로세스 ID (자동 설정)</li>
 *   <li>{@code procIp} - 송신 시스템 IP (자동 설정)</li>
 *   <li>{@code procHost} - 송신 시스템 Hostname (자동 설정)</li>
 *   <li>{@code source} - 송신 시스템 명칭</li>
 *   <li>{@code target} - 수신 시스템 명칭</li>
 *   <li>{@code result} - 통신 성공 여부 (true/false)</li>
 *   <li>{@code message} - 상태 또는 에러 메시지</li>
 *   <li>{@code time} - 통신 소요 시간 (나노초)</li>
 * </ul>
 * 
 * <p><b>Data 영역</b> (페이로드)</p>
 * <ul>
 *   <li>사용자 정의 키-값 쌍으로 자유롭게 구성</li>
 *   <li>Java 기본 객체 및 직렬화 가능한 객체 지원</li>
 * </ul>
 * 
 * <h3>응답 확인</h3>
 * <pre>{@code
 * if (response.head().isTrue("result")) {
 *     // 성공
 *     Object data = response.data().get("responseKey");
 * } else {
 *     // 실패
 *     String errorMsg = response.head().getString("message");
 *     logger.error("통신 실패: {}", errorMsg);
 * }
 * }</pre>
 * 
 * <h3>Inner Classes</h3>
 * <ul>
 *   <li>{@link INMessage} - 통신 메시지 컨테이너 (Externalizable 최적화)</li>
 *   <li>{@link INMap} - LinkedHashMap 기반 유틸리티 맵</li>
 *   <li>{@link INetClientHandler} - Netty 메시지 핸들러</li>
 * </ul>
 * 
 * @author TX24
 * @version 3.0 (Netty 4.2.6)
 * @see INMessage
 * @see INMap
 */
public class INet implements java.io.Serializable {

	private static final long serialVersionUID 	= -3518167926980673854L;
	private static final Logger logger			= LoggerFactory.getLogger(INet.class);

	private static final int DEFAULT_CONNECT_TIMEOUT 	= 2 * 1000;	// 2초
	private static final int DEFAULT_READ_TIMEOUT 		= 2 * 60 * 1000;	// 2분
	private static final String TIMEOUT_CONNECT 		= "connect timeout";
	private static final String TIMEOUT_READ 			= "read timeout";
	private static final String TIMEOUT_WRITE 			= "write timeout";
	private static final String CONNECTED				= "connected";
	private static final String READY					= "ready";
	private static final String MESSAGE_SENT			= "message sent";

	private static final ThreadLocal<ByteArrayOutputStream> bosPool = ThreadLocal.withInitial(() -> new ByteArrayOutputStream(4096));

	private static volatile boolean isShutdown = false;

	private static volatile EventLoopGroup workerGroup;
	private static final Object lock = new Object();

	private final AtomicInteger maxRetryCount = new AtomicInteger(1);
	
	private final INMap headMap = new INMap();
	private final INMap dataMap = new INMap();


	private static EventLoopGroup getWorkerGroup() {
		if (workerGroup == null || workerGroup.isShutdown() || workerGroup.isTerminated()) {
			synchronized (lock) {
				if (workerGroup == null || workerGroup.isShutdown() || workerGroup.isTerminated()) {
					workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
				}
			}
		}
		return workerGroup;
	}

	/**
	 * INet 리소스 정리
	 */
	public static void shutdown() {
		if (isShutdown) {
			return;
		}

		isShutdown = true;
		try {
			bosPool.remove();

			if (workerGroup != null && !workerGroup.isShutdown()) {
				workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
			}
		} catch (Exception e) {
			logger.warn("Error during INet shutdown", e);
		}
	}

	public INet() {
	}

	public INet(String source) {
		this(source, "N/A");
	}

	public INet(String source, String target) {
		this.headMap.put("proc"	 , SystemUtils.getLocalProcessName());
		this.headMap.put("procId", SystemUtils.getLocalProcessId());
		this.headMap.put("procIp", SystemUtils.getLocalAddress());
		this.headMap.put("procHost", SystemUtils.getLocalHostname());
		this.headMap.put("source", source);
		this.headMap.put("target", target);
	}
	
	public INet(byte[] data) throws IOException, ClassNotFoundException {
	    INMessage message = deserialize(data);
	    headMap.putAll(message.head());
	    dataMap.putAll(message.data());
	}
	
	/**
	 * connectLb 실패 시 재시도 횟수 설정
	 * 기본 1회 재시도로 설정됨 , 재시도 안할 경우 0으로 설정 바람.
	 * @param retryCount 재시도 횟수 (0이면 재시도 안함, 최대 5회)
	 * @return INet 인스턴스 (메서드 체이닝용)
	 */
	public INet retry(int retryCount) {
		if (retryCount < 0) {
			this.maxRetryCount.set(0);
		} else if (retryCount > 5) {
			logger.info("Retry count {} exceeds maximum 5, set to 5", retryCount);
			this.maxRetryCount.set(5);  // 최대 10회로 제한
		} else {
			this.maxRetryCount.set(retryCount);
		}
		
		if (SystemUtils.deepview()) {
			logger.info("Retry count set to {}", this.maxRetryCount.get());
		}
		
		return this;
	}
	

	public INet head(String key, Object value) {
		this.headMap.put(key, value);
		return this;
	}

	public <M extends Map<String, ?>> INet head(M map) {
		if (map != null) {
			this.headMap.putAll(map);
		}
		return this;
	}

	public <M extends Map<String, ?>> INet data(M map) {
		if (map != null) {
			this.dataMap.putAll(map);
		}
		return this;
	}

	public INet data(String key, Object value) {
		this.dataMap.put(key, value);
		return this;
	}
	
	
	public INMap head() {
		return this.headMap;
	}

	public INMap data() {
		return this.dataMap;
	}

	public <T extends Map<String, Object>> T head(Class<T> clazz) {
		T map = MapFactory.createObjectMap(clazz);
		map.putAll(this.headMap);
		return map;
	}

	public <T extends Map<String, Object>> T data(Class<T> clazz) {
		T map = MapFactory.createObjectMap(clazz);
		map.putAll(this.dataMap);
		return map;
	}

	public <T extends Map<String, Object>> T head(TypeRegistry typeRegistry) {
		T map = MapFactory.createObjectMap(typeRegistry);
		map.putAll(this.headMap);
		return map;
	}

	public <T extends Map<String, Object>> T data(TypeRegistry typeRegistry) {
		T map = MapFactory.createObjectMap(typeRegistry);
		map.putAll(this.dataMap);
		return map;
	}
	

	public INMessage connect(String addr, int port) {
		return connect(addr, port, DEFAULT_READ_TIMEOUT);  // 기본 2분
	}

	
	public INMessage connect(String host, int port, int timeout) {
		INMessage message = new INMessage();
		
		message.head().putAll(this.headMap);
		message.data().putAll(this.dataMap);
		message.head().put("result", false);

		if (host == null || host.trim().equals("")) {
			logger.info("invalid host address");
			message.message("invalid host address");
			return message;
		}

		if (port == 0) {
			logger.info("invalid host port");
			message.message("invalid host port");
			return message;
		}

		return execute(message, host, port, timeout);
	}

	public INMessage connectLb(String server) {
		return connectLb(server, 2 * 60 * 1000);  // 기본 2분
	}
	

	public INMessage connectLb(String server, int timeout) {
		INMessage send = new INMessage();
		send.head().putAll(this.headMap);
		send.data().putAll(this.dataMap);
		send.head().put("result", false);

		String endPoint = "";
		String host = null;
		int port = 0;

		if (LoadBalancer.isEnabled()) {
			endPoint = LoadBalancer.getExcludeBrokenServer(server);
			if (endPoint != null && !endPoint.trim().equals("")) {
				String[] endPoints = LoadBalancer.getExcludeBrokenServer(server).split(":");
				host = endPoints[0];
				if (endPoints.length > 1) {
					port = Integer.parseInt(endPoints[1]);
				} else {
					logger.info("invalid host address. please check nlb.json");
					send.message("invalid host address. please check nlb.json " + endPoints);
					return send;
				}
			} else {
				logger.info("loadbalance address is null");
				send.message("loadbalance address is null");
				return send;
			}

		} else {
			logger.info("loadbalance not found, "+server);
			send.message("loadbalance not found, "+server);
			return send;
		}
         
	
		int retryCount = this.maxRetryCount.get();
		INMessage recv = execute(send, host, port, timeout);
		
		// 통신장애에 의한 실패의 경우 브로큰 서버로 등록하고 재시도
		if(!recv.successful() && recv.head.isEquals("message", TIMEOUT_CONNECT)) {
			LoadBalancer.setBrokenServer(server, endPoint);
			
			for (int attempt = 1; attempt <= retryCount; attempt++) {
				if (SystemUtils.deepview()) {
					logger.info("Retry attempt {}/{} for server {}", attempt, retryCount, server);
				}
				
				// 새로운 서버 엔드포인트 조회
				endPoint = LoadBalancer.getExcludeBrokenServer(server);
				if (endPoint == null || endPoint.trim().equals("")) {
					logger.info("No available server for retry attempt {}", attempt);
					send.message("No available server after " + attempt + " retry attempts");
					break;
				}
				
				// 서버 정보 파싱
				String[] endPoints = endPoint.split(":");
				host = endPoints[0];
				if (endPoints.length > 1) {
					port = Integer.parseInt(endPoints[1]);
				} else {
					logger.info("Invalid endpoint format for retry: {}", endPoint);
					continue;
				}
				// 재시도 실행
				recv = execute(send, host, port, timeout);
				
				// 성공하면 즉시 반환
				if (recv.successful()) {
					if (SystemUtils.deepview()) {
						logger.info("Retry succeeded on attempt {}/{}", attempt, retryCount);
					}
					return recv;
				}
				
				// 연결 실패 시 해당 서버도 브로큰으로 등록
				if (recv.head.isEquals("message", TIMEOUT_CONNECT)) {
					LoadBalancer.setBrokenServer(server, endPoint);
				}
			}
			
			// 모든 재시도 실패
			if (!recv.successful() && SystemUtils.deepview()) {
				logger.info("All {} retry attempts failed for server {}", retryCount, server);
			}
			
		}

		return recv;
	}

	
	/**
	 * Netty 기반 메시지 송수신 실행
	 * 
	 * @param message 전송할 메시지
	 * @param host 서버 호스트
	 * @param port 서버 포트
	 * @param timeout 타임아웃 (밀리초)
	 * @return 응답 메시지
	 */
	private INMessage execute(INMessage send, String host, int port, int timeout) {
		if (send.data().isEmpty()) {
			send.message("the data field is empty");
			return send;
		}

		long startTime = System.nanoTime();
		send.message(READY);

		// 직렬화
		byte[] data;
		
		try {
			data = serialize(send);
		} catch (Exception e) {
			send.message("serialize failed : "+CommonUtils.getExceptionMessage(e));
			return send;
		}

		InetSocketAddress endPoint = new InetSocketAddress(host, port);

		// 응답 대기용
		final CountDownLatch latch = new CountDownLatch(1);
		final AtomicReference<byte[]> responseData = new AtomicReference<>();
		final AtomicReference<String> errorMessage = new AtomicReference<>();

		// Bootstrap 설정
		Bootstrap bootstrap = new Bootstrap();
		bootstrap.group(getWorkerGroup())
				.channel(NioSocketChannel.class)
				.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, DEFAULT_CONNECT_TIMEOUT)
				.option(ChannelOption.SO_KEEPALIVE, true)
				.option(ChannelOption.TCP_NODELAY, true)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					protected void initChannel(SocketChannel ch) throws Exception {
						ch.pipeline()
								// 타임아웃 핸들러 (초 단위)
								.addLast("writeTimeout", new WriteTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
								.addLast("readTimeout", new ReadTimeoutHandler(timeout, TimeUnit.MILLISECONDS))
								// 프레임 디코더/인코더 (4바이트 길이 필드)
								.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(
										Integer.MAX_VALUE, 0, 4, 0, 4))
								.addLast("frameEncoder", new LengthFieldPrepender(4))
								// 비즈니스 로직 핸들러
								.addLast("handler", new INetClientHandler(responseData, errorMessage, latch));
					}
				});

		Channel channel = null;
		try {
			// 연결
			ChannelFuture connectFuture = bootstrap.connect(endPoint);

			// 연결 대기 (동기)
			if (!connectFuture.await(DEFAULT_CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)) {
				send.message(TIMEOUT_CONNECT);
				logger.info("connect timeout to {}:{}", host, port);
				return send;
			}

			if (!connectFuture.isSuccess()) {
				send.message(TIMEOUT_CONNECT + ":" + CommonUtils.getExceptionMessage(connectFuture.cause()));
				logger.info("Connect failed to {}:{}", host, port, CommonUtils.getExceptionMessage(connectFuture.cause()));
				return send;
			}

			channel = connectFuture.channel();
			send.message(CONNECTED);

			if (SystemUtils.deepview()) {
				logger.info("INET {} > {} : [{}] bytes",
						send.head().getString("proc"),
						send.head().getString("target"),
						data.length);
			}

			// 데이터 전송
			ByteBuf buf = Unpooled.wrappedBuffer(data);
			ChannelFuture writeFuture = channel.writeAndFlush(buf);

			// 쓰기 완료 대기
			writeFuture.addListener((ChannelFutureListener) future -> {
				if (future.isSuccess()) {
					if (SystemUtils.deepview()) {
						logger.debug("Message sent successfully");
					}
				} else {
					errorMessage.set(TIMEOUT_WRITE + ": " +
							(future.cause() != null ? future.cause().getMessage() : "unknown"));
					latch.countDown();
				}
			});

			send.message(MESSAGE_SENT);
			

			// 응답 대기 (전체 timeout)
			if (!latch.await(timeout, TimeUnit.MILLISECONDS)) {
				send.message(TIMEOUT_READ);
				logger.info("read timeout from {}:{}", host, port);
				return send;
			}

			// 에러 확인
			String error = errorMessage.get();
			if (error != null) {
				send.message(error);
				logger.info("Communication error: {}", error);
				return send;
			}

			// 응답 데이터 확인
			byte[] response = responseData.get();
			if (response == null) {
				send.message("No response received");
				logger.info("No response data received");
				return send;
			}

			if (SystemUtils.deepview()) {
				logger.info("INET {} < {} : [{}] bytes",
						send.head().getString("proc"),
						send.head().getString("target"),
						response.length);
			}

			// 역직렬화
			send = deserialize(response);
			

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			send.message("Interrupted: " + e.getMessage());
			logger.info("INet execute interrupted", e);
		} catch (Exception e) {
			send.message("execute step : " + send.head().getString("message") +
					" exception: " + e.getMessage());
			logger.info("INet execute exception", CommonUtils.getExceptionMessage(e));
		} finally {
			// 채널 종료
			if (channel != null && channel.isOpen()) {
				try {
					channel.close().sync();
				} catch (Exception e) {
					logger.debug("Error closing channel", e);
				}
			}

			long elapsed = System.nanoTime() - startTime;
			send.head.put("elapsed", elapsed);

			if (SystemUtils.deepview()) {
				logger.info(String.format("elapsed Time in %.3fms%n", elapsed / 1e6d));
			}
		}

		return send;
	}
	
	

	

	public INMessage deserialize(byte[] data) throws IOException, ClassNotFoundException {

		Objects.requireNonNull(data, "data is null");

		if (data.length == 0) {
			throw new InvalidObjectException("data is empty");
		}

		try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
			 ObjectInputStream in = new ObjectInputStream(bis)) {
			/*
			ObjectInputFilter filter = info -> {
				Class<?> clazz = info.serialClass();
				if (clazz == null) return ObjectInputFilter.Status.UNDECIDED;

				// 허용 클래스 명시
				if (INMessage.class.equals(clazz)) return ObjectInputFilter.Status.ALLOWED;
				if (INMap.class.equals(clazz)) return ObjectInputFilter.Status.ALLOWED;

				// 기본 타입 및 컬렉션 허용
				if (clazz == String.class || clazz == Integer.class || clazz == Long.class ||
						clazz == Double.class || clazz == Float.class || clazz == Boolean.class ||
						clazz == Byte.class || clazz == Short.class || clazz == Character.class ||
						clazz == BigDecimal.class || clazz == BigInteger.class || clazz == Number.class ||
						clazz == java.sql.Timestamp.class || clazz == java.util.Date.class ||
						clazz == java.util.LinkedHashMap.class || clazz == kr.tx24.lib.map.LinkedMap.class || clazz == kr.tx24.lib.map.SharedMap.class) {
					return ObjectInputFilter.Status.ALLOWED;
				}

				// 배열 타입 허용
				if (clazz.isArray()) {
					return ObjectInputFilter.Status.ALLOWED;
				}

				logger.warn("Rejected class in deserialization: {}", clazz.getName());
				return ObjectInputFilter.Status.REJECTED;
			};
			in.setObjectInputFilter(filter);
			 */
			Object obj = in.readObject();
			if (!(obj instanceof INMessage msg)) {
				throw new InvalidObjectException(
						"Deserialized object is not INMessage: " + obj.getClass());
			}

			return msg;
		} catch (EOFException e) {
			throw new InvalidObjectException("Malformed deserialized data: " + e.getMessage());
		}
	}

	public byte[] serialize(INMessage message) throws IOException {
		ByteArrayOutputStream bos = bosPool.get();
		bos.reset();

		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(message);
			out.flush();

			byte[] result = bos.toByteArray();
			return result;
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					logger.info("close ObjectOutputStream", e);
				}
			}
		}
	}
	
	public byte[] serialize() throws IOException {
	    INMessage message = new INMessage();
	    message.head().putAll(this.headMap);
	    message.data().putAll(this.dataMap);
	    
	    return serialize(message);
	}
	

	
	
	/**
	 * Netty 클라이언트 핸들러
	 * 메시지 수신 및 에러 처리
	 */
	private static class INetClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

		private final AtomicReference<byte[]> responseData;
		private final AtomicReference<String> errorMessage;
		private final CountDownLatch latch;

		public INetClientHandler(AtomicReference<byte[]> responseData,
								 AtomicReference<String> errorMessage,
								 CountDownLatch latch) {
			this.responseData = responseData;
			this.errorMessage = errorMessage;
			this.latch = latch;
		}

		@Override
		protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
			// 응답 데이터 읽기
			int readableBytes = msg.readableBytes();
			byte[] data = new byte[readableBytes];
			msg.readBytes(data);

			responseData.set(data);
			latch.countDown();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
				errorMessage.set(TIMEOUT_READ);
			} else if (cause instanceof io.netty.handler.timeout.WriteTimeoutException) {
				errorMessage.set(TIMEOUT_WRITE);
			} else {
				errorMessage.set("Exception: " + cause.getMessage());
			}

			logger.info("Handler exception caught", cause);
			latch.countDown();
			ctx.close();
		}

		@Override
		public void channelInactive(ChannelHandlerContext ctx) throws Exception {
			// 연결이 비정상적으로 끊어진 경우
			if (latch.getCount() > 0 && responseData.get() == null && errorMessage.get() == null) {
				errorMessage.set("Connection closed unexpectedly");
				latch.countDown();
			}
			super.channelInactive(ctx);
		}
	}
	
	

	/**
	 * INet 메시지 컨테이너
	 * <p>Externalizable 인터페이스를 구현하여 직렬화 최적화
	 */
	public static class INMessage implements Externalizable {

		private static final long serialVersionUID = 7834857649283647L;

		private INMap head = new INMap();
		private INMap data = new INMap();

		public INMessage() {
		}

		public INMessage(INMap head, INMap data) {
			this.head = head;
			this.data = data;
		}

		public INMap head() {
			return head;
		}

		public void head(INMap head) {
			this.head = head;
		}
		
		/*
		 * head.put("message",message);
		 */
		public void message(String message) {
			this.head.put("message", message);
		}
		
		/**
		 * return head.isTrue("result")
		 * @return
		 */
		public boolean successful() {
			return this.head.isTrue("result");
		}

		public INMap data() {
			return data;
		}

		public void data(INMap data) {
			this.data = data;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			writeMap(out, head);
			writeMap(out, data);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			head = readMap(in);
			data = readMap(in);
		}

		private static void writeMap(ObjectOutput out, INMap map) throws IOException {
			if (map == null) {
				out.writeInt(0);
				return;
			}

			out.writeInt(map.size());

			for (Map.Entry<String, Object> entry : map.entrySet()) {
				out.writeUTF(entry.getKey());
				out.writeObject(entry.getValue());
			}
		}

		private static INMap readMap(ObjectInput in) throws IOException, ClassNotFoundException {
			int size = in.readInt();

			if (size < 0) {
				throw new InvalidObjectException("Negative map size: " + size);
			}

			INMap map = new INMap();

			for (int i = 0; i < size; i++) {
				String key = in.readUTF();
				Object value = in.readObject();
				map.put(key, value);
			}

			return map;
		}
	}

	public static class INMap extends LinkedHashMap<String, Object> implements Serializable {

		private static final long serialVersionUID = 7834857649283647L;
		private static final DateTimeFormatter DF =
				DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.S");

		/**
		 * 16개의 엔트리를 저장할 수 있는 공간으로 시작, 
		 * 용량이 75% 차면 자동으로 2배 확장 
		 * false (삽입 순서): 데이터를 넣은 순서대로 유지
		 */
		public INMap() {
			super(16, 0.75f, false);
		}

		public INMap(Map<? extends String, ? extends Object> map) {
			super(16, 0.75f, false);
			if (map != null) super.putAll(map);
		}

		public INMap putAllSafe(Map<? extends String, ? extends Object> map) {
			if (map != null) super.putAll(map);
			return this;
		}

		public String getString(String key) {
			Object o = get(key);
			if (o == null) return "";
			try {
				// 1. 문자열 / 숫자 / 불리언 등 기본형 계열
				if (o instanceof String s) return s;
				if (o instanceof Character c) return String.valueOf(c);
				if (o instanceof Boolean b) return b.toString();

				if (o instanceof Byte v) return v.toString();
				if (o instanceof Short v) return v.toString();
				if (o instanceof Integer v) return v.toString();
				if (o instanceof Long v) return v.toString();
				if (o instanceof Float v) return v.toString();
				if (o instanceof Double v) return v.toString();
				if (o instanceof AtomicInteger v) return Integer.toString(v.get());
				if (o instanceof AtomicLong v) return Long.toString(v.get());

				if (o instanceof BigInteger v) return v.toString();
				if (o instanceof BigDecimal v) return v.toString();

				// 2. 배열 / 버퍼
				if (o instanceof char[] arr) return String.valueOf(arr);
				if (o instanceof byte[] arr) return new String(arr, Charset.defaultCharset());
				if (o instanceof String[] arr) return String.join(",", arr);
				if (o instanceof ByteBuffer bb) {
					ByteBuffer dup = bb.duplicate(); // position 보호
					byte[] dst = new byte[dup.remaining()];
					dup.get(dst);
					return new String(dst, Charset.defaultCharset());
				}

				// 3. 날짜/시간 관련
				if (o instanceof Timestamp ts) return DF.format(ts.toLocalDateTime());
				if (o instanceof java.sql.Date d) return DF.format(d.toLocalDate().atStartOfDay());
				if (o instanceof java.util.Date d)
					return DF.format(d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
				if (o instanceof Calendar cal)
					return DF.format(cal.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
				if (o instanceof LocalDate ld) return DF.format(ld.atStartOfDay());
				if (o instanceof LocalDateTime ldt) return DF.format(ldt);
				if (o instanceof Instant inst)
					return DF.format(inst.atZone(ZoneId.systemDefault()).toLocalDateTime());

				// 4. 컬렉션 / 맵 / Enum / Optional
				if (o instanceof Collection<?> c) {
					return String.join(",", c.stream().map(Objects::toString).toList());
				}
				if (o instanceof Map<?, ?> m) return m.entrySet().toString();
				if (o instanceof Enum<?> e) return e.name();
				if (o instanceof Optional<?> opt) return opt.map(Objects::toString).orElse("");

				// 5. 예외 및 그 외
				if (o instanceof Throwable t) return t.getMessage();
			} catch (Exception e) {
				logger.warn("Failed to convert key '{}' with value '{}' to String: {}", key, o, e.getMessage());
			}

			// Default
			return o.toString();
		}

		public String getString(String key, String replace) {
			String val = getString(key);
			return val.isBlank() ? replace : val;
		}

		public boolean getBoolean(String key) {
			Object obj = get(key);
			if (obj == null) return false;
			String val = getString(key).toLowerCase();
			return val.equals("true") || val.equals("1");
		}

		public int getInt(String key) {
			Object obj = get(key);
			if (obj == null) return 0;

			try {
				if (obj instanceof Number n) {
					return n.intValue();
				}
				if (obj instanceof Boolean b) {
					return b ? 1 : 0;
				}
				if (obj instanceof String s) {
					return Integer.parseInt(s.trim());
				}
				if (obj instanceof byte[] arr) {
					return Integer.parseInt(new String(arr).trim());
				}

				return 0; // 그 외 타입
			} catch (Exception e) {
				logger.warn("Failed to parse '{}' to int: {}", obj, e.getMessage());
				return 0;
			}
		}

		public int getInt(String key, int replace) {
			int val = getInt(key);
			return val == 0 ? replace : val;
		}

		public long getLong(String key) {
			Object obj = get(key);
			if (obj == null) return 0L;

			try {
				if (obj instanceof Number n) {
					return n.longValue();
				}
				if (obj instanceof Boolean b) {
					return b ? 1L : 0L;
				}
				if (obj instanceof String s) {
					return Long.parseLong(s.trim());
				}
				if (obj instanceof byte[] arr) {
					return Long.parseLong(new String(arr).trim());
				}

				return 0L;
			} catch (Exception e) {
				logger.warn("Failed to parse '{}' to long: {}", obj, e.getMessage());
				return 0L;
			}
		}

		public long getLong(String key, long replace) {
			long val = getLong(key);
			return val == 0L ? replace : val;
		}

		public double getDouble(String key) {
			Object obj = get(key);
			if (obj == null) return 0.0;

			try {
				if (obj instanceof Number n) {
					return n.doubleValue();
				}
				if (obj instanceof Boolean b) {
					return b ? 1.0 : 0.0;
				}
				if (obj instanceof String s) {
					return Double.parseDouble(s.trim());
				}
				if (obj instanceof byte[] arr) {
					return Double.parseDouble(new String(arr).trim());
				}

				return 0.0; // 그 외 타입
			} catch (Exception e) {
				logger.warn("Failed to parse '{}' to double: {}", obj, e.getMessage());
				return 0.0;
			}
		}

		public double getDouble(String key, double replace) {
			double val = getDouble(key);
			return val == 0.0 ? replace : val;
		}

		public BigDecimal getBigDecimal(String key) {
			Object obj = get(key);
			if (obj instanceof BigDecimal bd) return bd;
			return new BigDecimal(getString(key, "0"));
		}

		public BigInteger getBigInteger(String key) {
			Object obj = get(key);
			if (obj instanceof BigInteger bi) return bi;
			if (obj instanceof BigDecimal bd) return bd.toBigInteger();
			return new BigInteger(getString(key, "0"));
		}

		public Timestamp getTimestamp(String key) {
			Object obj = get(key);
			if (obj instanceof Timestamp ts) return ts;
			if (obj instanceof String s) return Timestamp.valueOf(s);
			return null;
		}

		public boolean like(String key, String value) {
			return getString(key).contains(value);
		}

		public boolean isTrue(String key) {
			return getBoolean(key);
		}

		public boolean isNull(String key) {
			return get(key) == null;
		}

		public boolean startsWith(String key, String value) {
			return getString(key).startsWith(value);
		}

		public boolean isEquals(String key, Object value) {
			Object obj = get(key);
			return obj == value || (obj != null && obj.equals(value));
		}

		public boolean isEmpty(String key) {
			Object value = get(key);
			if (value == null) return true;

			if (value instanceof String s) return s.trim().isEmpty();
			if (value instanceof Collection<?> c) return c.isEmpty();
			if (value instanceof Map<?, ?> m) return m.isEmpty();
			if (value instanceof Optional<?> opt) return opt.isEmpty();
			if (value.getClass().isArray()) return Array.getLength(value) == 0;
			if (value instanceof Number n) return n.doubleValue() == 0;
			if (value instanceof Boolean b) return !b;

			return false;
		}
	}
}