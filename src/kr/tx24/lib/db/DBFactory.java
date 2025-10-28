package kr.tx24.lib.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(DBFactory.class);
    private static volatile DBManager dbmanager = null;
    private static volatile boolean initialized = false;
	
    /**
    * Get singleton DBManager instance
    * @return DBManager instance
    * @throws RuntimeException if initialization fails
    */
   public static DBManager get() {
       if (dbmanager == null) {
           synchronized (DBFactory.class) {
               if (dbmanager == null) {
                   try {
                       dbmanager = new DBManager();
                       initialized = true;
                       logger.debug("DBManager initialized successfully");
                   } catch (Exception e) {
                       logger.error("Failed to initialize DBManager", e);
                       throw new RuntimeException("DBManager initialization failed", e);
                   }
               }
           }
       }
       return dbmanager;
   }
    
	
   /**
    * Check if DBManager is initialized
    * @return true if initialized
    */
   public static boolean isInitialized() {
       return initialized;
   }
   
   /**
    * Reset the factory (mainly for testing purposes)
    * WARNING: This will close existing connections
    */
   public static synchronized void reset() {
       if (dbmanager != null) {
           try {
               DBManager.shutdown();
           } catch (Exception e) {
               logger.warn("Error during reset", e);
           }
           dbmanager = null;
           initialized = false;
       }
   }
}



