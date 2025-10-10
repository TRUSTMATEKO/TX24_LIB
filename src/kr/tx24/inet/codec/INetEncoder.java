package kr.tx24.inet.codec;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.stream.ChunkedStream;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.lang.CommonUtils;

public class INetEncoder extends MessageToByteEncoder<INet> {

	private static final Logger logger = LoggerFactory.getLogger(INetEncoder.class);
	private static final int CHUNK_THRESHOLD = 128 * 1024;
	

	@Override
	protected void encode(ChannelHandlerContext ctx, INet inet, ByteBuf out) throws Exception {
		
		try {
			byte[] data 	= inet.serialize();
			
			
			 if (data.length <= CHUNK_THRESHOLD) {
				out.writeInt(data.length);
				out.writeBytes(data);
			 }else {
				 //아래와 같이 ChunkedWriteHandler 가 반드시 되어 있어야 한다.
				 //pipeline.addLast("chunkedWriter", new ChunkedWriteHandler());
				 //pipeline.addLast("inetEncoder", new INetEncoder());
				 
				 ByteBuf header = Unpooled.buffer(4);
		         header.writeInt(data.length);
		         ctx.write(header);
		         
		         
		         ctx.writeAndFlush(new ChunkedStream(new ByteArrayInputStream(data)));
		         logger.info("Infra send (chunked): length={} bytes", data.length);
		         
		         return; 
			 }
			
			 // 수신 내용 미리보기
			 String preview 	= data.length > 55 ? CommonUtils.toString(data, 0, 55, StandardCharsets.UTF_8) : CommonUtils.toString(data, StandardCharsets.UTF_8);
			 logger.info("Infra send: [{}], length={}", preview, data.length);
			
		}catch(Exception e) {
			 logger.error("Encoding failed for INet object: {}", inet, e);
			 throw e;
		}
	}


}
