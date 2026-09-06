package hotel.ai;

import java.time.Duration;

public record OllamaConfig(String endpoint, String model, String promptTemplate,
                           int contextTokens, int replyTokens, String keepAlive, Duration timeout,
                           Integer gpuLayers) {

    public static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/generate";
    public static final String DEFAULT_MODEL = "gemma4:e2b-it-qat";

    public static final String DEFAULT_PROMPT = """
            Extract booking fields from the sentence. Reply with JSON only.
            Indonesian and English may be mixed.

            A number is a guest count only when a guest word sits beside it:
            orang, org, tamu, pax, px, guest, guests, people, person.
            A number is a night count only when a night word sits beside it:
            malam, mlm, night, nights, nt, hari.
            If the sentence has no guest word, guests is null. Never reuse the nights
            number as the guests number, or the guests number as the nights number.

            roomNumber: a three-digit number such as 101, 204 or 308, usually after
            kamar, kmr, room or rm. A one or two digit number is never a room number.
            tier: only Studio, Deluxe or Suite, and only if that word appears.
            breakfast: true for sarapan or breakfast, false for tanpa sarapan or no breakfast.
            guestName: only a name written in the sentence.

            Use null for anything the sentence does not say. Do not guess.

            Sentence: %s""";

    public OllamaConfig {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("An Ollama endpoint is required.");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("A model name is required.");
        }
        if (promptTemplate == null || !promptTemplate.contains("%s")) {
            throw new IllegalArgumentException("The prompt template must have a %s for the sentence.");
        }
    }

    public static OllamaConfig of(String model) {
        return new OllamaConfig(DEFAULT_ENDPOINT, model, DEFAULT_PROMPT,
                1024, 96, "30m", Duration.ofSeconds(30), null);
    }

    public static OllamaConfig fromSystemProperties() {
        return new OllamaConfig(
                System.getProperty("hotel.ai.endpoint", DEFAULT_ENDPOINT),
                System.getProperty("hotel.ai.model", DEFAULT_MODEL),
                DEFAULT_PROMPT,
                Integer.getInteger("hotel.ai.contextTokens", 1024),
                Integer.getInteger("hotel.ai.replyTokens", 96),
                System.getProperty("hotel.ai.keepAlive", "30m"),
                Duration.ofSeconds(Integer.getInteger("hotel.ai.timeoutSeconds", 30)),
                Integer.getInteger("hotel.ai.gpuLayers"));
    }

    public OllamaConfig withModel(String otherModel) {
        return new OllamaConfig(endpoint, otherModel, promptTemplate,
                contextTokens, replyTokens, keepAlive, timeout, gpuLayers);
    }

    public String promptFor(String sentence) {
        return promptTemplate.formatted(sentence);
    }
}
