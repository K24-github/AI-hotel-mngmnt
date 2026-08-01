package hotel.service;

import hotel.model.Booking;
import hotel.model.BookingStatus;
import hotel.model.DeluxeRoom;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.StudioRoom;
import hotel.model.SuiteRoom;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Service layer: owns the room inventory and the booking ledger, and is the only
 * place that mutates both together. The UI talks to this class and never to the
 * collections directly.
 */
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
     * True if nothing already holds this room across [from, to). Back-to-back stays
     * are fine: a departure on the 5th does not clash with an arrival on the 5th.
     */
    public boolean isAvailable(Room room, LocalDate from, LocalDate to) {
        if (room == null || from == null || to == null) {
            throw new IllegalArgumentException("Room and both dates are required.");
        }
        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("Departure must be after arrival.");
        }
        return bookings.stream().noneMatch(booking ->
                booking.getRoom().equals(room) && booking.overlaps(from, to));
    }

    /** Same as {@link #isAvailable}, but ignores one booking — used when changing it. */
    private boolean isAvailableExcluding(Room room, LocalDate from, LocalDate to, Booking ignored) {
        return bookings.stream().noneMatch(booking ->
                booking != ignored && booking.getRoom().equals(room) && booking.overlaps(from, to));
    }

    public List<Room> getAvailableRooms(LocalDate from, LocalDate to) {
        return rooms.stream().filter(room -> isAvailable(room, from, to)).toList();
    }

    /** Bookings holding this room on a given night, if any. */
    public Optional<Booking> getBookingOn(Room room, LocalDate date) {
        return bookings.stream()
                .filter(booking -> booking.getRoom().equals(room))
                .filter(booking -> booking.overlaps(date, date.plusDays(1)))
                .findFirst();
    }

    /**
     * Free rooms that cost more per night than {@code fromRoom} and still fit the party.
     * Returns an empty list rather than throwing when the room is not occupied.
     */
    public List<Room> getAvailableUpgradeOptions(Room fromRoom) {
        if (fromRoom == null || !fromRoom.isOccupied()) {
            return List.of();
        }
        return getUpgradeOptionsFor(fromRoom.getActiveBooking());
    }

    public List<Room> getUpgradeOptionsFor(Booking booking) {
        if (booking == null || !booking.holdsInventory()) {
            return List.of();
        }
        Room fromRoom = booking.getRoom();
        LocalDate movesOn = moveDateFor(booking);
        LocalDate until = booking.getDepartureDate();
        if (!until.isAfter(movesOn)) {
            return List.of();
        }
        return rooms.stream()
                .filter(room -> !room.equals(fromRoom))
                .filter(room -> !(booking.isActive() && room.isOccupied()))
                .filter(room -> room.getNightlyRate() > fromRoom.getNightlyRate())
                .filter(room -> room.getCapacity() >= booking.getGuestCount())
                .filter(room -> isAvailableExcluding(room, movesOn, until, booking))
                .toList();
    }

    // ------------------------------------------------------------- operations

    /**
     * Books a room for future dates without anybody arriving. The room is held but
     * stays physically free until {@link #checkIn}.
     */
    public Booking createReservation(Room room, String guestName, String phone, String notes,
                                     LocalDate arrivalDate, int nights, int guestCount) {
        if (room == null) {
            throw new IllegalArgumentException("A room must be selected.");
        }
        if (!rooms.contains(room)) {
            throw new IllegalArgumentException("Room " + room.getRoomNumber() + " is not part of this hotel.");
        }
        if (arrivalDate == null) {
            throw new IllegalArgumentException("An arrival date is required.");
        }
        if (nights <= 0) {
            throw new IllegalArgumentException("Nights must be greater than zero.");
        }
        if (!isAvailable(room, arrivalDate, arrivalDate.plusDays(nights))) {
            throw new IllegalStateException("Room " + room.getRoomNumber()
                    + " is already booked for some of those dates.");
        }

        Guest guest = new Guest(guestName, phone, notes);
        Booking booking = new Booking(guest, room, nights, guestCount, arrivalDate);
        bookings.add(booking);
        return booking;
    }

    /** Walk-in: reserve from today and put the guest in the room immediately. */
    public Booking createBooking(Room room, String guestName, String phone, String notes,
                                 int nights, int guestCount) {
        Booking booking = createReservation(room, guestName, phone, notes, LocalDate.now(), nights, guestCount);
        checkIn(booking);
        return booking;
    }

    /** Moves a reservation to in-house and physically occupies the room. */
    public Booking checkIn(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("A booking is required.");
        }
        Room room = booking.getRoom();
        if (room.isOccupied()) {
            throw new IllegalStateException("Room " + room.getRoomNumber()
                    + " still has a guest in it.");
        }
        booking.checkIn();
        room.assignBooking(booking);
        return booking;
    }

    /** Cancels a reservation before arrival and frees its dates. */
    public Booking cancelReservation(Booking booking) {
        if (booking == null) {
            throw new IllegalArgumentException("A booking is required.");
        }
        booking.cancel();
        return booking;
    }

    public Booking extendStay(Room room, int extraNights) {
        return extendStay(requireActiveBooking(room), extraNights);
    }

    public Booking extendStay(Booking booking, int extraNights) {
        requireOpen(booking);
        if (extraNights <= 0) {
            throw new IllegalArgumentException("Extra nights must be greater than zero.");
        }
        Room room = booking.getRoom();
        LocalDate currentDeparture = booking.getDepartureDate();
        LocalDate newDeparture = currentDeparture.plusDays(extraNights);
        if (!isAvailableExcluding(room, currentDeparture, newDeparture, booking)) {
            throw new IllegalStateException("Room " + room.getRoomNumber()
                    + " is booked by someone else on those nights.");
        }
        booking.extendStay(extraNights);
        return booking;
    }

    /**
     * Moves an in-house guest into a free, higher-tier room. Nights already stayed
     * remain billed at the original rate.
     */
    public Booking upgradeBooking(Room currentRoom, Room newRoom) {
        return upgradeBooking(requireActiveBooking(currentRoom), newRoom);
    }

    /**
     * Moves a booking into a different room. For an in-house guest the physical room
     * assignment moves too; for a reservation only the ledger changes, since nobody
     * is in a room yet.
     */
    public Booking upgradeBooking(Booking booking, Room newRoom) {
        requireOpen(booking);
        if (newRoom == null) {
            throw new IllegalArgumentException("A new room must be selected.");
        }
        Room currentRoom = booking.getRoom();
        if (newRoom.equals(currentRoom)) {
            throw new IllegalArgumentException("Choose a different room to move into.");
        }
        // Only an in-house move cares about who is physically in the room right now.
        if (booking.isActive() && newRoom.isOccupied()) {
            throw new IllegalStateException("Selected room is already occupied.");
        }
        LocalDate movesOn = moveDateFor(booking);
        if (!isAvailableExcluding(newRoom, movesOn, booking.getDepartureDate(), booking)) {
            throw new IllegalStateException("Room " + newRoom.getRoomNumber()
                    + " is booked by someone else before this stay ends.");
        }

        // Validates capacity and remaining nights before any room state is touched.
        booking.upgradeRoom(newRoom);
        if (booking.isActive()) {
            currentRoom.clearBooking();
            newRoom.assignBooking(booking);
        }
        return booking;
    }

    private LocalDate moveDateFor(Booking booking) {
        if (!booking.isActive()) {
            return booking.getArrivalDate();
        }
        LocalDate today = LocalDate.now();
        return today.isAfter(booking.getArrivalDate()) ? today : booking.getArrivalDate();
    }

    private void requireOpen(Booking booking) {
        if (booking == null) {
            throw new IllegalStateException("No booking selected.");
        }
        if (!booking.holdsInventory()) {
            throw new IllegalStateException("Booking " + booking.getBookingId() + " is "
                    + booking.getStatus().getLabel().toLowerCase() + " and can no longer be changed.");
        }
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

    /** Rooms held on {@code date}, whether by an in-house guest or a reservation. */
    public int getBookedCount(LocalDate date) {
        return (int) rooms.stream().filter(room -> getBookingOn(room, date).isPresent()).count();
    }

    public int getFreeCount(LocalDate date) {
        return rooms.size() - getBookedCount(date);
    }

    /** Share of the hotel spoken for on {@code date}, as a percentage. */
    public double getOccupancyRate(LocalDate date) {
        return rooms.isEmpty() ? 0 : (getBookedCount(date) * 100.0) / rooms.size();
    }

    /** Bookings arriving on {@code date}; the day's expected check-ins. */
    public int getArrivalsOn(LocalDate date) {
        return (int) bookings.stream()
                .filter(Booking::holdsInventory)
                .filter(booking -> booking.getArrivalDate().equals(date))
                .count();
    }

    public int getReservedCount() {
        return (int) bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.RESERVED)
                .count();
    }

    /** Value of stays booked but not yet arrived. */
    public double getReservedValue() {
        return bookings.stream()
                .filter(booking -> booking.getStatus() == BookingStatus.RESERVED)
                .mapToDouble(Booking::getCurrentBill)
                .sum();
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
                .filter(booking -> booking.getStatus() == BookingStatus.CHECKED_OUT)
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