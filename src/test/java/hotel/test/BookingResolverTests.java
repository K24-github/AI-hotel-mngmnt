package hotel.test;

import hotel.ai.BookingDraft;
import hotel.ai.BookingProposal;
import hotel.ai.BookingResolver;
import hotel.model.Room;
import hotel.service.HotelManager;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
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

        BookingProposal proposal = resolve(hotel, draft,
                "Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included")
                .orElseThrow();

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

    @Test
    void aRoomNumberThatIsNotARoomFallsBackToTheTier() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("5").tier("Studio").nights(5).guestName("hana").build();

        BookingProposal proposal = resolve(hotel, draft, "hana jo studio 5 nights breakfast").orElseThrow();

        assertEquals("Studio", proposal.room().getTierName(), "a studio was picked instead");
        assertEquals(5, proposal.nights(), "the rest of the sentence survived");
        assertEquals("hana", proposal.guestName(), "and so did the name");
    }

    @Test
    void aFutureArrivalPicksARoomFreeOnThatDate() {
        HotelManager hotel = new HotelManager();
        java.time.LocalDate nextWeek = java.time.LocalDate.now().plusDays(7);
        for (Room studio : hotel.getRoomsByTier("Studio")) {
            hotel.createReservation(studio, "Kevin", "0812", null, nextWeek, 2, 1);
        }

        BookingDraft draft = BookingDraft.builder().tier("Studio").nights(2).build();

        assertTrue(new BookingResolver(hotel).resolve(draft, "", nextWeek).isEmpty(),
                "every studio is taken that week");
        assertTrue(new BookingResolver(hotel).resolve(draft, "", java.time.LocalDate.now()).isPresent(),
                "but they are free today");
    }

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

    /**
     * "Hana jo 5 nights deluxe breakfast" came back with guests=5, copied off the nights.
     * Five will not fit any deluxe, so every deluxe was filtered out and a booking the
     * clerk plainly asked for was refused. No guest word, no guest count.
     */
    @Test
    void aGuestCountTheSentenceNeverGaveIsIgnored() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().tier("Deluxe").guests(5).nights(5).breakfast(true).build();

        BookingProposal proposal =
                resolve(hotel, draft, "Hana jo 5 nights deluxe breakfast").orElseThrow();

        assertEquals("Deluxe", proposal.room().getTierName(), "a deluxe was still found");
        assertNull(proposal.guests(), "the invented count was dropped");
        assertEquals(5, proposal.nights(), "the nights were real");
    }

    // ------------------------------------------- night counts the sentence never gave

    /**
     * An invented night count widens the window the resolver checks, so a room that was
     * free for the stay the clerk asked for gets ruled out. Same shape as the guests bug.
     */
    @Test
    void aNightCountWithNoNightWordCannotDenyARoom() {
        HotelManager hotel = new HotelManager();
        for (Room deluxe : hotel.getRoomsByTier("Deluxe")) {
            hotel.createReservation(deluxe, "Someone", "0812", null, LocalDate.now().plusDays(3), 2, 1);
        }

        assertTrue(resolve(hotel, BookingDraft.builder().tier("Deluxe").nights(9).build(),
                "deluxe untuk pak Budi").isPresent(), "nine nights was never asked for");
    }

    @Test
    void aNightCountWithANightWordIsKept() {
        HotelManager hotel = new HotelManager();

        assertEquals(3, resolve(hotel, BookingDraft.builder().roomNumber("201").nights(3).build(),
                "kamar 201 3 malam").orElseThrow().nights(), "malam");
        assertEquals(2, resolve(hotel, BookingDraft.builder().roomNumber("201").nights(2).build(),
                "room 201 2 nights").orElseThrow().nights(), "nights");
        assertEquals(1, resolve(hotel, BookingDraft.builder().roomNumber("212").nights(1).build(),
                "kamar 212 utk bpk Hendra, 1 mlm").orElseThrow().nights(), "mlm");
        assertEquals(2, resolve(hotel, BookingDraft.builder().roomNumber("201").nights(2).build(),
                "kamar 201 2 hari").orElseThrow().nights(), "hari");
    }

    /** A stay can be given as a week rather than a number of nights, in either language. */
    @Test
    void aStayGivenAsWeeksIsKept() {
        HotelManager hotel = new HotelManager();

        assertEquals(7, resolve(hotel, BookingDraft.builder().tier("Deluxe").nights(7).build(),
                "deluxe for a week").orElseThrow().nights(), "week");
        assertEquals(7, resolve(hotel, BookingDraft.builder().roomNumber("201").nights(7).build(),
                "kamar 201 seminggu").orElseThrow().nights(), "seminggu");
        assertEquals(14, resolve(hotel, BookingDraft.builder().roomNumber("201").nights(14).build(),
                "kamar 201 2 minggu").orElseThrow().nights(), "minggu");
        assertEquals(14, resolve(hotel, BookingDraft.builder().roomNumber("201").nights(14).build(),
                "room 201 for 2 weeks").orElseThrow().nights(), "weeks");
    }

    @Test
    void aWordThatMerelyContainsANightWordDoesNotCount() {
        HotelManager hotel = new HotelManager();

        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("201").nights(2).build(),
                "kamar 201 untuk 2 harimau").orElseThrow().nights(), "harimau is not hari");
        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("201").nights(2).build(),
                "room 201 nightclub voucher").orElseThrow().nights(), "nightclub is not night");
        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("201").nights(2).build(),
                "room 201 weekend package").orElseThrow().nights(), "weekend is not week");
    }

    // ------------------------------------------- guest counts the sentence never gave

    /** Studio holds 2, Deluxe 3, Suite 5. An invented count must never rule a room out. */
    @Test
    void aCountWithNoGuestWordIsDroppedWhateverTheTier() {
        HotelManager hotel = new HotelManager();

        assertNull(resolve(hotel, BookingDraft.builder().tier("Deluxe").guests(4).nights(4).build(),
                "deluxe 4 malam").orElseThrow().guests(), "four nights is not four guests");
        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("202").guests(2).nights(2).build(),
                "kamar 202 untuk dua malam").orElseThrow().guests(), "spelled-out nights copied");
        assertNull(resolve(hotel, BookingDraft.builder().tier("Studio").guests(3).nights(3).build(),
                "3 malam studio").orElseThrow().guests(), "three nights is not three people");
        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("303").guests(2).nights(2).build(),
                "room 303 2 nights").orElseThrow().guests(), "no guest word in English either");
    }

    /** The count would not fit, but nothing in the sentence asked for it, so it cannot deny. */
    @Test
    void anInventedCountCannotEmptyAWholeTier() {
        HotelManager hotel = new HotelManager();

        assertTrue(resolve(hotel, BookingDraft.builder().tier("Studio").guests(9).nights(9).build(),
                "studio 9 nights").isPresent(), "nine will not fit a studio, but nobody asked for nine");
        assertTrue(resolve(hotel, BookingDraft.builder().roomNumber("101").guests(4).nights(4).build(),
                "kamar 101 4 malam").isPresent(), "a named room survives it too");
    }

    /** A count the clerk really typed still rules rooms out, in either language. */
    @Test
    void aCountWithAGuestWordIsStillHonoured() {
        HotelManager hotel = new HotelManager();

        assertTrue(resolve(hotel, BookingDraft.builder().tier("Studio").guests(6).nights(2).build(),
                "studio 6 orang 2 nights").isEmpty(), "orang");
        assertTrue(resolve(hotel, BookingDraft.builder().roomNumber("101").guests(4).nights(2).build(),
                "room 101 for 4 people 2 nights").isEmpty(), "people");
        assertTrue(resolve(hotel, BookingDraft.builder().roomNumber("204").guests(4).nights(2).build(),
                "room 204 for 4 guests 2 nights").isEmpty(), "guests");
        assertTrue(resolve(hotel, BookingDraft.builder().tier("Suite").guests(6).nights(2).build(),
                "suite 6 tamu 2 malam").isEmpty(), "tamu, and no suite holds six");

        assertEquals(2, resolve(hotel, BookingDraft.builder().roomNumber("201").guests(2).nights(2).build(),
                "201 deluxe 2 tamu 2 malam").orElseThrow().guests(), "a real count is carried");
        assertEquals(2, resolve(hotel, BookingDraft.builder().roomNumber("305").guests(2).nights(3).build(),
                "room 305 Ms Lim 3 nights 2 pax").orElseThrow().guests(), "pax");
        assertEquals(5, resolve(hotel, BookingDraft.builder().tier("Suite").guests(5).nights(1).build(),
                "suite untuk 5 orang").orElseThrow().guests(), "five fits a suite exactly");
    }

    @Test
    void guestWordsAreMatchedWhateverTheCase() {
        HotelManager hotel = new HotelManager();

        assertEquals(2, resolve(hotel, BookingDraft.builder().roomNumber("201").guests(2).nights(2).build(),
                "kamar 201 2 ORANG 2 malam").orElseThrow().guests(), "shouting still counts");
        assertEquals(2, resolve(hotel, BookingDraft.builder().roomNumber("201").guests(2).nights(1).build(),
                "room 201 2 Pax").orElseThrow().guests(), "mixed case");
    }

    /** "org" inside "organise" is not a guest word. Neither is "person" inside "personal". */
    @Test
    void aWordThatMerelyContainsAGuestWordDoesNotCount() {
        HotelManager hotel = new HotelManager();

        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("201").guests(2).nights(2).build(),
                "kamar 201 2 malam, organise a taxi").orElseThrow().guests(), "organise");
        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("201").guests(2).nights(2).build(),
                "room 201 2 nights personal request").orElseThrow().guests(), "personal");
        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("201").guests(2).nights(2).build(),
                "room 201 2 nights guesthouse transfer").orElseThrow().guests(), "guesthouse");
    }

    /** The note is the clerk's own text and is cut before the count is checked against it. */
    @Test
    void aGuestWordInsideTheNoteDoesNotCount() {
        HotelManager hotel = new HotelManager();

        assertNull(resolve(hotel, BookingDraft.builder().roomNumber("201").guests(4).nights(2).build(),
                "kamar 201 2 malam note: 4 orang datang jam 9").orElseThrow().guests(),
                "the guest word is only in the note");
    }

    @Test
    void aPartyTooBigForTheNamedRoomRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().roomNumber("101").guests(4).build();

        assertEquals(2, hotel.findRoom("101").orElseThrow().getCapacity(), "studio holds two");
        assertTrue(resolve(hotel, draft, "kamar 101 untuk 4 orang").isEmpty(),
                "four guests in a studio");
    }

    /** No room named, but no studio in the hotel could hold them either. */
    @Test
    void aPartyTooBigForTheWholeTierRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        assertTrue(resolve(hotel, BookingDraft.builder().tier("Studio").guests(4).build(),
                "studio untuk 4 orang").isEmpty(), "no studio holds four");
        assertTrue(resolve(hotel, BookingDraft.builder().tier("Suite").guests(4).build(),
                "suite untuk 4 orang").isPresent(), "a suite does");
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

        BookingProposal proposal = resolve(hotel, draft, "deluxe 2 orang 2 malam").orElseThrow();

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
