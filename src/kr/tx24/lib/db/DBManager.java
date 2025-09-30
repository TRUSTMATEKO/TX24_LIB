package kr.tx24.lib.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import kr.tx24.lib.conf.Configure;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.SharedMap;
import kr.tx24.lib.mapper.JacksonUtils;

public class DBManager {
	
	private static Logger logger = LoggerFactory.getLogger(DBManager.class);
	private static HikariDataSource ds		= null;
	private String error					= "";
	public static boolean injectionFilter	= false;
	
	
	
	public DBManager() throws Exception {
		init();
	}
	

	
	
	private synchronized void init() {
		if(ds == null) {
			try {
				HikariConfig config = null;
				
				SharedMap<String,Object> dbMap = loadConfig();	
				
				config = new HikariConfig();
				config.setJdbcUrl(dbMap.getString("jdbcurl"));
				config.setUsername(dbMap.getString("username"));
				config.setPassword(dbMap.getString("password"));
				config.setDriverClassName(dbMap.getString("driver"));
				config.setAutoCommit(dbMap.isTrue("autocommit"));
				config.setPoolName(dbMap.getString("pool"));
				//pool에 유지시킬 수 있는 최대 커넥션 수. pool의 커넥션 수가 옵션 값에 도달하게 되면 idle인 상태는 존재하지 않음.(default: 10)
				config.setMaximumPoolSize(dbMap.getInt("max"));
				//아무런 일을 하지않아도 적어도 이 옵션에 설정 값 size로 커넥션들을 유지해주는 설정. 최적의 성능과 응답성을 요구한다면 이 값은 설정하지 않는게 좋음. default값을 보면 이해할 수있음.
				config.setMinimumIdle(dbMap.getInt("min"));
				//커넥션 풀에서 살아있을 수 있는 커넥션의 최대 수명시간. 사용중인 커넥션은 maxLifetime에 상관없이 제거되지않음. 사용중이지 않을 때만 제거됨. 
				//풀 전체가아닌 커넥션 별로 적용이되는데 그 이유는 풀에서 대량으로 커넥션들이 제거되는 것을 방지하기 위함임. 
				//강력하게 설정해야하는 설정 값으로 데이터베이스나 인프라의 적용된 connection time limit보다 작아야함. 
				//0으로 설정하면 infinite lifetime이 적용됨(idleTimeout설정 값에 따라 적용 idleTimeout값이 설정되어 있을 경우 0으로 설정해도 무한 lifetime 적용 안됨). (default: 1800000 (30minutes))
				config.setMaxLifetime(Math.min(dbMap.getLong("lifetime"), 30 * 60 * 1000L));
				//pool에서 커넥션을 얻어오기전까지 기다리는 최대 시간, 허용가능한 wait time을 초과하면 SQLException을 던짐. 설정가능한 가장 작은 시간은 250ms (default: 30000 (30s))
				config.setConnectionTimeout(Math.max(dbMap.getLong("timeout"), 250L));
				//pool에 일을 안하는 커넥션을 유지하는 시간. 이 옵션은 minimumIdle이 maximumPoolSize보다 작게 설정되어 있을 때만 설정. pool에서 유지하는 최소 커넥션 수는 minimumIdle (A connection will never be retired as idle before this timeout.). 최솟값은 10000ms (default: 600000 (10minutes))
				config.setIdleTimeout(Math.min(dbMap.getLong("idleTimeout"), config.getMaxLifetime()));
				config.addDataSourceProperty("cachePrepStmts", "true");
				config.addDataSourceProperty("prepStmtCacheSize", "250");
				config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
				//config.addHealthCheckProperty("connectivityCheckTimeoutMs", "1000");
				
				DBManager.injectionFilter = dbMap.isTrue("injectionFilter");
				
				ds = new HikariDataSource(config);
				System.out.print("Jdbc        : Pool initialized");
				
			}catch(Exception e) {
				System.out.print("Jdbc        : initalize exception "+e.getMessage());
				logger.warn("Failed to initialize HikariCP pool : {}",CommonUtils.getExceptionMessage(e));
			}
		}
	}
	
	private static SharedMap<String,Object> loadConfig() throws Exception{
		Path configPath = Paths.get(SystemUtils.getDatabaseConfig());
		
		if (!Files.exists(configPath)) {
	        throw new Exception(configPath.toAbsolutePath() + " not found");
	    }

	    // JSON 읽기
	    String json = Files.readString(configPath).trim();
	    if (json.isEmpty()) {
	        throw new Exception("Config file is empty: " + configPath.toAbsolutePath());
	    }
	    SharedMap<String, Object> map = new JacksonUtils().fromJsonSharedMapObject(json);

	    if (map != null && map.containsKey("password")) {
	        String password = map.getString("password");
	        if (password != null && password.startsWith("ENC:")) {
	            // "ENC:" 제거 후 복호화
	            String encrypted = password.substring(4);
	            String decrypted = new Configure().decrypt(encrypted);
	            map.put("password", decrypted);
	        }
	    }
	    
	    return map;
	}
	
	
	
	public Connection getConnection() throws SQLException{
		if (ds == null) init();
		return ds.getConnection();
	} 
	
	public static void shutdown()throws SQLException {
		if (ds != null) {
            ds.close();
            ds = null;
		}
	}
	
	public static HikariDataSource getDataSource()throws SQLException{
		return ds;
	}
	
	
	public void close(Connection conn){
		if(conn != null){
			try{
				conn.close();
			}catch(SQLException e){}
		}
	}
	
	public void close(ResultSet rSet){
		if(rSet != null){
			try{
				rSet.close();
			}catch(SQLException e){}
		}
	}
	
	public void close(Statement stmt){
		if(stmt != null){
			try{
				stmt.close();
			}catch(SQLException e){}
		}
	}
		
	public void close(PreparedStatement pstmt){
		if(pstmt != null){
			try{
				pstmt.close();
			}catch(SQLException e){}
		}
	}
	
	public void close(CallableStatement cstmt){
		if(cstmt != null){
			try{
				cstmt.close();
			}catch(SQLException e){}
		}
	}
	
	public void close(Connection conn,PreparedStatement pstmt,ResultSet rset){
		close(rset);
		close(pstmt);
		close(conn);
	}
	
	public void close(Connection conn,Statement stmt,ResultSet rset){
		close(rset);
		close(stmt);
		close(conn);
	}
	
	public void close(Connection conn,CallableStatement stmt,ResultSet rset){
		close(rset);
		close(stmt);
		close(conn);
	}
	
	public void close(AutoCloseable... resources) {
        for (AutoCloseable r : resources) {
            if (r != null) {
                try { r.close(); } catch (Exception ignored) {}
            }
        }
    }
	
	
	
	public String getError(){
		return this.error;
	}
	
	public int preparedExecuteUpdate(String query) throws SQLException{
		PreparedStatement pstmt = null;
		Connection 	conn			= null;
		int result = 0;
		long startTime = System.nanoTime();

		try {
			conn		= getConnection();
			conn.setAutoCommit(false);
			pstmt		= conn.prepareStatement(query);
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(SQLException t){
			try { conn.rollback();}catch(SQLException c) {}
			error = CommonUtils.getExceptionMessage(t);
			logger.warn("sql error : {}",error);
			throw t;
		}finally {
			close(pstmt);
			close(conn);
			
			log(query, result, startTime, null);
			
			
		}
		return result;
	}
	
	
	public int preparedExecuteUpdate(String query,LinkedMap<String,Object> record) throws SQLException{
		PreparedStatement pstmt = null;
		Connection 	conn			= null;
		int result = 0;
		long startTime = System.nanoTime();
		try {
			
			conn		= getConnection();
			conn.setAutoCommit(false);
			pstmt		= conn.prepareStatement(query);
			DBUtils.setValues(pstmt, record);
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(SQLException t){
			try { conn.rollback();}catch(SQLException c) {}
			error = CommonUtils.getExceptionMessage(t);
			logger.warn("sql error : {}",error);
			throw t;
		}finally {
			close(pstmt);
			close(conn);
			
			log(query, result, startTime, null);
			
		}
		return result;
	}
	
	public long preparedExecuteUpdateAndLastIdx(String query,LinkedMap<String,Object> record) throws SQLException{
		PreparedStatement pstmt = null;
		Connection 	conn			= null;
		ResultSet rset			= null;
		long result = 0;
		long startTime = System.nanoTime();
		
		
		try {
			conn		= getConnection();
			conn.setAutoCommit(false);
			pstmt		= conn.prepareStatement(query);
			DBUtils.setValues(pstmt, record);
			result  	= pstmt.executeUpdate();
			rset		= pstmt.executeQuery("SELECT LAST_INSERT_ID() ");
			
			while(rset.next()){
				result = rset.getLong(1);
			}
			conn.commit();
		}catch(SQLException t){
			try { conn.rollback();}catch(SQLException c) {}
			error = CommonUtils.getExceptionMessage(t);
			logger.warn("sql error : {}",error);
			throw t;
		}finally {
			close(rset);
			close(pstmt);
			close(conn);
			log(query, result, startTime, null);
		}
		return result;
	}
	

	public int statementExecuteUpdate(String query)throws Exception{
		Statement stmt 	= null;
		Connection conn = null;
		int result = 0;
		long startTime  = System.nanoTime();

		try {
			conn		= getConnection();
			conn.setAutoCommit(false);
			stmt		= conn.createStatement();
			result  	= stmt.executeUpdate(query);
			conn.commit();
		}catch(SQLException t){
			try { conn.rollback();}catch(SQLException c) {}
			error = CommonUtils.getExceptionMessage(t);
			logger.warn("sql error : {}",error);
			throw t;
		}finally {
			close(stmt);
			close(conn);
			
			log(query, result, startTime, null);
			
		}
		return result;
	}
	
	
	
	
	
	public void setAutoCommit(Connection conn,boolean autoCommit) throws SQLException{
		conn.setAutoCommit(autoCommit);
	}

	

	
	public RecordSet statementExecute(String query) throws Exception {
		Statement stmt 	= null;
		ResultSet rset	= null;
		Connection conn = null;
		RecordSet records = null;
		
		long startTime = System.nanoTime();
		
		try {
			
			conn = getConnection();
			stmt = conn.createStatement();
			stmt.executeQuery(query);
			rset = stmt.getResultSet();
			
			if(rset != null){
				records = new RecordSet(rset);
			}
			
		}catch(SQLException e) {
			e.printStackTrace();
			error = CommonUtils.getExceptionMessage(e);
			logger.warn("sql error : {}",error);
			throw e;
		}finally{
			close(conn, stmt, rset);
			
			log(query, 0, startTime, records);
		}
		
		return records;
	}
	
	
	public RecordSet preparedStatementExecute(String query) throws Exception {
		PreparedStatement pstmt 	= null;
		ResultSet rset	= null;
		Connection conn = null;
		RecordSet records = null;
		
		long startTime = System.nanoTime();
		
		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(query);
			pstmt.executeQuery();
			rset = pstmt.getResultSet();
			
			if(rset != null){
				records = new RecordSet(rset);
			}
			
		}catch(SQLException e) {
			error = CommonUtils.getExceptionMessage(e);
			logger.warn("sql error : {}",error);
			throw e;
		}finally{
			close(conn, pstmt, rset);
			log(query, 0, startTime, records);
		}
		
		if(records == null) records = null;  // records = new RecordSet(null);
		return records;
	}
	
	
	public RecordSet preparedStatementExecute(String query,Object... args) throws Exception {
		PreparedStatement pstmt 	= null;
		ResultSet rset	= null;
		Connection conn = null;
		RecordSet records = null;
		
		long startTime = System.nanoTime();
		
		try {
			conn = getConnection();
			pstmt = conn.prepareStatement(query);
			DBUtils.setValues(pstmt,args);
			pstmt.executeQuery();
			rset = pstmt.getResultSet();
			
			if(rset != null){
				records = new RecordSet(rset);
			}
			
		}catch(SQLException e) {
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(e));
			throw e;
		}finally{
			close(conn, pstmt, rset);
			log(query, 0, startTime, records);
		}
		
		if(records == null) records = null;  // records = new RecordSet(null);
		return records;
	}
	
	
	public int preparedExecuteUpdate(String query,Object... record) throws Exception{
		PreparedStatement pstmt = null;
		Connection 	conn			= null;
		int result = 0;
		
		long startTime = System.nanoTime();
		
		try {
			
			conn		= getConnection();
			pstmt		= conn.prepareStatement(query);
			DBUtils.setValues(pstmt, record);
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(SQLException t){
			try { conn.rollback();}catch(SQLException c) {}
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
			throw t;
		}finally {
			close(pstmt);
			close(conn);
			log(query, result, startTime, null);
		}
		return result;
	}
	
	

	
	
	
	
	private void log(String query, Object result, long startTime, RecordSet records) {
	    if(SystemUtils.deepview()) {
	        logger.info("query  : [{}]", query);
	        logger.info("result : [{}]", result);
	        if(records != null ) {
	            logger.info("result size   : {}", records.size());
	        }
	        logger.info("elapsed: {}", SystemUtils.getElapsedTime(System.nanoTime() - startTime));
	    }
	}
	
	
	

}
