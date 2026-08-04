package hotel.model;

public final class StaySegment {
    private final Room room;
    private int nights;

    StaySegment(Room room, int nights) {
        if (room == null) {
            throw new IllegalArgumentException("Segment room cannot be empty.");
        }
        if (nights < 0) {
            throw new IllegalArgumentException("Segment nights cannot be negative.");
        }
        this.room = room;
        this.nights = nights;
    }

    /** Rebuilds a segment read back from storage. */
    public static StaySegment restore(Room room, int nights) {
        return new StaySegment(room, nights);
    }

    public Room getRoom() {
        return room;
    }

    public int getNights() {
        return nights;
    }

    public double getSubtotal() {
        return room.calculatePrice(nights);
    }

    void addNights(int extraNights) {
        if (extraNights <= 0) {
            throw new IllegalArgumentException("Extra nights must be greater than zero.");
        }
        nights += extraNights;
    }

    @Override
    public String toString() {
        return nights + " night(s) in " + room;
    }
}
