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

    private static void collectionsAreNotMutableByCallers() {
        HotelManager hotel = new HotelManager();
        Assert.throwsError("bookings list is unmodifiable", () -> hotel.getBookings().clear());
        Assert.throwsError("rooms list is unmodifiable", () -> hotel.getRooms().clear());
    }

    private AvailabilityTests() {
    }
}