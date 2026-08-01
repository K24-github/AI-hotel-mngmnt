package hotel.test;

/**
 * Runs every suite: {@code java -cp out hotel.test.AllTests}
 * <p>
 * Add new suites here as they arrive — the natural next one is a parser suite for
 * natural-language booking entry, which will need its own setup and is expected to
 * be slower than these.
 */
public final class AllTests {
    public static void main(String[] args) {
        BillingTests.run();
        AvailabilityTests.run();
        Assert.printSummary();
        if (Assert.hasFailures()) {
            System.exit(1);
        }
    }

    private AllTests() {
    }
}
