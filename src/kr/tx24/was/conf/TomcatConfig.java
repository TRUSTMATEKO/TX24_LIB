package kr.tx24.was.conf;

import java.util.HashMap;

import kr.tx24.lib.mapper.JacksonUtils;

/**
 * @author juseop
 *
 */
public class TomcatConfig {

	public String serverName	= "TX24";
	public String contextPath	= "../";
	public String host			= "0.0.0.0";
	public String basePackage	= "kr.tx24";
	public int port				= 8080;
	public int maxPostSize		= -1;
	public int acceptCount		= 50;
	public int maxThreads		= 256;
	public int minSpareThreads	= 50;
	public int maxConnection	= 500;
	public int connectionTimeout= 10000;
	public boolean tcpNoDelay	= false;
	public boolean reloadable	= false;
	public boolean isRESTful	= false;
	public boolean templateCacheable = true;
	public String uploadDirectory= "";
	
	public HashMap<String,String> parameter = new HashMap<String,String>();
	
	public TomcatConfig() {
	}
	
}
