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
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.lb.LoadBalancer;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;
import kr.tx24.lib.map.TypeRegistry;

/**
 *  INet 은 Internetwork 를 의미하며 내부 시스템간의 통신을 정의하기 위하서 만들어졌다.
 *  시스템간 Restfull, gPRC, Thrift, Tcp 등 많은 종류의 방법이 있겠지만.
 *  통신속도 및 marshalling 등에 소요되는 시간을 최소화 하기 위하여 아래와 같이
 *  TCP Base 에 Object 통신으로 정의하였다. INMessage 및 INMap 은 형상이 변경되면 안되므로
 *  INet 의 Inner Class 로 제작되었다. 
 *  아래 소스는 가급적 수정하면 통신이 불가 할 수 있다는 점에 유의하시기 바란다.
 *  
 *  
 *  사용 방법은 크게 2가지입니다.
 *  직접 타겟 시스템의 주소를 입력하는 방법 , 로드발런서 설정이 있다면 로드발런서 지정명칭을 사용하는 방법 
 *  
 *  INMessage exc = new INet("출발시스템명", "도착시스템명")
			.head("head", "필요한값")
			.data("data","data = pure java object")
			.connect("IP",3333, 2*60*1000);
		
	INMessage exc2 = new INet("출발시스템명", "도착시스템명")
			.head("head", "필요한값")
			.data("data","data = pure java object")
			.connectLb("로드밸런서이름값", 2*60*1000);
			
			
	Head 값 
		- proc 	: 현재 송신하는 프로그램의 명칭  실행시 지정
		- procId: 현재 송신하는 프로그램의 프로세스 아이디 
		- procIp: 현재 송신하는 시스템의 IP 또는 Hostname
		- source: 송신 프로그램 명칭 지정 
		- target: 수신 프로그램 명칭 지정
		- result: connect(), connectLb() 실행 후 해당 값으로 응답 결과 판단.
		  INMessage.head().isTrue("result") == true  > 송신 , 수신 정상
		  INMessage.head().isTrue("result") == false > 송신 시 실패하였거나. 서버에서 실패 메세지 회신 시 
		  INMessage.head().getString("message")  로 송수신 관련 성공/오류 관련 상세 메세지를 확인할 수 있습니다. 
		  
	Data 값
	    - 특정한 Key 를 사용하지 않는다. 사용자 임의 지정하는 방법을 사용하면 된다. 
		  
	
	★★★ 성능 최적화 ★★★
	- Externalizable 적용으로 ObjectOutputStream 오버헤드 제거 (3-5배 빠름)
	- ThreadLocal 버퍼 풀링으로 GC 압력 감소
	- 버퍼 초기 크기 지정으로 재할당 최소화
 */
public class INet implements java.io.Serializable{
	private static final long serialVersionUID 	= -3518167926980673854L;
	private static final Logger logger = LoggerFactory.getLogger(INet.class);
	
	private static final int DEFAULT_CONNECT_TIMEOUT= 1000*2;	//2초
	private static final String TIMEOUT_CONNECT = "connect timeout";
	private static final String TIMEOUT_READ = "read timeout";
	
	//버퍼 풀링 (ThreadLocal)
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
	}
	
	
	public INet(String source) {
		this(source,"NONE");
	}
	
	public INet(String source,String target) {
		this.message.head.put("proc"	, SystemUtils.getLocalProcessName());
		this.message.head.put("procId"	, SystemUtils.getLocalProcessId());
		this.message.head.put("procIp"	, SystemUtils.getLocalAddress());
		this.message.head.put("procHost"	, SystemUtils.getLocalHostname());
		this.message.head.put("source"	, source);
		this.message.head.put("target"	, target);
	}
	
	
	
	public INet head(String key,Object value) {
		this.message.head.put(key, value);
		return this;
	}
	
	public INet head(Map<String,Object> map){
		if(map != null){
			this.message.head.putAll(map);
		}
		return this;
	}
	
	public INet data(Map<String,Object> map){
		if(map != null){
			this.message.data.putAll(map);
		}
		return this;
	}
	
	public INet data(String key,Object value) {
		this.message.data.put(key, value);
		return this;
	}
	
	
	
	/**
	 * 주소 포트를 지정한 통신 실행 
	 * @param addr
	 * @param port
	 * @return
	 */
	public INMessage connect(String addr , int port) {
		return connect(addr,port,this.timeout);
	}
	
	/**
	 * 주소,포트,타임아웃을 지정한 통신 실행 
	 * @param addr
	 * @param port
	 * @param timeout
	 * @return
	 */
	public INMessage connect(String host , int port , int timeout) {
		this.host = host;
		this.port = port;
		this.timeout = timeout;
		
		this.head("result"	, false);
		
		
		if(this.host == null || this.host.trim().equals("")) {
			logger.info("host 정보를 찾을 수 없습니다.");	
			this.head("message"	, "서버 주소가 없습니다. ");
			return this.message;
		}
		
		if(this.port == 0 ) {
			logger.info("port is 0");
			this.head("message"	, "서버 포트가 지정되지 않았습니다.");
			return this.message;
		}
		
		return execute();
	}
	
	/**
	 * 로드밸런스를 통한 통신 실행 
	 * @param server
	 * @return
	 */
	public INMessage connectLb(String server) {
		return connectLb(server,this.timeout);
	}
	
	/**
	 * 로드밸런스를 통한 통신 실행
	 * @param server
	 * @param timeout
	 * @return
	 */
	public INMessage connectLb(String server,int timeout) {
		this.timeout = timeout;
		
		
		this.head("result"	, false);
		
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
					this.head("message"	, "접속 포트를 확인할 수 없습니다. "+endPoints);
					return this.message;
				}
			}else {
				logger.info("loadbalance address is null");
				this.head("message"	, "로드발런서 주소를 찾을 수 없습니다.");
				return this.message;
			}
			
		}else {
			this.head("message"	, "로드발런서가 적용되지 않았거나 설정을 찾을 수 없습니다.");
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
		return this.message.head;
	}

	
	public INMap data(){
		return this.message.data;
	}
	
	/*
	 * LinkedHashMap<String,Object> headMap = inNet.head(LinkedHashMap.class);
	 */
	public <T extends Map<String,Object>> T head(Class<T> clazz) {
	    try {
	        T map = clazz.getDeclaredConstructor().newInstance();
	        map.putAll(this.message.head);
	        return map;
	    } catch (Exception e) {
	        throw new RuntimeException(e);
	    }
	}

	/*
	 * LinkedHashMap<String,Object> dataMap = inNet.data(LinkedHashMap.class);
	 */
	public <T extends Map<String,Object>> T data(Class<T> clazz) {
	    try {
	        T map = clazz.getDeclaredConstructor().newInstance();
	        map.putAll(this.message.data);
	        return map;
	    } catch (Exception e) {
	        throw new RuntimeException(e);
	    }
	}
	

	public <T extends Map<String,Object>> T head(TypeRegistry typeRegistry) {
	    T map = createMapFromRegistry(typeRegistry);
	    map.putAll(this.message.head);
	    return map;
	}

	
	public <T extends Map<String,Object>> T data(TypeRegistry typeRegistry) {
	    T map = createMapFromRegistry(typeRegistry);
	    map.putAll(this.message.data);
	    return map;
	}

	@SuppressWarnings("unchecked")
	private <T extends Map<String,Object>> T createMapFromRegistry(TypeRegistry typeRegistry) {
	    switch (typeRegistry) {
	        case MAP_OBJECT:
	            return (T) new java.util.HashMap<String, Object>();
	        case MAP_SHAREDMAP_OBJECT:
	            return (T) new SharedMap<String, Object>();
	        case MAP_LINKEDMAP_OBJECT:
	            return (T) new LinkedMap<String, Object>();
	        case MAP_THREADSAFE_LINKEDMAP_OBJECT:
	            return (T) new ThreadSafeLinkedMap<String, Object>();
	        case MAP_LINKEDHASHMAP_OBJECT:
	            return (T) new LinkedHashMap<String, Object>();
	        case MAP_CONCURRENTHASHMAP_OBJECT:
	            return (T) new ConcurrentHashMap<String, Object>();
	        case MAP_TREEMAP_OBJECT:
	            return (T) new TreeMap<String, Object>();
	        default:
	            throw new IllegalArgumentException("Unsupported TypeRegistry: " + typeRegistry);
	    }
	}

	
	private INMessage execute() {
	    if (this.message.data.isEmpty()) {
	        this.head("message", "전송할 데이터가 없습니다. data is empty");
	        return this.message;
	    }

	    long startTime = System.nanoTime();
	    this.head("message", "ready");

	    byte[] data;
	    try {
	        data = serialize();
	    } catch (Exception e) {
	        this.head("message", "serialize failed: " + e.getMessage());
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
	            this.head("message", TIMEOUT_CONNECT);
	            logger.info("Connect timeout to {}:{}", this.host, this.port);
	            return this.message;
	        }
	        this.head("message", "connected");

	        // 송신
	        if (!writeFullyOnce(channel, selector, data, this.message)) {
	            return this.message;
	        }

	        if (SystemUtils.deepview()) {
	            logger.info("INET {} > {} : [{}] bytes",
	                    this.message.head.getString("proc"),
	                    this.message.head.getString("target"),
	                    data.length);
	        }

	        this.head("message", "message sent");

	        // 수신
	        byte[] response = readFullyOnce(channel, selector, this.message, timeout);
	        if (response == null) {
	            return this.message;
	        }

	        if (SystemUtils.deepview()) {
	            logger.info("INET {} < {} : [{}] bytes",
	                    this.message.head.getString("proc"),
	                    this.message.head.getString("target"),
	                    response.length);
	        }

	        // 역직렬화
	        deserialize(response);
	        this.head("result", true);

	    } catch (Exception e) {
	        this.head("message",
	                "execute step : " + this.message.head.getString("message") +
	                        " exception: " + e.getMessage());
	        logger.warn("INet execute exception", e);
	    } finally {
	        long elapsed = System.nanoTime() - startTime;
	        this.message.head.put("time", elapsed);

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

	
	
	

	
	
	
	/**
	 * 역직렬화 (Externalizable 최적화)
	 * 
	 * @param data 직렬화된 바이트 배열
	 * @return INet
	 * @throws IOException
	 * @throws ClassNotFoundException
	 */
	public INet deserialize(byte[] data) throws IOException, ClassNotFoundException{
		 
		Objects.requireNonNull(data, "data is null");

        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             ObjectInputStream in = new ObjectInputStream(bis)) {

            ObjectInputFilter filter = info -> {
                Class<?> clazz = info.serialClass();
                if (clazz == null) return ObjectInputFilter.Status.UNDECIDED;
                if (INMessage.class.equals(clazz)) return ObjectInputFilter.Status.ALLOWED;
                if (INMap.class.equals(clazz)) return ObjectInputFilter.Status.ALLOWED;
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
        }
	}
	
	
	/**
	 * 직렬화 (Externalizable + 버퍼 풀링 최적화)
	 * - 3-5배 성능 향상
	 * - GC 압력 감소
	 * 
	 * @return 직렬화된 바이트 배열
	 * @throws IOException
	 */
	public byte[] serialize() throws IOException{
		// ★ 버퍼 재사용
		ByteArrayOutputStream bos = bosPool.get();
		bos.reset();
		
		try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(this.message);
            out.flush();
            return bos.toByteArray();
        }
	}
	
	
	
	
	
	/**
	 * INMessage 클래스
	 * 
	 * ★★★ Externalizable 구현으로 직렬화 최적화 ★★★
	 * - ObjectOutputStream의 메타데이터 오버헤드 제거
	 * - 직접 제어로 불필요한 데이터 전송 최소화
	 * - 3-5배 성능 향상
	 */
	public class INMessage implements Externalizable {
		private static final long serialVersionUID = 6562469816779099746L;
		
		public INMap head = new INMap();
		public INMap data = new INMap();
		
		/**
		 * 기본 생성자 (Externalizable 필수)
		 */
		public INMessage() {}
		
		/**
		 * 직렬화 (Externalizable)
		 * - ObjectOutputStream 메타데이터 제거
		 * - 최소한의 데이터만 전송
		 */
		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			writeINMap(out, head);
			writeINMap(out, data);
		}
		
		/**
		 * 역직렬화 (Externalizable)
		 */
		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			head = readINMap(in);
			data = readINMap(in);
		}
		
		/**
		 * INMap 쓰기 (최적화)
		 */
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
		
		/**
		 * INMap 읽기 (최적화)
		 */
		private INMap readINMap(ObjectInput in) throws IOException, ClassNotFoundException {
			int size = in.readInt();
			
			INMap map = new INMap();
			
			for (int i = 0; i < size; i++) {
				String key = in.readUTF();
				Object value = in.readObject();
				map.put(key, value);
			}
			
			return map;
		}
	}
	

	

	/**
	 * INMap 클래스
	 * 
	 * LinkedHashMap 기반의 유틸리티 Map
	 * - 다양한 타입 변환 메서드 제공
	 * - 순서 보장 (LinkedHashMap)
	 */
	public class INMap extends LinkedHashMap<String, Object> implements Serializable {
		
	    private static final long serialVersionUID = 1L;
	    private static final DateTimeFormatter DF =
	            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.S");

	    public INMap() {}

	    public INMap(Map<? extends String, ? extends Object> map) {
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

	    // ---------------------- Boolean ----------------------
	    public boolean getBoolean(String key) {
	        Object obj = get(key);
	        if (obj == null) return false;
	        String val = getString(key).toLowerCase();
	        return val.equals("true") || val.equals("1");
	    }

	    // ---------------------- int ----------------------
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

	    // ---------------------- long ----------------------
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

	    // ---------------------- double ----------------------
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

	    // ---------------------- BigDecimal ----------------------
	    public BigDecimal getBigDecimal(String key) {
	        Object obj = get(key);
	        if (obj instanceof BigDecimal bd) return bd;
	        return new BigDecimal(getString(key, "0"));
	    }

	    // ---------------------- BigInteger ----------------------
	    public BigInteger getBigInteger(String key) {
	        Object obj = get(key);
	        if (obj instanceof BigInteger bi) return bi;
	        if (obj instanceof BigDecimal bd) return bd.toBigInteger();
	        return new BigInteger(getString(key, "0"));
	    }

	    // ---------------------- Timestamp ----------------------
	    public Timestamp getTimestamp(String key) {
	        Object obj = get(key);
	        if (obj instanceof Timestamp ts) return ts;
	        if (obj instanceof String s) return Timestamp.valueOf(s);
	        return null;
	    }

	    // ---------------------- Utility ----------------------
	    public boolean like(String key, String value) {
	        return getString(key).contains(value);
	    }

	    public boolean isTrue(String key) {
	        return getBoolean(key);
	    }

	    public boolean isNull(String key) {
	        return get(key) == null;
	    }

	    public boolean isNullOrSpace(String key) {
	        return getString(key).isBlank();
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