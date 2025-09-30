package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
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
	private static Logger logger = LoggerFactory.getLogger(DBManager.class);
	
	
	private List<DBSet> list = null;
	private Connection conn		= null;
	
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
	
	
	public boolean execute() {
		try {
			executeWithResult();
		}catch(Exception e) {
			return false;
		}
		return true;
	}
	
	
	public int[] executeWithResult() throws Exception{
		
		
		conn = DBFactory.get().getConnection();
		conn.setAutoCommit(false);
		
		logger.info("start transaction");
		
		int[] result = new int[list.size()];
		for (int i = 0; i < result.length; i++) {
		    result[i] = -1;
		}
		
		
		PreparedStatement pstmt = null;
		Statement stmt = null;
		int i = 0;
		
		try {
			for(DBSet data : list) {
				
				if(data.query.toUpperCase().startsWith("SELECT ")) {
					throw new Exception ("SELECT 구문은 허용하지 않습니다." +data.query);
				}
				
				if(data.hasValue()) {
					pstmt = conn.prepareStatement(data.query);
					DBUtils.setValues(pstmt,data.record.values());
					result[i]  = pstmt.executeUpdate();
					pstmt.close();
				}else {
					stmt = conn.createStatement();
					result[i]  = stmt.executeUpdate(data.query);
					stmt.close();
				}
				
				if(SystemUtils.deepview()) logger.info("[{}] ,[{}] ,{} ",i,result[i],data.query);	
				
				i++;
			}
			
			conn.commit();
			logger.info("commit transaction");
		}catch(Exception e) {
			logger.warn("db transaction execute failed : ",CommonUtils.getExceptionMessage(e));
			try {
                conn.rollback();
                logger.info("rollback transaction");
            } catch (SQLException rb) {
            	logger.warn("rollback failed : ",CommonUtils.getExceptionMessage(rb));
            }
			throw e;
		}finally {
			
			
			if(stmt != null){
				try{
					stmt.close();
					//if(SystemUtils.deepview()) logger.info("Statement closed");
				}catch(SQLException e){}
			}
			if(pstmt != null){
				try{
					pstmt.close();
					//if(SystemUtils.deepview()) logger.info("PreparedStatement closed");
				}catch(SQLException e){}
			}
			if(conn != null){
				try{
					conn.close();
					//if(SystemUtils.deepview()) logger.info("end transaction");
				}catch(SQLException e){}
			}
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
