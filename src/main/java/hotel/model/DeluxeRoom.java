package hotel.model;

public class DeluxeRoom extends Room {
    public static final double NIGHTLY_RATE = 780_000;
    public static final int CAPACITY = 3;

    public DeluxeRoom(String roomNumber, int floorNumber) {
        super(roomNumber, floorNumber, CAPACITY);
    }

    @Override
    public String getTierName() {
        return "Deluxe";
    }

    @Override
    public double getNightlyRate() {
        return NIGHTLY_RATE;
    }
}
