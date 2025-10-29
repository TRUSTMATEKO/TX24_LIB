package kr.tx24.lib.db;

public enum DBType {
	
		MARIADB("org.mariadb.jdbc.Driver"			, "jdbc:mariadb://%s/%s"),
	    MYSQL("com.mysql.cj.jdbc.Driver"			, "jdbc:mysql://%s/%s"),
	    POSTGRESQL("org.postgresql.Driver"			, "jdbc:postgresql://%s/%s"),
	    ORACLE("oracle.jdbc.OracleDriver"			, "jdbc:oracle:thin:@%s"),
	    MSSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://%s"),
	    SQLITE("org.sqlite.JDBC"					, "jdbc:sqlite:%s"),
	    H2("org.h2.Driver"							, "jdbc:h2:%s"),
	    H2_MEM("org.h2.Driver"						, "jdbc:h2:mem:%s"),
	    DB2("com.ibm.db2.jcc.DB2Driver"				, "jdbc:db2://%s"),
	    SYBASE("com.sybase.jdbc4.jdbc.SybDriver"	, "jdbc:sybase:Tds:%s"),
	    INFORMIX("com.informix.jdbc.IfxDriver"		, "jdbc:informix-sqli://%s"),
	    HSQLDB("org.hsqldb.jdbc.JDBCDriver"			, "jdbc:hsqldb:%s");
	   

	    private final String driver;
	    private final String pattern;

	    DBType(String driver, String pattern) {
	        this.driver = driver;
	        this.pattern = pattern;
	    }

	    public String getDriver() {
	        return driver;
	    }

	    public String getPattern() {
	        return pattern;
	    }
	
	
	
	
}
