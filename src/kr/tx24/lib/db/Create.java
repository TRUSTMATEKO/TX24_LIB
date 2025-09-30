package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;

/**
 * 
 */
public class Create {
	private static Logger logger 	= LoggerFactory.getLogger(Create.class);
	
	private static final String INSERT_INTO			= "INSERT INTO ";
	private static final String REPLACE_INTO		= "REPLACE INTO ";
	private static final String INSERT_IGNORE_INTO	= "INSERT IGNORE INTO ";
	private static final String VALUES				= ") VALUES (";
	private static final String QUESTION_MARK		= "?";
			
	
	private String INSERT			= INSERT_INTO;
	private String TRAILER			= "";
	private LinkedMap<String,Object> record = new LinkedMap<String,Object>();
	private boolean deepview		= false;
	private String table			= "";	
	
	
	
	public Create() {
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @param debug
	 */
	public Create(String table) {
		this.table 		= table;
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 및 LOG 출력 여부 
	 * @param table
	 * @param debug
	 */
	public Create(String table,boolean debug) {
		this.table 		= table;
		this.deepview	= debug;
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @return
	 */
	public Create table(String table) {
		this.table = table;
		return this;
	}
	
	/**
	 * REPLACE INTO 
	 * @return
	 */
	public Create replace() {
		this.INSERT = REPLACE_INTO;
		return this;
	}
	
	
	/**
	 * INSERT IGNORE INTO 
	 * @return
	 */
	public Create ignore() {
		this.INSERT = INSERT_IGNORE_INTO;
		return this;
	}
	
	/**
	 * INSERT 뒤에 쿼리를 추가한다. 예 ON DUPLICATE KEY UPDATE name=VALUES('') 
	 * @return
	 */
	public Create trailer(String trailer) {
		this.TRAILER = trailer;
		return this;
	}
	
	
	/*
	 * 테이블 명, 디버그, 레코드등을 초기화한다.
	 */
	public Create init() {
		table 	= "";
		deepview= SystemUtils.deepview();
		record.clear();
		return this;
	}
	
	/**
	 * 디버거 활성화 , 디버그는 기본 설정된 config 값을 사용한다.
	 * 디버그를 활성화 용도로만 사용된다.
	 * @return
	 */
	public Create debug() {
		deepview = true;
		return this;
	}
	
	
	/**
	 * Map 구조의 Key = Column name , Value = Column value 를 추가한다.
	 * 사용 시 데이터베이스의 Column 구조와 동일한 이름 값을 사용해야 한다.
	 * @param map
	 * @return
	 */
	public Create record(Map<? extends String, ? extends Object> map) {
		record.putAll(map);
		return this;
	}
	
	public Create record(ThreadSafeLinkedMap<? extends String, ? extends Object> map) {
		record.putAll(map.getMap());
		return this;
	}
	
	/**
	 * 컬럼 이름과 값을 추가한다.
	 * @param column
	 * @param value
	 */
	public Create record(String column,Object value){
		record.put(column, value);
		return this;
	}
	
	
	/**
	 * statement 형태의 Query 를 생성한다.
	 * @return
	 */
	public String build() {
		StringBuilder sql 	= new StringBuilder();
		StringBuilder val 	= new StringBuilder();
		sql.append(INSERT).append(table).append(DBUtils.SPACE).append(DBUtils.LEFT_PARENTHESIS);
		
		for(Map.Entry<String, Object> entry : record.entrySet()) {
			sql.append(entry.getKey()).append(DBUtils.COMMA);
			Object o = entry.getValue();
			
			if(o == null) {
				val.append(DBUtils.NULL);
			}else {
				if(o instanceof java.lang.Number) {
					val.append(o);
				}else if(o instanceof Boolean) {
					boolean b = (Boolean)o;
					val.append(b ? 1:0);
				}else{
					val.append(DBUtils.APOSTROPHE);
					val.append(CommonUtils.toString(o).replace(DBUtils.APOSTROPHE, "\\'" ));
					val.append(DBUtils.APOSTROPHE);
				}
			}
			val.append(DBUtils.COMMA);
		}
		
		sql.deleteCharAt(sql.length() -1);
		val.deleteCharAt(val.length() -1);
		
		sql.append(VALUES)
		.append(val)
		.append(DBUtils.RIGHT_PARENTHESIS)
		.append(DBUtils.SPACE)
		.append(TRAILER);
		
		return sql.toString();
	}
	
	
	/**
	 * Prepared Query string builder
	 * @return
	 */
	public String buildPreparedQuery() {
		StringBuilder sql 	= new StringBuilder();
		StringBuilder val 	= new StringBuilder();
		sql.append(INSERT).append(table).append(DBUtils.SPACE).append(DBUtils.LEFT_PARENTHESIS);
		
		for(String key : record.keySet()) {
			sql.append(key).append(DBUtils.COMMA);
			val.append(QUESTION_MARK).append(DBUtils.COMMA);
		}
		
		sql.deleteCharAt(sql.length() -1);
		val.deleteCharAt(val.length() -1);
		
		sql.append(VALUES)
		.append(val)
		.append(DBUtils.RIGHT_PARENTHESIS)
		.append(DBUtils.SPACE)
		.append(TRAILER);
		
		
		return sql.toString();
	}
	
	
	/**
	 * Async Insert
	 * 스레드 안전성을 위해 현재 레코드 snapshot을 사용
	 */
	public void insertAsync() {
	   
	    LinkedMap<String, Object> snapshot = new LinkedMap<>(record);
	    String query = buildPreparedQuery();
	    
	    
	    Thread async = new Thread(() -> {
	        DBManager db = null;
	        Connection conn = null;
	        long startTime = System.nanoTime();
	        int result = 0;

	        try {
	        	
	        	db = DBFactory.get();
	            conn = db.getConnection();
	        	
	            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
	                conn.setAutoCommit(false);
	                DBUtils.setValues(pstmt, snapshot.values());
	                result = pstmt.executeUpdate();
	                conn.commit();

	            } catch (Exception e) {
	            	try { if (conn != null) conn.rollback(); } catch (Exception ex) {}
	                logger.warn("sql error : {}", CommonUtils.getExceptionMessage(e));
	            }

	        }catch(Exception ex) {
	        	logger.warn("async insert failed : {}", CommonUtils.getExceptionMessage(ex));
	        }finally {
	        	if (db != null) db.close(conn);
	            if (deepview) {
	                logger.info("Async query : {} = [{}]", result, query);
	                logger.info(SystemUtils.getElapsedTime(System.nanoTime() - startTime));
	            }
	        }
	    });

	    async.start();
	}
	

	
	/**
	 * 설정된 값을 PREPARED 형태로 INSERT 한 후 INIT 이 호출된다.
	 * @return
	 */
	public int insert(){
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		String query			= buildPreparedQuery();
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			pstmt		= conn.prepareStatement(query);
			
			
			conn.setAutoCommit(false);
			DBUtils.setValues(pstmt,record.values());
			result  	= pstmt.executeUpdate();
			
			conn.commit();
		}catch(Exception t){
			try { 	conn.rollback(); }catch(SQLException s) {}
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(pstmt);
			db.close(conn);
			
			if(deepview) {
				logger.info("query : {} = [{}]",result,build());
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			init();
			
		}
		return result;
	}
	
	
	/**
	 * 쿼리를 직접 실행하여 데이터는 INSERT 한다. INIT 은 호출되지 않는다.
	 * @param query
	 * @return
	 */
	public int insert(String query){
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			pstmt		= conn.prepareStatement(query);	
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(Exception t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(pstmt);
			db.close(conn);
			
			if(deepview) {
				logger.info("query : {} = [{}]",result,query);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
		}
		return result;
	}
	
	
	/**
	 * INSERT 이후 AUTO_INCREMENT COLUMNS 의 LAST_ID 를 가져오며 INIT 이 실행된다.
	 * @return
	 */
	public long insertLastId(){
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		ResultSet rset			= null;
		
		String query			= buildPreparedQuery();
		long result = 0;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			pstmt		= conn.prepareStatement(query,Statement.RETURN_GENERATED_KEYS);
			
			DBUtils.setValues(pstmt,record.values());
			

			result 	= pstmt.executeUpdate();
			rset= pstmt.getGeneratedKeys();
			
			if(rset.next()){
				result = rset.getLong(1);
			}
			conn.commit();
		}catch(Exception t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(pstmt);
			db.close(conn);
			
			if(deepview) {
				logger.info("query : {} = [{}]",result,build());
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			init();
			
		}
		return result;
	}
	
	
	public LinkedMap<String,Object> getRecord(){
		return record;
	}
	
	
	public int insertSelect(Retrieve retrieve) {
		return insertSelect("",retrieve);
	}
	
	
	public int insertSelect(String columns, Retrieve retrieve){
		StringBuilder sql 	= new StringBuilder()
			.append(INSERT_INTO)
			.append(table).append(DBUtils.SPACE);
		
			if(CommonUtils.isNotEmpty(columns)) {
				sql.append(DBUtils.LEFT_PARENTHESIS)
					.append(columns)
					.append(DBUtils.RIGHT_PARENTHESIS)
					.append(DBUtils.SPACE);
			}
				
				sql.append(retrieve.build());
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			pstmt		= conn.prepareStatement(sql.toString());	
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(Exception t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(pstmt);
			db.close(conn);
			
			if(deepview) {
				logger.info("query : {} = [{}]",result,sql.toString());
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
		}
		return result;
	}
	

	
	
	
	
}
