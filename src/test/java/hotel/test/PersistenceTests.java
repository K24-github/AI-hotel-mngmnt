package hotel.test;

import hotel.model.Booking;
import hotel.model.BookingStatus;
import hotel.model.Room;
import hotel.service.HotelManager;
import hotel.store.BookingStore;
import hotel.store.JsonBookingStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTests {

    @TempDir
    Path tempDir;

    // ------------------------------------------------------------- round trips

    @Test
    void aReservationSurvivesARestart() {
        Path file = ledgerFile();
        HotelManager before = hotelUsing(file);
        Booking saved = before.createReservation(before.findRoom("201").orElseThrow(),
                "Kevin", "0812", "late arrival", LocalDate.of(2026, 3, 10), 3, 2);
        before.persist();

        HotelManager after = hotelUsing(file);
        assertEquals(1, after.getBookings().size(), "bookings after reload");

        Booking restored = after.getBookings().get(0);
        assertEquals(saved.getBookingId(), restored.getBookingId(), "booking id");
        assertEquals("Kevin", restored.getGuest().getFullName(), "guest name");
        assertEquals("0812", restored.getGuest().getPhoneNumber(), "phone");
        assertEquals("late arrival", restored.getGuest().getNotes(), "notes");
        assertEquals("201", restored.getRoom().getRoomNumber(), "room");
        assertEquals(LocalDate.of(2026, 3, 10), restored.getArrivalDate(), "arrival");
        assertEquals(3, restored.getNights(), "nights");
        assertEquals(2, restored.getGuestCount(), "guest count");
        assertEquals(BookingStatus.RESERVED, restored.getStatus(), "status");
        assertEquals(3 * 780_000.0, restored.getCurrentBill(), "quoted total");
    }

    @Test
    void anInHouseGuestStillOccupiesTheirRoomAfterAReload() {
        Path file = ledgerFile();
        HotelManager before = hotelUsing(file);
        before.createBooking(before.findRoom("101").orElseThrow(), "Rani", "0813", null, 2, 1);
        before.persist();

        HotelManager after = hotelUsing(file);
        Room room = after.findRoom("101").orElseThrow();
        assertTrue(room.isOccupied(), "room still occupied");
        assertEquals("Rani", room.getActiveBooking().getGuest().getFullName(), "guest in the room");
        assertEquals(BookingStatus.CHECKED_IN, room.getActiveBooking().getStatus(), "status");
        assertEquals(1, after.getOccupiedCount(), "occupied rooms");
    }

    @Test
    void aSplitStayKeepsItsSegmentsAndItsBill() {
        Path file = ledgerFile();
        HotelManager before = hotelUsing(file);
        Room studio = before.findRoom("101").orElseThrow();
        Room suite = before.findRoom("301").orElseThrow();

        Booking booking = before.createReservation(studio, "Kevin", "0812", null,
                LocalDate.now().minusDays(2), 5, 2);
        booking.checkIn(LocalDate.now().minusDays(2));
        studio.assignBooking(booking);
        before.upgradeBooking(booking, suite);
        before.persist();

        double expected = 2 * 450_000.0 + 3 * 1_350_000.0;
        assertEquals(expected, booking.getCurrentBill(), "bill before saving");

        HotelManager after = hotelUsing(file);
        Booking restored = after.getBookings().get(0);
        assertEquals(2, restored.getSegments().size(), "both segments kept");
        assertEquals(5, restored.getNights(), "total nights");
        assertEquals("301", restored.getRoom().getRoomNumber(), "current room");
        assertEquals(expected, restored.getCurrentBill(), "split bill after reload");
        assertTrue(after.findRoom("301").orElseThrow().isOccupied(), "suite occupied");
        assertFalse(after.findRoom("101").orElseThrow().isOccupied(), "studio released");
    }

    @Test
    void aCancelledReservationStaysCancelledAndReleasesItsDates() {
        Path file = ledgerFile();
        HotelManager before = hotelUsing(file);
        Booking booking = before.createReservation(before.findRoom("101").orElseThrow(),
                "Kevin", "0812", null, LocalDate.of(2026, 3, 10), 3, 2);
        before.cancelReservation(booking);
        before.persist();

        HotelManager after = hotelUsing(file);
        Booking restored = after.getBookings().get(0);
        assertEquals(BookingStatus.CANCELLED, restored.getStatus(), "status");
        assertEquals(0.0, restored.getCurrentBill(), "no bill");
        assertEquals(0.0, after.getReservedValue(), "not counted as reserved value");
        assertTrue(after.isAvailable(after.findRoom("101").orElseThrow(),
                LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 13)), "dates released");
    }

    // ------------------------------------------------------------------ money

    @Test
    void aFrozenBillIsNotRecomputedOnLoad() throws IOException {
        Path file = ledgerFile();
        Files.writeString(file, ledgerWith("BK1042", "CHECKED_OUT", segment("101", 2), "777777.0"));

        HotelManager hotel = hotelUsing(file);
        Booking restored = hotel.getBookings().get(0);
        assertEquals(777_777.0, restored.getCurrentBill(), "charged amount kept as stored");
        assertEquals(777_777.0, hotel.getRealizedRevenue(), "realized revenue uses it");
    }

    /** Nobody has paid for an open stay yet, so it is re-priced from its rooms. */
    @Test
    void anOpenBillIsRecomputedFromItsSegments() throws IOException {
        Path file = ledgerFile();
        Files.writeString(file, ledgerWith("BK1043", "RESERVED", segment("101", 2), "1.0"));

        HotelManager hotel = hotelUsing(file);
        Booking restored = hotel.getBookings().get(0);
        assertEquals(900_000.0, restored.getCurrentBill(), "re-priced, stored amount ignored");
    }

    // -------------------------------------------------------------------- ids

    @Test
    void idsDoNotCollideAfterARestart() {
        Path file = ledgerFile();
        HotelManager before = hotelUsing(file);
        Booking first = before.createReservation(before.findRoom("101").orElseThrow(),
                "Kevin", "0812", null, LocalDate.of(2026, 3, 10), 2, 2);
        Booking second = before.createReservation(before.findRoom("201").orElseThrow(),
                "Rani", "0813", null, LocalDate.of(2026, 3, 10), 2, 2);
        before.persist();

        HotelManager after = hotelUsing(file);
        Booking fresh = after.createReservation(after.findRoom("301").orElseThrow(),
                "Sari", "0814", null, LocalDate.of(2026, 3, 10), 1, 2);

        List<String> ids = after.getBookings().stream().map(Booking::getBookingId).toList();
        assertEquals(3, ids.size(), "all three in the ledger");
        assertEquals(3, Set.copyOf(ids).size(), "every id unique");
        assertNotEquals(first.getBookingId(), fresh.getBookingId(), "not reusing the first id");
        assertNotEquals(second.getBookingId(), fresh.getBookingId(), "not reusing the second id");
    }

    @Test
    void theCounterMovesPastAnIdReadFromItsPrefix() throws IOException {
        Path file = ledgerFile();
        Files.writeString(file, ledgerWith("BK987654", "RESERVED", segment("101", 2), "null"));

        HotelManager hotel = hotelUsing(file);
        Booking fresh = hotel.createReservation(hotel.findRoom("201").orElseThrow(),
                "Sari", "0814", null, LocalDate.of(2026, 3, 10), 1, 2);
        assertEquals("BK987655", fresh.getBookingId(), "counter seeded from BK987654");
    }

    // -------------------------------------------------------- unreadable files

    @Test
    void aMissingFileOpensAnEmptyLedger() {
        HotelManager hotel = assertDoesNotThrow(() -> hotelUsing(tempDir.resolve("not-there.json")));
        assertEquals(0, hotel.getBookings().size(), "no bookings");
        assertEquals(32, hotel.getAvailableCount(), "whole hotel free");
    }

    @Test
    void anEmptyFileOpensAnEmptyLedger() throws IOException {
        Path file = ledgerFile();
        Files.writeString(file, "");

        HotelManager hotel = assertDoesNotThrow(() -> hotelUsing(file));
        assertEquals(0, hotel.getBookings().size(), "no bookings");
    }

    @Test
    void aCorruptFileOpensAnEmptyLedger() throws IOException {
        Path file = ledgerFile();
        Files.writeString(file, "{ \"version\" : 1, \"bookings\" : [ { this is not json");

        HotelManager hotel = assertDoesNotThrow(() -> hotelUsing(file));
        assertEquals(0, hotel.getBookings().size(), "no bookings");
    }

    @Test
    void aSegmentInARoomTheHotelNoLongerHasIsDropped() throws IOException {
        Path file = ledgerFile();
        Files.writeString(file, ledgerWith("BK1050", "RESERVED",
                segment("999", 2) + ", " + segment("301", 3), "null"));

        HotelManager hotel = hotelUsing(file);
        Booking restored = hotel.getBookings().get(0);
        assertEquals(1, restored.getSegments().size(), "only the room that still exists");
        assertEquals(3, restored.getNights(), "nights from the surviving segment");
        assertEquals("301", restored.getRoom().getRoomNumber(), "room");
    }

    @Test
    void aBookingLeftWithNoRoomsIsDropped() throws IOException {
        Path file = ledgerFile();
        Files.writeString(file, ledgerWith("BK1051", "RESERVED", segment("999", 2), "null"));

        HotelManager hotel = hotelUsing(file);
        assertEquals(0, hotel.getBookings().size(), "unrestorable booking left out");
    }

    // ----------------------------------------------------------------- writing

    @Test
    void savingLeavesNoTemporaryFileBehind() throws IOException {
        Path file = ledgerFile();
        HotelManager hotel = hotelUsing(file);
        hotel.createBooking(hotel.findRoom("101").orElseThrow(), "Kevin", "0812", null, 2, 1);
        hotel.persist();

        assertTrue(Files.exists(file), "ledger written");
        try (Stream<Path> entries = Files.list(tempDir)) {
            List<String> leftovers = entries
                    .map(entry -> entry.getFileName().toString())
                    .filter(name -> name.endsWith(".tmp"))
                    .toList();
            assertEquals(List.of(), leftovers, "no temporary files left over");
        }
    }

    @Test
    void theLedgerIsPrettyPrintedAndCarriesAVersion() throws IOException {
        Path file = ledgerFile();
        HotelManager hotel = hotelUsing(file);
        hotel.createBooking(hotel.findRoom("101").orElseThrow(), "Kevin", "0812", null, 2, 1);
        hotel.persist();

        String json = Files.readString(file);
        assertTrue(json.replace(" ", "").contains("\"version\":1"), "version header: " + json);
        assertTrue(json.lines().count() > 5, "pretty printed across lines: " + json);
    }

    /** A booking that succeeded is not undone because the disk would not take it. */
    @Test
    void aFailedSaveLeavesTheBookingIntactInMemory() {
        HotelManager hotel = new HotelManager();
        hotel.useStore(new BookingStore() {
            @Override
            public void save(List<Booking> bookings) {
                throw new IllegalStateException("disk is full");
            }

            @Override
            public List<Booking> load() {
                return List.of();
            }
        });

        Booking booking = hotel.createBooking(hotel.findRoom("101").orElseThrow(),
                "Kevin", "0812", null, 2, 1);

        assertThrows(IllegalStateException.class, hotel::persist, "the failure is reported");
        assertEquals(List.of(booking), hotel.getBookings(), "booking still in the ledger");
        assertTrue(hotel.findRoom("101").orElseThrow().isOccupied(), "guest still in their room");
        assertEquals(900_000.0, booking.getCurrentBill(), "bill intact");
    }

    // ----------------------------------------------------------------- helpers

    private Path ledgerFile() {
        return tempDir.resolve("bookings.json");
    }

    private static HotelManager hotelUsing(Path file) {
        HotelManager hotel = new HotelManager();
        hotel.useStore(new JsonBookingStore(file, roomNumber -> hotel.findRoom(roomNumber).orElse(null)));
        return hotel;
    }

    private static String ledgerWith(String id, String status, String segments, String finalBill) {
        return """
                {
                  "version" : 1,
                  "bookings" : [ {
                    "id" : "%s",
                    "guest" : { "fullName" : "Kevin", "phone" : "0812", "notes" : "-" },
                    "guestCount" : 2,
                    "arrivalDate" : "2026-03-10",
                    "segments" : [ %s ],
                    "status" : "%s",
                    "checkedInOn" : null,
                    "checkedOutOn" : null,
                    "finalBill" : %s
                  } ]
                }
                """.formatted(id, segments, status, finalBill);
    }

    private static String segment(String roomNumber, int nights) {
        return "{ \"roomNumber\" : \"" + roomNumber + "\", \"nights\" : " + nights + " }";
    }
}
