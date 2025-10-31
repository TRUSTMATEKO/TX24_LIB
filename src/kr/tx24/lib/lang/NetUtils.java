package kr.tx24.lib.lang;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 
 */
public class NetUtils {

	
	private static final Logger logger = LoggerFactory.getLogger(NetUtils.class);
	
	// 최대 수신 가능한 데이터 크기 (기본 10MB)
	private static final int DEFAULT_MAX_SIZE = 10 * 1024 * 1024;
	private static final int DEFAULT_BUFFER_SIZE = 2048;
	private static final int DEFAULT_LOG_TEXT_LENGTH= 100;
	private static final int DEFAULT_TIMEOUT = 30000;
	
	
	
    private int maxReceiveSize;
    private int socketTimeout;
    private int bufferSize;
    
    
    // 기본 Socket 타임아웃 (30초)
    
	
	
    public NetUtils() {
    	this(DEFAULT_MAX_SIZE, DEFAULT_TIMEOUT, DEFAULT_BUFFER_SIZE);
    }
    
    /**
     * 설정 가능한 생성자
     * @param maxReceiveSize 최대 수신 크기 (바이트)
     * @param socketTimeout Socket 타임아웃 (밀리초)
     * @param bufferSize 버퍼 크기 (바이트)
     */
    public NetUtils(int maxReceiveSize, int socketTimeout, int bufferSize) {
        this.maxReceiveSize = maxReceiveSize;
        this.socketTimeout = socketTimeout;
        this.bufferSize = bufferSize;
    }
    
    
    /**
     * Socket 연결 상태를 확인합니다.
     */
    public static boolean isConnected(Socket socket) {
        return socket != null && 
               socket.isConnected() && 
               !socket.isClosed() && 
               !socket.isInputShutdown() && 
               !socket.isOutputShutdown();
    }

    /**
     * Socket이 읽기 가능한 상태인지 확인합니다.
     */
    public static boolean isReadable(Socket socket) {
        return socket != null && 
               socket.isConnected() && 
               !socket.isClosed() && 
               !socket.isInputShutdown();
    }

    /**
     * Socket이 쓰기 가능한 상태인지 확인합니다.
     */
    public static boolean isWritable(Socket socket) {
        return socket != null && 
               socket.isConnected() && 
               !socket.isClosed() && 
               !socket.isOutputShutdown();
    }
    
    /**
     * Socket을 안전하게 종료합니다.
     * 양방향 shutdown 후 close를 수행합니다.
     */
    public static void closeGracefully(Socket socket) {
        if (socket == null) return;
        
        try {
            // 먼저 output을 shutdown
            if (!socket.isOutputShutdown()) {
                socket.shutdownOutput();
            }
            
            // 짧은 대기 시간
            Thread.sleep(100);
            
            // input을 shutdown
            if (!socket.isInputShutdown()) {
                socket.shutdownInput();
            }
        } catch (Exception e) {
            // shutdown 실패는 무시
        }
        
        try {
            socket.close();
        } catch (IOException e) {
            // close 실패도 무시
        }
    }
    
    
    /**
     * L4 Health Check: TCP 연결 가능 여부를 확인합니다.
     * 대부분의 L4 로드밸런서(AWS ELB, HAProxy, NGINX)가 사용하는 방식입니다.
     * 
     * @param host 대상 호스트
     * @param port 대상 포트
     * @param timeoutMs 연결 타임아웃 (밀리초)
     * @return 연결 가능하면 true, 불가능하면 false
     */
    public static boolean isAlive(String host, int port, int timeoutMs) {
        Socket socket = null;
        try {
            socket = new Socket();
            // TCP 3-way handshake만 수행
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    
    /**
     * L4 Health Check: 기본 타임아웃 1초 사용
     * 
     * @param host 대상 호스트
     * @param port 대상 포트
     * @return 연결 가능하면 true, 불가능하면 false
     */
    public static boolean isAlive(String host, int port) {
        return isAlive(host, port, 1000);
    }
    
    
    

    /**
     * Socket으로부터 원격 IP 주소를 가져옵니다.
     * @param socket Socket 객체
     * @return IP 주소 문자열
     */
    public static String getRemoteAddress(Socket socket) {
        if (socket == null || socket.getInetAddress() == null) {
            return "";
        }
        return socket.getInetAddress().getHostAddress();
    }
	
	
    /**
     * 스트림으로부터 사용 가능한 모든 데이터를 수신합니다.
     * 스트림이 닫히거나(-1 반환) 더 이상 읽을 데이터가 없을 때까지 읽습니다.
     * 
     * 주의: 이 메서드는 상대방이 스트림을 닫거나 Socket을 닫을 때까지 대기합니다.
     * HTTP 응답 등 Content-Length가 명시되지 않은 경우에 사용하세요.
     * 
     * @param inputStream 입력 스트림
     * @return 수신된 데이터
     * @throws IOException 입출력 예외 발생 시
     * @throws IllegalArgumentException 수신 데이터가 최대 크기를 초과할 경우
     */
    public byte[] recv(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        try (ByteArrayOutputStream bout = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[bufferSize];
            int totalRead = 0;

            while (true) {
                int bytesRead = inputStream.read(buffer);
                
                if (bytesRead == -1) {
                    // 스트림 종료
                    break;
                }
                
                if (bytesRead > 0) {
                    totalRead += bytesRead;
                    
                    // 최대 크기 체크
                    if (totalRead > maxReceiveSize) {
                        throw new IOException(
                            String.format("Received data exceeds maximum size: %d bytes (max: %d)", 
                                totalRead, maxReceiveSize)
                        );
                    }
                    
                    bout.write(buffer, 0, bytesRead);
                }
            }
            
            byte[] data = bout.toByteArray();
            if(SystemUtils.deepview()) {
            	logger.info("recv  -> [{}],[{}]",data.length, CommonUtils.toString(data,0,Math.min(data.length, DEFAULT_LOG_TEXT_LENGTH)));
            }
            
            return data;
        }
    }

    /**
     * 지정된 크기만큼 데이터를 수신합니다.
     * 정확히 size 바이트를 읽을 때까지 블로킹됩니다.
     * 
     * @param inputStream 입력 스트림
     * @param size 수신할 바이트 크기
     * @return 수신된 데이터
     * @throws IOException 입출력 예외 발생 시
     * @throws IllegalArgumentException size가 유효하지 않을 경우
     */
    public byte[] recv(InputStream inputStream, int size) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }
        if (size > maxReceiveSize) {
            throw new IllegalArgumentException(
                String.format("Requested size %d exceeds maximum %d", size, maxReceiveSize)
            );
        }

        byte[] data = new byte[size];
        int totalRead = 0;

        while (totalRead < size) {
            int bytesRead = inputStream.read(data, totalRead, size - totalRead);
            
            if (bytesRead == -1) {
                throw new IOException(
                    String.format("Stream closed before reading complete data. Expected: %d, Read: %d", 
                        size, totalRead)
                );
            }
            
            totalRead += bytesRead;
        }
        
        
        
        if(SystemUtils.deepview()) {
        	logger.info("recv  -> [{}],[{}]",data.length, CommonUtils.toString(data,0,Math.min(data.length, DEFAULT_LOG_TEXT_LENGTH)));
        }

        return data;
    }
    
    
    /**
     * Socket으로부터 사용 가능한 모든 데이터를 수신합니다.
     * Socket의 타임아웃을 설정하고 데이터를 읽습니다.
     * 
     * @param socket Socket 객체
     * @return 수신된 데이터
     * @throws IOException 입출력 예외 발생 시
     */
    public byte[] recv(Socket socket) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
        }

        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(socketTimeout);
            return recv(socket.getInputStream());
        } catch (SocketTimeoutException e) {
            throw new IOException("Socket read timeout after " + socketTimeout + "ms", e);
        } finally {
            // 원래 타임아웃 복원
            try {
                socket.setSoTimeout(originalTimeout);
            } catch (IOException e) {
                // 타임아웃 복원 실패는 무시
            }
        }
    }

    /**
     * Socket으로부터 지정된 크기만큼 데이터를 수신합니다.
     * 
     * @param socket Socket 객체
     * @param size 수신할 바이트 크기
     * @return 수신된 데이터
     * @throws IOException 입출력 예외 발생 시
     */
    public byte[] recv(Socket socket, int size) throws IOException {
        if (socket == null) {
            throw new IllegalArgumentException("Socket cannot be null");
        }

        int originalTimeout = socket.getSoTimeout();
        try {
            socket.setSoTimeout(socketTimeout);
            return recv(socket.getInputStream(), size);
        } catch (SocketTimeoutException e) {
            throw new IOException("Socket read timeout after " + socketTimeout + "ms", e);
        } finally {
            // 원래 타임아웃 복원
            try {
                socket.setSoTimeout(originalTimeout);
            } catch (IOException e) {
                // 타임아웃 복원 실패는 무시
            }
        }
    }
    
    
    /**
     * 4바이트 길이 정보를 먼저 읽고, 그 길이만큼 데이터를 수신합니다.
     * 
     * 프로토콜 구조:
     * [4바이트 길이 필드][실제 데이터(길이 필드 값 만큼)]
     * 
     * @param inputStream 입력 스트림
     * @param bigEndian true이면 Big-Endian, false이면 Little-Endian
     * @return 수신된 데이터 (길이 필드는 제외)
     * @throws IOException 입출력 예외 발생 시
     */
    public byte[] recvBinaryPrefixed(InputStream inputStream, boolean bigEndian) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        // 1. 4바이트 길이 필드 읽기
        byte[] lengthBytes = recv(inputStream, 4);
        
        // 2. 길이를 숫자로 변환
        int dataLength = CommonUtils.bytesToInt(lengthBytes, bigEndian);
        
        // 3. 길이 검증
        if (dataLength < 0) {
            throw new IOException("Invalid data length: " + dataLength + " (negative value)");
        }
        
        if (dataLength > maxReceiveSize) {
            throw new IOException(
                String.format("Data length %d exceeds maximum size %d", 
                    dataLength, maxReceiveSize)
            );
        }
        
        // 4. 실제 데이터 읽기
        return recv(inputStream, dataLength);
    }
    
    
    /**
     * 4바이트 텍스트 길이 정보를 먼저 읽고, 그 길이만큼 데이터를 수신합니다.
     * 길이는 ASCII 문자열로 표현된 10진수 숫자입니다.
     * 
     * 프로토콜 구조:
     * [4바이트 텍스트 길이][실제 데이터(길이 값 만큼)]
     * 예: "0004ABCD" -> "0004"를 읽고 4로 변환 -> "ABCD"(4바이트)를 읽음
     * 
     * @param inputStream 입력 스트림
     * @param length 데이터의 길이 지시자 값
     * @param lengthInclude 데이터의 길이 문자열이 길이 지시자값 포함(4바이트 값-length) 인지 아닌지 
     * @return 수신된 데이터 (길이 필드는 제외)
     * @throws IOException 입출력 예외 발생 시
     * @throws NumberFormatException 길이 필드가 숫자가 아닐 경우
     */
    public byte[] recvTextPrefixed(InputStream inputStream,int lengthFieldSize, boolean includeHeaderInLength) throws IOException {
        if (inputStream == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }

        // 1. 4바이트 텍스트 길이 필드 읽기
        byte[] lengthBytes = recv(inputStream, lengthFieldSize);
        
        int dataLength = CommonUtils.toInt(lengthBytes);
        if(includeHeaderInLength) {
        	dataLength = dataLength-lengthFieldSize;
        }
        
        // 4. 길이 검증
        if (dataLength < 0) {
            throw new IOException("Invalid data length: " + dataLength + " (negative value)");
        }
        
        if (dataLength > maxReceiveSize) {
            throw new IOException(
                String.format("Data length %d exceeds maximum size %d", 
                    dataLength, maxReceiveSize)
            );
        }
        
        // 5. 실제 데이터 읽기
        return recv(inputStream, dataLength);
    }

    
    
    
    public void write(OutputStream outputStream, byte[] data) throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        try {
            outputStream.write(data);
            outputStream.flush();
            
            if(SystemUtils.deepview()) {
            	logger.info("write -> [{}],[{}]",data.length, CommonUtils.toString(data,0,Math.min(data.length, DEFAULT_LOG_TEXT_LENGTH)));
            }
            
        } catch (IOException e) {
            throw new IOException("Failed to write data: " + e.getMessage(), e);
        }
    }

    /**
     * 4바이트 바이너리 길이 접두사와 함께 데이터를 전송합니다.
     * 
     * 프로토콜 구조:
     * [4바이트 바이너리 길이][실제 데이터]
     * 
     * @param outputStream 출력 스트림
     * @param data 전송할 데이터
     * @param bigEndian true이면 Big-Endian, false이면 Little-Endian
     * @throws IOException 입출력 예외 발생 시
     * @throws IllegalArgumentException 파라미터가 유효하지 않을 경우
     */
    public void writeBinaryPrefixed(OutputStream outputStream, byte[] data, boolean bigEndian) 
            throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        try {
            // 1. 길이 전송 (4바이트 바이너리)
            byte[] lengthBytes = CommonUtils.intToBytes(data.length, bigEndian);
            outputStream.write(lengthBytes);
            
            // 2. 데이터 전송
            outputStream.write(data);
            
            // 3. Flush
            outputStream.flush();
            
            if(SystemUtils.deepview()) {
            	logger.info("write -> [{}],[{}]",CommonUtils.toString(lengthBytes), CommonUtils.toString(data,0,Math.min(data.length, DEFAULT_LOG_TEXT_LENGTH)));
            }
            
        } catch (IOException e) {
            throw new IOException("Failed to write binary prefixed data: " + e.getMessage(), e);
        }
    }

  

    /**
     * 4바이트 텍스트 길이 접두사와 함께 데이터를 전송합니다.
     * 
     * 프로토콜 구조:
     * [4바이트 텍스트 길이][실제 데이터]
     * 예: "0004ABCD"
     * 
     * @param outputStream 출력 스트림
     * @param data 전송할 데이터
     * @param includeHeaderInLength true이면 길이 값이 헤더(4바이트)를 포함
     * @throws IOException 입출력 예외 발생 시
     * @throws IllegalArgumentException 파라미터가 유효하지 않거나 데이터 크기가 범위를 벗어날 경우
     */
    public void writeTextPrefixed(OutputStream outputStream, byte[] data, boolean includeHeaderInLength) 
            throws IOException {
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        if (data == null) {
            throw new IllegalArgumentException("Data cannot be null");
        }

        // 길이 계산
        int lengthValue = includeHeaderInLength ? data.length + 4 : data.length;
        
        // 길이 범위 검증 (4자리 숫자: 0000 ~ 9999)
        if (lengthValue < 0 || lengthValue > 9999) {
            throw new IllegalArgumentException(
                String.format("Data length %d is out of range (0-9999) for text prefix", lengthValue)
            );
        }

        try {
            // 1. 길이 전송 (4바이트 텍스트)
            byte[] lengthBytes = CommonUtils.paddingZeroToByte(lengthValue,4);
            outputStream.write(lengthBytes);
            
            // 2. 데이터 전송
            outputStream.write(data);
            
            // 3. Flush
            outputStream.flush();
            
            if(SystemUtils.deepview()) {
            	logger.info("write -> [{}],[{}]",CommonUtils.toString(lengthBytes), CommonUtils.toString(data,0,Math.min(data.length, DEFAULT_LOG_TEXT_LENGTH)));
            }
            
        } catch (IOException e) {
            throw new IOException("Failed to write text prefixed data: " + e.getMessage(), e);
        }
    }

    
    
    
    


}
