package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.SystemUtils;

/**
 * 데이터베이스 트랜잭션 처리와 연결 소유권을 관리한다.
 * begin()으로 얻은 연결은 종료 시 닫으며, begin(Connection)으로 받은 연결은 호출부가 닫는다.
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
 *     trx.rollback(e);
 *     throw e;
 * }
 */
public class DBTrx{
	private static Logger logger = LoggerFactory.getLogger(DBTrx.class);
	private boolean    deepview       = false;
	private Connection conn           = null;
	private boolean    ownsConnection = false;
	
	
	public DBTrx() {

		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * 연결을 빌려 트랜잭션을 시작한다. 시작에 실패하면 연결을 닫고 원인 예외를 보존한다.
	 * @throws DBException 연결을 얻거나 autoCommit 설정에 실패한 경우
	 */
	public void begin() throws DBException{

		if(conn == null) {
			begin(DBFactory.get().getConnection(), true);
		}
	}
	
	/**
	 * 외부 연결로 트랜잭션을 시작한다. 시작 실패와 commit·rollback 이후에도 연결 종료는 호출부가 담당한다.
	 * 이미 시작한 연결은 다른 연결로 교체하지 않는다.
	 * @throws DBException autoCommit 설정에 실패한 경우
	 */
	public void begin(Connection conn) throws DBException{

		begin(conn, false);
	}

	/**
	 * 연결 소유권을 구분하여 트랜잭션을 시작하고, 설정 실패 시 직접 소유한 연결만 닫는다.
	 */
	private void begin(Connection conn, boolean ownsConnection) throws DBException{

		if(conn == null) {
			throw new IllegalArgumentException("트랜잭션 연결이 없습니다.");
		}
		if(this.conn != null) {
			if(this.conn == conn) {
				return;
			}
			throw new IllegalStateException("이미 시작된 트랜잭션의 연결을 교체할 수 없습니다.");
		}

		try {
			conn.setAutoCommit(false);
		}catch(SQLException ex){
			if(ownsConnection) {
				close(conn, ex);
			}
			logger.warn("트랜잭션 처리 실패", ex);
			throw new DBException("Failed to set auto-commit mode", ex);
		}catch(RuntimeException ex){
			if(ownsConnection) {
				close(conn, ex);
			}
			logger.warn("트랜잭션 처리 실패", ex);
			throw ex;
		}

		this.conn           = conn;
		this.ownsConnection = ownsConnection;
		if(SystemUtils.deepview()) {
			logger.info("start transaction");
		}
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
			DBUtils.setValues(pstmt,update.getRecord());
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
	 * placeholder가 포함된 UPDATE 쿼리를 PreparedStatement로 실행한다.
	 * @param query placeholder가 포함된 UPDATE 쿼리
	 * @param values placeholder 순서에 맞는 값
	 * @return 변경된 row 수
	 * @throws DBException 쿼리 실행 실패
	 */
	public int update(String query, Object... values) throws DBException{
		PreparedStatement pstmt = null;
		int result = 0;

		long startTime = System.nanoTime();
		try {
			pstmt = conn.prepareStatement(query);
			DBUtils.setValues(pstmt, values);
			result = pstmt.executeUpdate();
		} catch(SQLException t) {
			throw new DBException("Failed to execute prepared update", query, t);
		} finally {
			close(pstmt);
			if(deepview) {
				logger.info("query : {} = [{}]", result, query);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime() - startTime));
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
			DBUtils.setValues(pstmt,create.getRecord());
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


	public long insertLastId(Create create)throws DBException{

		PreparedStatement pstmt = null;
		ResultSet rset 			= null;
		String query			= create.buildPreparedQuery();
		long result = 0;

		long startTime = System.nanoTime();
		try {
			pstmt		= conn.prepareStatement(query,Statement.RETURN_GENERATED_KEYS);
			DBUtils.setValues(pstmt,create.getRecord());
			result  	= pstmt.executeUpdate();
			rset		= pstmt.getGeneratedKeys();

			if(rset.next()){
				result = rset.getLong(1);
			}


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
	
	
	public int execute(Object... daos) throws DBException {
		if (daos == null || daos.length == 0) {
			return 0;
		}
		return execute(Arrays.asList(daos));
	}
	
	
	public int execute(List<Object> daos) throws DBException {
		int result = 0;
		if (daos == null || daos.isEmpty()) {
			return result;
		}
		
		
		for(Object dao : daos) {
			if (dao instanceof Create create) {
				result += this.insert(create);
			} else if (dao instanceof Update update) {
				result += this.update(update);
			} else if (dao instanceof Delete delete) {
				result += this.delete(delete);
			} else {
				throw new DBException("unsupported dao type : " + dao.getClass().getName());
			}
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
			DBUtils.setValues(pstmt, values);
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
	 * 현재 트랜잭션에서 SELECT의 ?에 가변 인자를 순서대로 바인딩하여 조회한다.
	 * @param query placeholder가 포함된 SELECT 쿼리
	 * @param obj 바인딩할 값. null 배열과 빈 배열은 바인딩하지 않으며 null 원소는 SQL NULL이다.
	 * @return 조회 결과
	 * @throws DBException SELECT 실행에 실패한 경우
	 */
	public RecordSet select(String query, Object... obj)throws DBException{

		Collection<Object> values = obj == null ? null : Arrays.asList(obj);
		return select(query, values);
	}

	/**
	 * 트랜잭션을 commit하고 직접 소유한 연결만 닫는다. commit 실패 시 rollback한 뒤 원인 예외를 전달한다.
	 * rollback과 연결 종료 오류는 원인 예외의 suppressed에 보존한다.
	 * @throws DBException commit 또는 연결 종료에 실패한 경우. commit 후 종료만 실패하면 DB 반영은 완료된 상태다.
	 */
	public void commit() throws DBException{

		Connection connection      = conn;
		Connection ownedConnection = ownsConnection ? conn : null;
		boolean    committed       = false;
		conn           = null;
		ownsConnection = false;
		if(connection == null) {
			return;
		}

		try(ownedConnection) {
			try {
				connection.commit();
				committed = true;
			}catch(SQLException | RuntimeException ex){
				rollback(connection, ex);
				throw ex;
			}
		}catch(SQLException ex){
			logger.warn("트랜잭션 처리 실패", ex);
			throw new DBException(committed ? "트랜잭션 연결 종료에 실패했습니다." : "트랜잭션 commit에 실패했습니다.", ex);
		}catch(RuntimeException ex){
			logger.warn("트랜잭션 처리 실패", ex);
			throw ex;
		}

		if(SystemUtils.deepview()) {
			logger.info("commit transaction");
		}
	}
	
	/**
	 * 트랜잭션을 rollback하고 직접 소유한 연결만 닫는다. 정리 오류는 함께 로그로 기록하며 반복 호출은 무시한다.
	 */
	public void rollback(){

		rollback(null);
	}

	/**
	 * 업무 오류 후 rollback하고 직접 소유한 연결만 닫으며 정리 오류를 전달받은 원인의 suppressed에 보존한다.
	 * 원인 예외는 호출부에서 다시 전달하며, 이 메서드는 정리 오류로 원인을 덮어쓰지 않는다.
	 * @param cause 호출부의 원인 예외. null이면 정리 오류를 로그로만 기록한다.
	 */
	public void rollback(Throwable cause){

		Connection connection      = conn;
		Connection ownedConnection = ownsConnection ? conn : null;
		conn           = null;
		ownsConnection = false;
		if(connection == null) {
			return;
		}

		if(cause != null) {
			try {
				rollback(connection, cause);
			}finally {
				close(ownedConnection, cause);
			}
			logger.warn("트랜잭션 처리 실패", cause);
			return;
		}

		try(ownedConnection) {
			connection.rollback();
		}catch(SQLException | RuntimeException ex){
			logger.warn("트랜잭션 처리 실패", ex);
			return;
		}
		logger.info("rollback transaction");
	}

	/**
	 * commit 또는 업무 오류를 정리하는 과정의 rollback 오류를 원인 예외에 보존한다.
	 */
	private void rollback(Connection conn, Throwable cause){

		try {
			conn.rollback();
		}catch(SQLException | RuntimeException ex){
			if(ex != cause) {
				cause.addSuppressed(ex);
			}
		}
	}
	
	/**
	 * 트랜잭션 시작 또는 업무 오류를 정리하는 과정의 연결 종료 오류를 원인 예외에 보존한다.
	 */
	private void close(Connection conn, Throwable cause){

		if(conn == null) {
			return;
		}

		try {
			conn.close();
		}catch(SQLException | RuntimeException ex){
			if(ex != cause) {
				cause.addSuppressed(ex);
			}
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
