package kr.tx24.lib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;

/**
 * 
 */
public class Retrieve {
	private static Logger logger 	= LoggerFactory.getLogger(Retrieve.class);
	private static final String SELECT				= " SELECT ";
	private static final String FROM				= " FROM ";
	private static final String GROUP_BY			= " GROUP BY ";
	private static final String HAVING				= " HAVING ";
	private static final String ORDER_BY			= " ORDER BY ";
	private static final String LIMIT				= " LIMIT ";
	private static final String INNER_JOIN			= " INNER JOIN ";
	private static final String LEFT_OUTER_JOIN		= " LEFT OUTER JOIN ";
	private static final String RIGHT_OUTER_JOIN	= " RIGHT OUTER JOIN ";
	private static final String COUNT				= " COUNT(*) AS CNT ";
	private static final String ON					= " ON ";
	private static final String ASC					= " ASC";
	private static final String DESC				= " DESC";
	private static final String ASTERISK			= " * ";
	
	private StringBuilder where		= new StringBuilder();
	private boolean deepview		= false;
	private String columns			= "";
	private String table			= "";
	private StringBuilder join 		= new StringBuilder();
	private String orderBy			= "";
	private String groupBy			= "";
	private String having			= "";
	
	private long offset				= 0;
	private long length				= 0;
			
	
	
	public Retrieve() {
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @param debug
	 */
	public Retrieve(String table) {
		this.table 		= table;
		this.deepview	= SystemUtils.deepview();
	}
	
	/**
	 * TABLE 명 지정 및 LOG 출력 여부 
	 * @param table
	 * @param debug
	 */
	public Retrieve(String table,boolean debug) {
		this.table 		= table;
		this.deepview	= debug;
	}
	
	/**
	 * TABLE 명 지정 
	 * @param table
	 * @return
	 */
	public Retrieve table(String table) {
		this.table = table;
		return this;
	}
	
	
	/*
	 * debug, columns, table, join, orderBy, groupBy, having, where, offset, length 등의 값이 초기화된다.
	 */
	public Retrieve init() {
		deepview= SystemUtils.deepview();
		columns = "";
		table 	= "";
		join.setLength(0);
		orderBy = "";
		groupBy = "";
		having 	= "";
		where.setLength(0);
		offset	= 0;
		length	= 0;
		return this;
	}
	
	/**
	 * 디버거 활성화 , 디버그는 기본 설정된 config 값을 사용한다.
	 * 디버그를 활성화 용도로만 사용된다.
	 * @return
	 */
	public Retrieve debug() {
		this.deepview = true;
		return this;
	}
	
	
	/**
	 * 가져올 columns 를 지정한다.
	 * 호출 시 기존 값을 초기화하고 다시 columns 을 지정한다.
	 * @param columns
	 * @return
	 */
	public Retrieve columns(String columns) {
		this.columns = columns;
		return this;
	}
	
	/**
	 * 가져올 columns 를 지정한다.
	 * 호출 시 기존 값에 column  을 추가한다.
	 * @param columns
	 * @return
	 */
	public Retrieve column(String column) {
		if(CommonUtils.isBlank(this.columns)) {
			this.columns = column;
		}else {
			this.columns = this.columns +DBUtils.COMMA+column;
		}
		return this;
	}
	
	
	/**
	 * 정렬 순서를 지정한다.
	 * 자동으로 ORDER_BY 가 삽입되므로 컬럼 및 순서만 정의한다.
	 * ORDER BY 여러 컬럼 지정시는 중복 호출하면 Append 처리한다.
	 * @param orderBy
	 * @return
	 */
	public Retrieve orderBy(String orderBy) {
		if(CommonUtils.isBlank(this.orderBy)) {
			this.orderBy = orderBy;
		}else {
			this.orderBy = this.orderBy + DBUtils.COMMA + orderBy;
		}
		return this;
	}
	
	/**
	 * ASC 로 정렬 순서를 지정한다.
	 * 자동으로 ORDER_BY 가 삽입되므로 컬럼 및 순서만 정의한다.
	 * ORDER BY 여러 컬럼 지정시는 중복 호출하면 Append 처리한다.
	 * @param orderBy
	 * @return
	 */
	public Retrieve orderByAsc(String orderBy) {
		if(CommonUtils.isBlank(this.orderBy)) {
			this.orderBy = orderBy + ASC;
		}else {
			this.orderBy = this.orderBy + DBUtils.COMMA + orderBy + ASC;
		}
		return this;
	}
	
	
	
	/**
	 * DESC 로 정렬 순서를 지정한다.
	 * 자동으로 ORDER BY 가 삽입되므로 컬럼 및 순서만 정의한다.
	 * ORDER BY 여러 컬럼 지정시는 중복 호출하면 Append 처리한다.
	 * @param orderBy
	 * @return
	 */
	public Retrieve orderByDesc(String orderBy) {
		if(CommonUtils.isBlank(this.orderBy)) {
			this.orderBy = orderBy + DESC;
		}else {
			this.orderBy = this.orderBy + DBUtils.COMMA + orderBy + DESC;
		}
		return this;
	}
	
	
	
	
	/**
	 * Group By 를 지정한다. 
	 * 자동으로 GROUP BY 가 삽입되므로 컬림 이름만 지정한다.
	 * 여러개의 Column 을 지정시는 중복 호출하면 Append 처리된다.
	 * @param groupBy
	 * @return
	 */
	public Retrieve groupBy(String groupBy) {
		if(CommonUtils.isBlank(this.groupBy)) {
			this.groupBy = groupBy;
		}else {
			this.groupBy = this.groupBy + DBUtils.COMMA + groupBy;
		}
		return this;
	}
	
	/**
	 * Group By 시 Having 값을 지정한다.
	 * 예 COUNT(*) >= 1 
	 * 자동으로 HAVING 가 삽입되므로 조건 값만 지정한다.
	 * 여러개의 조건값 지정시는 중복 호출하면 Append 처리된다.
	 * @param having
	 * @return
	 */
	public Retrieve having(String having) {
		if(CommonUtils.isBlank(this.having)) {
			this.having = having;
		}else {
			this.having = this.having + DBUtils.AND + having;
		}
		return this;
	}
	
	/**
	 * LIMIT 지정으로 가져올 컬럼 갯수를 지정한다.
	 * @param length
	 * @return
	 */
	public Retrieve limit(long length) {
		this.length = length;
		return this;
	}
	
	/**
	 * LIMIT 의 OFFSET 과 가져올 컬럼 갯수(LENGTH) 를 지정한다.
	 * @param offset
	 * @param length
	 * @return
	 */
	public Retrieve limit(long offset, long length) {
		this.offset = offset;
		this.length = length;
		return this;
	}
	
	/**
	 * 페이징 처리를 위한 현재 페이지 및 가져올 ROW 갯수 지정.
	 * LIMIT 를 활용한 페이징 처리시 사용한다.
	 * paging(1,10) = LIMIT 0,10 , paging(2,10) = LIMIT 10,10
	 * @param current
	 * @param size
	 * @return
	 */
	public Retrieve paging(long current, long size) {
		this.offset = size*(current-1);
		this.length = size;
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
	public Retrieve joinInner(String tableB,String on) {
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
	public Retrieve joinLeftOuter(String tableB,String on) {
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
	public Retrieve joinRightOuter(String tableB,String on) {
		this.join.append(DBUtils.SPACE + RIGHT_OUTER_JOIN + tableB + ON + on);
		return this;
	}
	
	
	/**
	 * JOIN 절 전체를 지정할 때 사용한다.
	 * 예. join("LEFT OUTER JOIN TABLE B ON A.COLUMN = B.COLUMN ")
	 * @param join
	 * @return
	 */
	public Retrieve join(String join) {
		this.join.setLength(0);
		this.join.append(join);
		return this;
	}
	
	
	/**
	 * SELECT ~ WHERE LIMIT 의 쿼리 반환
	 * @return
	 */
	public String build() {
		if(CommonUtils.isBlank(columns)) {
			columns = ASTERISK;
		}
		StringBuilder sql 	= new StringBuilder();
		
		sql.append(SELECT)
			.append(columns).append(FROM).append(this.table).append(DBUtils.SPACE).append(this.join);
		sql.append(buildCondition());
		if(length > 0) {
			if(offset > 0) {
				sql.append(LIMIT).append(offset).append(DBUtils.COMMA).append(this.length);
			}else {
				sql.append(LIMIT).append(this.length);
			}
		}
		
		return sql.toString();
	}
	
	/**
	 * LIMIT 를 제외한 WHERE 조건절 반환
	 * @return
	 */
	private StringBuilder buildCondition() {
		StringBuilder sql = new StringBuilder();
		if(this.where.length() > 1){ sql.append(DBUtils.WHERE).append(this.where);}
		if(this.groupBy.length() > 1){ sql.append(GROUP_BY).append(this.groupBy);}
		if(this.having.length() > 1){ sql.append(HAVING).append(this.having);}
		if(this.orderBy.length() > 1){ sql.append(ORDER_BY).append(this.orderBy);}
		
		return sql;
	}
	
	
	/**
	 * LIMIT 를 제외한 WHERE 조건절 반환
	 * @return
	 */
	public StringBuilder buildWhere() {
		StringBuilder sql = new StringBuilder();
		if(this.where.length() > 1){ sql.append(this.where);}
		if(this.groupBy.length() > 1){ sql.append(GROUP_BY).append(this.groupBy);}
		if(this.having.length() > 1){ sql.append(HAVING).append(this.having);}
		if(this.orderBy.length() > 1){ sql.append(ORDER_BY).append(this.orderBy);}
		
		return sql;
	}
	
	/**
	 * LIMIT 및 ORDER BY 를 지정하지 않은 전체 ROW 갯수를 반환한다.
	 * SELECT COUNT(*) FROM TABLE JOIN WHERE GROUP_BY HAVING 
	 * @return
	 */
	public long count() {
		
		StringBuilder sql 	= new StringBuilder();
		sql.append(SELECT).append(COUNT).append(FROM).append(this.table).append(this.join);
		sql.append(buildCondition());
		
		if(this.groupBy.length() > 1) {
			sql.insert(0, SELECT + COUNT + FROM +"(");
			sql.append(") GROUPSUM");
		}
		
		RecordSet rset = select(sql.toString());
		if(rset == null || !rset.next()) {
			return 0;
		} else {
			return rset.getRowFirst().getLong("CNT");
		}
	}
	
	
	/**
	 * 설정된 값으로 SELECT 문을 실행한다.
	 * select() 호출후에는 init() 이 호출된다.
	 * @return
	 */
	public RecordSet select() {
		RecordSet rset =  select(build());
		init();
		return rset;
	}
	
	
	/**
	 * 설정된 값으로 SELECT COUNT(*) 및 SELECT 문을 실행한다. 
	 * selectWithCount() 는 
	 * SELECT COUNT(*) 를 먼저 실행하고 
	 * SELECT COLUMNS FROM 을 실행한다. 
	 * COUNT(*) 로 조회된 전체 ROW 수는 RecordSet 의 getCount() 로 확인할 수 있다.
	 * 단, SELECT COLUMNS FROM 이 정상 동작하지 않는 경우 RecordSet 에서 NULL Point  오류가 발생할 수 있따.
	 * 실행 후 최종 init() 이 호출된다.
	 * @return
	 */
	public RecordSet selectWithCount() {
		
		StringBuilder countBuf 	= new StringBuilder();
		countBuf.append(SELECT).append(COUNT).append(FROM).append(this.table).append(this.join);
		countBuf.append(buildCondition());
		
		String query 			= build();
		
		DBManager db 			= null;
		Statement stmt 			= null;
		Connection 	conn		= null;
		ResultSet rset			= null;
		RecordSet records		= null;
		int result = 0;
		long count				= 0L;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			stmt		= conn.createStatement();
			//전체 Row 의 갯수를 SET 
			if(this.groupBy.length() > 1) {
				countBuf.insert(0, SELECT + COUNT + FROM +"(");
				countBuf.append(") GROUPSUM");
			}
			stmt.executeQuery(countBuf.toString());
			rset		= stmt.getResultSet();
			while(rset.next()) {
				count = rset.getLong("CNT");
			}
			rset.close();
			
			//SELECT QUERY 
			stmt.executeQuery(build());
			rset		= stmt.getResultSet();
			if(rset != null) {
				records = new RecordSet(rset);
				records.count(count);
			}
			
			
		}catch(Exception t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(conn, stmt, rset);
			
			if(deepview) {
				logger.info("query : {} = [{}]",count,countBuf.toString());
				logger.info("query : {} = [{}]",result,query);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			init();
			
		}
		return records;

	}
	
	/**
	 * 설정된 값으로 SELECT 문을 실행한다.
	 * selectNotInit() 은 실행 후 init() 을 호출하지 않는다.
	 * @return
	 */
	public RecordSet selectNotInit() {
		return select(build());
	}
	
	
	/**
	 * query string 으로 전달된 질의문 실행
	 * @param query
	 * @return
	 */
	public RecordSet select(String query){
		
		
		DBManager db 			= null;
		Statement stmt 			= null;
		Connection 	conn		= null;
		ResultSet rset			= null;
		RecordSet records		= null;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			stmt		= conn.createStatement();
			stmt.executeQuery(query);
			rset		= stmt.getResultSet();
			if(rset != null) {
				records = new RecordSet(rset);
			}
			
		}catch(Exception t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(conn, stmt, rset);
			
			if(deepview) {
				logger.info("query : {} = [{}]",query,records != null ? records.size():0);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
		}
		return records;
	}
	
	
	/**
	 * Prepared형 쿼리 및 그에 매칭되는 values 
	 * 복잡한 쿼리문에 중간중간 값을 매핑하여 처리할 때 사용한다. 
	 * @param query
	 * @param values
	 * @return
	 */
	public RecordSet select(String query,Collection<Object> values){
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		ResultSet rset			= null;
		RecordSet records		= null;
		
		long startTime = System.nanoTime();
		try {
			db			= DBFactory.get();
			conn		= db.getConnection();
			pstmt		= conn.prepareStatement(query);
			DBUtils.setValues(pstmt, values);
			rset		= pstmt.executeQuery();
			if(rset != null) {
				records = new RecordSet(rset);
			}
			
		}catch(Exception t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			db.close(conn, pstmt, rset);
			
			if(deepview) {
				logger.info("query : {} = [{}]",query,records != null ? records.size():0);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			init();
			
		}
		return records;
	}
	
	/**
	 * function 실행 및 응답값 회신
	 * @param function
	 * @param values
	 * @return
	 */
	public Object function(String function,Collection<Object> values){
		Object ret = null;
		String query = SELECT + function;
		
		
		DBManager db 			= null;
		PreparedStatement pstmt = null;
		Connection 	conn		= null;
		ResultSet rset			= null;

		long startTime = System.nanoTime();
		try {
			
			db 			= DBFactory.get();
			conn		= db.getConnection();
			pstmt		= conn.prepareStatement(query);
			DBUtils.setValues(pstmt, values);
			rset		= pstmt.executeQuery();
			
			while(rset.next()){
				ret = rset.getObject(1);
			}
		}catch(Exception t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
			ret = "";
		}finally {
			db.close(conn, pstmt, rset);
			
			if(deepview) {
				logger.info("query : {}",query);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
	
		}
		return ret;
	}
	
	
	
	
	/**
	 * 기존 조건절을 초기화하고 조건절을 추가한다.
	 * 조건 완성형(A=B) 로 입력해야 한다.
	 * @param where
	 * @return
	 */
	public Retrieve whereInit(String condition) {
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
	public Retrieve where(String condition) {
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
	public Retrieve where(String column, Object value) {
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
	public Retrieve where(String column, Object value, String operator) {
		where(column,value,operator,DBUtils.AND);
		return this;
	}
	
	
	

	
	/**
	 * 조건절을 추가한다. 기존 조건이 있으면 OR 로 조건이 추가된다.
	 * 조건 완성형(A=B) 로 입력해야 한다.
	 * @param condition
	 * @return
	 */
	public Retrieve whereOr(String condition) {
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
	public Retrieve whereOr(String column, Object value) {
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
	public Retrieve whereOr(String column, Object value, String operator) {
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
	
	public Retrieve where(String column,Object value,String operator,String logicalOperator){
		String q = DBUtils.where(column, value, operator, logicalOperator);
		if(this.where.length() == 0) {
			this.where.append(q);
		}else {
			this.where.append(logicalOperator).append(q);
		}
		
		return this;
	}
	

}


