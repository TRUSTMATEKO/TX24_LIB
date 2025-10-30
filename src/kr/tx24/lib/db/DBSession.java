package kr.tx24.lib.db;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kr.tx24.lib.db.scheme.Catalog;
import kr.tx24.lib.db.scheme.Column;
import kr.tx24.lib.db.scheme.SqlResult;
import kr.tx24.lib.db.scheme.Table;
import kr.tx24.lib.lang.CommonUtils;
import kr.tx24.lib.lang.SystemUtils;
import kr.tx24.lib.map.LinkedMap;

/**
 * DBSession - 데이터베이스 연결 및 쿼리 실행 유틸리티 클래스
 * 
 * <p>이 클래스는 데이터베이스 연결을 간편하게 관리하고 쿼리를 실행할 수 있는 기능을 제공합니다.
 * AutoCloseable을 구현하여 try-with-resources 구문을 지원합니다.</p>
 * 
 * <h2>주요 기능</h2>
 * <ul>
 *   <li>메서드 체이닝을 통한 직관적인 설정</li>
 *   <li>autoClose: 쿼리 실행 후 자동으로 connection 닫기</li>
 *   <li>autoCommit: execute 실행 후 자동으로 commit</li>
 *   <li>트랜잭션 지원 (commit, rollback)</li>
 * </ul>
 * 
 * <h2>사용 예제</h2>
 * 
 * <h3>예제 1: 단순 조회 (autoClose=true, 기본값)</h3>
 * <pre>{@code
 * RecordSet records = new DBSession()
 *         .database(DBType.MARIADB)
 *         .host("localhost:3306/mydb")
 *         .username("root")
 *         .password("password")
 *         .select("SELECT * FROM users");
 * // 쿼리 실행 후 자동으로 connection이 닫힘
 * }</pre>
 * 
 * <h3>예제 2: 단순 업데이트 (autoClose=true, autoCommit=true, 기본값)</h3>
 * <pre>{@code
 * int result = new DBSession()
 *         .database(DBType.MARIADB)
 *         .host("localhost:3306/mydb")
 *         .username("root")
 *         .password("password")
 *         .execute("UPDATE users SET status = 'active' WHERE id = 1");
 * // 자동으로 commit되고 connection이 닫힘
 * }</pre>
 * 
 * <h3>예제 3: 옵션 추가</h3>
 * <pre>{@code
 * RecordSet records = new DBSession()
 *         .database(DBType.POSTGRESQL)
 *         .host("localhost:5432/testdb")
 *         .username("postgres")
 *         .password("mypassword")
 *         .option("ssl", "true")
 *         .option("sslmode", "require")
 *         .select("SELECT * FROM products");
 * }</pre>
 * 
 * <h3>예제 4: 트랜잭션 처리 (autoClose=false, autoCommit=false)</h3>
 * <pre>{@code
 * try (DBSession session = new DBSession()
 *         .database(DBType.MARIADB)
 *         .host("localhost:3306/mydb")
 *         .username("root")
 *         .password("password")
 *         .autoClose(false)    // connection 유지
 *         .autoCommit(false)) { // 수동 commit
 *     
 *     try {
 *         // 여러 쿼리 실행 (commit 대기)
 *         session.execute("UPDATE accounts SET balance = balance - 1000 WHERE id = 1");
 *         session.execute("UPDATE accounts SET balance = balance + 1000 WHERE id = 2");
 *         session.execute("INSERT INTO transactions (from_id, to_id, amount) VALUES (1, 2, 1000)");
 *         
 *         // 모두 성공하면 수동 commit
 *         session.commit();
 *         System.out.println("트랜잭션 성공!");
 *         
 *     } catch (Exception e) {
 *         // 하나라도 실패하면 rollback
 *         session.rollback();
 *         System.out.println("트랜잭션 실패 - 모든 변경사항 취소");
 *         throw e;
 *     }
 * }
 * }</pre>
 * 
 * <h3>예제 5: SQLite 사용</h3>
 * <pre>{@code
 * RecordSet records = new DBSession()
 *         .database(DBType.SQLITE)
 *         .host("test.db")
 *         .select("SELECT * FROM settings");
 * }</pre>
 * 
 * <h3>예제 6: 생성자 사용 (기존 방식)</h3>
 * <pre>{@code
 * try (DBSession session = new DBSession(
 *         DBType.MARIADB,
 *         "localhost:3306/testdb",
 *         "root",
 *         "password")) {
 *     
 *     RecordSet records = session.select("SELECT * FROM users");
 * }
 * }</pre>
 * 
 * @author tx24
 * @version 2.0
 * @see DBType
 * @see RecordSet
 */
public class DBSession implements AutoCloseable{
	

	private static Logger logger = LoggerFactory.getLogger(DBSession.class);
	
	private static List<String> DEFAULT_SCHEME = Arrays.asList("information_schema", "mysql", "performance_schema");
	
	
	/** 데이터베이스 연결 객체 */
    private Connection connection;
    
    /** 데이터베이스 타입 (MYSQL, POSTGRESQL, ORACLE 등) , 기본값은 MARIADB*/
    private DBType dbType = DBType.MARIADB;
    
    /** 데이터베이스 호스트 주소 (예: localhost:3306/mydb) */
    private String host;
    
    /** 데이터베이스 사용자명 */
    private String username;
    
    /** 데이터베이스 비밀번호 */
    private String password;
    
    /** JDBC 연결 옵션 (ssl, serverTimezone 등) */
    private Properties options;
    
    /** 
     * 자동 Close 설정
     * <ul>
     *   <li>true (기본): select()나 execute() 실행 후 자동으로 connection 닫기</li>
     *   <li>false: 수동으로 close() 호출 필요 (여러 쿼리 실행 시)</li>
     * </ul>
     */
    private boolean autoClose = true;
    
    /** 
     * 자동 Commit 설정
     * <ul>
     *   <li>true (기본): execute() 실행 후 자동으로 commit</li>
     *   <li>false: 수동으로 commit() 호출 필요 (트랜잭션 처리 시)</li>
     * </ul>
     */
    private boolean autoCommit = true;
    
    /**
     * 기본 생성자
     * 
     * <p>메서드 체이닝 방식으로 설정할 때 사용합니다.</p>
     * 
     * <pre>{@code
     * DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password");
     * }</pre>
     */
    public DBSession() {
        this.options = new Properties();
    }
    
    /**
     * DBSession 생성자
     * 
     * <p>데이터베이스 연결 정보를 직접 지정하여 생성합니다.</p>
     * 
     * @param dbType 데이터베이스 타입 (예: DBType.MARIADB, DBType.POSTGRESQL)
     * @param host 호스트 주소 (예: "localhost:3306/mydb", "192.168.1.100:5432/testdb")
     * @param username 데이터베이스 사용자명
     * @param password 데이터베이스 비밀번호
     * 
     * @see DBType
     * 
     * <pre>{@code
     * DBSession session = new DBSession(
     *         DBType.MARIADB,
     *         "localhost:3306/mydb",
     *         "root",
     *         "password");
     * }</pre>
     */
    public DBSession(DBType dbType, String host, String username, String password) {
        this(dbType, host, username, password, null);
    }
    
    /**
     * DBSession 생성자 (옵션 포함)
     * 
     * <p>데이터베이스 연결 정보와 추가 옵션을 지정하여 생성합니다.</p>
     * 
     * @param dbType 데이터베이스 타입
     * @param host 호스트 주소
     * @param username 사용자명
     * @param password 비밀번호
     * @param options 추가 연결 옵션 (예: ssl, serverTimezone 등)
     * 
     * <pre>{@code
     * Properties props = new Properties();
     * props.setProperty("useSSL", "true");
     * props.setProperty("serverTimezone", "UTC");
     * 
     * DBSession session = new DBSession(
     *         DBType.MARIADB,
     *         "localhost:3306/mydb",
     *         "root",
     *         "password",
     *         props);
     * }</pre>
     */
    public DBSession(DBType dbType, String host, String username, String password, Properties options) {
        this.dbType = dbType;
        this.host = host;
        this.username = username;
        this.password = password;
        this.options = options != null ? options : new Properties();
    }
    
    /**
     * 데이터베이스 타입 설정
     * 
     * <p>메서드 체이닝을 지원합니다.</p>
     * 
     * @param dbType 데이터베이스 타입 (예: DBType.MARIADB, DBType.POSTGRESQL)
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * DBSession session = new DBSession().database(DBType.MARIADB);
     * }</pre>
     */
    public DBSession database(DBType dbType) {
        this.dbType = dbType;
        return this;
    }
    
    /**
     * 호스트 주소 설정
     * 
     * <p>메서드 체이닝을 지원합니다.</p>
     * 
     * @param host 호스트 주소 (형식: "host:port/database")
     *             <ul>
     *               <li>MySQL/MariaDB/PostgreSQL: "localhost:3306/mydb"</li>
     *               <li>Oracle: "localhost:1521:ORCL"</li>
     *               <li>SQLite: "test.db"</li>
     *             </ul>
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * DBSession session = new DBSession().host("localhost:3306/mydb");
     * }</pre>
     */
    public DBSession host(String host) {
        this.host = host;
        return this;
    }
    
    /**
     * 사용자명 설정
     * 
     * <p>메서드 체이닝을 지원합니다.</p>
     * 
     * @param username 데이터베이스 사용자명
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * DBSession session = new DBSession().username("root");
     * }</pre>
     */
    public DBSession username(String username) {
        this.username = username;
        return this;
    }
    
    /**
     * 비밀번호 설정
     * 
     * <p>메서드 체이닝을 지원합니다.</p>
     * 
     * @param password 데이터베이스 비밀번호
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * DBSession session = new DBSession().password("mypassword");
     * }</pre>
     */
    public DBSession password(String password) {
        this.password = password;
        return this;
    }
    
    /**
     * JDBC 연결 옵션 설정 (개별 설정)
     * 
     * <p>메서드 체이닝을 지원하며, 여러 번 호출하여 여러 옵션을 설정할 수 있습니다.</p>
     * 
     * @param key 옵션 키 (예: "useSSL", "serverTimezone", "characterEncoding")
     * @param value 옵션 값 (예: "true", "UTC", "UTF-8")
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * DBSession session = new DBSession()
     *         .option("useSSL", "true")
     *         .option("serverTimezone", "UTC")
     *         .option("characterEncoding", "UTF-8");
     * }</pre>
     */
    public DBSession option(String key, String value) {
        this.options.setProperty(key, value);
        return this;
    }
    
    /**
     * JDBC 연결 옵션 일괄 설정
     * 
     * <p>Properties 객체로 여러 옵션을 한 번에 설정합니다. 메서드 체이닝을 지원합니다.</p>
     * 
     * @param options JDBC 연결 옵션을 담은 Properties 객체
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * Properties props = new Properties();
     * props.setProperty("useSSL", "true");
     * props.setProperty("serverTimezone", "UTC");
     * 
     * DBSession session = new DBSession().options(props);
     * }</pre>
     */
    public DBSession options(Properties options) {
        this.options = options;
        return this;
    }
    
    /**
     * 자동 Connection 닫기 설정
     * 
     * <p>select()나 execute() 메서드 실행 후 자동으로 connection을 닫을지 설정합니다.</p>
     * 
     * @param autoClose 
     *        <ul>
     *          <li>true (기본값): 쿼리 실행 후 자동으로 connection 닫기 (일회성 쿼리용)</li>
     *          <li>false: 수동으로 close() 호출 필요 (여러 쿼리 실행 시, 트랜잭션 처리 시)</li>
     *        </ul>
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * // 일회성 쿼리 (기본값)
     * RecordSet records = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .select("SELECT * FROM users");
     * // 자동으로 connection 닫힘
     * 
     * // 여러 쿼리 실행
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)) {  // 수동 관리
     *     
     *     session.execute("INSERT ...");
     *     session.execute("UPDATE ...");
     *     session.execute("DELETE ...");
     * } // try-with-resources로 자동 close
     * }</pre>
     */
    public DBSession autoClose(boolean autoClose) {
        this.autoClose = autoClose;
        return this;
    }
    
    /**
     * 자동 Commit 설정
     * 
     * <p>execute() 메서드 실행 후 자동으로 commit할지 설정합니다. 
     * 트랜잭션 처리 시 반드시 false로 설정해야 합니다.</p>
     * 
     * @param autoCommit 
     *        <ul>
     *          <li>true (기본값): execute() 실행 후 자동으로 commit (단일 쿼리용)</li>
     *          <li>false: 수동으로 commit() 호출 필요 (트랜잭션 처리 시)</li>
     *        </ul>
     * @return this (메서드 체이닝용)
     * 
     * <pre>{@code
     * // 단일 업데이트 (기본값)
     * int result = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .execute("UPDATE users SET status = 'active'");
     * // 자동으로 commit됨
     * 
     * // 트랜잭션 처리
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)
     *         .autoCommit(false)) {  // 수동 commit
     *     
     *     try {
     *         session.execute("UPDATE accounts SET balance = balance - 1000 WHERE id = 1");
     *         session.execute("UPDATE accounts SET balance = balance + 1000 WHERE id = 2");
     *         session.commit();  // 수동 commit
     *     } catch (Exception e) {
     *         session.rollback();  // 실패 시 rollback
     *     }
     * }
     * }</pre>
     */
    public DBSession autoCommit(boolean autoCommit) {
        this.autoCommit = autoCommit;
        return this;
    }
    
    /**
     * JDBC 드라이버 로드 (내부 메서드)
     * 
     * <p>데이터베이스 타입에 맞는 JDBC 드라이버를 동적으로 로드합니다.</p>
     * 
     * @throws RuntimeException JDBC 드라이버를 찾을 수 없는 경우
     */
    private void loadDriver() {
        try {
            Class.forName(dbType.getDriver());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("JDBC 드라이버를 찾을 수 없습니다: " + dbType.getDriver(), e);
        }
    }
    
    /**
     * 데이터베이스 연결 가져오기
     * 
     * <p>데이터베이스 Connection 객체를 반환합니다. 
     * 이미 유효한 connection이 있으면 재사용하고, 없으면 새로 생성합니다.
     * Connection은 기본적으로 autoCommit=false 상태입니다.</p>
     * 
     * @return Connection 객체
     * @throws SQLException SQL 예외 발생 시
     * @throws IllegalStateException 필수 설정(dbType, host)이 누락된 경우
     * 
     * <pre>{@code
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)) {
     *     
     *     Connection conn = session.getConnection();
     *     Statement stmt = conn.createStatement();
     *     // JDBC API 직접 사용 가능
     * }
     * }</pre>
     */
    public Connection getConnection() throws SQLException {
        // 필수 필드 검증
        if (dbType == null) {
            throw new IllegalStateException("데이터베이스 타입이 설정되지 않았습니다. database() 메서드를 호출하세요.");
        }
        if (CommonUtils.isBlank(host)) {
            throw new IllegalStateException("호스트 주소가 설정되지 않았습니다. host() 메서드를 호출하세요.");
        }
        
        // 드라이버 로드
        loadDriver();
        
        // 기존 Connection이 있고 유효한 경우 재사용
        if (connection != null && !connection.isClosed() && connection.isValid(5)) {
            return connection;
        }
        
        // 새로운 Connection 생성
        String url = buildConnectionUrl();
        Properties props = new Properties();
        props.putAll(options);
        
        if (username != null && !username.isEmpty()) {
            props.setProperty("user", username);
        }
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        
        connection = DriverManager.getConnection(url, props);
        connection.setAutoCommit(false);
        return connection;
    }
    
    /**
     * JDBC 연결 URL 생성 (내부 메서드)
     * 
     * <p>데이터베이스 타입과 호스트 정보를 기반으로 JDBC URL을 생성합니다.</p>
     * 
     * @return JDBC 연결 URL (예: "jdbc:mysql://localhost:3306/mydb")
     */
    private String buildConnectionUrl() {
        String urlPattern = dbType.getPattern();
        
        // URL 패턴에 따라 호스트 정보 포맷팅
        if (urlPattern.contains("%s/%s")) {
            // MySQL, PostgreSQL 등의 경우
            String[] parts = host.split("/", 2);
            String hostPort = parts[0];
            String database = parts.length > 1 ? parts[1] : "";
            return String.format(urlPattern, hostPort, database);
        } else {
            // Oracle, SQLite 등의 경우
            return String.format(urlPattern, host);
        }
    }
    
    /**
     * 현재 세션의 Connection 닫기 (AutoCloseable 인터페이스 구현)
     * 
     * <p>try-with-resources 구문에서 자동으로 호출됩니다.
     * Connection이 null이 아니고 닫히지 않은 경우에만 닫습니다.</p>
     * 
     * @throws SQLException Connection 닫기 실패 시
     * 
     * <pre>{@code
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")) {
     *     
     *     // 작업 수행
     *     
     * } // 자동으로 close() 호출됨
     * }</pre>
     */
    @Override
    public void close() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
            connection = null;
        }
    }
    
    /**
     * Connection 닫기 (close()의 별칭)
     * 
     * <p>명시적으로 connection을 닫을 때 사용합니다. close()와 동일한 동작을 수행합니다.</p>
     * 
     * @throws SQLException Connection 닫기 실패 시
     * 
     * <pre>{@code
     * DBSession session = new DBSession().database(...);
     * try {
     *     // 작업 수행
     * } finally {
     *     session.closeConnection();
     * }
     * }</pre>
     */
    public void closeConnection() throws SQLException {
        close();
    }
    
    /**
     * 수동 Commit
     * 
     * <p>autoCommit=false로 설정한 경우, 트랜잭션을 명시적으로 커밋합니다.
     * 여러 쿼리를 하나의 원자적 작업으로 처리할 때 사용합니다.</p>
     * 
     * @throws SQLException Commit 실패 시
     * 
     * <pre>{@code
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)
     *         .autoCommit(false)) {
     *     
     *     try {
     *         session.execute("UPDATE accounts SET balance = balance - 1000 WHERE id = 1");
     *         session.execute("UPDATE accounts SET balance = balance + 1000 WHERE id = 2");
     *         session.commit();  // 두 쿼리 모두 성공 시 커밋
     *     } catch (Exception e) {
     *         session.rollback();
     *     }
     * }
     * }</pre>
     */
    public void commit() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.commit();
        }
    }
    
    /**
     * 수동 Rollback
     * 
     * <p>autoCommit=false로 설정한 경우, 트랜잭션을 명시적으로 롤백합니다.
     * 에러 발생 시 모든 변경사항을 취소할 때 사용합니다.</p>
     * 
     * @throws SQLException Rollback 실패 시
     * 
     * <pre>{@code
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)
     *         .autoCommit(false)) {
     *     
     *     try {
     *         session.execute("INSERT INTO users (name) VALUES ('John')");
     *         session.execute("INSERT INTO invalid_table VALUES (...)");  // 에러 발생
     *         session.commit();
     *     } catch (Exception e) {
     *         session.rollback();  // 모든 변경사항 취소
     *     }
     * }
     * }</pre>
     */
    public void rollback() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.rollback();
        }
    }
    
    /**
	 * ResultSet 리소스 닫기
	 * 
	 * <p>ResultSet을 안전하게 닫습니다. SQLException이 발생해도 무시합니다.</p>
	 * 
	 * @param rSet 닫을 ResultSet 객체 (null 가능)
	 */
    public void close(ResultSet rSet){
		if(rSet != null){
			try{
				rSet.close();
			}catch(SQLException e){}
		}
	}
	
	/**
	 * Statement 리소스 닫기
	 * 
	 * <p>Statement를 안전하게 닫습니다. SQLException이 발생해도 무시합니다.</p>
	 * 
	 * @param stmt 닫을 Statement 객체 (null 가능)
	 */
	public void close(Statement stmt){
		if(stmt != null){
			try{
				stmt.close();
			}catch(SQLException e){}
		}
	}
	
	/**
	 * PreparedStatement 리소스 닫기
	 * 
	 * <p>PreparedStatement를 안전하게 닫습니다. SQLException이 발생해도 무시합니다.</p>
	 * 
	 * @param pstmt 닫을 PreparedStatement 객체 (null 가능)
	 */	
	public void close(PreparedStatement pstmt){
		if(pstmt != null){
			try{
				pstmt.close();
			}catch(SQLException e){}
		}
	}
	
	/**
	 * CallableStatement 리소스 닫기
	 * 
	 * <p>CallableStatement를 안전하게 닫습니다. SQLException이 발생해도 무시합니다.</p>
	 * 
	 * @param cstmt 닫을 CallableStatement 객체 (null 가능)
	 */
	public void close(CallableStatement cstmt){
		if(cstmt != null){
			try{
				cstmt.close();
			}catch(SQLException e){}
		}
	}
    
    
    
    /**
     * INSERT 쿼리 실행 (Create 객체 사용)
     * 
     * <p>Create 객체를 사용하여 INSERT 쿼리를 실행합니다.</p>
     * 
     * @param create Create 쿼리 빌더 객체
     * @return 삽입된 레코드 수
     * 
     * @see Create
     */
    public int insert(Create create) {
    	return execute(create.build());
    }
    
   
    
    
    /**
     * UPDATE 쿼리 실행 (Update 객체 사용)
     * 
     * <p>Update 객체를 사용하여 UPDATE 쿼리를 실행합니다.</p>
     * 
     * @param update Update 쿼리 빌더 객체
     * @return 업데이트된 레코드 수
     * 
     * @see Update
     */
    public int update(Update update) {
    	return execute(update.build());
    }
    
   
    
    
    /**
     * SELECT 쿼리 실행 (Retrieve 객체 사용)
     * 
     * <p>Retrieve 객체를 사용하여 SELECT 쿼리를 실행합니다.</p>
     * 
     * @param retrieve Retrieve 쿼리 빌더 객체
     * @return 조회 결과 RecordSet
     * 
     * @see Retrieve
     * @see RecordSet
     */
    public RecordSet select(Retrieve retrieve) {
    	return select(retrieve.build());
    }
    
    
    
    /**
     * SELECT 쿼리 실행
     * 
     * <p>SQL SELECT 쿼리를 실행하고 결과를 RecordSet으로 반환합니다.
     * autoClose=true(기본값)인 경우 쿼리 실행 후 자동으로 connection이 닫힙니다.</p>
     * 
     * <p><b>성능 모니터링:</b> SystemUtils.deepview()가 true면 쿼리와 실행 시간을 로깅합니다.</p>
     * 
     * @param query 실행할 SELECT SQL 쿼리
     * @return 조회 결과 RecordSet (결과가 없으면 null)
     * 
     * @see RecordSet
     * 
     * <pre>{@code
     * // 예제 1: 단순 조회 (autoClose=true, 기본값)
     * RecordSet records = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .select("SELECT * FROM users WHERE status = 'active'");
     * 
     * if(records != null) {
     *     while(records.next()) {
     *         String name = records.getString("name");
     *         int age = records.getInt("age");
     *         System.out.println(name + ", " + age);
     *     }
     * }
     * // 자동으로 connection 닫힘
     * 
     * // 예제 2: 여러 조회 실행 (autoClose=false)
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)) {
     *     
     *     RecordSet users = session.select("SELECT * FROM users");
     *     RecordSet orders = session.select("SELECT * FROM orders");
     *     RecordSet products = session.select("SELECT * FROM products");
     * } // try-with-resources로 자동 close
     * }</pre>
     */
    public RecordSet select(String query){
		
		Statement stmt 			= null;
		ResultSet rset			= null;
		RecordSet records		= null;
		
		long startTime = System.nanoTime();
		try {
			stmt		= connection.createStatement();
			stmt.executeQuery(query);
			rset		= stmt.getResultSet();
			if(rset != null) {
				records = new RecordSet(rset);
			}
			
		}catch(SQLException t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			close(rset);
			close(stmt);
			
			if(SystemUtils.deepview()) {
				logger.info("query : {} = [{}]",query,records != null ? records.size():0);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			// autoClose가 true면 자동으로 connection 닫기
			if(autoClose) {
				try {
					closeConnection();
				} catch (SQLException e) {
					logger.warn("Failed to close connection: {}", CommonUtils.getExceptionMessage(e));
				}
			}
		}
		return records;
	}
    
    
    
    
    /**
     * DELETE 쿼리 실행 (Delete 객체 사용)
     * 
     * <p>Delete 객체를 사용하여 DELETE 쿼리를 실행합니다.</p>
     * 
     * @param delete Delete 쿼리 빌더 객체
     * @return 삭제된 레코드 수
     * 
     * @see Delete
     */
    public int delete(Delete delete) {
    	return execute(delete.build());
    }
    
    
    
    /**
     * CUD(INSERT/UPDATE/DELETE) 쿼리 실행
     * 
     * <p>SQL INSERT, UPDATE, DELETE 쿼리를 실행하고 영향받은 레코드 수를 반환합니다.
     * autoCommit=true(기본값)인 경우 자동으로 commit되고,
     * autoClose=true(기본값)인 경우 쿼리 실행 후 자동으로 connection이 닫힙니다.</p>
     * 
     * <p><b>트랜잭션 처리:</b> autoCommit=false로 설정한 경우 수동으로 commit() 또는 rollback()을 호출해야 합니다.</p>
     * 
     * <p><b>성능 모니터링:</b> SystemUtils.deepview()가 true면 쿼리와 실행 시간을 로깅합니다.</p>
     * 
     * @param query 실행할 SQL 쿼리 (INSERT/UPDATE/DELETE)
     * @return 영향받은 레코드 수
     * 
     * <pre>{@code
     * // 예제 1: 단순 업데이트 (autoCommit=true, autoClose=true, 기본값)
     * int updated = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .execute("UPDATE users SET status = 'active' WHERE id = 1");
     * System.out.println(updated + "건 업데이트됨 (자동 commit)");
     * 
     * // 예제 2: 트랜잭션 처리 (autoCommit=false, autoClose=false)
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)
     *         .autoCommit(false)) {
     *     
     *     try {
     *         // 여러 쿼리를 하나의 트랜잭션으로 처리
     *         session.execute("UPDATE accounts SET balance = balance - 1000 WHERE id = 1");
     *         session.execute("UPDATE accounts SET balance = balance + 1000 WHERE id = 2");
     *         session.execute("INSERT INTO transactions (from_id, to_id, amount) VALUES (1, 2, 1000)");
     *         
     *         // 모두 성공하면 commit
     *         session.commit();
     *         System.out.println("트랜잭션 성공!");
     *         
     *     } catch (Exception e) {
     *         // 하나라도 실패하면 rollback
     *         session.rollback();
     *         System.out.println("트랜잭션 실패 - 모든 변경사항 취소");
     *         throw e;
     *     }
     * }
     * 
     * // 예제 3: 대량 INSERT (autoCommit=true, autoClose=false)
     * try (DBSession session = new DBSession()
     *         .database(DBType.MARIADB)
     *         .host("localhost:3306/mydb")
     *         .username("root")
     *         .password("password")
     *         .autoClose(false)) {
     *     
     *     for(int i = 0; i < 1000; i++) {
     *         session.execute("INSERT INTO logs (message) VALUES ('Log " + i + "')");
     *         // 각 쿼리마다 자동 commit
     *     }
     * }
     * }</pre>
     */
    public int execute(String query){
		
    	int result = 0;
		long startTime = System.nanoTime();
		
    	PreparedStatement pstmt = null;
    	try {
    		pstmt = connection.prepareStatement(query);
    		result  	= pstmt.executeUpdate();
    		
    		// autoCommit이 true일 때만 자동으로 commit
    		if(autoCommit) {
    			connection.commit();
    		}
    	}catch(SQLException t){
			logger.warn("sql error : {}",CommonUtils.getExceptionMessage(t));
		}finally {
			close(pstmt);
			
			if(SystemUtils.deepview()) {
				logger.info("query : {} = [{}]",result,query);
				logger.info(SystemUtils.getElapsedTime(System.nanoTime()- startTime));
			}
			
			// autoClose가 true면 자동으로 connection 닫기
			if(autoClose) {
				try {
					closeConnection();
				} catch (SQLException e) {
					logger.warn("Failed to close connection: {}", CommonUtils.getExceptionMessage(e));
				}
			}
		}
		return result;
	}
    
    
 // ========== 메타데이터 조회 기능 ==========
	
 	/**
 	 * 데이터베이스 목록 조회
 	 * 
 	 * <p>시스템 데이터베이스를 제외한 사용자 데이터베이스 목록을 조회합니다.</p>
 	 * 
 	 * @return 데이터베이스 정보 맵
 	 * 
 	 * <pre>{@code
 	 * try (DBSession session = new DBSession()
 	 *         .database(DBType.MARIADB)
 	 *         .host("localhost:3306")
 	 *         .username("root")
 	 *         .password("password")
 	 *         .autoClose(false)) {
 	 *     
 	 *     LinkedMap<String, Object> dbInfo = session.getDatabases();
 	 *     System.out.println("Product: " + dbInfo.getString("product"));
 	 *     System.out.println("JDBC Driver: " + dbInfo.getString("jdbcdriver"));
 	 *     
 	 *     List<String> catalogs = (List<String>) dbInfo.get("catalogs");
 	 *     for (String catalog : catalogs) {
 	 *         System.out.println("Database: " + catalog);
 	 *     }
 	 * }
 	 * }</pre>
 	 */
 	public LinkedMap<String, Object> getDatabases() {
 		LinkedMap<String, Object> map = new LinkedMap<String, Object>();
 		ResultSet rset = null;
 		
 		try {
 			Connection conn = getConnection();
 			DatabaseMetaData md = conn.getMetaData();
 			
 			map.put("product", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
 			map.put("jdbcdriver", md.getDriverName() + " " + md.getDriverVersion());
 			
 			rset = md.getCatalogs();
 			List<String> catalogs = new ArrayList<String>();
 			
 			while (rset.next()) {
 				String catalog = CommonUtils.nToB(rset.getString(1));
 				if (!CommonUtils.isEmpty(catalog) && !DEFAULT_SCHEME.contains(catalog)) {
 					catalogs.add(rset.getString(1));
 				}
 			}
 			
 			Collections.sort(catalogs);
 			map.put("catalogs", catalogs);
 			
 		} catch (Exception e) {
 			logger.error("Failed to get databases: {}", CommonUtils.getExceptionMessage(e));
 		} finally {
 			close(rset);
 		}
 		
 		return map;
 	}
 	
 	/**
 	 * 전체 데이터베이스 정보 조회
 	 * 
 	 * <p>모든 데이터베이스의 상세 정보(테이블, 뷰, 시퀀스, 함수, 프로시저)를 조회합니다.</p>
 	 * 
 	 * @return 전체 데이터베이스 정보 맵
 	 * 
 	 * <pre>{@code
 	 * LinkedMap<String, Object> info = session.getInfo();
 	 * 
 	 * List<String> catalogs = (List<String>) info.get("catalogs");
 	 * for (String catalog : catalogs) {
 	 *     Catalog catalogInfo = (Catalog) info.get("DB." + catalog);
 	 *     System.out.println("Database: " + catalogInfo.name);
 	 *     System.out.println("Tables: " + catalogInfo.tables.size());
 	 *     System.out.println("Views: " + catalogInfo.views.size());
 	 * }
 	 * }</pre>
 	 */
 	public LinkedMap<String, Object> getInfo() {
 		LinkedMap<String, Object> map = new LinkedMap<String, Object>();
 		ResultSet rset = null;
 		
 		try {
 			Connection conn = getConnection();
 			DatabaseMetaData md = conn.getMetaData();
 			
 			map.put("product", md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
 			map.put("jdbcdriver", md.getDriverName() + " " + md.getDriverVersion());
 			
 			rset = md.getCatalogs();
 			List<String> catalogs = new ArrayList<String>();
 			
 			while (rset.next()) {
 				String catalog = CommonUtils.nToB(rset.getString(1));
 				if (!CommonUtils.isEmpty(catalog) && !DEFAULT_SCHEME.contains(catalog)) {
 					catalogs.add(rset.getString(1));
 				}
 			}
 			
 			Collections.sort(catalogs);
 			map.put("catalogs", catalogs);
 			rset.close();
 			
 			// 각 카탈로그 상세 정보 조회
 			for (String catalog : catalogs) {
 				map.put("DB." + catalog, getCatalog(md, catalog));
 			}
 			
 		} catch (Exception e) {
 			logger.error("Failed to get database info: {}", CommonUtils.getExceptionMessage(e));
 		} finally {
 			close(rset);
 		}
 		
 		return map;
 	}
 	
 	/**
 	 * 특정 데이터베이스 정보 조회
 	 * 
 	 * <p>지정한 데이터베이스의 상세 정보를 조회합니다.</p>
 	 * 
 	 * @param catalogName 데이터베이스(카탈로그) 이름
 	 * @return 데이터베이스 상세 정보
 	 * 
 	 * <pre>{@code
 	 * Catalog catalog = session.getDatabase("mydb");
 	 * 
 	 * System.out.println("Database: " + catalog.name);
 	 * System.out.println("Tables: " + catalog.tables.size());
 	 * 
 	 * for (Table table : catalog.tables) {
 	 *     System.out.println("  Table: " + table.n);
 	 *     System.out.println("  Remarks: " + table.r);
 	 *     
 	 *     for (Column column : table.c) {
 	 *         System.out.println("    Column: " + column.n + " - " + column.t);
 	 *     }
 	 * }
 	 * }</pre>
 	 */
 	public Catalog getDatabase(String catalogName) {
 		Catalog catalog = null;
 		ResultSet rset = null;
 		
 		try {
 			Connection conn = getConnection();
 			DatabaseMetaData md = conn.getMetaData();
 			catalog = getCatalog(md, catalogName);
 		} catch (Exception e) {
 			logger.error("Failed to get database '{}': {}", catalogName, CommonUtils.getExceptionMessage(e));
 		} finally {
 			close(rset);
 		}
 		
 		return catalog;
 	}
 	
 	/**
 	 * 카탈로그(데이터베이스) 메타데이터 조회 (내부용)
 	 * 
 	 * @param md DatabaseMetaData 객체
 	 * @param catalogName 카탈로그 이름
 	 * @return 카탈로그 정보
 	 */
 	private Catalog getCatalog(DatabaseMetaData md, String catalogName) {
 		Catalog catalog = new Catalog();
 		catalog.name = catalogName;
 		ResultSet rset = null;
 		
 		try {
 			rset = md.getTables(catalogName, null, "%", null);
 			
 			catalog.tables = new ArrayList<Table>();
 			catalog.views = new ArrayList<Table>();
 			catalog.seqs = new ArrayList<Table>();
 			
 			while (rset.next()) {
 				Table table = new Table();
 				table.n = rset.getString("TABLE_NAME");
 				String type = rset.getString("TABLE_TYPE");
 				table.r = rset.getString("REMARKS");
 				
 				if ("TABLE".equals(type)) {
 					catalog.tables.add(table);
 				} else if ("VIEW".equals(type)) {
 					catalog.views.add(table);
 				} else if ("SEQUENCE".equals(type)) {
 					catalog.seqs.add(table);
 				}
 				
 				table.c = getColumns(md, catalogName, table.n);
 			}
 			
 			rset.close();
 			
 			// Functions 조회
 			rset = md.getFunctions(catalogName, null, "%");
 			while (rset.next()) {
 				if (catalog.functions == null) {
 					catalog.functions = new ArrayList<Table>();
 				}
 				Table table = new Table();
 				table.n = rset.getString("FUNCTION_NAME");
 				table.r = rset.getString("REMARKS");
 				catalog.functions.add(table);
 			}
 			
 			rset.close();
 			
 			// Procedures 조회
 			rset = md.getProcedures(catalogName, null, "%");
 			while (rset.next()) {
 				if (catalog.procedures == null) {
 					catalog.procedures = new ArrayList<Table>();
 				}
 				Table table = new Table();
 				table.n = rset.getString("PROCEDURE_NAME");
 				table.r = rset.getString("REMARKS");
 				catalog.procedures.add(table);
 			}
 			
 			// 정렬
 			sortTables(catalog);
 			
 		} catch (Exception e) {
 			logger.error("Failed to get catalog '{}': {}", catalogName, CommonUtils.getExceptionMessage(e));
 		} finally {
 			try {
 				if (rset != null) rset.close();
 			} catch (Exception e) {
 			}
 		}
 		
 		return catalog;
 	}
 	
 	/**
 	 * 테이블 목록 정렬
 	 */
 	private void sortTables(Catalog catalog) {
 		Comparator<Table> comparator = new Comparator<Table>() {
 			@Override
 			public int compare(Table o1, Table o2) {
 				return o1.n.compareTo(o2.n);
 			}
 		};
 		
 		Collections.sort(catalog.tables, comparator);
 		Collections.sort(catalog.views, comparator);
 		Collections.sort(catalog.seqs, comparator);
 		
 		if (catalog.functions != null) {
 			Collections.sort(catalog.functions, comparator);
 		}
 		if (catalog.procedures != null) {
 			Collections.sort(catalog.procedures, comparator);
 		}
 	}
 	
 	/**
 	 * 테이블 컬럼 정보 조회
 	 * 
 	 * <p>지정한 테이블의 컬럼 정보를 조회합니다.</p>
 	 * 
 	 * @param catalogName 데이터베이스(카탈로그) 이름
 	 * @param tableName 테이블 이름
 	 * @return 컬럼 정보 리스트
 	 * 
 	 * <pre>{@code
 	 * List<Column> columns = session.getColumns("mydb", "users");
 	 * 
 	 * for (Column column : columns) {
 	 *     System.out.println("Position: " + column.p);
 	 *     System.out.println("Name: " + column.n);
 	 *     System.out.println("Type: " + column.t);
 	 *     System.out.println("Remarks: " + column.r);
 	 * }
 	 * }</pre>
 	 */
 	public List<Column> getColumns(String catalogName, String tableName) {
 		List<Column> columns = new ArrayList<Column>();
 		
 		try {
 			Connection conn = getConnection();
 			DatabaseMetaData md = conn.getMetaData();
 			columns = getColumns(md, catalogName, tableName);
 		} catch (Exception e) {
 			logger.error("Failed to get columns for '{}.{}': {}", 
 				catalogName, tableName, CommonUtils.getExceptionMessage(e));
 		}
 		
 		return columns;
 	}
 	
 	/**
 	 * 테이블 컬럼 정보 조회 (내부용)
 	 * 
 	 * @param md DatabaseMetaData 객체
 	 * @param catalogName 카탈로그 이름
 	 * @param tableName 테이블 이름
 	 * @return 컬럼 정보 리스트
 	 */
 	private List<Column> getColumns(DatabaseMetaData md, String catalogName, String tableName) {
 		List<Column> list = new ArrayList<Column>();
 		ResultSet rset = null;
 		
 		try {
 			// Primary Key 조회
 			List<String> pks = new ArrayList<String>();
 			rset = md.getPrimaryKeys(catalogName, null, tableName);
 			while (rset.next()) {
 				pks.add(rset.getString("COLUMN_NAME"));
 			}
 			rset.close();
 			
 			// 컬럼 정보 조회
 			rset = md.getColumns(catalogName, null, tableName, "%");
 			
 			while (rset.next()) {
 				Column column = new Column();
 				column.p = rset.getInt("ORDINAL_POSITION");
 				column.n = rset.getString("COLUMN_NAME");
 				String typeName = rset.getString("TYPE_NAME").toLowerCase();
 				int size = rset.getInt("COLUMN_SIZE");
 				int decimal = rset.getInt("DECIMAL_DIGITS");
 				boolean nullable = rset.getInt("NULLABLE") == 1;
 				int dt = rset.getInt("DATA_TYPE");
 				boolean ai = "YES".equals(rset.getString("IS_AUTOINCREMENT"));
 				column.r = CommonUtils.nToB(rset.getString("REMARKS"));
 				
 				// 타입 정보 포맷팅
 				StringBuilder sb = new StringBuilder()
 					.append(String.format("-%20s", column.n));
 				
 				if (typeName.indexOf("char") > -1) {
 					sb.append(typeName).append("(").append(size).append(") ");
 				} else if (typeName.indexOf("decimal") > -1) {
 					sb.append(typeName).append("(").append(size).append(",").append(decimal).append(") ");
 				} else if (typeName.indexOf("int") > -1) {
 					if (typeName.indexOf("bigint") > -1) {
 						dt = 18;
 					}
 					typeName = typeName.toLowerCase().replaceAll("unsigned", "UN");
 					String[] ar = typeName.split(" ");
 					if (ar.length == 1) {
 						sb.append(ar[0]).append("(").append(dt).append(") ");
 					} else if (ar.length == 2) {
 						sb.append(ar[0]).append("(").append(dt).append(") ").append(ar[1]).append(" ");
 					} else {
 						sb.append(typeName);
 					}
 				} else {
 					sb.append(typeName).append(" ");
 				}
 				
 				if (nullable) {
 					sb.append("N ");
 				}
 				if (ai) {
 					sb.append("AI ");
 				}
 				if (pks.contains(column.n)) {
 					sb.append("PK ");
 				}
 				
 				column.t = sb.toString();
 				list.add(column);
 			}
 			
 			// 위치 순으로 정렬
 			Collections.sort(list, new Comparator<Column>() {
 				@Override
 				public int compare(Column o1, Column o2) {
 					return o1.p - o2.p;
 				}
 			});
 			
 		} catch (Exception e) {
 			logger.error("Failed to get columns: {}", CommonUtils.getExceptionMessage(e));
 		} finally {
 			try {
 				if (rset != null) rset.close();
 			} catch (Exception e) {
 			}
 		}
 		
 		return list;
 	}
 	
 	
 	
 	/**
	 * SQL 쿼리 실행 (결과 포함)
	 * 
	 * <p>SELECT, UPDATE, DELETE, CREATE, DROP 등 모든 SQL 쿼리를 실행하고
	 * 실행 결과를 SqlResult 객체로 반환합니다.</p>
	 * 
	 * @param sql 실행할 SQL 쿼리
	 * @return SQL 실행 결과
	 * 
	 * <pre>{@code
	 * SqlResult result = session.executeSql("SELECT * FROM users LIMIT 10");
	 * 
	 * if (result.ret > 0) {
	 *     System.out.println("Message: " + result.msg);
	 *     System.out.println("Duration: " + result.duration);
	 *     
	 *     // SELECT 결과
	 *     if (result.columns != null) {
	 *         System.out.println("Columns: " + result.columns);
	 *         for (List<Object> row : result.datas) {
	 *             System.out.println(row);
	 *         }
	 *     }
	 * }
	 * }</pre>
	 */
	public SqlResult executeSql(String sql) {
		return executeSql(sql, null);
	}
	
	/**
	 * SQL 쿼리 실행 (데이터베이스 지정)
	 * 
	 * @param sql 실행할 SQL 쿼리
	 * @param database 사용할 데이터베이스 이름 (null 가능)
	 * @return SQL 실행 결과
	 */
	public SqlResult executeSql(String sql, String database) {
		String tmp = CommonUtils.ltrim(sql);
		
		// SELECT 쿼리에 LIMIT 자동 추가
		if (tmp.startsWith("SELECT") && tmp.indexOf(" LIMIT ") == -1) {
			sql = sql + " LIMIT 0,500";
		}
		
		SqlResult result = new SqlResult();
		Statement stmt = null;
		ResultSet rset = null;
		
		try {
			Connection conn = getConnection();
			stmt = conn.createStatement();
			stmt.setQueryTimeout(120);
			
			double start = System.currentTimeMillis();
			
			// 데이터베이스 선택
			if (!CommonUtils.isEmpty(database)) {
				stmt.execute("USE " + database);
			}
			
			boolean hasResult = stmt.execute(sql);
			double duration = System.currentTimeMillis() - start;
			
			// 경고 메시지 수집
			StringBuilder sbw = new StringBuilder();
			SQLWarning warning = stmt.getWarnings();
			while (warning != null) {
				sbw.append(" ,Warning Code:").append(warning.getErrorCode())
					.append(", State:").append(warning.getSQLState())
					.append(", Message:").append(warning.getMessage())
					.append("\n");
				warning = warning.getNextWarning();
			}
			
			if (hasResult) {
				// SELECT 결과 처리
				rset = stmt.getResultSet();
				ResultSetMetaData meta = rset.getMetaData();
				int cnt = meta.getColumnCount() + 1;
				
				result.columns = new ArrayList<String>(cnt);
				for (int i = 1; i < cnt; i++) {
					result.columns.add(meta.getColumnName(i));
				}
				
				result.datas = new ArrayList<List<Object>>();
				while (rset.next()) {
					List<Object> data = new ArrayList<Object>(cnt);
					for (int i = 1; i < cnt; i++) {
						Object obj = rset.getObject(i);
						data.add(obj == null ? "null" : obj);
					}
					result.datas.add(data);
				}
				
				result.msg = String.format("OK, %d row(s) returned", result.datas.size());
				result.ret = result.datas.size();
				
			} else {
				// UPDATE, DELETE, CREATE, DROP 처리
				result.ret = stmt.getUpdateCount();
				if (result.ret > 0 && autoCommit) {
					conn.commit();
				}
				result.msg = String.format("OK, %d row(s) affected", result.ret);
			}
			
			result.duration = String.format("%.3f sec", (duration / 1000));
			if (sbw.length() > 0) {
				result.msg = result.msg + sbw.toString();
			}
			
		} catch (Exception e) {
			if (e instanceof SQLException) {
				SQLException ex = (SQLException) e;
				result.msg = "Error Code:" + ex.getErrorCode() + ". " + e.getMessage();
			} else {
				result.msg = e.getMessage();
			}
		} finally {
			close(rset);
			close(stmt);
		}
		
		return result;
	}
    
    

    
}