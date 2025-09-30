package kr.tx24.lib.db;

public class DBFactory {

	private static DBManager dbmanager = null;
	
	
	public static DBManager get()throws Exception{
		if(dbmanager == null) {
			dbmanager = new DBManager();
		}	
		return dbmanager;
	}

}
