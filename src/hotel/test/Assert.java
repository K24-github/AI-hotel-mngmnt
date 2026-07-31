package hotel.test;

import java.util.Objects;

final class Assert {
    private static int passed;
    private static int failed;

    static void equals(String name, Object expected, Object actual) {
        boolean ok = (expected instanceof Number expectedNumber && actual instanceof Number actualNumber)
                ? Math.abs(expectedNumber.doubleValue() - actualNumber.doubleValue()) < 0.001
                : Objects.equals(expected, actual);
        report(ok, name, ok ? "" : "expected <" + expected + "> but was <" + actual + ">");
    }

    static void isTrue(String name, boolean condition) {
        report(condition, name, "expected true");
    }

    static void isFalse(String name, boolean condition) {
        report(!condition, name, "expected false");
    }

    /** Passes when the action rejects the operation with an unchecked rule violation. */
    static void throwsError(String name, Runnable action) {
        try {
            action.run();
            report(false, name, "expected an exception, none was thrown");
        } catch (RuntimeException expected) {
            report(true, name, "");
        }
    }

    private static void report(boolean ok, String name, String detail) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name + " -- " + detail);
        }
    }

    static void printSummary() {
        System.out.printf("%n%d passed, %d failed%n", passed, failed);
    }

    static boolean hasFailures() {
        return failed > 0;
    }

    private Assert() {
    }
}
