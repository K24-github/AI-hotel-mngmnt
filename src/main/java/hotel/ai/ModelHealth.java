package hotel.ai;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
        return config != null && isAnswering(config.endpoint());
    }

    public static boolean isAnswering(String endpoint) {
        if (endpoint == null) {
            return false;
        }
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(REACH_TIMEOUT).build();
            URI tags = URI.create(endpoint.replace("/api/generate", "/api/tags"));
            HttpRequest request = HttpRequest.newBuilder(tags).timeout(REACH_TIMEOUT).GET().build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        }
    }
}
