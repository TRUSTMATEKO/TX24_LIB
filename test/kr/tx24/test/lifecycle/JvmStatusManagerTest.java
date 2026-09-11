package kr.tx24.test.lifecycle;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class JvmStatusManagerTest {

    private static final Constructor<?> TRACKER_CONSTRUCTOR;
    private static final Method         EVALUATE_METHOD;
    private static final Method         IS_WARNING_REQUIRED_METHOD;
    private static final Method         IS_RECOVERED_METHOD;
    private static final Method         DURATION_MILLIS_METHOD;
    private static final Method         GC_COUNT_METHOD;

    static {
        try {
            Class<?> trackerClass = Class.forName(
                    "kr.tx24.lib.lifecycle.JvmStatusManager$GcUsageWarningTracker");
            Class<?> evaluationClass = Class.forName(
                    "kr.tx24.lib.lifecycle.JvmStatusManager$GcUsageEvaluation");

            TRACKER_CONSTRUCTOR = trackerClass.getDeclaredConstructor(
                    double.class, double.class, long.class, int.class);
            EVALUATE_METHOD = trackerClass.getDeclaredMethod("evaluate", double.class, long.class);
            IS_WARNING_REQUIRED_METHOD = evaluationClass.getDeclaredMethod("isWarningRequired");
            IS_RECOVERED_METHOD = evaluationClass.getDeclaredMethod("isRecovered");
            DURATION_MILLIS_METHOD = evaluationClass.getDeclaredMethod("durationMillis");
            GC_COUNT_METHOD = evaluationClass.getDeclaredMethod("gcCount");

            TRACKER_CONSTRUCTOR.setAccessible(true);
            EVALUATE_METHOD.setAccessible(true);
            IS_WARNING_REQUIRED_METHOD.setAccessible(true);
            IS_RECOVERED_METHOD.setAccessible(true);
            DURATION_MILLIS_METHOD.setAccessible(true);
            GC_COUNT_METHOD.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        isWarningRaisedOnlyAfterDurationAndRepeatedGc();
        isWarningRaisedAgainOnlyAfterRecovery();
        isHighUsageDurationResetBelowThreshold();
    }

    private static void isWarningRaisedOnlyAfterDurationAndRepeatedGc() throws ReflectiveOperationException {
        Object tracker = newTracker();

        assertFalse(evaluate(tracker, 0.75, 0).isWarningRequired());
        assertFalse(evaluate(tracker, 0.80, 10).isWarningRequired());
        assertFalse(evaluate(tracker, 0.85, 29).isWarningRequired());

        Evaluation evaluation = evaluate(tracker, 0.82, 30);
        assertTrue(evaluation.isWarningRequired());
        assertEquals(4, evaluation.gcCount());
        assertEquals(TimeUnit.MINUTES.toMillis(30), evaluation.durationMillis());

        assertFalse(evaluate(tracker, 0.90, 40).isWarningRequired());
    }

    private static void isWarningRaisedAgainOnlyAfterRecovery() throws ReflectiveOperationException {
        Object tracker = newTracker();

        evaluate(tracker, 0.80, 0);
        evaluate(tracker, 0.80, 15);
        assertTrue(evaluate(tracker, 0.80, 30).isWarningRequired());

        assertFalse(evaluate(tracker, 0.70, 35).isRecovered());
        assertFalse(evaluate(tracker, 0.80, 65).isWarningRequired());

        assertTrue(evaluate(tracker, 0.60, 70).isRecovered());

        evaluate(tracker, 0.80, 80);
        evaluate(tracker, 0.80, 95);
        assertTrue(evaluate(tracker, 0.80, 110).isWarningRequired());
    }

    private static void isHighUsageDurationResetBelowThreshold() throws ReflectiveOperationException {
        Object tracker = newTracker();

        evaluate(tracker, 0.80, 0);
        evaluate(tracker, 0.80, 20);
        evaluate(tracker, 0.70, 25);
        evaluate(tracker, 0.80, 30);
        evaluate(tracker, 0.80, 50);

        assertFalse(evaluate(tracker, 0.80, 59).isWarningRequired());
        assertTrue(evaluate(tracker, 0.80, 60).isWarningRequired());
    }

    private static Object newTracker() throws ReflectiveOperationException {
        return TRACKER_CONSTRUCTOR.newInstance(0.75, 0.65, TimeUnit.MINUTES.toMillis(30), 3);
    }

    private static Evaluation evaluate(Object tracker, double usageRate, long minute)
            throws ReflectiveOperationException {
        Object evaluation = EVALUATE_METHOD.invoke(tracker, usageRate, TimeUnit.MINUTES.toMillis(minute));
        return new Evaluation(
                (boolean) IS_WARNING_REQUIRED_METHOD.invoke(evaluation),
                (boolean) IS_RECOVERED_METHOD.invoke(evaluation),
                (long) DURATION_MILLIS_METHOD.invoke(evaluation),
                (int) GC_COUNT_METHOD.invoke(evaluation));
    }

    private static void assertTrue(boolean value) {
        if (!value) {
            throw new AssertionError("Expected true");
        }
    }

    private static void assertFalse(boolean value) {
        if (value) {
            throw new AssertionError("Expected false");
        }
    }

    private static void assertEquals(long expected, long actual) {
        if (expected != actual) {
            throw new AssertionError("Expected " + expected + " but was " + actual);
        }
    }

    private record Evaluation(boolean isWarningRequired, boolean isRecovered, long durationMillis, int gcCount) {}
}
