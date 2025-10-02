package kr.tx24.inter.codec;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

public class INetDecoder extends MessageToMessageDecoder<ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(INetDecoder.class);

    // 패킷 길이를 나타내는 바이트 수 (int = 4bytes) 
    private static final int LENGTH_INDICATOR = 4;

    // 최소 유효 패킷 길이: L4/Health Check 패킷 제외 기준 
    private static final int MIN_VALID_PACKET = 8;
    
    //최대 방지 
    private static final int MAX_PACKET_SIZE = 100 * 1024 * 1024; 

    //상태 유지
    private ByteBuf cumulation = null;
    private int packetLength = 0;

    
    
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {

        while (in.isReadable()) {

            // 아직 패킷 길이를 읽지 않은 상태
            if (packetLength == 0) {
                if (in.readableBytes() < LENGTH_INDICATOR) {
                    return; // 길이 정보 부족 → 다음번 호출에서 처리
                }

                packetLength = in.readInt();

                // 너무 짧으면 → Health Check 패킷으로 간주하고 무시
                if (packetLength < MIN_VALID_PACKET) {
                    if (SystemUtils.deepview()) {
                        logger.info("Ignored small packet, length={}", packetLength);
                    }
                    if (in.readableBytes() >= packetLength) {
                        in.skipBytes(packetLength);
                    } else {
                        in.resetReaderIndex();
                        packetLength = 0;
                        return;
                    }
                    packetLength = 0;
                    continue;
                }

                // 너무 크면 → 비정상 패킷으로 보고 채널 종료
                if (packetLength > MAX_PACKET_SIZE) {
                    logger.error("Packet too large: {} bytes (max {}), closing channel", 
                                 packetLength, MAX_PACKET_SIZE);
                    ctx.close();
                    return;
                }

                // 새 패킷 버퍼 할당
                cumulation = ctx.alloc().buffer(packetLength, packetLength);
            }

            // 현재 수신 가능한 만큼 읽어서 누적
            int toRead = Math.min(in.readableBytes(), packetLength - cumulation.readableBytes());
            cumulation.writeBytes(in, toRead);

            if (SystemUtils.deepview()) {
                logger.info("Receiving packet: total={}, received={}", 
                            packetLength, cumulation.readableBytes());
            }

            // 패킷 완성됨
            if (cumulation.readableBytes() == packetLength) {
                byte[] data = new byte[packetLength];
                cumulation.readBytes(data);
                cumulation.release();
                cumulation = null;

                // 로그 (미리보기)
                String preview = data.length > 55
                        ? CommonUtils.toString(data, 0, 55, StandardCharsets.UTF_8)
                        : CommonUtils.toString(data, StandardCharsets.UTF_8);

                logger.info("Infra recv: [{}], length={}", preview, data.length);

                // INet 역직렬화 후 out에 전달
                out.add(new INet().deserialize(data));

                // 상태 초기화 → 다음 패킷 준비
                packetLength = 0;
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        // 누적 중인 버퍼 해제 (메모리 릭 방지)
        if (cumulation != null) {
            cumulation.release();
            cumulation = null;
        }
        super.handlerRemoved(ctx);
    }
}
