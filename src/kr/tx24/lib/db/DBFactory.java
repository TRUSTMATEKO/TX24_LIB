package kr.tx24.lib.db;

public class DBFactory {
private static volatile DBManager dbmanager = null;
    
    public static DBManager get() throws Exception {
        if (dbmanager == null) {
            synchronized (DBFactory.class) {
                if (dbmanager == null) {  // Double-checked locking
                    dbmanager = new DBManager();
                }
            }
        }
        return dbmanager;
    }

}
