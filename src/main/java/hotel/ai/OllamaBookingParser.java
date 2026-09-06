package hotel.ai;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public final class OllamaBookingParser implements BookingParser {

    @FunctionalInterface
    public interface Transport {
        Optional<String> send(String requestBody);
    }

    private static final String SCHEMA = """
            {"type":"object","properties":{
              "roomNumber":{"type":["string","null"]},
              "tier":{"type":["string","null"]},
              "guestName":{"type":["string","null"]},
              "guests":{"type":["integer","null"]},
              "nights":{"type":["integer","null"]},
              "breakfast":{"type":["boolean","null"]}},
             "required":["roomNumber","tier","guestName","guests","nights","breakfast"]}""";

    private final OllamaConfig config;
    private final Transport transport;
    private final ObjectMapper mapper;

    public OllamaBookingParser(OllamaConfig config) {
        this(config, httpTransport(config));
    }

    public OllamaBookingParser(OllamaConfig config, Transport transport) {
        if (config == null || transport == null) {
            throw new IllegalArgumentException("A config and a transport are required.");
        }
        this.config = config;
        this.transport = transport;
        this.mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Optional<BookingDraft> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return transport.send(requestFor(text))
                .flatMap(this::replyBody)
                .flatMap(this::draftFrom)
                .filter(draft -> !draft.isEmpty());
    }

    private String requestFor(String text) {
        ObjectNode request = mapper.createObjectNode();
        request.put("model", config.model());
        request.put("prompt", config.promptFor(text));
        request.put("stream", false);
        request.put("think", false);
        request.put("keep_alive", config.keepAlive());
        try {
            request.set("format", mapper.readTree(SCHEMA));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("The built-in response schema is not valid JSON.", ex);
        }
        ObjectNode options = request.putObject("options");
        options.put("num_ctx", config.contextTokens());
        options.put("num_predict", config.replyTokens());
        options.put("temperature", 0);
        if (config.gpuLayers() != null) {
            options.put("num_gpu", config.gpuLayers());
        }
        return request.toString();
    }

    private Optional<String> replyBody(String envelope) {
        try {
            Reply reply = mapper.readValue(envelope, Reply.class);
            return Optional.ofNullable(reply.response()).filter(body -> !body.isBlank());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<BookingDraft> draftFrom(String body) {
        try {
            RawDraft raw = mapper.readValue(body, RawDraft.class);
            return Optional.of(BookingDraft.builder()
                    .roomNumber(raw.roomNumber())
                    .tier(raw.tier())
                    .guestName(raw.guestName())
                    .guests(raw.guests())
                    .nights(raw.nights())
                    .breakfast(raw.breakfast())
                    .build());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Transport httpTransport(OllamaConfig config) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(config.timeout()).build();
        return requestBody -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(config.endpoint()))
                        .timeout(config.timeout())
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                return (response.statusCode() == 200) ? Optional.of(response.body()) : Optional.empty();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (Exception ex) {
                return Optional.empty();
            }
        };
    }

    record Reply(String response) {
    }

    record RawDraft(String roomNumber, String tier, String guestName,
                    Integer guests, Integer nights, Boolean breakfast) {
    }
}
