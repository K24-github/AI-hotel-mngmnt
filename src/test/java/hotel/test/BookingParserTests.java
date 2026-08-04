package hotel.test;

import hotel.ai.BookingDraft;
import hotel.ai.BookingParser;
import hotel.ai.ScriptedBookingParser;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingParserTests {

    // ------------------------------------------------------------ the off switch

    /** Switching the AI off is a parser, not a flag, so the caller keeps one code path. */
    @Test
    void theOffParserNeverProposesAnything() {
        BookingParser parser = BookingParser.off();

        assertTrue(parser.parse("booking kamar 201 untuk 2 orang 2 malam").isEmpty(), "an Indonesian sentence");
        assertTrue(parser.parse("room 201 for 2 nights").isEmpty(), "an English sentence");
        assertTrue(parser.parse("").isEmpty(), "empty input");
        assertTrue(parser.parse(null).isEmpty(), "null input");
    }

    // -------------------------------------------------------- the scripted stand-in

    @Test
    void scriptedParserReturnsWhatItWasTold() {
        BookingDraft expected = BookingDraft.builder()
                .roomNumber("201").tier("Deluxe").guests(2).nights(2).breakfast(true).build();
        BookingParser parser = new ScriptedBookingParser()
                .answer("Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included", expected);

        Optional<BookingDraft> parsed =
                parser.parse("Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included");

        assertTrue(parsed.isPresent(), "scripted sentence recognised");
        assertEquals(expected, parsed.get(), "the scripted draft comes back");
    }

    /** An unrecognised sentence behaves exactly like the AI being off. */
    @Test
    void unscriptedTextParsesToNothing() {
        BookingParser parser = new ScriptedBookingParser()
                .answer("room 201 for 2 nights", BookingDraft.builder().roomNumber("201").nights(2).build());

        assertTrue(parser.parse("something nobody scripted").isEmpty(), "unknown sentence");
        assertTrue(parser.parse("").isEmpty(), "empty input");
        assertTrue(parser.parse(null).isEmpty(), "null input");
    }

    /** Receptionists do not type consistent casing or spacing. */
    @Test
    void scriptedLookupIgnoresCaseAndSurroundingSpace() {
        BookingDraft draft = BookingDraft.builder().roomNumber("201").guests(2).nights(2).build();
        BookingParser parser = new ScriptedBookingParser()
                .answer("booking kamar 201 untuk 2 orang 2 malam", draft);

        assertEquals(Optional.of(draft), parser.parse("BOOKING KAMAR 201 UNTUK 2 ORANG 2 MALAM"), "upper case");
        assertEquals(Optional.of(draft), parser.parse("  booking kamar 201 untuk 2 orang 2 malam  "), "padded");
    }

    /** Several phrasings of one booking must be able to land on one draft. */
    @Test
    void differentPhrasingsCanShareADraft() {
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("201").guests(2).nights(2).breakfast(true).build();
        BookingParser parser = new ScriptedBookingParser()
                .answer("2 nights 2 pax room 201 include breakfast", draft)
                .answer("201 deluxe, 2 tamu, 2 malam, pakai sarapan", draft);

        assertEquals(Optional.of(draft), parser.parse("2 nights 2 pax room 201 include breakfast"), "English");
        assertEquals(Optional.of(draft), parser.parse("201 deluxe, 2 tamu, 2 malam, pakai sarapan"), "Indonesian");
    }
}
