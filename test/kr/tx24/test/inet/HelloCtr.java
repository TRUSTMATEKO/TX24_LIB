package kr.tx24.test.inet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.channel.ChannelHandlerContext;
import kr.tx24.inet.mapper.Autowired;
import kr.tx24.inet.mapper.Controller;
import kr.tx24.inet.mapper.Data;
import kr.tx24.inet.mapper.Head;
import kr.tx24.inet.mapper.Route;
import kr.tx24.inet.util.INetRespUtils;
import kr.tx24.lib.inter.INetB;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.mapper.JacksonUtils;

@Controller(target = "/inet")

public class HelloCtr {
	private static final Logger logger = LoggerFactory.getLogger(HelloCtr.class);
	
	
	@Autowired
	private INetB inet;
	
	@Autowired
	private ChannelHandlerContext ctx;
	
	
	/*
	 * 생성자는 반드시 Autowired 를 하거나 또는 default 생성자만 허용을 한다.
	 * 그 외의 생성자는 무시된다.
	 * @Autowired가 있으면 → 그 생성자 사용 , 없으면 → 기본 생성자 사용 , 둘 다 없으면 → 에러
	 */
	@Autowired
	public HelloCtr(ChannelHandlerContext ctx, INetB inet) {
		this.ctx = ctx;
		this.inet = inet;
	}
	
	
	@Route(target ="/")
	public INetB init() {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired ctx : \nctx : {}", ctx.name());
		
		return inet;
	}
	
	
	@Route(target ="/head")
	public INetB head(@Head LinkedMap<String,Object> head) {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired head : \nhead : {}", new JacksonUtils().toJson(head));
		
		INetB resInet = new INetB();
		resInet.head(head);
		return resInet;
	}
	
	
	@Route(target ="/data")
	public INetB data(@Data LinkedMap<String,Object> data) {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired head : \ndata : {}", new JacksonUtils().toJson(data));
		
		INetB resInet = new INetB();
		resInet.head(data);
		return resInet;
	}
	
	
	@Route(target ="/ctx", loggable =true, authRequired=false)
	public void ctx(@Data LinkedMap<String,Object> data) {
		
		logger.info("Autowhired INet : \nhead : {},\ndata:{}", new JacksonUtils().toJson(this.inet.head()), new JacksonUtils().toJson(this.inet.data()));
		
		logger.info("Autowhired head : \ndata : {}", new JacksonUtils().toJson(data));
		
		INetRespUtils.success(ctx)
	        .message("test")
	        .data("timestamp", System.currentTimeMillis())
	        .send();
		
		return;
	}
	
	
	
	
	
}
