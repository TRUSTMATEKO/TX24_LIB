package kr.tx24.inter.mapper;

import java.lang.reflect.Method;

import kr.tx24.lib.lang.Abbreviator;

public class RouteMap {
	public Method method;
	public boolean loggable		= false;
	public Class<?> cls ;
	 
	@Override
	public String toString(){
		
		StringBuilder sb = new StringBuilder()
		.append(loggable == true ? "true , ": "false, ")
		//.append(method.toGenericString())
		.append(Abbreviator.format(cls.getName(),'.',20)).append(".").append(method.getName()).append("(").append(method.getParameterCount()).append(")")
		.append("\n");
		return sb.toString();
	}
}
