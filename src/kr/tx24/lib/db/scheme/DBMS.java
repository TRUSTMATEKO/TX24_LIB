/**
 * SYSLINK LIBRARY 
 * @author : juseop , 2023. 11. 3.
 */
package kr.tx24.lib.db.scheme;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.map.LinkedMap;

/**
 * 
 */
public class DBMS {

	private static List<String> DEFAULT_SCHEME  = Arrays.asList("information_schema", "mysql", "performance_schema");
	private static final String DBMS_URL = "%s?user=%s&password=%s&connectTimeout=%s";
	private static final String DBMS_TIMEOUT = "60000";
	private DBConfig config = null;
	private Connection conn	= null;
	
	

	/**
	 * Properties 
	 * driver 
	 * url
	 * user
	 * passwd 
	 * autoCommit true,false
	 * @param properties
	 */
	public DBMS(Properties properties) {
		config 				= new DBConfig();
		
		config.driver		= properties.getProperty("driver", "");
		config.url			= properties.getProperty("url", "");
		config.user			= properties.getProperty("user", "");
		config.passwd		= properties.getProperty("passwd", "");
		config.autoCommit	= Boolean.parseBoolean(properties.getProperty("autoCommit", "true"));
	}
	
	
	public DBMS(String driver,String url, String user,String passwd,boolean autoCommit) {
		config 				= new DBConfig();
		
		config.driver		= driver;
		config.url			= url;
		config.user			= user;
		config.passwd		= passwd;
		config.autoCommit	= autoCommit;
	}
	
	
	
	
	public LinkedMap<String,Object> getDatabases(){
		LinkedMap<String,Object> map = new LinkedMap<String,Object>();
		
		ResultSet rset 		= null;
		try{
			conn	= getConnection();
			DatabaseMetaData md = conn.getMetaData();
			map.put("product", md.getDatabaseProductName()+" "+md.getDatabaseProductVersion());
			map.put("jdbcdriver", md.getDriverName()+" "+md.getDriverVersion());
			rset = md.getCatalogs();
			List<String> catalogs = new ArrayList<String>();
			while(rset.next()){
				String catalog = CommonUtils.nToB(rset.getString(1));
				if(CommonUtils.isNullOrSpace(catalog) || DEFAULT_SCHEME.contains(catalog)){
				}else{
					catalogs.add(rset.getString(1));
				}
			}
			Collections.sort(catalogs);
			map.put("catalogs", catalogs);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			close(rset);
			close(conn);
		}
		return map;
	}
	
	
	public LinkedMap<String,Object> getInfo(){
		LinkedMap<String,Object> map = new LinkedMap<String,Object>();
		ResultSet rset 		= null;
		try{
			conn	= getConnection();
			DatabaseMetaData md = conn.getMetaData();
			map.put("product", md.getDatabaseProductName()+" "+md.getDatabaseProductVersion());
			map.put("jdbcdriver", md.getDriverName()+" "+md.getDriverVersion());
			rset = md.getCatalogs();
			List<String> catalogs = new ArrayList<String>();
			while(rset.next()){
				String catalog = CommonUtils.nToB(rset.getString(1));
				if(CommonUtils.isNullOrSpace(catalog) || DEFAULT_SCHEME.contains(catalog)){
				}else{
					catalogs.add(rset.getString(1));
				}
			}
			Collections.sort(catalogs);
			map.put("catalogs", catalogs);
			rset.close();

			for(String catalog : catalogs){
				map.put("DB."+catalog,getCatalog(md, catalog));
			}
			
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			close(rset);
			close(conn);
		}
		return map;
	}
	
	
	public Catalog getDatabases(String catalogName){
		Catalog catalog = null;
		ResultSet rset 		= null;
		try{
			conn	= getConnection();
			DatabaseMetaData md = conn.getMetaData();
			catalog = getCatalog(md,catalogName);
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			close(rset);
			close(conn);
		}
		return catalog;
	}
	
	
	public Catalog getCatalog(DatabaseMetaData md,String catalogName){
		Catalog catalog = new Catalog();
		catalog.name = catalogName;
		ResultSet rset = null ;
		try{
			rset = md.getTables(catalogName, null, "%", null);

			catalog.tables 	= new ArrayList<Table>();
			catalog.views 	= new ArrayList<Table>();
			catalog.seqs 	= new ArrayList<Table>();
			while(rset.next()){
				Table table = new Table();
				table.n	= rset.getString("TABLE_NAME");
				String type	= rset.getString("TABLE_TYPE");
				table.r	= rset.getString("REMARKS");

				if(type.equals("TABLE")){
					catalog.tables.add(table);
				}else if(type.equals("VIEW")){
					catalog.views.add(table);
				}else if(type.equals("SEQUENCE")){
					catalog.seqs.add(table);
				}else{
				}

				table.c = getColumn(md, catalogName, table.n);
			}

			rset.close();
			rset = md.getFunctions(catalogName, null, "%");

			while(rset.next()){
				if(catalog.functions == null){
					catalog.functions = new ArrayList<Table>();
				}
				Table table = new Table();
				table.n = rset.getString("FUNCTION_NAME");
				table.r = rset.getString("REMARKS");
				catalog.functions.add(table);
			}


			rset.close();
			rset = md.getProcedures(catalogName, null, "%");
			while(rset.next()){
				if(catalog.procedures == null){
					catalog.procedures = new ArrayList<Table>();
				}
				Table table = new Table();
				table.n = rset.getString("PROCEDURE_NAME");
				table.r = rset.getString("REMARKS");
				catalog.procedures.add(table);
			}




			Collections.sort(catalog.tables, new Comparator<Table>() {
				@Override
				public int compare(Table o1, Table o2) {
					return o1.n.compareTo(o2.n);
				}
			});
			Collections.sort(catalog.views, new Comparator<Table>() {
				@Override
				public int compare(Table o1, Table o2) {
					return o1.n.compareTo(o2.n);
				}
			});
			Collections.sort(catalog.seqs, new Comparator<Table>() {
				@Override
				public int compare(Table o1, Table o2) {
					return o1.n.compareTo(o2.n);
				}
			});
			Collections.sort(catalog.functions, new Comparator<Table>() {
				@Override
				public int compare(Table o1, Table o2) {
					return o1.n.compareTo(o2.n);
				}
			});

		}catch(Exception e){
		}finally{
			try{rset.close();}catch(Exception e){}
		}

		return catalog;

	}
	
	
	
	public List<Column> getColumn(DatabaseMetaData md,String catalogName,String tableName){
		List<Column> list = new ArrayList<Column>();
		ResultSet rset = null ;
		try{

			//primarykey 
			List<String> pks = new ArrayList<String>();
			rset = md.getPrimaryKeys(catalogName, null, tableName);
			while(rset.next()){
				pks.add(rset.getString("COLUMN_NAME"));
			}
			rset.close();


			rset = md.getColumns(catalogName, null, tableName, "%");

			while(rset.next()){
				Column column = new Column();
				column.p		= rset.getInt("ORDINAL_POSITION");
				column.n 		= rset.getString("COLUMN_NAME");
				String typeName = rset.getString("TYPE_NAME").toLowerCase();
				int size		= rset.getInt("COLUMN_SIZE");
				int decimal		= rset.getInt("DECIMAL_DIGITS");
				boolean nullable= rset.getInt("NULLABLE") == 1 ? true : false;
				int dt			= rset.getInt("DATA_TYPE");
				//	String def		= CommonUtil.nToB(rset.getString("COLUMN_DEF"));
				boolean ai  	= rset.getString("IS_AUTOINCREMENT").equals("YES") ? true : false;
				column.r 		= CommonUtils.nToB(rset.getString("REMARKS"));


				StringBuilder sb = new StringBuilder()
						.append(String.format("-%20s",column.n) );

				if(typeName.indexOf("char") > -1){
					sb.append(typeName).append("(").append(size).append(") ");
				}else if(typeName.indexOf("decimal") > -1){
					sb.append(typeName).append("(").append(size).append(",").append(decimal).append(") ");
				}else if(typeName.indexOf("int") > -1){
					if(typeName.indexOf("bigint") > -1){
						dt = 18;
					}
					typeName = typeName.toLowerCase().replaceAll("unsigned","UN");
					String[] ar = typeName.split(" ");
					if(ar.length == 1) {
						sb.append(ar[0]).append("(").append(dt).append(") ").append(ar[1]).append(" ");
					}else if(ar.length == 2) {
						sb.append(ar[0]).append("(").append(dt).append(") ").append(ar[1]).append(" ");
					}else {
						sb.append(typeName);
					}
				}else{
					sb.append(typeName).append(" ");
				}


				if(nullable){
					sb.append("N ");
				}

				if(ai){
					sb.append("AI ");
				}

				if(pks.contains(column.n)){
					sb.append("PK ");
				}



				column.t = sb.toString();

				list.add(column);
			}

			Collections.sort(list, new Comparator<Column>() {
				@Override
				public int compare(Column o1, Column o2) {
					return o1.p - o2.p;
				}
			});


		}catch(Exception e){
		}finally{
			try{rset.close();}catch(Exception e){}
		}

		return list;
	}

	
	public SqlResult execute(String sql){
		return execute(sql,null);
	}

	public SqlResult execute(String sql,String database){
		String tmp = CommonUtils.ltrim(sql);
		//WHERE 조건이 없을경우 패치 한다. LIMIT 가 없을 경우 기본 LIMIT 를 추가한다.
		if(tmp.startsWith("SELECT")){
			if(tmp.indexOf(" LIMIT ") > -1){
				sql = sql+ " LIMIT 0,500";	//기본 조회 가능 수 
			}
		}

		SqlResult result = new SqlResult();

		Statement stmt	= null;
		ResultSet rset = null;
		try{
			conn	= getConnection();
			stmt	= conn.createStatement();
			stmt.setQueryTimeout(120);

			boolean hasResult = false;
			double start = System.currentTimeMillis();

			if(!CommonUtils.isNullOrSpace(database)){
				stmt.execute("USE "+database);
			}

			hasResult = stmt.execute(sql);

			double duration = System.currentTimeMillis() - start;

			StringBuilder sbw = new StringBuilder();
			SQLWarning warning = stmt.getWarnings();
			while(warning != null){
				sbw.append(" ,Warning Code:")
						.append(warning.getErrorCode())
						.append(", State:")
						.append(warning.getSQLState())
						.append(", Message:")
						.append(warning.getMessage())
						.append("\n");
				warning = warning.getNextWarning();
			}


			if(hasResult){	//SELECT 에 대한 처리 , column 이름과 결과 array return  
				rset = stmt.getResultSet();
				ResultSetMetaData meta = rset.getMetaData();
				int	cnt = meta.getColumnCount()+1;
				result.columns = new ArrayList<String>(cnt);
				for(int i = 1; i < cnt; i++) {
					result.columns.add(meta.getColumnName(i));
				}
				result.datas	= new ArrayList<List<Object>>();

				while(rset.next()){
					List<Object> data = new ArrayList<Object>(cnt);
					for(int i = 1; i < cnt; i++) {
						Object obj = rset.getObject(i);
						if(obj == null){
							data.add("null");
						}else{
							data.add(obj);
						}
					}
					result.datas.add(data);
				}
				result.msg = String.format("OK, %d row(s) returned",result.datas.size());

				result.ret = result.datas.size();

			}else{	//UPDATE , DELETE, CREATE , DROP 처리 
				result.ret = stmt.getUpdateCount();
				if(result.ret > 0){	//UPDATE, DELETE 에 대해서 영향이 있을 경우만. CREATE, DROP 등은 -1이지만  원래 autocommit 이다.
					conn.commit();
				}
				result.msg = String.format("OK, %d row(s) affected", result.ret);
			}

			result.duration = String.format("%.3f sec",(duration/1000));
			if(sbw.length() > 0){
				result.msg = result.msg+sbw.toString();
			}

		}catch(Exception e){

			if(e instanceof java.sql.SQLException){
				SQLException ex = (SQLException)e;
				result.msg = "Error Code:" + ex.getErrorCode() +". " +e.getMessage();
			}else{
				result.msg = e.getMessage();
			}
		}finally{
			close(conn,stmt,rset);
		}
		return result;
	}
	
	
	public Connection getConnection() throws Exception{
		if(config == null){
			throw new Exception("config not loaded ");
		}
		
		
		String url = "";
		if(config.driver.indexOf("mysql") > -1 || config.driver.indexOf("mariadb") > -1 ) {
			url = String.format(DBMS_URL+"&useAffectedRows=true",config.url,config.user,config.passwd,DBMS_TIMEOUT);
		}else {
			url = String.format(DBMS_URL,config.url,config.user,config.passwd,DBMS_TIMEOUT);
		}
				
		
		Class.forName(config.driver);
		conn = DriverManager.getConnection(url);
		conn.setAutoCommit(config.autoCommit);
		return conn;
	}
	
	
	private void close(Connection conn){
		if(conn != null){
			try{
				conn.close();
			}catch(SQLException e){}
		}
	}
	
	private void close(ResultSet rSet){
		if(rSet != null){
			try{
				rSet.close();
			}catch(SQLException e){}
		}
	}
	
	private void close(Statement stmt){
		if(stmt != null){
			try{
				stmt.close();
			}catch(SQLException e){}
		}
	}
		
	private void close(PreparedStatement pstmt){
		if(pstmt != null){
			try{
				pstmt.close();
			}catch(SQLException e){}
		}
	}
	
	private void close(CallableStatement cstmt){
		if(cstmt != null){
			try{
				cstmt.close();
			}catch(SQLException e){}
		}
	}
	
	private void close(Connection conn,PreparedStatement pstmt,ResultSet rset){
		close(rset);
		close(pstmt);
		close(conn);
	}
	
	private void close(Connection conn,Statement stmt,ResultSet rset){
		close(rset);
		close(stmt);
		close(conn);
	}
	
	private void close(Connection conn,CallableStatement stmt,ResultSet rset){
		close(rset);
		close(stmt);
		close(conn);
	}
	
	
	
	public static void print(ResultSet rset){
		try{
			java.sql.ResultSetMetaData rsm = rset.getMetaData();
			int cols = rsm.getColumnCount();
			for(int i=1 ; i< cols;i++){
				System.out.println(rsm.getColumnClassName(i));
				System.out.println(rsm.getColumnName(i));
				System.out.println(rsm.getColumnType(i));
				System.out.println(rsm.getColumnTypeName(i));
			}
			System.out.println(rsm.toString());
		}catch(Exception e){}
	}
	
	
	public class DBConfig {

		public String driver	= "";
		public String url		= "";
		public String user		= "";
		public String passwd	= "";
		public boolean autoCommit = false;
		
		
		public DBConfig() {
		 
		}

	}
	
	
	
	
	
	

}
