package hotel.test;

import hotel.model.Booking;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.StudioRoom;
import hotel.model.SuiteRoom;
import hotel.service.HotelManager;

import java.time.LocalDate;

public final class BillingTests {
    public static void main(String[] args) {
        run();
        Assert.printSummary();
        if (Assert.hasFailures()) {
            System.exit(1);
        }
    }

    static void run() {
        System.out.println("-- billing --");
        pricingIsRatePerNight();
        extendStayAddsToTheBill();
        sameDayUpgradeRepricesWholeStay();
        midStayUpgradeKeepsStayedNightsAtOldRate();
        checkOutFreezesTheBill();
        revenueSurvivesCheckOut();
        reservationsAreNotCountedAsRevenue();
        cancelledStayHasNoBill();
    }

    private static void pricingIsRatePerNight() {
        Room studio = new StudioRoom("101", 1);
        Assert.equals("studio 3 nights", 1_350_000.0, studio.calculatePrice(3));
    }

    private static void extendStayAddsToTheBill() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);
        hotel.extendStay(studio, 3);

        Assert.equals("nights after extend", 5, booking.getNights());
        Assert.equals("bill after extend", 5 * 450_000.0, booking.getCurrentBill());
    }

    /** With zero nights stayed there is nothing to preserve, so the new rate applies throughout. */
    private static void sameDayUpgradeRepricesWholeStay() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();

        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 4, 2);
        hotel.upgradeBooking(studio, suite);

        Assert.equals("same-day upgrade bill", 4 * 1_350_000.0, booking.getCurrentBill());
        Assert.equals("nights preserved", 4, booking.getNights());
    }

    /** Regression: an upgrade used to re-price nights the guest had already stayed. */
    private static void midStayUpgradeKeepsStayedNightsAtOldRate() {
        Room studio = new StudioRoom("101", 1);
        Room suite = new SuiteRoom("301", 3);
        Guest guest = new Guest("Kevin", "0812", null);

        Booking booking = new Booking(guest, studio, 5, 2, LocalDate.now().minusDays(2));
        booking.checkIn(LocalDate.now().minusDays(2));
        booking.upgradeRoom(suite);

        double expected = 2 * 450_000.0 + 3 * 1_350_000.0;
        Assert.equals("split bill after mid-stay upgrade", expected, booking.getCurrentBill());
        Assert.equals("total nights unchanged", 5, booking.getNights());
        Assert.equals("two segments recorded", 2, booking.getSegments().size());
        Assert.equals("current room is the suite", suite, booking.getRoom());
    }

    private static void checkOutFreezesTheBill() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);

        double finalBill = hotel.checkOut(studio);
        Assert.equals("final bill returned", 900_000.0, finalBill);
        Assert.equals("bill frozen on booking", 900_000.0, booking.getCurrentBill());
        Assert.equals("status", "Checked out", booking.getStatus().getLabel());
    }

    /** Regression: revenue used to drop to zero the moment a guest checked out. */
    private static void revenueSurvivesCheckOut() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room deluxe = hotel.findRoom("201").orElseThrow();

        hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);   // 900.000
        hotel.createBooking(deluxe, "Rani", "0813", null, 1, 2);    // 780.000
        hotel.checkOut(studio);

        Assert.equals("realized revenue", 900_000.0, hotel.getRealizedRevenue());
        Assert.equals("projected revenue", 780_000.0, hotel.getProjectedRevenue());
        Assert.equals("total revenue", 1_680_000.0, hotel.getTotalRevenue());
        Assert.equals("completed check-outs", 1, hotel.getCheckedOutCount());
    }

    /** A booking nobody has arrived for is neither collected nor in-house money. */
    private static void reservationsAreNotCountedAsRevenue() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, LocalDate.of(2026, 3, 10), 2, 2);

        Assert.equals("nothing in-house", 0.0, hotel.getProjectedRevenue());
        Assert.equals("nothing collected", 0.0, hotel.getRealizedRevenue());
        Assert.equals("shown as reserved value", 900_000.0, hotel.getReservedValue());
    }

    private static void cancelledStayHasNoBill() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, LocalDate.of(2026, 3, 10), 3, 2);
        hotel.cancelReservation(booking);

        Assert.equals("cancelled bill", 0.0, booking.getCurrentBill());
        Assert.equals("not counted as reserved value", 0.0, hotel.getReservedValue());
    }

    private BillingTests() {
    }
}