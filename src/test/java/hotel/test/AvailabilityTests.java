package hotel.test;

import hotel.model.Booking;
import hotel.model.Room;
import hotel.service.HotelManager;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvailabilityTests {
    private static final LocalDate BASE = LocalDate.of(2026, 3, 10);


    // ------------------------------------------------------- overlap boundaries

    /** 10th-13th against 12th-15th: the 12th is shared, so it clashes. */
    @Test
    void overlapBlocksTheRoom() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        assertFalse(hotel.isAvailable(room, BASE.plusDays(2), BASE.plusDays(5)), "overlapping range unavailable");
        assertThrows(RuntimeException.class,
                () -> hotel.createReservation(room, "Rani", "0813", null, BASE.plusDays(2), 3, 1),
                "overlapping reservation rejected");
        assertFalse(hotel.isAvailable(room, BASE, BASE.plusDays(3)), "identical range unavailable");
    }

    /** The case everyone gets wrong: one guest leaves the morning another arrives. */
    @Test
    void backToBackStaysAreFine() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking first = hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        assertEquals(BASE.plusDays(3), first.getDepartureDate(), "departure date");
        assertTrue(hotel.isAvailable(room, BASE.plusDays(3), BASE.plusDays(5)),
                "arrival on the departure day is free");
        hotel.createReservation(room, "Rani", "0813", null, BASE.plusDays(3), 2, 1);
        assertEquals(2, hotel.getBookings().size(), "both bookings kept");
    }

    @Test
    void aStayInsideAnotherIsBlocked() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 7, 2);
        assertFalse(hotel.isAvailable(room, BASE.plusDays(2), BASE.plusDays(4)), "contained range unavailable");
    }

    @Test
    void aStaySwallowingAnotherIsBlocked() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE.plusDays(2), 2, 2);
        assertFalse(hotel.isAvailable(room, BASE, BASE.plusDays(7)), "enclosing range unavailable");
    }

    /** One night on the 10th occupies the 10th only; the 11th is someone else's. */
    @Test
    void departureIsExclusive() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, BASE, 1, 1);

        assertEquals(BASE.plusDays(1), booking.getDepartureDate(), "one-night departure");
        assertTrue(booking.overlaps(BASE, BASE.plusDays(1)), "the night itself is taken");
        assertFalse(booking.overlaps(BASE.plusDays(1), BASE.plusDays(2)), "the next night is not");
    }

    @Test
    void otherRoomsAreUnaffected() {
        HotelManager hotel = new HotelManager();
        Room booked = hotel.findRoom("101").orElseThrow();
        Room free = hotel.findRoom("102").orElseThrow();
        hotel.createReservation(booked, "Kevin", "0812", null, BASE, 3, 2);
        assertTrue(hotel.isAvailable(free, BASE, BASE.plusDays(3)), "neighbouring room still free");
    }

    @Test
    void rejectsBackwardsDateRange() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        assertThrows(RuntimeException.class,
                () -> hotel.isAvailable(room, BASE.plusDays(3), BASE), "departure before arrival");
        assertThrows(RuntimeException.class, () -> hotel.isAvailable(room, BASE, BASE), "zero-length range");
    }

    @Test
    void availableRoomsShrinkWhenBooked() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        int before = hotel.getAvailableRooms(BASE, BASE.plusDays(2)).size();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        assertEquals(before - 1, hotel.getAvailableRooms(BASE, BASE.plusDays(2)).size(),
                "one fewer room for those dates");
        assertEquals(before, hotel.getAvailableRooms(BASE.plusDays(5), BASE.plusDays(6)).size(),
                "other dates unaffected");
    }

    // ------------------------------------------------------------- room fit

    @Test
    void rejectsPartyLargerThanRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        assertThrows(RuntimeException.class,
                () -> hotel.createBooking(studio, "Kevin", "0812", null, 2, 4), "studio cannot hold 4 guests");
        assertFalse(studio.isOccupied(), "failed booking leaves room free");
    }

    @Test
    void rejectsDoubleBooking() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);
        assertThrows(RuntimeException.class,
                () -> hotel.createBooking(studio, "Rani", "0813", null, 1, 1), "second booking on the same room");
    }

    // --------------------------------------------------------------- lifecycle

    @Test
    void reservationLeavesTheRoomPhysicallyFree() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        assertFalse(room.isOccupied(), "nobody is in the room yet");
        assertEquals(32, hotel.getAvailableCount(), "counted as available");
        assertEquals(1, hotel.getReservedCount(), "counted as reserved");
    }

    @Test
    void checkingInOccupiesTheRoom() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, LocalDate.now(), 2, 2);
        hotel.checkIn(booking);

        assertTrue(room.isOccupied(), "room occupied");
        assertEquals("Checked in", booking.getStatus().getLabel(), "status");
        assertEquals(0, hotel.getReservedCount(), "reserved count drops");
    }

    @Test
    void cannotCheckInTwice() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(room, "Kevin", "0812", null, 2, 2);
        assertThrows(RuntimeException.class, () -> hotel.checkIn(booking), "second check-in");
    }

    @Test
    void cannotCheckOutTwice() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(room, "Kevin", "0812", null, 1, 1);
        hotel.checkOut(room);
        assertThrows(RuntimeException.class, () -> hotel.checkOut(room), "second check-out");
        assertFalse(room.isOccupied(), "room released");
    }

    @Test
    void cancellingFreesTheDates() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, BASE, 3, 2);

        assertFalse(hotel.isAvailable(room, BASE, BASE.plusDays(3)), "held while reserved");
        hotel.cancelReservation(booking);
        assertTrue(hotel.isAvailable(room, BASE, BASE.plusDays(3)), "released after cancelling");
        assertEquals("Cancelled", booking.getStatus().getLabel(), "status");
    }

    @Test
    void cannotCancelAfterArrival() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(room, "Kevin", "0812", null, 2, 2);
        assertThrows(RuntimeException.class, () -> hotel.cancelReservation(booking), "cancel an in-house guest");
    }

    // ----------------------------------------------------- changes to a stay

    @Test
    void extendIsBlockedByTheNextArrival() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking staying = hotel.createBooking(room, "Kevin", "0812", null, 2, 2);
        hotel.createReservation(room, "Rani", "0813", null, staying.getDepartureDate(), 2, 1);

        assertThrows(RuntimeException.class, () -> hotel.extendStay(room, 2), "extend into someone else's booking");
        assertEquals(2, staying.getNights(), "nights unchanged after refusal");
    }

    @Test
    void upgradeIsBlockedByAFutureReservation() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();

        hotel.createBooking(studio, "Kevin", "0812", null, 4, 2);
        hotel.createReservation(suite, "Rani", "0813", null, LocalDate.now().plusDays(1), 2, 2);

        assertThrows(RuntimeException.class,
                () -> hotel.upgradeBooking(studio, suite), "upgrade into a room booked later");
        assertFalse(hotel.getAvailableUpgradeOptions(studio).contains(suite), "suite not offered as an option");
    }

    /** Regression: this used to throw NullPointerException on a vacant room. */
    @Test
    void upgradeOptionsAreEmptyForFreeRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        assertEquals(0, hotel.getAvailableUpgradeOptions(studio).size(), "no options for a free room");
        assertEquals(0, hotel.getAvailableUpgradeOptions(null).size(), "no options for null");
    }

    @Test
    void upgradeOptionsExcludeTooSmallRooms() {
        HotelManager hotel = new HotelManager();
        Room deluxe = hotel.findRoom("201").orElseThrow();
        hotel.createBooking(deluxe, "Kevin", "0812", null, 2, 3);

        java.util.List<Room> options = hotel.getAvailableUpgradeOptions(deluxe);
        assertTrue(!options.isEmpty(), "upgrade options exist");
        assertTrue(options.stream().allMatch(room -> room.getCapacity() >= 3), "all options fit the party");
        assertTrue(options.stream().allMatch(room -> room.getNightlyRate() > deluxe.getNightlyRate()),
                "all options cost more");
    }

    @Test
    void cannotUpgradeIntoTheSameRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(studio, "Kevin", "0812", null, 1, 1);
        assertThrows(RuntimeException.class,
                () -> hotel.upgradeBooking(studio, studio), "upgrade into the same room");
    }

    /** The dashboard asks "how full are we that night", not "who is here now". */
    @Test
    void occupancyIsCountedPerDate() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        assertEquals(1, hotel.getBookedCount(BASE), "taken on the first night");
        assertEquals(1, hotel.getBookedCount(BASE.plusDays(1)), "taken on the second night");
        assertEquals(0, hotel.getBookedCount(BASE.plusDays(2)), "free again on departure day");
        assertEquals(0, hotel.getBookedCount(BASE.minusDays(1)), "free the day before");

        assertEquals(31, hotel.getFreeCount(BASE), "available on a booked night");
        assertEquals(32, hotel.getFreeCount(BASE.plusDays(5)), "available on a free night");
        assertEquals(100.0 / 32, hotel.getOccupancyRate(BASE), "occupancy rate on a booked night");
        assertEquals(0.0, hotel.getOccupancyRate(BASE.plusDays(5)), "occupancy rate on a free night");
    }

    /** A room held by a reservation is just as unavailable as one with a guest in it. */
    @Test
    void occupancyCountsReservationsAndGuestsAlike() {
        HotelManager hotel = new HotelManager();
        LocalDate today = LocalDate.now();
        hotel.createBooking(hotel.findRoom("101").orElseThrow(), "Kevin", "0812", null, 1, 1);
        hotel.createReservation(hotel.findRoom("102").orElseThrow(), "Rani", "0813", null, today, 1, 1);

        assertEquals(2, hotel.getBookedCount(today), "both rooms counted today");
        assertEquals(1, hotel.getOccupiedCount(), "only the arrival is physically in-house");
    }

    /** The dashboard reads these, so they must answer for a date, not just for now. */
    @Test
    void countsFollowTheChosenDate() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        assertEquals(1, hotel.getBookedCount(BASE), "booked on the arrival date");
        assertEquals(31, hotel.getFreeCount(BASE), "free on the arrival date");
        assertEquals(1, hotel.getBookedCount(BASE.plusDays(1)), "booked the night after");
        assertEquals(0, hotel.getBookedCount(BASE.plusDays(2)), "free again on departure day");
        assertEquals(0, hotel.getBookedCount(BASE.minusDays(7)), "nothing booked a week earlier");
        assertEquals(3.125, hotel.getOccupancyRate(BASE), "occupancy on arrival");
        assertEquals(1, hotel.getArrivalsOn(BASE), "arrivals on the day");
        assertEquals(0, hotel.getArrivalsOn(BASE.plusDays(1)), "no arrivals mid-stay");
    }

    /** A guest ringing ahead to add a night should not need the booking cancelled. */
    @Test
    void reservationCanBeExtendedBeforeArrival() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, BASE, 2, 2);

        hotel.extendStay(booking, 3);
        assertEquals(5, booking.getNights(), "nights after extending a reservation");
        assertEquals(BASE.plusDays(5), booking.getDepartureDate(), "departure moves out");
        assertFalse(room.isOccupied(), "still nobody in the room");
        assertEquals("Reserved", booking.getStatus().getLabel(), "still a reservation");
    }

    @Test
    void reservationCanBeMovedBeforeArrival() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();
        Booking booking = hotel.createReservation(studio, "Kevin", "0812", null, BASE, 2, 2);

        hotel.upgradeBooking(booking, suite);
        assertEquals(suite, booking.getRoom(), "booking now points at the suite");
        assertTrue(hotel.isAvailable(studio, BASE, BASE.plusDays(2)), "old room free for those dates");
        assertFalse(hotel.isAvailable(suite, BASE, BASE.plusDays(2)), "new room held for those dates");
        assertEquals(2 * 1_350_000.0, booking.getCurrentBill(),
                "whole stay repriced, none of it stayed");
    }

    /** Moving a reservation touches the ledger only; no guest is in a room to move. */
    @Test
    void movingAReservationLeavesRoomsUntouched() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();
        Booking booking = hotel.createReservation(studio, "Kevin", "0812", null, BASE, 2, 2);

        hotel.upgradeBooking(booking, suite);
        assertFalse(studio.isOccupied(), "old room not physically occupied");
        assertFalse(suite.isOccupied(), "new room not physically occupied");
        assertEquals(32, hotel.getAvailableCount(), "hotel still shows every room free today");
    }

    @Test
    void upgradeOptionsSkipRoomsBookedLater() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();
        Booking booking = hotel.createReservation(studio, "Kevin", "0812", null, BASE, 3, 2);
        hotel.createReservation(suite, "Rani", "0813", null, BASE.plusDays(1), 1, 1);

        assertFalse(hotel.getUpgradeOptionsFor(booking).contains(suite), "suite booked mid-stay is not offered");
        assertThrows(RuntimeException.class, () -> hotel.upgradeBooking(booking, suite), "and is refused if forced");
    }

    @Test
    void finishedBookingsCannotBeChanged() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(room, "Kevin", "0812", null, 1, 1);
        hotel.checkOut(room);

        assertThrows(RuntimeException.class, () -> hotel.extendStay(booking, 1), "extend after check-out");
        assertThrows(RuntimeException.class,
                () -> hotel.upgradeBooking(booking, hotel.findRoom("301").orElseThrow()), "move after check-out");
        assertEquals(0, hotel.getUpgradeOptionsFor(booking).size(), "no options for a closed booking");
    }

    // ------------------------------------------------- a guest who has not left

    /**
     * Booked one night, never checked out. The dates say the room is free from
     * yesterday onwards, but there is still a person in it.
     */
    @Test
    void aGuestPastTheirLastNightStillHoldsTheRoom() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("203").orElseThrow();
        LocalDate today = LocalDate.now();
        Booking booking = hotel.createReservation(room, "Raymond", "0812", null, today.minusDays(2), 1, 1);
        hotel.checkIn(booking);

        assertTrue(room.isOccupied(), "the guest never checked out");
        assertFalse(hotel.isAvailable(room, today, today.plusDays(3)), "not free while someone is in it");
        assertFalse(hotel.getAvailableRooms(today, today.plusDays(3)).contains(room), "and not offered");
    }

    /** The grid, the filters and the dashboard all ask this, so it has to count the person. */
    @Test
    void anOverstayingGuestStillHoldsTheRoomToday() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("203").orElseThrow();
        LocalDate today = LocalDate.now();
        Booking booking = hotel.createReservation(room, "Raymond", "0812", null, today.minusDays(2), 1, 1);
        hotel.checkIn(booking);

        assertTrue(hotel.getBookingOn(room, today).isEmpty(), "the dates alone say the room is free");
        assertTrue(hotel.getHolderOn(room, today).isPresent(), "but someone is still in it");
        assertEquals(booking, hotel.getHolderOn(room, today).orElseThrow(), "and it is their booking");
    }

    /** Only today. Whether they will still be there next week is not knowable. */
    @Test
    void anOverstayingGuestDoesNotHoldFutureDates() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("204").orElseThrow();
        LocalDate today = LocalDate.now();
        hotel.checkIn(hotel.createReservation(room, "Raymond", "0812", null, today.minusDays(2), 1, 1));

        assertTrue(hotel.getHolderOn(room, today.plusDays(1)).isEmpty(), "tomorrow is not held");
        assertTrue(hotel.getHolderOn(room, today.plusDays(7)).isEmpty(), "nor next week");
    }

    @Test
    void anOverstayingGuestIsCountedAsBookedToday() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("205").orElseThrow();
        LocalDate today = LocalDate.now();
        int freeBefore = hotel.getFreeCount(today);
        hotel.checkIn(hotel.createReservation(room, "Raymond", "0812", null, today.minusDays(2), 1, 1));

        assertEquals(freeBefore - 1, hotel.getFreeCount(today), "the dashboard stops calling it free");
    }

    @Test
    void holderOnHandlesMissingArguments() {
        HotelManager hotel = new HotelManager();
        assertTrue(hotel.getHolderOn(null, LocalDate.now()).isEmpty(), "no room");
        assertTrue(hotel.getHolderOn(hotel.findRoom("101").orElseThrow(), null).isEmpty(), "no date");
    }

    @Test
    void aRoomFreedByCheckOutIsAvailableAgain() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("204").orElseThrow();
        LocalDate today = LocalDate.now();
        Booking booking = hotel.createReservation(room, "Raymond", "0812", null, today.minusDays(2), 1, 1);
        hotel.checkIn(booking);
        hotel.checkOut(room);

        assertTrue(hotel.isAvailable(room, today, today.plusDays(3)), "free once they have gone");
    }

    @Test
    void collectionsAreNotMutableByCallers() {
        HotelManager hotel = new HotelManager();
        assertThrows(RuntimeException.class, () -> hotel.getBookings().clear(), "bookings list is unmodifiable");
        assertThrows(RuntimeException.class, () -> hotel.getRooms().clear(), "rooms list is unmodifiable");
    }

}