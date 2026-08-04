package hotel.test;

import hotel.model.Booking;
import hotel.model.Guest;
import hotel.model.Room;
import hotel.model.StudioRoom;
import hotel.model.SuiteRoom;
import hotel.service.HotelManager;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingTests {

    @Test
    void pricingIsRatePerNight() {
        Room studio = new StudioRoom("101", 1);
        assertEquals(1_350_000.0, studio.calculatePrice(3), "studio 3 nights");
    }

    @Test
    void extendStayAddsToTheBill() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);
        hotel.extendStay(studio, 3);

        assertEquals(5, booking.getNights(), "nights after extend");
        assertEquals(5 * 450_000.0, booking.getCurrentBill(), "bill after extend");
    }

    /** With zero nights stayed there is nothing to preserve, so the new rate applies throughout. */
    @Test
    void sameDayUpgradeRepricesWholeStay() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room suite = hotel.findRoom("301").orElseThrow();

        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 4, 2);
        hotel.upgradeBooking(studio, suite);

        assertEquals(4 * 1_350_000.0, booking.getCurrentBill(), "same-day upgrade bill");
        assertEquals(4, booking.getNights(), "nights preserved");
    }

    /** Regression: an upgrade used to re-price nights the guest had already stayed. */
    @Test
    void midStayUpgradeKeepsStayedNightsAtOldRate() {
        Room studio = new StudioRoom("101", 1);
        Room suite = new SuiteRoom("301", 3);
        Guest guest = new Guest("Kevin", "0812", null);

        Booking booking = new Booking(guest, studio, 5, 2, LocalDate.now().minusDays(2));
        booking.checkIn(LocalDate.now().minusDays(2));
        booking.upgradeRoom(suite);

        double expected = 2 * 450_000.0 + 3 * 1_350_000.0;
        assertEquals(expected, booking.getCurrentBill(), "split bill after mid-stay upgrade");
        assertEquals(5, booking.getNights(), "total nights unchanged");
        assertEquals(2, booking.getSegments().size(), "two segments recorded");
        assertEquals(suite, booking.getRoom(), "current room is the suite");
    }

    @Test
    void checkOutFreezesTheBill() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);

        double finalBill = hotel.checkOut(studio);
        assertEquals(900_000.0, finalBill, "final bill returned");
        assertEquals(900_000.0, booking.getCurrentBill(), "bill frozen on booking");
        assertEquals("Checked out", booking.getStatus().getLabel(), "status");
    }

    /** Regression: revenue used to drop to zero the moment a guest checked out. */
    @Test
    void revenueSurvivesCheckOut() {
        HotelManager hotel = new HotelManager();
        Room studio = hotel.findRoom("101").orElseThrow();
        Room deluxe = hotel.findRoom("201").orElseThrow();

        hotel.createBooking(studio, "Kevin", "0812", null, 2, 2);   // 900.000
        hotel.createBooking(deluxe, "Rani", "0813", null, 1, 2);    // 780.000
        hotel.checkOut(studio);

        assertEquals(900_000.0, hotel.getRealizedRevenue(), "realized revenue");
        assertEquals(780_000.0, hotel.getProjectedRevenue(), "projected revenue");
        assertEquals(1_680_000.0, hotel.getTotalRevenue(), "total revenue");
        assertEquals(1, hotel.getCheckedOutCount(), "completed check-outs");
    }

    /** A booking nobody has arrived for is neither collected nor in-house money. */
    @Test
    void reservationsAreNotCountedAsRevenue() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        hotel.createReservation(room, "Kevin", "0812", null, LocalDate.of(2026, 3, 10), 2, 2);

        assertEquals(0.0, hotel.getProjectedRevenue(), "nothing in-house");
        assertEquals(0.0, hotel.getRealizedRevenue(), "nothing collected");
        assertEquals(900_000.0, hotel.getReservedValue(), "shown as reserved value");
    }

    @Test
    void cancelledStayHasNoBill() {
        HotelManager hotel = new HotelManager();
        Room room = hotel.findRoom("101").orElseThrow();
        Booking booking = hotel.createReservation(room, "Kevin", "0812", null, LocalDate.of(2026, 3, 10), 3, 2);
        hotel.cancelReservation(booking);

        assertEquals(0.0, booking.getCurrentBill(), "cancelled bill");
        assertEquals(0.0, hotel.getReservedValue(), "not counted as reserved value");
    }

}