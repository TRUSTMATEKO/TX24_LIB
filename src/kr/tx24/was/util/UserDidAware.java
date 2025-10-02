package kr.tx24.was.util;

import kr.tx24.lib.map.SharedMap;

public interface UserDidAware {

	void setUserDid(SharedMap<String, Object> userDid);
	
}
