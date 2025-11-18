package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.executor.AsyncExecutor;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;
import kr.tx24.lib.map.ThreadSafeLinkedMap;

/**
 * 
 */
public class Update {
	private static Logger logger 	= LoggerFactory.getLogger(Update.class);
	private static final String UPDATE				= "UPDATE ";
	private static final String SET					= " SET ";
	private static final String INNER_JOIN			= " INNER JOIN ";
	private static final String LEFT_OUTER_JOIN		= " LEFT OUTER JOIN ";
	private static final String RIGHT_OUTER_JOIN	= " RIGHT OUTER JOIN ";
	private static final String ON					= " ON ";
	
			
	
	
	private LinkedMap<String,Object> record = new LinkedMap<String,Object>();
	private StringBuilder where		= new StringBuilder();
	private boolean deepview		= false;
	private String table			= "";	
	private StringBuilder join 		= new StringBuilder();
	
	
	public Update() {
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @param debug
	 */
	public Update(String table) {
		this.table 		= table;
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 및 LOG 출력 여부 
	 * @param table
	 * @param debug
	 */
	public Update(String table,boolean debug) {
		this.table 		= table;
		this.deepview	= debug;
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @return
	 */
	public Update table(String table) {
		this.table = table;
		return this;
	}
	
	
	/*
	 * 테이블 명, 디버그, 레코드, 조건절 등을 초기화한다.
	 */
	public Update init() {
		deepview= SystemUtils.deepview();
		table 	= "";
		join.setLength(0);
		record.clear();
		where.setLength(0);
		return this;
	}
	
	/**
	 * 디버거 활성화 , 디버그는 기본 설정된 config 값을 사용한다.
	 * 디버그를 활성화 용도로만 사용된다.
	 * @return
	 */
	public Update debug() {
		deepview = true;
		return this;
	}
	
	/**
	 * Map 구조의 Key = Column name , Value = Column value 를 추가한다.
	 * 사용 시 데이터베이스의 Column 구조와 동일한 이름 값을 사용해야 한다.
	 * @param map
	 * @return
	 */
	public Update record(Map<? extends String, ? extends Object> map) {
		record.putAll(map);
		return this;
	}
	
	
	public Update record(ThreadSafeLinkedMap<? extends String, ? extends Object> map) {
		record.putAll(map.getMap());
		return this;
	}
	

	
	/**
	 * 컬럼 이름과 값을 추가한다.
	 * @param column
	 * @param value
	 */
	public Update record(String column,Object value){
		record.put(column, value);
		return this;
	}
	
	
	/**
	 * INNER JOIN 을 지정한다. 
	 * INNER JOIN (TABLEB) 및 ON 뒤의 비교절을 지정할 수 있따.
	 * TABLE A INNER JOIN TABLE B ON A.COLUMN = B.COLUMN 과 같이 표현되며
	 * TABLE A 는 .table() 을 통하여 지정하고 joinInner("TABLE B", "A.COLUMN = B.COLUMN") 와 같이 사용한다.
	 * @param tableB
	 * @param on
	 * @return
	 */
	public Update joinInner(String tableB,String on) {
		this.join.append(DBUtils.SPACE + INNER_JOIN + tableB + ON + on);
		return this;
	}
	
	/**
	 * LEFT OUTER JOIN 을 지정한다. 
	 * LEFT OUTER JOIN (TABLEB) 및 ON 뒤의 비교절을 지정할 수 있따.
	 * TABLE A LEFT OUTER JOIN TABLE B ON A.COLUMN = B.COLUMN 과 같이 표현되며
	 * TABLE A 는 .table() 을 통하여 지정하고 joinLeftOuter("TABLE B", "A.COLUMN = B.COLUMN") 와 같이 사용한다.
	 * @param tableB
	 * @param on
	 * @return
	 */
	public Update joinLeftOuter(String tableB,String on) {
		this.join.append(DBUtils.SPACE + LEFT_OUTER_JOIN + tableB + ON + on);
		return this;
	}
	
	/**
	 * RIGHT OUTER JOIN 을 지정한다. 
	 * RIGHT OUTER JOIN (TABLEB) 및 ON 뒤의 비교절을 지정할 수 있따.
	 * TABLE A RIGHT OUTER JOIN TABLE B ON A.COLUMN = B.COLUMN 과 같이 표현되며
	 * TABLE A 는 .table() 을 통하여 지정하고 joinRightOuter("TABLE B", "A.COLUMN = B.COLUMN") 와 같이 사용한다.
	 * @param tableB
	 * @param on
	 * @return
	 */
	public Update joinRightOuter(String tableB,String on) {
		this.join.append(DBUtils.SPACE + RIGHT_OUTER_JOIN + tableB + ON + on);
		return this;
	}
	
	
	/**
	 * JOIN 절 전체를 지정할 때 사용한다.
	 * 예. join("LEFT OUTER JOIN TABLE B ON A.COLUMN = B.COLUMN ")
	 * @param join
	 * @return
	 */
	public Update join(String join) {
		this.join.setLength(0);
		this.join.append(join);
		return this;
	}
	
	
	
	/**
	 * 기존 조건절을 초기화하고 조건절을 추가한다.
	 * 조건 완성형(A=B) 로 입력해야 한다.
	 * @param where
	 * @return
	 */
	public Update whereInit(String condition) {
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
	public Update where(String condition) {
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
	public Update where(String column, Object value) {
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
	public Update where(String column, Object value, String operator) {
		where(column,value,operator,DBUtils.AND);
		return this;
	}
	
	/**
	 * 조건절을 추가한다. 기존 조건이 있으면 OR 로 조건이 추가된다.
	 * 조건 완성형(A=B) 로 입력해야 한다.
	 * @param condition
	 * @return
	 */
	public Update whereOr(String condition) {
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
	public Update whereOr(String column, Object value) {
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
	public Update whereOr(String column, Object value, String operator) {
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
	
	public Update where(String column,Object value,String operator,String logicalOperator){
		String q = DBUtils.where(column, value, operator, logicalOperator);
		if(this.where.length() == 0) {
			this.where.append(q);
		}else {
			this.where.append(logicalOperator).append(q);
		}
		
		return this;
	}
	
	
	
	
	/**
	 * 설정된 값으로 UPDATE TABLE SET COLUMN='B'  의 쿼리 반환한다.
	 * 안전한 쿼리를 위하여 WHERE 조건이 없을 경우 실패 쿼리 형식으로 리턴된다.
	 * @return
	 */
	public String build() {
		StringBuilder sql 	= new StringBuilder();
		sql.append(UPDATE).append(table).append(DBUtils.SPACE).append(join).append(SET);
		
		for(Map.Entry<String, Object> entry : record.entrySet()) {
			sql.append(entry.getKey()).append(DBUtils.EQUAL_SIGN);
			Object o = entry.getValue();
			
			if(o == null) {
				sql.append(DBUtils.NULL);
			}else {
				if(o instanceof java.lang.Number) {
					sql.append(o);
				}else if(o instanceof Boolean) {
					boolean b = (Boolean)o;
					sql.append(b ? 1:0);
				}else{
					sql.append(DBUtils.APOSTROPHE);
					sql.append(CommonUtils.toString(o).replaceAll(DBUtils.APOSTROPHE, "\\\\'" ));
					sql.append(DBUtils.APOSTROPHE);
				}
			}
			sql.append(" ");
			sql.append(DBUtils.COMMA);
		}
		
		sql.deleteCharAt(sql.length() -1);

		sql.append(DBUtils.WHERE);
		if(where.length() == 0) {
			sql.append(" UPDATE WITHOUT A CONDITIONAL CLAUSE IS REJECTED!");
		}else {
			sql.append(where);
		}
		
		return sql.toString();
	}
	
	
	/**
	 * 설정된 값으로 UPDATE TABLE SET COLUMN= ?  의 쿼리 반환한다.
	 * 안전한 쿼리를 위하여 WHERE 조건이 없을 경우 실패 쿼리 형식으로 리턴된다.
	 * @return
	 */
	public String buildPreparedQuery() {
		StringBuilder sql 	= new StringBuilder();
		sql.append(UPDATE).append(table).append(DBUtils.SPACE).append(join).append(SET);
		
		for(String key : record.keySet()) {
			sql.append(key)
			.append(DBUtils.EQUAL_SIGN)
			.append(DBUtils.QUESTION_MARK)
			.append(DBUtils.COMMA);
		}
		
		sql.deleteCharAt(sql.length() -1);
		sql.append(DBUtils.WHERE);
		if(where.length() == 0) {
			sql.append(" UPDATE WITHOUT A CONDITIONAL CLAUSE IS REJECTED!");
		}else {
			sql.append(where);
		}
		
		return sql.toString();
	}
	
	
	
	
	
	/**
	 * 설정된 값으로 UPDATE 를 실행한다.
	 * UPDATE 완료 후에는 init() 이 자동 호출된다.
	 * @return
	 */
	public int update(){
		
		
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
			
			DBUtils.setValues(pstmt,record);
			
			/* 아래 코드도 정상 동작할 것이다. 그러나 정확한 형변환을하기 위하여 위의 방법을 사용한다.
			List<Object> list = new ArrayList<>(record.values());
			for(int idx=0; idx < list.size(); idx++) {
				pstmt.setObject(idx+1, list.get(idx));
			}*/	
			result  	= pstmt.executeUpdate();
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
	
	
	/**
	 * 설정된 값으로 UPDATE 를 실행한다.
	 * UPDATE 완료 후에는 init() 이 자동 호출되지 않는다.
	 * @return
	 */
	public int updateNoInit(){
		
		
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
			
			DBUtils.setValues(pstmt,record);
			
			/* 아래 코드도 정상 동작할 것이다. 그러나 정확한 형변환을하기 위하여 위의 방법을 사용한다.
			List<Object> list = new ArrayList<>(record.values());
			for(int idx=0; idx < list.size(); idx++) {
				pstmt.setObject(idx+1, list.get(idx));
			}*/	
			result  	= pstmt.executeUpdate();
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
			
		}
		return result;
	}
	
	
	/**
	 * 지정된 쿼리를 실행한다.
	 * @param query
	 * @return
	 */
	public int update(String query){
		
		
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
	
	
	
	public LinkedMap<String,Object> getRecord(){
		return record;
	}
	
	
	
	
	/**
     * 비동기 UPDATE
     * AsyncExecutor를 사용하여 스레드 풀에서 실행
     * 
     * @return CompletableFuture<Integer> - 완료 시 수정된 행 수 반환
     */
    public CompletableFuture<Integer> updateAsync() {
        // 스레드 안전성을 위한 불변 복사본
        final LinkedMap<String, Object> snapshot = new LinkedMap<>(record);
        final String query = buildPreparedQuery();
        final boolean enableLog = deepview;
        
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
                DBUtils.setValues(pstmt, snapshot);
                result = pstmt.executeUpdate();
                
                conn.commit();
                
                if (enableLog) {
                    logger.info("Async update completed: {} row(s) updated", result);
                }
                
            } catch (Exception e) {
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        logger.error("Rollback failed", rollbackEx);
                    }
                }
                logger.error("Async update failed: {}", CommonUtils.getExceptionMessage(e));
                throw new RuntimeException("Async update failed", e);
                
            } finally {
                if (db != null) {
                    db.close(pstmt);
                    db.close(conn);
                }
                
                if (enableLog) {
                    logger.info("Async update query: [{}]", query);
                    logger.info(SystemUtils.getElapsedTime(System.nanoTime() - startTime));
                }
            }
            
            return result;
            
        }, AsyncExecutor.getExecutor());
    }
    
    /**
     * 비동기 UPDATE 후 자동 init()
     * 
     * @return CompletableFuture<Integer>
     */
    public CompletableFuture<Integer> updateAsyncAndInit() {
        return updateAsync().thenApply(result -> {
            init();
            return result;
        });
    }
    
	
	
	
}

