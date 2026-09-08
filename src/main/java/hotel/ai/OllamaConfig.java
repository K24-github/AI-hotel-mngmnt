package hotel.ai;

import java.net.URI;
import java.time.Duration;

public record OllamaConfig(String endpoint, String model, String promptTemplate,
                           int contextTokens, int replyTokens, String keepAlive, Duration timeout,
                           Integer gpuLayers) {

    public static final String DEFAULT_ENDPOINT = "http://localhost:11434/api/generate";
    public static final String DEFAULT_MODEL = "gemma4:e2b-it-qat";

    /**
     * The field rules also travel as descriptions on {@link BookingDraft}, but descriptions
     * alone are not enough: with the rules only in the schema the model stopped filling tier
     * altogether and put the tier word in roomNumber instead. They earn their place here.
     */
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
            breakfast: true for sarapan or breakfast, false if a refusal word sits
            either side of it: tidak, tdk, ga, gak, tanpa, no, not, skip, without.
            guestName: only a name written in the sentence.

            Use null for anything the sentence does not say. Do not guess.""";

    public OllamaConfig {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("An Ollama endpoint is required.");
        }
        if (!inThisBuilding(endpoint)) {
            throw new IllegalArgumentException(
                    "Guest details never leave the building, so the endpoint must be this machine "
                            + "or your own network: " + endpoint);
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("A model name is required.");
        }
        if (promptTemplate == null || promptTemplate.isBlank()) {
            throw new IllegalArgumentException("A prompt is required.");
        }
    }

    /** This machine or one on the same private network. A public address would send guests abroad. */
    private static boolean inThisBuilding(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            if (host == null) {
                return false;
            }
            return "localhost".equals(host) || "127.0.0.1".equals(host) || "[::1]".equals(host)
                    || isPrivateNetwork(host);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean isPrivateNetwork(String host) {
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 10
                    || (first == 192 && second == 168)
                    || (first == 172 && second >= 16 && second <= 31);
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public String baseUrl() {
        return baseUrlOf(endpoint);
    }

    /**
     * LangChain4j wants the server, not a route on it, so the path comes off. The settings
     * still hold a full endpoint because that is what the eval harness posts to when it
     * unloads a model, and there is no call in the library for that.
     *
     * @return null if the endpoint has no host to find
     */
    static String baseUrlOf(String endpoint) {
        if (endpoint == null) {
            return null;
        }
        try {
            URI whole = URI.create(endpoint);
            if (whole.getHost() == null) {
                return null;
            }
            String port = (whole.getPort() == -1) ? "" : ":" + whole.getPort();
            return whole.getScheme() + "://" + whole.getHost() + port;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /** Ollama takes a duration string over its own API, and seconds through LangChain4j. */
    public Integer keepAliveSeconds() {
        if (keepAlive == null || keepAlive.isBlank()) {
            return null;
        }
        String trimmed = keepAlive.trim();
        char unit = trimmed.charAt(trimmed.length() - 1);
        try {
            if (Character.isDigit(unit)) {
                return Integer.valueOf(trimmed);
            }
            int amount = Integer.parseInt(trimmed.substring(0, trimmed.length() - 1));
            return switch (unit) {
                case 's' -> amount;
                case 'm' -> amount * 60;
                case 'h' -> amount * 3600;
                default -> null;
            };
        } catch (NumberFormatException ex) {
            return null;
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
}
