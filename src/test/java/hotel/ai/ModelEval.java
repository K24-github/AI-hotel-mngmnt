package hotel.ai;

import hotel.service.HotelManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModelEval {

    public record FieldScore(String field, int right, int scored) {
        public double percent() {
            return (scored == 0) ? 0 : (100.0 * right) / scored;
        }
    }

    public record Report(String model, List<FieldScore> fields, int resolveAgreements, int rows,
                         long p50Millis, long p95Millis, int emptyReplies, String placement,
                         List<String> misses) {
    }

    private ModelEval() {
    }

    /** The names Ollama actually has, so a typo fails the run instead of scoring the default. */
    public static List<String> installedModels(String endpoint) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            URI tags = URI.create(endpoint.replace("/api/generate", "/api/tags"));
            HttpRequest request = HttpRequest.newBuilder(tags).timeout(Duration.ofSeconds(2)).GET().build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            List<String> names = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode node
                    : new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("models")) {
                names.add(node.path("name").asText());
            }
            return names;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static Report run(OllamaConfig config, List<EvalSet.Row> rows) {
        evictEveryModel(config.endpoint());
        BookingParser parser = new OllamaBookingParser(config);
        parser.parse("kamar 101 1 malam");
        Map<String, int[]> tally = new LinkedHashMap<>();
        for (String field : List.of("roomNumber", "tier", "guests", "nights", "breakfast", "guestName")) {
            tally.put(field, new int[2]);
        }
        List<Long> timings = new ArrayList<>();
        List<String> misses = new ArrayList<>();
        int resolveAgreements = 0;
        int emptyReplies = 0;

        for (EvalSet.Row row : rows) {
            long started = System.nanoTime();
            Optional<BookingDraft> parsed = parser.parse(row.sentence());
            timings.add((System.nanoTime() - started) / 1_000_000);

            if (parsed.isEmpty()) {
                emptyReplies++;
                misses.add("no reply | " + row.sentence());
                continue;
            }
            BookingDraft got = parsed.get();
            BookingDraft want = row.expected();

            score(tally, "roomNumber", Objects.equals(want.roomNumber(), got.roomNumber()));
            score(tally, "tier", Objects.equals(want.tier(), got.tier()));
            score(tally, "guests", Objects.equals(want.guests(), got.guests()));
            score(tally, "nights", Objects.equals(want.nights(), got.nights()));
            score(tally, "breakfast", want.wantsBreakfast() == got.wantsBreakfast());
            score(tally, "guestName", equalNames(want.guestName(), got.guestName()));

            boolean resolved = new BookingResolver(new HotelManager())
                    .resolve(got, row.sentence()).isPresent();
            if (resolved == row.resolves()) {
                resolveAgreements++;
            }
            if (!sameDraft(want, got)) {
                misses.add(describe(row.sentence(), want, got));
            }
        }

        List<FieldScore> fields = new ArrayList<>();
        tally.forEach((field, counts) -> fields.add(new FieldScore(field, counts[0], counts[1])));
        timings.sort(Comparator.naturalOrder());
        return new Report(config.model(), fields, resolveAgreements, rows.size(),
                percentile(timings, 50), percentile(timings, 95), emptyReplies,
                placementOf(config), misses);
    }

    /**
     * Sends every resident model home before a run. Two models of this size do not fit in
     * VRAM together, so whatever ran last would otherwise push this one onto the CPU and
     * show up as the new model being slower.
     */
    public static void evictEveryModel(String endpoint) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            URI ps = URI.create(endpoint.replace("/api/generate", "/api/ps"));
            HttpRequest listing = HttpRequest.newBuilder(ps).timeout(Duration.ofSeconds(2)).GET().build();
            String body = client.send(listing, HttpResponse.BodyHandlers.ofString()).body();
            for (com.fasterxml.jackson.databind.JsonNode node
                    : new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("models")) {
                evict(client, endpoint, node.path("name").asText());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // A model we cannot see is a model we cannot evict; the run still reports its placement.
        }
    }

    private static void evict(HttpClient client, String endpoint, String model)
            throws java.io.IOException, InterruptedException {
        String body = "{\"model\":\"" + model + "\",\"keep_alive\":0}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Whether the model sat in VRAM or on the CPU, which moves latency by a third. */
    public static String placementOf(OllamaConfig config) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            URI ps = URI.create(config.endpoint().replace("/api/generate", "/api/ps"));
            HttpRequest request = HttpRequest.newBuilder(ps).timeout(Duration.ofSeconds(2)).GET().build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            for (com.fasterxml.jackson.databind.JsonNode node
                    : new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).path("models")) {
                if (config.model().equals(node.path("name").asText())) {
                    long total = node.path("size").asLong();
                    long inVram = node.path("size_vram").asLong();
                    long percent = (total == 0) ? 0 : Math.round((100.0 * inVram) / total);
                    return percent + "% GPU / " + (100 - percent) + "% CPU";
                }
            }
            return "not resident";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return "unknown";
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private static void score(Map<String, int[]> tally, String field, boolean right) {
        int[] counts = tally.get(field);
        counts[1]++;
        if (right) {
            counts[0]++;
        }
    }

    private static boolean equalNames(String want, String got) {
        if (want == null || got == null) {
            return want == null && got == null;
        }
        return want.equalsIgnoreCase(got);
    }

    private static boolean sameDraft(BookingDraft want, BookingDraft got) {
        return Objects.equals(want.roomNumber(), got.roomNumber())
                && Objects.equals(want.tier(), got.tier())
                && Objects.equals(want.guests(), got.guests())
                && Objects.equals(want.nights(), got.nights())
                && want.wantsBreakfast() == got.wantsBreakfast()
                && equalNames(want.guestName(), got.guestName());
    }

    private static String describe(String sentence, BookingDraft want, BookingDraft got) {
        StringBuilder diff = new StringBuilder();
        appendIfDifferent(diff, "room", want.roomNumber(), got.roomNumber());
        appendIfDifferent(diff, "tier", want.tier(), got.tier());
        appendIfDifferent(diff, "guests", want.guests(), got.guests());
        appendIfDifferent(diff, "nights", want.nights(), got.nights());
        if (want.wantsBreakfast() != got.wantsBreakfast()) {
            appendIfDifferent(diff, "breakfast", want.wantsBreakfast(), got.wantsBreakfast());
        }
        if (!equalNames(want.guestName(), got.guestName())) {
            appendIfDifferent(diff, "name", want.guestName(), got.guestName());
        }
        return diff + " | " + sentence;
    }

    private static void appendIfDifferent(StringBuilder diff, String label, Object want, Object got) {
        if (Objects.equals(want, got)) {
            return;
        }
        if (diff.length() > 0) {
            diff.append(", ");
        }
        diff.append(label).append(" want=").append(want).append(" got=").append(got);
    }

    private static long percentile(List<Long> sorted, int percentile) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }
}
