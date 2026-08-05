package hotel.test;

import hotel.ai.BookingDraft;
import hotel.ai.BookingProposal;
import hotel.ai.BookingResolver;
import hotel.service.HotelManager;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingResolverTests {

    private static BookingResolver resolverFor(HotelManager hotel) {
        return new BookingResolver(hotel);
    }

    // ------------------------------------------------------- the happy sentence

    /** "Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included" */
    @Test
    void aFullSentenceResolvesToEveryField() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("201").tier("Deluxe").guests(2).nights(2).breakfast(true).build();

        BookingProposal proposal = resolverFor(hotel).resolve(draft).orElseThrow();

        assertEquals(hotel.findRoom("201").orElseThrow(), proposal.room(), "room 201 found");
        assertEquals("Deluxe", proposal.tier(), "tier carried");
        assertEquals(2, proposal.guests(), "guests carried");
        assertEquals(2, proposal.nights(), "nights carried");
        assertTrue(proposal.breakfast(), "breakfast requested");
    }

    /** Receptionists do not capitalise tiers, and the model will echo whatever they typed. */
    @Test
    void tierMatchingIgnoresCase() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolverFor(hotel)
                .resolve(BookingDraft.builder().roomNumber("201").tier("dELuXe").build())
                .orElseThrow();

        assertEquals("Deluxe", proposal.tier(), "tier normalised to the hotel's spelling");
    }

    @Test
    void notesAndBreakfastPassStraightThrough() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolverFor(hotel)
                .resolve(BookingDraft.builder().roomNumber("101").notes("late arrival").build())
                .orElseThrow();

        assertEquals("late arrival", proposal.notes(), "notes kept");
        assertFalse(proposal.breakfast(), "breakfast not mentioned");
    }

    // -------------------------------------------------------- inventions go nowhere

    /** Constraint: if the model invents room 999 the whole proposal dies. */
    @Test
    void anInventedRoomNumberRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("999").guests(2).nights(2).build();

        assertTrue(resolverFor(hotel).resolve(draft).isEmpty(), "room 999 does not exist");
    }

    @Test
    void anInventedTierRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        assertTrue(resolverFor(hotel).resolve(BookingDraft.builder().tier("Penthouse").build()).isEmpty(),
                "this hotel has no penthouse");
    }

    /** A room number and a tier that disagree means the sentence was misread. */
    @Test
    void aTierContradictingTheRoomRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("101").tier("Suite").nights(2).build();

        assertEquals("Studio", hotel.findRoom("101").orElseThrow().getTierName(), "101 is a studio");
        assertTrue(resolverFor(hotel).resolve(draft).isEmpty(), "studio called a suite");
    }

    // ------------------------------------------------------------------ capacity

    @Test
    void aPartyTooBigForTheNamedRoomRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().roomNumber("101").guests(4).build();

        assertEquals(2, hotel.findRoom("101").orElseThrow().getCapacity(), "studio holds two");
        assertTrue(resolverFor(hotel).resolve(draft).isEmpty(), "four guests in a studio");
    }

    /** No room named, but no studio in the hotel could hold them either. */
    @Test
    void aPartyTooBigForTheWholeTierRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        assertTrue(resolverFor(hotel).resolve(BookingDraft.builder().tier("Studio").guests(4).build()).isEmpty(),
                "no studio holds four");
        assertTrue(resolverFor(hotel).resolve(BookingDraft.builder().tier("Suite").guests(4).build()).isPresent(),
                "a suite does");
    }

    @Test
    void aPartyLargerThanAnyRoomRejectsTheDraft() {
        HotelManager hotel = new HotelManager();
        assertTrue(resolverFor(hotel).resolve(BookingDraft.builder().guests(9).nights(2).build()).isEmpty(),
                "nine guests fit nowhere");
    }

    // -------------------------------------------------- a tier with no room number

    /** "kamar deluxe untuk 2 malam" - fill what was said, let the clerk pick the room. */
    @Test
    void aTierWithoutARoomNumberLeavesTheRoomUnchosen() {
        HotelManager hotel = new HotelManager();
        BookingDraft draft = BookingDraft.builder().tier("Deluxe").guests(2).nights(2).build();

        BookingProposal proposal = resolverFor(hotel).resolve(draft).orElseThrow();

        assertFalse(proposal.hasRoom(), "no room chosen for the clerk");
        assertNull(proposal.room(), "room left null");
        assertEquals("Deluxe", proposal.tier(), "tier kept so the grid can be filtered");
        assertEquals(2, proposal.guests(), "guests still prefilled");
        assertEquals(2, proposal.nights(), "nights still prefilled");
    }

    @Test
    void aSentenceWithNoRoomOrTierStillPrefillsNumbers() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolverFor(hotel)
                .resolve(BookingDraft.builder().guests(2).nights(3).breakfast(true).build())
                .orElseThrow();

        assertFalse(proposal.hasRoom(), "nothing to pick a room from");
        assertNull(proposal.tier(), "no tier mentioned");
        assertEquals(3, proposal.nights(), "nights survive on their own");
    }

    // ------------------------------------------------------------ nonsense numbers

    /** A bad number is dropped on its own; the rest of the sentence was still good. */
    @Test
    void impossibleCountsAreDroppedNotFatal() {
        HotelManager hotel = new HotelManager();
        BookingProposal proposal = resolverFor(hotel)
                .resolve(BookingDraft.builder().roomNumber("201").nights(0).guests(-1).build())
                .orElseThrow();

        assertNull(proposal.nights(), "zero nights dropped");
        assertNull(proposal.guests(), "negative guests dropped");
        assertEquals(hotel.findRoom("201").orElseThrow(), proposal.room(), "the room was still valid");
    }

    // ---------------------------------------------------------------- empty input

    @Test
    void nothingToResolveYieldsNoProposal() {
        HotelManager hotel = new HotelManager();
        BookingResolver resolver = resolverFor(hotel);

        assertTrue(resolver.resolve(null).isEmpty(), "null draft");
        assertTrue(resolver.resolve(BookingDraft.empty()).isEmpty(), "empty draft");
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

        Optional<BookingProposal> proposal = resolverFor(hotel)
                .resolve(BookingDraft.builder().roomNumber("201").nights(2).build());

        assertTrue(proposal.isPresent(), "resolved despite the guest");
        assertTrue(proposal.orElseThrow().room().isOccupied(), "and the room really is taken");
    }
}
