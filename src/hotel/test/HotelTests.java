package hotel.test;

import hotel.model.Booking;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.StudioRoom;
import hotel.model.SuiteRoom;
import hotel.service.HotelManager;

import java.time.LocalDate;
import java.util.List;

public final class HotelTests {
    public static void main(String[] args) {
        run();
        if (Assert.hasFailures()) {
            System.exit(1);
        }
    }
    static void run() 
    {
        pricingIsRatePerNight();
        rejectsPartyLargerThanRoom();
        rejectsDoubleBooking();
        extendStayAddsNights();
        sameDayUpgradeRepricesWholeStay();
        midStayUpgradeKeepsStayedNightsAtOldRate();
        checkOutFreezesTheBill();
        revenueSurvivesCheckOut();
        upgradeOptionsAreEmptyForFreeRoom();
        upgradeOptionsExcludeTooSmallRooms();
        bookingListIsNotMutableByCallers();
        cannotCheckOutTwice();
        cannotUpgradeIntoTheSameRoom();

    }
    // ------------------------------------------------------------------ tests

    private static void pricingIsRatePerNight() {
        Room studio = new StudioRoom("101", 1);
        check("studio 3 nights", 1_350_000, studio.calculatePrice(3));
    }

    private static void rejectsPartyLargerThanRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        expectFailure("studio cannot hold 4 guests",
                () -> hotel.createBooking(studio, "Kevin", "0812", null, 2, 4));
        check("failed booking leaves room free", true, !studio.isOccupied());
    }

    private static void rejectsDoubleBooking() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);
        expectFailure("second booking on the same room",
                () -> hotel.createBooking(studio, "Rani", "0813", null, 1, 1));
    }

    private static void extendStayAddsNights() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);
        hotel.extendStay(studio, 3);

        check("nights after extend", 5, booking.getNights());
        check("bill after extend", 5 * 450_000.0, booking.getCurrentBill());
    }

    /** With zero nights stayed there is nothing to preserve, so the new rate applies throughout. */
    private static void sameDayUpgradeRepricesWholeStay() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();

        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 4, 2);
        hotel.upgradeBooking(studio, suite);

        check("old room released", false, studio.isOccupied());
        check("new room taken", true, suite.isOccupied());
        check("same-day upgrade bill", 4 * 1_350_000.0, booking.getCurrentBill());
        check("nights preserved", 4, booking.getNights());
    }

    /** Regression: an upgrade used to re-price nights the guest had already stayed. */
    private static void midStayUpgradeKeepsStayedNightsAtOldRate() {
        Room studio = new StudioRoom("101", 1);
        Room suite = new SuiteRoom("301", 3);
        Guest guest = new Guest("Kevin", "0812", null);

        Booking booking = new Booking(guest, studio, 5, 2, LocalDate.now().minusDays(2));
        booking.upgradeRoom(suite);

        double expected = 2 * 450_000.0 + 3 * 1_350_000.0;
        check("split bill after mid-stay upgrade", expected, booking.getCurrentBill());
        check("total nights unchanged", 5, booking.getNights());
        check("two segments recorded", 2, booking.getSegments().size());
        check("current room is the suite", suite, booking.getRoom());
    }

    private static void checkOutFreezesTheBill() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);

        double finalBill = hotel.checkOut(studio);
        check("final bill returned", 900_000.0, finalBill);
        check("bill frozen on booking", 900_000.0, booking.getCurrentBill());
        check("room released", false, studio.isOccupied());
        check("status", "Checked out", booking.getStatus().getLabel());
    }

    /** Regression: revenue used to drop to zero the moment a guest checked out. */
    private static void revenueSurvivesCheckOut() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room deluxe = hotel.findRoom("201").orElseThrow();

        hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);   //   900.000
        hotel.createBooking(deluxe, "Rani", "0813", null, 1, 2);    //   780.000
        hotel.checkOut(studio);

        check("realized revenue", 900_000.0, hotel.getRealizedRevenue());
        check("projected revenue", 780_000.0, hotel.getProjectedRevenue());
        check("total revenue", 1_680_000.0, hotel.getTotalRevenue());
        check("completed check-outs", 1, hotel.getCheckedOutCount());
    }

    /** Regression: this used to throw NullPointerException on a vacant room. */
    private static void upgradeOptionsAreEmptyForFreeRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        check("no options for a free room", 0, hotel.getAvailableUpgradeOptions(studio).size());
        check("no options for null", 0, hotel.getAvailableUpgradeOptions(null).size());
    }

    private static void upgradeOptionsExcludeTooSmallRooms() {
        HotelManager hotel = new HotelManager();
        Room deluxe = hotel.findRoom("201").orElseThrow();
        hotel.createBooking(deluxe, "Kevin", "0812", null, 2, 3);

        List<Room> options = hotel.getAvailableUpgradeOptions(deluxe);
        boolean allFit = options.stream().allMatch(room -> room.getCapacity() >= 3);
        boolean allPricier = options.stream().allMatch(room -> room.getNightlyRate() > deluxe.getNightlyRate());

        check("upgrade options exist", true, !options.isEmpty());
        check("all options fit the party", true, allFit);
        check("all options cost more", true, allPricier);
    }

    private static void bookingListIsNotMutableByCallers() {
        HotelManager hotel = new HotelManager();
        expectFailure("bookings list is unmodifiable", () -> hotel.getBookings().clear());
        expectFailure("rooms list is unmodifiable", () -> hotel.getRooms().clear());
    }

    private static void cannotCheckOutTwice() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(studio, "Kevin", "0812", null, 1, 1);
        hotel.checkOut(studio);
        expectFailure("second check-out", () -> hotel.checkOut(studio));
    }

    private static void cannotUpgradeIntoTheSameRoom() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        hotel.createBooking(studio, "Kevin", "0812", null, 1, 1);
        expectFailure("upgrade into the same room", () -> hotel.upgradeBooking(studio, studio));
    }

    // ---------------------------------------------------------------- helpers

    private static void check(String name, Object expected, Object actual) {
       Assert.equals(name, expected, actual);
    }

    /** Passes when the action rejects the operation with an unchecked rule violation. */
    private static void expectFailure(String name, Runnable action) {
        Assert.throwsError(name, action);
    }
    private HotelTests() {
    }
}
