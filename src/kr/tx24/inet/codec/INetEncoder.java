package kr.tx24.inet.codec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.SystemUtils;

public class INetEncoder extends MessageToByteEncoder<INet> {

	private static final Logger logger = LoggerFactory.getLogger(INetEncoder.class);
	

	@Override
	protected void encode(ChannelHandlerContext ctx, INet inet, ByteBuf out) throws Exception {
		
		try {
			byte[] data 	= inet.serialize();
			
			// 프로토콜 포맷: [4 bytes: 길이] + [N bytes: 데이터]
			out.writeInt(data.length);
			out.writeBytes(data);
			
			if(SystemUtils.deepview()) {
				logger.info("inet response length={}", data.length);
			}
			
		}catch(Exception e) {
			 logger.error("Encoding failed for INet object: {}", inet, e);
			 throw e;
		}
	}
	


}
