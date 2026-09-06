package hotel.test;

import hotel.ai.BookingDraft;
import hotel.ai.BookingProposal;
import hotel.ai.BookingResolver;
import hotel.model.Room;
import hotel.service.HotelManager;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingResolverTests {

    private static Optional<BookingProposal> resolve(HotelManager hotel, BookingDraft draft) {
        return new BookingResolver(hotel).resolve(draft, "");
    }

    private static Optional<BookingProposal> resolve(HotelManager hotel, BookingDraft draft, String typed) {
        return new BookingResolver(hotel).resolve(draft, typed);
    }

    // ------------------------------------------------------- the happy sentence

    /** "Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included" */
    @Test
    void aFullSentenceResolvesToEveryField() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("201").tier("Deluxe").guests(2).nights(2).breakfast(true).build();

        BookingProposal proposal = resolve(hotel, draft).orElseThrow();

        assertEquals(hotel.findRoom("201").orElseThrow(), proposal.room(), "room 201 found");
        assertEquals(2, proposal.guests(), "guests carried");
        assertEquals(2, proposal.nights(), "nights carried");
        assertTrue(proposal.breakfast(), "breakfast requested");
    }

    /** Receptionists do not capitalise tiers, and the model will echo whatever they typed. */
    @Test
    void tierMatchingIgnoresCase() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal =
                resolve(hotel, BookingDraft.builder().tier("dELuXe").nights(2).build()).orElseThrow();

        assertEquals("Deluxe", proposal.room().getTierName(), "a deluxe was picked");
    }

    @Test
    void aMarkedNoteIsCopiedFromTheText() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("101").build(),
                "kamar 101, note: late arrival").orElseThrow();

        assertEquals("late arrival", proposal.notes(), "note taken from after the marker");
        assertFalse(proposal.breakfast(), "breakfast not mentioned");
    }

    @Test
    void anUnmarkedSentenceHasNoNote() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("101").build(),
                "kamar 101 2 malam minta lantai atas").orElseThrow();

        assertNull(proposal.notes(), "nothing marked, so nothing prefilled");
    }

    @Test
    void aNoteDoesNotDonateItsDigits() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("101").build(),
                "kamar 101 2 malam, catatan: hubungi istri di 0812-3456-789").orElseThrow();

        assertNull(proposal.phone(), "the number in the note is not the guest's");
        assertEquals("hubungi istri di 0812-3456-789", proposal.notes(), "note kept whole");
    }

    // -------------------------------------------------------- inventions go nowhere

    /** Constraint: if the model invents room 999 the whole proposal dies. */
    @Test
    void anInventedRoomNumberRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().roomNumber("999").guests(2).nights(2).build();

        assertTrue(resolve(hotel, draft).isEmpty(), "room 999 does not exist");
    }

    @Test
    void aTierWrittenAsTheWordNullDoesNotSinkTheDraft() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("201").tier("null").guestName("null").nights(2).build(),
                "2 nights 2 pax room 201 include breakfast").orElseThrow();

        assertEquals(hotel.findRoom("201").orElseThrow(), proposal.room(), "the room still resolves");
        assertNull(proposal.guestName(), "no name was really given");
    }

    @Test
    void anInventedTierRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        assertTrue(resolve(hotel, BookingDraft.builder().tier("Penthouse").build()).isEmpty(),
                "this hotel has no penthouse");
    }

    @Test
    void aRoomNumberOutranksAnyTierTheModelInvented() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().roomNumber("101").tier("Suite").nights(2).build();

        assertEquals("Studio", hotel.findRoom("101").orElseThrow().getTierName(), "101 is a studio");

        BookingProposal proposal = resolve(hotel, draft).orElseThrow();
        assertEquals(hotel.findRoom("101").orElseThrow(), proposal.room(), "the room number still wins");
    }

    // ------------------------------------------------------------------ capacity

    @Test
    void aPartyTooBigForTheNamedRoomRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().roomNumber("101").guests(4).build();

        assertEquals(2, hotel.findRoom("101").orElseThrow().getCapacity(), "studio holds two");
        assertTrue(resolve(hotel, draft).isEmpty(), "four guests in a studio");
    }

    /** No room named, but no studio in the hotel could hold them either. */
    @Test
    void aPartyTooBigForTheWholeTierRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        assertTrue(resolve(hotel, BookingDraft.builder().tier("Studio").guests(4).build()).isEmpty(),
                "no studio holds four");
        assertTrue(resolve(hotel, BookingDraft.builder().tier("Suite").guests(4).build()).isPresent(),
                "a suite does");
    }

    @Test
    void aPartyLargerThanAnyRoomRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        assertTrue(resolve(hotel, BookingDraft.builder().guests(9).nights(2).build()).isEmpty(),
                "nine guests fit nowhere");
    }

    // -------------------------------------------------- a tier with no room number

    @Test
    void aTierWithoutARoomNumberPicksAFreeRoomOfThatTier() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().tier("Deluxe").guests(2).nights(2).build();

        BookingProposal proposal = resolve(hotel, draft).orElseThrow();

        assertEquals("Deluxe", proposal.room().getTierName(), "and it is the tier that was asked for");
        assertEquals("201", proposal.room().getRoomNumber(), "the lowest free one");
        assertEquals(2, proposal.guests(), "guests still prefilled");
        assertEquals(2, proposal.nights(), "nights still prefilled");
    }

    @Test
    void aPickedRoomIsBigEnoughForTheParty() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal =
                resolve(hotel, BookingDraft.builder().tier("Suite").guests(5).nights(2).build()).orElseThrow();

        assertTrue(proposal.room().getCapacity() >= 5, "the picked suite holds the party");
    }

    @Test
    void aTierWithEverythingBookedIsDenied() {
        HotelManager hotel = new HotelManager();
        for (Room suite : hotel.getRoomsByTier("Suite")) {
            hotel.createBooking(suite, "Kevin", "0812", null, 2, 2);
        }

        assertTrue(resolve(hotel, BookingDraft.builder().tier("Suite").nights(2).build()).isEmpty(),
                "no suite free, so nothing to prefill");
    }

    @Test
    void aSentenceNamingNeitherRoomNorTierIsDenied() {
        HotelManager hotel = new HotelManager();

        assertTrue(resolve(hotel, BookingDraft.builder().guests(2).nights(3).breakfast(true).build()).isEmpty(),
                "counts alone do not say what to book");
        assertTrue(resolve(hotel, BookingDraft.builder().nights(2).build()).isEmpty(), "nights alone");
    }

    // ------------------------------------------------------------ nonsense numbers

    /** A bad number is dropped on its own; the rest of the sentence was still good. */
    @Test
    void impossibleCountsAreDroppedNotFatal() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal =
                resolve(hotel, BookingDraft.builder().roomNumber("201").nights(0).guests(-1).build()).orElseThrow();

        assertNull(proposal.nights(), "zero nights dropped");
        assertNull(proposal.guests(), "negative guests dropped");
        assertEquals(hotel.findRoom("201").orElseThrow(), proposal.room(), "the room was still valid");
    }

    // ---------------------------------------------------------------- empty input

    @Test
    void nothingToResolveYieldsNoProposal() {
        HotelManager hotel = new HotelManager();

        assertTrue(resolve(hotel, null).isEmpty(), "null draft");
        assertTrue(resolve(hotel, BookingDraft.empty()).isEmpty(), "empty draft");
    }

    // ------------------------------------------------- occupancy is not our business

    /**
     * A taken room still resolves. The existing check-in guard refuses it exactly as it
     * does for a manual entry, rather than the resolver silently swallowing the sentence.
     */
    @Test
    void anOccupiedRoomStillResolves() {
        HotelManager hotel = new HotelManager();
        hotel.createBooking(hotel.findRoom("201").orElseThrow(), "Kevin", "0812", null, 2, 2);

        Optional<BookingProposal> proposal =
                resolve(hotel, BookingDraft.builder().roomNumber("201").nights(2).build());

        assertTrue(proposal.isPresent(), "resolved despite the guest");
        assertTrue(proposal.orElseThrow().room().isOccupied(), "and the room really is taken");
    }

    // -------------------------------------------------------------- phone numbers

    /** The phone is copied out of what the clerk typed, never out of the model's answer. */
    @Test
    void thePhoneIsTakenFromTheTypedText() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("201").nights(2).build(),
                "booking kamar 201 untuk 2 malam 0812-3456-789").orElseThrow();

        assertEquals("0812-3456-789", proposal.phone(), "kept exactly as typed");
    }

    /** Guests are not all Indonesian, so nothing may assume a national format. */
    @Test
    void foreignNumbersSurviveUntouched() {
        HotelManager hotel = new HotelManager();

        assertEquals("+81 90-1234-5678", resolve(hotel,
                BookingDraft.builder().roomNumber("201").build(),
                "room 201 for Mr Tanaka +81 90-1234-5678").orElseThrow().phone(), "Japanese mobile");
        assertEquals("+65 8123 4567", resolve(hotel,
                BookingDraft.builder().roomNumber("201").build(),
                "201 2 nights +65 8123 4567").orElseThrow().phone(), "Singapore mobile");
        assertEquals("+1 (415) 555-0192", resolve(hotel,
                BookingDraft.builder().roomNumber("201").build(),
                "book 201, +1 (415) 555-0192").orElseThrow().phone(), "US number with brackets");
    }

    /** Room numbers, guest counts and night counts are all digits too. */
    @Test
    void shortNumbersAreNotMistakenForPhones() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("201").guests(2).nights(2).build(),
                "Make a booking at room 201 deluxe for 2 guests for 2 nights").orElseThrow();

        assertNull(proposal.phone(), "no phone in that sentence");
    }

    /** Two candidates means we cannot tell which is the guest's; the clerk types it. */
    @Test
    void anAmbiguousSentenceYieldsNoPhone() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("201").build(),
                "201, call 0812-3456-789 or 0857-1111-2222").orElseThrow();

        assertNull(proposal.phone(), "ambiguous, so left blank");
    }

    // ---------------------------------------------------------------- guest names

    @Test
    void aNameTheClerkTypedIsKept() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("201").guestName("Budi").build(),
                "booking kamar 201 untuk pak Budi").orElseThrow();

        assertEquals("Budi", proposal.guestName(), "name lifted from the sentence");
    }

    @Test
    void nameMatchingIgnoresCase() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("201").guestName("Tanaka").build(),
                "room 201 for mr tanaka").orElseThrow();

        assertEquals("Tanaka", proposal.guestName(), "case difference is not an invention");
    }

    /** Nothing validates a name, so it has to have come from the clerk's own words. */
    @Test
    void anInventedNameIsDropped() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolve(hotel,
                BookingDraft.builder().roomNumber("201").guestName("Siti Rahayu").build(),
                "booking kamar 201 untuk 2 orang 2 malam").orElseThrow();

        assertNull(proposal.guestName(), "a name nobody typed is not trusted");
    }
}
