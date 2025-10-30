package kr.tx24.lib.inter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInput;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.lb.LoadBalancer;
import kr.tx24.lib.map.MapFactory;
import kr.tx24.lib.map.TypeRegistry;

/**
 * INet - Internetwork 통신 프레임워크
 * 
 * <p>내부 시스템 간 고성능 TCP 기반 객체 통신을 제공합니다.
 * 
 * <h3>주요 특징</h3>
 * <ul>
 *   <li>TCP 기반 바이너리 객체 통신</li>
 *   <li>Externalizable 최적화 (ObjectOutputStream 오버헤드 제거)</li>
 *   <li>ThreadLocal 버퍼 풀링으로 GC 압력 감소</li>
 *   <li>로드밸런서 통합 지원</li>
 *   <li>자동 재연결 및 장애 서버 관리</li>
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
 * </ul>
 * 
 * @author TX24
 * @version 2.0
 * @see INMessage
 * @see INMap
 */
public class INet implements java.io.Serializable{
	
	private static final long serialVersionUID 	= -3518167926980673854L;
	private static final Logger logger = LoggerFactory.getLogger(INet.class);
	
	private static final int DEFAULT_CONNECT_TIMEOUT= 1000*2;	//2초
	private static final String TIMEOUT_CONNECT = "connect timeout";
	private static final String TIMEOUT_READ = "read timeout";
	
	private static final ThreadLocal<ByteArrayOutputStream> bosPool = 
	    ThreadLocal.withInitial(() -> new ByteArrayOutputStream(4096));
	
	private static volatile boolean isShutdown = false;
	
	
	
	static {
        Runtime.getRuntime().addShutdownHook(new Thread(INet::shutdown, "ShutdownHook-INet"));
    }
	
	
	private String host		= null;
	private int port		= 0;
	private int timeout		= 2*60*1000 ; 	//2분 
	
	private INMessage message	= new INMessage();
	
	
	public static void shutdown() {
        if (isShutdown) {
            return;
        }
        
        isShutdown = true;
        try {
            bosPool.remove();
        } catch (Exception e) {
            logger.warn("Error during INet shutdown", e);
        }
    }
	
	
	
	public INet() {
		this.message = new INMessage();
	}
	
	
	public INet(String source) {
		this(source,"N/A");
	}
	
	public INet(String source,String target) {
		this.message = new INMessage();
		this.message.getHead().put("proc"	, SystemUtils.getLocalProcessName());
		this.message.getHead().put("procId"	, SystemUtils.getLocalProcessId());
		this.message.getHead().put("procIp"	, SystemUtils.getLocalAddress());
		this.message.getHead().put("procHost"	, SystemUtils.getLocalHostname());
		this.message.getHead().put("source"	, source);
		this.message.getHead().put("target"	, target);
	}
	
	
	
	public INet head(String key,Object value) {
		this.message.getHead().put(key, value);
		return this;
	}
	
	public <M extends Map<String, ?>> INet head(M map){
		if(map != null){
			this.message.getHead().putAll(map);
		}
		return this;
	}
	

	
	public <M extends Map<String, ?>> INet data(M map){
		if(map != null){
			this.message.getData().putAll(map);
		}
		return this;
	}
	
	public INet data(String key,Object value) {
		this.message.getData().put(key, value);
		return this;
	}
	

	public INMessage connect(String addr , int port) {
		return connect(addr,port,this.timeout);
	}
	

	public INMessage connect(String host , int port , int timeout) {
		this.host = host;
		this.port = port;
		this.timeout = timeout;
		
		this.message.getHead().put("result"	, false);
		
		
		if(this.host == null || this.host.trim().equals("")) {
			logger.info("host 정보를 찾을 수 없습니다.");	
			this.message.getHead().put("message"	, "서버 주소가 없습니다. ");
			return this.message;
		}
		
		if(this.port == 0 ) {
			logger.info("port is 0");
			this.message.getHead().put("message"	, "서버 포트가 지정되지 않았습니다.");
			return this.message;
		}
		
		return execute();
	}
	

	public INMessage connectLb(String server) {
		return connectLb(server,this.timeout);
	}
	

	public INMessage connectLb(String server,int timeout) {
		this.timeout = timeout;
		
		
		this.message.getHead().put("result"	, false);
		
		String endPoint = "";
		
		if(LoadBalancer.isEnabled()) {
			endPoint = LoadBalancer.getExcludeBrokenServer(server);
			if(endPoint != null && !endPoint.trim().equals("")) {
				String[] endPoints = LoadBalancer.getExcludeBrokenServer(server).split(":");
				this.host = endPoints[0];
				if(endPoints.length > 1) {
					this.port = Integer.parseInt(endPoints[1]);
				}else {
					logger.info("접속 포트를 확인할 수 없습니다.");
					this.message.getHead().put("message"	, "접속 포트를 확인할 수 없습니다. "+endPoints);
					return this.message;
				}
			}else {
				logger.info("loadbalance address is null");
				this.message.getHead().put("message"	, "로드밸런서 주소를 찾을 수 없습니다.");
				return this.message;
			}
			
		}else {
			this.message.getHead().put("message"	, "로드밸런서가 적용되지 않았거나 설정을 찾을 수 없습니다.");
			return this.message;
		}
		
		INMessage msg = execute();
		//통신장애에 의한 실패의 경우 브로큰 서버로 등록한다.
		if(!msg.head.isTrue("result") && msg.head.isEquals("message",TIMEOUT_CONNECT)) {
			LoadBalancer.setBrokenServer(server, endPoint);
			logger.info("connect failure , reconnect other server");
			msg = execute();
		}
		
		return msg;
	}

	
	public INMap head(){
		return this.message.getHead();
	}

	
	public INMap data(){
		return this.message.getData();
	}
	

	public <T extends Map<String,Object>> T head(Class<T> clazz) {
		T map = MapFactory.createObjectMap(clazz);
	    map.putAll(this.message.getHead());
	    return map;
	}


	public <T extends Map<String,Object>> T data(Class<T> clazz) {
		T map = MapFactory.createObjectMap(clazz);
	    map.putAll(this.message.getData());
	    return map;
	}
	

	public <T extends Map<String,Object>> T head(TypeRegistry typeRegistry) {
		T map = MapFactory.createObjectMap(typeRegistry);
	    map.putAll(this.message.getHead());
	    return map;
	}

	
	public <T extends Map<String,Object>> T data(TypeRegistry typeRegistry) {
		T map = MapFactory.createObjectMap(typeRegistry);
	    map.putAll(this.message.getData());
	    return map;
	}

	

	
	private INMessage execute() {
	    if (this.message.getData().isEmpty()) {
	        this.message.getHead().put("message", "전송할 데이터가 없습니다. data is empty");
	        return this.message;
	    }

	    long startTime = System.nanoTime();
	    this.message.getHead().put("message", "ready");

	    byte[] data;
	    try {
	        data = serialize();
	    } catch (Exception e) {
	        this.message.getHead().put("message", "serialize failed: " + e.getMessage());
	        logger.warn("Serialization failed", e);
	        return this.message;
	    }

	    InetSocketAddress endPoint = new InetSocketAddress(this.host, this.port);

	    try (SocketChannel channel = SocketChannel.open();
	         Selector selector = Selector.open()) {

	        channel.configureBlocking(false);
	        channel.connect(endPoint);
	        channel.register(selector, SelectionKey.OP_CONNECT);

	        // 접속
	        if (!waitForConnect(channel, selector, DEFAULT_CONNECT_TIMEOUT)) {
	            this.message.getHead().put("message", TIMEOUT_CONNECT);
	            logger.info("Connect timeout to {}:{}", this.host, this.port);
	            return this.message;
	        }
	        this.message.getHead().put("message", "connected");

	        // 송신
	        if (!writeFullyOnce(channel, selector, data, this.message)) {
	            return this.message;
	        }

	        if (SystemUtils.deepview()) {
	            logger.info("INET {} > {} : [{}] bytes",
	                    this.message.getHead().getString("proc"),
	                    this.message.getHead().getString("target"),
	                    data.length);
	        }

	        this.message.getHead().put("message", "message sent");

	        // 수신
	        byte[] response = readFullyOnce(channel, selector, this.message, timeout);
	        if (response == null) {
	            return this.message;
	        }

	        if (SystemUtils.deepview()) {
	            logger.info("INET {} < {} : [{}] bytes",
	                    this.message.getHead().getString("proc"),
	                    this.message.getHead().getString("target"),
	                    response.length);
	        }

	        // 역직렬화
	        deserialize(response);
	        this.message.getHead().put("result", true);

	    } catch (Exception e) {
	        this.message.getHead().put("message",
	                "execute step : " + this.message.getHead().getString("message") +
	                        " exception: " + e.getMessage());
	        logger.warn("INet execute exception", e);
	    } finally {
	        long elapsed = System.nanoTime() - startTime;
	        this.message.getHead().put("time", elapsed);

	        if (SystemUtils.deepview()) {
	            logger.info(String.format("elapsed Time in %.3fms%n", elapsed / 1e6d));
	        }
	    }

	    return this.message;
	}

	private boolean waitForConnect(SocketChannel channel, Selector selector, int timeoutMs)
	        throws IOException {
	    long start = System.currentTimeMillis();
	    while (true) {
	        long elapsed = System.currentTimeMillis() - start;
	        if (elapsed >= timeoutMs) {
	            return false;
	        }

	        selector.select(Math.max(timeoutMs - elapsed, 1));
	        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
	        while (it.hasNext()) {
	            SelectionKey key = it.next();
	            it.remove();
	            if (!key.isValid()) continue;

	            if (key.isConnectable()) {
	                if (channel.finishConnect()) {
	                    key.interestOps(0);
	                    return true;
	                }
	            }
	        }
	    }
	}

	private boolean writeFullyOnce(SocketChannel channel, Selector selector, byte[] data, INMessage msg)
	        throws IOException {
	    ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + data.length);
	    buffer.putInt(data.length);
	    buffer.put(data);
	    buffer.flip();

	    channel.register(selector, SelectionKey.OP_WRITE);
	    long start = System.currentTimeMillis();

	    while (buffer.hasRemaining()) {
	        long elapsed = System.currentTimeMillis() - start;
	        if (elapsed >= timeout) {
	            msg.head.put("message", TIMEOUT_READ);
	            logger.info("Write timeout");
	            return false;
	        }

	        selector.select(Math.max(timeout - elapsed, 1));
	        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
	        while (it.hasNext()) {
	            SelectionKey key = it.next();
	            it.remove();
	            if (!key.isValid()) continue;

	            if (key.isWritable()) {
	                channel.write(buffer);
	            }
	        }
	    }
	    return true;
	}

	private byte[] readFullyOnce(SocketChannel channel, Selector selector, INMessage msg, int timeoutMs)
	        throws IOException {
	    channel.register(selector, SelectionKey.OP_READ);

	    ByteBuffer lengthBuffer = ByteBuffer.allocate(Integer.BYTES);
	    ByteBuffer payloadBuffer = null;
	    long start = System.currentTimeMillis();

	    while (true) {
	        long elapsed = System.currentTimeMillis() - start;
	        if (elapsed >= timeoutMs) {
	            msg.head.put("message", TIMEOUT_READ);
	            logger.info("Read timeout");
	            return null;
	        }

	        selector.select(Math.max(timeoutMs - elapsed, 1));
	        Iterator<SelectionKey> it = selector.selectedKeys().iterator();
	        while (it.hasNext()) {
	            SelectionKey key = it.next();
	            it.remove();
	            if (!key.isValid()) continue;

	            if (key.isReadable()) {
	                // 1. length 읽기
	                if (lengthBuffer.hasRemaining()) {
	                    int r = channel.read(lengthBuffer);
	                    if (r == -1) throw new EOFException("EOF while reading length");
	                    if (!lengthBuffer.hasRemaining()) {
	                        lengthBuffer.flip();
	                        int len = lengthBuffer.getInt();
	                        payloadBuffer = ByteBuffer.allocate(len);
	                    }
	                }
	                // 2. payload 읽기
	                else if (payloadBuffer != null && payloadBuffer.hasRemaining()) {
	                    int r = channel.read(payloadBuffer);
	                    if (r == -1) throw new EOFException("EOF while reading payload");
	                    if (!payloadBuffer.hasRemaining()) {
	                        payloadBuffer.flip();
	                        byte[] resp = new byte[payloadBuffer.remaining()];
	                        payloadBuffer.get(resp);

	                        if (SystemUtils.deepview()) {
	                            logger.info("INET {} < {} : [{}] bytes",
	                                    msg.head.getString("proc"),
	                                    msg.head.getString("target"),
	                                    resp.length + Integer.BYTES);
	                        }
	                        return resp;
	                    }
	                }
	            }
	        }
	    }
	}

	
	
	public INet deserialize(byte[] data) throws IOException, ClassNotFoundException{
		 
		Objects.requireNonNull(data, "data is null");
		
		if (data.length == 0) {
			throw new InvalidObjectException("data is empty");
		}

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {

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
                    clazz == BigDecimal.class || clazz == BigInteger.class ||
                    clazz == java.sql.Timestamp.class || clazz == java.util.Date.class ||
                    clazz == java.util.LinkedHashMap.class) {
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

            Object obj = in.readObject();
            if (!(obj instanceof INMessage msg)) {
                throw new InvalidObjectException(
                        "Deserialized object is not INMessage: " + obj.getClass());
            }

            this.message = msg;
            return this;
        } catch (EOFException e) {
            throw new InvalidObjectException("Malformed serialized data: " + e.getMessage());
        }
	}
	
	
	public byte[] serialize() throws IOException{
		ByteArrayOutputStream bos = bosPool.get();
		bos.reset();
		
		ObjectOutputStream out = null;
		try {
			out = new ObjectOutputStream(bos);
			out.writeObject(this.message);
			out.flush();
			
			byte[] result = bos.toByteArray();
			return result;
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					logger.warn("Error closing ObjectOutputStream", e);
				}
			}
		}
	}
	
	
	
	

	public static class INMessage implements Externalizable {
		private static final long serialVersionUID = 6562469816779099746L;
		
		private INMap head = null;
		private INMap data = null;
		

		public INMessage() {
			this.head = new INMap();
			this.data = new INMap();
		}
		
		
		public INMap getHead() {
			return this.head;
		}
		
		public INMap getData() {
			return this.data;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			if (head == null) {
				out.writeInt(0);
			} else {
				writeINMap(out, head);
			}
			
			if (data == null) {
				out.writeInt(0);
			} else {
				writeINMap(out, data);
			}
		}
		
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			try {
				head = readINMap(in);
				if (head == null) {
					head = new INMap();
				}
				
				data = readINMap(in);
				if (data == null) {
					data = new INMap();
				}
			} catch (ClassNotFoundException e) {
				// 필드 초기화 보장
				if (head == null) head = new INMap();
				if (data == null) data = new INMap();
				throw e;
			}
		}
		

		private void writeINMap(ObjectOutput out, INMap map) throws IOException {
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
		

		private INMap readINMap(ObjectInput in) throws IOException, ClassNotFoundException {
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
			    if (o instanceof java.util.Date d) return DF.format(d.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
			    if (o instanceof Calendar cal) return DF.format(cal.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
			    if (o instanceof LocalDate ld) return DF.format(ld.atStartOfDay());
			    if (o instanceof LocalDateTime ldt) return DF.format(ldt);
			    if (o instanceof Instant inst) return DF.format(inst.atZone(ZoneId.systemDefault()).toLocalDateTime());

			    // 4. 컬렉션 / 맵 / Enum / Optional
			    if (o instanceof Collection<?> c) {
			        return String.join(",", c.stream().map(Objects::toString).toList());
			    }
			    if (o instanceof Map<?,?> m) return m.entrySet().toString();
			    if (o instanceof Enum<?> e) return e.name();
			    if (o instanceof Optional<?> opt) return opt.map(Objects::toString).orElse("");

			    // 5. 예외 및 그 외
			    if (o instanceof Throwable t) return t.getMessage();
	        }catch(Exception e) {
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
	        if (value instanceof Map<?,?> m) return m.isEmpty();
	        if (value instanceof Optional<?> opt) return opt.isEmpty();
	        if (value.getClass().isArray()) return Array.getLength(value) == 0;
	        if (value instanceof Number n) return n.doubleValue() == 0;
	        if (value instanceof Boolean b) return !b;
	        
	        return false;
	    }
	    
	}

	


}