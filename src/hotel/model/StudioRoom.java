package hotel.model;

public class StudioRoom extends Room {
    public static final double NIGHTLY_RATE = 450_000;
    public static final int CAPACITY = 2;

    public StudioRoom(String roomNumber, int floorNumber) {
        super(roomNumber, floorNumber, CAPACITY);
    }

    @Override
    public String getTierName() {
        return "Studio";
    }

    @Override
    public double getNightlyRate() {
        return NIGHTLY_RATE;
    }
}
