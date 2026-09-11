package kr.tx24.test.db;

import com.zaxxer.hikari.HikariDataSource;
import kr.tx24.lib.db.Create;
import kr.tx24.lib.db.DBException;
import kr.tx24.lib.db.DBFactory;
import kr.tx24.lib.db.DBManager;
import kr.tx24.lib.db.DBTrxUpdate;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 실제 DB 접속 없이 복합 쿼리의 결과·오류 전파·자원 반환을 확인한다. */
public class DBTrxUpdateTest {

    private static final String QUERY = "UPDATE TARGET SET status='done' WHERE id=1";
    private static final String MEMO  = "담당자'\\ 메모";

    public static void main(String[] args) {

        // 단독 실행 테스트는 라이브러리의 주기 작업을 기다리지 않고 결과 코드로 종료한다.
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
            factoryField.set(null, null);
            factoryReady.set(null, false);
            DBManager.injectionFilter = false;

            executesMixedQueriesAndReusesBuilder(source);
            beginFailureClosesConnection(source);
            queryFailurePreservesCleanupErrors(source);
            commitFailurePreservesCleanupErrors(source);
            closeFailureAfterCommitDoesNotRollback(source);
            executeReturnsSuccessFlag(source);
            System.out.println("DBTrxUpdateTest: 7 checks passed");
        } finally {
            sourceField.set(null, oldSource);
            readyField.set(null, oldReady);
            factoryField.set(null, oldFactory);
            factoryReady.set(null, oldStatus);
            DBManager.injectionFilter = oldFilter;
        }
    }

    private static void executesMixedQueriesAndReusesBuilder(StubDataSource source) throws Exception {

        DBTrxUpdate transaction = batch();
        for (int i = 0; i < 2; i++) {
            JdbcCall call = source.call = new JdbcCall();
            int[]    rows = transaction.executeWithResult();

            require(Arrays.equals(rows, new int[]{1, 2}), "쿼리 순서대로 영향받은 행 수를 반환해야 합니다.");
            require(call.statements.size() == 2 && QUERY.equals(call.statements.get(0).query),
                    "직접 쿼리와 바인딩 쿼리를 등록 순서대로 실행해야 합니다.");
            StatementCall prepared = call.statements.get(1);
            require(prepared.prepared && prepared.query.contains("?")
                            && Arrays.asList(20260912, MEMO, null).equals(prepared.parameters),
                    "숫자·문자열·NULL을 순서대로 바인딩해야 합니다.");
            require(call.commits == 1 && call.rollbacks == 0, "각 실행은 새 연결에서 한 번 commit해야 합니다.");
            requireClosed(call);
        }
    }

    private static void beginFailureClosesConnection(StubDataSource source) throws Exception {

        JdbcCall call = source.call = new JdbcCall();
        call.beginError = new SQLException("autoCommit 설정 실패", "08000", 10);
        call.closeError = new SQLException("연결 종료 실패");

        try {
            batch().executeWithResult();
            throw new AssertionError("시작 실패를 호출부에 전달해야 합니다.");
        } catch (DBException ex) {
            require(ex.getCause() == call.beginError && ex.getCode() == 10, "시작 오류와 코드를 보존해야 합니다.");
            requireSuppressed(call.beginError);
            requireSuppressed(ex, call.closeError);
        }

        require(call.statements.isEmpty() && call.commits == 0 && call.rollbacks == 0,
                "시작 실패 후 쿼리나 트랜잭션 종료를 다시 실행하면 안 됩니다.");
        requireClosed(call);
    }

    private static void queryFailurePreservesCleanupErrors(StubDataSource source) throws Exception {

        for (boolean prepared : new boolean[]{false, true}) {
            JdbcCall  call     = source.call = new JdbcCall();
            Throwable original = prepared ? new IllegalStateException("쿼리 실행 실패")
                    : new SQLException("쿼리 실행 실패", "42000", 20);
            call.failureIndex        = prepared ? 1 : 0;
            call.executeError        = original;
            call.statementCloseError = new SQLException("문장 종료 실패");
            call.rollbackError       = new SQLException("rollback 실패");
            call.closeError          = new SQLException("연결 종료 실패");

            try {
                batch().executeWithResult();
                throw new AssertionError("쿼리 실패 후 결과를 반환하면 안 됩니다.");
            } catch (DBException ex) {
                require(!prepared && ex.getCause() == original && ex.getCode() == 20 && QUERY.equals(ex.getQuery()),
                        "SQL 오류의 원인·코드·실패 쿼리를 보존해야 합니다.");
            } catch (IllegalStateException ex) {
                require(prepared && ex == original, "런타임 오류는 원래 예외를 전달해야 합니다.");
            }

            requireSuppressed(original, call.statementCloseError, call.rollbackError, call.closeError);
            require(call.statements.size() == call.failureIndex + 1 && call.commits == 0 && call.rollbacks == 1,
                    "쿼리 실패 후 후속 실행을 멈추고 한 번 rollback해야 합니다.");
            requireClosed(call);
        }
    }

    private static void commitFailurePreservesCleanupErrors(StubDataSource source) throws Exception {

        JdbcCall call = source.call = new JdbcCall();
        call.commitError   = new SQLException("commit 실패", "08007", 30);
        call.rollbackError = new SQLException("rollback 실패");
        call.closeError    = new SQLException("연결 종료 실패");

        try {
            batch().executeWithResult();
            throw new AssertionError("commit 실패를 호출부에 전달해야 합니다.");
        } catch (DBException ex) {
            require(ex.getCause() == call.commitError && ex.getCode() == 30,
                    "DBTrx가 전달한 예외를 다시 감싸지 않아야 합니다.");
            requireSuppressed(call.commitError, call.rollbackError);
            requireSuppressed(ex, call.closeError);
        }

        require(call.commits == 1 && call.rollbacks == 1, "commit 실패 정리를 중복 실행하면 안 됩니다.");
        requireClosed(call);
    }

    private static void closeFailureAfterCommitDoesNotRollback(StubDataSource source) throws Exception {

        JdbcCall call = source.call = new JdbcCall();
        call.closeError = new SQLException("commit 후 연결 종료 실패");

        try {
            batch().executeWithResult();
            throw new AssertionError("연결 종료 실패도 호출부에 전달해야 합니다.");
        } catch (DBException ex) {
            require(ex.getCause() == call.closeError, "연결 종료 오류를 보존해야 합니다.");
        }

        require(call.commits == 1 && call.rollbacks == 0, "완료한 commit을 다시 rollback하면 안 됩니다.");
        requireClosed(call);
    }

    private static void executeReturnsSuccessFlag(StubDataSource source) {

        DBTrxUpdate transaction = batch();
        JdbcCall    failed      = source.call = new JdbcCall();
        failed.commitError = new SQLException("commit 실패");
        require(!transaction.execute(), "기존 execute()는 실패 시 false를 반환해야 합니다.");
        require(failed.rollbacks == 1, "실패를 반환하기 전에 rollback해야 합니다.");
        requireClosed(failed);

        JdbcCall succeeded = source.call = new JdbcCall();
        require(transaction.execute(), "다음 실행은 새 연결로 성공 여부를 반환해야 합니다.");
        require(succeeded.commits == 1, "성공을 반환하기 전에 commit해야 합니다.");
        requireClosed(succeeded);
    }

    private static DBTrxUpdate batch() {

        Create create = new Create("TARGET")
                .record("day", 20260912)
                .record("memo", MEMO)
                .record("note", null);
        return new DBTrxUpdate()
                .add(QUERY)
                .add(create, create.getRecord());
    }

    private static void requireClosed(JdbcCall call) {

        require(call.closes == 1, "실행을 마친 연결은 한 번만 닫아야 합니다.");
        for (StatementCall statement : call.statements) {
            require(statement.closes == 1, "사용한 문장은 한 번만 닫아야 합니다.");
        }
    }

    private static void requireSuppressed(Throwable cause, Throwable... expected) {

        require(Arrays.equals(cause.getSuppressed(), expected), "정리 오류를 원인의 suppressed에 순서대로 보존해야 합니다.");
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

            return call.connection;
        }
    }

    private static class StatementCall {

        private final List<Object> parameters = new ArrayList<>();
        private String             query;
        private boolean            prepared;
        private int                closes;
    }

    private static class JdbcCall {

        private final List<StatementCall> statements = new ArrayList<>();
        private int          failureIndex;
        private int          commits;
        private int          rollbacks;
        private int          closes;
        private SQLException beginError;
        private Throwable    executeError;
        private SQLException statementCloseError;
        private SQLException commitError;
        private SQLException rollbackError;
        private SQLException closeError;

        private final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    require(closes == 0, "종료한 연결을 재사용하면 안 됩니다.");
                    switch (method.getName()) {
                        case "setAutoCommit" -> {
                            require(Boolean.FALSE.equals(args[0]), "수동 트랜잭션으로 설정해야 합니다.");
                            if (beginError != null) {
                                throw beginError;
                            }
                        }
                        case "prepareStatement" -> {
                            return statement(true, (String) args[0]);
                        }
                        case "createStatement" -> {
                            return statement(false, null);
                        }
                        case "commit" -> {
                            commits++;
                            if (commitError != null) {
                                throw commitError;
                            }
                        }
                        case "rollback" -> {
                            rollbacks++;
                            if (rollbackError != null) {
                                throw rollbackError;
                            }
                        }
                        case "close" -> {
                            closes++;
                            if (closeError != null) {
                                throw closeError;
                            }
                        }
                        default -> throw new AssertionError("예상하지 못한 연결 호출: " + method.getName());
                    }
                    return null;
                });

        private Statement statement(boolean prepared, String query) {

            StatementCall call  = new StatementCall();
            int           index = statements.size();
            call.prepared = prepared;
            call.query    = query;
            statements.add(call);

            Class<?> type = prepared ? PreparedStatement.class : Statement.class;
            return (Statement) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "setString", "setInt", "setObject" -> {
                        require((int) args[0] == call.parameters.size() + 1, "바인딩 순서를 지켜야 합니다.");
                        call.parameters.add(args[1]);
                    }
                    case "executeUpdate" -> {
                        if (!prepared) {
                            call.query = (String) args[0];
                        }
                        if (index == failureIndex && executeError != null) {
                            throw executeError;
                        }
                        return index + 1;
                    }
                    case "close" -> {
                        call.closes++;
                        if (index == failureIndex && statementCloseError != null) {
                            throw statementCloseError;
                        }
                    }
                    default -> throw new AssertionError("예상하지 못한 문장 호출: " + method.getName());
                }
                return null;
            });
        }
    }
}
