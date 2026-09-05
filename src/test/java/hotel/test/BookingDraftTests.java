package hotel.test;

import hotel.ai.BookingDraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingDraftTests {

    // ------------------------------------------------------------ absent fields

    /** A sentence naming only a room leaves everything else genuinely unknown. */
    @Test
    void unsetFieldsStayNull() {
        BookingDraft draft = BookingDraft.builder().roomNumber("201").build();

        assertEquals("201", draft.roomNumber(), "room number kept");
        assertNull(draft.tier(), "tier not mentioned");
        assertNull(draft.guestName(), "no name mentioned");
        assertNull(draft.guests(), "guests not mentioned");
        assertNull(draft.nights(), "nights not mentioned");
        assertNull(draft.breakfast(), "breakfast not mentioned");
    }

    /** Silence about breakfast is not a request for breakfast. */
    @Test
    void unmentionedBreakfastIsNotRequested() {
        assertFalse(BookingDraft.empty().wantsBreakfast(), "absent breakfast");
        assertFalse(BookingDraft.builder().breakfast(false).build().wantsBreakfast(), "declined breakfast");
        assertTrue(BookingDraft.builder().breakfast(true).build().wantsBreakfast(), "requested breakfast");
    }

    @Test
    void emptyDraftReportsItself() {
        assertTrue(BookingDraft.empty().isEmpty(), "nothing extracted");
        assertFalse(BookingDraft.builder().nights(2).build().isEmpty(), "one field is enough");
        assertFalse(BookingDraft.builder().guestName("Budi").build().isEmpty(), "a name counts too");
    }

    // -------------------------------------------------------------- normalising

    /** A model padding its answer with spaces must not become a different draft. */
    @Test
    void textFieldsAreTrimmed() {
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("  201 ")
                .tier(" Deluxe ")
                .guestName("  Budi  ")
                .build();

        assertEquals("201", draft.roomNumber(), "room number trimmed");
        assertEquals("Deluxe", draft.tier(), "tier trimmed");
        assertEquals("Budi", draft.guestName(), "name trimmed");
    }

    /** An empty string means the model found nothing, which is null, not "". */
    @Test
    void blankTextBecomesNull() {
        BookingDraft draft = BookingDraft.builder()
                .roomNumber("")
                .tier("   ")
                .guestName("\t")
                .build();

        assertNull(draft.roomNumber(), "blank room number");
        assertNull(draft.tier(), "whitespace tier");
        assertNull(draft.guestName(), "tab-only name");
        assertTrue(draft.isEmpty(), "a draft of blanks is empty");
    }

    @Test
    void draftsWithTheSameFieldsAreEqual() {
        BookingDraft first = BookingDraft.builder().roomNumber("201").guests(2).nights(2).build();
        BookingDraft second = BookingDraft.builder().roomNumber(" 201 ").guests(2).nights(2).build();

        assertEquals(first, second, "same fields after trimming");
        assertEquals(first.hashCode(), second.hashCode(), "matching hash codes");
    }
}
