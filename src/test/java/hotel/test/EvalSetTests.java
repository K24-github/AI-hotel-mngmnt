package hotel.test;

import hotel.ai.BookingProposal;
import hotel.ai.BookingResolver;
import hotel.ai.EvalSet;
import hotel.ai.Notes;
import hotel.service.HotelManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvalSetTests {

    static Stream<Arguments> rows() {
        return EvalSet.load().stream().map(row -> Arguments.of(row.sentence(), row));
    }

    @Test
    void theEvalSetLoadsStrictly() {
        List<EvalSet.Row> rows = EvalSet.load();

        assertFalse(rows.isEmpty(), "the eval set has rows");
        for (EvalSet.Row row : rows) {
            assertNotNull(row.sentence(), "every row has a sentence");
            assertFalse(row.sentence().isBlank(), "no blank sentences");
            assertNotNull(row.expected(), "every row has an expected draft: " + row.sentence());
            assertFalse(row.expected().isEmpty(), "no row expects nothing: " + row.sentence());
            assertNotNull(row.note(), "every row says why it exists: " + row.sentence());
        }
    }

    @Test
    void sentencesAreUnique() {
        Set<String> seen = new HashSet<>();
        for (EvalSet.Row row : EvalSet.load()) {
            assertTrue(seen.add(row.sentence().toLowerCase()), "duplicated sentence: " + row.sentence());
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void expectedDraftsResolveAsTheRowClaims(String sentence, EvalSet.Row row) {
        HotelManager hotel = new HotelManager();
        Optional<BookingProposal> proposal =
                new BookingResolver(hotel).resolve(row.expected(), row.sentence());

        assertEquals(row.resolves(), proposal.isPresent(), row.note());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rows")
    void expectedNamesAppearInWhatGetsParsed(String sentence, EvalSet.Row row) {
        String name = row.expected().guestName();
        if (name == null) {
            return;
        }
        assertTrue(Notes.withoutNote(row.sentence()).toLowerCase().contains(name.toLowerCase()),
                "the eval row expects a name nobody typed: " + row.sentence());
    }
}
