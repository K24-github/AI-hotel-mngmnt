package hotel.ai;

import dev.langchain4j.model.ollama.OllamaModels;

import java.time.Duration;

public final class ModelHealth {

    public static final String NO_MODEL =
            "No model running. Start Ollama, or untick AI to fill the form by hand.";
    public static final String COULD_NOT_READ =
            "Could not read that. Pick a room and fill the form as usual.";

    private static final Duration REACH_TIMEOUT = Duration.ofSeconds(2);

    private ModelHealth() {
    }

    /** Nothing came back. Whether that is the clerk's sentence or a missing model is a real difference. */
    public static String messageWhenNothingCameBack(boolean modelAnswered) {
        return modelAnswered ? COULD_NOT_READ : NO_MODEL;
    }

    public static boolean isAnswering(OllamaConfig config) {
        return config != null && reachable(config.baseUrl());
    }

    public static boolean isAnswering(String endpoint) {
        return reachable(OllamaConfig.baseUrlOf(endpoint));
    }

    /** Ollama lists its models whether or not one is loaded, and that listing is the question. */
    private static boolean reachable(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        try {
            return OllamaModels.builder()
                    .baseUrl(baseUrl)
                    .timeout(REACH_TIMEOUT)
                    .maxRetries(1)
                    .build()
                    .availableModels()
                    .content() != null;
        } catch (Exception ex) {
            return false;
        }
    }
}
