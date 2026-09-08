package hotel.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.Map;

/**
 * Drops a guest or night count the sentence never actually gave, before the reply becomes
 * a {@link BookingDraft}. On a sentence like "5 nights studio" the model sometimes puts
 * the 5 in guests as well, and a guest count nobody typed rules out every room in the
 * tier. It is about five sentences in a hundred, but it costs a booking each time.
 *
 * <p>This rewrites the reply instead of asking the model again. Temperature is 0, so a
 * plain retry comes back identical, and a reprompt would mean a second call while the
 * clerk is waiting. Rewriting takes no time and cannot lose the rest of the extraction.
 */
public final class CountsBackedBySentence implements OutputGuardrail {

    static final String SENTENCE = "sentence";

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        String sentence = sentenceIn(request);
        String reply = replyIn(request);
        if (sentence == null || reply == null) {
            return success();
        }
        try {
            JsonNode parsed = mapper.readTree(reply);
            if (!parsed.isObject()) {
                return success();
            }
            ObjectNode fields = (ObjectNode) parsed;
            boolean guestsDropped = dropUnbacked(fields, "guests", SentenceEvidence.mentionsGuests(sentence));
            boolean nightsDropped = dropUnbacked(fields, "nights", SentenceEvidence.mentionsNights(sentence));
            return (guestsDropped || nightsDropped)
                    ? successWith(AiMessage.from(fields.toString()))
                    : success();
        } catch (Exception ex) {
            // An unreadable reply is the parser's problem to report, not a guardrail failure.
            return success();
        }
    }

    private static boolean dropUnbacked(ObjectNode fields, String name, boolean sentenceSaysSo) {
        if (sentenceSaysSo || !fields.hasNonNull(name) || !fields.get(name).isNumber()) {
            return false;
        }
        fields.putNull(name);
        return true;
    }

    private static String sentenceIn(OutputGuardrailRequest request) {
        Map<String, Object> variables = request.requestParams().variables();
        Object typed = (variables == null) ? null : variables.get(SENTENCE);
        return (typed == null) ? null : typed.toString();
    }

    private static String replyIn(OutputGuardrailRequest request) {
        AiMessage answer = request.responseFromLLM().aiMessage();
        return (answer == null) ? null : answer.text();
    }
}
