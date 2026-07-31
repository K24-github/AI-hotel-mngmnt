package hotel.service;

import hotel.model.Booking;
import hotel.model.BookingStatus;
import hotel.model.DeluxeRoom;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.StudioRoom;
import hotel.model.SuiteRoom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class HotelManager {
    private static final Comparator<Room> ROOM_ORDER =
            Comparator.comparingInt(Room::getFloorNumber).thenComparing(Room::getRoomNumber);

    private final List<Room> rooms = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();

    public HotelManager() {
        seedRooms();
    }

    private void seedRooms() {
        for (int i = 1; i <= 12; i++) {
            rooms.add(new StudioRoom(String.format("1%02d", i), 1));
        }
        for (int i = 1; i <= 12; i++) {
            rooms.add(new DeluxeRoom(String.format("2%02d", i), 2));
        }
        for (int i = 1; i <= 8; i++) {
            rooms.add(new SuiteRoom(String.format("3%02d", i), 3));
        }
        rooms.sort(ROOM_ORDER);
    }

    // ---------------------------------------------------------------- queries

    /** @return every room, floor-then-number order. Unmodifiable. */
    public List<Room> getRooms() {
        return List.copyOf(rooms);
    }

    /** @return every booking ever made, oldest first. Unmodifiable. */
    public List<Booking> getBookings() {
        return List.copyOf(bookings);
    }

    /** Floor numbers that actually exist, ascending. Drives the floor filter. */
    public List<Integer> getFloors() {
        return rooms.stream().map(Room::getFloorNumber).distinct().sorted().toList();
    }

    public List<Room> getRoomsByFloor(int floor) {
        return rooms.stream().filter(room -> room.getFloorNumber() == floor).toList();
    }

    public List<Room> getRoomsByTier(String tierName) {
        if (tierName == null) {
            return List.of();
        }
        return rooms.stream().filter(room -> room.getTierName().equalsIgnoreCase(tierName)).toList();
    }

    public Optional<Room> findRoom(String roomNumber) {
        if (roomNumber == null) {
            return Optional.empty();
        }
        return rooms.stream().filter(room -> room.getRoomNumber().equalsIgnoreCase(roomNumber.trim())).findFirst();
    }

    /**
     * Free rooms that cost more per night than {@code fromRoom} and still fit the party.
     * Returns an empty list rather than throwing when the room is not occupied.
     */
    public List<Room> getAvailableUpgradeOptions(Room fromRoom) {
        if (fromRoom == null || !fromRoom.isOccupied()) {
            return List.of();
        }
        Booking booking = fromRoom.getActiveBooking();
        return rooms.stream()
                .filter(room -> !room.isOccupied())
                .filter(room -> room.getNightlyRate() > fromRoom.getNightlyRate())
                .filter(room -> room.getCapacity() >= booking.getGuestCount())
                .toList();
    }

    // ------------------------------------------------------------- operations

    public Booking createBooking(Room room, String guestName, String phone, String notes,
                                 int nights, int guestCount) {
        if (room == null) {
            throw new IllegalArgumentException("A room must be selected.");
        }
        if (!rooms.contains(room)) {
            throw new IllegalArgumentException("Room " + room.getRoomNumber() + " is not part of this hotel.");
        }
        if (room.isOccupied()) {
            throw new IllegalStateException("Selected room is already occupied.");
        }

        Guest guest = new Guest(guestName, phone, notes);
        Booking booking = new Booking(guest, room, nights, guestCount);
        room.assignBooking(booking);
        bookings.add(booking);
        return booking;
    }

    public Booking extendStay(Room room, int extraNights) {
        Booking booking = requireActiveBooking(room);
        booking.extendStay(extraNights);
        return booking;
    }

    /**
     * Moves an in-house guest into a free, higher-tier room. Nights already stayed
     * remain billed at the original rate.
     */
    public Booking upgradeBooking(Room currentRoom, Room newRoom) {
        Booking booking = requireActiveBooking(currentRoom);
        if (newRoom == null) {
            throw new IllegalArgumentException("A new room must be selected.");
        }
        if (newRoom.equals(currentRoom)) {
            throw new IllegalArgumentException("Choose a different room to upgrade into.");
        }
        if (newRoom.isOccupied()) {
            throw new IllegalStateException("Selected upgrade room is already occupied.");
        }

        // Validates capacity and remaining nights before any room state is touched.
        booking.upgradeRoom(newRoom);
        currentRoom.clearBooking();
        newRoom.assignBooking(booking);
        return booking;
    }

    /**
     * Closes out a stay and frees the room.
     *
     * @return the final bill, which is then frozen on the booking.
     */
    public double checkOut(Room room) {
        Booking booking = requireActiveBooking(room);
        double finalBill = booking.checkOut();
        room.clearBooking();
        return finalBill;
    }

    // -------------------------------------------------------------- reporting

    public int getAvailableCount() {
        return (int) rooms.stream().filter(room -> !room.isOccupied()).count();
    }

    public int getOccupiedCount() {
        return (int) rooms.stream().filter(Room::isOccupied).count();
    }

    public int getCheckedOutCount() {
        return (int) bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.CHECKED_OUT)
                .count();
    }

    /** Occupied rooms as a percentage of the whole inventory. */
    public double getOccupancyRate() {
        return rooms.isEmpty() ? 0 : (getOccupiedCount() * 100.0) / rooms.size();
    }

    /** Money still on the floor: running bills of guests currently in-house. */
    public double getProjectedRevenue() {
        return bookings.stream()
                .filter(Booking::isActive)
                .mapToDouble(Booking::getCurrentBill)
                .sum();
    }

    /** Money already collected: frozen final bills of completed stays. */
    public double getRealizedRevenue() {
        return bookings.stream()
                .filter(booking -> !booking.isActive())
                .mapToDouble(Booking::getCurrentBill)
                .sum();
    }

    public double getTotalRevenue() {
        return getRealizedRevenue() + getProjectedRevenue();
    }

    private Booking requireActiveBooking(Room room) {
        if (room == null || !room.isOccupied()) {
            throw new IllegalStateException("Selected room is not currently occupied.");
        }
        return room.getActiveBooking();
    }
}
