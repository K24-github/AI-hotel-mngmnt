package hotel.ai;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LangChainBookingParserTests {

    private static final OllamaConfig CONFIG = OllamaConfig.of("test-model");

    /** Stands in for Ollama, so the trust boundary can be tested without a model. */
    private static final class Canned implements HttpClient {
        private final List<String> sent = new ArrayList<>();
        private final String reply;
        private final RuntimeException failure;

        private Canned(String reply, RuntimeException failure) {
            this.reply = reply;
            this.failure = failure;
        }

        @Override
        public SuccessfulHttpResponse execute(HttpRequest request) {
            sent.add(request.body());
            if (failure != null) {
                throw failure;
            }
            return SuccessfulHttpResponse.builder()
                    .statusCode(200)
                    .headers(Map.of("Content-Type", List.of("application/json")))
                    .body(reply)
                    .build();
        }

        @Override
        public void execute(HttpRequest request, ServerSentEventParser parser,
                            ServerSentEventListener listener) {
            throw new UnsupportedOperationException("the parser never streams");
        }
    }

    private static final class CannedBuilder implements HttpClientBuilder {
        private final Canned client;
        private Duration connectTimeout;
        private Duration readTimeout;

        private CannedBuilder(Canned client) {
            this.client = client;
        }

        @Override
        public Duration connectTimeout() {
            return connectTimeout;
        }

        @Override
        public HttpClientBuilder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        @Override
        public Duration readTimeout() {
            return readTimeout;
        }

        @Override
        public HttpClientBuilder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        @Override
        public HttpClient build() {
            return client;
        }
    }

    private static String envelope(String content) {
        return "{\"model\":\"test-model\",\"created_at\":\"2026-01-01T00:00:00Z\",\"done\":true,"
                + "\"message\":{\"role\":\"assistant\",\"content\":" + quoted(content) + "}}";
    }

    private static String quoted(String raw) {
        return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static Canned canned;

    private static LangChainBookingParser answering(String replyJson) {
        canned = new Canned(envelope(replyJson), null);
        return new LangChainBookingParser(CONFIG, new CannedBuilder(canned));
    }

    private static LangChainBookingParser failingWith(RuntimeException failure) {
        canned = new Canned(null, failure);
        return new LangChainBookingParser(CONFIG, new CannedBuilder(canned));
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

    /**
     * The old hand-rolled parser kept the fields it recognised and dropped the rest.
     * LangChain4j refuses the whole reply instead, on the grounds that a model which
     * invented one field may have invented others. The schema keeps it from arising.
     */
    @Test
    void aReplyCarryingFieldsTheSchemaNeverAskedForIsRefused() {
        assertTrue(answering(
                "{\"roomNumber\":\"201\",\"nights\":2,\"confidence\":0.9,\"reasoning\":\"the user said 201\"}")
                .parse("kamar 201 2 malam").isEmpty(),
                "an invented field makes the whole reply suspect");
    }

    @Test
    void aReplyMayLeaveOutWhateverTheSentenceDidNotMention() {
        BookingDraft draft = answering("{\"roomNumber\":\"201\",\"nights\":2}")
                .parse("kamar 201 2 malam").orElseThrow();

        assertEquals("201", draft.roomNumber(), "what was said survives");
        assertEquals(2, draft.nights(), "what was said survives");
        assertNull(draft.tier(), "what was not said is absent");
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
    void anUnreachableOllamaLooksExactlyLikeAConfusingSentence() {
        Optional<BookingDraft> parsed = failingWith(new RuntimeException("connection refused"))
                .parse("booking kamar 201 untuk 2 orang 2 malam");

        assertTrue(parsed.isEmpty(), "nothing listening, nothing proposed, no exception");
    }

    @Test
    void nothingToParseIsNotAskedAboutAtAll() {
        LangChainBookingParser exploding =
                failingWith(new IllegalStateException("the model should not have been called"));

        assertTrue(exploding.parse(null).isEmpty(), "null input");
        assertTrue(exploding.parse("   ").isEmpty(), "blank input");
        assertTrue(canned.sent.isEmpty(), "no request was sent at all");
    }

    /** The guardrail runs before the reply becomes a draft, so an invented count never lands. */
    @Test
    void aCountTheSentenceNeverBackedIsStruckOutBeforeItBecomesADraft() {
        BookingDraft draft = answering(
                "{\"roomNumber\":null,\"tier\":\"Studio\",\"guestName\":\"Hana jo\","
                        + "\"guests\":5,\"nights\":5,\"breakfast\":true}")
                .parse("Hana jo 5 nights studio breakfast").orElseThrow();

        assertNull(draft.guests(), "no guest word in the sentence, so the 5 was copied from the nights");
        assertEquals(5, draft.nights(), "nights were actually said");
        assertEquals("Studio", draft.tier(), "the rest of the reply is untouched");
        assertEquals("Hana jo", draft.guestName(), "the rest of the reply is untouched");
    }

    @Test
    void aCountTheSentenceDidBackSurvives() {
        BookingDraft draft = answering(
                "{\"roomNumber\":\"201\",\"tier\":null,\"guestName\":null,"
                        + "\"guests\":2,\"nights\":3,\"breakfast\":null}")
                .parse("kamar 201 2 orang 3 malam").orElseThrow();

        assertEquals(2, draft.guests(), "guest word present");
        assertEquals(3, draft.nights(), "night word present");
    }

    @Test
    void theRequestPinsDownEverythingThatCostsMemoryOrTime() {
        answering("{\"roomNumber\":\"201\"}").parse("kamar 201 2 malam");

        String sent = canned.sent.get(0);
        String request = sent.replaceAll("\\s+", "");
        assertTrue(request.contains("\"num_ctx\":1024"), "context window pinned small: " + sent);
        assertTrue(request.contains("\"num_predict\":96"), "reply length capped: " + sent);
        assertTrue(request.contains("\"temperature\":0.0"), "extraction is not creative: " + sent);
        assertTrue(request.contains("\"think\":false"), "no chain of thought first: " + sent);
        assertTrue(request.contains("\"keep_alive\":1800"), "model stays resident: " + sent);
        assertTrue(request.contains("\"stream\":false"), "one whole reply, not a stream: " + sent);
        assertTrue(request.contains("\"format\""), "output constrained to the schema: " + sent);
        assertTrue(sent.contains("kamar 201 2 malam"), "the sentence reaches the model: " + sent);
    }

    /** The schema is generated from the record, so the six fields travel with the request. */
    @Test
    void theGeneratedSchemaNamesEveryFieldTheDraftHas() {
        answering("{\"roomNumber\":\"201\"}").parse("kamar 201 2 malam");

        String request = canned.sent.get(0);
        for (String field : List.of("roomNumber", "tier", "guestName", "guests", "nights", "breakfast")) {
            assertTrue(request.contains(field), field + " is missing from the schema: " + request);
        }
    }
}
