package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.AsyncExecutor;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * 
 */
public class Delete {
	private static Logger logger 	= LoggerFactory.getLogger(Delete.class);
	private static final String DELETE				= "DELETE FROM ";
	
	private StringBuilder where		= new StringBuilder();
	private boolean deepview		= false;
	private String table			= "";	
	
	
	public Delete() {
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @param debug
	 */
	public Delete(String table) {
		this.table 		= table;
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 및 LOG 출력 여부 
	 * @param table
	 * @param debug
	 */
	public Delete(String table,boolean debug) {
		this.table 		= table;
		this.deepview	= debug;
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @return
	 */
	public Delete table(String table) {
		this.table = table;
		return this;
	}
	
	
	/*
	 * 테이블 명, 디버그, 레코드, 조건절 등을 초기화한다.
	 */
	public Delete init() {
		deepview= SystemUtils.deepview();
		table 	= "";
		where.setLength(0);
		return this;
	}
	
	/**
	 * 디버거 활성화 , 디버그는 기본 설정된 config 값을 사용한다.
	 * 디버그를 활성화 용도로만 사용된다.
	 * @return
	 */
	public Delete debug() {
		deepview = true;
		return this;
	}
	
	
	
	/**
	 * 기존 조건절을 초기화하고 조건절을 추가한다.
	 * 조건 완성형(A=B) 로 입력해야 한다.
	 * @param where
	 * @return
	 */
	public Delete whereInit(String condition) {
		this.where.setLength(0);
		this.where.append(condition);
		return this;
	}
	
	
	/**
	 * 조건절을 추가한다. 기존 조건이 있으면 AND 로 조건이 추가된다.
	 * 조건 완성형(A=B) 로 입력해야 한다.
	 * @param condition
	 * @return
	 */
	public Delete where(String condition) {
		if(this.where.length() == 0) {
			this.where.append(condition);
		}else {
			this.where.append(DBUtils.AND).append(condition);
		}
		return this;
	}
	

	/**
	 * logicalOpetator = " AND " 입력되며, column 과 value 비교연산자는 equals 이 된다. 
	 * IS NULL     where("column",null)  operator 가 eq 가 아니라면 모두 IS NOT NULL 처리된다.
	 * IS NOT NULL where("column",null) 
	 * @param column
	 * @param value
	 * @param operatorator
	 */
	public Delete where(String column, Object value) {
		where(column,value,DBUtils.eq,DBUtils.AND);
		return this;
	}
	
	
	/**
	 * logicalOpetator = " OR " 입력되며, column 과 value 비교연산자는 equals 이 된다. 
	 * IS NULL     where("column",null)  operator 가 eq 가 아니라면 모두 IS NOT NULL 처리된다.
	 * IS NOT NULL where("column",null) 
	 * @param column
	 * @param value
	 * @param operatorator
	 */
	public Delete where(String column, Object value, String operator) {
		where(column,value,operator,DBUtils.AND);
		return this;
	}
	
	/**
	 * 조건절을 추가한다. 기존 조건이 있으면 OR 로 조건이 추가된다.
	 * 조건 완성형(A=B) 로 입력해야 한다.
	 * @param condition
	 * @return
	 */
	public Delete whereOr(String condition) {
		if(this.where.length() == 0) {
			this.where.append(condition);
		}else {
			this.where.append(DBUtils.OR).append(condition);
		}
		return this;
	}
	
	/**
	 * logicalOpetator = " OR " 입력되며, column 과 value 비교연산자는 equals 이 된다. 
	 * IS NULL     where("column",null)  operator 가 eq 가 아니라면 모두 IS NOT NULL 처리된다.
	 * IS NOT NULL where("column",null) 
	 * @param column
	 * @param value
	 * @param operatorator
	 */
	public Delete whereOr(String column, Object value) {
		where(column,value,DBUtils.eq,DBUtils.OR);
		return this;
	}
	
	
	/**
	 * logicalOpetator = " OR " 입력됨.
	 * operator 는 eq,fneq = , ne,fnne	!= , gt,fngt > , ge.fnge >= , lt,fnlt < , le <= , lk LIKE, nk NOT LIKE, in,fnin IN , ni,fnni NOT IN, bt BETWEEN 
	 * IS NULL     where("column",null,DBUtils.eq)  operator 가 eq 가 아니라면 모두 IS NOT NULL 처리된다.
	 * IS NOT NULL where("column",null,DBUtils.ne)
	 * SUBQUERY = where("column","SELECT A FROM DUAL",DBUtils.fnin)
	 * FUNCTION = where("FUNCTION('a')","1",DBUtils.eq)
	 * FUNCTION = where("column","FUNCTION('a')",DBUtils.fneq)
	 * IN ( SUBQUERY ) , Function 등을 WHERE 에 사용할때는 fneq, fnne, fngt, fnge, fnlt 등을 사용해야 한다. 
	 * @param column
	 * @param value
	 * @param operatorator
	 */
	public Delete whereOr(String column, Object value, String operator) {
		where(column,value,operator,DBUtils.OR);
		return this;
	}
	
	
	/**
	 * operator 는 eq,fneq = , ne,fnne	!= , gt,fngt > , ge.fnge >= , lt,fnlt < , le <= , lk LIKE, nk NOT LIKE, in,fnin IN , ni,fnni NOT IN, bt BETWEEN
	 * logicalOpetator = " AND " , " OR " 앞뒤 반드시 공백이 필요하다. 
	 * IS NULL     where("column",null,DBUtils.eq," AND ")  operator 가 eq 가 아니라면 모두 IS NOT NULL 처리된다.
	 * IS NOT NULL where("column",null,DBUtils.ne," AND ")
	 * SUBQUERY = where("column","SELECT A FROM DUAL",DBUtils.fnin," AND ")
	 * FUNCTION = where("FUNCTION('a')","1",DBUtils.eq," AND ")
	 * FUNCTION = where("column","FUNCTION('a')",DBUtils.fneq," AND ")
	 * IN ( SUBQUERY ) , Function 등을 WHERE 에 사용할때는 fneq, fnne, fngt, fnge, fnlt 등을 사용해야 한다. 
	 * @param column
	 * @param value
	 * @param operatorator
	 */
	
	public Delete where(String column,Object value,String operator,String logicalOperator){
		String q = DBUtils.where(column, value, operator, logicalOperator);
		if(this.where.length() == 0) {
			this.where.append(q);
		}else {
			this.where.append(logicalOperator).append(q);
		}
		
		return this;
	}
	
	
	
	
	/**
	 * 설정된 값으로 DELETE FROM TABLE WHERE  의 쿼리 반환한다.
	 * 안전한 쿼리를 위하여 WHERE 조건이 없을 경우 실패 쿼리 형식으로 리턴된다.
	 * @return
	 */
	public String build() {
		StringBuilder sql 	= new StringBuilder();
		sql.append(DELETE)
			.append(table)
			.append(DBUtils.WHERE);
		
		if(where.length() == 0) {
			sql.append(" DELETE WITHOUT A CONDITIONAL CLAUSE IS REJECTED!");
		}else {
			sql.append(where);
		}
		
		return sql.toString();
	}
	

	
	
	
	
	/**
	 * 설정된 값으로 DELETE 를 실행한다.
	 * DELETE 완료 후에는 init() 이 자동 호출된다.
	 * @return
	 */
	public int delete(){
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		String query			= build();
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			conn.setAutoCommit(false);
			pstmt		= conn.prepareStatement(query);	
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(Exception t){
			try { if (conn != null) conn.rollback(); } catch (Exception ex) {}
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(pstmt);
			db.close(conn);
			
			if(deepview) {
				logger.info("query : {} = [{}]",result,query);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			init();
			
		}
		return result;
	}
	
	
	
	
	
	/**
	 * 설정된 값으로 DELETE 를 실행한다.
	 * DELETE 완료 후에는 init() 이 자동 호출되지 않는다.
	 * @return
	 */
	public int deleteNotInit(){
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		String query			= build();
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			conn.setAutoCommit(false);
			pstmt		= conn.prepareStatement(query);	
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(Exception t){
			try { if (conn != null) conn.rollback(); } catch (Exception ex) {}
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(pstmt);
			db.close(conn);
			
			if(deepview) {
				logger.info("query : {} = [{}]",result,build());
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			
		}
		return result;
	}
	
	
	/**
	 * 지정된 쿼리를 실행한다.
	 * @param query
	 * @return
	 */
	public int delete(String query){
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		int result = 0;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			conn.setAutoCommit(false);
			pstmt		= conn.prepareStatement(query);	
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(Exception t){
			try { if (conn != null) conn.rollback(); } catch (Exception ex) {}
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
     * 비동기 DELETE
     * AsyncExecutor를 사용하여 스레드 풀에서 실행
     * 
     * @return CompletableFuture<Integer> - 완료 시 삭제된 행 수 반환
     */
    public CompletableFuture<Integer> deleteAsync() {
        final String query = build();
        final boolean enableLog = deepview;
        
        // init()은 호출하지 않음 - 비동기 작업 완료 후에도 재사용 가능
        
        return CompletableFuture.supplyAsync(() -> {
            DBManager db = null;
            Connection conn = null;
            PreparedStatement pstmt = null;
            int result = 0;
            long startTime = System.nanoTime();

            try {
                db = DBFactory.get();
                conn = db.getConnection();
                conn.setAutoCommit(false);
                
                pstmt = conn.prepareStatement(query);
                result = pstmt.executeUpdate();
                
                conn.commit();
                
                if (enableLog) {
                    logger.info("Async delete completed: {} row(s) deleted", result);
                }
                
            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        logger.error("Rollback failed", rollbackEx);
                    }
                }
                logger.error("Async delete failed: {}", CommonUtils.getExceptionMessage(e));
                throw new RuntimeException("Async delete failed", e);
                
            } finally {
                if (db != null) {
                    db.close(pstmt);
                    db.close(conn);
                }
                
                if (enableLog) {
                    logger.info("Async delete query: [{}]", query);
                    logger.info(SystemUtils.getElapsedTime(System.nanoTime() - startTime));
                }
            }
            
            return result;
            
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * 비동기 DELETE 후 자동 init()
     * 
     * @return CompletableFuture<Integer>
     */
    public CompletableFuture<Integer> deleteAsyncAndInit() {
        return deleteAsync().thenApply(result -> {
            init();
            return result;
        });
    }
	
	
	
	
}

