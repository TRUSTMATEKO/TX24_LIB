package kr.tx24.inet.example;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.inet.mapper.Controller;
import kr.tx24.inet.mapper.Data;
import kr.tx24.inet.mapper.Head;
import kr.tx24.inet.mapper.Autowired;
import kr.tx24.inet.mapper.Route;
import kr.tx24.lib.inter.INet;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.mapper.JacksonUtils;

@Controller(target = "/inet")

public class HelloCtr {
	private static final Logger logger = LoggerFactory.getLogger(HelloCtr.class);
	
	
	@Autowired
	private INet inet;
	
	@Autowired
	private ChannelHandlerContext ctx;
	
	
	@Route(target ="/")
	public INet init() {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired ctx : \nctx : {}", ctx.name());
		
		return inet;
	}
	
	
	@Route(target ="/head")
	public INet head(@Head LinkedMap<String,Object> head) {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired head : \nhead : {}", new JacksonUtils().toJson(head));
		
		INet resInet = new INet();
		resInet.head(head);
		return resInet;
	}
	
	
	@Route(target ="/data")
	public INet data(@Data LinkedMap<String,Object> data) {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired head : \ndata : {}", new JacksonUtils().toJson(data));
		
		INet resInet = new INet();
		resInet.head(data);
		return resInet;
	}
	
	
	@Route(target ="/ctx")
	public INet ctx(@Data LinkedMap<String,Object> data) {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired head : \ndata : {}", new JacksonUtils().toJson(data));
		
		INet resInet = new INet();
		resInet.head(data);
		return resInet;
	}
	
	
	
	
	
}
