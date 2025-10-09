package kr.tx24.inter.codec;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;


/**
 * INet 프로토콜 디코더
 * 
 * [최적화된 설정]
 * - Water Mark: 512KB / 2MB
 * - 일반 패킷: 10KB 이하 (99%) → 백프레셔 없음
 * - 대용량 패킷: 10MB (1%) → 5회 백프레셔 (허용)
 * - 최대 크기: 100MB (예외적)
 * 
 * [특징]
 * - ByteToMessageDecoder 사용 (Netty 자동 cumulation 관리)
 * - 스레드 안전 (각 연결마다 별도 인스턴스)
 * - 메모리 효율적 (대부분 작은 패킷 처리)
 * - 대용량 패킷도 안정적 처리
 */
public class INetDecoder extends ByteToMessageDecoder {

	private static final Logger logger = LoggerFactory.getLogger(INetDecoder.class);

	private static final int LENGTH_INDICATOR	= 4;					// 패킷 길이 필드 크기 (int = 4bytes)
	private static final int MIN_VALID_PACKET	= 8;					// 최소 유효 패킷 (Health Check 제외)
	private static final int MAX_PACKET_SIZE	= 100 * 1024 * 1024;	// 최대 100MB

	private long totalPackets	= 0;	// 총 패킷 수
	private long largePackets	= 0;	// 대용량 패킷 수 (1MB 이상)
	private long lastLogTime	= 0;	// 마지막 로그 시간

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
		
		while (in.readableBytes() >= LENGTH_INDICATOR) {						// 여러 패킷 처리
			
			in.markReaderIndex();												// 현재 readerIndex 저장 (롤백용)
			
			int packetLength = in.readInt();									// 패킷 길이 읽기 (4 bytes)
			
			if (packetLength < MIN_VALID_PACKET) {								// L4/Nginx Health Check 처리
				handleHealthCheckPacket(in, packetLength);
				continue;
			}
			
			if (packetLength > MAX_PACKET_SIZE) {								// 비정상적으로 큰 패킷 감지
				logger.error("Packet too large: {} bytes (max: {}), closing channel", packetLength, MAX_PACKET_SIZE);
				throw new TooLongFrameException("Packet size " + packetLength + " exceeds maximum of " + MAX_PACKET_SIZE);
			}
			
			if (in.readableBytes() < packetLength) {							// 전체 패킷이 도착하지 않음
				in.resetReaderIndex();											// readerIndex를 길이 필드 이전으로 복구
				
				if (packetLength > 1024 * 1024 && SystemUtils.deepview()) {		// 대용량 패킷 수신 진행률 로깅
					logReceivingProgress(packetLength, in.readableBytes());
				}
				
				return;															// decode() 종료 → Netty가 다음 데이터 도착 시 다시 호출
			}
			
			byte[] data = new byte[packetLength];								// 패킷 완성됨 - 데이터 읽기
			in.readBytes(data);
			
			totalPackets++;														// 통계 수집
			if (packetLength > 1024 * 1024) {
				largePackets++;
			}
			
			logReceivedPacket(data, packetLength);								// 로깅
			
			try {
				INet inet = new INet().deserialize(data);						// INet 역직렬화
				out.add(inet);
				
				if (SystemUtils.deepview()) {
					logger.debug("Decoded INet packet successfully, length={}", packetLength);
				}
				
			} catch (Exception e) {
				logger.error("Failed to deserialize INet packet, length={}", packetLength, e);
				ctx.fireExceptionCaught(e);										// 역직렬화 실패 시 예외 전파
				ctx.close();													// 손상된 패킷이므로 연결 종료
				return;
			}
		}
	}
	
	/**
	 * Health Check 패킷 처리
	 * 
	 * [처리 대상]
	 * - packetLength < 8: 정상적인 INet 프로토콜이 아님
	 * - 모니터링 도구가 보낸 더미 데이터
	 * 
	 * [처리 전략]
	 * 1. packetLength <= 0: 쓰레기 데이터, 무시
	 * 2. packetLength > 0 && 데이터 도착: 건너뛰기
	 * 3. packetLength > 0 && 데이터 부족: readerIndex 복구 후 대기
	 * 
	 * [참고]
	 * - 대부분의 L4/Nginx Health Check는 데이터를 보내지 않음
	 * - 따라서 이 메서드는 거의 호출되지 않음
	 * - 호출되더라도 빠르게 처리하고 다음 패킷으로 진행
	 */
	private void handleHealthCheckPacket(ByteBuf in, int packetLength) {
		if (SystemUtils.deepview()) {
			logger.info("Health check packet detected, length={}", packetLength);
		}
		
		if (packetLength <= 0) {												// 음수 또는 0: 쓰레기 데이터
			logger.debug("Invalid packet length: {}, skipping", packetLength);
			return;																// 이미 4 bytes(length 필드)는 소비했으므로 리턴
		}
		
		if (in.readableBytes() >= packetLength) {								// 패킷 데이터가 완전히 도착함
			in.skipBytes(packetLength);											// 건너뛰기
			if (SystemUtils.deepview()) {
				logger.debug("Skipped {} bytes of health check data", packetLength);
			}
		} else {																// 데이터가 부족함
			in.resetReaderIndex();												// readerIndex 복구하고 대기
			if (SystemUtils.deepview()) {
				logger.debug("Waiting for {} bytes, currently {} bytes available", 
						packetLength, in.readableBytes());
			}
		}
	}
	
	private void logReceivingProgress(int total, int received) {
		long now = System.currentTimeMillis();
		
		if (now - lastLogTime > 1000) {											// 1초마다 로깅
			double progress		= (received * 100.0) / total;
			double receivedMB	= received / (1024.0 * 1024.0);
			double totalMB		= total / (1024.0 * 1024.0);
			
			logger.info("Receiving large packet: {:.1f}% ({:.2f}MB / {:.2f}MB)", progress, receivedMB, totalMB);
			
			lastLogTime = now;
		}
	}
	
	private void logReceivedPacket(byte[] data, int packetLength) {
		if (packetLength > 1024 * 1024) {										// 1MB 이상
			double sizeMB = packetLength / (1024.0 * 1024.0);
			logger.info("Infra recv: [LARGE PACKET], length={:.2f}MB", sizeMB);
		} else if (SystemUtils.deepview() || packetLength > 10240) {			// 10KB 이상이거나 디버그 모드
			String preview = data.length > 55
					? CommonUtils.toString(data, 0, 55, StandardCharsets.UTF_8)
					: CommonUtils.toString(data, StandardCharsets.UTF_8);
			logger.info("Infra recv: [{}...], length={}", preview, packetLength);
		}
		
		if (totalPackets % 1000 == 0 && totalPackets > 0) {					// 주기적 통계 출력 (1000개마다)
			double largeRatio = (largePackets * 100.0) / totalPackets;
			logger.info("Decoder stats: total={}, large={}({:.2f}%)", totalPackets, largePackets, largeRatio);
			
			if (largeRatio > 5.0) {												// 대용량 패킷 비율이 5% 이상이면 경고
				logger.warn("Large packet ratio is high: {:.2f}% - Consider increasing water mark", largeRatio);
			}
		}
	}
	
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		
		if (cause instanceof TooLongFrameException) {
			logger.error("Frame too long - closing channel", cause);
		} else {
			logger.error("Decoder exception", cause);
		}
		
		super.exceptionCaught(ctx, cause);										// 예외를 다음 핸들러로 전파
	}
	
	@Override
	protected void handlerRemoved0(ChannelHandlerContext ctx) throws Exception {
		if (SystemUtils.deepview()) {
			logger.debug("INetDecoder removed, stats: total={}, large={}", totalPackets, largePackets);
		}
	}
}
