package hotel.ai;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonAnyOfSchema;
import dev.langchain4j.model.chat.request.json.JsonNullSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaChatRequestParameters;
import dev.langchain4j.service.AiServices;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

/**
 * Turns the typed sentence into a {@link BookingDraft} through LangChain4j.
 *
 * <p>LangChain4j writes the schema off the record, but the one it writes was wrong for
 * this job, so {@code everyFieldOrNull} rebuilds it before the request goes out. That is
 * the only part of this class doing real work.
 *
 * <p>Constraining the schema only fixes the shape of the reply. Whether the values in it
 * are true to the sentence is still {@link BookingResolver}'s job.
 */
public final class LangChainBookingParser implements BookingParser {

    private final BookingExtractor extractor;

    public LangChainBookingParser(OllamaConfig config) {
        this(config, null);
    }

    /** Lets a test stand in for Ollama. Nothing in the app passes a builder. */
    LangChainBookingParser(OllamaConfig config, HttpClientBuilder httpClientBuilder) {
        if (config == null) {
            throw new IllegalArgumentException("A config is required.");
        }
        this.extractor = serviceOn(chatModel(config, httpClientBuilder), config);
    }

    /**
     * Takes the sentence exactly as the clerk typed it. A marked note and a phone number
     * are cut out here, because their digits otherwise land in a numbered field.
     */
    @Override
    public Optional<BookingDraft> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String readable = PhoneNumbers.withoutPhone(Notes.withoutNote(text));
        if (readable.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(extractor.extract(readable)).filter(draft -> !draft.isEmpty());
        } catch (Exception ex) {
            // A model that is not running looks the same here as a reply that made no sense.
            return Optional.empty();
        }
    }

    private static ChatModel chatModel(OllamaConfig config, HttpClientBuilder httpClientBuilder) {
        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .baseUrl(config.baseUrl())
                .modelName(config.model())
                .temperature(0.0)
                .numCtx(config.contextTokens())
                .numPredict(config.replyTokens())
                .think(false)
                .timeout(config.timeout())
                .supportedCapabilities(Set.of(Capability.RESPONSE_FORMAT_JSON_SCHEMA))
                .defaultRequestParameters(OllamaChatRequestParameters.builder()
                        .keepAlive(config.keepAliveSeconds())
                        .numGPU(config.gpuLayers())
                        .build());
        if (httpClientBuilder != null) {
            builder = builder.httpClientBuilder(httpClientBuilder);
        }
        return builder.build();
    }

    private static BookingExtractor serviceOn(ChatModel model, OllamaConfig config) {
        return AiServices.builder(BookingExtractor.class)
                .chatModel(model)
                .systemMessageProvider(request -> config.promptTemplate())
                .chatRequestTransformer(LangChainBookingParser::everyFieldOrNull)
                .outputGuardrails(new CountsBackedBySentence())
                .build();
    }

    /**
     * The schema LangChain4j generates marks nothing as required and lets no field hold
     * null, and gemma4 takes that as permission. It drops any field it is not sure about,
     * or writes the word null into a string when it has to put something there. Either way
     * the value never arrives.
     *
     * <p>So every field goes back in as required, and every field is allowed to be null.
     * Then the model has to answer all six, and saying null is one of the answers.
     */
    private static ChatRequest everyFieldOrNull(ChatRequest request) {
        ResponseFormat format = request.responseFormat();
        if (format == null || format.jsonSchema() == null
                || !(format.jsonSchema().rootElement() instanceof JsonObjectSchema fields)) {
            return request;
        }
        JsonObjectSchema.Builder nullable = JsonObjectSchema.builder()
                .description(fields.description());
        fields.properties().forEach((name, element) -> nullable.addProperty(name,
                JsonAnyOfSchema.builder()
                        .description(element.description())
                        .anyOf(element, new JsonNullSchema())
                        .build()));
        nullable.required(new ArrayList<>(fields.properties().keySet()));

        return request.toBuilder()
                .responseFormat(ResponseFormat.builder()
                        .type(ResponseFormatType.JSON)
                        .jsonSchema(JsonSchema.builder()
                                .name(format.jsonSchema().name())
                                .rootElement(nullable.build())
                                .build())
                        .build())
                .build();
    }
}
