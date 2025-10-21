package kr.tx24.api.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;

public class RouteHandler extends SimpleChannelInboundHandler<FullHttpRequest>{
	
	private static final Logger logger = LoggerFactory.getLogger(RouteHandler.class);
	
	this.trxId = IDUtils.getUnique("TRX");

}
