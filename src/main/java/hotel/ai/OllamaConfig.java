package hotel.ai;

import java.time.Duration;

public record OllamaConfig(String endpoint, String model, String promptTemplate,
                           int contextTokens, int replyTokens, String keepAlive, Duration timeout) {

    public static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/generate";
    public static final String DEFAULT_MODEL = "qwen3.5:2b-q8_0";

    public static final String DEFAULT_PROMPT = """
            Extract booking fields from the sentence. Reply with JSON only.
            Indonesian and English may be mixed. malam/night = nights. orang/tamu/pax/guest = guests.
            kamar/room number goes in roomNumber. Studio, Deluxe and Suite are tiers.
            sarapan/breakfast = true, tanpa sarapan/no breakfast = false.
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
                1024, 96, "30m", Duration.ofSeconds(30));
    }

    public static OllamaConfig fromSystemProperties() {
        return new OllamaConfig(
                System.getProperty("hotel.ai.endpoint", DEFAULT_ENDPOINT),
                System.getProperty("hotel.ai.model", DEFAULT_MODEL),
                DEFAULT_PROMPT,
                Integer.getInteger("hotel.ai.contextTokens", 1024),
                Integer.getInteger("hotel.ai.replyTokens", 96),
                System.getProperty("hotel.ai.keepAlive", "30m"),
                Duration.ofSeconds(Integer.getInteger("hotel.ai.timeoutSeconds", 30)));
    }

    public OllamaConfig withModel(String otherModel) {
        return new OllamaConfig(endpoint, otherModel, promptTemplate,
                contextTokens, replyTokens, keepAlive, timeout);
    }

    public String promptFor(String sentence) {
        return promptTemplate.formatted(sentence);
    }
}
