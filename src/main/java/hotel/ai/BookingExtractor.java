package hotel.ai;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * The one call to the model. LangChain4j builds the schema off {@link BookingDraft},
 * sends it to Ollama as an output constraint and hands back the filled record, so there
 * is no request to write here.
 *
 * <p>The rules come in as the system message, set in {@link LangChainBookingParser} from
 * whatever prompt the config is holding. Only the sentence goes in the user message.
 */
public interface BookingExtractor {

    @UserMessage("""
            Sentence: {{sentence}}""")
    BookingDraft extract(@V("sentence") String sentence);
}
