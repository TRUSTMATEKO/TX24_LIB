package kr.tx24.api.conf;

import java.util.List;
import java.util.Map;


public class ApiConfig {

	public ApiConfig() {
		// TODO Auto-generated constructor stub
	}
	
	public String host			= "";
	public int port				= 0;
	public boolean logging		= false;
	public String basePackage	= "";;
	public boolean cacheable	= false;
	public String webroot		= "";
	public String index			= "";
	
	public Map<String, Object> property;
	
	public DenyConfig deny;
    public List<HandlerConfig> handlers;
	
	

}
