package kr.tx24.api.conf;

import com.fasterxml.jackson.annotation.JsonProperty;

import kr.tx24.lib.map.LinkedMap;

public class HandlerConfig {
	public String name		= "";
	@JsonProperty("class")
    public String className= "";
	public LinkedMap<String, Object> params	= new LinkedMap<String,Object>();
	
	
}
