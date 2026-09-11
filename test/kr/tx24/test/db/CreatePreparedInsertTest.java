package kr.tx24.test.db;

import com.zaxxer.hikari.HikariDataSource;
import kr.tx24.lib.db.Create;
import kr.tx24.lib.db.DBFactory;
import kr.tx24.lib.db.DBManager;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 실제 DB 접속 없이 가변 인자 INSERT의 바인딩·트랜잭션·자원 반환 계약을 확인한다. */
public class CreatePreparedInsertTest {

    private static final String QUERY = "INSERT INTO TARGET (day, regId, note, sourceDay) SELECT ?, ?, ?, ?";

    public static void main(String[] args) {

        // 단독 실행 테스트는 라이브러리의 주기 작업까지 기다리지 않고 결과 코드로 종료한다.
        try {
            runTests();
            System.exit(0);
        } catch (Throwable ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void runTests() throws Exception {

        Field   sourceField  = field(DBManager.class, "ds");
        Field   readyField   = field(DBManager.class, "initialized");
        Field   factoryField = field(DBFactory.class, "dbmanager");
        Field   factoryReady = field(DBFactory.class, "initialized");
        Object  oldSource    = sourceField.get(null);
        Object  oldReady     = readyField.get(null);
        Object  oldFactory   = factoryField.get(null);
        Object  oldStatus    = factoryReady.get(null);
        boolean oldFilter    = DBManager.injectionFilter;

        try (StubDataSource source = new StubDataSource()) {
            sourceField.set(null, source);
            readyField.set(null, true);
            DBManager.injectionFilter = false;

            bindsValuesAndCommits(source);
            acceptsEmptyParameters(source);
            returnsResultOnFailure(source);
            System.out.println("CreatePreparedInsertTest: 5 checks passed");
        } finally {
            sourceField.set(null, oldSource);
            readyField.set(null, oldReady);
            factoryField.set(null, oldFactory);
            factoryReady.set(null, oldStatus);
            DBManager.injectionFilter = oldFilter;
        }
    }

    private static void bindsValuesAndCommits(StubDataSource source) throws Exception {

        JdbcCall call   = source.call = new JdbcCall();
        String   userId = "담당자'\\ OR 1=1 --";
        Create   create = new Create()
                .table("UNUSED")
                .record("memo", "유지");
        int      count  = create.insert(QUERY, 20260912, userId, null, 20260912);

        require(count == 3, "성공 시 영향받은 행 수를 반환해야 합니다.");
        require(QUERY.equals(call.query) && call.prepares == 1, "SQL을 변형하거나 추가 쿼리를 실행하면 안 됩니다.");
        require(Arrays.asList(20260912, userId, null, 20260912).equals(call.parameters),
                "문자열·숫자·NULL 값을 순서대로 바인딩해야 합니다.");
        require("유지".equals(create.getRecord().getString("memo")), "직접 쿼리 실행은 빌더를 초기화하면 안 됩니다.");
        require(call.commits == 1 && call.rollbacks == 0, "성공 시 한 번 commit해야 합니다.");
        require(call.statementCloses == 1 && call.connectionCloses == 1, "빌린 자원을 반환해야 합니다.");
    }

    private static void acceptsEmptyParameters(StubDataSource source) throws Exception {

        for (Object[] parameters : new Object[][]{new Object[0], null}) {
            JdbcCall call = source.call = new JdbcCall();
            call.count = 0;
            int count = new Create()
                    .insert("INSERT INTO TARGET (day) SELECT day FROM SOURCE WHERE 1=0", parameters);

            require(count == 0 && call.parameters.isEmpty(), "빈 배열·null 배열은 바인딩 없이 실행해야 합니다.");
            require(call.commits == 1 && call.rollbacks == 0, "0건도 정상 완료로 처리해야 합니다.");
            require(call.statementCloses == 1 && call.connectionCloses == 1, "0건에서도 자원을 반환해야 합니다.");
        }
    }

    private static void returnsResultOnFailure(StubDataSource source) {

        for (boolean commitFailure : new boolean[]{false, true}) {
            JdbcCall     call     = source.call = new JdbcCall();
            SQLException original = new SQLException("테스트 오류", "42000", 1064);
            if (commitFailure) {
                call.commitError = original;
            } else {
                call.executeError = original;
            }

            int count = new Create()
                    .insert(QUERY, 20260912, "test", null, 20260912);

            require(count == (commitFailure ? call.count : 0),
                    "insert(String)과 동일하게 실행 실패는 0, commit 실패는 이미 얻은 행 수를 반환해야 합니다.");
            require(call.commits == (commitFailure ? 1 : 0) && call.rollbacks == 0,
                    "insert(String)과 동일하게 실행 성공 후 commit하고 별도 rollback은 호출하지 않아야 합니다.");
            require(call.statementCloses == 1 && call.connectionCloses == 1, "실패한 경우에도 자원을 반환해야 합니다.");
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {

        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void require(boolean condition, String message) {

        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static class StubDataSource extends HikariDataSource {

        private JdbcCall call;

        @Override
        public Connection getConnection() {

            return call.connection();
        }
    }

    private static class JdbcCall {

        private final List<Object> parameters = new ArrayList<>();
        private String             query;
        private int                count      = 3;
        private int                prepares;
        private int                commits;
        private int                rollbacks;
        private int                statementCloses;
        private int                connectionCloses;
        private SQLException       executeError;
        private SQLException       commitError;

        private Connection connection() {

            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "prepareStatement" -> {
                                query = (String) args[0];
                                prepares++;
                                return statement();
                            }
                            case "commit" -> {
                                commits++;
                                if (commitError != null) {
                                    throw commitError;
                                }
                            }
                            case "rollback" -> rollbacks++;
                            case "close" -> connectionCloses++;
                            default -> throw new AssertionError("예상하지 못한 연결 호출: " + method.getName());
                        }
                        return null;
                    });
        }

        private PreparedStatement statement() {

            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "setString", "setInt", "setObject" -> {
                                require((int) args[0] == parameters.size() + 1, "바인딩 순서가 잘못되었습니다.");
                                parameters.add(args[1]);
                            }
                            case "executeUpdate" -> {
                                if (executeError != null) {
                                    throw executeError;
                                }
                                return count;
                            }
                            case "close" -> statementCloses++;
                            default -> throw new AssertionError("예상하지 못한 문장 호출: " + method.getName());
                        }
                        return null;
                    });
        }
    }
}
