package kr.tx24.lib.db;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.DateUtils;
import kr.tx24.lib.lang.SecurityUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;

/**
 * 
 */
public class DBUtils {
	private static Logger logger 		= LoggerFactory.getLogger(DBUtils.class);
	
	

	public static final String AND					= " AND ";
	public static final String OR					= " OR ";
	public static final String WHERE				= " WHERE ";
	public static final String DATE_FORMAT			= "DATE_FORMAT(";
	public static final String DATE_FORMAT_MYSQL	= ",'%Y%m%d%H%i%s')";
	public static final String NULL					= "NULL";
	public static final String IS_NULL				= " IS NULL";
	public static final String IS_NOT_NULL			= " IS NOT NULL";
	public static final String EQUAL_SIGN			= "=";
	public static final String LEFT_PARENTHESIS	    = "(";
	public static final String RIGHT_PARENTHESIS	= ")";
	public static final String APOSTROPHE			= "'";
	public static final String COMMA				= ",";
	public static final String QUESTION_MARK		= "?";
	public static final String SPACE				= " ";
	
	
	public static final String eq					= "eq";
	public static final String ne					= "ne";
	public static final String gt					= "gt";
	public static final String ge					= "ge";
	public static final String lt					= "lt";
	public static final String le					= "le";
	public static final String lk					= "lk";
	public static final String nk					= "nk";
	public static final String in					= "in";
	public static final String ni					= "ni";
	public static final String bt					= "bt";
	public static final String fneq					= "fneq";
	public static final String fnne					= "fnne";
	public static final String fngt					= "fngt";
	public static final String fnge					= "fnge";
	public static final String fnlt					= "fnlt";
	public static final String fnle					= "fnle";
	public static final String fnin					= "fnin";
	public static final String fnni					= "fnni";
	
	public static final String isnull				= "isnull";
	public static final String isnotnull			= "isnotnull";
	
	/**
	 * 
	 */
	public DBUtils() {
		// TODO Auto-generated constructor stub
	}
	
	
	public static String getIn(Collection<?> collection) {
		if(collection != null && collection.size() !=0) {
			StringBuilder sb = new StringBuilder();
			collection.forEach((temp) -> {
				if(temp instanceof Number) {
					sb.append(DBUtils.COMMA).append(temp);
				}else {
					sb.append(DBUtils.COMMA).append(DBUtils.APOSTROPHE).append(temp).append(DBUtils.APOSTROPHE);
				}
			});
			return sb.substring(1);
		}else {
			return "";
		}
	}
	
	

	
	public static String getOperator(String cond){
		cond = cond.toLowerCase();
		if(cond.equals(eq) || cond.equals(fneq)){
			return " = ";
		}else if(cond.equals(ne) || cond.equals(fnne)){
			return " != ";
		}else if(cond.equals(gt) || cond.equals(fngt)){
			return " > ";
		}else if(cond.equals(ge) || cond.equals(fnge)){
			return " >= ";
		}else if(cond.equals(lt) || cond.equals(fnlt)){
			return " < ";
		}else if(cond.equals(le) || cond.equals(fnle)){
			return " <= ";
		}else if(cond.equals(lk)){
			return " LIKE ";
		}else if(cond.equals(nk)){
			return " NOT LIKE ";
		}else if(cond.equals(in) || cond.equals(fnin)){
			return " IN ";
		}else if(cond.equals(ni) || cond.equals(fnni)){
			return " NOT IN ";
		}else if(cond.equals(bt)){
			return " BETWEEN ";
		}else{
			return cond;
		}
	}
	
	
	/**
	 * operator 는 eq,fneq = , ne,fnne	!= , gt,fngt > , ge.fnge >= , lt,fnlt < , le <= , lk LIKE, nk NOT LIKE, in,fnin IN , ni,fnni NOT IN, bt BETWEEN
	 * logicalOpetator = " AND " , " OR " 앞뒤 반드시 공백이 필요하다. 
	 * IS NULL     where("column",null,eq," AND ")  operator 가 eq 가 아니라면 모두 IS NOT NULL 처리된다.
	 * IS NOT NULL where("column",null,ne," AND ")
	 * SUBQUERY = where("column","SELECT A FROM DUAL",fnin," AND ")
	 * FUNCTION = where("FUNCTION('a')","1",eq," AND ")
	 * FUNCTION = where("column","FUNCTION('a')",fneq," AND ")
	 * IN ( SUBQUERY ) , Function 등을 WHERE 에 사용할때는 fneq, fnne, fngt, fnge, fnlt 등을 사용해야 한다. 
	 * @param column
	 * @param value
	 * @param operatorator
	 */
	
	public static String where(String column,Object value,String operator,String logicalOperator){
		
		//안전한 쿼리 형성을 위하여 데이터의 속성 등을 검증한다.
		if(CommonUtils.isNull(column)) {
			return "";
		}
		if(CommonUtils.isNull(operator)) {
			operator = eq;
		}
		if(CommonUtils.isNull(logicalOperator)) {
			operator = AND ;
		}
		
		if(value instanceof java.util.Date) {
			java.util.Date d = (java.util.Date)value;
			value = new Timestamp(d.getTime());
		}
		
		if(value instanceof java.sql.Date) {
			java.sql.Date d = (java.sql.Date)value;
			value = new Timestamp(d.getTime());
		}
		
		operator = operator.toLowerCase();
		
		StringBuilder sb = new StringBuilder();
		
		if(operator.equals(isnull)) {
			return sb
				.append(column)
				.append(IS_NULL)
				.toString();
		}else if(operator.equals(isnotnull)) {
			return sb
				.append(column)
				.append(IS_NOT_NULL)
				.toString();
		}else {}
		
		if(value instanceof Number) {
			if(operator.equals(lk) || operator.equals(nk) || operator.equals(bt)) {
				logger.info("'like','not like','between' operation are not supported, Number Format : {}" , CommonUtils.toString(value));
				return "";
			}else {
				if(operator.equals(in) || operator.equals(ni) || operator.equals(fnin) || operator.equals(fnni)) {
					sb
					.append(column)
					.append(getOperator(operator))
					.append(LEFT_PARENTHESIS)
					.append(CommonUtils.toString(value))
					.append(RIGHT_PARENTHESIS);
				
				}else {
					sb
					.append(column)
					.append(getOperator(operator))
					.append(CommonUtils.toString(value));
				}
			}
		}else if(value instanceof java.sql.Timestamp) {
			if(operator.equals(lk) || operator.equals(nk) || operator.equals(bt)) {
				logger.info("'like','not like','between' operation are not supported, Timestamp Format : {}" , CommonUtils.toString(value));
				return "";
			}else { 
				sb
				.append(DATE_FORMAT)
				.append(column)
				.append(DATE_FORMAT_MYSQL)
				.append(getOperator(operator))
				.append(APOSTROPHE)
				.append(DateUtils.toString((Timestamp)value))
				.append(APOSTROPHE);
			}
		}else {
			
			if(operator.equals(lk) || operator.equals(nk)) {
				sb
				.append(column)
				.append(getOperator(operator))
				.append("'%")
				.append(CommonUtils.toString(value))
				.append("%'");
			}else if(operator.equals(in) || operator.equals(ni) || operator.equals(fnin) || operator.equals(fnni)) {
				sb
				.append(column)
				.append(getOperator(operator))
				.append(LEFT_PARENTHESIS)
				.append(CommonUtils.toString(value))
				.append(RIGHT_PARENTHESIS);
				
			}else if(operator.equals(bt)) {
				String[] scope = CommonUtils.toString(value).split(COMMA);
				if(scope.length == 1) {
					sb
					.append(column)
					.append(getOperator(ge))
					.append(scope[0]);
				}else {
					sb
					.append(column)
					.append(getOperator(operator))
					.append(scope[0])
					.append(AND)
					.append(scope[1]);
				}
			}else if(operator.startsWith("fn")){
				sb
				.append(column)
				.append(getOperator(operator))
				.append(CommonUtils.toString(value));
			}else {
				if(value == null) {
					sb.append(column);
					if(operator.equals(eq)) {
						sb.append(IS_NULL);
					}else{
						sb.append(IS_NOT_NULL);
					}
				}else {
					sb
					.append(column)
					.append(getOperator(operator))
					.append(APOSTROPHE)
					.append(CommonUtils.toString(value))
					.append(APOSTROPHE);
				}
			}
		
		}
		

		
		return sb.toString();
	}
	
	

	/**
	 * TABLE 이름 지정, sbsCommit 단계적 commit 10개 단위 commit 실행 여부 
	 * @param table
	 * @param columns
	 * @param datas
	 * @param sbsCommit
	 * @return
	 * @throws Exception
	 */
	public long insertBulk(String table,List<String> columns, List<List<Object>> datas,boolean sbsCommit) throws DBException{
		return insertBulk(table, columns, datas, sbsCommit,1024*1024);
	}
	
	public long insertBulk(String table,List<String> columns, List<List<Object>> datas,boolean sbsCommit, int insertMaxSize) throws DBException{
		
		if(columns == null || datas == null || datas.get(0) == null){
			throw new DBException("columns or datas is null");
		}
		
		
		if(columns.size() != datas.get(0).size()){
			throw new DBException("columns & data 의 사이즈가 일치하지 않습니다.");
		}
		
		
		logger.info("make list to values pattern");
		int maxRowLength = 0;
		List<String> values = new ArrayList<String>();
		StringBuilder valBuf = null;
		for(List<Object> data : datas){
			valBuf = new StringBuilder();
			
			
			for(Object obj : data){
				valBuf.append(",");
				
				if(obj instanceof String){
					valBuf.append("'")
							.append(((String)obj).replaceAll("'", "\\\\'" ))
							.append("'");
				}else if(obj instanceof Integer || obj instanceof Long || obj instanceof Double){
					valBuf.append(obj);
				}else if(obj instanceof Byte){
					Byte b = (Byte)obj;
					valBuf.append("'")
							.append(b.toString())
							.append("'");
				}else if(obj instanceof byte[]){
					byte[] b = (byte[])obj;
					valBuf.append("'")
							.append(new String(b))
							.append("'");
				}else if(obj instanceof Character){
					Character c = (Character)obj;
					valBuf.append("'")
							.append(String.valueOf(c))
							.append("'");
				}else if(obj instanceof char[]){
					char[] c = (char[])obj;
					valBuf.append("'")
							.append(String.valueOf(c).replaceAll("'", "\\\\'" ))
							.append("'");
				}else if(obj instanceof BigInteger){
					BigInteger b =  (BigInteger)obj;
					valBuf.append(b.toString());
				}else if(obj instanceof BigDecimal){
					BigDecimal b =  (BigDecimal)obj;
					valBuf.append(b.toString());
				}else if(obj instanceof Boolean){
					Boolean b =  (Boolean)obj;
					valBuf.append(b.toString());
				}else if(obj instanceof Timestamp){
					Timestamp t =  (Timestamp)obj;
					valBuf.append("'")
							.append(t.toString())
							.append("'");
				}else if(obj instanceof Date){
					Date d =  (Date)obj;
					valBuf.append("'")
							.append(new Timestamp(d.getTime()).toString())
							.append("'");
				}else if(obj instanceof String[]){
					String[] s = (String[])obj;
					valBuf.append("'")
							.append(Arrays.toString(s).replaceAll("'", "\\\\'" ))
							.append("'");
					
				}else {
					valBuf.append("'")
							.append(obj.toString())
							.append("'");
				}
			}
			
			values.add("("+valBuf.substring(1)+")");
			int rowLength = ("("+valBuf.substring(1)+")").getBytes().length;
			if(maxRowLength < rowLength){
				maxRowLength = rowLength;
			}
		}
		
		int bulkRows = (insertMaxSize/maxRowLength);
		if(insertMaxSize % maxRowLength != 0){
			bulkRows++;
		}
		
		
		if(SystemUtils.deepview()) {
			logger.info("bulk insert maxRowLength : {}",maxRowLength);
			logger.info("bulk insert bulk rows : {}",bulkRows);
		}
		
		
		StringBuilder columnBuf = new StringBuilder();
		for(String column : columns){
			columnBuf.append(",`").append(column).append("`");
		}
		
		
		
		String q = "INSERT INTO "+table+" ("+columnBuf.substring(1)+") VALUES %s";
		
		if(SystemUtils.deepview()) {
			logger.info("query initialize : {}",q);
		}
		
		int bulkSize = values.size() / bulkRows;
		if(values.size()%bulkRows !=0){
			bulkSize++;
		}
		
		List<String> bulkQuerys = new ArrayList<String>();
		for(int i=0 ; i < bulkSize ; i++){
			if(i == bulkSize-1){
				bulkQuerys.add(String.format(q,String.join(",",values.subList(i*bulkRows, values.size()))));
			}else{
				bulkQuerys.add(String.format(q,String.join(",",values.subList(i*bulkRows, (i+1)*bulkRows))));
			}
		}
		
		
		long ret = 0;
		long startTime = System.nanoTime();
		
		DBManager db 		= null;
		Statement stmt 		= null;
		Connection 	conn	= null;
	
		try {
			db 			= DBFactory.get();
			conn		= db.getConnection();
			if(sbsCommit){
				conn.setAutoCommit(false);
			}
			stmt		= conn.createStatement();
			for(int i=0 ; i < bulkQuerys.size() ; i++){
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
				ret += stmt.executeUpdate(bulkQuerys.get(i));
				logger.info("inserted  : {} ",ret);
				if((i+1)%10 == 0 && sbsCommit){	//10개 단위 commit 
					conn.commit();
				}
			}
			
			conn.commit();
		}catch(SQLException t){
			try { conn.rollback();}catch(SQLException c) {}
			throw new DBException("Failed to execute insert", q, t);
		}finally {
			db.close(stmt);
			db.close(conn);
			if(SystemUtils.deepview()) {
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
		}
		
		return ret;
	}
	
	
	public RecordSet preparedStatementExecute(String query,Object... record) throws DBException {
		PreparedStatement pstmt 	= null;
		ResultSet rset	= null;
		Connection conn = null;
		RecordSet records = null;
		DBManager db 		= null;
		try {
			db 	= DBFactory.get();
			conn = db.getConnection();
			conn.setAutoCommit(false);
			pstmt = conn.prepareStatement(query);
			setValues(pstmt,Arrays.asList(record));
			pstmt.executeQuery();
			rset = pstmt.getResultSet();
			
			if(rset != null){
				records = new RecordSet(rset);
			}
			
			conn.commit();
			
		}catch(SQLException e) {
			try { conn.rollback();}catch(SQLException c) {}
			throw new DBException("Failed to execute update", query, e);
		}finally{
			db.close(conn, pstmt, rset);
			if(SystemUtils.deepview()) {
				logger.info("query : [{}]",query);
				logger.info("args  : {}",Arrays.asList(record));
				if(records == null){
					logger.info("result: [{}] ",0);
				}else{
					logger.info("result: [{}] ",records.size());
				}
			}
		}
		
		if(records == null) records = null;  // records = new RecordSet(null);
		return records;
	}
	
	
	public int preparedExecuteUpdate(String query,Object... record) throws DBException{
		PreparedStatement pstmt = null;
		Connection 	conn			= null;
		int result = 0;
		DBManager db 		= null;
		try {
			
			db 		= DBFactory.get();
			conn 	= db.getConnection();
			conn.setAutoCommit(false);
			
			pstmt	= conn.prepareStatement(query);
			setValues(pstmt, Arrays.asList(record));
			result  	= pstmt.executeUpdate();
			conn.commit();
		}catch(SQLException t){
			try { conn.rollback();}catch(SQLException c) {}
			throw new DBException("Failed to execute update", query, t);
		}finally {
			db.close(pstmt);
			db.close(conn);
			if(SystemUtils.deepview()) {
				logger.info("query : [{}]",query);
				logger.info("result: [{}]",result);
			}
		}
		return result;
	}
	
	
	/**
     * PreparedStatement에 가변인자로 값 설정
     * @param ps PreparedStatement
     * @param args 바인딩할 값들
     */
	public static void setValues(PreparedStatement ps, Object... args) throws SQLException {
        if (args == null || args.length == 0) {
            return;
        }

        int idx = 1;
        for (Object arg : args) {
            bindValue(ps, idx++, arg);
        }
    }
	
	
	
	/**
     * PreparedStatement에 Map의 값들을 순서대로 설정
     * @param ps PreparedStatement
     * @param record 순서가 보장되는 Map (LinkedHashMap, SortedMap, LinkedMap)
     */
	public static void setValues(PreparedStatement ps, Map<String, ?> record) throws SQLException {
        if (ps == null) {
            throw new IllegalArgumentException("PreparedStatement is null");
        }
        if (record == null || record.isEmpty()) {
            return;
        }

        // 순서 보장 Map 타입 체크
        boolean ordered = (record instanceof LinkedHashMap) ||
                         (record instanceof SortedMap) ||
                         record.getClass().getName().contains("LinkedMap");

        if (!ordered) {
            throw new IllegalArgumentException(
                "Record Map must be order-preserving (e.g. LinkedHashMap, SortedMap or LinkedMap). " +
                "Provided: " + record.getClass().getName()
            );
        }

        int idx = 1;
        for (Object value : record.values()) {
            bindValue(ps, idx++, value);
        }
    }
	
	
	/**
     * PreparedStatement에 LinkedMap의 값들을 순서대로 설정
     * @param ps PreparedStatement
     * @param record LinkedMap
     */
    public static void setValues(PreparedStatement ps, LinkedMap<String, ?> record) throws SQLException {
        if (ps == null) {
            throw new IllegalArgumentException("PreparedStatement is null");
        }
        if (record == null || record.isEmpty()) {
            return;
        }

        int idx = 1;
        for (Object value : record.values()) {
            bindValue(ps, idx++, value);
        }
    }
	
    /**
     * PreparedStatement에 Collection의 값들을 순서대로 설정
     * @param ps PreparedStatement
     * @param values Collection
     */
    public static void setValues(PreparedStatement ps, Collection<?> values) throws SQLException {
        if (ps == null) {
            throw new IllegalArgumentException("PreparedStatement is null");
        }
        if (values == null || values.isEmpty()) {
            return;
        }

        int idx = 1;
        for (Object value : values) {
            bindValue(ps, idx++, value);
        }
    }
    
    /**
     * 단일 값을 PreparedStatement의 지정된 인덱스에 바인딩
     * @param ps PreparedStatement
     * @param idx 파라미터 인덱스 (1부터 시작)
     * @param arg 바인딩할 값
     */
    private static void bindValue(PreparedStatement ps, int idx, Object arg) throws SQLException {
        try {
            if (arg == null) {
                ps.setObject(idx, null);
                
            } else if (arg instanceof String s) {
                if (DBManager.injectionFilter) {
                    ps.setString(idx, SecurityUtils.escapeHtml(s));
                } else {
                    ps.setString(idx, s);
                }
                
            } else if (arg instanceof Integer i) {
                ps.setInt(idx, i);
                
            } else if (arg instanceof Long l) {
                ps.setLong(idx, l);
                
            } else if (arg instanceof Double d) {
                ps.setDouble(idx, d);
                
            } else if (arg instanceof Float f) {
                ps.setFloat(idx, f);
                
            } else if (arg instanceof BigInteger bi) {
                ps.setBigDecimal(idx, SecurityUtils.toBigDecimal(bi));
                
            } else if (arg instanceof BigDecimal bd) {
                ps.setBigDecimal(idx, bd);
                
            } else if (arg instanceof Timestamp ts) {
                ps.setTimestamp(idx, ts);
                
            } else if (arg instanceof java.sql.Date sqlDate) {
                ps.setDate(idx, sqlDate);
                
            } else if (arg instanceof java.util.Date utilDate) {
                ps.setTimestamp(idx, new Timestamp(utilDate.getTime()));
                
            } else if (arg instanceof Time t) {
                ps.setTime(idx, t);
                
            } else if (arg instanceof Boolean bool) {
                ps.setBoolean(idx, bool);
                
            } else if (arg instanceof Byte b) {
                ps.setByte(idx, b);
                
            } else if (arg instanceof byte[] bytes) {
                ps.setBytes(idx, bytes);
                
            } else {
                // 알 수 없는 타입은 setObject로 처리
                ps.setObject(idx, arg);
                
                if (logger.isDebugEnabled()) {
                    logger.debug("Parameter at index {} (type {}) bound using setObject()", 
                                idx, arg.getClass().getName());
                }
            }
            
        } catch (SQLException e) {
            logger.warn("Failed to bind parameter at index {} with value [{}] (type {}). " +
                       "Using setObject() fallback. Cause: {}",
                       idx,
                       arg,
                       arg.getClass().getName(),
                       e.toString());
            
            // 최종 fallback
            ps.setObject(idx, arg);
        }
    }
    
    
    
    
    public static int countBatchResult(int[] results) {
	    int success = 0;
	    for (int r : results) {
	        if (r >= 0) {
	            success += r;
	        } else if (r == Statement.SUCCESS_NO_INFO) {
	            success += 1;
	        }
	    }
	    return success;
	}

}

