package hotel.test;

import hotel.ai.Notes;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NotesTests {

    @Test
    void everyMarkerSpellingIsUnderstood() {
        assertEquals(Optional.of("minta lantai atas"),
                Notes.noteIn("kamar 109 2 malam, note: minta lantai atas"), "note:");
        assertEquals(Optional.of("extra bed please"),
                Notes.noteIn("room 307 2 nights, notes: extra bed please"), "notes:");
        assertEquals(Optional.of("tolong kamar dekat lift"),
                Notes.noteIn("booking 112 1 malam, catatan: tolong kamar dekat lift"), "catatan:");
        assertEquals(Optional.of("minta bantal tambahan"),
                Notes.noteIn("kmr 208 2mlm, cttn: minta bantal tambahan"), "cttn:");
    }

    @Test
    void markersAreCaseInsensitive() {
        assertEquals(Optional.of("late arrival"),
                Notes.noteIn("kamar 101, NOTE: late arrival"), "upper case");
        assertEquals(Optional.of("minta twin bed"),
                Notes.noteIn("kamar 203 2 malam, Catatan: minta twin bed"), "capitalised");
    }

    @Test
    void aNoteMayContainTheKeywordsTheParserHunts() {
        assertEquals(Optional.of("minta kamar yang tenang"),
                Notes.noteIn("kamar 210, 2 tamu, 1 malam, catatan: minta kamar yang tenang"),
                "note repeats kamar");
        assertEquals(Optional.of("tamu lansia tolong dekat lobby"),
                Notes.noteIn("kamar 102 1 malam, catatan: tamu lansia tolong dekat lobby"),
                "note repeats tamu");
        assertEquals("kamar 102 1 malam,",
                Notes.withoutNote("kamar 102 1 malam, catatan: tamu lansia tolong dekat lobby"),
                "and none of it reaches the parser");
    }

    @Test
    void theNoteRunsToTheEndOfTheInput() {
        assertEquals(Optional.of("honeymoon, decorate the room"),
                Notes.noteIn("308 3 nights, note: honeymoon, decorate the room"), "commas do not end it");
    }

    @Test
    void theNoteIsRemovedFromWhatIsParsed() {
        assertEquals("kamar 205 2 malam,",
                Notes.withoutNote("kamar 205 2 malam, note: tamu minta 2 bantal extra"), "note text cut off");
        assertEquals("kamar 101,",
                Notes.withoutNote("kamar 101, catatan: hubungi 0812-3456-789"), "phone inside the note cut off");
    }

    @Test
    void anUnmarkedSentenceIsLeftWhole() {
        assertEquals("kamar 210, 2 tamu, 1 malam",
                Notes.withoutNote("kamar 210, 2 tamu, 1 malam"), "nothing to strip");
        assertEquals(Optional.empty(), Notes.noteIn("kamar 210, 2 tamu, 1 malam"), "and no note found");
    }

    @Test
    void aMarkerWithNothingAfterItIsNotANote() {
        assertEquals(Optional.empty(), Notes.noteIn("kamar 101 2 malam note:"), "trailing marker");
        assertEquals(Optional.empty(), Notes.noteIn("kamar 101 2 malam note:    "), "marker and spaces");
    }

    @Test
    void nothingToSplitIsHandled() {
        assertEquals(Optional.empty(), Notes.noteIn(null), "null note");
        assertEquals("", Notes.withoutNote(null), "null remainder");
        assertEquals(Optional.empty(), Notes.noteIn(""), "empty");
    }
}
