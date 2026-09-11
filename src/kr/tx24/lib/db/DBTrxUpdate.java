package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;

/**
 * UPDATE,CREATE,DELETE 에 대한 복합 쿼리 처리 
 * Transaction 처리를 하며 SELECT 구문은 사용할 수 없다. 
 * DBTrxUpdate trx = new DBTrxUpdate();
 * trx.add(Update)
 * trx.add(Delete)
 * trx.add(Create)
 * trx.executeWithResult();
 */
public class DBTrxUpdate{
	private static Logger logger = LoggerFactory.getLogger(DBTrxUpdate.class);
	
	
	private List<DBSet> list = null;
	
	public DBTrxUpdate() {
		list = new ArrayList<DBSet>();
	}
	
	

	
	public DBTrxUpdate add(Update update) {
		list.add(new DBSet(update.build(),null));
		return this;
	}
	
	public DBTrxUpdate add(Create create) {
		list.add(new DBSet(create.build(),null));
		return this;
	}
	
	public DBTrxUpdate add(Delete delete) {
		list.add(new DBSet(delete.build(),null));
		return this;
	}
	
	public DBTrxUpdate add(String query) {
		list.add(new DBSet(query,null));
		return this;
	}
	
	public DBTrxUpdate add(Update update,LinkedMap<String,Object> record) {
		list.add(new DBSet(update.buildPreparedQuery(),record));
		return this;
	}
	
	public DBTrxUpdate add(Create create,LinkedMap<String,Object> record) {
		list.add(new DBSet(create.buildPreparedQuery(),record));
		return this;
	}
	
	
	/**
	 * 등록한 쿼리를 하나의 트랜잭션으로 실행하고 성공 여부를 반환한다.
	 * 원인 예외가 필요한 호출부는 executeWithResult()를 사용한다.
	 */
	public boolean execute() {

		try {
			executeWithResult();
		}catch(Exception e) {
			return false;
		}
		return true;
	}
	
	
	/**
	 * DBTrx로 등록한 쿼리를 실행하고 쿼리별 영향받은 행 수를 반환하며 직접 얻은 연결을 닫는다.
	 * 문장·rollback 오류는 원인에, 연결 종료 오류는 전달되는 예외의 suppressed에 보존한다.
	 * @throws DBException 시작·쿼리·commit·연결 종료에 실패한 경우. JDBC 원인은 getCause()로 확인한다.
	 */
	public int[] executeWithResult() throws DBException{

		DBTrx  trx    = new DBTrx();
		String query  = null;
		int[]  result = new int[list.size()];

		try(Connection conn = DBFactory.get().getConnection()) {
			try {
				trx.begin(conn);

				int i = 0;
				for(DBSet data : list) {
					query = data.query;
					if(query.toUpperCase().startsWith("SELECT ")) {
						throw new SQLException("SELECT 구문은 허용하지 않습니다." +query);
					}

					if(data.hasValue()) {
						try(PreparedStatement pstmt = conn.prepareStatement(query)) {
							DBUtils.setValues(pstmt,data.record);
							result[i] = pstmt.executeUpdate();
						}
					}else {
						try(Statement stmt = conn.createStatement()) {
							result[i] = stmt.executeUpdate(query);
						}
					}

					if(SystemUtils.deepview()) {
						logger.info("[{}] ,[{}] ,{} ",i,result[i],query);
					}
					i++;
				}

				trx.commit();
			}catch(SQLException | RuntimeException | Error ex){
				trx.rollback(ex);
				throw ex;
			}
		}catch(SQLException ex){
			logger.warn("복합 쿼리 처리 실패", ex);
			if(ex instanceof DBException dbEx) {
				throw dbEx;
			}
			throw new DBException("Failed to execute update", query, ex);
		}catch(RuntimeException | Error ex){
			logger.warn("복합 쿼리 처리 실패", ex);
			throw ex;
		}

		return result;
	}
	
	
	
	
	public class DBSet {
		private String query 	= "";
		private LinkedMap<String,Object> record = null;
		
		public DBSet(String query,LinkedMap<String,Object> record) {
			this.query = query;
			this.record = record;
		}
		
		public String query() {
			return query;
		}
		
		public boolean hasValue() {
			return record == null ? false: true;
		}
		
		public LinkedMap<String,Object> record(){
			return record;
		}
		
	}

}
