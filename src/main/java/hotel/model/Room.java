package hotel.model;

import java.util.Objects;

public abstract class Room implements Priceable {
    private final String roomNumber;
    private final int floorNumber;
    private final int capacity;
    private Booking activeBooking;

    protected Room(String roomNumber, int floorNumber, int capacity) {
        if (roomNumber == null || roomNumber.isBlank()) {
            throw new IllegalArgumentException("Room number cannot be empty.");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException("Room capacity must be greater than zero.");
        }
        this.roomNumber = roomNumber.trim();
        this.floorNumber = floorNumber;
        this.capacity = capacity;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isOccupied() {
        return activeBooking != null && activeBooking.isActive();
    }

    /** @return the booking currently staying in this room, or {@code null} if the room is free. */
    public Booking getActiveBooking() {
        return isOccupied() ? activeBooking : null;
    }

    public void assignBooking(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("Booking cannot be empty.");
        }
        if (isOccupied()) {
            throw new IllegalStateException("Room " + roomNumber + " is already occupied.");
        }
        this.activeBooking = booking;
    }

    public void clearBooking() {
        this.activeBooking = null;
    }

    public abstract String getTierName();

    public abstract double getNightlyRate();

    @Override
    public double calculatePrice(int nights) {
        if (nights < 0) {
            throw new IllegalArgumentException("Nights cannot be negative.");
        }
        return getNightlyRate() * nights;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Room room)) {
            return false;
        }
        return roomNumber.equals(room.roomNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roomNumber);
    }

    @Override
    public String toString() {
        return getTierName() + " Room " + roomNumber;
    }
}
