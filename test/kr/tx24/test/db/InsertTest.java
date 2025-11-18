package kr.tx24.test.db;

import kr.tx24.lib.db.Create;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;

public class InsertTest {

	public static void main(String[] args) {
		int i = new Create()
		.table("TX_LOG.PROC")
		.record("id"   		, DateUtils.getCurrentTimestamp().getTime()/1000)
		.record("name"   	, "22222")
		.record("pid"   	, 1111)
		.record("host"   	, "host")
		.record("start"   	, 1111)
		.record("end"   	, 0)
		.record("regDay"   	, 20251118)
		.record("regDate"   , DateUtils.getCurrentTimestamp())
		.insert();
		
		System.out.println("result -->   "+ i);

	}

}
