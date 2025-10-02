package kr.tx24.was.example.custom;

import org.springframework.stereotype.Service;

import kr.tx24.lib.db.Create;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.was.annotation.UserDid;
import kr.tx24.was.util.SessionUtils;
import kr.tx24.was.util.UserDidAware;

@UserDid
@Service
public class UserDisService implements UserDidAware {

	private static final String USER_DID_TABLE_NAME = "";
	
	
	
	
	/**
	 * SharedMap<String,Object> userDidMap = new SharedMap<String,Object>();
	            userDidMap.put("sessionId"	, sessionMap.getString(SessionUtils.SESSION_ID));
	            userDidMap.put("id"			, sessionMap.getString(SessionUtils.SESSION_USERID));
	            userDidMap.put("uri"		, reqUri);
	            userDidMap.put("method"		, request.getMethod());
	            userDidMap.put("pageId"		, "");
	            userDidMap.put("payload"	, "");
	            userDidMap.put("regDay"		, DateUtils.getRegDay());
	   이 전달 됨.
	 */
	@Override
	public void setUserDid(SharedMap<String, Object> userDidMap) {
		if(userDidMap != null) {
            new Create().table(USER_DID_TABLE_NAME).record(userDidMap).insertAsync();
		}
	}

}
