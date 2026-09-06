package hotel.test;

import hotel.ai.PhoneNumbers;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhoneNumbersTests {

    @Test
    void internationalFormatsAreKeptExactlyAsTyped() {
        assertEquals(Optional.of("0812-3456-789"),
                PhoneNumbers.findIn("kamar 108 2 malam 0812-3456-789"), "Indonesian, dashed");
        assertEquals(Optional.of("08123456789"),
                PhoneNumbers.findIn("kamar 108 2 malam 08123456789"), "Indonesian, unbroken");
        assertEquals(Optional.of("+81 90-1234-5678"),
                PhoneNumbers.findIn("room 302 for Mr Tanaka, +81 90-1234-5678"), "Japanese");
        assertEquals(Optional.of("+65 8123 4567"),
                PhoneNumbers.findIn("201 2 nights +65 8123 4567"), "Singaporean");
        assertEquals(Optional.of("+1 (415) 555-0192"),
                PhoneNumbers.findIn("book 201, +1 (415) 555-0192"), "American, bracketed");
        assertEquals(Optional.of("0857 1111 2222"),
                PhoneNumbers.findIn("kamar 205, 0857 1111 2222"), "spaced groups");
        assertEquals(Optional.of("+60 12-345 6789"),
                PhoneNumbers.findIn("room 303 3 nights +60 12-345 6789"), "Malaysian, mixed separators");
    }

    @Test
    void aNumberSurroundedByOtherFieldsIsStillFound() {
        assertEquals(Optional.of("+65 9123 4567"),
                PhoneNumbers.findIn("room 304 2 nights, contact +65 9123 4567, breakfast included"),
                "fields on both sides");
        assertEquals(Optional.of("0878-5555-1234"),
                PhoneNumbers.findIn("kamar 211, wa 0878-5555-1234, 2 malam"), "introduced as a WhatsApp number");
    }

    @Test
    void aRoomNumberBesideAPhoneIsNotAbsorbed() {
        assertEquals(Optional.of("08123456789"),
                PhoneNumbers.findIn("kamar 204 08123456789"), "room number left alone");
        assertEquals(Optional.of("08123456789"),
                PhoneNumbers.findIn("201 2 2 08123456789"), "room, guests and nights left alone");
    }

    @Test
    void aCountAfterAPhoneIsNotAbsorbed() {
        assertEquals(Optional.of("0813 2222 3333"),
                PhoneNumbers.findIn("booking 107 a/n Rina 0813 2222 3333 2 malam"), "nights left alone");
        assertEquals(Optional.of("08123456789"),
                PhoneNumbers.findIn("kamar 204 08123456789 2 malam 2 orang"), "counts on both sides");
    }

    @Test
    void shortNumbersAreNeverPhones() {
        assertEquals(Optional.empty(),
                PhoneNumbers.findIn("Make a booking at room 201 deluxe for 2 guests for 2 nights"), "no phone");
        assertEquals(Optional.empty(), PhoneNumbers.findIn("201 deluxe, 2 tamu, 2 malam"), "fragments only");
        assertEquals(Optional.empty(), PhoneNumbers.findIn("kamar 112 3 malam"), "room and nights only");
    }

    @Test
    void twoNumbersYieldNothing() {
        assertEquals(Optional.empty(),
                PhoneNumbers.findIn("kamar 203, hp 0812-3456-789 atau 0857-1111-2222"), "two mobiles");
    }

    @Test
    void thePhoneIsCutOutOfWhatTheModelReads() {
        assertEquals("Hana Malmo 6 nights studio breakfast",
                PhoneNumbers.withoutPhone("Hana Malmo 6 nights studio +6143567382 breakfast"),
                "a country code must not read as a guest count");
        assertEquals("kamar 201 2 malam",
                PhoneNumbers.withoutPhone("kamar 201 2 malam 0812-3456-789"), "trailing number removed");
        assertEquals("kamar 201 2 malam",
                PhoneNumbers.withoutPhone("kamar 201 2 malam"), "nothing to remove");
        assertEquals("booking 107 a/n Rina 2 malam",
                PhoneNumbers.withoutPhone("booking 107 a/n Rina 0813 2222 3333 2 malam"),
                "the nights count survives");
    }

    @Test
    void nothingToSearchYieldsNothing() {
        assertEquals(Optional.empty(), PhoneNumbers.findIn(null), "null");
        assertEquals(Optional.empty(), PhoneNumbers.findIn("   "), "blank");
        assertEquals(Optional.empty(), PhoneNumbers.findIn("kamar deluxe untuk dua malam"), "no digits at all");
    }
}
