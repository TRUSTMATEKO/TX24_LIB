package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * 데이터베이스 트랜젝션 처리를 위한 커녁션 관리 
 * JPA 와 같은 형식으로 사용한다. NEW -> begin() -> commit() or rollback() 을 통한 transaction 마감 및 connection 자동 close
 * DBTrx  trx = new DBTrx();
 * try{
 *     trx.begin();
 * 	    //SELECT 와 UPDATE, DELETE 등은 QUERY 형태 및 Object 형식을 모두 지원한다.
 *     RecordSet rset = trx.select(query);
 *     RecordSet rset = trx.select(Retrieve);
 *     int result = trx.update(Update);
 *  
 *     trx.commit();
 * }catch(Exception e){
 *     trx.rollback();
 * }
 */
public class DBTrx{
	private static Logger logger = LoggerFactory.getLogger(DBManager.class);
	private boolean deepview 	= false;
	private Connection conn		= null;
	
	
	public DBTrx() {
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * DBTrx 를 사용할 때 반드시 호출하여야 한다.
	 * begin() 은 connection 을 할당하며 autocommit(false) 로 처리한다.
	 * @throws Exception
	 */
	public void begin() throws DBException{
		if(conn == null) {
			conn = DBFactory.get().getConnection();
			try {
				conn.setAutoCommit(false);
			} catch (SQLException e) {
				throw new DBException("Failed to set auto-commit mode", e);
			}
			if(SystemUtils.deepview()) logger.info("start transaction");
		}
	}
	
	/**
	 * DBTrx 를 사용할 때 반드시 호출하여야 한다.
	 * begin(Connection) 은 connection 을 할당하며 autocommit(false) 로 처리한다.
	 * @throws Exception
	 */
	public void begin(Connection conn) throws DBException{
		this.conn = conn;
		try {
			conn.setAutoCommit(false);
		} catch (SQLException e) {
			throw new DBException("Failed to set auto-commit mode", e);
		}
		if(SystemUtils.deepview()) logger.info("start transaction");
	}
	
	
	/**
	 * Delete Object 에 의한 DELETE 처리  , Delete.init() 자동 실행됨.
	 * @param delete
	 * @return
	 * @throws Exception
	 */
	public int delete(Delete delete) throws DBException{
		int result = 0;
		try {
			result = update(delete.build());
			delete.init();
		}catch(DBException e) {
			throw e;
		}
		return result;
	}
	
	/**
	 * 직접 쿼리를 통한 DELETE 처리 update 와 동일한다.
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public int delete(String query) throws DBException{
		return update(query);
	}
	
	
	/**
	 * Update Object 를 이용한 UPDATE 처리  Update.init() 자동 호출 됨
	 * @param update
	 * @return
	 * @throws Exception
	 */
	public int update(Update update)throws DBException{
		
		PreparedStatement pstmt = null;
		String query			= update.buildPreparedQuery();
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			pstmt		= conn.prepareStatement(query);			
			DBUtils.setValues(pstmt,update.getRecord().values());
			result  	= pstmt.executeUpdate();
		}catch(SQLException t){
			throw new DBException("Failed to execute update", query, t);
		}finally {
			close(pstmt);
			if(deepview) {
				logger.info("query : {} = [{}]",result,update.build());
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			update.init();
			
		}
		return result;
	}
	
	/**
	 * 직접 쿼리를 이용한 UPDATE 처리 
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public int update(String query) throws DBException{
		PreparedStatement pstmt = null;
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			pstmt		= conn.prepareStatement(query);	
			result  	= pstmt.executeUpdate();
		}catch(SQLException t){
			throw new DBException("Failed to execute update", query, t);
		}finally {
			close(pstmt);
			if(deepview) {
				logger.info("query : {} = [{}]",result,query);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
		}
		return result;
	}
	
	
	/**
	 * 직접 쿼리를 통한 INSERT 처리 update 와 동일한다.
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public int insert(String query) throws DBException{
		return update(query);
	}
	
	
	/**
	 * Create Object 를 이용한 UPDATE 처리  Create.init() 자동 호출 됨
	 * @param update
	 * @return
	 * @throws Exception
	 */
	public int insert(Create create)throws DBException{
		
		PreparedStatement pstmt = null;
		String query			= create.buildPreparedQuery();
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			pstmt		= conn.prepareStatement(query);			
			DBUtils.setValues(pstmt,create.getRecord().values());
			result  	= pstmt.executeUpdate();
		}catch(SQLException t){
			throw new DBException("Failed to execute insert", query, t);
		}finally {
			close(pstmt);
			if(deepview) {
				logger.info("query : {} = [{}]",result,create.build());
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			create.init();
			
		}
		return result;
	}
	
	
	/**
	 * Retrieve Object 를 통한 SELECT 처리 Retrieve.init() 자동 호출 됨.
	 * @param retrieve
	 * @return
	 * @throws Exception
	 */
	public RecordSet select(Retrieve retrieve)throws DBException{
		RecordSet rset = select(retrieve.build());
		retrieve.init();
		return rset;
	}
	
	/**
	 * 직접 쿼리를 통한 SELECT 처리 
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public RecordSet select(String query)throws DBException{
		
		Statement stmt 			= null;
		ResultSet rset			= null;
		RecordSet records		= null;
		
		long startTime = System.nanoTime();
		try {

			stmt		= conn.createStatement();
			stmt.executeQuery(query);
			rset		= stmt.getResultSet();
			if(rset != null) {
				records = new RecordSet(rset);
			}
			
		}catch(SQLException t){
			throw new DBException("Failed to execute select", query, t);
		}finally {
			close(rset);
			close(stmt);
			
			if(deepview) {
				logger.info("query : {} = [{}]",query,records != null ? records.size():0);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
		}
		return records;
	}
	
	
	/**
	 * 직접 쿼리에 PreparedStatement 와 같은 Object 를 맵핑하여 처리 한다.
	 * WHERE ABC=? AND CDE=? 와 같은 형식일때 사용하며. Collection 은 순서가 있는 LinkedHashMap, LinkedMap 등을 이용하여야 한다.
	 * SELECT 부터 ? 로 되어 있는 값을 순서적으로 맵핑하여 PreparedStatement 를 실행한다. 
	 * @param query
	 * @param values
	 * @return
	 */
	public RecordSet select(String query,Collection<Object> values)throws DBException{

		PreparedStatement pstmt = null;
		ResultSet rset			= null;
		RecordSet records		= null;
		
		long startTime = System.nanoTime();
		try {
			pstmt		= conn.prepareStatement(query);
			DBUtils.setValues(pstmt, values.toArray());
			rset		= pstmt.executeQuery();
			if(rset != null) {
				records = new RecordSet(rset);
			}
			
		}catch(SQLException t){
			throw new DBException("Failed to execute select", query, t);
		}finally {
			close(rset);
			close(pstmt);
			
			if(deepview) {
				logger.info("query : {} = [{}]",query,records != null ? records.size():0);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
		}
		return records;
	}
	
	
	
	/**
	 * 모든 select(),update(),delete() 쿼리 실행 후 반드시 호출되어야 한다. 
	 */
	public void commit(){
		try{
			if(conn != null) {
				conn.commit();
			}
			logger.info("commit transaction");
		}catch(SQLException e){
			logger.warn("db transaction commit failed: ",CommonUtils.getExceptionMessage(e));
		}finally {
			close(conn);
		}
	}
	
	/**
	 * 연속된 호출에서 Exception 이 발생시에 호출한다.
	 */
	public void rollback(){
		try{
			if(conn != null) {
				conn.rollback();
			}
			logger.info("rollback transaction");
		}catch(SQLException e){
			logger.warn("db transaction rollback failed : ",CommonUtils.getExceptionMessage(e));
		}finally {
			close(conn);
		}
	}
	
	private void close(Connection conn){
		if(conn != null){
			try{
				conn.close();
				//if(SystemUtils.deepview()) logger.info("Connection closed");
			}catch(SQLException e){}
		}
	}
	
	private void close(ResultSet rSet){
		if(rSet != null){
			try{
				rSet.close();
				//if(SystemUtils.deepview()) logger.info("ResultSet closed");
			}catch(SQLException e){}
		}
	}
	
	private void close(Statement stmt){
		if(stmt != null){
			try{
				stmt.close();
				//if(SystemUtils.deepview()) logger.info("Statement closed");
			}catch(SQLException e){}
		}
	}
		
	private void close(PreparedStatement pstmt){
		if(pstmt != null){
			try{
				pstmt.close();
				//if(SystemUtils.deepview()) logger.info("PreparedStatement closed");
			}catch(SQLException e){}
		}
	}
	

}
