package kr.tx24.lib.test;

import kr.tx24.lib.map.ThreadSafeLinkedMap;
import kr.tx24.lib.mapper.JacksonUtils;
import kr.tx24.lib.mapper.JacksonXmlUtils;

public class Test {

	public static void main(String[] args) {
		ThreadSafeLinkedMap<String,Object> map = new ThreadSafeLinkedMap<String,Object>();
		
		
		map.put("1", "1111");
		map.put("2", "1111");
		map.put("apop", "1111");
		
		System.out.println(new JacksonUtils().toJson(map));
		System.out.println(new JacksonXmlUtils().toXml(map));
	}
}
