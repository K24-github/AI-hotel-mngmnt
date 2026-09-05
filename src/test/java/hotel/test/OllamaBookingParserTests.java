package hotel.test;

import hotel.ai.BookingDraft;
import hotel.ai.OllamaBookingParser;
import hotel.ai.OllamaConfig;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OllamaBookingParserTests {

    private static final OllamaConfig CONFIG = OllamaConfig.of("test-model");

    private static OllamaBookingParser answering(String replyJson) {
        return new OllamaBookingParser(CONFIG, request -> Optional.of(
                "{\"model\":\"test-model\",\"done\":true,\"response\":" + quoted(replyJson) + "}"));
    }

    private static String quoted(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @Test
    void aWellFormedReplyBecomesADraft() {
        BookingDraft draft = answering(
                "{\"roomNumber\":\"201\",\"tier\":\"Deluxe\",\"guestName\":null,"
                        + "\"guests\":2,\"nights\":2,\"breakfast\":true}")
                .parse("Make a booking at room 201 deluxe for 2 guests for 2 nights, breakfast included")
                .orElseThrow();

        assertEquals("201", draft.roomNumber(), "room number");
        assertEquals("Deluxe", draft.tier(), "tier");
        assertEquals(2, draft.guests(), "guests");
        assertEquals(2, draft.nights(), "nights");
        assertTrue(draft.wantsBreakfast(), "breakfast");
        assertNull(draft.guestName(), "no name in the sentence");
    }

    @Test
    void nullsMeanTheSentenceDidNotSayIt() {
        BookingDraft draft = answering(
                "{\"roomNumber\":\"201\",\"tier\":null,\"guestName\":null,"
                        + "\"guests\":null,\"nights\":2,\"breakfast\":null}")
                .parse("kamar 201 2 malam").orElseThrow();

        assertNull(draft.tier(), "tier absent");
        assertNull(draft.guests(), "guests absent");
        assertNull(draft.breakfast(), "breakfast absent");
        assertFalse(draft.wantsBreakfast(), "silence is not a request");
    }

    @Test
    void aFieldSentAsTextIsStillUnderstood() {
        BookingDraft draft = answering(
                "{\"roomNumber\":201,\"guests\":\"2\",\"nights\":\"3\"}")
                .parse("kamar 201 2 orang 3 malam").orElseThrow();

        assertEquals("201", draft.roomNumber(), "number coerced to text");
        assertEquals(2, draft.guests(), "text coerced to number");
        assertEquals(3, draft.nights(), "text coerced to number");
    }

    @Test
    void inventedFieldsAreIgnored() {
        BookingDraft draft = answering(
                "{\"roomNumber\":\"201\",\"nights\":2,\"confidence\":0.9,\"reasoning\":\"the user said 201\"}")
                .parse("kamar 201 2 malam").orElseThrow();

        assertEquals("201", draft.roomNumber(), "the real fields survive");
        assertEquals(2, draft.nights(), "nights survive");
    }

    @Test
    void unusableRepliesYieldNothing() {
        assertTrue(answering("I think you want room 201 for two nights.").parse("kamar 201").isEmpty(),
                "prose instead of JSON");
        assertTrue(answering("{\"roomNumber\":\"201\",\"nights\":").parse("kamar 201").isEmpty(),
                "truncated JSON");
        assertTrue(answering("").parse("kamar 201").isEmpty(), "empty reply");
        assertTrue(answering("{}").parse("kamar 201").isEmpty(), "no fields at all");
        assertTrue(answering("{\"roomNumber\":null,\"nights\":null}").parse("kamar 201").isEmpty(),
                "every field null");
    }

    @Test
    void aBrokenEnvelopeYieldsNothing() {
        OllamaBookingParser garbled = new OllamaBookingParser(CONFIG, request -> Optional.of("not json at all"));
        assertTrue(garbled.parse("kamar 201 2 malam").isEmpty(), "unreadable envelope");

        OllamaBookingParser silent = new OllamaBookingParser(CONFIG, request -> Optional.empty());
        assertTrue(silent.parse("kamar 201 2 malam").isEmpty(), "no reply at all");
    }

    @Test
    void nothingToParseIsNotAskedAboutAtAll() {
        OllamaBookingParser exploding = new OllamaBookingParser(CONFIG, request -> {
            throw new AssertionError("the transport should not have been called");
        });

        assertTrue(exploding.parse(null).isEmpty(), "null input");
        assertTrue(exploding.parse("   ").isEmpty(), "blank input");
    }

    @Test
    void anAbsentOllamaLooksExactlyLikeAConfusingSentence() {
        OllamaConfig unreachable = new OllamaConfig("http://localhost:1/api/generate", "qwen3.5:2b-q8_0",
                OllamaConfig.DEFAULT_PROMPT, 1024, 96, "30m", Duration.ofSeconds(2));

        Optional<BookingDraft> parsed =
                new OllamaBookingParser(unreachable).parse("booking kamar 201 untuk 2 orang 2 malam");

        assertTrue(parsed.isEmpty(), "nothing installed, nothing proposed, no exception");
    }

    @Test
    void theRequestPinsDownEverythingThatCostsMemoryOrTime() {
        StringBuilder sent = new StringBuilder();
        new OllamaBookingParser(CONFIG, request -> {
            sent.append(request);
            return Optional.empty();
        }).parse("kamar 201 2 malam");

        String request = sent.toString();
        assertTrue(request.contains("\"num_ctx\":1024"), "context window pinned small: " + request);
        assertTrue(request.contains("\"num_predict\":96"), "reply length capped");
        assertTrue(request.contains("\"temperature\":0"), "extraction is not a creative task");
        assertTrue(request.contains("\"think\":false"), "no chain of thought before the JSON");
        assertTrue(request.contains("\"keep_alive\":\"30m\""), "model stays resident between bookings");
        assertTrue(request.contains("\"stream\":false"), "one whole reply, not a stream");
        assertTrue(request.contains("\"format\""), "output constrained to the schema");
        assertTrue(request.contains("kamar 201 2 malam"), "the sentence reaches the model");
    }
}
