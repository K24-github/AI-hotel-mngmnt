package hotel.model;

public class SuiteRoom extends Room {
    public static final double NIGHTLY_RATE = 1_350_000;
    public static final int CAPACITY = 5;

    public SuiteRoom(String roomNumber, int floorNumber) {
        super(roomNumber, floorNumber, CAPACITY);
    }

    @Override
    public String getTierName() {
        return "Suite";
    }

    @Override
    public double getNightlyRate() {
        return NIGHTLY_RATE;
    }
}
