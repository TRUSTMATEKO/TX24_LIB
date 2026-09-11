package kr.tx24.test.db;

import com.zaxxer.hikari.HikariDataSource;
import kr.tx24.lib.db.DBException;
import kr.tx24.lib.db.DBFactory;
import kr.tx24.lib.db.DBManager;
import kr.tx24.lib.db.DBTrx;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

/** 실제 DB 접속 없이 트랜잭션의 오류 전파·suppressed·연결 반환을 확인한다. */
public class DBTrxLifecycleTest {

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

        Field  sourceField  = field(DBManager.class, "ds");
        Field  readyField   = field(DBManager.class, "initialized");
        Field  factoryField = field(DBFactory.class, "dbmanager");
        Field  factoryReady = field(DBFactory.class, "initialized");
        Object oldSource    = sourceField.get(null);
        Object oldReady     = readyField.get(null);
        Object oldFactory   = factoryField.get(null);
        Object oldStatus    = factoryReady.get(null);

        try (StubDataSource source = new StubDataSource()) {
            sourceField.set(null, source);
            readyField.set(null, true);
            factoryField.set(null, null);
            factoryReady.set(null, false);

            beginFailureClosesConnection(source);
            commitFailurePreservesCleanupErrors(source);
            rollbackPreservesOriginalError(source);
            repeatedCleanupAllowsReuse(source);
            closeFailureAfterCommitDoesNotRollback(source);
            legacyRollbackPreservesCleanupErrors(source);
            System.out.println("DBTrxLifecycleTest: 10 checks passed");
        } finally {
            sourceField.set(null, oldSource);
            readyField.set(null, oldReady);
            factoryField.set(null, oldFactory);
            factoryReady.set(null, oldStatus);
        }
    }

    private static void beginFailureClosesConnection(StubDataSource source) throws Exception {

        for (boolean fromPool : new boolean[]{true, false}) {
            JdbcCall call = source.call = new JdbcCall();
            DBTrx    trx  = new DBTrx();
            call.beginError = new SQLException("autoCommit 설정 실패", "08000", 10);
            call.closeError = new SQLException("연결 종료 실패");

            try {
                if (fromPool) {
                    trx.begin();
                } else {
                    trx.begin(call.connection);
                }
                throw new AssertionError("시작 실패를 호출부에 전달해야 합니다.");
            } catch (DBException ex) {
                require(ex.getCause() == call.beginError && ex.getCode() == 10, "시작 오류와 코드를 보존해야 합니다.");
                requireSuppressed(call.beginError, fromPool ? new Throwable[]{call.closeError} : new Throwable[0]);
            }

            requireDetached(trx);
            trx.rollback();
            require(call.closes == (fromPool ? 1 : 0) && call.rollbacks == 0,
                    "시작 실패 시 직접 얻은 연결만 닫고 외부 연결은 호출부에 남겨야 합니다.");
        }
    }

    private static void commitFailurePreservesCleanupErrors(StubDataSource source) throws Exception {

        for (boolean fromPool : new boolean[]{true, false}) {
            JdbcCall call = source.call = new JdbcCall();
            DBTrx    trx  = new DBTrx();
            call.commitError   = new SQLException("commit 실패", "08007", 20);
            call.rollbackError = new SQLException("rollback 실패");
            call.closeError    = new SQLException("연결 종료 실패");
            if (fromPool) {
                trx.begin();
            } else {
                trx.begin(call.connection);
            }

            try {
                trx.commit();
                throw new AssertionError("commit 실패를 호출부에 전달해야 합니다.");
            } catch (DBException ex) {
                require(ex.getCause() == call.commitError && ex.getCode() == 20, "commit 오류와 코드를 보존해야 합니다.");
                requireSuppressed(call.commitError, fromPool
                        ? new Throwable[]{call.rollbackError, call.closeError} : new Throwable[]{call.rollbackError});
                trx.rollback(ex);
            }

            requireDetached(trx);
            require(call.commits == 1 && call.rollbacks == 1 && call.closes == (fromPool ? 1 : 0),
                    "commit 실패 후 한 번 rollback하고 직접 얻은 연결만 닫아야 합니다.");
        }
    }

    private static void rollbackPreservesOriginalError(StubDataSource source) throws Exception {

        for (boolean fromPool : new boolean[]{true, false}) {
            JdbcCall              call     = source.call = new JdbcCall();
            DBTrx                 trx      = new DBTrx();
            IllegalStateException original = new IllegalStateException("업무 처리 실패");
            call.rollbackError = new SQLException("rollback 실패");
            call.closeError    = new SQLException("연결 종료 실패");
            if (fromPool) {
                trx.begin();
            } else {
                trx.begin(call.connection);
            }

            trx.rollback(original);
            trx.rollback(original);

            requireSuppressed(original, fromPool
                    ? new Throwable[]{call.rollbackError, call.closeError} : new Throwable[]{call.rollbackError});
            requireDetached(trx);
            require(call.rollbacks == 1 && call.closes == (fromPool ? 1 : 0),
                    "반복 정리는 직접 얻은 연결만 한 번 닫고 원인과 정리 결과를 변경하면 안 됩니다.");
        }
    }

    private static void repeatedCleanupAllowsReuse(StubDataSource source) throws Exception {

        JdbcCall first  = source.call = new JdbcCall();
        JdbcCall second = new JdbcCall();
        DBTrx    trx    = new DBTrx();
        trx.begin();
        trx.begin(first.connection);

        try {
            trx.begin(second.connection);
            throw new AssertionError("사용 중인 연결을 다른 연결로 덮어쓰면 안 됩니다.");
        } catch (IllegalStateException expected) {
            require(second.beginCalls == 0 && second.closes == 0, "거절한 연결은 호출부가 관리해야 합니다.");
        }

        trx.commit();
        trx.commit();
        trx.rollback();
        requireDetached(trx);
        require(first.beginCalls == 1 && first.commits == 1 && first.rollbacks == 0 && first.closes == 1,
                "시작·종료를 반복해도 완료한 연결을 다시 사용하면 안 됩니다.");

        trx.begin(second.connection);
        trx.begin();
        trx.commit();
        trx.rollback();
        trx.commit();
        requireDetached(trx);
        require(second.beginCalls == 1 && second.commits == 1 && second.rollbacks == 0 && second.closes == 0,
                "외부 연결로 다시 시작한 경우 commit 후에도 호출부가 연결을 관리해야 합니다.");
        second.connection.close();
        require(second.closes == 1, "외부 연결은 호출부가 한 번 닫아야 합니다.");

        JdbcCall third = source.call = new JdbcCall();
        trx.begin();
        trx.rollback();
        trx.rollback();
        requireDetached(trx);
        require(third.rollbacks == 1 && third.closes == 1, "외부 연결 사용 후 직접 얻은 연결도 정상적으로 반환해야 합니다.");
    }

    private static void closeFailureAfterCommitDoesNotRollback(StubDataSource source) throws Exception {

        JdbcCall call = source.call = new JdbcCall();
        DBTrx    trx  = new DBTrx();
        call.closeError = new SQLException("commit 후 연결 종료 실패");
        trx.begin();

        try {
            trx.commit();
            throw new AssertionError("연결 종료 실패도 호출부에 전달해야 합니다.");
        } catch (DBException ex) {
            require(ex.getCause() == call.closeError, "연결 종료 오류를 보존해야 합니다.");
        }

        requireDetached(trx);
        trx.rollback();
        require(call.commits == 1 && call.rollbacks == 0 && call.closes == 1,
                "commit이 끝난 연결에 rollback을 다시 시도하면 안 됩니다.");
    }

    private static void legacyRollbackPreservesCleanupErrors(StubDataSource source) throws Exception {

        for (boolean fromPool : new boolean[]{true, false}) {
            JdbcCall call = source.call = new JdbcCall();
            DBTrx    trx  = new DBTrx();
            call.rollbackError = new SQLException("rollback 실패");
            call.closeError    = new SQLException("연결 종료 실패");
            if (fromPool) {
                trx.begin();
            } else {
                trx.begin(call.connection);
            }

            trx.rollback();

            requireSuppressed(call.rollbackError, fromPool ? new Throwable[]{call.closeError} : new Throwable[0]);
            requireDetached(trx);
            require(call.rollbacks == 1 && call.closes == (fromPool ? 1 : 0),
                    "기존 rollback()도 오류 발생 후 직접 얻은 연결만 반환해야 합니다.");
        }
    }

    private static void requireDetached(DBTrx trx) throws Exception {

        require(field(DBTrx.class, "conn").get(trx) == null, "종료 후 내부 conn은 null이어야 합니다.");
        require(Boolean.FALSE.equals(field(DBTrx.class, "ownsConnection").get(trx)), "종료 후 연결 소유권을 초기화해야 합니다.");
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

    private static class JdbcCall {

        private int          beginCalls;
        private int          commits;
        private int          rollbacks;
        private int          closes;
        private SQLException beginError;
        private SQLException commitError;
        private SQLException rollbackError;
        private SQLException closeError;

        private final Connection connection = (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "setAutoCommit" -> {
                            require(Boolean.FALSE.equals(args[0]), "수동 트랜잭션으로 설정해야 합니다.");
                            beginCalls++;
                            if (beginError != null) {
                                throw beginError;
                            }
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
    }
}
