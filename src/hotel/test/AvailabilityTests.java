package hotel.test;

import hotel.model.Booking;
import hotel.model.Room;
import hotel.service.HotelManager;

import java.time.LocalDate;

public final class AvailabilityTests {
    private static final LocalDate BASE = LocalDate.of(2026, 3, 10);

    public static void main(String[] args) {
        run();
        Assert.printSummary();
        if (Assert.hasFailures()) {
            System.exit(1);
        }
    }

    static void run() {
        System.out.println("\n-- availability --");
        overlapBlocksTheRoom();
        backToBackStaysAreFine();
        aStayInsideAnotherIsBlocked();
        aStaySwallowingAnotherIsBlocked();
        departureIsExclusive();
        otherRoomsAreUnaffected();
        rejectsBackwardsDateRange();
        availableRoomsShrinkWhenBooked();

        rejectsPartyLargerThanRoom();
        rejectsDoubleBooking();

        reservationLeavesTheRoomPhysicallyFree();
        checkingInOccupiesTheRoom();
        cannotCheckInTwice();
        cannotCheckOutTwice();
        cancellingFreesTheDates();
        cannotCancelAfterArrival();

        extendIsBlockedByTheNextArrival();
        upgradeIsBlockedByAFutureReservation();
        upgradeOptionsAreEmptyForFreeRoom();
        upgradeOptionsExcludeTooSmallRooms();
        cannotUpgradeIntoTheSameRoom();

        occupancyIsCountedPerDate();
        occupancyCountsReservationsAndGuestsAlike();

        countsFollowTheChosenDate();
        reservationCanBeExtendedBeforeArrival();
        reservationCanBeMovedBeforeArrival();
        movingAReservationLeavesRoomsUntouched();
        upgradeOptionsSkipRoomsBookedLater();
        finishedBookingsCannotBeChanged();
        collectionsAreNotMutableByCallers();
    }

    // ------------------------------------------------------- overlap boundaries

    /** 10th-13th against 12th-15th: the 12th is shared, so it clashes. */
    private static void overlapBlocksTheRoom() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        Assert.isFalse("overlapping range unavailable", hotel.isAvailable(room, BASE.plusDays(2), BASE.plusDays(5)));
        Assert.throwsError("overlapping reservation rejected",
                () -> hotel.createReservation(room, "Rani", "0813", null, BASE.plusDays(2), 3, 1));
        Assert.isFalse("identical range unavailable", hotel.isAvailable(room, BASE, BASE.plusDays(3)));
    }

    /** The case everyone gets wrong: one guest leaves the morning another arrives. */
    private static void backToBackStaysAreFine() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking first = hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        Assert.equals("departure date", BASE.plusDays(3), first.getDepartureDate());
        Assert.isTrue("arrival on the departure day is free",
                hotel.isAvailable(room, BASE.plusDays(3), BASE.plusDays(5)));
        hotel.createReservation(room, "Rani", "0813", null, BASE.plusDays(3), 2, 1);
        Assert.equals("both bookings kept", 2, hotel.getBookings().size());
    }

    private static void aStayInsideAnotherIsBlocked() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 7, 2);
        Assert.isFalse("contained range unavailable",
                hotel.isAvailable(room, BASE.plusDays(2), BASE.plusDays(4)));
    }

    private static void aStaySwallowingAnotherIsBlocked() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE.plusDays(2), 2, 2);
        Assert.isFalse("enclosing range unavailable", hotel.isAvailable(room, BASE, BASE.plusDays(7)));
    }

    /** One night on the 10th occupies the 10th only; the 11th is someone else's. */
    private static void departureIsExclusive() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, BASE, 1, 1);

        Assert.equals("one-night departure", BASE.plusDays(1), booking.getDepartureDate());
        Assert.isTrue("the night itself is taken", booking.overlaps(BASE, BASE.plusDays(1)));
        Assert.isFalse("the next night is not", booking.overlaps(BASE.plusDays(1), BASE.plusDays(2)));
    }

    private static void otherRoomsAreUnaffected() {
        HotelManager hotel = new HotelManager();
        Room booked = hotel.findRoom("101").orElseThrow();
        Room free = hotel.findRoom("102").orElseThrow();
        hotel.createReservation(booked, "Kevin", "0812", null, BASE, 3, 2);
        Assert.isTrue("neighbouring room still free", hotel.isAvailable(free, BASE, BASE.plusDays(3)));
    }

    private static void rejectsBackwardsDateRange() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Assert.throwsError("departure before arrival", () -> hotel.isAvailable(room, BASE.plusDays(3), BASE));
        Assert.throwsError("zero-length range", () -> hotel.isAvailable(room, BASE, BASE));
    }

    private static void availableRoomsShrinkWhenBooked() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        int before = hotel.getAvailableRooms(BASE, BASE.plusDays(2)).size();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        Assert.equals("one fewer room for those dates", before - 1,
                hotel.getAvailableRooms(BASE, BASE.plusDays(2)).size());
        Assert.equals("other dates unaffected", before,
                hotel.getAvailableRooms(BASE.plusDays(5), BASE.plusDays(6)).size());
    }

    // ------------------------------------------------------------- room fit

    private static void rejectsPartyLargerThanRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Assert.throwsError("studio cannot hold 4 guests",
                () -> hotel.createBooking(studio, "Kevin", "0812", null, 2, 4));
        Assert.isFalse("failed booking leaves room free", studio.isOccupied());
    }

    private static void rejectsDoubleBooking() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);
        Assert.throwsError("second booking on the same room",
                () -> hotel.createBooking(studio, "Rani", "0813", null, 1, 1));
    }

    // --------------------------------------------------------------- lifecycle

    private static void reservationLeavesTheRoomPhysicallyFree() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        Assert.isFalse("nobody is in the room yet", room.isOccupied());
        Assert.equals("counted as available", 32, hotel.getAvailableCount());
        Assert.equals("counted as reserved", 1, hotel.getReservedCount());
    }

    private static void checkingInOccupiesTheRoom() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, LocalDate.now(), 2, 2);
        hotel.checkIn(booking);

        Assert.isTrue("room occupied", room.isOccupied());
        Assert.equals("status", "Checked in", booking.getStatus().getLabel());
        Assert.equals("reserved count drops", 0, hotel.getReservedCount());
    }

    private static void cannotCheckInTwice() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(room, "Kevin", "0812", null, 2, 2);
        Assert.throwsError("second check-in", () -> hotel.checkIn(booking));
    }

    private static void cannotCheckOutTwice() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(room, "Kevin", "0812", null, 1, 1);
        hotel.checkOut(room);
        Assert.throwsError("second check-out", () -> hotel.checkOut(room));
        Assert.isFalse("room released", room.isOccupied());
    }

    private static void cancellingFreesTheDates() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        Assert.isFalse("held while reserved", hotel.isAvailable(room, BASE, BASE.plusDays(3)));
        hotel.cancelReservation(booking);
        Assert.isTrue("released after cancelling", hotel.isAvailable(room, BASE, BASE.plusDays(3)));
        Assert.equals("status", "Cancelled", booking.getStatus().getLabel());
    }

    private static void cannotCancelAfterArrival() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(room, "Kevin", "0812", null, 2, 2);
        Assert.throwsError("cancel an in-house guest", () -> hotel.cancelReservation(booking));
    }

    // ----------------------------------------------------- changes to a stay

    private static void extendIsBlockedByTheNextArrival() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking staying = hotel.createBooking(room, "Kevin", "0812", null, 2, 2);
        hotel.createReservation(room, "Rani", "0813", null, staying.getDepartureDate(), 2, 1);

        Assert.throwsError("extend into someone else's booking", () -> hotel.extendStay(room, 2));
        Assert.equals("nights unchanged after refusal", 2, staying.getNights());
    }

    private static void upgradeIsBlockedByAFutureReservation() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();

        hotel.createBooking(studio, "Kevin", "0812", null, 4, 2);
        hotel.createReservation(suite, "Rani", "0813", null, LocalDate.now().plusDays(1), 2, 2);

        Assert.throwsError("upgrade into a room booked later", () -> hotel.upgradeBooking(studio, suite));
        Assert.isFalse("suite not offered as an option",
                hotel.getAvailableUpgradeOptions(studio).contains(suite));
    }

    /** Regression: this used to throw NullPointerException on a vacant room. */
    private static void upgradeOptionsAreEmptyForFreeRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Assert.equals("no options for a free room", 0, hotel.getAvailableUpgradeOptions(studio).size());
        Assert.equals("no options for null", 0, hotel.getAvailableUpgradeOptions(null).size());
    }

    private static void upgradeOptionsExcludeTooSmallRooms() {
        HotelManager hotel = new HotelManager();
        Room deluxe = hotel.findRoom("201").orElseThrow();
        hotel.createBooking(deluxe, "Kevin", "0812", null, 2, 3);

        java.util.List<Room> options = hotel.getAvailableUpgradeOptions(deluxe);
        Assert.isTrue("upgrade options exist", !options.isEmpty());
        Assert.isTrue("all options fit the party",
                options.stream().allMatch(room -> room.getCapacity() >= 3));
        Assert.isTrue("all options cost more",
                options.stream().allMatch(room -> room.getNightlyRate() > deluxe.getNightlyRate()));
    }

    private static void cannotUpgradeIntoTheSameRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(studio, "Kevin", "0812", null, 1, 1);
        Assert.throwsError("upgrade into the same room", () -> hotel.upgradeBooking(studio, studio));
    }

    /** The dashboard asks "how full are we that night", not "who is here now". */
    private static void occupancyIsCountedPerDate() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        Assert.equals("taken on the first night", 1, hotel.getBookedCount(BASE));
        Assert.equals("taken on the second night", 1, hotel.getBookedCount(BASE.plusDays(1)));
        Assert.equals("free again on departure day", 0, hotel.getBookedCount(BASE.plusDays(2)));
        Assert.equals("free the day before", 0, hotel.getBookedCount(BASE.minusDays(1)));

        Assert.equals("available on a booked night", 31, hotel.getFreeCount(BASE));
        Assert.equals("available on a free night", 32, hotel.getFreeCount(BASE.plusDays(5)));
        Assert.equals("occupancy rate on a booked night", 100.0 / 32, hotel.getOccupancyRate(BASE));
        Assert.equals("occupancy rate on a free night", 0.0, hotel.getOccupancyRate(BASE.plusDays(5)));
    }

    /** A room held by a reservation is just as unavailable as one with a guest in it. */
    private static void occupancyCountsReservationsAndGuestsAlike() {
        HotelManager hotel = new HotelManager();
        LocalDate today = LocalDate.now();
        hotel.createBooking(hotel.findRoom("101").orElseThrow(), "Kevin", "0812", null, 1, 1);
        hotel.createReservation(hotel.findRoom("102").orElseThrow(), "Rani", "0813", null, today, 1, 1);

        Assert.equals("both rooms counted today", 2, hotel.getBookedCount(today));
        Assert.equals("only the arrival is physically in-house", 1, hotel.getOccupiedCount());
    }

    /** The dashboard reads these, so they must answer for a date, not just for now. */
    private static void countsFollowTheChosenDate() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        Assert.equals("booked on the arrival date", 1, hotel.getBookedCount(BASE));
        Assert.equals("free on the arrival date", 31, hotel.getFreeCount(BASE));
        Assert.equals("booked the night after", 1, hotel.getBookedCount(BASE.plusDays(1)));
        Assert.equals("free again on departure day", 0, hotel.getBookedCount(BASE.plusDays(2)));
        Assert.equals("nothing booked a week earlier", 0, hotel.getBookedCount(BASE.minusDays(7)));
        Assert.equals("occupancy on arrival", 3.125, hotel.getOccupancyRate(BASE));
        Assert.equals("arrivals on the day", 1, hotel.getArrivalsOn(BASE));
        Assert.equals("no arrivals mid-stay", 0, hotel.getArrivalsOn(BASE.plusDays(1)));
    }

    /** A guest ringing ahead to add a night should not need the booking cancelled. */
    private static void reservationCanBeExtendedBeforeArrival() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        hotel.extendStay(booking, 3);
        Assert.equals("nights after extending a reservation", 5, booking.getNights());
        Assert.equals("departure moves out", BASE.plusDays(5), booking.getDepartureDate());
        Assert.isFalse("still nobody in the room", room.isOccupied());
        Assert.equals("still a reservation", "Reserved", booking.getStatus().getLabel());
    }

    private static void reservationCanBeMovedBeforeArrival() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();
        Booking booking = hotel.createReservation(studio, "Kevin", "0812", null, BASE, 2, 2);

        hotel.upgradeBooking(booking, suite);
        Assert.equals("booking now points at the suite", suite, booking.getRoom());
        Assert.isTrue("old room free for those dates", hotel.isAvailable(studio, BASE, BASE.plusDays(2)));
        Assert.isFalse("new room held for those dates", hotel.isAvailable(suite, BASE, BASE.plusDays(2)));
        Assert.equals("whole stay repriced, none of it stayed", 2 * 1_350_000.0, booking.getCurrentBill());
    }

    /** Moving a reservation touches the ledger only; no guest is in a room to move. */
    private static void movingAReservationLeavesRoomsUntouched() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();
        Booking booking = hotel.createReservation(studio, "Kevin", "0812", null, BASE, 2, 2);

        hotel.upgradeBooking(booking, suite);
        Assert.isFalse("old room not physically occupied", studio.isOccupied());
        Assert.isFalse("new room not physically occupied", suite.isOccupied());
        Assert.equals("hotel still shows every room free today", 32, hotel.getAvailableCount());
    }

    private static void upgradeOptionsSkipRoomsBookedLater() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();
        Booking booking = hotel.createReservation(studio, "Kevin", "0812", null, BASE, 3, 2);
        hotel.createReservation(suite, "Rani", "0813", null, BASE.plusDays(1), 1, 1);

        Assert.isFalse("suite booked mid-stay is not offered",
                hotel.getUpgradeOptionsFor(booking).contains(suite));
        Assert.throwsError("and is refused if forced",
                () -> hotel.upgradeBooking(booking, suite));
    }

    private static void finishedBookingsCannotBeChanged() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(room, "Kevin", "0812", null, 1, 1);
        hotel.checkOut(room);

        Assert.throwsError("extend after check-out", () -> hotel.extendStay(booking, 1));
        Assert.throwsError("move after check-out",
                () -> hotel.upgradeBooking(booking, hotel.findRoom("301").orElseThrow()));
        Assert.equals("no options for a closed booking", 0, hotel.getUpgradeOptionsFor(booking).size());
    }

    private static void collectionsAreNotMutableByCallers() {
        HotelManager hotel = new HotelManager();
        Assert.throwsError("bookings list is unmodifiable", () -> hotel.getBookings().clear());
        Assert.throwsError("rooms list is unmodifiable", () -> hotel.getRooms().clear());
    }

    private AvailabilityTests() {
    }
}